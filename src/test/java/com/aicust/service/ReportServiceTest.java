package com.aicust.service;

import com.aicust.dto.SentimentReport;
import com.aicust.dto.ServiceDashboard;
import com.aicust.model.InteractionLog;
import com.aicust.repository.InteractionLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportServiceTest {

    private final InteractionLogRepository repository = mock(InteractionLogRepository.class);
    private final SentimentAnalysisService sentimentService = new SentimentAnalysisService();
    private final ReportService reportService = new ReportService(repository, sentimentService);

    @Test
    void getSentimentReportBuildsDistributionAndFocusClusters() {
        InteractionLog positive = newLog("熊猫馆好玩吗？", "熊猫馆很好，服务热情，值得推荐。", "POSITIVE", 0.8);
        positive.setFocusPoints("熊猫馆");

        InteractionLog negative = newLog("停车场远吗？", "停车场排队太长，体验差。", "NEGATIVE", -0.6);
        negative.setFocusPoints("停车场");

        when(repository.findByCreatedAtBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(positive, negative));

        SentimentReport report = reportService.getSentimentReport(7);

        assertThat(report.getSentiment().getPositive()).isEqualTo(1);
        assertThat(report.getSentiment().getNegative()).isEqualTo(1);
        assertThat(report.getFocusClusters())
                .extracting(item -> item.get("keyword"))
                .contains("熊猫馆", "停车场");
    }

    @Test
    void getDashboardAggregatesVisitorsAndHotQa() {
        when(repository.countByCreatedAtBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(3L, 12L);
        when(repository.topQuestionsInRange(any(LocalDateTime.class), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.<Object[]>of(new Object[]{"熊猫馆几点开放？", 5L}));
        when(repository.findByCreatedAtBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

        ServiceDashboard dashboard = reportService.getDashboard();

        assertThat(dashboard.getTodayVisitors()).isEqualTo(3L);
        assertThat(dashboard.getWeekVisitors()).isEqualTo(12L);
        assertThat(dashboard.getHotQaTop10()).hasSize(1);
        assertThat(dashboard.getHotQaTop10().getFirst().get("question")).isEqualTo("熊猫馆几点开放？");
    }

    private InteractionLog newLog(String question, String answer, String label, double score) {
        InteractionLog log = new InteractionLog();
        log.setUserId(1L);
        log.setQuestion(question);
        log.setAnswer(answer);
        log.setSentimentLabel(label);
        log.setSentimentScore(score);
        log.setCreatedAt(LocalDateTime.now());
        return log;
    }
}
