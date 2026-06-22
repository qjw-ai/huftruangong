package com.aicust.service;

import com.aicust.dto.SatisfactionTrendPoint;
import com.aicust.dto.SentimentDistribution;
import com.aicust.dto.SentimentReport;
import com.aicust.dto.ServiceDashboard;
import com.aicust.model.InteractionLog;
import com.aicust.repository.InteractionLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 聚合报表服务 — 提供日报/周报所需的各类统计查询。
 */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final InteractionLogRepository logRepository;
    private final SentimentAnalysisService sentimentService;

    public ReportService(InteractionLogRepository logRepository,
                         SentimentAnalysisService sentimentService) {
        this.logRepository = logRepository;
        this.sentimentService = sentimentService;
    }

    // ======================== 服务看板 ========================

    /**
     * 获取当日/本周服务概览。
     */
    @Transactional(readOnly = true)
    public ServiceDashboard getDashboard() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = now.toLocalDate().atStartOfDay();
        LocalDateTime weekStart = now.with(DayOfWeek.MONDAY).toLocalDate().atStartOfDay();

        long todayVisitors = logRepository.countByCreatedAtBetween(todayStart, now);
        long weekVisitors = logRepository.countByCreatedAtBetween(weekStart, now);

        // 热门问答 Top10
        List<Map<String, Object>> hotQa = logRepository.topQuestionsInRange(
                        weekStart, now, PageRequest.of(0, 10))
                .stream()
                .map(row -> Map.<String, Object>of(
                        "question", row[0],
                        "count", row[1]
                ))
                .collect(Collectors.toList());

        // 满意度趋势（最近7天）
        List<SatisfactionTrendPoint> trend = buildSatisfactionTrend(now);

        // 各景点关注度分布
        List<Map<String, Object>> attractionDist = buildAttractionDistribution(todayStart, now);

        return ServiceDashboard.builder()
                .todayVisitors(todayVisitors)
                .weekVisitors(weekVisitors)
                .hotQaTop10(hotQa)
                .satisfactionTrend(trend)
                .attractionDistribution(attractionDist)
                .build();
    }

    // ======================== 情感分析报告 ========================

    /**
     * 生成情感分析报告。
     *
     * @param days 回溯天数，默认7
     */
    @Transactional(readOnly = true)
    public SentimentReport getSentimentReport(int days) {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusDays(days);

        List<InteractionLog> logs = logRepository.findByCreatedAtBetween(start, end);
        if (logs.isEmpty()) {
            return SentimentReport.builder()
                    .sentiment(SentimentDistribution.builder().build())
                    .focusClusters(Collections.emptyList())
                    .satisfactionTrend(Collections.emptyList())
                    .attractionScores(Collections.emptyList())
                    .build();
        }

        // 1. 情感分布
        SentimentDistribution dist = analyzeSentimentDistribution(logs, start, end);

        // 2. 关注点聚类
        List<Map<String, Object>> clusters = buildFocusClusters(logs);

        // 3. 满意度趋势（近7天）
        List<Map<String, Object>> trend = buildSatisfactionTrendMap(end);

        // 4. 各景点关注度
        List<Map<String, Object>> attractions = buildAttractionScores(logs);

        return SentimentReport.builder()
                .sentiment(dist)
                .focusClusters(clusters)
                .satisfactionTrend(trend)
                .attractionScores(attractions)
                .build();
    }

    // ======================== 私有辅助方法 ========================

    private SentimentDistribution analyzeSentimentDistribution(List<InteractionLog> logs,
                                                               LocalDateTime start, LocalDateTime end) {
        long positive = 0, neutral = 0, negative = 0;
        double totalScore = 0.0;
        int scored = 0;

        for (InteractionLog log : logs) {
            // 优先使用已标注的情感
            if (log.getSentimentLabel() != null) {
                switch (log.getSentimentLabel()) {
                    case "POSITIVE" -> positive++;
                    case "NEGATIVE" -> negative++;
                    default -> neutral++;
                }
                if (log.getSentimentScore() != null) {
                    totalScore += log.getSentimentScore();
                    scored++;
                }
            } else {
                // 实时分析
                SentimentAnalysisService.AnalysisResult result = sentimentService.analyze(log.getAnswer());
                if (result.score() > 0.05) positive++;
                else if (result.score() < -0.05) negative++;
                else neutral++;
                totalScore += result.score();
                scored++;
            }
        }

        double avgScore = scored > 0 ? totalScore / scored : 0.0;
        long total = positive + neutral + negative;

        return SentimentDistribution.builder()
                .positive(positive)
                .neutral(neutral)
                .negative(negative)
                .positiveRatio(total > 0 ? (double) positive / total : 0.0)
                .avgSentimentScore(Math.round(avgScore * 1000.0) / 1000.0)
                .build();
    }

    private List<Map<String, Object>> buildFocusClusters(List<InteractionLog> logs) {
        // 合并所有日志的关注点和问答文本做词频统计
        Map<String, Long> allFocus = new HashMap<>();
        for (InteractionLog log : logs) {
            if (log.getFocusPoints() != null && !log.getFocusPoints().isBlank()) {
                Arrays.stream(log.getFocusPoints().split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .forEach(word -> allFocus.merge(word, 1L, Long::sum));
            }
            // 也统计问答中的景点关键词
            Map<String, Long> extracted = sentimentService.extractFocusPoints(log.getQuestion(), log.getAnswer());
            extracted.forEach((key, count) -> allFocus.merge(key, count, Long::sum));
        }

        return allFocus.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(20)
                .map(e -> Map.<String, Object>of(
                        "keyword", e.getKey(),
                        "count", e.getValue()
                ))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> buildAttractionScores(List<InteractionLog> logs) {
        Map<String, Long> scoreMap = new HashMap<>();
        for (InteractionLog log : logs) {
            Map<String, Long> points = sentimentService.extractFocusPoints(log.getQuestion(), log.getAnswer());
            points.keySet().forEach(key -> scoreMap.merge(key, 1L, Long::sum));
        }
        return scoreMap.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(15)
                .map(e -> Map.<String, Object>of(
                        "attraction", e.getKey(),
                        "mentions", e.getValue()
                ))
                .collect(Collectors.toList());
    }

    private List<SatisfactionTrendPoint> buildSatisfactionTrend(LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        List<SatisfactionTrendPoint> result = new ArrayList<>();

        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            LocalDateTime start = day.atStartOfDay();
            LocalDateTime end = day.plusDays(1).atStartOfDay();

            List<InteractionLog> dayLogs = logRepository.findByCreatedAtBetween(start, end);
            if (dayLogs.isEmpty()) continue;

            int totalScore = 0;
            long posCount = 0;
            for (InteractionLog log : dayLogs) {
                if (log.getSatisfaction() != null) {
                    totalScore += log.getSatisfaction();
                }
                if (log.getSentimentLabel() != null && "POSITIVE".equals(log.getSentimentLabel())) {
                    posCount++;
                }
            }

            result.add(SatisfactionTrendPoint.builder()
                    .date(day.format(DATE_FMT))
                    .avgScore(totalScore > 0 ? (double) totalScore / dayLogs.size() : 0.0)
                    .totalCount(dayLogs.size())
                    .positiveRatio(posCount / (double) dayLogs.size())
                    .build());
        }

        return result;
    }

    private List<Map<String, Object>> buildSatisfactionTrendMap(LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        List<Map<String, Object>> result = new ArrayList<>();

        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            LocalDateTime start = day.atStartOfDay();
            LocalDateTime end = day.plusDays(1).atStartOfDay();

            List<InteractionLog> dayLogs = logRepository.findByCreatedAtBetween(start, end);
            if (dayLogs.isEmpty()) continue;

            int totalScore = 0;
            long posCount = 0;
            for (InteractionLog log : dayLogs) {
                if (log.getSatisfaction() != null) {
                    totalScore += log.getSatisfaction();
                }
                if (log.getSentimentLabel() != null && "POSITIVE".equals(log.getSentimentLabel())) {
                    posCount++;
                }
            }

            result.add(Map.<String, Object>of(
                    "date", day.format(DATE_FMT),
                    "avgScore", totalScore > 0 ? Math.round((double) totalScore / dayLogs.size() * 100.0) / 100.0 : 0.0,
                    "totalCount", dayLogs.size(),
                    "positiveRatio", Math.round(posCount / (double) dayLogs.size() * 1000.0) / 1000.0
            ));
        }

        return result;
    }

    private List<Map<String, Object>> buildAttractionDistribution(LocalDateTime start, LocalDateTime end) {
        List<InteractionLog> logs = logRepository.findByCreatedAtBetween(start, end);
        Map<String, Long> scoreMap = new HashMap<>();
        for (InteractionLog log : logs) {
            Map<String, Long> points = sentimentService.extractFocusPoints(log.getQuestion(), log.getAnswer());
            points.forEach((k, v) -> scoreMap.merge(k, v, Long::sum));
        }
        return scoreMap.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .map(e -> Map.<String, Object>of(
                        "name", e.getKey(),
                        "score", e.getValue()
                ))
                .collect(Collectors.toList());
    }
}
