package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.model.FileUpload;
import com.yizhaoqi.smartpai.repository.ChunkInfoRepository;
import com.yizhaoqi.smartpai.repository.FileUploadRepository;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.messages.Item;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class AbandonedUploadCleanupService {
    private final JdbcTemplate jdbc;
    private final FileUploadRepository files;
    private final ChunkInfoRepository chunks;
    private final MinioClient minio;
    private final RedisTemplate<String, Object> redis;

    public AbandonedUploadCleanupService(JdbcTemplate jdbc, FileUploadRepository files, ChunkInfoRepository chunks,
                                         MinioClient minio, RedisTemplate<String, Object> redis) {
        this.jdbc = jdbc;
        this.files = files;
        this.chunks = chunks;
        this.minio = minio;
        this.redis = redis;
    }

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 60_000)
    public void cleanup() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        List<Long> ids = jdbc.queryForList("select id from file_upload where status=0 and last_chunk_at < ? "
                + "order by last_chunk_at limit 50", Long.class, cutoff);
        for (Long id : ids) {
            try {
                cleanupOne(id, cutoff);
            } catch (Exception e) {
                log.error("废弃上传清理失败，需要人工检查: fileId={}", id, e);
            }
        }
    }

    void cleanupOne(Long id, LocalDateTime cutoff) throws Exception {
        FileUpload file = files.findById(id).orElse(null);
        if (file == null || files.countByFileMd5(file.getFileMd5()) != 1) return;
        // 状态 CAS 阻止同时上传或合并；相同 MD5 被多个用户引用时保留共享分片。
        int claimed = jdbc.update("update file_upload set status=3 where id=? and status=0 and last_chunk_at < ?",
                id, cutoff);
        if (claimed != 1) return;
        try {
            String prefix = "chunks/" + file.getFileMd5() + "/";
            for (Result<Item> result : minio.listObjects(ListObjectsArgs.builder()
                    .bucket("uploads").prefix(prefix).recursive(true).build())) {
                minio.removeObject(RemoveObjectArgs.builder()
                        .bucket("uploads").object(result.get().objectName()).build());
            }
            chunks.deleteByFileMd5(file.getFileMd5());
            redis.delete("upload:" + file.getUserId() + ":" + file.getFileMd5());
            // 保留文件记录，更新活动时间；用户之后仍可重新上传同一文件。
            jdbc.update("update file_upload set status=0, last_chunk_at=? where id=? and status=3",
                    LocalDateTime.now(), id);
            log.info("废弃上传分片已清理: fileId={}, fileMd5={}", id, file.getFileMd5());
        } catch (Exception e) {
            // 部分对象删除可以安全重试；保留旧活动时间，下一轮扫描继续清理。
            jdbc.update("update file_upload set status=0 where id=? and status=3", id);
            throw e;
        }
    }
}
