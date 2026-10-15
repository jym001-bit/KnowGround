package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.config.UsageQuotaProperties;
import com.yizhaoqi.smartpai.exception.RateLimitExceededException;
import com.yizhaoqi.smartpai.model.DailyReqCountStat;
import com.yizhaoqi.smartpai.model.DailyUsageStat;
import com.yizhaoqi.smartpai.model.UserTokenRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class UsageBalanceQuotaService extends UsageQuotaService {

    private static final Logger logger = LoggerFactory.getLogger(UsageBalanceQuotaService.class);
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private final UserTokenService userTokenService;

    public UsageBalanceQuotaService(StringRedisTemplate stringRedisTemplate,
                                    UsageQuotaProperties properties,
                                    UserTokenService userTokenService,
                                    QuotaExemptionService quotaExemptionService
    ) {
        super(stringRedisTemplate, properties, quotaExemptionService);
        this.userTokenService = userTokenService;
    }

    public TokenReservation reserveLlmTokens(String userId, int estimatedPromptTokens, int maxCompletionTokens) {
        if (!isQuotaManaged(userId)) {
            return TokenReservation.noop("llm", userId);
        }

        int reserveTokens = Math.max(estimatedPromptTokens, 0) + Math.max(maxCompletionTokens, 0);
        reserveTokens = Math.max(reserveTokens, 1);

        // 检查用户余额是否充足
        if (!userTokenService.hasEnoughLlmTokens(userId, reserveTokens)) {
            Long balance = userTokenService.getLlmTokenBalance(userId);
            throw new com.yizhaoqi.smartpai.exception.InsufficientBalanceException(
                    "LLM Token 余额不足，预估需要：" + reserveTokens + ", 当前余额：" + balance);
        }

        // 同时预留当日额度；结算时再按实际用量扣除总余额。
        TokenReservation dailyReservation = super.reserveLlmTokens(userId, estimatedPromptTokens, maxCompletionTokens);
        return dailyReservation.noop() ? balanceOnlyReservation("llm", userId, reserveTokens) : dailyReservation;
    }

    public TokenReservation reserveEmbeddingTokens(String userId, List<String> texts) {
        if (!isQuotaManaged(userId)) {
            return TokenReservation.noop("embedding", userId);
        }

        int estimatedTokens = Math.max(estimateEmbeddingTokens(texts), 1);

        // 检查用户余额是否充足
        if (!userTokenService.hasEnoughEmbeddingTokens(userId, estimatedTokens)) {
            Long balance = userTokenService.getEmbeddingTokenBalance(userId);
            throw new com.yizhaoqi.smartpai.exception.InsufficientBalanceException(
                    "Embedding Token 余额不足，预估需要：" + estimatedTokens + ", 当前余额：" + balance);
        }

        TokenReservation dailyReservation = super.reserveEmbeddingTokens(userId, texts);
        return dailyReservation.noop() ? balanceOnlyReservation("embedding", userId, estimatedTokens) : dailyReservation;
    }

    private TokenReservation balanceOnlyReservation(String scope, String userId, int reserveTokens) {
        return new TokenReservation(scope, userId, "", "", reserveTokens, reserveTokens, 0, false, true);
    }

    @Override
    public TokenReservationBundle reserveExemptMetering(String scope, String userId) {
        if (userId == null || userId.startsWith("system")) return TokenReservationBundle.noop(scope, userId);
        TokenReservation meter = new TokenReservation("meter-" + scope, userId, "", "", 0, 0, 0, false, false);
        return TokenReservationBundle.of(scope, userId, List.of(meter));
    }

    /**
     * 记录用户聊天请求次数
     * @param userId
     */
    @Transactional(rollbackFor = Exception.class)
    public void recordChatRequest(String userId) {
        if (!isQuotaManaged(userId)) {
            return;
        }

        userTokenService.updateUserDailyChatCount(userId, LocalDate.now());
    }

    /**
     * 结算用户的token消耗
     *
     * @param reservation
     * @param actualTokens
     */
    @Transactional(rollbackFor = Exception.class)
    public void settleReservation(TokenReservation reservation, int actualTokens) {
        if (reservation == null || reservation.noop()) {
            return;
        }

        if (reservation.scope().startsWith("meter-")) {
            UserTokenRecord.TokenType type = "meter-llm".equals(reservation.scope())
                    ? UserTokenRecord.TokenType.LLM : UserTokenRecord.TokenType.EMBEDDING;
            userTokenService.recordExemptUsage(reservation.userId(), type, actualTokens);
            return;
        }

        if (reservation.quotaKey().isBlank()) {
            if (actualTokens <= 0) {
                return;
            }

            // 结算用户的token消耗
            try {
                // 更新总的请求次数
                userTokenService.incrementUserTotalRequestCount(reservation.scope(), reservation.userId());

                // 根据消耗token扣减用户余额
                if ("llm".equals(reservation.scope())) {
                    userTokenService.consumeLlmTokens(reservation.userId(), actualTokens);
                    logger.info("用户 {} 结算 LLM Token: {}, 剩余额度从预留中扣减",
                            reservation.userId(), actualTokens);
                } else if ("embedding".equals(reservation.scope())) {
                    userTokenService.consumeEmbeddingTokens(reservation.userId(), actualTokens);
                    logger.info("用户 {} 结算 Embedding Token: {}, 剩余额度从预留中扣减",
                            reservation.userId(), actualTokens);
                }
            } catch (Exception e) {
                logger.error("结算用户 Token 失败：userId={}, scope={}, tokens={}",
                        reservation.userId(), reservation.scope(), actualTokens, e);
                throw e;
            }
        } else {
            super.settleReservation(reservation, actualTokens);
            if (actualTokens > 0 && "llm".equals(reservation.scope())) {
                userTokenService.consumeLlmTokens(reservation.userId(), actualTokens);
            } else if (actualTokens > 0 && "embedding".equals(reservation.scope())) {
                userTokenService.consumeEmbeddingTokens(reservation.userId(), actualTokens);
            }
        }
    }

    @Override
    public void abortReservation(TokenReservation reservation) {
        if (reservation != null && reservation.quotaKey().isBlank()) return;
        super.abortReservation(reservation);
    }


    /**
     * 获取用户使用情况快照
     * @param userIds
     * @return
     */
    public Map<String, UserUsageSnapshot> getSnapshots(List<String> userIds) {
        Map<String, UserUsageSnapshot> result = new LinkedHashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return result;
        }

        for (String userId : userIds) {
            result.put(userId, new UserUsageSnapshot(
                    currentDay(),
                    userTokenService.getUserDailyChatCount(userId, LocalDate.now()),
                    buildQuotaView("llm", userId, properties.getLlm()),
                    buildQuotaView("embedding", userId, properties.getEmbedding())
            ));
        }
        return result;
    }

    /**
     * 获取用户额度视图
     * @param scope
     * @param userId
     * @param quota
     * @return
     */
    private QuotaView buildQuotaView(String scope, String userId, UsageQuotaProperties.DailyTokenQuota quota) {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        UserTokenRecord.TokenType type = "llm".equals(scope)
                ? UserTokenRecord.TokenType.LLM : UserTokenRecord.TokenType.EMBEDDING;
        long usedToday = userTokenService.getConsumedOnDay(userId, today, type);
        long requestsToday = userTokenService.getRequestCountOnDay(userId, today, type);
        if (quotaExemptionService.isExempt(userId)) {
            return new QuotaView(false, usedToday, 0, 0, requestsToday);
        }
        long balance = type == UserTokenRecord.TokenType.LLM
                ? userTokenService.getLlmTokenBalance(userId) : userTokenService.getEmbeddingTokenBalance(userId);
        return new QuotaView(quota.isEnabled(), usedToday,
                quota.isEnabled() ? quota.getDayMaxTokens() : 0, balance, requestsToday);
    }

    private String currentDay() {
        return ZonedDateTime.now(ZoneId.systemDefault()).format(DAY_FORMATTER);
    }

    private boolean isQuotaManaged(String userId) {
        return userId != null && !userId.isBlank() && !quotaExemptionService.isExempt(userId);
    }


    /**
     * 用于后台统计每天的使用量
     *
     * @param userIds
     * @param days
     * @return
     */
    public List<DailyUsageAggregate> getDailyAggregates(List<String> userIds, int days) {
        int normalizedDays = Math.max(1, Math.min(days, properties.getRetentionDays()));
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        LocalDate startDay = today.minusDays(normalizedDays - 1);

        // 查询所有用户，在这个时间段内的数据统计
        List<DailyUsageStat> llmStat = userTokenService.getDailyStatsByType(startDay, today, UserTokenRecord.TokenType.LLM);
        List<DailyUsageStat> embeddingStat = userTokenService.getDailyStatsByType(startDay, today, UserTokenRecord.TokenType.EMBEDDING);

        List<DailyReqCountStat> reqCountStat = userTokenService.getDailyReqCountStats(startDay, today);

        // 将上面的数据，聚合成结果返回，需要开始时间到结束时间，如果中间缺了日期，则需要自动补全
        List<DailyUsageAggregate> result = new ArrayList<>();

        // 构建日期到统计数据的映射
        java.util.Map<LocalDate, DailyUsageStat> llmStatMap = llmStat.stream()
                .collect(java.util.stream.Collectors.toMap(
                        DailyUsageStat::recordDate,
                        stat -> stat,
                        (existing, replacement) -> existing
                ));

        java.util.Map<LocalDate, DailyUsageStat> embeddingStatMap = embeddingStat.stream()
                .collect(java.util.stream.Collectors.toMap(
                        DailyUsageStat::recordDate,
                        stat -> stat,
                        (existing, replacement) -> existing
                ));

        java.util.Map<LocalDate, DailyReqCountStat> reqCountStatMap = reqCountStat.stream()
                .collect(java.util.stream.Collectors.toMap(
                        DailyReqCountStat::recordDate,
                        stat -> stat,
                        (existing, replacement) -> existing
                ));

        // 遍历所有日期，生成聚合结果
        for (LocalDate day = startDay; !day.isAfter(today); day = day.plusDays(1)) {
            String dayString = day.format(DAY_FORMATTER);

            DailyUsageStat llmStatForDay = llmStatMap.get(day);
            DailyUsageStat embeddingStatForDay = embeddingStatMap.get(day);
            DailyReqCountStat reqCountStatForDay = reqCountStatMap.get(day);

            result.add(new DailyUsageAggregate(
                    dayString,
                    reqCountStatForDay != null ? reqCountStatForDay.totalRequestCount() : 0L,
                    llmStatForDay != null ? llmStatForDay.totalAmount() : 0L,
                    llmStatForDay != null ? llmStatForDay.totalRequestCount() : 0L,
                    embeddingStatForDay != null ? embeddingStatForDay.totalAmount() : 0L,
                    embeddingStatForDay != null ? embeddingStatForDay.totalRequestCount() : 0L
            ));
        }

        return result;
    }
}
