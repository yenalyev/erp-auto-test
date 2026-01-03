package com.erp.validators;

import io.qameta.allure.Allure;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;

@Slf4j
public class ResponseValidator {

    private final Response response;
    private final List<String> errors = new ArrayList<>();
    private boolean useSchema = false;
    private String schemaPath;

    private ResponseValidator(Response response) {
        this.response = response;
    }

    public static ResponseValidator validate(Response response) {
        return new ResponseValidator(response);
    }

    public ResponseValidator usingSchema(String schemaPath) {
        this.useSchema = true;
        this.schemaPath = schemaPath;
        return this;
    }

    @Step("Validate response using JSON Schema: {this.schemaPath}")
    private void validateSchema() {
        if (!useSchema || schemaPath == null) {
            log.debug("⚠️ Schema validation skipped - no schema specified");
            return;
        }

        log.info("📋 Validating response against schema: {}", schemaPath);

        // ✅ 1. ДОДАЄМО SCHEMA ЯК ПАРАМЕТР
        Allure.parameter("JSON Schema Path", schemaPath);

        // ✅ 2. ДОДАЄМО ВМІСТ СХЕМИ
        attachSchemaContent(schemaPath);

        boolean validationSuccess = false;

        try {
            // ✅ 3. ВИКОНУЄМО ВАЛІДАЦІЮ
            response.then()
                    .assertThat()
                    .body(matchesJsonSchemaInClasspath(schemaPath));

            validationSuccess = true;
            log.info("✅ Schema validation PASSED: {}", schemaPath);

            // ✅ 4. ДОДАЄМО РЕЗУЛЬТАТ УСПІХУ
            Allure.addAttachment(
                    "✅ Schema Validation Result",
                    "text/plain",
                    String.format("Response successfully validated against schema:\n%s", schemaPath),
                    "txt"
            );

        } catch (AssertionError e) {
            validationSuccess = false;
            String errorDetails = extractValidationError(e);
            String error = String.format("Schema validation failed for '%s':\n%s", schemaPath, errorDetails);

            errors.add(error);
            log.error("❌ Schema validation FAILED: {}", schemaPath);
            log.error("Error details: {}", errorDetails);

            // ✅ 5. ДОДАЄМО ДЕТАЛЬНУ ПОМИЛКУ
            Allure.addAttachment(
                    "❌ Schema Validation Error",
                    "text/plain",
                    error,
                    "txt"
            );

        } catch (Exception e) {
            validationSuccess = false;
            String error = String.format("Schema validation exception for '%s': %s", schemaPath, e.getMessage());

            errors.add(error);
            log.error("❌ Schema validation EXCEPTION: {}", e.getMessage(), e);

            Allure.addAttachment(
                    "❌ Schema Validation Exception",
                    "text/plain",
                    error + "\n\nStack trace:\n" + getStackTraceAsString(e),
                    "txt"
            );
        }

//        // ✅ 6. ЗАПИСУЄМО СТАТИСТИКУ (якщо є)
//        try {
//            SchemaValidationStats.recordSchemaUsage(schemaPath, validationSuccess);
//        } catch (Exception e) {
//            log.warn("Failed to record schema stats: {}", e.getMessage());
//        }
    }

    /**
     * ✅ НОВИЙ МЕТОД: Додає вміст схеми до Allure
     */
    private void attachSchemaContent(String schemaPath) {
        log.debug("📎 Attaching schema content to Allure: {}", schemaPath);

        try {
            // Читаємо файл схеми з classpath
            InputStream inputStream = Thread.currentThread()
                    .getContextClassLoader()
                    .getResourceAsStream(schemaPath);

            if (inputStream == null) {
                log.warn("⚠️ Schema file not found in classpath: {}", schemaPath);
                Allure.addAttachment(
                        "⚠️ Schema File Not Found",
                        "text/plain",
                        String.format("Schema file not found in classpath: %s\n\n" +
                                "Expected location: src/test/resources/%s", schemaPath, schemaPath),
                        "txt"
                );
                return;
            }

            // Читаємо вміст файлу
            String schemaContent;
            try (Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8.name())) {
                scanner.useDelimiter("\\A");
                schemaContent = scanner.hasNext() ? scanner.next() : "";
            }

            if (schemaContent.isEmpty()) {
                log.warn("⚠️ Schema file is empty: {}", schemaPath);
                return;
            }

            // Витягуємо ім'я файлу
            String schemaFileName = extractSchemaFileName(schemaPath);

            // ✅ ДОДАЄМО ЯК JSON ATTACHMENT
            Allure.addAttachment(
                    "📋 JSON Schema: " + schemaFileName,
                    "application/json",
                    schemaContent,
                    "json"
            );

            log.debug("✅ Schema content attached successfully: {} ({} bytes)",
                    schemaFileName, schemaContent.length());

        } catch (Exception e) {
            log.warn("⚠️ Failed to attach schema content: {}", e.getMessage(), e);
            Allure.addAttachment(
                    "⚠️ Schema Attachment Error",
                    "text/plain",
                    String.format("Failed to attach schema content for: %s\nError: %s", schemaPath, e.getMessage()),
                    "txt"
            );
        }
    }

    /**
     * Витягує ім'я файлу зі шляху
     */
    private String extractSchemaFileName(String schemaPath) {
        if (schemaPath == null || schemaPath.isEmpty()) {
            return "unknown.json";
        }

        String[] parts = schemaPath.split("/");
        return parts[parts.length - 1];
    }

    /**
     * Витягує детальну інформацію про помилку валідації
     */
    private String extractValidationError(AssertionError error) {
        String message = error.getMessage();

        if (message == null) {
            return "No error details available";
        }

        // JSON Schema validation errors зазвичай містять детальну інформацію
        // Форматуємо її для кращої читабельності
        return message
                .replace("1 error:", "\n1 error:")
                .replace("error:", "\nerror:")
                .trim();
    }

    /**
     * Перетворює stack trace в string
     */
    private String getStackTraceAsString(Exception e) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : e.getStackTrace()) {
            sb.append("    at ").append(element.toString()).append("\n");
        }
        return sb.toString();
    }

    // ========== MANUAL FIELD VALIDATION ==========

    public ResponseValidator hasField(String fieldName) {
        try {
            Object value = response.jsonPath().get(fieldName);
            if (value == null) {
                errors.add(String.format("Field '%s' is missing", fieldName));
            }
        } catch (Exception e) {
            errors.add(String.format("Field '%s' not found: %s", fieldName, e.getMessage()));
        }
        return this;
    }

    public ResponseValidator hasNonEmptyField(String fieldName) {
        try {
            Object value = response.jsonPath().get(fieldName);
            if (value == null) {
                errors.add(String.format("Field '%s' is null", fieldName));
            } else if (value instanceof String && ((String) value).isEmpty()) {
                errors.add(String.format("Field '%s' is empty string", fieldName));
            }
        } catch (Exception e) {
            errors.add(String.format("Field '%s' validation failed: %s", fieldName, e.getMessage()));
        }
        return this;
    }

    public ResponseValidator hasPositiveNumber(String fieldName) {
        try {
            Number value = response.jsonPath().get(fieldName);
            if (value == null) {
                errors.add(String.format("Field '%s' is null", fieldName));
            } else if (value.longValue() <= 0) {
                errors.add(String.format("Field '%s' is not positive: %s", fieldName, value));
            }
        } catch (Exception e) {
            errors.add(String.format("Field '%s' validation failed: %s", fieldName, e.getMessage()));
        }
        return this;
    }

    public ResponseValidator isArray() {
        try {
            List<?> list = response.jsonPath().getList("$");
            if (list == null) {
                errors.add("Response is not an array");
            }
        } catch (Exception e) {
            errors.add("Failed to parse response as array: " + e.getMessage());
        }
        return this;
    }

    public ResponseValidator hasMinArraySize(int minSize) {
        try {
            List<?> list = response.jsonPath().getList("$");
            if (list == null) {
                errors.add("Response is not an array");
            } else if (list.size() < minSize) {
                errors.add(String.format("Array size %d is less than minimum %d", list.size(), minSize));
            }
        } catch (Exception e) {
            errors.add("Array size validation failed: " + e.getMessage());
        }
        return this;
    }

    public ResponseValidator eachArrayItemHasField(String fieldName) {
        try {
            List<?> list = response.jsonPath().getList("$");
            if (list == null) {
                errors.add("Response is not an array");
                return this;
            }

            for (int i = 0; i < list.size(); i++) {
                Object value = response.jsonPath().get(String.format("[%d].%s", i, fieldName));
                if (value == null) {
                    errors.add(String.format("Array item [%d] is missing field '%s'", i, fieldName));
                }
            }
        } catch (Exception e) {
            errors.add("Array items validation failed: " + e.getMessage());
        }
        return this;
    }

    public ResponseValidator hasArrayField(String fieldName) {
        try {
            List<?> list = response.jsonPath().getList(fieldName);
            if (list == null) {
                errors.add(String.format("Field '%s' is not an array or doesn't exist", fieldName));
            }
        } catch (Exception e) {
            errors.add(String.format("Array field '%s' validation failed: %s", fieldName, e.getMessage()));
        }
        return this;
    }

    public ResponseValidator eachItemInArrayField(String arrayField, String itemField) {
        try {
            List<?> list = response.jsonPath().getList(arrayField);
            if (list == null) {
                errors.add(String.format("Field '%s' is not an array", arrayField));
                return this;
            }

            for (int i = 0; i < list.size(); i++) {
                Object value = response.jsonPath().get(String.format("%s[%d].%s", arrayField, i, itemField));
                if (value == null) {
                    errors.add(String.format("Array '%s' item [%d] is missing field '%s'",
                            arrayField, i, itemField));
                }
            }
        } catch (Exception e) {
            errors.add(String.format("Array field '%s' items validation failed: %s", arrayField, e.getMessage()));
        }
        return this;
    }

    public ResponseValidator isPaginated() {
        return this
                .hasArrayField("content")
                .hasPositiveNumber("totalElements")
                .hasField("totalPages")
                .hasField("size")
                .hasField("number");
    }

    public ResponseValidator validateIf(boolean condition,
                                        java.util.function.Consumer<ResponseValidator> validation) {
        if (condition) {
            validation.accept(this);
        }
        return this;
    }

    // ========== FINAL ASSERTION ==========

    @Step("Assert response is valid")
    public void assertValid() {
        log.debug("🔍 Starting final validation assertion...");

        // Спочатку валідуємо через schema
        if (useSchema) {
            log.debug("📋 Schema validation enabled, validating...");
            validateSchema();
        } else {
            log.debug("⚠️ Schema validation disabled");
        }

        // Перевіряємо чи є помилки
        if (!errors.isEmpty()) {
            String errorMessage = String.format(
                    "Response validation failed (%d errors):\n%s",
                    errors.size(),
                    String.join("\n", errors)
            );

            log.error("❌ Validation failed with {} errors", errors.size());

            // Додаємо всі помилки до Allure
            Allure.addAttachment(
                    "❌ Validation Errors",
                    "text/plain",
                    errorMessage,
                    "txt"
            );

            throw new AssertionError(errorMessage);
        }

        log.debug("✅ Response validation passed (Schema: {}, Errors: 0)",
                useSchema ? schemaPath : "none");
    }
}