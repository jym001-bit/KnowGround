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
import static org.mockito.Mockito.never;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;
import com.yizhaoqi.smartpai.model.UserTokenRecord;
import java.time.LocalDate;

@ExtendWith(MockitoExtension.class)
class UsageBalanceQuotaServiceTest {

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> values;
    @Mock private UserTokenService userTokens;
    @Mock private QuotaExemptionService quotaExemptionService;

    private UsageBalanceQuotaService quota;

    @BeforeEach
    void setUp() {
        UsageQuotaProperties properties = new UsageQuotaProperties();
        properties.getLlm().setDayMaxTokens(1_000);
        quota = new UsageBalanceQuotaService(redis, properties, userTokens, quotaExemptionService);
    }

    @Test
    void balanceModeRejectsDailyTokenLimitBeforeCallingProvider() {
        when(redis.opsForValue()).thenReturn(values);
        when(userTokens.hasEnoughLlmTokens("42", 1_100)).thenReturn(true);
        when(values.increment(anyString(), eq(1_100L))).thenReturn(1_100L);
        when(redis.expire(anyString(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);

        assertThrows(RateLimitExceededException.class, () -> quota.reserveLlmTokens("42", 100, 1_000));

        verify(values).increment(anyString(), eq(-1_100L));
    }

    @Test
    void balanceModeSettlesDailyUsageAndLifetimeBalance() {
        when(redis.opsForValue()).thenReturn(values);
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

    @Test
    void adminDoesNotRequireOrConsumeTokenBalance() {
        when(quotaExemptionService.isExempt("1")).thenReturn(true);

        UsageQuotaService.TokenReservation reservation = quota.reserveLlmTokens("1", 100, 1000);
        quota.settleReservation(reservation, 500);

        assertTrue(reservation.noop());
        verify(userTokens, never()).hasEnoughLlmTokens("1", 1100);
        verify(userTokens, never()).consumeLlmTokens("1", 500);
    }

    @Test
    void disabledDailyQuotaStillChargesBalanceWithoutWritingEmptyRedisKey() {
        quota.properties.getLlm().setEnabled(false);
        when(userTokens.hasEnoughLlmTokens("42", 800)).thenReturn(true);

        UsageQuotaService.TokenReservation reservation = quota.reserveLlmTokens("42", 100, 700);
        assertFalse(reservation.noop());
        quota.abortReservation(reservation);
        quota.settleReservation(reservation, 600);

        verify(userTokens).consumeLlmTokens("42", 600);
        verifyNoInteractions(redis);
    }

    @Test
    void adminUsageIsRecordedButBalanceIsNotCharged() {
        UsageQuotaService.TokenReservationBundle reservation = quota.reserveExemptMetering("llm", "1");
        quota.settleReservation(reservation, 500);

        verify(userTokens).recordExemptUsage("1", UserTokenRecord.TokenType.LLM, 500);
        verify(userTokens, never()).consumeLlmTokens("1", 500);
    }

    @Test
    void snapshotsShowTodaysConsumptionEvenWhenDailyCapIsDisabled() {
        quota.properties.getLlm().setEnabled(false);
        when(userTokens.getConsumedOnDay("42", LocalDate.now(), UserTokenRecord.TokenType.LLM)).thenReturn(123L);
        when(userTokens.getRequestCountOnDay("42", LocalDate.now(), UserTokenRecord.TokenType.LLM)).thenReturn(2L);
        when(userTokens.getLlmTokenBalance("42")).thenReturn(900L);

        UsageQuotaService.QuotaView view = quota.getSnapshot("42").llm();

        assertFalse(view.enabled());
        assertEquals(123L, view.usedTokens());
        assertEquals(900L, view.remainingTokens());
        assertEquals(2L, view.requestCount());
    }

    @Test
    void adminSnapshotShowsMeteredTokensWithoutBalance() {
        when(quotaExemptionService.isExempt("1")).thenReturn(true);
        when(userTokens.getConsumedOnDay("1", LocalDate.now(), UserTokenRecord.TokenType.LLM)).thenReturn(321L);

        UsageQuotaService.QuotaView view = quota.getSnapshot("1").llm();

        assertEquals(321L, view.usedTokens());
        assertEquals(0L, view.remainingTokens());
        verify(userTokens, never()).getLlmTokenBalance("1");
    }
}
