package com.erp.utils.parser;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class AllureResultsParser {

    private static final String ALLURE_RESULTS_DIR = "target/allure-results";
    private final Gson gson = new Gson();

    /**
     * Парсинг всіх Allure результатів
     */
    public List<TestResult> parseAllureResults() throws IOException {
        List<TestResult> results = new ArrayList<>();
        Path resultsPath = Paths.get(ALLURE_RESULTS_DIR);

        if (!Files.exists(resultsPath)) {
            log.warn("⚠️  Allure results directory not found: {}", ALLURE_RESULTS_DIR);
            return results;
        }

        try (Stream<Path> paths = Files.walk(resultsPath)) {
            List<File> jsonFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith("-result.json"))
                    .map(Path::toFile)
                    .collect(Collectors.toList());

            log.info("📄 Found {} Allure result files", jsonFiles.size());

            for (File file : jsonFiles) {
                try {
                    TestResult result = parseResultFile(file);
                    if (result != null) {
                        results.add(result);
                    }
                } catch (Exception e) {
                    log.error("❌ Failed to parse file {}: {}", file.getName(), e.getMessage());
                }
            }
        }

        log.info("✅ Parsed {} test results", results.size());
        return results;
    }

    /**
     * Парсинг одного файлу результату
     */
    private TestResult parseResultFile(File file) throws IOException {
        try (FileReader reader = new FileReader(file)) {
            JsonObject json = gson.fromJson(reader, JsonObject.class);

            TestResult result = new TestResult();

            // Basic info
            result.setTestClass(getTestClass(json));
            result.setTestMethod(json.get("name").getAsString());
            result.setStatus(json.get("status").getAsString());

            // Labels (Epic, Feature, Story)
            if (json.has("labels")) {
                JsonArray labels = json.getAsJsonArray("labels");
                for (int i = 0; i < labels.size(); i++) {
                    JsonObject label = labels.get(i).getAsJsonObject();
                    String name = label.get("name").getAsString();
                    String value = label.get("value").getAsString();

                    switch (name) {
                        case "epic":
                            result.setEpic(value);
                            break;
                        case "feature":
                            result.setFeature(value);
                            break;
                        case "story":
                            result.setStory(value);
                            break;
                    }
                }
            }

            // Timing
            if (json.has("start") && json.has("stop")) {
                long start = json.get("start").getAsLong();
                long stop = json.get("stop").getAsLong();
                long duration = stop - start;

                result.setDuration(formatDuration(duration));
                result.setLastRun(formatTimestamp(start));
            }

            // Links (Bug ID)
            if (json.has("links")) {
                JsonArray links = json.getAsJsonArray("links");
                for (int i = 0; i < links.size(); i++) {
                    JsonObject link = links.get(i).getAsJsonObject();
                    if ("issue".equals(link.get("type").getAsString())) {
                        result.setBugId(link.get("name").getAsString());
                        break;
                    }
                }
            }

            return result;
        }
    }

    /**
     * Витягти назву тест класу
     */
    private String getTestClass(JsonObject json) {
        if (json.has("labels")) {
            JsonArray labels = json.getAsJsonArray("labels");
            for (int i = 0; i < labels.size(); i++) {
                JsonObject label = labels.get(i).getAsJsonObject();
                if ("testClass".equals(label.get("name").getAsString())) {
                    String fullName = label.get("value").getAsString();
                    // Повертаємо тільки назву класу без пакету
                    return fullName.substring(fullName.lastIndexOf('.') + 1);
                }
            }
        }
        return "Unknown";
    }

    /**
     * Форматувати тривалість
     */
    private String formatDuration(long milliseconds) {
        double seconds = milliseconds / 1000.0;
        return String.format("%.2fs", seconds);
    }

    /**
     * Форматувати timestamp
     */
    private String formatTimestamp(long milliseconds) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(new Date(milliseconds));
    }

    /**
     * Test Result Model
     */
    @Data
    public static class TestResult {
        private String testClass;
        private String testMethod;
        private String epic;
        private String feature;
        private String story;
        private String status;
        private String lastRun;
        private String duration;
        private String bugId;

        /**
         * Конвертація в рядок для Google Sheets (deprecated, використовуйте GoogleSheetsHelper.TestResult)
         */
        @Deprecated
        public List<Object> toRowData() {
            return Arrays.asList(
                    testClass != null ? testClass : "",
                    testMethod != null ? testMethod : "",
                    epic != null ? epic : "",
                    feature != null ? feature : "",
                    story != null ? story : "",
                    status != null ? status : "",
                    lastRun != null ? lastRun : "",
                    duration != null ? duration : "",
                    bugId != null ? bugId : ""
            );
        }
    }
}