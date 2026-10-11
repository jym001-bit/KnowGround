package com.yizhaoqi.smartpai.controller;

import com.yizhaoqi.smartpai.model.DocumentConflict;
import com.yizhaoqi.smartpai.model.DocumentVector;
import com.yizhaoqi.smartpai.model.FileUpload;
import com.yizhaoqi.smartpai.repository.DocumentVectorRepository;
import com.yizhaoqi.smartpai.repository.FileUploadRepository;
import com.yizhaoqi.smartpai.service.conflict.DocumentConflictService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentConflictAdminControllerTest {
    private final DocumentConflictService service = mock(DocumentConflictService.class);
    private final FileUploadRepository uploads = mock(FileUploadRepository.class);
    private final DocumentVectorRepository vectors = mock(DocumentVectorRepository.class);
    private final DocumentConflictAdminController controller = new DocumentConflictAdminController(service, uploads, vectors);

    @Test
    void reviewedListReturnsFrontendEnvelopeWithDocumentNames() {
        DocumentConflict conflict = conflict();
        conflict.setStatus(DocumentConflict.KEEP_LEFT);
        FileUpload left = new FileUpload();
        left.setFileMd5("left");
        left.setFileName("新版配置.pdf");
        when(service.reviewed()).thenReturn(List.of(conflict));
        when(uploads.findByFileMd5In(List.of("left", "right"))).thenReturn(List.of(left));

        var response = controller.list("REVIEWED");
        var records = (List<?>) response.get("data");
        var view = (DocumentConflictAdminController.ConflictView) records.get(0);

        assertEquals(200, response.get("code"));
        assertEquals("新版配置.pdf", view.leftFileName());
        assertEquals("right", view.rightFileName());
        assertEquals(DocumentConflict.KEEP_LEFT, view.status());
    }

    @Test
    void contextIncludesAdjacentChunksForBothDocuments() {
        when(service.get(1L)).thenReturn(conflict());
        when(vectors.findByFileMd5AndChunkIdBetweenOrderByChunkIdAsc("left", 1, 3))
                .thenReturn(List.of(chunk(1, "前文"), chunk(2, "命中"), chunk(3, "后文")));
        when(vectors.findByFileMd5AndChunkIdBetweenOrderByChunkIdAsc("right", 4, 6))
                .thenReturn(List.of(chunk(5, "另一个值")));

        var response = controller.context(1L);
        var data = (DocumentConflictAdminController.ConflictContext) response.get("data");

        assertEquals(200, response.get("code"));
        assertEquals("分块 1：前文\n\n分块 2：命中\n\n分块 3：后文", data.leftText());
        assertEquals("分块 5：另一个值", data.rightText());
    }

    @Test
    void rejectsUnsupportedListStatus() {
        var error = assertThrows(ResponseStatusException.class, () -> controller.list("OTHER"));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    private static DocumentConflict conflict() {
        DocumentConflict conflict = new DocumentConflict();
        conflict.setId(1L);
        conflict.setLeftFileMd5("left");
        conflict.setRightFileMd5("right");
        conflict.setLeftChunkId(2);
        conflict.setRightChunkId(5);
        conflict.setConflictKey("服务端口");
        conflict.setLeftValue("8081");
        conflict.setRightValue("8082");
        return conflict;
    }

    private static DocumentVector chunk(int id, String text) {
        DocumentVector chunk = new DocumentVector();
        chunk.setChunkId(id);
        chunk.setTextContent(text);
        return chunk;
    }
}
