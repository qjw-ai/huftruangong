package com.aicust.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 交互日志 — 记录每次用户对话的完整信息，用于满意度分析和情感追踪。
 */
@Entity
@Table(name = "interaction_log", indexes = {
        @Index(name = "idx_ilog_user_time", columnList = "user_id, created_at"),
        @Index(name = "idx_ilog_satisfaction", columnList = "satisfaction"),
        @Index(name = "idx_ilog_created", columnList = "created_at")
})
@Data
public class InteractionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 用户名（冗余快照，避免后续删用户丢失数据） */
    @Column(name = "username", length = 64)
    private String username;

    /** 用户提出的问题 */
    @Column(name = "question", columnDefinition = "TEXT")
    private String question;

    /** 系统回答的内容 */
    @Column(name = "answer", columnDefinition = "TEXT")
    private String answer;

    /** 对话模式：chat / agent */
    @Column(name = "mode", length = 16)
    private String mode;

    /** 预计 token 数 */
    @Column(name = "estimated_tokens")
    private Integer estimatedTokens;

    /** 实际 token 数 */
    @Column(name = "actual_tokens")
    private Integer actualTokens;

    /** 响应耗时（毫秒） */
    @Column(name = "duration_ms")
    private Long durationMs;

    /** 满意度评分：1~5，null 表示未评价 */
    @Column(name = "satisfaction")
    private Integer satisfaction;

    /** 情感得分：-1.0 ~ 1.0，null 表示未分析 */
    @Column(name = "sentiment_score")
    private Double sentimentScore;

    /** 情感标签：POSITIVE / NEUTRAL / NEGATIVE */
    @Column(name = "sentiment_label", length = 16)
    private String sentimentLabel;

    /** 关注点/关键词（逗号分隔，如"大熊猫,喂食时间"） */
    @Column(name = "focus_points", length = 512)
    private String focusPoints;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
