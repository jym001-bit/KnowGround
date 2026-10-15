package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.config.ModelCallProperties;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.Exceptions;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ModelCallGuardTest {
    private final ModelCallQueue queue = mock(ModelCallQueue.class);

    @Test
    void retryBudgetNeverExceedsTenPercentOfOriginalCalls() {
        ModelCallGuard.RetryBudget budget = new ModelCallGuard.RetryBudget();
        for (int i = 0; i < 9; i++) budget.newCall();
        assertFalse(budget.tryRetry());
        budget.newCall();
        assertTrue(budget.tryRetry());
        assertFalse(budget.tryRetry());
    }

    @Test
    void badRequestIsNotRetried() {
        ModelCallGuard guard = new ModelCallGuard(new ModelCallProperties(), queue);
        AtomicInteger attempts = new AtomicInteger();
        WebClientResponseException badRequest = response(400);
        assertThrows(WebClientResponseException.class, () -> guard.mono("llm", "test-400",
                ModelCallQueue.Priority.ONLINE, () -> {
                    attempts.incrementAndGet();
                    return Mono.error(badRequest);
                }).block());
        assertEquals(1, attempts.get());
    }

    @Test
    void breakerOpensAfterConfiguredFailures() {
        ModelCallProperties properties = new ModelCallProperties();
        properties.getLlm().setMinimumCalls(2);
        properties.getLlm().setFailureRatePercent(50);
        ModelCallGuard guard = new ModelCallGuard(properties, queue);
        for (int i = 0; i < 2; i++) {
            assertThrows(WebClientResponseException.class, () -> guard.mono("llm", "test-breaker",
                    ModelCallQueue.Priority.ONLINE, () -> Mono.error(response(503))).block());
        }
        assertThrows(CallNotPermittedException.class, () -> guard.mono("llm", "test-breaker",
                ModelCallQueue.Priority.ONLINE, () -> Mono.just("unused")).block());
    }

    @Test
    void emittedStreamContentIsNeverReplayedOnFailure() {
        ModelCallGuard guard = new ModelCallGuard(new ModelCallProperties(), queue);
        for (int i = 0; i < 10; i++) {
            assertEquals("ok", guard.mono("llm", "test-stream",
                    ModelCallQueue.Priority.ONLINE, () -> Mono.just("ok")).block());
        }
        AtomicInteger subscriptions = new AtomicInteger();
        assertThrows(WebClientResponseException.class, () -> guard.flux("llm", "test-stream",
                ModelCallQueue.Priority.ONLINE, () -> Flux.defer(() -> {
                    subscriptions.incrementAndGet();
                    return Flux.concat(Flux.just("visible"), Flux.error(response(503)));
                })).collectList().block());
        assertEquals(1, subscriptions.get());
    }

    @Test
    void retryAfterHeaderTakesPrecedenceOverJitter() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "3");
        assertTrue(ModelCallGuard.retryDelayMillis(
                new WebClientResponseException(429, "Too Many Requests", headers, new byte[0], null), 0) >= 3000);
    }

    @Test
    void configuredTimeoutStopsAnUnresponsiveProvider() {
        ModelCallProperties properties = new ModelCallProperties();
        properties.getLlm().setTimeoutMillis(50);
        ModelCallGuard guard = new ModelCallGuard(properties, queue);

        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> guard.mono("llm", "test-timeout",
                        ModelCallQueue.Priority.ONLINE, Mono::never).block());
        assertInstanceOf(TimeoutException.class, Exceptions.unwrap(failure));
    }

    private WebClientResponseException response(int status) {
        return new WebClientResponseException(status, "failure", new HttpHeaders(), new byte[0], null);
    }
}
