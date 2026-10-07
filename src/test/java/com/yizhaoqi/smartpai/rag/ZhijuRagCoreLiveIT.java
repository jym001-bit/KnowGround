package com.yizhaoqi.smartpai.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 知据 RAG 核心真实测试：直接访问正在运行的检索接口，不使用 Mockito。
 *
 * 正样本以“预期文档 + 答案关键词”标注相关片段，计算 Recall@1、Recall@K、
 * Precision@K 和 MRR；负样本检查无关问题是否被错误召回。
 * 类名以 IT 结尾，普通 mvn test 不会意外调用外部服务。
 */
@Execution(ExecutionMode.SAME_THREAD)
@DisplayName("知据 RAG 核心真实检索评估")
class ZhijuRagCoreLiveIT {
    private static volatile String cachedToken;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String base = System.getenv().getOrDefault("ZHIJU_TEST_BASE", "http://localhost:8081")
            .replaceAll("/$", "");

    @Test
    @DisplayName("01 未登录不能读取知识库文档列表")
    void anonymousCannotReadDocuments() throws Exception {
        var response = http.send(HttpRequest.newBuilder(URI.create(base + "/api/v1/documents/accessible"))
                .timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofString());
        save("security", Map.of("httpStatus", response.statusCode(), "body", response.body()));
        assertTrue(response.statusCode() == 401 || response.statusCode() == 403,
                "未登录应返回401或403，实际：" + response.statusCode());
    }

    @Test
    @DisplayName("02 批量评估真实检索的 Recall、Precision、MRR 和耗时")
    void retrievalQualityMeetsThresholds() throws Exception {
        JsonNode dataset = dataset();
        int topK = dataset.path("topK").asInt(3);//前三
        JsonNode cases = dataset.path("positiveCases");
        assertTrue(cases.isArray() && !cases.isEmpty(), "positiveCases 至少需要1条人工标注用例");

        List<Map<String, Object>> rows = new ArrayList<>();
        int hitAt1 = 0;
        int hitAtK = 0;
        int relevantReturned = 0;
        int returned = 0;
        double reciprocalRankSum = 0;
        List<Long> latencies = new ArrayList<>();
        //逐题
        for (JsonNode testCase : cases) {
            validatePositiveCase(testCase);
            long started = System.nanoTime();
            JsonNode results = search(testCase.path("question").asText(), topK);//拼接问题，以及返回topK个数
            long latencyMs = (System.nanoTime() - started) / 1_000_000;
            latencies.add(latencyMs);

            int firstRelevantRank = 0;//第一有关的位置排名
            int relevantCount = 0;
            List<Map<String, Object>> ranked = new ArrayList<>();
            for (int i = 0; i < results.size(); i++) {
                JsonNode result = results.get(i);
                boolean relevant = isRelevant(result, testCase);//比对文件 以及文件中的信息 都需要满足
                if (relevant) {
                    relevantCount++;
                    if (firstRelevantRank == 0) firstRelevantRank = i + 1;
                }
                ranked.add(Map.of(
                        "rank", i + 1,
                        "relevant", relevant,
                        "fileName", result.path("fileName").asText(""),
                        "fileMd5", result.path("fileMd5").asText(""),
                        "chunkId", result.path("chunkId").asInt(-1),
                        "score", result.path("score").asDouble(0),
                        "text", result.path("textContent").asText("")));
            }

            if (firstRelevantRank == 1) hitAt1++;//第一个就满足的
            if (firstRelevantRank > 0) {
                hitAtK++;//召回数量
                reciprocalRankSum += 1.0 / firstRelevantRank;
            }
            relevantReturned += relevantCount;
            returned += results.size();
            rows.add(Map.of(
                    "id", testCase.path("id").asText(),
                    "question", testCase.path("question").asText(),
                    "firstRelevantRank", firstRelevantRank,
                    "relevantReturned", relevantCount,
                    "latencyMs", latencyMs,
                    "results", ranked));
        }

        int count = cases.size();
        double recallAt1 = ratio(hitAt1, count);
        double recallAtK = ratio(hitAtK, count);//
        double precisionAtK = ratio(relevantReturned, returned);//精准 相关/总
        double mrr = reciprocalRankSum / count;//结果位置
        long p95LatencyMs = percentile95(latencies);//P95耗时

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("caseCount", count);
        metrics.put("topK", topK);
        metrics.put("recallAt1", recallAt1);
        metrics.put("recallAtK", recallAtK);
        metrics.put("precisionAtK", precisionAtK);
        metrics.put("mrr", mrr);
        metrics.put("p95LatencyMs", p95LatencyMs);
        metrics.put("definition", "相关片段必须同时命中人工标注文档和答案关键词；Recall@K为有至少一个相关片段的问题比例");
        save("retrieval-quality", Map.of("metrics", metrics, "cases", rows));
        System.out.println(json.writerWithDefaultPrettyPrinter().writeValueAsString(metrics));

        assertAll(
                () -> assertTrue(recallAt1 >= threshold(dataset, "minRecallAt1"), "Recall@1不达标：" + recallAt1),
                () -> assertTrue(recallAtK >= threshold(dataset, "minRecallAtK"), "Recall@K不达标：" + recallAtK),
                () -> assertTrue(precisionAtK >= threshold(dataset, "minPrecisionAtK"), "Precision@K不达标：" + precisionAtK),
                () -> assertTrue(mrr >= threshold(dataset, "minMrr"), "MRR不达标：" + mrr),
                () -> assertTrue(p95LatencyMs <= dataset.path("maxP95LatencyMs").asLong(15_000),
                        "P95检索耗时过高：" + p95LatencyMs + "ms"));
    }

    @Test
    @DisplayName("03 无关问题不应召回知识片段")
    void irrelevantQueriesDoNotOverRetrieve() throws Exception {
        JsonNode dataset = dataset();
        int topK = dataset.path("topK").asInt(3);
        JsonNode cases = dataset.path("negativeCases");
        assertTrue(cases.isArray() && !cases.isEmpty(), "negativeCases 至少需要1条无答案用例");
        List<Map<String, Object>> rows = new ArrayList<>();
        int falsePositives = 0;
        for (JsonNode testCase : cases) {
            String id = requiredText(testCase, "id");
            String question = requiredText(testCase, "question");
            JsonNode results = search(question, topK);
            boolean falsePositive = !results.isEmpty();
            if (falsePositive) falsePositives++;
            rows.add(Map.of("id", id, "question", question,
                    "falsePositive", falsePositive, "results", results));
        }
        double falsePositiveRate = ratio(falsePositives, cases.size());
        save("negative-retrieval", Map.of(
                "caseCount", cases.size(), "falsePositiveRate", falsePositiveRate, "cases", rows));
        assertTrue(falsePositiveRate <= threshold(dataset, "maxFalsePositiveRate"),
                "无关问题误召回率不达标：" + falsePositiveRate + "，详见target/zhiju-rag-results");
    }

    private boolean isRelevant(JsonNode result, JsonNode testCase) {
        String fileName = result.path("fileName").asText("");
        String fileMd5 = result.path("fileMd5").asText("");
        boolean documentMatches = contains(testCase.path("expectedFileNames"), fileName)
                || contains(testCase.path("expectedFileMd5s"), fileMd5);
        if (!documentMatches) return false;
        JsonNode keywords = testCase.path("answerKeywordsAny");
        if (!keywords.isArray() || keywords.isEmpty()) return true;
        String text = result.path("textContent").asText("");
        for (JsonNode keyword : keywords) if (text.contains(keyword.asText())) return true;
        return false;
    }

    private boolean contains(JsonNode array, String value) {
        if (!array.isArray() || value.isBlank()) return false;
        for (JsonNode item : array) if (value.equals(item.asText())) return true;
        return false;
    }

    private void validatePositiveCase(JsonNode testCase) {
        requiredText(testCase, "id");
        requiredText(testCase, "question");
        boolean hasFileName = testCase.path("expectedFileNames").isArray()
                && !testCase.path("expectedFileNames").isEmpty();
        boolean hasMd5 = testCase.path("expectedFileMd5s").isArray()
                && !testCase.path("expectedFileMd5s").isEmpty();
        assertTrue(hasFileName || hasMd5, "每个正样本必须人工指定正确文件名或MD5：" + testCase.path("id").asText());
        assertTrue(testCase.path("answerKeywordsAny").isArray()
                        && !testCase.path("answerKeywordsAny").isEmpty(),
                "每个正样本必须给出用于定位正确片段的答案关键词：" + testCase.path("id").asText());
    }

    private String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText();
        assertFalse(value.isBlank(), "缺少字段：" + field);
        return value;
    }

    private double threshold(JsonNode dataset, String name) {
        assertTrue(dataset.has(name) && dataset.path(name).isNumber(), "测试集缺少阈值：" + name);
        return dataset.path(name).asDouble();
    }

    private double ratio(int numerator, int denominator) {
        return denominator == 0 ? 0 : (double) numerator / denominator;
    }

    private long percentile95(List<Long> values) {
        List<Long> sorted = values.stream().sorted(Comparator.naturalOrder()).toList();
        int index = Math.max(0, (int) Math.ceil(sorted.size() * 0.95) - 1);
        return sorted.get(index);
    }

    private JsonNode search(String question, int topK) throws Exception {
        String route = "/api/v1/search/hybrid?topK=" + topK + "&query="
                + URLEncoder.encode(question, StandardCharsets.UTF_8);
        var request = HttpRequest.newBuilder(URI.create(base + route))
                .timeout(Duration.ofSeconds(90))
                .header("Authorization", "Bearer " + token())
                .GET().build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), "检索接口HTTP失败：" + response.body());
        JsonNode body = json.readTree(response.body());
        assertEquals(200, body.path("code").asInt(), "检索接口业务失败：" + body.path("message").asText());
        JsonNode data = body.path("data");
        assertTrue(data.isArray(), "检索结果data不是数组");
        assertTrue(data.size() <= topK, "返回数量超过topK");
        return data;
    }

    private JsonNode dataset() throws Exception {
        String configured = System.getenv("ZHIJU_RAG_DATASET");
        Path path = configured == null || configured.isBlank()
                ? Path.of("src", "test", "resources", "rag", "retrieval-cases.json")
                : Path.of(configured);
        assertTrue(Files.isRegularFile(path), "找不到测试集：" + path.toAbsolutePath());
        return json.readTree(path.toFile());
    }

    private synchronized String token() throws Exception {
        if (cachedToken != null && !cachedToken.isBlank()) return cachedToken;

        String suppliedToken = setting("ZHIJU_TEST_TOKEN");
        if (suppliedToken != null && !suppliedToken.isBlank()) {
            cachedToken = suppliedToken.trim();
            return cachedToken;
        }

        Map<String, String> dotenv = readDotEnv();
        String username = firstNonBlank(setting("ZHIJU_TEST_USERNAME"), dotenv.get("ADMIN_BOOTSTRAP_USERNAME"));
        String password = firstNonBlank(setting("ZHIJU_TEST_PASSWORD"), dotenv.get("ADMIN_BOOTSTRAP_PASSWORD"));
        assertNotNull(username,
                "无法自动登录：请设置 ZHIJU_TEST_TOKEN，或配置 ZHIJU_TEST_USERNAME/ZHIJU_TEST_PASSWORD");
        assertNotNull(password,
                "无法自动登录：项目.env中没有可用密码，请设置 ZHIJU_TEST_TOKEN 或 ZHIJU_TEST_PASSWORD");

        String payload = json.writeValueAsString(Map.of("username", username, "password", password));
        var request = HttpRequest.newBuilder(URI.create(base + "/api/v1/users/login"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8)).build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(),
                "真实测试自动登录失败（HTTP " + response.statusCode() + "）。可在IDEA配置ZHIJU_TEST_TOKEN后重试");
        JsonNode body = json.readTree(response.body());
        String loginToken = body.path("data").path("token").asText();
        assertFalse(loginToken.isBlank(),
                "真实测试自动登录未取得token。可在IDEA配置ZHIJU_TEST_TOKEN后重试");
        cachedToken = loginToken;
        return cachedToken;
    }

    private Map<String, String> readDotEnv() throws Exception {
        Map<String, String> values = new LinkedHashMap<>();
        Path path = Path.of(".env");
        if (!Files.isRegularFile(path)) return values;
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            int equals = trimmed.indexOf('=');
            if (equals <= 0) continue;
            String key = trimmed.substring(0, equals).trim();
            String value = trimmed.substring(equals + 1).trim();
            if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'")))) {
                value = value.substring(1, value.length() - 1);
            }
            values.put(key, value);
        }
        return values;
    }

    private String setting(String name) {
        return firstNonBlank(System.getProperty(name), System.getenv(name));
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private void save(String label, Object data) throws Exception {
        Path folder = Path.of("target", "zhiju-rag-results");
        Files.createDirectories(folder);
        Path output = folder.resolve(label + "-" + Instant.now().toEpochMilli() + ".json");
        json.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), data);
        System.out.println("真实测试报告：" + output.toAbsolutePath());
    }
}
