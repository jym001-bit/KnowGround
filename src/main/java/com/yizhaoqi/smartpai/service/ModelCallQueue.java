package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.config.ModelCallProperties;
import com.yizhaoqi.smartpai.exception.RateLimitExceededException;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Service
public class ModelCallQueue {
    public enum Priority { ONLINE, BACKGROUND }

    private final AtomicTokenBucket bucket;
    private final Map<String, Semaphore> waiting = new ConcurrentHashMap<>();

    public ModelCallQueue(AtomicTokenBucket bucket) { this.bucket = bucket; }

    public void acquire(String scope, String provider, Priority priority,
                        ModelCallProperties.Settings settings, long deadlineNanos) {
        String key = "model:qps:{" + scope + ":" + provider + "}";
        String lane = key + ":" + priority;
        Semaphore slots = waiting.computeIfAbsent(lane,
                ignored -> new Semaphore(settings.queueCapacity(), true));
        if (!slots.tryAcquire()) throw new RateLimitExceededException("模型供应商等待队列已满", 1);
        try {
            long maxWait = priority == Priority.ONLINE ? 5_000 : 60_000;
            long queueDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(maxWait);
            while (true) {
                long left = Math.min(queueDeadline, deadlineNanos) - System.nanoTime();
                if (left <= 0) throw new RateLimitExceededException("等待模型供应商令牌超时", 1);
                long wait = bucket.take(key, settings.qps(), settings.qps(), priority == Priority.BACKGROUND);
                if (wait == 0) return;
                long pause = Math.min(TimeUnit.NANOSECONDS.toMillis(left), Math.min(wait, 200));
                if (pause <= 0) throw new RateLimitExceededException("等待模型供应商令牌超时", 1);
                Thread.sleep(pause);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待模型供应商令牌被中断", e);
        } finally {
            slots.release();
        }
    }
}
