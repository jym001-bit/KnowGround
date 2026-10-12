package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.entity.EsDocument;
import com.yizhaoqi.smartpai.model.DocumentVector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbeddingIndexMigrationServiceTest {
    @Test
    void catchUpReembedsWhenTextContextOrModelChanged() {
        DocumentVector row = new DocumentVector();
        row.setTextContent("正文");
        row.setChunkContext("上下文");
        row.setUserId("7");
        row.setOrgTag("team");
        EsDocument indexed = new EsDocument();
        indexed.setTextContent("正文");
        indexed.setChunkContext("上下文");
        indexed.setUserId("7");
        indexed.setOrgTag("team");
        indexed.setModelVersion("provider:model:2048");

        assertTrue(EmbeddingIndexMigrationService.sameChunk(row, indexed, "provider:model:2048", true));
        row.setTextContent("新正文");
        assertFalse(EmbeddingIndexMigrationService.sameChunk(row, indexed, "provider:model:2048", true));
        row.setTextContent("正文");
        row.setChunkContext("新上下文");
        assertFalse(EmbeddingIndexMigrationService.sameChunk(row, indexed, "provider:model:2048", true));
        row.setChunkContext("上下文");
        assertFalse(EmbeddingIndexMigrationService.sameChunk(row, indexed, "provider:new-model:2048", true));
    }
}
