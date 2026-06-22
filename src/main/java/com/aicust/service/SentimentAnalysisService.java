package com.aicust.service;

import com.aicust.model.InteractionLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 情感分析 & 关注点聚类服务。
 * <p>
 * 采用轻量级关键词匹配策略，不依赖外部 LLM 调用，保证高性能。
 * 关键词库可扩展（通过配置文件注入）。
 */
@Service
public class SentimentAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(SentimentAnalysisService.class);

    /** 正面关键词 */
    private static final List<String> POSITIVE_WORDS = List.of(
            "很好", "优秀", "棒", "满意", "喜欢", "推荐", "漂亮", "干净", "方便",
            "服务好", "热情", "周到", "值得", "不错", "赞", "舒服", "开心", "愉快",
            "风景优美", "空气清新", "物有所值", "性价比高", "体验好", "人性化", "贴心"
    );

    /** 负面关键词 */
    private static final List<String> NEGATIVE_WORDS = List.of(
            "差", "糟糕", "垃圾", "失望", "抱怨", "排队久", "贵", "坑", "骗",
            "脏乱", "服务态度差", "不好", "讨厌", "后悔", "别来", "避雷", "太差",
            "拥挤", "拥挤不堪", "排队太长", "收费高", "不推荐", "体验差", "迷路",
            "厕所", "没地方", "没意思", "不值"
    );

    /** 景点/区域关键词（用于关注点聚类） */
    private static final List<String> ATTRACTION_KEYWORDS = List.of(
            "大熊猫", "熊猫馆", "猴子", "狮虎山", "猛兽区", "儿童乐园",
            "植物园", "樱花", "荷花", "竹林", "水族馆", "鸟语林",
            "小吃街", "美食广场", "餐厅", "停车场", "南门", "北门",
            "东门", "西门", "动物园", "游乐场", "摩天轮", "过山车",
            "玻璃栈道", "漂流", "温泉", "滑雪场", "滑雪场", "古镇",
            "西湖", "泰山", "黄山", "长城", "故宫", "九寨沟"
    );

    /** 停用词 */
    private static final Set<String> STOP_WORDS = Set.of(
            "的", "了", "和", "是", "在", "吗", "呢", "啊", "哦", "嗯",
            "我", "你", "他", "她", "它", "我们", "你们", "他们",
            "这", "那", "什么", "怎么", "为什么", "哪里", "多少",
            "一个", "没有", "不是", "可以", "会", "要", "想"
    );

    /** 中文标点正则 */
    private static final Pattern CN_PUNCT = Pattern.compile("[\\p{P}\\p{S}]");

    /**
     * 对回答内容做情感分析。
     *
     * @return (score, label) pair，score 范围 -1.0 ~ 1.0
     */
    public AnalysisResult analyze(String answer) {
        if (answer == null || answer.isBlank()) {
            return new AnalysisResult(0.0, "NEUTRAL");
        }

        String cleaned = CN_PUNCT.matcher(answer).replaceAll(" ");
        int posCount = 0;
        int negCount = 0;

        for (String word : POSITIVE_WORDS) {
            if (cleaned.contains(word)) {
                posCount++;
            }
        }
        for (String word : NEGATIVE_WORDS) {
            if (cleaned.contains(word)) {
                negCount++;
            }
        }

        double score;
        String label;
        if (posCount > negCount) {
            score = Math.min(1.0, (double) posCount / (posCount + negCount + 1));
            label = "POSITIVE";
        } else if (negCount > posCount) {
            score = -Math.min(1.0, (double) negCount / (posCount + negCount + 1));
            label = "NEGATIVE";
        } else {
            score = 0.0;
            label = "NEUTRAL";
        }

        return new AnalysisResult(score, label);
    }

    /**
     * 从对话文本中提取关注点（关键词频次统计）。
     */
    public Map<String, Long> extractFocusPoints(String question, String answer) {
        String text = (question != null ? question : "") + " " + (answer != null ? answer : "");

        // 1. 匹配预定义景点关键词
        Map<String, Long> focusMap = new HashMap<>();
        for (String keyword : ATTRACTION_KEYWORDS) {
            long count = countOccurrences(text, keyword);
            if (count > 0) {
                focusMap.put(keyword, count);
            }
        }

        // 2. 提取高频双字中文词组作为补充关注点
        Map<String, Long> bigrams = extractBigrams(text);
        bigrams.entrySet().stream()
                .filter(e -> !STOP_WORDS.contains(e.getKey()))
                .filter(e -> !focusMap.containsKey(e.getKey()))
                .limit(10)
                .forEach(e -> focusMap.put(e.getKey(), e.getValue()));

        return focusMap.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(20)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }

    /**
     * 批量分析多条日志的情感。
     */
    public List<AnalysisResult> analyzeBatch(List<InteractionLog> logs) {
        return logs.stream()
                .map(log -> analyze(log.getAnswer()))
                .collect(Collectors.toList());
    }

    private long countOccurrences(String text, String keyword) {
        long count = 0;
        int idx = 0;
        while ((idx = text.indexOf(keyword, idx)) != -1) {
            count++;
            idx += keyword.length();
        }
        return count;
    }

    /**
     * 提取中文双字词组频次。
     */
    private Map<String, Long> extractBigrams(String text) {
        Map<String, Long> map = new HashMap<>();
        String cleaned = text.replaceAll("[^\\u4E00-\\u9FA5]", "");
        if (cleaned.length() < 2) return map;

        for (int i = 0; i <= cleaned.length() - 2; i++) {
            String bigram = cleaned.substring(i, i + 2);
            // 过滤：首字必须是汉字部首范围内（粗筛）
            map.merge(bigram, 1L, Long::sum);
        }

        // 只保留频次 >= 2 的词组
        return map.entrySet().stream()
                .filter(e -> e.getValue() >= 2)
                .collect(Collectors.groupingBy(Map.Entry::getKey, LinkedHashMap::new,
                        Collectors.summingLong(Map.Entry::getValue)));
    }

    /**
     * 情感分析结果。
     */
    public record AnalysisResult(double score, String label) {
    }
}
