package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.config.ModelCallProperties;
import com.yizhaoqi.smartpai.utils.RequestDeadline;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.timelimiter.TimeLimiterOperator;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.io.IOException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

@Service
public class ModelCallGuard {
    private final ModelCallProperties properties;
    private final ModelCallQueue queue;
    private final Map<String, CircuitBreaker> breakers = new ConcurrentHashMap<>();
    private final Map<String, RetryBudget> budgets = new ConcurrentHashMap<>();

    public ModelCallGuard(ModelCallProperties properties, ModelCallQueue queue) {
        this.properties = properties;
        this.queue = queue;
    }

    public <T> Mono<T> mono(String scope, String provider, ModelCallQueue.Priority priority,
                            Supplier<Mono<T>> call) {
        Context ctx = context(scope, provider, priority);
        return attemptMono(ctx, call).retryWhen(retry(ctx, new AtomicBoolean(false)));
    }

    public <T> Flux<T> flux(String scope, String provider, ModelCallQueue.Priority priority,
                            Supplier<Flux<T>> call) {
        Context ctx = context(scope, provider, priority);
        AtomicBoolean emitted = new AtomicBoolean(false);
        return attemptFlux(ctx, call).doOnNext(item -> emitted.set(true)).retryWhen(retry(ctx, emitted));
    }

    private Context context(String scope, String provider, ModelCallQueue.Priority priority) {
        String key = scope + ":" + provider;
        ModelCallProperties.Settings settings = properties.resolve(scope, provider);
        CircuitBreaker breaker = breakers.computeIfAbsent(key, ignored -> CircuitBreaker.of(key,
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(settings.failureRatePercent())
                        .minimumNumberOfCalls(settings.minimumCalls())
                        .slidingWindowSize(Math.max(20, settings.minimumCalls()))
                        .waitDurationInOpenState(Duration.ofSeconds(settings.openSeconds()))
                        .recordException(ModelCallGuard::isTransient)
                        .build()));
        RetryBudget budget = budgets.computeIfAbsent(key, ignored -> new RetryBudget());
        budget.newCall();
        return new Context(scope, provider, priority, settings, breaker, budget,
                RequestDeadline.currentNanos().orElse(Long.MAX_VALUE));
    }

    private <T> Mono<T> attemptMono(Context ctx, Supplier<Mono<T>> call) {
        return Mono.fromCallable(() -> {
                    queue.acquire(ctx.scope, ctx.provider, ctx.priority, ctx.settings, ctx.deadlineNanos);
                    return true;
                }).subscribeOn(Schedulers.boundedElastic())
                .flatMap(ignored -> Mono.defer(call)
                        .transformDeferred(TimeLimiterOperator.of(timeLimiter(ctx)))
                        .timeout(timeoutDuration(ctx)))
                .transformDeferred(CircuitBreakerOperator.of(ctx.breaker));
    }

    private <T> Flux<T> attemptFlux(Context ctx, Supplier<Flux<T>> call) {
        return Mono.fromCallable(() -> {
                    queue.acquire(ctx.scope, ctx.provider, ctx.priority, ctx.settings, ctx.deadlineNanos);
                    return true;
                }).subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(ignored -> Flux.defer(call)
                        .transformDeferred(TimeLimiterOperator.of(timeLimiter(ctx)))
                        .timeout(timeoutDuration(ctx)))
                .transformDeferred(CircuitBreakerOperator.of(ctx.breaker));
    }

    private TimeLimiter timeLimiter(Context ctx) {
        return TimeLimiter.of(ctx.scope + ":" + ctx.provider,
                TimeLimiterConfig.custom().timeoutDuration(timeoutDuration(ctx)).build());
    }

    private Duration timeoutDuration(Context ctx) {
        long configured = TimeUnit.MILLISECONDS.toNanos(ctx.settings.timeoutMillis());
        long left = remainingNanos(ctx.deadlineNanos);
        long timeout = Math.max(1, Math.min(configured, left));
        return Duration.ofNanos(timeout);
    }

    private Retry retry(Context ctx, AtomicBoolean emitted) {
        return Retry.from(signals -> signals.concatMap(signal -> {
            Throwable failure = signal.failure();
            if (emitted.get() || signal.totalRetries() >= ctx.settings.maxRetries()
                    || !isTransient(failure)) {
                return Mono.error(failure);
            }
            long delay = retryDelayMillis(failure, (int) signal.totalRetries());
            if (remainingNanos(ctx.deadlineNanos) <= TimeUnit.MILLISECONDS.toNanos(delay + 1)) {
                return Mono.error(failure);
            }
            if (!ctx.budget.tryRetry()) return Mono.error(failure);
            return Mono.delay(Duration.ofMillis(delay));
        }));
    }

    static long retryDelayMillis(Throwable failure, int retries) {
        long base = Math.min(4_000, 250L << Math.min(retries, 4));
        long jittered = ThreadLocalRandom.current().nextLong(Math.max(1, base / 2), base + base / 2 + 1);
        if (failure instanceof WebClientResponseException response) {
            String header = response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER);
            if (header != null) {
                try { return Math.max(jittered, TimeUnit.SECONDS.toMillis(Long.parseLong(header.trim()))); }
                catch (NumberFormatException ignored) {
                    try {
                        long until = Duration.between(ZonedDateTime.now(),
                                ZonedDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME)).toMillis();
                        return Math.max(jittered, Math.max(0, until));
                    } catch (Exception ignoredDate) { /* invalid header */ }
                }
            }
        }
        return jittered;
    }

    static boolean isTransient(Throwable failure) {
        if (failure instanceof WebClientResponseException response) {
            int status = response.getStatusCode().value();
            return status == 408 || status == 429 || status >= 500;
        }
        return failure instanceof WebClientRequestException
                || failure instanceof TimeoutException || failure instanceof IOException;
    }

    private static long remainingNanos(long deadline) {
        return deadline == Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(0, deadline - System.nanoTime());
    }

    private record Context(String scope, String provider, ModelCallQueue.Priority priority,
                           ModelCallProperties.Settings settings, CircuitBreaker breaker,
                           RetryBudget budget, long deadlineNanos) { }

    static final class RetryBudget {
        private long minute = System.currentTimeMillis() / 60_000;
        private long calls;
        private long retries;

        synchronized void newCall() {
            resetIfNeeded();
            calls++;
        }

        synchronized boolean tryRetry() {
            resetIfNeeded();
            if ((retries + 1) * 10 > calls) return false;
            retries++;
            return true;
        }

        private void resetIfNeeded() {
            long now = System.currentTimeMillis() / 60_000;
            if (now != minute) { minute = now; calls = 0; retries = 0; }
        }
    }
}
