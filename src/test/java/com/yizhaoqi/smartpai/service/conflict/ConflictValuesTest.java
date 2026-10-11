package com.yizhaoqi.smartpai.service.conflict;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConflictValuesTest {
    @Test
    void comparesValuesAfterMaskingVersionInKey() {
        var difference = ConflictValues.compare("服务 v1 端口: 8081", "服务 v2 端口: 8082");
        assertTrue(difference.isPresent());
        assertEquals("8081", difference.get().leftValue());
        assertEquals("8082", difference.get().rightValue());
    }

    @Test
    void similarityWithoutSameExplicitFactIsNotAConflict() {
        assertTrue(ConflictValues.compare("服务稳定可靠", "服务很稳定可靠").isEmpty());
        assertTrue(ConflictValues.compare("服务端口: 8081", "管理端口: 8082").isEmpty());
        assertTrue(ConflictValues.compare("服务端口: 8081", "服务端口: 8081").isEmpty());
    }

    @Test
    void cosineThresholdUsesActualCosineNotElasticsearchScore() {
        assertEquals(1.0, DocumentConflictService.cosine(new float[]{1, 0}, new float[]{2, 0}), 1e-6);
        assertEquals(0.0, DocumentConflictService.cosine(new float[]{1, 0}, new float[]{0, 1}), 1e-6);
    }
}
