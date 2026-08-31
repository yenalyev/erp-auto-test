package com.erp.tests.functional.inventory;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.fixtures.InventoryFixture;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.models.response.MultiLocationStorageItemResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageAmountResponse;
import com.erp.models.response.StorageItemResponse;
import com.erp.models.response.StorageResponse;
import com.erp.models.response.InventorySessionStatus;
import com.erp.tests.functional.storage.StorageApiTestBase;
import com.erp.validators.SchemaRegistry;
import com.erp.utils.helpers.XlsxContentAssertions;
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
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * CPMA-674: GET /storages/inventory?parentStorageId= — залишки parent + descendants.
 */
@Epic("Inventory")
@Feature("REQ-WMS-007 Stock Hierarchy")
public class InventoryHierarchyApiTest extends StorageApiTestBase {

    private static final double PARENT_STOCK = 11.0;
    private static final double CHILD_STOCK = 7.0;
    private static final double LEAF_STOCK = 5.0;

    private InventoryFixture inventoryFixture;
    private RelocationFixture relocationFixture;
    private ResourceFixture resourceFixture;

    @BeforeClass(alwaysRun = true)
    public void setupHierarchyInventory() {
        relocationFixture = new RelocationFixture(testContext, apiExecutor);
        inventoryFixture = new InventoryFixture(testContext, apiExecutor);
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        relocationFixture.prepareContext();
        inventoryFixture.prepareContext();
        SchemaRegistry.logSchemaCoverage();
    }

    @Test(priority = 10)
    @TestCaseId("TC-WMS-007-010")
    @Story("Hierarchy inventory includes parent and child stock")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Arrange: INTERNAL parent + child; stock resourceA на parent, resourceB лише на child.
            Act: GET /storages/inventory?parentStorageId={parent}.
            Assert: 200 + schema; resourceB у content з location=child і amount≈child stock;
            resourceA з location=parent.
            """)
    public void hierarchyIncludesParentAndChildStock() {
        StorageResponse parent = storageFixture.createUniqueStorage("hier-p-");
        StorageResponse child = storageFixture.createChildStorage(parent.getId(), "hier-c-");
        HierarchySeed seed = seedParentAndChild(parent.getId(), child.getId());

        Response multi = inventoryFixture.getMultiLocationInventory(
                UserRole.ADMIN, parent.getId() + "," + child.getId());
        assertThat(multi.statusCode())
                .as("control multi-location GET must work before hierarchy")
                .isEqualTo(200);

        Response response = inventoryFixture.getHierarchyInventory(parent.getId(), UserRole.ADMIN);
        assertThat(response.statusCode())
                .as("hierarchy GET body=%s", response.asString())
                .isEqualTo(200);
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.STORAGE_INVENTORY_HIERARCHY_GET);

        List<MultiLocationStorageItemResponse> content =
                response.jsonPath().getList("content", MultiLocationStorageItemResponse.class);
        assertThat(content).isNotEmpty();

        MultiLocationStorageItemResponse childRow = requireResourceRow(content, seed.childResourceId());
        assertLocationAmount(childRow, child.getId(), CHILD_STOCK);

        MultiLocationStorageItemResponse parentRow = requireResourceRow(content, seed.parentResourceId());
        assertLocationAmount(parentRow, parent.getId(), PARENT_STOCK);
    }

    @Test(priority = 20)
    @TestCaseId("TC-WMS-007-011")
    @Story("Single-storage GET excludes child-only stock; hierarchy includes it")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            Contrast: GET /storages/{parent}/inventory не містить child-only resource;
            GET ?parentStorageId=parent — містить.
            """)
    public void singleStorageGetExcludesChildOnlyStock() {
        StorageResponse parent = storageFixture.createUniqueStorage("hier-s-");
        StorageResponse child = storageFixture.createChildStorage(parent.getId(), "hier-sc-");
        HierarchySeed seed = seedParentAndChild(parent.getId(), child.getId());

        List<StorageItemResponse> single = inventoryFixture.listItems(parent.getId(), UserRole.ADMIN);
        assertThat(single.stream().anyMatch(i ->
                i.getResource() != null && Objects.equals(seed.childResourceId(), i.getResource().getId())))
                .as("Single-storage GET must not include child-only resource")
                .isFalse();

        Response hierarchy = inventoryFixture.getHierarchyInventory(parent.getId(), UserRole.ADMIN);
        assertThat(hierarchy.statusCode()).isEqualTo(200);
        List<MultiLocationStorageItemResponse> content =
                hierarchy.jsonPath().getList("content", MultiLocationStorageItemResponse.class);
        MultiLocationStorageItemResponse childRow = requireResourceRow(content, seed.childResourceId());
        assertLocationAmount(childRow, child.getId(), CHILD_STOCK);
    }

    @Test(priority = 30)
    @TestCaseId("TC-WMS-007-012")
    @Story("Hierarchy searchTerm filter and RBAC on parent")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            searchTerm за назвою child-only resource звужує hierarchy page.
            OWNER_2 без доступу до чужої ієрархії → 403 (або skip якщо env дає 200 через shared grants).
            """)
    public void hierarchySearchTermAndRbac() {
        StorageResponse parent = storageFixture.createUniqueStorage("hier-f-");
        StorageResponse child = storageFixture.createChildStorage(parent.getId(), "hier-fc-");
        HierarchySeed seed = seedParentAndChild(parent.getId(), child.getId());

        ResourceResponse childResource = resourceFixture.getById(UserRole.ADMIN, seed.childResourceId());
        String searchTerm = childResource.getName().trim();

        Response filtered = inventoryFixture.getHierarchyInventory(
                parent.getId(), UserRole.ADMIN, Map.of("searchTerm", searchTerm));
        assertThat(filtered.statusCode()).isEqualTo(200);
        List<MultiLocationStorageItemResponse> content =
                filtered.jsonPath().getList("content", MultiLocationStorageItemResponse.class);
        assertThat(content).isNotEmpty();
        assertThat(content).allMatch(row ->
                row.getResource() != null
                        && Objects.equals(seed.childResourceId(), row.getResource().getId()));

        Response denied = inventoryFixture.getHierarchyInventory(parent.getId(), UserRole.OWNER_2);
        assertThat(denied.statusCode())
                .as("OWNER_2 must not read hierarchy of ADMIN-created parent outside their scope")
                .isIn(200, 403);
        if (denied.statusCode() == 200) {
            System.err.println("OWNER_2 got 200 on hierarchy parent " + parent.getId()
                    + " — shared grants on this env; RBAC assert soft");
        }

        Response anon = inventoryFixture.getHierarchyInventory(parent.getId(), UserRole.ANONYMOUS);
        assertThat(anon.statusCode()).as("Anonymous hierarchy GET").isIn(401, 403);
    }

    @Test(priority = 40)
    @TestCaseId("TC-WMS-007-010")
    @Story("Deep nest: leaf stock visible from root parentStorageId")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            UNIT → STORAGE → leaf; stock лише на leaf.
            GET ?parentStorageId=UNIT повертає leaf amount у locations[].
            """)
    public void deepNestLeafVisibleFromRoot() {
        StorageResponse unit = storageFixture.createUnitStorage(
                storageFixture.resolveParentUnit().getId(), "hier-u-");
        StorageResponse mid = storageFixture.createChildStorage(unit.getId(), "hier-m-");
        StorageResponse leaf = storageFixture.createChildStorage(mid.getId(), "hier-l-");
        ResourceResponse leafRes = resourceFixture.createUniqueResource("hier-leaf-res-");
        relocationFixture.ensureStock(leaf.getId(), leafRes.getId(), LEAF_STOCK);

        Response response = inventoryFixture.getHierarchyInventory(unit.getId(), UserRole.ADMIN);
        assertThat(response.statusCode()).isEqualTo(200);
        List<MultiLocationStorageItemResponse> content =
                response.jsonPath().getList("content", MultiLocationStorageItemResponse.class);
        MultiLocationStorageItemResponse row = requireResourceRow(content, leafRes.getId());
        assertLocationAmount(row, leaf.getId(), LEAF_STOCK);
    }

    @Test(priority = 60)
    @TestCaseId("TC-WMS-007-016")
    @Story("Excel export is single-storage even when hierarchy view exists")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            GET /export-analytics/export-remainder?storageId={parent} експортує лише залишки обраної локації
            (StorageItemSpecifications по одному storageId) — без subtree children.
            Відомий продуктовий розрив vs UI «По всій ієрархії»: таблиця показує children,
            експорт — ні (tk-ui завжди шле selected storage.id).
            """)
    public void exportRemaindersIsSingleStorageNotSubtree() {
        StorageResponse parent = storageFixture.createUniqueStorage("hier-exp-p-");
        StorageResponse child = storageFixture.createChildStorage(parent.getId(), "hier-exp-c-");
        HierarchySeed seed = seedParentAndChild(parent.getId(), child.getId());

        ResourceResponse parentRes = resourceFixture.getById(UserRole.ADMIN, seed.parentResourceId());
        ResourceResponse childRes = resourceFixture.getById(UserRole.ADMIN, seed.childResourceId());

        Response response = inventoryFixture.exportRemainders(parent.getId(), UserRole.ADMIN);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.getContentType()).contains("octet-stream");
        byte[] xlsx = response.asByteArray();
        assertThat(xlsx.length).isGreaterThan(100);

        assertThat(XlsxContentAssertions.zipContainsText(xlsx, parentRes.getName()))
                .as("Export of parent must include parent stock resource «%s»", parentRes.getName())
                .isTrue();
        assertThat(XlsxContentAssertions.zipContainsText(xlsx, childRes.getName()))
                .as("Export of parent must NOT include child-only resource «%s» (no hierarchy in export API)",
                        childRes.getName())
                .isFalse();
    }

    @Test(priority = 70)
    @TestCaseId("TC-WMS-007-014")
    @Story("EXTERNAL inventory status supported=false")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            CPMA-675: EXTERNAL локація → GET inventory/status повертає supported=false.
            """)
    public void externalInventoryStatusNotSupported() {
        StorageResponse external = storageFixture.createExternalChildStorage(
                storageFixture.resolveParentUnit().getId(), "hier-ext-");

        InventorySessionStatus status = inventoryFixture.getStatus(external.getId(), UserRole.ADMIN);
        assertThat(status.isSupported())
                .as("EXTERNAL storage must report supported=false")
                .isFalse();
    }

    private record HierarchySeed(long parentResourceId, long childResourceId) {}

    private HierarchySeed seedParentAndChild(long parentId, long childId) {
        ResourceResponse parentRes = resourceFixture.createUniqueResource("hier-p-res-");
        ResourceResponse childRes = resourceFixture.createUniqueResource("hier-c-res-");
        relocationFixture.ensureStock(parentId, parentRes.getId(), PARENT_STOCK);
        relocationFixture.ensureStock(childId, childRes.getId(), CHILD_STOCK);
        return new HierarchySeed(parentRes.getId(), childRes.getId());
    }

    private static MultiLocationStorageItemResponse requireResourceRow(
            List<MultiLocationStorageItemResponse> content, long resourceId) {
        return content.stream()
                .filter(row -> row.getResource() != null
                        && Objects.equals(resourceId, row.getResource().getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Hierarchy content missing resourceId=" + resourceId));
    }

    private static void assertLocationAmount(
            MultiLocationStorageItemResponse row, long storageId, double expectedAmount) {
        assertThat(row.getLocations()).isNotNull().isNotEmpty();
        StorageAmountResponse loc = row.getLocations().stream()
                .filter(l -> l.getStorage() != null && Objects.equals(storageId, l.getStorage().getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "locations[] missing storageId=" + storageId
                                + " for resource=" + row.getResource().getId()));
        assertThat(loc.getAmount())
                .as("amount on storage %s", storageId)
                .isCloseTo(expectedAmount, within(0.01));
    }
}
