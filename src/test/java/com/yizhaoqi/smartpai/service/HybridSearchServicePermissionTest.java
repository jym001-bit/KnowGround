package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.entity.SearchResult;
import com.yizhaoqi.smartpai.model.FileUpload;
import com.yizhaoqi.smartpai.config.ContextualRetrievalProperties;
import com.yizhaoqi.smartpai.config.HybridSearchProperties;
import com.yizhaoqi.smartpai.client.EmbeddingClient;
import com.yizhaoqi.smartpai.client.RerankClient;
import com.yizhaoqi.smartpai.repository.UserRepository;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.yizhaoqi.smartpai.repository.FileUploadRepository;
import org.junit.jupiter.api.Test;

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

        HybridSearchService service = new HybridSearchService(
                mock(ElasticsearchClient.class), mock(EmbeddingClient.class), mock(RerankClient.class),
                new HybridSearchProperties(), new ContextualRetrievalProperties(),
                mock(UserRepository.class), mock(OrgTagCacheService.class), files, Runnable::run);
        List<SearchResult> hits = List.of(
                new SearchResult("private-md5", 1, "admin 的面经", 1.0, "1", "ORG_tag", false),
                new SearchResult("own-md5", 1, "我的文档", 1.0, "2", null, false),
                new SearchResult("public-md5", 1, "公开资料", 1.0, "1", null, true));

        List<SearchResult> visible = service.retainAccessibleFiles(hits, "2", List.of());

        assertEquals(List.of("own-md5", "public-md5"),
                visible.stream().map(SearchResult::getFileMd5).toList());
    }
}
