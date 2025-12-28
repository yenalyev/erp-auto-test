package com.erp.utils.config;

import org.aeonbits.owner.Config;

/**
 * Configuration interface using Owner library
 * Reads from config/${env}.properties
 */
@Config.Sources({
        "classpath:config/${env}.properties",
        "classpath:config/dev.properties"
})
public interface TestConfig extends Config {

    @Key("api.base.url")
    String baseUrl();

    @Key("api.auth.token")
    String authToken();

    @Key("api.timeout")
    @DefaultValue("30")
    int timeout();

    @Key("api.username")
    @DefaultValue("admin")
    String username();

    @Key("api.password")
    String password();
}