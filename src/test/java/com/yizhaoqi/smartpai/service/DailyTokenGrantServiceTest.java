package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.config.UsageQuotaProperties;
import com.yizhaoqi.smartpai.model.User;
import com.yizhaoqi.smartpai.model.UserTokenRecord;
import com.yizhaoqi.smartpai.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyTokenGrantServiceTest {
    @Mock private UserRepository users;
    @Mock private UserTokenService tokens;

    @Test
    void regularUserReceivesIndependentDailyGrantsWithCorrectThresholds() {
        UsageQuotaProperties properties = enabledProperties();
        User user = new User();
        user.setRole(User.Role.USER);
        when(users.findById(42L)).thenReturn(Optional.of(user));

        new DailyTokenGrantService(properties, users, tokens).grantIfEligible("42");

        verify(tokens).grantDailyTokens("42", LocalDate.now(), UserTokenRecord.TokenType.LLM, 800_000, 500_000);
        verify(tokens).grantDailyTokens("42", LocalDate.now(), UserTokenRecord.TokenType.EMBEDDING, 500_000, 300_000);
    }

    @Test
    void administratorAndSystemDoNotReceiveDailyGrants() {
        UsageQuotaProperties properties = enabledProperties();
        User admin = new User();
        admin.setRole(User.Role.ADMIN);
        when(users.findById(1L)).thenReturn(Optional.of(admin));
        DailyTokenGrantService grants = new DailyTokenGrantService(properties, users, tokens);

        grants.grantIfEligible("1");
        grants.grantIfEligible("system");

        verify(tokens, never()).grantDailyTokens(any(), any(), any(), anyLong(), anyLong());
    }

    private UsageQuotaProperties enabledProperties() {
        UsageQuotaProperties properties = new UsageQuotaProperties();
        properties.setUseUserTokenBalance(true);
        properties.getDailyGrant().setEnabled(true);
        return properties;
    }
}
