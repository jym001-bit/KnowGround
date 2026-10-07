package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.config.RateLimitProperties;
import com.yizhaoqi.smartpai.config.UsageQuotaProperties;
import com.yizhaoqi.smartpai.exception.RateLimitExceededException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> values;
    @Mock private RateLimitConfigService rateLimitConfig;
    @Mock private UsageQuotaService usageQuota;

    @Test
    void eleventhQuestionIsBlockedAndDoesNotRecordAcceptedQuestion() {
        UsageQuotaProperties quotaProperties = new UsageQuotaProperties();
        quotaProperties.setChatDayMaxRequests(10);
        RateLimitService service = new RateLimitService(
                redis, new RateLimitProperties(), rateLimitConfig, usageQuota, quotaProperties
        );
        when(rateLimitConfig.getCurrentSettings()).thenReturn(new RateLimitConfigService.RateLimitSettingsView(
                new RateLimitConfigService.WindowLimitView(30, 60), null, null, null, null
        ));
        when(redis.opsForValue()).thenReturn(values);
        when(values.increment("chat:user:42")).thenReturn(1L);
        when(values.increment(startsWith("chat:day:user:42:"))).thenReturn(11L);

        assertThrows(RateLimitExceededException.class, () -> service.checkChatByUser("42"));

        verify(values).decrement(startsWith("chat:day:user:42:"));
        verify(usageQuota, never()).recordChatRequest("42");
    }
}
