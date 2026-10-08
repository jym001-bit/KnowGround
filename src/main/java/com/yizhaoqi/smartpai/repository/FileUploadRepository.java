package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.FileUpload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface FileUploadRepository extends JpaRepository<FileUpload, Long> {
    Optional<FileUpload> findFirstByFileMd5OrderByCreatedAtDesc(String fileMd5);

    List<FileUpload> findAllByFileMd5(String fileMd5);

    List<FileUpload> findAllByVectorizationStatusIsNull();

    List<FileUpload> findAllByFileMd5AndUserIdOrderByCreatedAtDesc(String fileMd5, String userId);

    Optional<FileUpload> findFirstByFileMd5AndUserIdOrderByCreatedAtDesc(String fileMd5, String userId);

    Optional<FileUpload> findFirstByFileMd5AndIsPublicTrueOrderByCreatedAtDesc(String fileMd5);

    Optional<FileUpload> findFirstByFileNameAndIsPublicTrueOrderByCreatedAtDesc(String fileName);

    Optional<FileUpload> findFirstByOrderByMergedAtDesc();
    
    long countByFileMd5(String fileMd5);

    long countByFileMd5AndUserId(String fileMd5, String userId);
    
    @Transactional
    @Modifying
    @Query("delete from FileUpload f where f.fileMd5 = :fileMd5")
    int deleteByFileMd5(@Param("fileMd5") String fileMd5);

    @Transactional
    @Modifying
    @Query("delete from FileUpload f where f.fileMd5 = :fileMd5 and f.userId = :userId")
    int deleteByFileMd5AndUserId(@Param("fileMd5") String fileMd5, @Param("userId") String userId);
    
    /**
     * 查询用户自己的文件和公开文件
     */
    List<FileUpload> findByUserIdOrIsPublicTrue(String userId);
    
    /**
     * 查询用户自己上传的所有文件
     * 
     * @param userId 用户ID
     * @return 用户上传的文件列表
     */
    List<FileUpload> findByUserId(String userId);

    List<FileUpload> findByUserIdAndFileNameOrderByCreatedAtDesc(String userId, String fileName);

    List<FileUpload> findByFileMd5In(List<String> md5List);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE FileUpload f SET f.status = :newStatus WHERE f.id = :id AND f.status = :currentStatus")
    int updateStatusIfCurrent(@Param("id") Long id,
                              @Param("currentStatus") int currentStatus,
                              @Param("newStatus") int newStatus);
}
