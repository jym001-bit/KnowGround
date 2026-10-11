package com.yizhaoqi.smartpai.controller;

import com.yizhaoqi.smartpai.model.DocumentConflict;
import com.yizhaoqi.smartpai.model.FileUpload;
import com.yizhaoqi.smartpai.repository.FileUploadRepository;
import com.yizhaoqi.smartpai.repository.DocumentVectorRepository;
import com.yizhaoqi.smartpai.service.conflict.DocumentConflictService;
import com.yizhaoqi.smartpai.service.conflict.ConflictDetectionSettingsService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/v1/admin/document-conflicts")
public class DocumentConflictAdminController {
    private final DocumentConflictService service;
    private final FileUploadRepository fileUploadRepository;
    private final DocumentVectorRepository documentVectorRepository;
    private final ConflictDetectionSettingsService settingsService;

    public DocumentConflictAdminController(DocumentConflictService service, FileUploadRepository fileUploadRepository,
                                           DocumentVectorRepository documentVectorRepository,
                                           ConflictDetectionSettingsService settingsService) {
        this.service = service;
        this.fileUploadRepository = fileUploadRepository;
        this.documentVectorRepository = documentVectorRepository;
        this.settingsService = settingsService;
    }

    @GetMapping("/settings")
    public Map<String, Object> settings() {
        return success(settingsService.getSettings());
    }

    @PutMapping("/settings")
    public Map<String, Object> updateSettings(@RequestBody UpdateSettings request,
                                               @RequestAttribute("userId") String adminId) {
        if (request == null || request.llmConfirmationEnabled() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请指定是否启用 LLM 二次确认");
        }
        return success(settingsService.setLlmConfirmationEnabled(request.llmConfirmationEnabled(), adminId));
    }

    public record UpdateSettings(Boolean llmConfirmationEnabled) {
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(defaultValue = "PENDING") String status) {
        List<DocumentConflict> conflicts = switch (status) {
            case "PENDING" -> service.pending();
            case "REVIEWED" -> service.reviewed();
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status 只能是 PENDING 或 REVIEWED");
        };
        List<String> md5s = conflicts.stream()
                .flatMap(item -> Stream.of(item.getLeftFileMd5(), item.getRightFileMd5()))
                .distinct().toList();
        Map<String, String> fileNames = md5s.isEmpty() ? Map.of() : fileUploadRepository.findByFileMd5In(md5s)
                .stream().collect(Collectors.toMap(FileUpload::getFileMd5, DocumentConflictAdminController::fileName,
                        (first, ignored) -> first));
        return success(conflicts.stream().map(item -> toView(item, fileNames)).toList());
    }

    @PostMapping("/{id}/decision")
    public Map<String, Object> decide(@PathVariable long id, @RequestBody Decision request,
                                      @RequestAttribute("userId") String adminId) {
        if (request == null || !Boolean.TRUE.equals(request.dismiss())
                && (request.keepFileMd5() == null || request.keepFileMd5().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请指定保留文档或驳回误报");
        }
        try {
            return success(toView(service.decide(id, request.keepFileMd5(), Boolean.TRUE.equals(request.dismiss()), adminId)));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
        }
    }

    public record Decision(String keepFileMd5, Boolean dismiss) {
    }

    @GetMapping("/{id}/context")
    public Map<String, Object> context(@PathVariable long id) {
        try {
            DocumentConflict conflict = service.get(id);
            return success(new ConflictContext(
                    contextText(conflict.getLeftFileMd5(), conflict.getLeftChunkId()),
                    contextText(conflict.getRightFileMd5(), conflict.getRightChunkId())));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
    }

    private String contextText(String fileMd5, int chunkId) {
        int from = Math.max(0, chunkId - 1);
        String content = documentVectorRepository.findByFileMd5AndChunkIdBetweenOrderByChunkIdAsc(
                        fileMd5, from, chunkId + 1).stream()
                .map(chunk -> "分块 " + chunk.getChunkId() + "：" + chunk.getTextContent())
                .collect(Collectors.joining("\n\n"));
        return content.length() > 3000 ? content.substring(0, 3000) : content;
    }

    public record ConflictContext(String leftText, String rightText) {
    }

    @PostMapping("/{id}/undo")
    public Map<String, Object> undo(@PathVariable long id) {
        try {
            return success(toView(service.undo(id)));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
        }
    }

    private ConflictView toView(DocumentConflict conflict) {
        List<String> md5s = List.of(conflict.getLeftFileMd5(), conflict.getRightFileMd5());
        Map<String, String> names = fileUploadRepository.findByFileMd5In(md5s).stream()
                .collect(Collectors.toMap(FileUpload::getFileMd5, DocumentConflictAdminController::fileName,
                        (first, ignored) -> first));
        return toView(conflict, names);
    }

    private static String fileName(FileUpload file) {
        return file.getFileName() == null || file.getFileName().isBlank()
                ? file.getFileMd5() : file.getFileName();
    }

    private static ConflictView toView(DocumentConflict conflict, Map<String, String> names) {
        return new ConflictView(conflict.getId(), conflict.getLeftFileMd5(),
                names.getOrDefault(conflict.getLeftFileMd5(), conflict.getLeftFileMd5()), conflict.getLeftChunkId(),
                conflict.getLeftValue(), conflict.getRightFileMd5(),
                names.getOrDefault(conflict.getRightFileMd5(), conflict.getRightFileMd5()), conflict.getRightChunkId(),
                conflict.getRightValue(), conflict.getConflictKey(), conflict.getSimilarity(), conflict.getStatus(),
                conflict.getCreatedAt(), conflict.getReviewedAt(), conflict.getReviewedBy());
    }

    private static Map<String, Object> success(Object data) {
        return Map.of("code", 200, "message", "success", "data", data);
    }

    public record ConflictView(Long id, String leftFileMd5, String leftFileName, Integer leftChunkId,
                               String leftValue, String rightFileMd5, String rightFileName, Integer rightChunkId,
                               String rightValue, String conflictKey, double similarity, String status,
                               java.time.LocalDateTime createdAt, java.time.LocalDateTime reviewedAt,
                               String reviewedBy) {
    }
}
