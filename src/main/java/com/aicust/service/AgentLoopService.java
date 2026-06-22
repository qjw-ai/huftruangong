package com.aicust.service;

import com.aicust.model.AgentStep;
import com.aicust.model.AgentTask;
import com.aicust.repository.AgentStepRepository;
import com.aicust.repository.AgentTaskRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AgentLoopService {

    private static final Logger log = LoggerFactory.getLogger(AgentLoopService.class);

    private final ChatClient chatClient;
    private final AgentTaskRepository taskRepository;
    private final AgentStepRepository stepRepository;
    private final ChatMemoryService memoryService;
    private final TokenQuotaService quotaService; // 计费/限流服务
    private final ToolService toolService;        // 工具执行服务
    private final ObjectMapper objectMapper;      // 用于解析 JSON
    private final InteractionLogWriter logWriter;
    private final SentimentAnalysisService sentimentService;

    public AgentLoopService(ChatClient chatClient,
                            AgentTaskRepository taskRepository,
                            AgentStepRepository stepRepository,
                            ChatMemoryService memoryService,
                            TokenQuotaService quotaService,
                            ToolService toolService,
                            ObjectMapper objectMapper,
                            InteractionLogWriter logWriter,
                            SentimentAnalysisService sentimentService) {
        this.chatClient = chatClient;
        this.taskRepository = taskRepository;
        this.stepRepository = stepRepository;
        this.memoryService = memoryService;
        this.quotaService = quotaService;
        this.toolService = toolService;
        this.objectMapper = objectMapper;
        this.logWriter = logWriter;
        this.sentimentService = sentimentService;
    }

    /**
     * 核心 Agent 循环入口
     */
    public Flux<String> runAgent(Long userId, String prompt) {
        return Flux.create(sink -> {
            long startTime = System.currentTimeMillis();
            String taskId = UUID.randomUUID().toString();

            // 1. 初始化任务并持久化 (MySQL)
            AgentTask task = new AgentTask();
            task.setId(taskId);
            task.setUserId(userId);
            task.setPrompt(prompt);
            task.setStatus("THINKING");
            task.setModelName("qwen-max"); // 或动态获取
            task.setCreatedAt(LocalDateTime.now());
            taskRepository.save(task);

            // 2. 构建 System Prompt (包含工具定义，强制 JSON 输出)
            // 💡 关键点：用 Prompt 引导 AI，而不是完全依赖 Function Calling API，这样控制力更强
            String systemPrompt = """
                你是一个智能助手，具备意图识别和工具调用能力。
                
                【可用工具】
                1. getCurrentTime: 获取当前服务器时间，无参数。
                2. getUserBalance: 查询用户余额，参数: {"userId": "用户ID"}。
                
                【思考与回复规则】
                1. 如果用户的问题需要使用工具才能回答：
                   请务必只输出一个 JSON 对象，格式如下：
                   { "tool": "工具名称", "args": "参数字符串" }
                2. 如果不需要工具，或者已经获得工具结果：
                   请直接输出正常的自然语言回答。
                """;

            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(systemPrompt));
            messages.addAll(memoryService.getHistory(userId)); // 加载历史记忆
            messages.add(new UserMessage(prompt));

            boolean isDone = false;
            int currentStep = 0;
            int maxSteps = 5; // 🛑 安全熔断：防止无限循环

            try {
                while (!isDone && currentStep < maxSteps) {
                    currentStep++;

                    // A. 状态推送：思考中
                    sink.next("event:status\ndata: {\"step\": " + currentStep + ", \"type\": \"THINKING\"}\n\n");
                    saveStep(taskId, "THOUGHT", "Agent 正在规划第 " + currentStep + " 步...");

                    // B. 调用大模型
                    // 💰 这里可以插入 quotaService.check() 进行预扣费
                    ChatResponse response = chatClient.prompt()
                            .messages(messages)
                            .call()
                            .chatResponse();

                    AssistantMessage assistantMsg = response.getResult().getOutput();
                    String content = assistantMsg.getContent();

                    // 将 AI 的回复加入上下文，保证对话连贯
                    messages.add(assistantMsg);

                    // C. 意图判断：是调工具还是最终回答？
                    if (isToolCall(content)) {
                        // === 进入工具执行分支 ===

                        // 1. 解析 JSON
                        JsonNode json = objectMapper.readTree(content);
                        String toolName = json.get("tool").asText();
                        String args = json.has("args") ? json.get("args").asText() : "";

                        // 2. 状态推送：执行中
                        sink.next("event:status\ndata: {\"type\": \"EXECUTING\", \"tool\": \"" + toolName + "\"}\n\n");
                        saveStep(taskId, "CALL", "调用工具: " + toolName + " 参数: " + args);

                        // 3. 执行工具 (调用 ToolService)
                        String result = toolService.execute(toolName, args);

                        // 4. 记录结果 (Observation)
                        saveStep(taskId, "OBSERVATION", result);

                        // 5. 将工具结果反馈给 AI (作为 UserMessage 或 SystemMessage)
                        messages.add(new UserMessage("【工具执行结果】: " + result + "\n请根据结果继续回答用户问题。"));

                    } else {
                        // === 进入最终回复分支 ===

                        // 1. 状态推送：输出正文
                        sink.next("event:message\ndata: " + content + "\n\n");
                        saveStep(taskId, "FINISHED", content);

                        // 2. 更新任务状态
                        task.setStatus("COMPLETED");
                        taskRepository.save(task);

                        // 3. 异步更新长期记忆
                        memoryService.addMessage(userId, new UserMessage(prompt)); // 存问题
                        memoryService.addMessage(userId, new AssistantMessage(content)); // 存答案

                        // 4. 异步写入交互日志
                        saveInteractionLog(userId, prompt, content,
                                System.currentTimeMillis() - startTime);

                        isDone = true;
                    }
                }

                // D. 步数超限处理
                if (currentStep >= maxSteps && !isDone) {
                    sink.next("event:error\ndata: 任务执行步骤过多，已强制终止。\n\n");
                    saveStep(taskId, "ERROR", "Max steps exceeded");
                    task.setStatus("FAILED");
                    taskRepository.save(task);
                }

            } catch (Exception e) {
                log.error("Agent Loop Error", e);
                task.setStatus("FAILED");
                taskRepository.save(task);
                sink.next("event:error\ndata: 系统内部错误: " + e.getMessage() + "\n\n");
                sink.error(e);
            } finally {
                sink.complete();
            }
        });
    }

    /**
     * 异步保存 Agent 对话日志。
     */
    private void saveInteractionLog(Long userId, String question, String answer, long durationMs) {
        try {
            SentimentAnalysisService.AnalysisResult sentiment = sentimentService.analyze(answer);
            Map<String, Long> focusPoints = sentimentService.extractFocusPoints(question, answer);
            String focusStr = focusPoints.keySet().stream()
                    .limit(10)
                    .reduce((a, b) -> a + "," + b)
                    .orElse("");

            logWriter.logInteraction(userId, null, question, answer, "agent",
                    null, answer != null ? answer.length() : 0, durationMs,
                    sentiment.score(), sentiment.label(), focusStr);
        } catch (Exception e) {
            log.warn("Failed to save agent interaction log for userId={}: {}", userId, e.getMessage());
        }
    }

    /**
     * 辅助方法：记录步骤到 MySQL
     */
    private void saveStep(String taskId, String type, String content) {
        try {
            AgentStep step = new AgentStep();
            step.setTaskId(taskId);
            step.setStepType(type);
            step.setContent(content);
            step.setCreatedAt(LocalDateTime.now());
            stepRepository.save(step);
        } catch (Exception e) {
            log.warn("Failed to save agent step", e);
        }
    }

    /**
     * 简单的意图识别逻辑 (基于 JSON 格式检查)
     * 生产环境可以使用更严格的 Json Schema 校验
     */
    private boolean isToolCall(String content) {
        if (content == null) return false;
        String trimmed = content.trim();
        // 简单判断：以 { 开头，且包含 "tool" 关键字
        return trimmed.startsWith("{") && trimmed.contains("\"tool\"");
    }
}
