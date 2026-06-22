package com.aicust.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 情感分布
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SentimentDistribution {

    private long positive;
    private long neutral;
    private long negative;
    private double positiveRatio;
    private double avgSentimentScore;
}
