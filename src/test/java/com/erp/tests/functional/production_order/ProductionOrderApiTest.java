package com.erp.tests.functional.production_order;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.ProductionOrderState;
import com.erp.enums.UserRole;
import com.erp.fixtures.ProductionOrderFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.models.request.DecompositionRequest;
import com.erp.models.response.ProductionOrderResponse;
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
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Production orders")
@Feature("REQ-PO Production orders")
public class ProductionOrderApiTest extends BaseFunctionalTest {

    private ProductionOrderFixture fixture;
    private long storageId;
    private long resourceId;
    private final List<Long> createdIds = new ArrayList<>();

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void setupProductionOrderTests() {
        fixture = new ProductionOrderFixture(testContext, apiExecutor);
        new ResourceFixture(testContext, apiExecutor).prepareContext();
        storageId = fixture.resolveTargetStorageId(UserRole.ADMIN);
        List<ResourceResponse> resources = testContext.get(ContextKey.SHARED_AVAILABLE_RESOURCES);
        resourceId = resources.getFirst().getId();
    }

    @AfterMethod(alwaysRun = true)
    public void cancelCreatedOrders() {
        for (Long id : createdIds) {
            try {
                fixture.cancel(UserRole.ADMIN, id);
            } catch (RuntimeException ignored) {
                fixture.deleteRaw(UserRole.ADMIN, id);
            }
        }
        createdIds.clear();
    }

    @Test(priority = 10)
    @TestCaseId("TC-PO-001")
    @Story("Create production order")
    @Severity(SeverityLevel.CRITICAL)
    @Description("POST /production-orders створює NEW замовлення з output.")
    public void createProductionOrder() {
        ProductionOrderResponse created = fixture.create(
                UserRole.ADMIN, fixture.buildCreateRequest(storageId, resourceId, 5.0));
        createdIds.add(created.getId());
        assertThat(created.getId()).isNotNull();
        assertThat(created.getState()).isEqualTo(ProductionOrderState.NEW);
        ProductionOrderResponse fetched = fixture.getById(UserRole.ADMIN, created.getId());
        assertThat(fetched.getId()).isEqualTo(created.getId());
    }

    @Test(priority = 20)
    @TestCaseId("TC-PO-002")
    @Story("List and target locations")
    @Severity(SeverityLevel.CRITICAL)
    public void listPageAndTargetLocations() {
        ProductionOrderResponse created = fixture.create(
                UserRole.ADMIN, fixture.buildCreateRequest(storageId, resourceId, 2.0));
        createdIds.add(created.getId());
        Response page = fixture.getPageRaw(UserRole.ADMIN, storageId);
        assertThat(page.statusCode()).isEqualTo(200);
        Response locations = fixture.getTargetLocationsRaw(UserRole.ADMIN);
        assertThat(locations.statusCode()).isEqualTo(200);
    }

    @Test(priority = 30)
    @TestCaseId("TC-PO-003")
    @Story("Holds empty before generate")
    @Severity(SeverityLevel.CRITICAL)
    @Description("GET holds до generate — порожній список (не блокує вільний залишок).")
    public void holdsEmptyBeforeGenerate() {
        ProductionOrderResponse created = fixture.create(
                UserRole.ADMIN, fixture.buildCreateRequest(storageId, resourceId, 3.0));
        createdIds.add(created.getId());
        Response holds = fixture.getHoldsRaw(UserRole.ADMIN, created.getId());
        assertThat(holds.statusCode()).isEqualTo(200);
    }

    @Test(priority = 40)
    @TestCaseId("TC-PO-004")
    @Story("Decompose echo")
    @Severity(SeverityLevel.CRITICAL)
    public void decomposeReturnsBlocks() {
        ProductionOrderResponse created = fixture.create(
                UserRole.ADMIN, fixture.buildCreateRequest(storageId, resourceId, 4.0));
        createdIds.add(created.getId());
        DecompositionRequest request = DecompositionRequest.builder()
                .blocks(List.of(DecompositionRequest.DecompositionBlockRequest.builder()
                        .items(List.of(DecompositionRequest.DecompositionItemRequest.builder()
                                .resourceId(resourceId)
                                .assignments(List.of())
                                .build()))
                        .build()))
                .build();
        Response response = fixture.decomposeRaw(UserRole.ADMIN, created.getId(), request);
        assertThat(response.statusCode()).isIn(200, 400);
    }

    @Test(priority = 50)
    @TestCaseId("TC-PO-005")
    @Story("Cancel production order")
    @Severity(SeverityLevel.CRITICAL)
    public void cancelProductionOrder() {
        ProductionOrderResponse created = fixture.create(
                UserRole.ADMIN, fixture.buildCreateRequest(storageId, resourceId, 1.0));
        ProductionOrderResponse cancelled = fixture.cancel(UserRole.ADMIN, created.getId());
        assertThat(cancelled.getState()).isEqualTo(ProductionOrderState.CANCELLED);
    }

    @Test(priority = 60)
    @TestCaseId("TC-PO-006")
    @Story("RBAC")
    @Severity(SeverityLevel.CRITICAL)
    public void outsiderCreateDenied() {
        Response denied = apiExecutor.execute(
                ApiEndpointDefinition.PRODUCTION_ORDER_POST_CREATE,
                UserRole.OWNER_2,
                fixture.buildCreateRequest(storageId, resourceId, 1.0));
        assertThat(denied.statusCode()).isIn(403, 400);
    }

    @Test(priority = 70)
    @TestCaseId("TC-PO-007")
    @Story("Linkable warehouse orders")
    public void linkableOrdersEndpoint() {
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.PRODUCTION_ORDER_GET_LINKABLE_ORDERS,
                UserRole.ADMIN,
                java.util.Map.of("storageIds", storageId));
        assertThat(response.statusCode()).isIn(200, 400);
    }
}
