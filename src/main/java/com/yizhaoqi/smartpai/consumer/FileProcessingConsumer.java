package com.yizhaoqi.smartpai.consumer;

import com.yizhaoqi.smartpai.config.KafkaConfig;
import com.yizhaoqi.smartpai.model.FileProcessingTask;
import com.yizhaoqi.smartpai.model.FileUpload;
import com.yizhaoqi.smartpai.repository.DocumentVectorRepository;
import com.yizhaoqi.smartpai.repository.FileUploadRepository;
import com.yizhaoqi.smartpai.service.DocumentService;
import com.yizhaoqi.smartpai.service.ElasticsearchService;
import com.yizhaoqi.smartpai.service.FileTaskClaimService;
import com.yizhaoqi.smartpai.service.FileTaskHeartbeat;
import com.yizhaoqi.smartpai.service.StaleFileTaskException;
import com.yizhaoqi.smartpai.service.ParseService;
import com.yizhaoqi.smartpai.service.UploadService;
import com.yizhaoqi.smartpai.service.VectorizationService;
import com.yizhaoqi.smartpai.service.conflict.DocumentConflictService;
import io.minio.errors.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

@Service
@Slf4j
public class FileProcessingConsumer {

    private final ParseService parseService;
    private final VectorizationService vectorizationService;
    private final DocumentService documentService;
    private final DocumentConflictService conflictService;
    private final FileTaskClaimService claims;
    private final FileUploadRepository files;
    private final DocumentVectorRepository chunks;
    private final ElasticsearchService elasticsearchService;
    private final UploadService uploadService;
    @Autowired
    private KafkaConfig kafkaConfig;


    public FileProcessingConsumer(
            ParseService parseService,
            VectorizationService vectorizationService,
            DocumentService documentService,
            DocumentConflictService conflictService,
            FileTaskClaimService claims,
            FileUploadRepository files,
            DocumentVectorRepository chunks,
            ElasticsearchService elasticsearchService,
            UploadService uploadService
    ) {
        this.parseService = parseService;
        this.vectorizationService = vectorizationService;
        this.documentService = documentService;
        this.conflictService = conflictService;
        this.claims = claims;
        this.files = files;
        this.chunks = chunks;
        this.elasticsearchService = elasticsearchService;
        this.uploadService = uploadService;
    }

    @KafkaListener(id = "fileProcessing", topics = "#{kafkaConfig.getFileProcessingTopic()}", groupId = "#{kafkaConfig.getFileProcessingGroupId()}")
    public void processTask(FileProcessingTask task) {
        String owner = claims.claim(task);
        if (owner == null) {
            log.info("重复文件任务已跳过: taskId={}, fileMd5={}", claims.taskId(task), task.getFileMd5());
            return;
        }
        try (com.yizhaoqi.smartpai.service.EmbeddingSwitchGate.Permit ignored =
                     com.yizhaoqi.smartpai.service.DocumentMutationGate.read();
             FileTaskHeartbeat.Scope heartbeat = FileTaskHeartbeat.bind(() -> claims.heartbeat(task, owner))) {
            FileTaskHeartbeat.pulse();
            processTaskLocked(task);
            FileTaskHeartbeat.pulse();
            claims.finish(task, owner, true, null);
        } catch (Exception e) {
            claims.finish(task, owner, false, e);
            if (isStale(e)) {
                log.warn("旧消费线程已失去文件任务所有权，停止处理: taskId={}", claims.taskId(task));
                return;
            }
            throw e;
        }
    }

    private void processTaskLocked(FileProcessingTask task) {
        log.info("Received task: {}", task);
        log.info("文件权限信息: userId={}, orgTag={}, isPublic={}", 
                task.getUserId(), task.getOrgTag(), task.isPublic());

        if (!FileProcessingTask.TASK_TYPE_REINDEX.equals(task.getTaskType())
                && files.findFirstByFileMd5AndUserIdOrderByCreatedAtDesc(task.getFileMd5(), task.getUserId())
                .map(file -> FileUpload.VECTORIZATION_STATUS_COMPLETED.equals(file.getVectorizationStatus()))
                .orElse(false)) {
            log.info("文件已经向量化，跳过重复上传任务: fileMd5={}", task.getFileMd5());
            return;
        }

        documentService.markVectorizationProcessing(task.getFileMd5(), false);

        if (FileProcessingTask.TASK_TYPE_REINDEX.equals(task.getTaskType())) {
            processReindexTask(task);
            return;
        }

        InputStream fileStream = null;
        try {
            // 下载文件
            fileStream = FileProcessingTask.TASK_TYPE_REINDEX.equals(task.getTaskType())
                    ? downloadFileFromStorage(task.getFilePath())
                    : uploadService.getMergedFileStream(task.getFileMd5());
            // 在 downloadFileFromStorage 返回后立即检查流是否可读
            if (fileStream == null) {
                throw new IOException("流为空");
            }

            // 强制转换为可缓存流
            if (!fileStream.markSupported()) {
                fileStream = new BufferedInputStream(fileStream);
            }

            // 解析文件
            // 失败后重新投递时先清掉上次留下的部分分块和向量。
            chunks.deleteByFileMd5(task.getFileMd5());
            elasticsearchService.deleteByFileMd5(task.getFileMd5());
            parseService.parseAndSave(task.getFileMd5(), fileStream, 
                    task.getUserId(), task.getOrgTag(), task.isPublic());
            FileTaskHeartbeat.pulse();
            log.info("文件解析完成，fileMd5: {}", task.getFileMd5());

            // 向量化处理
            VectorizationService.VectorizationUsageResult vectorizationResult = vectorizationService.vectorizeWithUsage(
                    task.getFileMd5(),
                    task.getUserId(),
                    task.getOrgTag(),
                    task.isPublic(),
                    task.getUserId()
            );
            FileTaskHeartbeat.pulse();
            documentService.markVectorizationCompleted(task.getFileMd5(), vectorizationResult);
            conflictService.submit(task.getFileMd5(), task.getUserId());//提交检测工作
            log.info("向量化完成，fileMd5: {}", task.getFileMd5());
        } catch (Exception e) {
            if (!isStale(e)) documentService.markVectorizationFailed(task.getFileMd5(), e);
            log.error("Error processing task: {}", task, e);
            // 抛出异常让 Kafka 的 DefaultErrorHandler 捕获并触发重试 / 死信
            throw new RuntimeException("Error processing task", e);
        } finally {
            // 确保关闭输入流
            if (fileStream != null) {
                try {
                    fileStream.close();
                } catch (IOException e) {
                    log.error("Error closing file stream", e);
                }
            }
        }
    }

    private void processReindexTask(FileProcessingTask task) {
        try {
            String requesterId = task.getRequesterId() == null || task.getRequesterId().isBlank()
                    ? task.getUserId()
                    : task.getRequesterId();
            documentService.reindexDocument(task.getFileMd5(), requesterId);
            conflictService.submit(task.getFileMd5(), requesterId);
        } catch (Exception e) {
            if (!isStale(e)) documentService.markVectorizationFailed(task.getFileMd5(), e);
            log.error("Error reindexing task: {}", task, e);
            throw new RuntimeException("Error reindexing task", e);
        }
    }

    private boolean isStale(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof StaleFileTaskException) return true;
        }
        return false;
    }

    /**
     * 模拟从存储系统下载文件
     *
     * @param filePath 文件路径或 URL
     * @return 文件输入流
     */
    private InputStream downloadFileFromStorage(String filePath) throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        log.info("Downloading file from storage: {}", filePath);

        try {
            // 如果是文件系统路径
            File file = new File(filePath);
            if (file.exists()) {
                log.info("Detected file system path: {}", filePath);
                return new FileInputStream(file);
            }

            // 如果是远程 URL
            if (filePath.startsWith("http://") || filePath.startsWith("https://")) {
                log.info("Detected remote URL: {}", filePath);
                URL url = new URL(filePath);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(30000); // 连接超时30秒
                connection.setReadTimeout(180000);   // 读取超时时间3分钟

                // 添加必要的请求头
                connection.setRequestProperty("User-Agent", "SmartPAI-FileProcessor/1.0");

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    log.info("Successfully connected to URL, starting download...");
                    return connection.getInputStream();
                } else if (responseCode == HttpURLConnection.HTTP_FORBIDDEN) {
                    log.error("Access forbidden - possible expired presigned URL");
                    throw new IOException("Access forbidden - the presigned URL may have expired");
                } else {
                    log.error("Failed to download file, HTTP response code: {} for URL: {}", responseCode, filePath);
                    throw new IOException(String.format("Failed to download file, HTTP response code: %d", responseCode));
                }
            }

            // 如果既不是文件路径也不是 URL
            throw new IllegalArgumentException("Unsupported file path format: " + filePath);
        } catch (Exception e) {
            log.error("Error downloading file from storage: {}", filePath, e);
            return null; // 或者抛出异常
        }
    }

}
