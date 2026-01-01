package com.erp.utils.parser;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AllureResultsParser {
    private static final String ALLURE_RESULTS_PATH = "target/allure-results";
    private static final Gson gson = new Gson();

    public static class TestResult {
        String testClass;
        String testMethod;
        String epic;
        String feature;
        String story;
        String status;
        String lastRun;
        long duration;
        String bugId;

        public List<Object> toRowData() {
            return Arrays.asList(
                    testClass, testMethod, epic, feature, story,
                    status, lastRun, duration + "ms", bugId
            );
        }
    }

    public List<TestResult> parseAllureResults() throws IOException {
        List<TestResult> results = new ArrayList<>();

        File resultsDir = new File(ALLURE_RESULTS_PATH);
        if (!resultsDir.exists()) {
            System.out.println("Allure results directory not found");
            return results;
        }

        // Читаємо всі *-result.json файли
        File[] resultFiles = resultsDir.listFiles((dir, name) ->
                name.endsWith("-result.json"));

        if (resultFiles != null) {
            for (File file : resultFiles) {
                TestResult result = parseResultFile(file);
                if (result != null) {
                    results.add(result);
                }
            }
        }

        return results;
    }

    private TestResult parseResultFile(File file) throws IOException {
        JsonObject json = gson.fromJson(new FileReader(file), JsonObject.class);

        TestResult result = new TestResult();
        result.testMethod = json.get("name").getAsString();
        result.status = json.get("status").getAsString();
        result.duration = json.get("stop").getAsLong() - json.get("start").getAsLong();
        result.lastRun = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                .format(new java.util.Date(json.get("stop").getAsLong()));

        // Витягуємо класс з fullName
        String fullName = json.get("fullName").getAsString();
        result.testClass = fullName.substring(0, fullName.lastIndexOf("."));

        // Парсимо labels (Epic, Feature, Story)
        JsonArray labels = json.getAsJsonArray("labels");
        result.epic = "";
        result.feature = "";
        result.story = "";
        result.bugId = "";

        for (int i = 0; i < labels.size(); i++) {
            JsonObject label = labels.get(i).getAsJsonObject();
            String name = label.get("name").getAsString();
            String value = label.get("value").getAsString();

            switch (name) {
                case "epic":
                    result.epic = value;
                    break;
                case "feature":
                    result.feature = value;
                    break;
                case "story":
                    result.story = value;
                    break;
            }
        }

        // Витягуємо Bug ID з links якщо є
        if (json.has("links")) {
            JsonArray links = json.getAsJsonArray("links");
            for (int i = 0; i < links.size(); i++) {
                JsonObject link = links.get(i).getAsJsonObject();
                if ("issue".equals(link.get("type").getAsString())) {
                    result.bugId = link.get("name").getAsString();
                    break;
                }
            }
        }

        return result;
    }
}
