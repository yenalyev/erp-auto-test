package com.erp.utils.helpers;

import com.erp.dto.tcm.TcmImportResponse;
import com.erp.dto.tcm.TcmResultDto;
import com.erp.dto.tcm.TcmRunImportRequest;
import com.erp.dto.tcm.TcmSuiteDto;
import com.erp.utils.config.ConfigProvider;
import io.restassured.http.ContentType;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static io.restassured.RestAssured.given;

@Slf4j
public class TcmApiClient {

    public static final String API_TOKEN_HEADER = "X-TCM-Api-Token";

    private TcmApiClient() {
    }

    public static TcmSuiteDto fetchFeatureSuite(long featureId) {
        return given()
                .baseUri(ConfigProvider.getTcmBaseUrl())
                .header(API_TOKEN_HEADER, ConfigProvider.getTcmApiToken())
                .when()
                .get("/api/autotest/suites/features/{featureId}", featureId)
                .then()
                .statusCode(200)
                .extract()
                .as(TcmSuiteDto.class);
    }

    public static TcmSuiteDto fetchAcSuite(long acId) {
        return given()
                .baseUri(ConfigProvider.getTcmBaseUrl())
                .header(API_TOKEN_HEADER, ConfigProvider.getTcmApiToken())
                .when()
                .get("/api/autotest/suites/ac/{acId}", acId)
                .then()
                .statusCode(200)
                .extract()
                .as(TcmSuiteDto.class);
    }

    public static TcmImportResponse submitRun(TcmRunImportRequest request) {
        String baseUrl = ConfigProvider.getTcmBaseUrl();
        String token = ConfigProvider.getTcmApiToken();

        var response = given()
                .baseUri(baseUrl)
                .header(API_TOKEN_HEADER, token)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/autotest/runs");

        int statusCode = response.getStatusCode();
        if (statusCode < 200 || statusCode >= 300) {
            String body = response.getBody().asString();
            throw new IllegalStateException(
                    "TCM import failed with HTTP " + statusCode + ": " + summarizeErrorBody(body));
        }
        return response.as(TcmImportResponse.class);
    }

    private static String summarizeErrorBody(String body) {
        if (body == null || body.isBlank()) {
            return "(empty response)";
        }
        String trimmed = body.trim();
        return trimmed.length() > 500 ? trimmed.substring(0, 500) + "…" : trimmed;
    }

    public static TcmRunImportRequest buildRequest(String suiteName, List<BufferedResult> bufferedResults) {
        String env = System.getProperty("env", "debug");
        String runName = suiteName + " " + env;

        List<TcmResultDto> results = new ArrayList<>();
        for (BufferedResult buffered : bufferedResults) {
            results.add(TcmResultDto.builder()
                    .testCaseId(buffered.testCaseId())
                    .status(mapStatus(buffered.testngStatus()))
                    .durationMs(buffered.durationMs())
                    .errorMessage(buffered.errorMessage())
                    .executedAt(buffered.executedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                    .build());
        }

        Long testPlanId = TcmScopeContext.getTestPlanId();
        if (testPlanId == null || testPlanId <= 0) {
            testPlanId = ConfigProvider.getTcmTestPlanId();
        }

        TcmRunImportRequest.TcmRunImportRequestBuilder builder = TcmRunImportRequest.builder()
                .runName(runName)
                .environment(env)
                .suite(suiteName)
                .buildName("Maven")
                .results(results);

        Long featureId = TcmScopeContext.getFeatureId();
        Long acId = TcmScopeContext.getAcId();
        Long projectId = ConfigProvider.getTcmProjectId();
        if (featureId != null) {
            builder.featureId(featureId);
        } else if (acId != null) {
            builder.acId(acId);
        } else if (testPlanId > 0) {
            builder.testPlanId(testPlanId);
        }
        if (projectId != null && projectId > 0) {
            builder.projectId(projectId);
        }

        return builder.build();
    }

    private static String mapStatus(int testngStatus) {
        return switch (testngStatus) {
            case org.testng.ITestResult.SUCCESS -> "PASS";
            case org.testng.ITestResult.FAILURE -> "FAIL";
            case org.testng.ITestResult.SKIP -> "SKIPPED";
            default -> "NOT_RUN";
        };
    }

    public record BufferedResult(
            String testCaseId,
            int testngStatus,
            Long durationMs,
            String errorMessage,
            LocalDateTime executedAt
    ) {
    }
}
