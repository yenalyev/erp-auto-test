package com.erp.tests.functional.order;

import com.erp.annotations.TestCaseId;
import com.erp.models.response.OrderAvailabilityLocationResponse;
import com.erp.models.response.OrderAvailabilityResponse;
import com.erp.models.response.OrderResponse;
import io.qameta.allure.*;
import lombok.extern.slf4j.Slf4j;
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
}
