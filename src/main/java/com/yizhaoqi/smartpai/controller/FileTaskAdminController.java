package com.yizhaoqi.smartpai.controller;

import com.yizhaoqi.smartpai.model.FileTaskDeadLetter;
import com.yizhaoqi.smartpai.repository.FileTaskDeadLetterRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/file-tasks")
public class FileTaskAdminController {
    private final FileTaskDeadLetterRepository deadLetters;

    public FileTaskAdminController(FileTaskDeadLetterRepository deadLetters) {
        this.deadLetters = deadLetters;
    }

    @GetMapping("/dead-letters")
    public Page<FileTaskDeadLetter> deadLetters(@RequestParam(defaultValue = "0") int page) {
        return deadLetters.findAll(PageRequest.of(Math.max(0, page), 20,
                Sort.by(Sort.Direction.DESC, "createdAt")));
    }
}
