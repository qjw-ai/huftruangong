package com.aicust.service;

import com.aicust.model.InteractionLog;
import com.aicust.repository.InteractionLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 异步写入交互日志 — 在对话完成后将用户问题、回答、token 消耗等信息持久化到 MySQL。
 */
@Service
public class InteractionLogWriter {

    private static final Logger log = LoggerFactory.getLogger(InteractionLogWriter.class);

    private final InteractionLogRepository logRepository;

    public InteractionLogWriter(InteractionLogRepository logRepository) {
        this.logRepository = logRepository;
    }

    /**
     * 异步保存交互日志。
     *
     * @param log 日志实体
     */
    @Async("taskExecutor")
    public void save(InteractionLog interactionLog) {
        try {
            logRepository.save(interactionLog);
            log.debug("InteractionLog saved: userId={}, question={}...",
                    interactionLog.getUserId(),
                    interactionLog.getQuestion() != null ? interactionLog.getQuestion().substring(0, Math.min(30, interactionLog.getQuestion().length())) : "null");
        } catch (Exception e) {
            log.error("Failed to save InteractionLog: userId={}, question={}",
                    interactionLog.getUserId(), interactionLog.getQuestion(), e);
        }
    }

    /**
     * 便捷工厂方法 — 在对话完成后调用。
     */
    @Async("taskExecutor")
    public void logInteraction(Long userId, String username, String question,
                               String answer, String mode,
                               Integer estimatedTokens, Integer actualTokens,
                               Long durationMs,
                               Double sentimentScore, String sentimentLabel,
                               String focusPoints) {
        InteractionLog interactionLog = new InteractionLog();
        interactionLog.setUserId(userId);
        interactionLog.setUsername(username);
        interactionLog.setQuestion(question);
        interactionLog.setAnswer(answer);
        interactionLog.setMode(mode);
        interactionLog.setEstimatedTokens(estimatedTokens);
        interactionLog.setActualTokens(actualTokens);
        interactionLog.setDurationMs(durationMs);
        interactionLog.setSentimentScore(sentimentScore);
        interactionLog.setSentimentLabel(sentimentLabel);
        interactionLog.setFocusPoints(focusPoints);
        interactionLog.setCreatedAt(LocalDateTime.now());
        save(interactionLog);
    }
}
