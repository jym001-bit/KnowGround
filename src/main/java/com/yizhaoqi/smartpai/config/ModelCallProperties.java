package com.yizhaoqi.smartpai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "model-calls")
public class ModelCallProperties {
    private final Scope llm = new Scope(5, 90_000);
    private final Scope embedding = new Scope(10, 30_000);

    public Scope getLlm() { return llm; }
    public Scope getEmbedding() { return embedding; }

    public Settings resolve(String scope, String provider) {
        Scope base = "embedding".equals(scope) ? embedding : llm;
        Override override = base.providers.get(provider);
        int qps = override != null && override.qps != null ? override.qps : base.qps;
        int timeout = override != null && override.timeoutMillis != null ? override.timeoutMillis : base.timeoutMillis;
        int minCalls = override != null && override.minimumCalls != null ? override.minimumCalls : base.minimumCalls;
        int failureRate = override != null && override.failureRatePercent != null
                ? override.failureRatePercent : base.failureRatePercent;
        int openSeconds = override != null && override.openSeconds != null ? override.openSeconds : base.openSeconds;
        return new Settings(Math.max(1, qps), Math.max(1, timeout), Math.max(1, minCalls),
                Math.max(1, Math.min(100, failureRate)), Math.max(1, openSeconds),
                Math.max(0, base.maxRetries), Math.max(1, base.queueCapacity));
    }

    public static class Scope {
        private int qps;
        private int timeoutMillis;
        private int minimumCalls = 10;
        private int failureRatePercent = 50;
        private int openSeconds = 30;
        private int maxRetries = 2;
        private int queueCapacity = 100;
        private Map<String, Override> providers = new HashMap<>();

        public Scope() { }
        public Scope(int qps, int timeoutMillis) { this.qps = qps; this.timeoutMillis = timeoutMillis; }
        public int getQps() { return qps; }
        public void setQps(int qps) { this.qps = qps; }
        public int getTimeoutMillis() { return timeoutMillis; }
        public void setTimeoutMillis(int timeoutMillis) { this.timeoutMillis = timeoutMillis; }
        public int getMinimumCalls() { return minimumCalls; }
        public void setMinimumCalls(int minimumCalls) { this.minimumCalls = minimumCalls; }
        public int getFailureRatePercent() { return failureRatePercent; }
        public void setFailureRatePercent(int failureRatePercent) { this.failureRatePercent = failureRatePercent; }
        public int getOpenSeconds() { return openSeconds; }
        public void setOpenSeconds(int openSeconds) { this.openSeconds = openSeconds; }
        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
        public int getQueueCapacity() { return queueCapacity; }
        public void setQueueCapacity(int queueCapacity) { this.queueCapacity = queueCapacity; }
        public Map<String, Override> getProviders() { return providers; }
        public void setProviders(Map<String, Override> providers) { this.providers = providers; }
    }

    public static class Override {
        private Integer qps;
        private Integer timeoutMillis;
        private Integer minimumCalls;
        private Integer failureRatePercent;
        private Integer openSeconds;
        public Integer getQps() { return qps; }
        public void setQps(Integer qps) { this.qps = qps; }
        public Integer getTimeoutMillis() { return timeoutMillis; }
        public void setTimeoutMillis(Integer timeoutMillis) { this.timeoutMillis = timeoutMillis; }
        public Integer getMinimumCalls() { return minimumCalls; }
        public void setMinimumCalls(Integer minimumCalls) { this.minimumCalls = minimumCalls; }
        public Integer getFailureRatePercent() { return failureRatePercent; }
        public void setFailureRatePercent(Integer failureRatePercent) { this.failureRatePercent = failureRatePercent; }
        public Integer getOpenSeconds() { return openSeconds; }
        public void setOpenSeconds(Integer openSeconds) { this.openSeconds = openSeconds; }
    }

    public record Settings(int qps, int timeoutMillis, int minimumCalls, int failureRatePercent,
                           int openSeconds, int maxRetries, int queueCapacity) { }
}
