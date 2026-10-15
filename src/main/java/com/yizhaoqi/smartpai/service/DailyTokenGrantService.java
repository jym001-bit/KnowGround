package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.config.UsageQuotaProperties;
import com.yizhaoqi.smartpai.model.User;
import com.yizhaoqi.smartpai.model.UserTokenRecord;
import com.yizhaoqi.smartpai.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;

/** 首次登录或使用时按当天实际消耗判断，两种 Token 各自每天最多赠送一次。 */
@Service
public class DailyTokenGrantService {
    private final UsageQuotaProperties properties;
    private final UserRepository users;
    private final UserTokenService tokens;

    public DailyTokenGrantService(UsageQuotaProperties properties, UserRepository users, UserTokenService tokens) {
        this.properties = properties;
        this.users = users;
        this.tokens = tokens;
    }

    public void grantIfEligible(String userId) {
        if (!properties.isUseUserTokenBalance() || !properties.getDailyGrant().isEnabled()) return;
        long id;
        try {
            id = Long.parseLong(userId);
        } catch (NumberFormatException e) {
            return; // 系统任务没有用户余额，也不领取每日赠送。
        }
        if (users.findById(id).map(User::getRole).orElse(null) != User.Role.USER) return;

        UsageQuotaProperties.DailyGrant grant = properties.getDailyGrant();
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        tokens.grantDailyTokens(userId, today, UserTokenRecord.TokenType.LLM,
                grant.getLlmTokens(), grant.getLlmSkipAbove());
        tokens.grantDailyTokens(userId, today, UserTokenRecord.TokenType.EMBEDDING,
                grant.getEmbeddingTokens(), grant.getEmbeddingSkipAbove());
    }
}
