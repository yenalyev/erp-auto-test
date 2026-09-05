package com.erp.tests.functional.defect;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.defect.DefectDataFactory;
import com.erp.enums.UserRole;
import com.erp.fixtures.DefectFixture;
import com.erp.fixtures.IsolatedRestrictedOwnerScope;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.UserFixture;
import com.erp.models.query.DefectQuery;
import com.erp.models.response.DefectResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageResponse;
import com.erp.models.response.UserMeResponse;
import com.erp.tests.functional.storage.StorageApiTestBase;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Step;
import io.qameta.allure.Story;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-DEF-001 / AC-08: parent defect query includes structural children even when the child
 * is outside the viewer's visibility scope ({@code allowedStorageIds}, {@code /storages/names}).
 */
@Epic("Defects")
@Feature("Defect hierarchy filter (parent → children)")
@Story("Structural children vs visibility scope")
public class DefectHierarchyVisibilityApiTest extends StorageApiTestBase {

    private DefectFixture defectFixture;
    private RelocationFixture relocationFixture;
    private ResourceFixture resourceFixture;
    private UserFixture userFixture;
    private IsolatedRestrictedOwnerScope isolatedOwnerScope;

    private Long parentUnitId;

    @BeforeClass(alwaysRun = true)
    public void setupVisibilityHierarchyDefectTests() {
        defectFixture = new DefectFixture(testContext, apiExecutor);
        relocationFixture = new RelocationFixture(testContext, apiExecutor);
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        userFixture = new UserFixture(testContext, apiExecutor);
        relocationFixture.prepareContext();
        resourceFixture.prepareContext();
        SchemaRegistry.logSchemaCoverage();

        isolatedOwnerScope = new IsolatedRestrictedOwnerScope(
                storageFixture,
                userFixture,
                apiExecutor,
                getPlaywrightSessionProvider());
        parentUnitId = isolatedOwnerScope.acquire();
        regionFixture.purgeViewerVisibilityScope(UserRole.ADMIN, parentUnitId, storageFixture);
        regionFixture.purgeRegionsByNamePrefixes(UserRole.ADMIN, "def-vis-");
    }

    @AfterClass(alwaysRun = true)
    public void releaseIsolatedOwner() {
        if (isolatedOwnerScope != null) {
            isolatedOwnerScope.release();
        }
    }

    @Test(priority = 10)
    @TestCaseId("TC-DEF-033")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            REQ-DEF-001 AC-08 (visibility contrast): REGIONS owner on parent UNIT; structural child
            STORAGE is absent from allowedStorageIds and /storages/names, but GET /defects?storageIds={parent}
            still returns the child's defect (all structural children — not visibility-filtered).
            Arrange: ADMIN creates child + STORAGE defect on child.
            Act: OWNER_2 (isolated REGIONS grant on parent only) lists defects by parent id.
            Assert: child defect id in content; child id ∉ allowedStorageIds before query.
            """)
    public void parentQueryIncludesDefectOnChildOutsideVisibilityScope() {
        StorageResponse child = storageFixture.createChildStorage(parentUnitId, "def-vis-c-");

        assertChildHiddenFromViewerScope(child);

        ResourceResponse resource = resourceFixture.createUniqueResource("def-vis-res-");
        relocationFixture.ensureStock(child.getId(), resource.getId(), 10.0);
        DefectResponse childDefect = defectFixture.createAs(UserRole.ADMIN,
                DefectDataFactory.buildStorageFifoDefect(child.getId(), resource.getId(), 2.0));

        List<DefectResponse> fromParent = defectFixture.listDefectsAs(UserRole.OWNER_2,
                DefectQuery.builder().storageId(parentUnitId).pageSize(500).build());

        assertThat(fromParent).extracting(DefectResponse::getId)
                .as("Parent query must include defect on structural child outside visibility scope")
                .contains(childDefect.getId());
        assertThat(fromParent.stream()
                .filter(d -> d.getId().equals(childDefect.getId()))
                .findFirst()
                .orElseThrow()
                .getStorage().getId())
                .isEqualTo(child.getId());
    }

    @Step("Child structural storage is hidden from REGIONS owner selectors")
    private void assertChildHiddenFromViewerScope(StorageResponse child) {
        UserMeResponse me = userFixture.getMe(UserRole.OWNER_2);
        assertThat(me.getAllowedStorageIds())
                .as("/users/me allowedStorageIds must contain parent only (REGIONS owner)")
                .contains(parentUnitId)
                .doesNotContain(child.getId());

        List<StorageResponse> names = storageFixture.getNames(UserRole.OWNER_2, true, null);
        assertThat(names.stream().map(StorageResponse::getId).toList())
                .as("Child must be absent from GET /storages/names for REGIONS owner")
                .doesNotContain(child.getId());
    }
}
