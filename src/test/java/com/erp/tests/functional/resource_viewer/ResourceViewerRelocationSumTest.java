package com.erp.tests.functional.resource_viewer;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.fixtures.ResourceFixture;
import com.erp.models.response.ResourceRelocationSumViewerResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.config.ConfigProvider;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.*;
import io.restassured.common.mapper.TypeRef;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
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
public class ResourceViewerRelocationSumTest extends BaseFunctionalTest {

    private ResourceFixture resourceFixture;
    private Long receiverStorageId;

    @BeforeClass(alwaysRun = true)
    public void setupResourceViewerTests() {
        if (testContext == null) {
            baseTestClassSetup();
        }
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        resourceFixture.prepareContext();
        receiverStorageId = ConfigProvider.getOwner1StorageId();
    }

    @Test(priority = 1)
    @TestCaseId("TC-RVW-001")
    @Story("Summary sorted by resource name")
    @Description("GET /resources-viewer/relocations/sum — сортування resourceName (ASC): цифри, латиниця, л/є/і/ї")
    @Severity(SeverityLevel.NORMAL)
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

        Map<String, Object> params = new HashMap<>();
        params.put("resourceIds", List.of(
                digits.getId(), aaa.getId(), mmm.getId(), zzz.getId(),
                el.getId(), ye.getId(), ii.getId(), yi.getId()));
        params.put("receiverIds", receiverStorageId);

        Response response = Allure.step("GET relocations/sum як RESOURCE_VIEWER (wolf)", () ->
                apiExecutor.executeWithQueryParams(
                        ApiEndpointDefinition.RESOURCE_VIEWER_RELOCATIONS_SUM,
                        UserRole.RESOURCE_VIEWER,
                        params));

        assertThat(response.statusCode()).isEqualTo(200);
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.RESOURCE_VIEWER_RELOCATIONS_SUM);

        List<ResourceRelocationSumViewerResponse> items = response.as(
                new TypeRef<List<ResourceRelocationSumViewerResponse>>() {});

        List<Long> expectedIds = List.of(
                digits.getId(), aaa.getId(), mmm.getId(), zzz.getId(),
                el.getId(), ye.getId(), ii.getId(), yi.getId());
        Allure.step("Перевірка наявності восьми тестових ресурсів", () -> {
            assertThat(items).extracting(ResourceRelocationSumViewerResponse::getResourceId)
                    .containsAll(expectedIds);
        });

        List<String> names = items.stream()
                .filter(item -> expectedIds.contains(item.getResourceId()))
                .map(ResourceRelocationSumViewerResponse::getResourceName)
                .toList();

        Allure.step("Перевірка порядку resourceName (Java natural order: цифри → латиниця → л → є/і/ї)", () -> {
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
}
