package com.aicust.repository;

import com.aicust.model.InteractionLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface InteractionLogRepository extends JpaRepository<InteractionLog, Long> {

    /** 查询指定时间段内的所有交互日志 */
    List<InteractionLog> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    /** 按满意度分组统计 */
    @Query("SELECT i.satisfaction, COUNT(i) FROM InteractionLog i " +
           "WHERE i.createdAt BETWEEN :start AND :end GROUP BY i.satisfaction ORDER BY i.satisfaction")
    List<Object[]> countBySatisfactionInRange(@Param("start") LocalDateTime start,
                                              @Param("end") LocalDateTime end);

    /** 热门问题 TopN（按被评价次数降序） */
    @Query("SELECT i.question, COUNT(i) FROM InteractionLog i " +
           "WHERE i.createdAt BETWEEN :start AND :end " +
           "GROUP BY i.question ORDER BY COUNT(i) DESC")
    List<Object[]> topQuestionsInRange(@Param("start") LocalDateTime start,
                                       @Param("end") LocalDateTime end,
                                       Pageable pageable);

    /** 用户最近交互日志 */
    List<InteractionLog> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** 更新满意度评分 */
    @Modifying
    @Query("UPDATE InteractionLog i SET i.satisfaction = :score WHERE i.id = :id")
    int updateSatisfaction(@Param("id") Long id, @Param("score") Integer score);

    /** 用户当日对话次数 */
    long countByUserIdAndCreatedAtBetween(Long userId, LocalDateTime start, LocalDateTime end);

    /** 当日总对话数 */
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    /** 本周（周一到周日）每天的对话数 */
    @Query("SELECT FUNCTION('DATE', i.createdAt), COUNT(i) FROM InteractionLog i " +
           "WHERE i.createdAt BETWEEN :start AND :end GROUP BY FUNCTION('DATE', i.createdAt) " +
           "ORDER BY FUNCTION('DATE', i.createdAt)")
    List<Object[]> dailyCountInRange(@Param("start") LocalDateTime start,
                                     @Param("end") LocalDateTime end);
}
