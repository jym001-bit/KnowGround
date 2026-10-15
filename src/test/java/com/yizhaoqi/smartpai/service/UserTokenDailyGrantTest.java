package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.config.UsageQuotaProperties;
import com.yizhaoqi.smartpai.model.UserTokenRecord;
import com.yizhaoqi.smartpai.repository.UserDailyChatCountRepository;
import com.yizhaoqi.smartpai.repository.UserRepository;
import com.yizhaoqi.smartpai.repository.UserTokenRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserTokenDailyGrantTest {
    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> values;
    @Mock private UserTokenRecordRepository records;
    @Mock private UserDailyChatCountRepository chats;
    @Mock private UserRepository users;

    @Test
    void firstGrantCreditsBalanceAndRepeatedClaimDoesNotWriteSecondRecord() {
        LocalDate today = LocalDate.now();
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("user:token:llm:42")).thenReturn("100");
        when(records.sumConsumedByUserAndDay("42", today, UserTokenRecord.TokenType.LLM)).thenReturn(120L);
        when(redis.execute(any(RedisScript.class), anyList(), eq("300000"), eq("120"), eq("500000"), eq("259200")))
                .thenReturn("granted:300100", "duplicate");
        UserTokenService service = service();

        assertTrue(service.grantDailyTokens("42", today, UserTokenRecord.TokenType.LLM, 300_000, 500_000));
        assertFalse(service.grantDailyTokens("42", today, UserTokenRecord.TokenType.LLM, 300_000, 500_000));

        ArgumentCaptor<UserTokenRecord> capture = ArgumentCaptor.forClass(UserTokenRecord.class);
        verify(records, times(1)).save(capture.capture());
        assertEquals(300_000L, capture.getValue().getAmount());
        assertEquals("每日赠送", capture.getValue().getReason());
    }

    @Test
    void aboveThresholdSkipsCorrespondingGift() {
        LocalDate today = LocalDate.now();
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("user:token:embedding:42")).thenReturn("100");
        when(records.sumConsumedByUserAndDay("42", today, UserTokenRecord.TokenType.EMBEDDING)).thenReturn(300_001L);
        when(redis.execute(any(RedisScript.class), anyList(), eq("500000"), eq("300001"), eq("300000"), eq("259200")))
                .thenReturn("skipped");

        assertFalse(service().grantDailyTokens("42", today, UserTokenRecord.TokenType.EMBEDDING, 500_000, 300_000));
        verify(records, never()).save(any());
    }

    @Test
    void negativeBalanceAfterGrantStillCountsAsGranted() {
        LocalDate today = LocalDate.now();
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("user:token:llm:42")).thenReturn("-900000");
        when(records.sumConsumedByUserAndDay("42", today, UserTokenRecord.TokenType.LLM)).thenReturn(0L);
        when(redis.execute(any(RedisScript.class), anyList(), eq("300000"), eq("0"), eq("500000"), eq("259200")))
                .thenReturn("granted:-600000");

        assertTrue(service().grantDailyTokens("42", today, UserTokenRecord.TokenType.LLM, 300_000, 500_000));
        ArgumentCaptor<UserTokenRecord> capture = ArgumentCaptor.forClass(UserTokenRecord.class);
        verify(records).save(capture.capture());
        assertEquals(-900_000L, capture.getValue().getBalanceBefore());
        assertEquals(-600_000L, capture.getValue().getBalanceAfter());
    }

    private UserTokenService service() {
        return new UserTokenService(redis, new UsageQuotaProperties(), records, chats, users);
    }
}
