package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.config.RateLimitProperties;
import com.yizhaoqi.smartpai.exception.RateLimitExceededException;
import org.springframework.stereotype.Service;


@Service
public class RateLimitService {

    private final AtomicTokenBucket bucket;
    private final RateLimitProperties properties;
    private final RateLimitConfigService rateLimitConfigService;
    private final UsageQuotaService usageQuotaService;
    private final QuotaExemptionService quotaExemptionService;

    public RateLimitService(
            RateLimitProperties properties,
            RateLimitConfigService rateLimitConfigService,
            UsageQuotaService usageQuotaService,
            QuotaExemptionService quotaExemptionService,
            AtomicTokenBucket bucket
    ) {
        this.bucket = bucket;
        this.properties = properties;
        this.rateLimitConfigService = rateLimitConfigService;
        this.usageQuotaService = usageQuotaService;
        this.quotaExemptionService = quotaExemptionService;
    }

    public void checkRegisterByIp(String ip) {
        checkSingleWindow("register:ip:" + ip, properties.getRegister().getMax(), properties.getRegister().getWindowSeconds(), "注册请求过于频繁");
    }

    public void checkLoginByIp(String ip) {
        checkSingleWindow("login:ip:" + ip, properties.getLogin().getMax(), properties.getLogin().getWindowSeconds(), "登录请求过于频繁");
    }

    public void checkChatByUser(String userId) {
        if (quotaExemptionService.isExempt(userId)) {
            return;
        }
        RateLimitConfigService.WindowLimitView limit = rateLimitConfigService.getCurrentSettings().chatMessage();
        checkSingleWindow("chat:user:" + userId, limit.max(), limit.windowSeconds(), "聊天请求过于频繁");
        usageQuotaService.recordChatRequest(userId);
    }

    public UsageQuotaService.TokenReservationBundle reserveLlmUsage(
            String userId,
            int estimatedPromptTokens,
            int maxCompletionTokens
    ) {
        if (quotaExemptionService.isExempt(userId)) {
            return UsageQuotaService.TokenReservationBundle.noop("llm", userId);
        }
        RateLimitConfigService.TokenBudgetView limit = rateLimitConfigService.getCurrentSettings().llmGlobalToken();
        return usageQuotaService.reserveLlmTokensWithGlobalBudget(
                userId,
                estimatedPromptTokens,
                maxCompletionTokens,
                limit.minuteMax(),
                limit.minuteWindowSeconds(),
                limit.dayMax(),
                limit.dayWindowSeconds()
        );
    }

    public void checkEmbeddingQueryByUser(String userId) {
        if (quotaExemptionService.isExempt(userId)) {
            return;
        }
        RateLimitConfigService.DualWindowLimitView limit = rateLimitConfigService.getCurrentSettings().embeddingQueryRequest();
        checkSingleWindow("embedding:query:min:user:" + userId, limit.minuteMax(), limit.minuteWindowSeconds(), "Embedding查询过于频繁");
    }

    public UsageQuotaService.TokenReservationBundle reserveEmbeddingUploadUsage(String userId, java.util.List<String> texts) {
        if (quotaExemptionService.isExempt(userId)) {
            return UsageQuotaService.TokenReservationBundle.noop("embedding-upload", userId);
        }
        RateLimitConfigService.TokenBudgetView limit = rateLimitConfigService.getCurrentSettings().embeddingUploadToken();
        return usageQuotaService.reserveEmbeddingTokensWithGlobalBudget(
                userId,
                texts,
                "embedding-upload",
                "Embedding上传全网分钟Token预算已达上限",
                "Embedding上传全网当日Token预算已达上限",
                limit.minuteMax(),
                limit.minuteWindowSeconds(),
                limit.dayMax(),
                limit.dayWindowSeconds()
        );
    }

    public UsageQuotaService.TokenReservationBundle reserveEmbeddingQueryUsage(String userId, java.util.List<String> texts) {
        if (quotaExemptionService.isExempt(userId)) {
            return UsageQuotaService.TokenReservationBundle.noop("embedding-query", userId);
        }
        checkEmbeddingQueryByUser(userId);
        RateLimitConfigService.TokenBudgetView limit = rateLimitConfigService.getCurrentSettings().embeddingQueryGlobalToken();
        return usageQuotaService.reserveEmbeddingTokensWithGlobalBudget(
                userId,
                texts,
                "embedding-query",
                "Embedding查询全网分钟Token预算已达上限",
                "Embedding查询全网当日Token预算已达上限",
                limit.minuteMax(),
                limit.minuteWindowSeconds(),
                limit.dayMax(),
                limit.dayWindowSeconds()
        );
    }

    private void checkSingleWindow(String key, long max, long windowSeconds, String message) {
        if (max <= 0 || windowSeconds <= 0) throw new RateLimitExceededException(message, windowSeconds);
        long waitMillis = bucket.take("request:" + key, max, (double) max / windowSeconds, false);
        if (waitMillis > 0) throw new RateLimitExceededException(message, Math.max(1, (waitMillis + 999) / 1000));
    }
}
