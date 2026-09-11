package com.erp.tests.framework;

import com.erp.utils.config.ConfigProvider;
import java.util.LinkedHashMap;
import java.util.Map;

/** Scoped configuration overrides for the sequential local framework suite. */
final class FrameworkProperties implements AutoCloseable {
    private final Map<String, String> previous = new LinkedHashMap<>();

    FrameworkProperties(Map<String, String> overrides) {
        overrides.forEach((key, value) -> {
            previous.put(key, System.getProperty(key));
            System.setProperty(key, value);
        });
        ConfigProvider.reload();
    }

    @Override
    public void close() {
        previous.forEach((key, value) -> {
            if (value == null) System.clearProperty(key);
            else System.setProperty(key, value);
        });
        ConfigProvider.reload();
    }
}
