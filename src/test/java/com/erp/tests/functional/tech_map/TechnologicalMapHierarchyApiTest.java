package com.erp.tests.functional.tech_map;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.fixtures.TechnologicalMapHierarchyFixture;
import com.erp.models.query.TechnologicalMapListQuery;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.tests.functional.storage.StorageApiTestBase;
import com.erp.utils.helpers.DatabaseIntegrityValidator;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TC-MFG-035: GET /technological-maps for a STORAGE or PRODUCTION parent returns the
 * entire subtree (including maps on locations without access); leaf does not expand.
 */
@Slf4j
@Epic("Technological Maps")
@Feature("REQ-MFG-001-03 Tech map list hierarchy")
public class TechnologicalMapHierarchyApiTest extends StorageApiTestBase {

    private TechnologicalMapHierarchyFixture hierarchyFixture;
    private TechnologicalMapHierarchyFixture.Seed seed;

    @BeforeClass(alwaysRun = true)
    public void setupHierarchyTechMaps() {
        SchemaRegistry.logSchemaCoverage();
        hierarchyFixture = new TechnologicalMapHierarchyFixture(
                testContext, apiExecutor, storageFixture, regionFixture, getPlaywrightSessionProvider());
        seed = hierarchyFixture.acquireAndSeed();
    }

    @AfterMethod(alwaysRun = true)
    public void deactivateHierarchyMapsAfterMethod() {
        if (hierarchyFixture != null) {
            hierarchyFixture.deactivateCreatedMaps();
        }
    }

    @AfterClass(alwaysRun = true)
    public void releaseIsolatedHierarchy() {
        if (hierarchyFixture != null) {
            hierarchyFixture.release();
        }
    }

    @Test(priority = 10)
    @TestCaseId("TC-MFG-035")
    @Story("STORAGE/PRODUCTION parent list shows entire subtree tech maps")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Arrange: isolated REGIONS owner bound to STORAGE parent P (not UNIT);
            sibling PRODUCTION parent P; each with A (grant), B (no grant) → C (grant); sibling X (grant).
            Maps on P/A/B/C/X.
            Act: GET /technological-maps?storageIds=P as OWNER_2 for STORAGE and PRODUCTION; then leaf A.
            Assert: P → TM-P, TM-A, TM-B, TM-C (усе піддерево, включно з B без доступу), без TM-X
            і без карт іншої гілки. A → лише TM-A.
            """)
    public void parentContextListsEntireSubtreeMaps() {
        assertParentSubtree("STORAGE", seed.getStorageParent(), seed.getProductionParent());
        assertParentSubtree("PRODUCTION", seed.getProductionParent(), seed.getStorageParent());
        assertLeafDoesNotExpand(seed.getStorageParent());
        assertLeafDoesNotExpand(seed.getProductionParent());
    }

    private void assertParentSubtree(
            String parentType,
            TechnologicalMapHierarchyFixture.Branch branch,
            TechnologicalMapHierarchyFixture.Branch other) {
        Response parentList = hierarchyFixture.techMaps().listRaw(
                UserRole.OWNER_2,
                TechnologicalMapListQuery.forStorage(branch.getParent().getId()).toBuilder()
                        .isActive(true)
                        .pageSize(100)
                        .build());
        assertThat(parentList.statusCode())
                .as("GET tech maps for %s parent P body=%s", parentType, parentList.asString())
                .isEqualTo(200);
        SchemaRegistry.validateIfSuccess(parentList, ApiEndpointDefinition.TECH_MAP_GET_ALL);

        Set<String> parentNames = namesOf(DatabaseIntegrityValidator.extractList(
                parentList, TechnologicalMapResponse.class));
        assertThat(parentNames)
                .as("%s parent P list must include own map and entire subtree, including B (no access)",
                        parentType)
                .contains(
                        branch.getMapParent().getName(),
                        branch.getMapA().getName(),
                        branch.getMapB().getName(),
                        branch.getMapC().getName());
        assertThat(parentNames)
                .as("%s parent P list must not include X or the other parent branch", parentType)
                .doesNotContain(
                        seed.getMapX().getName(),
                        other.getMapParent().getName(),
                        other.getMapA().getName(),
                        other.getMapB().getName(),
                        other.getMapC().getName());
    }

    private void assertLeafDoesNotExpand(TechnologicalMapHierarchyFixture.Branch branch) {
        Response leafList = hierarchyFixture.techMaps().listRaw(
                UserRole.OWNER_2,
                TechnologicalMapListQuery.forStorage(branch.getStorageA().getId()).toBuilder()
                        .isActive(true)
                        .pageSize(100)
                        .build());
        assertThat(leafList.statusCode())
                .as("GET tech maps for leaf A body=%s", leafList.asString())
                .isEqualTo(200);
        SchemaRegistry.validateIfSuccess(leafList, ApiEndpointDefinition.TECH_MAP_GET_ALL);

        Set<String> leafNames = namesOf(DatabaseIntegrityValidator.extractList(
                leafList, TechnologicalMapResponse.class));
        assertThat(leafNames)
                .as("Leaf A must not expand the tree")
                .contains(branch.getMapA().getName())
                .doesNotContain(
                        branch.getMapParent().getName(),
                        branch.getMapB().getName(),
                        branch.getMapC().getName(),
                        seed.getMapX().getName());
    }

    private static Set<String> namesOf(List<TechnologicalMapResponse> maps) {
        return maps.stream()
                .map(TechnologicalMapResponse::getName)
                .collect(Collectors.toSet());
    }
}
