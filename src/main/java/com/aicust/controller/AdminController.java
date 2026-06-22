package com.aicust.controller;

import com.aicust.dto.SentimentReport;
import com.aicust.dto.ServiceDashboard;
import com.aicust.repository.InteractionLogRepository;
import com.aicust.service.ReportService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * 管理后台接口 — 报表查询、满意度评价等。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final ReportService reportService;
    private final InteractionLogRepository logRepository;

    public AdminController(ReportService reportService, InteractionLogRepository logRepository) {
        this.reportService = reportService;
        this.logRepository = logRepository;
    }

    /**
     * 服务看板 — 当日/本周服务人次、热门问答、满意度趋势、景点关注度。
     */
    @GetMapping("/dashboard")
    public ServiceDashboard getDashboard() {
        return reportService.getDashboard();
    }

    /**
     * 情感分析报告。
     *
     * @param days 回溯天数，默认 7
     */
    @GetMapping("/report/sentiment")
    public SentimentReport getSentimentReport(@RequestParam(defaultValue = "7") int days) {
        return reportService.getSentimentReport(days);
    }

    /**
     * 提交满意度评价。
     *
     * @param logId   交互日志ID
     * @param score   评分 1~5
     */
    @Transactional
    @PostMapping("/satisfaction")
    public String submitSatisfaction(@RequestParam Long logId,
                                     @RequestParam int score) {
        if (score < 1 || score > 5) {
            throw new IllegalArgumentException("满意度评分必须在 1~5 之间");
        }
        int updated = logRepository.updateSatisfaction(logId, score);
        if (updated == 0) {
            throw new IllegalArgumentException("交互日志不存在: " + logId);
        }
        return String.format("Satisfaction recorded: logId=%d, score=%d", logId, score);
    }
}
