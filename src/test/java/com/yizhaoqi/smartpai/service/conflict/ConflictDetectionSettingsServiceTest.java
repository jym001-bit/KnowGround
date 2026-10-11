package com.yizhaoqi.smartpai.service.conflict;

import com.yizhaoqi.smartpai.model.ConflictDetectionSettings;
import com.yizhaoqi.smartpai.repository.ConflictDetectionSettingsRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConflictDetectionSettingsServiceTest {
    @Test
    void persistedChoiceOverridesDefaultAndSurvivesServiceRecreation() {
        ConflictDetectionSettingsRepository repository = mock(ConflictDetectionSettingsRepository.class);
        when(repository.findById(1L)).thenReturn(Optional.empty());
        ConflictDetectionSettingsService service = new ConflictDetectionSettingsService(repository, true, false);
        assertFalse(service.getSettings().llmConfirmationEnabled());

        service.setLlmConfirmationEnabled(true, "admin");
        var saved = org.mockito.ArgumentCaptor.forClass(ConflictDetectionSettings.class);
        verify(repository).save(saved.capture());
        assertEquals("admin", saved.getValue().getUpdatedBy());
        assertTrue(saved.getValue().isLlmConfirmationEnabled());

        when(repository.findById(1L)).thenReturn(Optional.of(saved.getValue()));
        assertTrue(new ConflictDetectionSettingsService(repository, true, false)
                .getSettings().llmConfirmationEnabled());
    }
}
