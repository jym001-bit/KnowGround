package com.yizhaoqi.smartpai.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbeddingSwitchGateTest {
    @Test
    void cutoverWaitsForAnInFlightQueryToFinish() throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        try {
            EmbeddingSwitchGate.Permit query = EmbeddingSwitchGate.read();
            CountDownLatch entered = new CountDownLatch(1);
            var switchAttempt = executor.submit(() -> {
                entered.countDown();
                try (EmbeddingSwitchGate.Permit ignored = EmbeddingSwitchGate.write()) {
                    return true;
                }
            });
            try {
                assertTrue(entered.await(1, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> switchAttempt.get(100, TimeUnit.MILLISECONDS));
            } finally {
                query.close();
            }
            assertTrue(switchAttempt.get(1, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }
}
