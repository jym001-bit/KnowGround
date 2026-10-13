package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.FileTaskDeadLetter;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileTaskDeadLetterRepository extends JpaRepository<FileTaskDeadLetter, String> {
}
