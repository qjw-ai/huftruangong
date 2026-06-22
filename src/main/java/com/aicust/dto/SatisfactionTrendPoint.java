package com.aicust.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 满意度趋势单点
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SatisfactionTrendPoint {

    private String date;
    private double avgScore;
    private long totalCount;
    private double positiveRatio;
}
