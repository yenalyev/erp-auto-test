package com.erp.utils.config;

import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class DotEnvLoaderTest {

    @Test
    public void mapsContainerEnvironmentWithoutOverridingCliProperties() {
        String previousUrl = System.getProperty("tcm.base.url");
        String previousToken = System.getProperty("tcm.api.token");
        try {
            System.setProperty("tcm.base.url", "http://cli-value");
            System.clearProperty("tcm.api.token");

            int loaded = DotEnvLoader.applyEnvironmentOverrides(Map.of(
                    "TCM_BASE_URL", "http://container-value",
                    "TCM_API_TOKEN", "container-token"));

            assertThat(loaded).isEqualTo(1);
            assertThat(System.getProperty("tcm.base.url")).isEqualTo("http://cli-value");
            assertThat(System.getProperty("tcm.api.token")).isEqualTo("container-token");
        } finally {
            restore("tcm.base.url", previousUrl);
            restore("tcm.api.token", previousToken);
        }
    }

    private static void restore(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
