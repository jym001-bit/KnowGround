package com.yizhaoqi.smartpai.controller;

import com.yizhaoqi.smartpai.service.HybridSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class SearchControllerTest {
    @Test
    void missingIdentityNeverStartsKnowledgeSearch() {
        HybridSearchService search = mock(HybridSearchService.class);
        SearchController controller = new SearchController();
        ReflectionTestUtils.setField(controller, "hybridSearchService", search);

        assertThrows(ResponseStatusException.class,
                () -> controller.hybridSearch("面经", 10, null));
        verifyNoInteractions(search);
    }
}
