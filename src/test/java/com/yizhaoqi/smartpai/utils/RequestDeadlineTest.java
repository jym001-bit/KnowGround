package com.yizhaoqi.smartpai.utils;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class RequestDeadlineTest {
    @Test
    void nestedScopeCannotExtendOuterDeadline() {
        try (RequestDeadline.Scope outer = RequestDeadline.open(RequestDeadline.after(Duration.ofSeconds(2)))) {
            long outside = RequestDeadline.remaining().orElseThrow().toNanos();
            try (RequestDeadline.Scope inner = RequestDeadline.open(RequestDeadline.after(Duration.ofSeconds(10)))) {
                assertTrue(RequestDeadline.remaining().orElseThrow().toNanos() <= outside);
                assertTrue(RequestDeadline.cap(Duration.ofSeconds(30)).toMillis() <= 2000);
            }
            assertTrue(RequestDeadline.hasAtLeast(Duration.ofSeconds(1)));
        }
        assertTrue(RequestDeadline.remaining().isEmpty());
    }
}
