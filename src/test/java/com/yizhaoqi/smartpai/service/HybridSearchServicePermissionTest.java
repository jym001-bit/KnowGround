package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.entity.SearchResult;
import com.yizhaoqi.smartpai.model.FileUpload;
import com.yizhaoqi.smartpai.repository.FileUploadRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HybridSearchServicePermissionTest {
    @Test
    void privateDocumentOwnedByAnotherUserIsRemovedEvenIfSearchReturnsIt() {
        FileUploadRepository files = mock(FileUploadRepository.class);
        FileUpload adminPrivate = new FileUpload();
        adminPrivate.setFileMd5("private-md5");
        adminPrivate.setUserId("1");
        adminPrivate.setPublic(false);
        adminPrivate.setOrgTag("ORG_tag");
        FileUpload ownPrivate = new FileUpload();
        ownPrivate.setFileMd5("own-md5");
        ownPrivate.setUserId("2");
        ownPrivate.setPublic(false);
        FileUpload publicFile = new FileUpload();
        publicFile.setFileMd5("public-md5");
        publicFile.setUserId("1");
        publicFile.setPublic(true);
        when(files.findByFileMd5In(anyList())).thenReturn(List.of(adminPrivate, ownPrivate, publicFile));

        HybridSearchService service = new HybridSearchService();
        ReflectionTestUtils.setField(service, "fileUploadRepository", files);
        List<SearchResult> hits = List.of(
                new SearchResult("private-md5", 1, "admin 的面经", 1.0),
                new SearchResult("own-md5", 1, "我的文档", 1.0),
                new SearchResult("public-md5", 1, "公开资料", 1.0));

        List<SearchResult> visible = service.retainAccessibleFiles(hits, "2");

        assertEquals(List.of("own-md5", "public-md5"),
                visible.stream().map(SearchResult::getFileMd5).toList());
    }
}
