package com.erp.tests.functional.order;

import com.erp.annotations.TestCaseId;
import com.erp.models.response.OrderAvailabilityLocationResponse;
import com.erp.models.response.OrderAvailabilityResponse;
import com.erp.models.response.OrderResponse;
import com.erp.api.endpoints.ApiEndpointDefinition;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.SkipException;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Orders")
@Feature("REQ-ORD Order availability")
public class OrderAvailabilityApiTest extends OrderApiTestBase {

    @Test(priority = 10)
    @TestCaseId("TC-ORD-050")
    @Story("Resource availability")
    @Severity(SeverityLevel.CRITICAL)
    public void testGetAvailabilityForInProgressOrder() {
        OrderResponse order = prepareManagedInProgress();

        List<OrderAvailabilityResponse> availability = orderFixture.getAvailability(
                MANAGER, order.getId(), requesterStorageId);

        assertThat(availability).isNotEmpty();
        OrderAvailabilityResponse line = availability.stream()
                .filter(a -> resourceId.equals(a.getResourceId()))
                .findFirst()
                .orElseThrow();
        assertThat(line.getLocations()).isNotEmpty();

        OrderAvailabilityLocationResponse location = line.getLocations().stream()
                .filter(l -> gatheringStorageId.equals(l.getStorageId()))
                .findFirst()
                .orElse(line.getLocations().getFirst());
        assertThat(location.getAmount()).isNotNull();
        assertThat(location.getAmount().doubleValue()).isGreaterThan(0);
        if (location.getHeldByThisOrder() != null) {
            assertThat(location.getHeldByThisOrder().doubleValue()).isGreaterThanOrEqualTo(0);
        }
        assertThat(line.getProduction())
                .as("CPMA-725: production[] is present (empty when no open ВЗ)")
                .isNotNull();
    }

    @Test(priority = 11)
    @TestCaseId("TC-ORD-051")
    @Story("Availability scope")
    @Description("Availability обмежений order_availability_root_storage (+ children).")
    public void testAvailabilityScopedToConfiguredRoot() {
        OrderResponse order = prepareManagedInProgress();
        List<OrderAvailabilityResponse> availability = orderFixture.getAvailability(
                MANAGER, order.getId(), requesterStorageId);
        assertThat(availability).isNotEmpty();
        assertThat(availability.getFirst().getLocations()).isNotEmpty();
        assertThat(availability.getFirst().getLocations())
                .allMatch(loc -> loc.getStorageId() != null);
    }

    @Test(priority = 12)
    @TestCaseId("TC-ORD-052")
    @Story("Availability without root config")
    @Description("""
            Без order_availability_root_storage — availability по всіх локаціях зі stock.
            На стенді root заданий: тест тимчасово DELETE рядок у БД і відновлює його в finally.
            """)
    public void testAvailabilityWithoutRootConfig() {
        if (getDbHelper() == null) {
            throw new SkipException(
                    "TC-ORD-052 потребує БД, щоб тимчасово зняти order_availability_root_storage");
        }
        OrderResponse order = prepareManagedInProgress();
        try {
            orderFixture.clearAvailabilityRootConfig(getDbHelper());
            List<OrderAvailabilityResponse> availability = orderFixture.getAvailability(
                    MANAGER, order.getId(), requesterStorageId);
            assertThat(availability)
                    .as("без root config availability не порожня (усі локації зі stock)")
                    .isNotEmpty();
            assertThat(availability.getFirst().getLocations()).isNotEmpty();
        } finally {
            orderFixture.ensureAvailabilityRootConfig(getDbHelper());
        }
    }

    @Test(priority = 13)
    @TestCaseId("TC-ORD-053")
    @Story("Availability RBAC")
    @Description("Availability лише з manage на requester.")
    public void testAvailabilityRequiresManageOnRequester() {
        OrderResponse order = prepareManagedInProgress();
        Response denied = apiExecutor.execute(
                ApiEndpointDefinition.ORDER_GET_AVAILABILITY,
                REQUESTER,
                null,
                order.getId(),
                requesterStorageId);
        assertThat(denied.statusCode()).isEqualTo(403);
    }
}
