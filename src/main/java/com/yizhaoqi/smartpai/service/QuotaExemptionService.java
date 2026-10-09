package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.model.User;
import com.yizhaoqi.smartpai.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class QuotaExemptionService {
    private final UserRepository userRepository;

    public QuotaExemptionService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public boolean isExempt(String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        if ("system".equals(userId) || "system-cloud-reindex".equals(userId)) {
            return true;
        }
        try {
            return userRepository.findById(Long.parseLong(userId))
                    .map(user -> user.getRole() == User.Role.ADMIN).orElse(false);
        } catch (NumberFormatException ignored) {
            return userRepository.findByUsername(userId)
                    .map(user -> user.getRole() == User.Role.ADMIN).orElse(false);
        }
    }
}
