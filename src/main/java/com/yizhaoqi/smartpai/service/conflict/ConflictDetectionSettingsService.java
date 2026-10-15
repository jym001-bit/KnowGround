package com.yizhaoqi.smartpai.service.conflict;

import com.yizhaoqi.smartpai.model.ConflictDetectionSettings;
import com.yizhaoqi.smartpai.repository.ConflictDetectionSettingsRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class ConflictDetectionSettingsService {
    private static final long SETTINGS_ID = 1L;

    private final ConflictDetectionSettingsRepository repository;
    private final boolean detectionEnabled;
    private final boolean defaultLlmConfirmationEnabled;

    public ConflictDetectionSettingsService(ConflictDetectionSettingsRepository repository,
                                            @Value("${knowledge.conflict-detection.enabled:true}") boolean detectionEnabled,
                                            @Value("${knowledge.conflict-detection.llm-confirmation-enabled:false}")
                                            boolean defaultLlmConfirmationEnabled) {
        this.repository = repository;
        this.detectionEnabled = detectionEnabled;
        this.defaultLlmConfirmationEnabled = defaultLlmConfirmationEnabled;
    }

    public SettingsView getSettings() {
        return new SettingsView(detectionEnabled, repository.findById(SETTINGS_ID)
                .map(ConflictDetectionSettings::isLlmConfirmationEnabled)
                .orElse(defaultLlmConfirmationEnabled));
    }

    @Transactional
    public SettingsView setLlmConfirmationEnabled(boolean enabled, String adminId) {
        ConflictDetectionSettings settings = repository.findById(SETTINGS_ID)
                .orElseGet(ConflictDetectionSettings::new);
        settings.setLlmConfirmationEnabled(enabled);
        settings.setUpdatedBy(adminId);
        settings.setUpdatedAt(LocalDateTime.now());
        repository.save(settings);
        return new SettingsView(detectionEnabled, enabled);
    }

    public record SettingsView(boolean detectionEnabled, boolean llmConfirmationEnabled) {
    }
}
