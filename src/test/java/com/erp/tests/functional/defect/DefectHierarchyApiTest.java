package com.erp.tests.functional.defect;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.defect.DefectDataFactory;
import com.erp.enums.UserRole;
import com.erp.fixtures.DefectFixture;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.models.query.DefectQuery;
import com.erp.models.response.DefectResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageResponse;
import com.erp.tests.functional.storage.StorageApiTestBase;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-DEF-001 / AC-08: parent workspace on /defects aggregates child-location defects
 * (all structural INTERNAL descendants via {@code DefectFacade.augmentStoragesWithChildren},
 * not visibility-region / per-child {@code allowedStorageIds} filtering).
 */
@Epic("Defects")
@Feature("Defect hierarchy filter (parent → children)")
public class DefectHierarchyApiTest extends StorageApiTestBase {

    private DefectFixture defectFixture;
    private RelocationFixture relocationFixture;
    private ResourceFixture resourceFixture;

    @BeforeClass(alwaysRun = true)
    public void setupHierarchyDefectTests() {
        defectFixture = new DefectFixture(testContext, apiExecutor);
        relocationFixture = new RelocationFixture(testContext, apiExecutor);
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        relocationFixture.prepareContext();
        resourceFixture.prepareContext();
        SchemaRegistry.logSchemaCoverage();
    }

    @Test(priority = 10)
    @TestCaseId("TC-DEF-031")
    @Story("Parent storageIds includes child-location defects")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            REQ-DEF-001 AC-08: parent query includes all structural children (INTERNAL tree),
            not visibility-scoped subset. Arrange: parent + child; STORAGE defect only on child.
            Act: GET /api/v1/defects?storageIds={parentId}.
            Assert: child defect present; outsider storage isolated.
            Backend: DefectFacade.augmentStoragesWithChildren → StorageHierarchyHolder.getSubStoragesIncluding.
            """)
    public void parentQueryIncludesChildOnlyDefect() {
        StorageResponse parent = storageFixture.createUniqueStorage("def-hier-p-");
        StorageResponse child = storageFixture.createChildStorage(parent.getId(), "def-hier-c-");
        StorageResponse outsider = storageFixture.createUniqueStorage("def-hier-out-");

        ResourceResponse childResource = resourceFixture.createUniqueResource("def-hier-res-");
        relocationFixture.ensureStock(child.getId(), childResource.getId(), 12.0);

        DefectResponse childDefect = defectFixture.createAs(UserRole.ADMIN,
                DefectDataFactory.buildStorageFifoDefect(child.getId(), childResource.getId(), 3.0));

        ResourceResponse outsiderResource = resourceFixture.createUniqueResource("def-hier-out-res-");
        relocationFixture.ensureStock(outsider.getId(), outsiderResource.getId(), 8.0);
        DefectResponse outsiderDefect = defectFixture.createAs(UserRole.ADMIN,
                DefectDataFactory.buildStorageFifoDefect(outsider.getId(), outsiderResource.getId(), 2.0));

        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.DEFECT_GET_PAGE,
                UserRole.ADMIN,
                DefectQuery.builder().storageId(parent.getId()).pageSize(500).build().toListQueryParams());
        assertThat(response.statusCode())
                .as("GET /defects?storageIds=parent body=%s", response.asString())
                .isEqualTo(200);
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.DEFECT_GET_PAGE);

        List<DefectResponse> fromParent = response.jsonPath().getList("content", DefectResponse.class);
        assertThat(fromParent).extracting(DefectResponse::getId).contains(childDefect.getId());
        assertThat(fromParent.stream()
                .filter(d -> d.getId().equals(childDefect.getId()))
                .findFirst()
                .orElseThrow()
                .getStorage().getId())
                .as("Returned row must keep the child storage reference")
                .isEqualTo(child.getId());

        assertThat(fromParent).extracting(DefectResponse::getId)
                .as("Unrelated storage defect must not leak into parent scope")
                .doesNotContain(outsiderDefect.getId());

        List<DefectResponse> fromChild = defectFixture.listDefectsAs(UserRole.ADMIN,
                DefectQuery.builder().storageId(child.getId()).pageSize(500).build());
        assertThat(fromChild).extracting(DefectResponse::getId).contains(childDefect.getId());
    }

    @Test(priority = 20)
    @TestCaseId("TC-DEF-032")
    @Story("Parent query aggregates defects from parent and multiple children")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            Arrange: parent + two children; one STORAGE defect on parent, one on each child.
            Act: GET /defects?storageIds={parentId}.
            Assert: all three defect ids returned.
            """)
    public void parentQueryAggregatesParentAndChildrenDefects() {
        StorageResponse parent = storageFixture.createUniqueStorage("def-hier2-p-");
        StorageResponse childA = storageFixture.createChildStorage(parent.getId(), "def-hier2-a-");
        StorageResponse childB = storageFixture.createChildStorage(parent.getId(), "def-hier2-b-");

        ResourceResponse resParent = resourceFixture.createUniqueResource("def-hier2-p-res-");
        ResourceResponse resA = resourceFixture.createUniqueResource("def-hier2-a-res-");
        ResourceResponse resB = resourceFixture.createUniqueResource("def-hier2-b-res-");

        relocationFixture.ensureStock(parent.getId(), resParent.getId(), 10.0);
        relocationFixture.ensureStock(childA.getId(), resA.getId(), 10.0);
        relocationFixture.ensureStock(childB.getId(), resB.getId(), 10.0);

        DefectResponse onParent = defectFixture.createAs(UserRole.ADMIN,
                DefectDataFactory.buildStorageFifoDefect(parent.getId(), resParent.getId(), 1.0));
        DefectResponse onChildA = defectFixture.createAs(UserRole.ADMIN,
                DefectDataFactory.buildStorageFifoDefect(childA.getId(), resA.getId(), 2.0));
        DefectResponse onChildB = defectFixture.createAs(UserRole.ADMIN,
                DefectDataFactory.buildStorageFifoDefect(childB.getId(), resB.getId(), 3.0));

        List<DefectResponse> fromParent = defectFixture.listDefectsAs(UserRole.ADMIN,
                DefectQuery.builder().storageId(parent.getId()).pageSize(500).build());

        assertThat(fromParent).extracting(DefectResponse::getId)
                .contains(onParent.getId(), onChildA.getId(), onChildB.getId());
    }
}
