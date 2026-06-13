package com.erp.utils.helpers;

import com.erp.dto.tcm.TcmImportResponse;
import com.erp.dto.tcm.TcmResultDto;
import com.erp.dto.tcm.TcmRunImportRequest;
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

    public static TcmImportResponse submitRun(TcmRunImportRequest request) {
        String baseUrl = ConfigProvider.getTcmBaseUrl();
        String token = ConfigProvider.getTcmApiToken();

        return given()
                .baseUri(baseUrl)
                .header(API_TOKEN_HEADER, token)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/autotest/runs")
                .then()
                .extract()
                .as(TcmImportResponse.class);
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

        return TcmRunImportRequest.builder()
                .testPlanId(ConfigProvider.getTcmTestPlanId())
                .runName(runName)
                .environment(env)
                .suite(suiteName)
                .buildName("Maven")
                .results(results)
                .build();
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
