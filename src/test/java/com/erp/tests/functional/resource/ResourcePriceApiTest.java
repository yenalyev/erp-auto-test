package com.erp.tests.functional.resource;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.fixtures.ResourceFixture;
import com.erp.models.request.ResourcePriceUpdateRequest;
import com.erp.models.response.ResourcePriceResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.test_context.ContextKey;
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
    private long resourceId;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void setupPriceTests() {
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        resourceFixture.prepareContext();
        List<ResourceResponse> resources = testContext.get(ContextKey.SHARED_AVAILABLE_RESOURCES);
        resourceId = resources.getFirst().getId();
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
        ResourcePriceUpdateRequest request = ResourcePriceUpdateRequest.builder()
                .resourceId(resourceId)
                .price(new BigDecimal("12.50"))
                .build();
        Response response = apiExecutor.execute(ApiEndpointDefinition.RESOURCE_PRICE_PUT_UPDATE, UserRole.ADMIN, request);
        assertThat(response.statusCode()).isIn(200, 204);
    }
}
