package com.aicust.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 服务看板聚合查询 DTO — /api/admin/dashboard 返回结构
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceDashboard {

    /** 当日服务人次 */
    private long todayVisitors;

    /** 本周服务人次 */
    private long weekVisitors;

    /** 热门问答 Top10 */
    private List<Map<String, Object>> hotQaTop10;

    /** 满意度趋势（最近7天） */
    private List<SatisfactionTrendPoint> satisfactionTrend;

    /** 各景点关注度分布 */
    private List<Map<String, Object>> attractionDistribution;
}
