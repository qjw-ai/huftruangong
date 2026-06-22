package com.aicust.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SentimentAnalysisServiceTest {

    private final SentimentAnalysisService service = new SentimentAnalysisService();

    @Test
    void analyzeReturnsPositiveLabelWhenPositiveWordsExist() {
        SentimentAnalysisService.AnalysisResult result =
                service.analyze("景区风景优美，服务很好，工作人员也很热情，非常推荐。 ");

        assertThat(result.label()).isEqualTo("POSITIVE");
        assertThat(result.score()).isPositive();
    }

    @Test
    void analyzeReturnsNegativeLabelWhenNegativeWordsExist() {
        SentimentAnalysisService.AnalysisResult result =
                service.analyze("排队太长，体验差，收费高，不推荐。 ");

        assertThat(result.label()).isEqualTo("NEGATIVE");
        assertThat(result.score()).isNegative();
    }

    @Test
    void extractFocusPointsCountsAttractionKeywords() {
        Map<String, Long> focusPoints = service.extractFocusPoints(
                "熊猫馆几点开放？停车场远吗？",
                "熊猫馆九点开放，停车场在南门附近。"
        );

        assertThat(focusPoints).containsKeys("熊猫馆", "停车场", "南门");
        assertThat(focusPoints.get("熊猫馆")).isEqualTo(2L);
    }
}
