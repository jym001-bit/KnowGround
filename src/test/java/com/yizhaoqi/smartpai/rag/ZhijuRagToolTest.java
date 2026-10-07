package com.yizhaoqi.smartpai.rag;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.yizhaoqi.smartpai.client.DeepSeekClient;
import com.yizhaoqi.smartpai.entity.SearchResult;
import com.yizhaoqi.smartpai.repository.FileUploadRepository;
import com.yizhaoqi.smartpai.service.AgentToolRegistry;
import com.yizhaoqi.smartpai.service.HybridSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * RAG 工具单元测试：IDEA 点击类或方法旁的绿色按钮运行。
 * 被测试的 AgentToolRegistry 是真实代码；Mockito 只替代外部依赖。
 * 不需要 Spring、数据库或模型密钥，不上传文档，不消耗模型额度。
 * 注意：这些测试不能证明实际检索准确率、模型不产生幻觉或组织隔离有效。
 */
@DisplayName("RAG：检索工具与来源证据测试")
class ZhijuRagToolTest {
    private HybridSearchService search;
    private DeepSeekClient model;
    private AgentToolRegistry registry;

    @BeforeEach
    void setUp() {
        search = mock(HybridSearchService.class);
        model = mock(DeepSeekClient.class);
        registry = new AgentToolRegistry(search, model, mock(StringRedisTemplate.class),
                mock(ElasticsearchClient.class), mock(FileUploadRepository.class));
    }

    @Test
    @DisplayName("01 不指定topK时只请求默认5个片段，并传递当前用户身份")
    void defaultSearchPreservesQueryAndUser() {
        // 准备：模拟检索服务暂时没有匹配文档。
        when(search.searchWithPermission("知据如何保存聊天记录？", "42", 5)).thenReturn(List.of());
        // 执行：调用项目真实的工具入口。
        var result = registry.executeTool("search_knowledge",
                Map.of("query", "知据如何保存聊天记录？"), "42");
        // 断言：检查工具是否把问题、用户和数量正确交给带权限检索服务。
        verify(search).searchWithPermission("知据如何保存聊天记录？", "42", 5);
        assertEquals(5, result.data().get("topK"));
        assertTrue(result.success());
        verifyNoInteractions(model);
    }

    @Test
    @DisplayName("02 请求过多片段时限制为20个，避免无界扩大上下文")
    void capsExcessiveTopK() {
        registry.executeTool("search_knowledge", Map.of("query", "RAG", "topK", 1000), "42");
        verify(search).searchWithPermission("RAG", "42", 20);
    }

    @Test
    @DisplayName("03 topK为0时修正为至少1个片段")
    void clampsZeroTopK() {
        registry.executeTool("search_knowledge", Map.of("query", "RAG", "topK", 0), "42");
        verify(search).searchWithPermission("RAG", "42", 1);
    }

    @Test
    @DisplayName("04 空白问题被拒绝，不访问检索服务")
    void rejectsBlankQuestion() {
        assertThrows(IllegalArgumentException.class, () -> registry.executeTool(
                "search_knowledge", Map.of("query", "  "), "42"));
        verifyNoInteractions(search, model);
    }

    @Test
    @DisplayName("05 缺少用户身份时被拒绝，不能发起无身份检索")
    void rejectsMissingUser() {
        assertThrows(IllegalArgumentException.class, () -> registry.executeTool(
                "search_knowledge", Map.of("query", "内部文档"), null));
        verifyNoInteractions(search, model);
    }

    @Test
    @DisplayName("06 没有检索结果时明确返回无片段提示")
    void reportsEmptyRetrieval() {
        when(search.searchWithPermission("不存在的资料", "42", 5)).thenReturn(List.of());
        var result = registry.executeTool("search_knowledge", Map.of("query", "不存在的资料"), "42");
        assertEquals(List.of(), result.data().get("results"));
        assertTrue(result.content().contains("未检索到"));
        // 只验证工具层不制造来源，不代表真实模型一定拒答。
        assertFalse(result.content().contains("[1]"));
    }

    @Test
    @DisplayName("07 检索到PDF片段后保留文件名、页码和原文")
    void preservesReferenceEvidence() {
        SearchResult source = source("数据库保存长期聊天历史。", 7);
        when(search.searchWithPermission("历史存在哪里", "42", 5)).thenReturn(List.of(source));
        var result = registry.executeTool("search_knowledge", Map.of("query", "历史存在哪里"), "42");
        assertAll(
                () -> assertTrue(result.content().contains("知据架构说明.pdf")),
                () -> assertTrue(result.content().contains("page=7")),
                () -> assertTrue(result.content().contains("数据库保存长期聊天历史。")),
                () -> assertTrue(result.content().contains("[1]")),
                () -> assertEquals(List.of(source), result.data().get("results")));
    }

    @Test
    @DisplayName("08 使用命中片段原文，避免把扩展文本当作命中证据")
    void prefersMatchedChunk() {
        SearchResult source = source("外围扩展文本", 2);
        source.setMatchedChunkText("实际命中的片段");
        when(search.searchWithPermission("引用", "42", 5)).thenReturn(List.of(source));
        var result = registry.executeTool("search_knowledge", Map.of("query", "引用"), "42");
        assertTrue(result.content().contains("实际命中的片段"));
        assertFalse(result.content().contains("外围扩展文本"));
    }

    @Test
    @DisplayName("09 过长片段在模型上下文中截断，结构化原文仍完整保留")
    void limitsPromptWithoutDestroyingEvidence() {
        SearchResult source = source("知".repeat(1200) + "尾部标记", 1);
        when(search.searchWithPermission("长文", "42", 5)).thenReturn(List.of(source));
        var result = registry.executeTool("search_knowledge", Map.of("query", "长文"), "42");
        assertTrue(result.content().endsWith("知".repeat(1200) + "..."));
        assertFalse(result.content().contains("尾部标记"));
        var evidence = (List<?>) result.data().get("results");
        assertEquals("知".repeat(1200) + "尾部标记", ((SearchResult) evidence.get(0)).getMatchedChunkText());
    }

    @Test
    @DisplayName("10 摘要工具将检索来源和当前用户交给模型，并记录来源数量")
    void summaryUsesRetrievedSources() {
        List<SearchResult> sources = List.of(source("Redis用于短期上下文。", 3));
        when(search.searchWithPermission("聊天上下文", "42", 5)).thenReturn(sources);
        when(model.summarize("42", "聊天上下文", sources, null)).thenReturn("摘要示例");
        var result = registry.executeTool("generate_summary", Map.of("topic", "聊天上下文"), "42");
        verify(model).summarize("42", "聊天上下文", sources, null);
        assertEquals(1, result.data().get("sourceCount"));
        assertEquals(sources, result.data().get("sources"));
        assertTrue(result.content().contains("摘要示例"));
    }

    @Test
    @DisplayName("11 未注册的工具调用被拒绝")
    void rejectsUnknownTool() {
        assertThrows(IllegalArgumentException.class,
                () -> registry.executeTool("unknown_tool", Map.of(), "42"));
        verifyNoInteractions(search, model);
    }

    private SearchResult source(String text, int page) {
        return new SearchResult("fixture-md5", 1, text, 0.9, "42", "test-org", false,
                "知据架构说明.pdf", page, text);
    }
}
