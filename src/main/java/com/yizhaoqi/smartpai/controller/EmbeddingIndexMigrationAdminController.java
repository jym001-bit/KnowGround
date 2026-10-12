package com.yizhaoqi.smartpai.controller;

import com.yizhaoqi.smartpai.model.EmbeddingIndexMigration;
import com.yizhaoqi.smartpai.repository.UserRepository;
import com.yizhaoqi.smartpai.service.EmbeddingIndexMigrationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/embedding-index-migrations")
public class EmbeddingIndexMigrationAdminController {
    private final EmbeddingIndexMigrationService service;
    private final UserRepository users;

    public EmbeddingIndexMigrationAdminController(EmbeddingIndexMigrationService service, UserRepository users) {
        this.service = service;
        this.users = users;
    }

    @GetMapping
    public EmbeddingIndexMigration status() {
        return service.status();
    }

    @PostMapping
    public EmbeddingIndexMigration start(@RequestBody StartRequest request, Principal principal) throws Exception {
        if (request == null || request.targetProvider() == null || request.targetProvider().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请指定目标 Embedding provider");
        }
        return service.start(request.targetProvider(), request.targetModel(), request.targetDimension(), requesterId(principal));
    }

    @PostMapping("/switch")
    public EmbeddingIndexMigration switchIndex(@RequestBody SwitchRequest request, Principal principal) {
        return service.switchIndex(request == null ? List.of() : request.probes(), requesterId(principal));
    }

    @PostMapping("/rollback")
    public EmbeddingIndexMigration rollback(Principal principal) throws Exception {
        return service.rollback(requesterId(principal));
    }

    private String requesterId(Principal principal) {
        return users.findByUsername(principal.getName())
                .map(user -> user.getId().toString())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "管理员账号不存在"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public void invalid(IllegalArgumentException error) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
    }

    @ExceptionHandler(IllegalStateException.class)
    public void conflict(IllegalStateException error) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, error.getMessage(), error);
    }

    public record StartRequest(String targetProvider, String targetModel, Integer targetDimension) {
    }

    public record SwitchRequest(List<EmbeddingIndexMigrationService.Probe> probes) {
    }
}
