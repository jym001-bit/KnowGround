package com.yizhaoqi.smartpai.controller;

import com.yizhaoqi.smartpai.service.HybridSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void rejectsOutOfRangeTopKBeforeSearching() {
        HybridSearchService search = mock(HybridSearchService.class);
        SearchController controller = new SearchController();
        ReflectionTestUtils.setField(controller, "hybridSearchService", search);

        for (int topK : new int[]{0, 21}) {
            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> controller.hybridSearch("面经", topK, "1"));
            assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        }
        verifyNoInteractions(search);
    }
}
