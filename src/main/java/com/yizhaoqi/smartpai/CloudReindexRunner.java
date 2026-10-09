package com.yizhaoqi.smartpai;

import com.yizhaoqi.smartpai.model.FileUpload;
import com.yizhaoqi.smartpai.repository.FileUploadRepository;
import com.yizhaoqi.smartpai.service.ElasticsearchService;
import com.yizhaoqi.smartpai.service.VectorizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** One-shot cloud index rebuild from the copied MySQL text chunks. */
@Component
@Profile("cloud-reindex")
public class CloudReindexRunner implements ApplicationRunner {
    private static final Logger logger = LoggerFactory.getLogger(CloudReindexRunner.class);

    private final FileUploadRepository files;
    private final ElasticsearchService search;
    private final VectorizationService vectors;
    private final ConfigurableApplicationContext context;

    public CloudReindexRunner(FileUploadRepository files, ElasticsearchService search,
                              VectorizationService vectors, ConfigurableApplicationContext context) {
        this.files = files;
        this.search = search;
        this.vectors = vectors;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        String selection = System.getenv().getOrDefault("CLOUD_REINDEX_FILE_MD5", "");
        boolean confirmed = "true".equalsIgnoreCase(System.getenv("CLOUD_REINDEX_CONFIRM"));
        if (!confirmed || selection.isBlank()) {
            logger.error("Cloud reindex requires CLOUD_REINDEX_CONFIRM=true and CLOUD_REINDEX_FILE_MD5=all or one MD5");
            System.exit(2);
        }

        Map<String, FileUpload> uniqueFiles = new LinkedHashMap<>();
        for (FileUpload file : files.findAll()) uniqueFiles.putIfAbsent(file.getFileMd5(), file);
        int indexed = 0;
        int chunks = 0;
        try {
            for (FileUpload file : uniqueFiles.values()) {
                if (!selection.equals("all") && !selection.equals(file.getFileMd5())) continue;
                String md5 = file.getFileMd5();
                logger.info("Rebuilding cloud index for fileMd5={}", md5);
                search.deleteByFileMd5(md5);
                VectorizationService.VectorizationUsageResult result = vectors.vectorizeWithUsage(
                        md5, file.getUserId(), file.getOrgTag(), file.isPublic(), "system-cloud-reindex");
                if (result.actualChunkCount() == 0) throw new IllegalStateException("No chunks for " + md5);
                indexed++;
                chunks += result.actualChunkCount();
                logger.info("Rebuilt fileMd5={}, chunks={}, embeddingTokens={}",
                        md5, result.actualChunkCount(), result.actualEmbeddingTokens());
            }
            if (indexed == 0) throw new IllegalArgumentException("No matching file: " + selection);
            logger.info("Cloud reindex complete: files={}, chunks={}", indexed, chunks);
            System.exit(org.springframework.boot.SpringApplication.exit(context));
        } catch (Exception exception) {
            logger.error("Cloud reindex failed after files={} chunks={}", indexed, chunks, exception);
            System.exit(1);
        }
    }
}
