package com.erp.tests.functional.analytics;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Analytics")
@Feature("Analytics and audit smokes")
public class AnalyticsAuditApiTest extends BaseFunctionalTest {

    private long storageId;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void setupAnalyticsTests() {
        storageId = ConfigProvider.getOwner1StorageId();
    }

    @Test(priority = 10)
    @TestCaseId("TC-ANL-001")
    @Story("Production analytics")
    @Severity(SeverityLevel.NORMAL)
    @Description("GET /production/analytic/summary + RBAC anonymous.")
    public void productionAnalyticSummary() {
        Response ok = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.PRODUCTION_ANALYTIC_SUMMARY_GET,
                UserRole.ADMIN,
                Map.of("parentStorageId", storageId,
                        "fromDate", LocalDate.now().minusDays(30).toString(),
                        "toDate", LocalDate.now().toString()));
        assertThat(ok.statusCode()).isIn(200, 400);
        Response anon = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.PRODUCTION_ANALYTIC_SUMMARY_GET,
                UserRole.ANONYMOUS,
                Map.of("parentStorageId", storageId));
        assertThat(anon.statusCode()).isIn(401, 403);
    }

    @Test(priority = 20)
    @TestCaseId("TC-ANL-002")
    @Story("Order analytics")
    @Severity(SeverityLevel.NORMAL)
    public void orderAnalyticSummary() {
        Response ok = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.ORDER_ANALYTIC_SUMMARY_GET,
                UserRole.ADMIN,
                Map.of("storageIds", storageId,
                        "fromDate", LocalDate.now().minusDays(30).toString(),
                        "toDate", LocalDate.now().toString()));
        assertThat(ok.statusCode()).isIn(200, 400, 403);
    }

    @Test(priority = 30)
    @TestCaseId("TC-ANL-003")
    @Story("Daily report")
    @Severity(SeverityLevel.NORMAL)
    public void productionDailyReport() {
        Response ok = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.PRODUCTION_DAILY_REPORT_GET,
                UserRole.ADMIN,
                Map.of("parentStorageId", storageId));
        assertThat(ok.statusCode()).isIn(200, 400);
    }

    @Test(priority = 40)
    @TestCaseId("TC-ANL-004")
    @Story("Audit log")
    @Severity(SeverityLevel.NORMAL)
    public void auditLogPage() {
        Response ok = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.AUDIT_LOG_GET_PAGE,
                UserRole.ADMIN,
                Map.of("page", 0, "size", 10));
        assertThat(ok.statusCode()).isIn(200, 403);
        Response anon = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.AUDIT_LOG_GET_PAGE,
                UserRole.ANONYMOUS,
                Map.of("page", 0, "size", 10));
        assertThat(anon.statusCode()).isIn(401, 403);
    }

    @Test(priority = 50)
    @TestCaseId("TC-ANL-005")
    @Story("Analytics sessions")
    @Severity(SeverityLevel.NORMAL)
    public void analyticsSessions() {
        Response ok = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.ANALYTICS_SESSIONS_GET,
                UserRole.ADMIN,
                Map.of("page", 0, "size", 10));
        assertThat(ok.statusCode()).isIn(200, 403);
    }

    @Test(priority = 60)
    @TestCaseId("TC-ANL-006")
    @Story("Export remainder")
    @Severity(SeverityLevel.NORMAL)
    public void exportRemainder() {
        Response ok = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.EXPORT_REMAINDER_GET,
                UserRole.ADMIN,
                Map.of("storageId", storageId));
        assertThat(ok.statusCode()).isIn(200, 400, 403);
    }
}
