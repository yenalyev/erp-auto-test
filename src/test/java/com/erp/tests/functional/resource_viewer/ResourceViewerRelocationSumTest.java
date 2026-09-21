package com.erp.tests.functional.resource_viewer;

import com.erp.annotations.DynamicResourceViewer;
import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.LocationProfile;
import com.erp.enums.UserRole;
import com.erp.fixtures.LocationProfileFixture;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.models.response.PagedResourceRelocationViewerResponse;
import com.erp.models.response.ResourceRelocationSumViewerResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Resource Viewer")
@Feature("Relocation resources sum")
@DynamicResourceViewer
public class ResourceViewerRelocationSumTest extends BaseFunctionalTest {

    private ResourceFixture resourceFixture;
    private RelocationFixture relocationFixture;
    private LocationProfileFixture locationProfileFixture;
    private Long unitReceiverId;
    private Long sourceStorageId;

    @BeforeClass(alwaysRun = true)
    public void setupResourceViewerTests() {
        if (testContext == null) {
            baseTestClassSetup();
        }
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        relocationFixture = new RelocationFixture(testContext, apiExecutor);
        locationProfileFixture = new LocationProfileFixture(testContext, apiExecutor);
        resourceFixture.prepareContext();
        relocationFixture.prepareContext();
        sourceStorageId = locationProfileFixture
                .create(LocationProfile.TSUK_WARENHAUSE, 1)
                .locations().getFirst().getId();
        unitReceiverId = locationProfileFixture
                .create(LocationProfile.BATTALION_UNIT, 1)
                .locations().getFirst().getId();
    }

    @AfterClass(alwaysRun = true)
    public void cleanupLocations() {
        if (locationProfileFixture != null) {
            locationProfileFixture.cleanup();
        }
    }

    @Test(priority = 1)
    @TestCaseId("TC-RVW-001")
    @Story("Summary sorted by resource name")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            GET /resources-viewer/relocations → поле sums:
            сортування resourceName (ASC): цифри, латиниця, л/є/і/ї.
            Усі явно вибрані resourceIds присутні: ресурси з рухом мають фактичну кількість,
            ресурси без руху — amount=0. Порядок вибору не впливає на сортування.
            """)
    public void testRelocationsSumSortedByResourceNameAsc() {
        ResourceResponse digits = Allure.step("Створити ресурс 111_rvw_* (ADMIN, цифри)", () ->
                resourceFixture.createUniqueResource("111_rvw_"));
        ResourceResponse aaa = Allure.step("Створити ресурс aaa_rvw_* (ADMIN, латиниця)", () ->
                resourceFixture.createUniqueResource("aaa_rvw_"));
        ResourceResponse mmm = Allure.step("Створити ресурс mmm_rvw_* (ADMIN, латиниця)", () ->
                resourceFixture.createUniqueResource("mmm_rvw_"));
        ResourceResponse zzz = Allure.step("Створити ресурс zzz_rvw_* (ADMIN, латиниця)", () ->
                resourceFixture.createUniqueResource("zzz_rvw_"));
        ResourceResponse el = Allure.step("Створити ресурс лим_rvw_* (ADMIN, л)", () ->
                resourceFixture.createUniqueResource("лим_rvw_"));
        ResourceResponse ye = Allure.step("Створити ресурс єба_rvw_* (ADMIN, є)", () ->
                resourceFixture.createUniqueResource("єба_rvw_"));
        ResourceResponse ii = Allure.step("Створити ресурс іва_rvw_* (ADMIN, і)", () ->
                resourceFixture.createUniqueResource("іва_rvw_"));
        ResourceResponse yi = Allure.step("Створити ресурс їжа_rvw_* (ADMIN, ї)", () ->
                resourceFixture.createUniqueResource("їжа_rvw_"));

        List<Long> expectedIds = List.of(
                yi.getId(), mmm.getId(), digits.getId(), ii.getId(),
                zzz.getId(), el.getId(), aaa.getId(), ye.getId());

        double aaaAmount = 2.0;
        double yeAmount = 3.0;
        relocationFixture.ensureStock(sourceStorageId, aaa.getId(), 10.0, UserRole.ADMIN);
        relocationFixture.ensureStock(sourceStorageId, ye.getId(), 10.0, UserRole.ADMIN);
        relocationFixture.createSend(
                UserRole.ADMIN, sourceStorageId, unitReceiverId, aaa.getId(), aaaAmount);
        relocationFixture.createSend(
                UserRole.ADMIN, sourceStorageId, unitReceiverId, ye.getId(), yeAmount);

        Map<String, Object> params = new HashMap<>();
        params.put("resourceIds", expectedIds);
        params.put("receiverIds", unitReceiverId);

        Response response = Allure.step("GET relocations (sums) як RESOURCE_VIEWER (wolf)", () ->
                apiExecutor.executeWithQueryParams(
                        ApiEndpointDefinition.RESOURCE_VIEWER_RELOCATIONS_GET,
                        UserRole.RESOURCE_VIEWER,
                        params));

        assertThat(response.statusCode()).isEqualTo(200);
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.RESOURCE_VIEWER_RELOCATIONS_GET);

        PagedResourceRelocationViewerResponse page = response.as(PagedResourceRelocationViewerResponse.class);
        List<ResourceRelocationSumViewerResponse> items =
                page.getSums() != null ? page.getSums() : List.of();

        Allure.step("Усі 8 вибраних ресурсів у sums: фактичні та нульові", () -> {
            assertThat(items).extracting(ResourceRelocationSumViewerResponse::getResourceId)
                    .containsAll(expectedIds);
            assertThat(items.stream()
                    .filter(s -> expectedIds.contains(s.getResourceId()))
                    .filter(s -> !aaa.getId().equals(s.getResourceId()))
                    .filter(s -> !ye.getId().equals(s.getResourceId()))
                    .map(ResourceRelocationSumViewerResponse::getAmount))
                    .allSatisfy(amount -> assertThat(amount).isEqualByComparingTo(java.math.BigDecimal.ZERO));
            assertThat(amountOf(items, aaa.getId())).isEqualTo(aaaAmount);
            assertThat(amountOf(items, ye.getId())).isEqualTo(yeAmount);
        });

        List<String> names = items.stream()
                .filter(item -> expectedIds.contains(item.getResourceId()))
                .map(ResourceRelocationSumViewerResponse::getResourceName)
                .toList();

        Allure.step("Порядок resourceName (Java natural order: цифри → латиниця → л → є/і/ї)", () -> {
            assertThat(names).hasSize(8);
            assertThat(names).isSortedAccordingTo(Comparator.naturalOrder());
            assertThat(names.get(0)).startsWith("111_rvw_");
            assertThat(names.get(1)).startsWith("aaa_rvw_");
            assertThat(names.get(2)).startsWith("mmm_rvw_");
            assertThat(names.get(3)).startsWith("zzz_rvw_");
            assertThat(names.get(4)).startsWith("лим_rvw_");
            assertThat(names.get(5)).startsWith("єба_rvw_");
            assertThat(names.get(6)).startsWith("іва_rvw_");
            assertThat(names.get(7)).startsWith("їжа_rvw_");
        });
    }

    private static double amountOf(List<ResourceRelocationSumViewerResponse> items, Long resourceId) {
        return items.stream()
                .filter(item -> resourceId.equals(item.getResourceId()))
                .map(ResourceRelocationSumViewerResponse::getAmount)
                .mapToDouble(java.math.BigDecimal::doubleValue)
                .sum();
    }
}
