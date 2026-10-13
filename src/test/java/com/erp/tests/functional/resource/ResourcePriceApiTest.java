package com.erp.tests.functional.resource;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.fixtures.ResourceFixture;
import com.erp.models.request.ResourcePriceUpdateRequest;
import com.erp.models.response.ResourcePriceResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.tests.functional.BaseFunctionalTest;
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

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Master Data")
@Feature("Resource prices")
public class ResourcePriceApiTest extends BaseFunctionalTest {

    private ResourceFixture resourceFixture;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void setupPriceTests() {
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        resourceFixture.prepareContext();
    }

    @Test(priority = 10)
    @TestCaseId("TC-PRICE-001")
    @Story("Price page")
    @Severity(SeverityLevel.CRITICAL)
    @Description("GET /resources-price повертає сторінку цін.")
    public void getPricePage() {
        List<ResourcePriceResponse> prices = resourceFixture.getResourcePrices(UserRole.ADMIN, true, null);
        assertThat(prices).isNotNull();
    }

    @Test(priority = 20)
    @TestCaseId("TC-PRICE-002")
    @Story("Update price")
    @Severity(SeverityLevel.CRITICAL)
    public void updatePrice() {
        ResourceResponse resource = resourceFixture.createUniqueResource("admin-price-");
        try {
            assertPriceUpdate(UserRole.ADMIN, resource.getId(), "12.50");
            assertCurrentPrice(resource, UserRole.ADMIN, "12.50");
        } finally {
            resourceFixture.deactivate(UserRole.ADMIN, resource.getId());
        }
    }

    @Test(priority = 30)
    @TestCaseId("TC-ACC-PRICE-001")
    @Story("Бухгалтер встановлює ціну ресурсу")
    public void accountantCanSetPrice() {
        ResourceResponse resource = resourceFixture.createUniqueResource("accountant-price-new-");
        try {
            assertPriceUpdate(UserRole.ACCOUNTANT, resource.getId(), "11.25");
            assertCurrentPrice(resource, "11.25");
        } finally {
            resourceFixture.deactivate(UserRole.ADMIN, resource.getId());
        }
    }

    @Test(priority = 31)
    @TestCaseId("TC-ACC-PRICE-002")
    @Story("Бухгалтер змінює вже встановлену ціну")
    public void accountantCanReplacePrice() {
        ResourceResponse resource = resourceFixture.createUniqueResource("accountant-price-");
        try {
            assertPriceUpdate(UserRole.ACCOUNTANT, resource.getId(), "11.25");
            assertPriceUpdate(UserRole.ACCOUNTANT, resource.getId(), "12.50");
            assertCurrentPrice(resource, "12.50");
        } finally {
            resourceFixture.deactivate(UserRole.ADMIN, resource.getId());
        }
    }

    @Test(priority = 32)
    @TestCaseId("TC-ACC-PRICE-003")
    @Story("Нульова ціна дозволена")
    public void accountantCanSetZeroPrice() {
        ResourceResponse resource = resourceFixture.createUniqueResource("accountant-zero-price-");
        try {
            assertPriceUpdate(UserRole.ACCOUNTANT, resource.getId(), "0.00");
            assertCurrentPrice(resource, "0.00");
        } finally {
            resourceFixture.deactivate(UserRole.ADMIN, resource.getId());
        }
    }

    @Test(priority = 33)
    @TestCaseId("TC-ACC-PRICE-004")
    @Story("Від’ємна ціна заборонена")
    public void negativePriceIsRejected() {
        ResourceResponse resource = resourceFixture.createUniqueResource("accountant-negative-price-");
        try {
            assertPriceUpdate(UserRole.ADMIN, resource.getId(), "12.50");
            Response rejected = updatePrice(UserRole.ADMIN, resource.getId(), "-1.00");
            assertThat(rejected.statusCode()).as("Від’ємна ціна має бути відхилена").isEqualTo(400);
            assertCurrentPrice(resource, UserRole.ADMIN, "12.50");
        } finally {
            // Restore even on an older backend that incorrectly accepted the negative price.
            updatePrice(UserRole.ADMIN, resource.getId(), "12.50");
            resourceFixture.deactivate(UserRole.ADMIN, resource.getId());
        }
    }

    private Response updatePrice(UserRole role, Long id, String value) {
        ResourcePriceUpdateRequest request = ResourcePriceUpdateRequest.builder()
                .resourceId(id)
                .price(new BigDecimal(value))
                .build();
        return apiExecutor.execute(ApiEndpointDefinition.RESOURCE_PRICE_PUT_UPDATE, role, request);
    }

    private void assertPriceUpdate(UserRole role, Long id, String value) {
        Response response = updatePrice(role, id, value);
        assertThat(response.statusCode()).as("Користувач із правом зміни цін зберігає %s", value).isEqualTo(200);
        ResourcePriceResponse saved = response.as(ResourcePriceResponse.class);
        assertThat(saved.getPrice())
                .isEqualByComparingTo(new BigDecimal(value));
    }

    private void assertCurrentPrice(ResourceResponse resource, String value) {
        assertCurrentPrice(resource, UserRole.ACCOUNTANT, value);
    }

    private void assertCurrentPrice(ResourceResponse resource, UserRole role, String value) {
        ResourcePriceResponse current = resourceFixture.getResourcePrices(
                        role, true, resource.getName()).stream()
                .filter(row -> resource.getId().equals(row.getResourceId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Ресурс відсутній у цінах: " + resource.getId()));
        assertThat(current.getPrice()).isEqualByComparingTo(new BigDecimal(value));
        assertThat(current.getAddAt()).as("Дата чинної ціни в переліку").isNotNull();
    }
}
