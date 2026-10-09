package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.model.User;
import com.yizhaoqi.smartpai.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuotaExemptionServiceTest {
    @Test
    void onlyAdministratorRoleIsExempt() {
        UserRepository users = mock(UserRepository.class);
        User admin = new User();
        admin.setRole(User.Role.ADMIN);
        User member = new User();
        member.setRole(User.Role.USER);
        when(users.findById(1L)).thenReturn(Optional.of(admin));
        when(users.findById(2L)).thenReturn(Optional.of(member));

        QuotaExemptionService policy = new QuotaExemptionService(users);
        assertTrue(policy.isExempt("1"));
        assertFalse(policy.isExempt("2"));
        assertFalse(policy.isExempt("unknown"));
    }
}
