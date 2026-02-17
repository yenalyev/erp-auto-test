package com.erp.utils.helpers;

import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.models.rbac.EndpointAccessRule;
import io.qameta.allure.Allure;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Slf4j
public class AllureHelper {

    /**
     * Прикріплює очікувану схему та фактичний JSON в Allure для порівняння
     */
    @Step("Attach Schema Validation Details (Expected vs Actual)")
    public static void attachSchemaValidationInfo(EndpointAccessRule rule, Response response) {
        // 1. Прикріплюємо Actual Body
        String actualJson;
        try {
            actualJson = response.jsonPath().prettify();
        } catch (Exception e) {
            actualJson = response.body().asString();
        }
        Allure.addAttachment("🔍 Actual Response Body", "application/json", actualJson, "json");

        // 2. Прикріплюємо Expected Schema
        String schemaPath = rule.getSchemaPath();
        if (schemaPath == null || schemaPath.isEmpty()) return;

        try (InputStream schemaStream = AllureHelper.class.getClassLoader().getResourceAsStream(schemaPath)) {
            if (schemaStream != null) {
                String schemaContent = new String(schemaStream.readAllBytes(), StandardCharsets.UTF_8);
                Allure.addAttachment("📜 Expected JSON Schema (" + schemaPath + ")",
                        "application/json", schemaContent, "json");
            } else {
                log.warn("Could not find schema file at: {}", schemaPath);
            }
        } catch (Exception e) {
            log.error("Failed to read schema file for attachment", e);
        }
    }


    /**
     * 🔥 Версія для функціональних тестів (використовує ApiEndpointDefinition)
     */
    @Step("Validation Details for {definition.name}")
    public static void attachSchemaValidationInfo(ApiEndpointDefinition definition, Response response) {
        // 1. Прикріплюємо Actual Body
        String actualJson = response.jsonPath().prettify();
        Allure.addAttachment("🔍 Actual Response Body", "application/json", actualJson, "json");

        // 2. Прикріплюємо Expected Schema
        String schemaPath = definition.getSchemaPath();
        if (schemaPath == null || schemaPath.isEmpty()) {
            log.warn("No schema path defined for endpoint: {}", definition.name());
            return;
        }

        try (InputStream schemaStream = AllureHelper.class.getClassLoader().getResourceAsStream(schemaPath)) {
            if (schemaStream != null) {
                String schemaContent = new String(schemaStream.readAllBytes(), StandardCharsets.UTF_8);
                Allure.addAttachment("📜 Expected JSON Schema (" + schemaPath + ")",
                        "application/json", schemaContent, "json");
            }
        } catch (Exception e) {
            log.error("Failed to read schema file: {}", schemaPath, e);
        }
    }

    @Step("Attach Response Details")
    public static void attachResponseDetails(Response response) {
        Allure.addAttachment("Response Status", String.valueOf(response.statusCode()));
        String body = response.body().asString();
        if (body != null && !body.isEmpty()) {
            Allure.addAttachment("Response Body", "application/json", body, "json");
        }
    }
}