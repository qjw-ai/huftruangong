package com.aicust.service;

import com.aicust.model.AiPlan;
import com.aicust.service.SentimentAnalysisService.AnalysisResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);

    private final ChatClient chatClient;
    private final TokenEstimator estimator;
    private final TokenQuotaService quotaService;
    private final PlanService planService;
    private final SensitiveWordService sensitiveService;
    private final ChatMemoryService memoryService;
    private final RagSearchService ragSearchService;
    private final InteractionLogWriter logWriter;
    private final SentimentAnalysisService sentimentService;

    /** RAG 检索默认 topK */
    private static final int RAG_TOPK = 5;

    public AiChatService(ChatClient chatClient, TokenEstimator estimator, TokenQuotaService quotaService,
                         PlanService planService, SensitiveWordService sensitiveService,
                         ChatMemoryService memoryService, RagSearchService ragSearchService,
                         InteractionLogWriter logWriter, SentimentAnalysisService sentimentService) {
        this.chatClient = chatClient;
        this.estimator = estimator;
        this.quotaService = quotaService;
        this.planService = planService;
        this.sensitiveService = sensitiveService;
        this.memoryService = memoryService;
        this.ragSearchService = ragSearchService;
        this.logWriter = logWriter;
        this.sentimentService = sentimentService;
    }

    /**
     * 流式对话（带 RAG 检索增强）。
     *
     * @param userId 用户ID
     * @param prompt 用户问题
     * @param mode   兴趣模式（"history"/"nature"/"food"/null），用于 RAG 分类过滤
     */
    public Flux<String> streamChat(Long userId, String prompt, String mode) {

        if (sensitiveService.hasSensitiveWord(prompt)) {
            return Flux.just("Your prompt contains sensitive content. Request denied.");
        }

        long startTime = System.currentTimeMillis();
        AtomicReference<String> usernameRef = new AtomicReference<>("");
        AtomicReference<String> fullAnswerRef = new AtomicReference<>("");

        // 尝试获取用户名
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getDetails() instanceof com.aicust.model.User user) {
                usernameRef.set(user.getUsername());
            }
        } catch (Exception ignored) {
        }

        // ===== RAG 检索 =====
        String category = modeToCategory(mode);
        List<RagSearchService.SearchHit> ragHits = ragSearchService.search(prompt, RAG_TOPK, category);
        String systemPrompt = buildSystemPrompt(ragHits, category);

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        messages.addAll(memoryService.getRelatedHistory(userId, prompt));
        messages.add(new UserMessage(prompt));

        String model = "qwen";
        int estimated = estimator.estimate(model, prompt);
        AiPlan plan = planService.getPlan(userId);
        quotaService.check(userId, plan.getDailyTokenLimit(), estimated);

        memoryService.addMessage(userId, new UserMessage(prompt));

        AtomicInteger actualLength = new AtomicInteger(0);

        return chatClient.prompt()
                .messages(messages)
                .stream()
                .content()
                .doOnNext(chunk -> {
                    if (chunk != null) {
                        actualLength.addAndGet(chunk.length());
                        fullAnswerRef.set(fullAnswerRef.get() + chunk);
                    }
                })
                .doOnComplete(() -> {
                    int actual = actualLength.get();
                    long duration = System.currentTimeMillis() - startTime;
                    String answer = fullAnswerRef.get();

                    quotaService.settle(userId, estimated, actual);
                    memoryService.addMessage(userId, new AssistantMessage(answer));

                    // 异步写入交互日志 + 情感分析
                    saveInteractionLog(userId, usernameRef.get(), prompt, answer, mode,
                            estimated, actual, duration);
                })
                .doOnError(e -> {
                    quotaService.rollback(userId, estimated);
                    log.error("Chat error for userId={}: {}", userId, e.getMessage());
                });
    }

    /**
     * 异步保存交互日志并进行情感分析。
     */
    private void saveInteractionLog(Long userId, String username, String question,
                                    String answer, String mode,
                                    int estimatedTokens, int actualTokens,
                                    long durationMs) {
        try {
            // 情感分析
            AnalysisResult sentiment = sentimentService.analyze(answer);

            // 关注点聚类
            Map<String, Long> focusPoints = sentimentService.extractFocusPoints(question, answer);
            String focusStr = focusPoints.keySet().stream()
                    .limit(10)
                    .reduce((a, b) -> a + "," + b)
                    .orElse("");

            logWriter.logInteraction(userId, username, question, answer, mode,
                    estimatedTokens, actualTokens, durationMs,
                    sentiment.score(), sentiment.label(), focusStr);

        } catch (Exception e) {
            log.error("Failed to save interaction log for userId={}: {}", userId, e.getMessage());
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 构建带 RAG 检索结果的 system prompt。
     * <p>有检索结果时要求 LLM 严格基于参考资料回答；无结果时降级为通用回答。
     */
    private String buildSystemPrompt(List<RagSearchService.SearchHit> hits, String category) {
        if (hits.isEmpty()) {
            String catHint = category != null ? "关于" + category + "方面的" : "";
            return "你是一个景区智能助手。"
                    + "请尽力回答游客的" + catHint + "问题。"
                    + "如果遇到不确定的信息，请如实告知游客并建议其咨询景区工作人员。";
        }

        String catHint = category != null ? "（" + category + "）" : "";
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个景区智能助手，正在为该景区" + catHint + "的游客提供服务。\n\n");
        sb.append("请严格基于以下参考资料回答游客问题，不要编造参考资料中没有的信息。\n");
        sb.append("如果参考资料不足以回答问题，请如实告知游客。\n\n");
        sb.append("=== 参考资料（共").append(hits.size()).append("条） ===\n");

        for (int i = 0; i < hits.size(); i++) {
            RagSearchService.SearchHit hit = hits.get(i);
            sb.append("[").append(i + 1).append("]");
            if (!hit.title().isBlank()) {
                sb.append(" 《").append(hit.title()).append("》");
            }
            sb.append("（相关度: ").append(String.format("%.2f", hit.fusedScore())).append("）\n");
            sb.append(hit.text()).append("\n\n");
        }
        sb.append("=== 参考资料结束 ===\n\n");
        sb.append("请用自然、亲切的语气回答游客，并在回答中适当引用参考资料的编号（如[1][2]）以增加可信度。");

        return sb.toString();
    }

    /** 将前端 mode 转为 RAG category */
    private String modeToCategory(String mode) {
        if (mode == null || mode.isBlank() || "all".equalsIgnoreCase(mode)) {
            return null;
        }
        return switch (mode.toLowerCase()) {
            case "history" -> "历史文化";
            case "nature" -> "自然风光";
            case "food" -> "美食特产";
            default -> null;
        };
    }
}