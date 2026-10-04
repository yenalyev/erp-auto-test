package com.erp.tests.functional.storage;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.tests.functional.BaseFunctionalTest;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.restassured.response.Response;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Read-only release contract for the FAITA position snapshot in write-off API. */
@Epic("Integration")
@Feature("FAITA crew sync and write-off")
public class FaitaCrewSyncContractTest extends BaseFunctionalTest {

    @TestCaseId("TC-FAITA-SYNC-POS-API-001")
    @Description("FLIGHT write-offs expose a separate positionName snapshot, including JSON null for old records")
    @Severity(SeverityLevel.CRITICAL)
    @Test
    public void flightWriteOffsExposeJournalPosition() {
        List<Map<String, Object>> flightRows = new ArrayList<>();
        for (int pageNumber = 0; pageNumber < 10; pageNumber++) {
            Response response = apiExecutor.executeWithQueryParams(
                    ApiEndpointDefinition.INVENTORY_WRITE_OFF_GET_PAGE,
                    UserRole.ADMIN,
                    Map.of("page", pageNumber, "size", 100, "sort", "id,desc"));
            assertThat(response.statusCode()).as("write-off page %s HTTP status", pageNumber).isEqualTo(200);
            List<Map<String, Object>> rows = response.jsonPath().getList("content");
            assertThat(rows).as("write-off page content").isNotNull();
            rows.stream().filter(row -> "FLIGHT".equals(row.get("source"))).forEach(flightRows::add);
            if (rows.size() < 100) {
                break;
            }
        }

        assertThat(flightRows)
                .as("Dev needs at least one FLIGHT write-off in the first 1000 newest records")
                .isNotEmpty();
        for (Map<String, Object> row : flightRows) {
            assertThat(row.containsKey("positionName"))
                    .as("FLIGHT write-off id=%s must contain positionName", row.get("id"))
                    .isTrue();
            Object position = row.get("positionName");
            assertThat(position == null || position instanceof String)
                    .as("positionName must be a string or JSON null for id=%s", row.get("id"))
                    .isTrue();
        }
    }
}
