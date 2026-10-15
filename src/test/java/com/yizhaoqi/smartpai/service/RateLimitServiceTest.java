package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.config.RateLimitProperties;
import com.yizhaoqi.smartpai.exception.RateLimitExceededException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    @Mock private AtomicTokenBucket bucket;
    @Mock private RateLimitConfigService rateLimitConfig;
    @Mock private UsageQuotaService usageQuota;
    @Mock private QuotaExemptionService quotaExemptionService;

    @Test
    void eleventhQuestionIsAllowedWhenMinuteLimitIsNotReached() {
        RateLimitService service = new RateLimitService(
                new RateLimitProperties(), rateLimitConfig, usageQuota, quotaExemptionService, bucket
        );
        when(rateLimitConfig.getCurrentSettings()).thenReturn(new RateLimitConfigService.RateLimitSettingsView(
                new RateLimitConfigService.WindowLimitView(30, 60), null, null, null, null
        ));
        when(bucket.take("request:chat:user:42", 30, 0.5d, false)).thenReturn(0L);

        service.checkChatByUser("42");

        verify(usageQuota).recordChatRequest("42");
        verify(bucket, never()).take(startsWith("request:chat:day:user:"), anyDouble(), anyDouble(), anyBoolean());
    }

    @Test
    void minuteChatLimitStillBlocks() {
        RateLimitService service = new RateLimitService(
                new RateLimitProperties(), rateLimitConfig, usageQuota, quotaExemptionService, bucket
        );
        when(rateLimitConfig.getCurrentSettings()).thenReturn(new RateLimitConfigService.RateLimitSettingsView(
                new RateLimitConfigService.WindowLimitView(30, 60), null, null, null, null
        ));
        when(bucket.take("request:chat:user:42", 30, 0.5d, false)).thenReturn(1000L);

        assertThrows(RateLimitExceededException.class, () -> service.checkChatByUser("42"));

        verify(usageQuota, never()).recordChatRequest("42");
    }

    @Test
    void embeddingQueryHasNoDailyRequestLimit() {
        RateLimitService service = new RateLimitService(
                new RateLimitProperties(), rateLimitConfig, usageQuota, quotaExemptionService, bucket
        );
        when(rateLimitConfig.getCurrentSettings()).thenReturn(new RateLimitConfigService.RateLimitSettingsView(
                null, null, null,
                new RateLimitConfigService.DualWindowLimitView(60, 60, 1, 86400), null
        ));
        when(bucket.take("request:embedding:query:min:user:42", 60, 1d, false)).thenReturn(0L);

        service.checkEmbeddingQueryByUser("42");

        verify(bucket, never()).take(startsWith("request:embedding:query:day:user:"), anyDouble(), anyDouble(), anyBoolean());
    }

    @Test
    void adminQuestionSkipsMinuteLimits() {
        RateLimitService service = new RateLimitService(
                new RateLimitProperties(), rateLimitConfig, usageQuota, quotaExemptionService, bucket
        );
        when(quotaExemptionService.isExempt("1")).thenReturn(true);

        service.checkChatByUser("1");
        service.checkEmbeddingQueryByUser("1");

        verifyNoInteractions(bucket);
        verify(usageQuota, never()).recordChatRequest("1");
    }
}
