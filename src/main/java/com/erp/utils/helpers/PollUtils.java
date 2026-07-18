package com.erp.utils.helpers;

import lombok.extern.slf4j.Slf4j;

import java.util.function.Predicate;
import java.util.function.Supplier;

@Slf4j
public final class PollUtils {

    private static final long DEFAULT_INTERVAL_MS = 500;

    private PollUtils() {
    }

    public static <T> T waitUntil(Supplier<T> supplier,
                                  Predicate<T> condition,
                                  long timeoutMs,
                                  String description) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        T last = null;
        while (System.currentTimeMillis() < deadline) {
            last = supplier.get();
            if (condition.test(last)) {
                return last;
            }
            sleep(DEFAULT_INTERVAL_MS);
        }
        throw new IllegalStateException(
                description + " not satisfied within " + timeoutMs + "ms (last=" + last + ")");
    }

    public static void waitUntilTrue(Supplier<Boolean> supplier, long timeoutMs, String description) {
        waitUntil(supplier, Boolean.TRUE::equals, timeoutMs, description);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for " + millis + "ms", e);
        }
    }
}
