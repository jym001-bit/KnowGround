package com.yizhaoqi.smartpai.controller;

import com.yizhaoqi.smartpai.model.DocumentConflict;
import com.yizhaoqi.smartpai.service.conflict.DocumentConflictService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/document-conflicts")
public class DocumentConflictAdminController {
    private final DocumentConflictService service;

    public DocumentConflictAdminController(DocumentConflictService service) {
        this.service = service;
    }

    @GetMapping
    public List<DocumentConflict> pending() {
        return service.pending();
    }

    @PostMapping("/{id}/decision")
    public DocumentConflict decide(@PathVariable long id, @RequestBody Decision request,
                                   @RequestAttribute("userId") String adminId) {
        if (request == null || !Boolean.TRUE.equals(request.dismiss())
                && (request.keepFileMd5() == null || request.keepFileMd5().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请指定保留文档或驳回误报");
        }
        try {
            return service.decide(id, request.keepFileMd5(), Boolean.TRUE.equals(request.dismiss()), adminId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
        }
    }

    public record Decision(String keepFileMd5, Boolean dismiss) {
    }

    @PostMapping("/{id}/undo")
    public DocumentConflict undo(@PathVariable long id) {
        try {
            return service.undo(id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
        }
    }
}
