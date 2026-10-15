package com.yizhaoqi.smartpai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yizhaoqi.smartpai.service.ModelProviderConfigService;
import com.yizhaoqi.smartpai.service.FileTaskHeartbeat;
import com.yizhaoqi.smartpai.service.ModelCallGuard;
import com.yizhaoqi.smartpai.service.ModelCallQueue;
import com.yizhaoqi.smartpai.utils.RequestDeadline;
import com.yizhaoqi.smartpai.service.RateLimitService;
import com.yizhaoqi.smartpai.service.UsageQuotaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 嵌入向量生成客户端
@Component
public class EmbeddingClient {

    public enum UsageType {
        UPLOAD,
        QUERY
    }

    @Value("${embedding.api.batch-size:100}")
    private int batchSize;

    private static final Logger logger = LoggerFactory.getLogger(EmbeddingClient.class);
    private final ObjectMapper objectMapper;
    private final RateLimitService rateLimitService;
    private final UsageQuotaService usageQuotaService;
    private final ModelProviderConfigService modelProviderConfigService;
    private final ModelCallGuard modelCallGuard;

    public EmbeddingClient(ObjectMapper objectMapper,
                           RateLimitService rateLimitService,
                           UsageQuotaService usageQuotaService,
                           ModelProviderConfigService modelProviderConfigService,
                           ModelCallGuard modelCallGuard) {
        this.objectMapper = objectMapper;
        this.rateLimitService = rateLimitService;
        this.usageQuotaService = usageQuotaService;
        this.modelProviderConfigService = modelProviderConfigService;
        this.modelCallGuard = modelCallGuard;
    }

    @PostConstruct
    public void init() {
        ModelProviderConfigService.ActiveProviderView provider = modelProviderConfigService.getActiveProvider(ModelProviderConfigService.SCOPE_EMBEDDING);
        logger.info("EmbeddingClient 初始化 - Provider: {}, 模型: {}, 批次大小: {}, 维度: {}, API地址: {}",
                provider.provider(), provider.model(), batchSize, provider.dimension(), provider.apiBaseUrl());
    }

    /**
     * 调用通义千问 API 生成向量
     * @param texts 输入文本列表
     * @return 对应的向量列表
     */
    public List<float[]> embed(List<String> texts) {
        return embedWithUsage(texts, "system", UsageType.UPLOAD).vectors();
    }

    public List<float[]> embed(List<String> texts, String requesterId) {
        return embedWithUsage(texts, requesterId, UsageType.UPLOAD).vectors();
    }

    public List<float[]> embed(List<String> texts, String requesterId, UsageType usageType) {
        return embedWithUsage(texts, requesterId, usageType).vectors();
    }

    public EmbeddingUsageResult embedWithUsage(List<String> texts, String requesterId, UsageType usageType) {
        return embedWithProvider(texts, requesterId, usageType,
                modelProviderConfigService.getActiveProvider(ModelProviderConfigService.SCOPE_EMBEDDING));
    }

    /** Build a candidate index with an inactive provider while normal queries keep the active model. */
    public EmbeddingUsageResult embedForProvider(List<String> texts, String requesterId, String providerCode) {
        return embedWithProvider(texts, requesterId, UsageType.UPLOAD,
                modelProviderConfigService.getEmbeddingProvider(providerCode));
    }

    public EmbeddingUsageResult embedForProvider(List<String> texts, String requesterId, String providerCode,
                                                 String model, int dimension) {
        var configured = modelProviderConfigService.getEmbeddingProvider(providerCode);
        var target = new ModelProviderConfigService.ActiveProviderView(configured.provider(), configured.displayName(),
                configured.apiStyle(), configured.apiBaseUrl(), model, configured.apiKey(), dimension);
        return embedWithProvider(texts, requesterId, UsageType.UPLOAD, target);
    }

    private EmbeddingUsageResult embedWithProvider(List<String> texts, String requesterId, UsageType usageType,
                                                   ModelProviderConfigService.ActiveProviderView provider) {
        try {
            String normalizedRequesterId = requesterId == null || requesterId.isBlank() ? "unknown" : requesterId;
            logger.info("开始生成向量，文本数量: {}", texts.size());

            List<float[]> all = new ArrayList<>(texts.size());
            int totalTokens = 0;
            for (int start = 0; start < texts.size(); start += batchSize) {
                int end = Math.min(start + batchSize, texts.size());//防止越界
                List<String> sub = texts.subList(start, end);
                UsageQuotaService.TokenReservationBundle reservation = usageType == UsageType.QUERY
                        ? rateLimitService.reserveEmbeddingQueryUsage(normalizedRequesterId, sub)
                        : rateLimitService.reserveEmbeddingUploadUsage(normalizedRequesterId, sub);//预占用 估算
                logger.debug("调用向量 API, 批次: {}-{} (size={})", start, end - 1, sub.size());
                try {
                    String response = callApiOnce(sub, provider, usageType);//调用API
                    EmbeddingApiResponse parsedResponse = parseEmbeddingResponse(response, sub);//解析
                    usageQuotaService.settleReservation(reservation, parsedResponse.totalTokens());//按实际用量结算
                    all.addAll(parsedResponse.vectors());
                    totalTokens += parsedResponse.totalTokens();
                    if (usageType == UsageType.UPLOAD) FileTaskHeartbeat.pulse();
                } catch (Exception e) {
                    usageQuotaService.abortReservation(reservation);
                    throw e;
                }
            }
            logger.info("成功生成向量，总数量: {}", all.size());
            return new EmbeddingUsageResult(all, totalTokens,
                    provider.provider() + ":" + provider.model() + ":" + provider.dimension());
        } catch (WebClientResponseException e) {
            // 提供详细的API响应错误信息
            logger.error("API调用失败 - 状态码: {}, 响应: {}, 请求头: {}",
                    e.getStatusCode().value(),
                    e.getResponseBodyAsString(),
                    e.getHeaders());
            throw new RuntimeException(String.format(
                    "向量生成失败 - API错误: HTTP %d - %s",
                    e.getStatusCode().value(),
                    e.getResponseBodyAsString()), e);
        } catch (Exception e) {
            logger.error("调用向量化 API 失败: {} - 类型: {}",
                    e.getMessage(),
                    e.getClass().getSimpleName(), e);
            throw new RuntimeException("向量生成失败: " + e.getMessage(), e);
        }
    }

    private String callApiOnce(List<String> batch, ModelProviderConfigService.ActiveProviderView provider,
                               UsageType usageType) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", provider.model());
        requestBody.put("input", batch);
        if (provider.dimension() != null) {
            requestBody.put("dimension", provider.dimension());
        }
        requestBody.put("encoding_format", "float");

        logger.debug("发送嵌入请求 - Provider: {}, 模型: {}, 维度: {}, 批次大小: {}, 文本预览: {}",
                provider.provider(), provider.model(), provider.dimension(), batch.size(),
                batch.isEmpty() ? "空" : batch.get(0).substring(0, Math.min(50, batch.get(0).length())) + "...");

        return modelCallGuard.mono("embedding", provider.provider(),
                        usageType == UsageType.QUERY ? ModelCallQueue.Priority.ONLINE : ModelCallQueue.Priority.BACKGROUND,
                        () -> buildClient(provider).post()
                                .uri("/embeddings")
                                .bodyValue(requestBody)
                                .retrieve()
                                .bodyToMono(String.class))
                .block(RequestDeadline.cap(Duration.ofMinutes(3)));
    }

    private WebClient buildClient(ModelProviderConfigService.ActiveProviderView provider) {
        WebClient.Builder builder = WebClient.builder()
                .baseUrl(ModelProviderConfigService.normalizeOpenAiCompatibleBaseUrl(provider.apiBaseUrl()))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                // WebClient 的默认缓冲区大小限制（256KB）, 这里调高到 16MB
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024));
        if (provider.apiKey() != null && !provider.apiKey().isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + provider.apiKey());
        }
        return builder.build();
    }

    private EmbeddingApiResponse parseEmbeddingResponse(String response, List<String> inputTexts) throws Exception {
        JsonNode jsonNode = objectMapper.readTree(response);
        JsonNode data = jsonNode.get("data");  // 兼容模式下使用data字段
        if (data == null || !data.isArray()) {
            throw new RuntimeException("API 响应格式错误: data 字段不存在或不是数组");
        }
        
        List<float[]> vectors = new ArrayList<>();
        for (JsonNode item : data) {
            JsonNode embedding = item.get("embedding");
            if (embedding != null && embedding.isArray()) {
                float[] vector = new float[embedding.size()];
                for (int i = 0; i < embedding.size(); i++) {
                    vector[i] = (float) embedding.get(i).asDouble();
                }
                vectors.add(vector);
            }
        }

        JsonNode usage = jsonNode.path("usage");
        int totalTokens = usage.path("total_tokens").asInt(usage.path("input_tokens").asInt(0));
        return new EmbeddingApiResponse(vectors, totalTokens > 0 ? totalTokens : usageQuotaService.estimateEmbeddingTokens(inputTexts));
    }

    public String currentModelVersion() {
        ModelProviderConfigService.ActiveProviderView provider = modelProviderConfigService.getActiveProvider(ModelProviderConfigService.SCOPE_EMBEDDING);
        return provider.provider() + ":" + provider.model() + ":" + provider.dimension();
    }

    private record EmbeddingApiResponse(List<float[]> vectors, int totalTokens) {
    }

    public record EmbeddingUsageResult(List<float[]> vectors, int totalTokens, String modelVersion) {
    }
}
