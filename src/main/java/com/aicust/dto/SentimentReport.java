package com.aicust.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 分析报告 DTO — /api/admin/report/sentiment 返回结构
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SentimentReport {

    /** 情感分布统计 */
    private SentimentDistribution sentiment;

    /** 关注点聚类 Top20 */
    private List<Map<String, Object>> focusClusters;

    /** 满意度趋势（最近7天） */
    private List<Map<String, Object>> satisfactionTrend;

    /** 各景点关注度分布 */
    private List<Map<String, Object>> attractionScores;
}
