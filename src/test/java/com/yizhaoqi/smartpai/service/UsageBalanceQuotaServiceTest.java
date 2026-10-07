package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.config.UsageQuotaProperties;
import com.yizhaoqi.smartpai.exception.RateLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsageBalanceQuotaServiceTest {

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> values;
    @Mock private UserTokenService userTokens;

    private UsageBalanceQuotaService quota;

    @BeforeEach
    void setUp() {
        UsageQuotaProperties properties = new UsageQuotaProperties();
        properties.getLlm().setDayMaxTokens(1_000);
        quota = new UsageBalanceQuotaService(redis, properties, userTokens);
        when(redis.opsForValue()).thenReturn(values);
    }

    @Test
    void balanceModeRejectsDailyTokenLimitBeforeCallingProvider() {
        when(userTokens.hasEnoughLlmTokens("42", 1_100)).thenReturn(true);
        when(values.increment(anyString(), eq(1_100L))).thenReturn(1_100L);
        when(redis.expire(anyString(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);

        assertThrows(RateLimitExceededException.class, () -> quota.reserveLlmTokens("42", 100, 1_000));

        verify(values).increment(anyString(), eq(-1_100L));
    }

    @Test
    void balanceModeSettlesDailyUsageAndLifetimeBalance() {
        when(userTokens.hasEnoughLlmTokens("42", 800)).thenReturn(true);
        when(values.increment(anyString(), eq(800L))).thenReturn(800L);
        when(redis.expire(anyString(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);

        UsageQuotaService.TokenReservation reservation = quota.reserveLlmTokens("42", 100, 700);
        when(values.increment(anyString(), eq(-200L))).thenReturn(600L);
        when(values.increment(anyString(), eq(1L))).thenReturn(1L);

        quota.settleReservation(reservation, 600);

        verify(values).increment(anyString(), eq(-200L));
        verify(userTokens).consumeLlmTokens("42", 600);
    }
}
