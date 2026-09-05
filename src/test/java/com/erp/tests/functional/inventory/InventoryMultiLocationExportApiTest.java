package com.erp.tests.functional.inventory;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.InventoryFixture;
import com.erp.fixtures.IsolatedMultiLocationOwnerScope;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.UserFixture;
import com.erp.models.response.MultiLocationStorageItemResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageAmountResponse;
import com.erp.models.response.UserMeResponse;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.helpers.XlsxContentAssertions;
import io.qameta.allure.Allure;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Multi-location owner export regression: «Всі локації» → GET /export-analytics/inventory.
 */
@Slf4j
@Epic("Inventory")
@Feature("REQ-WMS-007 Stock")
public class InventoryMultiLocationExportApiTest extends BaseFunctionalTest {

    private StorageFixture storageFixture;
    private UserFixture userFixture;
    private InventoryFixture inventoryFixture;
    private RelocationFixture relocationFixture;
    private ResourceFixture resourceFixture;
    private IsolatedMultiLocationOwnerScope multiLocationScope;

    private IsolatedMultiLocationOwnerScope.Context ownerContext;
    private ResourceResponse resourceA;
    private ResourceResponse resourceB;
    private ResourceResponse decoyResource;
    private static final UserRole OWNER = UserRole.OWNER_2;
    private static final double STOCK_A = 12.0;
    private static final double STOCK_B = 18.0;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void arrangeMultiLocationOwnerWithStock() {
        storageFixture = new StorageFixture(testContext, apiExecutor);
        userFixture = new UserFixture(testContext, apiExecutor);
        inventoryFixture = new InventoryFixture(testContext, apiExecutor);
        relocationFixture = new RelocationFixture(testContext, apiExecutor);
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        relocationFixture.prepareContext();
        resourceFixture.prepareContext();

        multiLocationScope = new IsolatedMultiLocationOwnerScope(
                storageFixture,
                userFixture,
                apiExecutor,
                getPlaywrightSessionProvider());
        ownerContext = multiLocationScope.acquire(OWNER);

        resourceA = resourceFixture.createUniqueResource("mloc-exp-a-");
        resourceB = resourceFixture.createUniqueResource("mloc-exp-b-");
        decoyResource = resourceFixture.createUniqueResource("mloc-decoy-");
        relocationFixture.ensureStock(ownerContext.storageAId(), resourceA.getId(), STOCK_A);
        relocationFixture.ensureStock(ownerContext.storageBId(), resourceB.getId(), STOCK_B);

        long forbiddenStorageId = storageFixture.createUniqueStorage("mloc-forbidden-").getId();
        relocationFixture.ensureStock(forbiddenStorageId, decoyResource.getId(), 99.0);

        inventoryFixture.requireItemForResourceWithRetry(
                ownerContext.storageAId(), resourceA.getId(), OWNER, 15_000);
        inventoryFixture.requireItemForResourceWithRetry(
                ownerContext.storageBId(), resourceB.getId(), OWNER, 15_000);
        inventoryFixture.requireItemForResourceWithRetry(
                forbiddenStorageId, decoyResource.getId(), UserRole.ADMIN, 15_000);
    }

    @AfterClass(alwaysRun = true)
    public void releaseMultiLocationScope() {
        if (multiLocationScope != null) {
            multiLocationScope.release();
        }
    }

    @Test
    @TestCaseId("TC-WMS-007-019")
    @Story("Multi-location owner exports all permitted remainders")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Arrange: ephemeral Keycloak owner з двома UNIT-локаціями; унікальні ресурси з залишками на кожній.
            Act (CPMA-762): GET /export-analytics/inventory?locations=storageA,storageB
            — як tk-ui у режимі «Всі локації» (allowedActiveStorageIds).
            Expect: 200, XLSX містить лише рядки з API inventory owner-локацій (resourceA/B + кількості).
            Control: decoy на чужій локації та resourceB відсутні там, де не мають бути.
            """)
    public void multiLocationOwnerExportsAllLocationsExcel() {
        UserMeResponse me = userFixture.getMe(OWNER);
        Allure.parameter("ownerUsername", ownerContext.owner().username());
        Allure.parameter("allowedStorageIds", me.getAllowedStorageIds());
        Allure.parameter("storageAId", ownerContext.storageAId());
        Allure.parameter("storageBId", ownerContext.storageBId());

        assertThat(me.getAllowedStorageIds())
                .as("Owner must have access to both isolated storages")
                .contains(ownerContext.storageAId(), ownerContext.storageBId());

        String locationsCsv = ownerContext.storageAId() + "," + ownerContext.storageBId();
        Response inventoryList = inventoryFixture.getMultiLocationInventory(OWNER, locationsCsv);
        assertThat(inventoryList.statusCode())
                .as("Multi-location inventory list must load for owner storages")
                .isEqualTo(200);

        Response allLocations = inventoryFixture.exportRemaindersByLocations(
                OWNER,
                List.of(ownerContext.storageAId(), ownerContext.storageBId()),
                null);
        assertThat(allLocations.statusCode())
                .as("All-locations export must succeed for multi-location owner")
                .isEqualTo(200);
        byte[] allBytes = allLocations.asByteArray();
        assertThat(allBytes.length).isGreaterThan(100);
        assertThat(allBytes[0]).as("XLSX ZIP magic").isEqualTo((byte) 'P');
        assertThat(allBytes[1]).as("XLSX ZIP magic").isEqualTo((byte) 'K');
        assertExportMatchesOwnerInventory(allBytes, inventoryList);
        assertThat(XlsxContentAssertions.zipContainsAmount(allBytes, STOCK_A))
                .as("All-locations XLSX must include stock quantity for resource A")
                .isTrue();
        assertThat(XlsxContentAssertions.zipContainsAmount(allBytes, STOCK_B))
                .as("All-locations XLSX must include stock quantity for resource B")
                .isTrue();
        assertThat(XlsxContentAssertions.zipContainsText(allBytes, decoyResource.getName()))
                .as("All-locations XLSX must not leak decoy from forbidden storage")
                .isFalse();
        assertThat(XlsxContentAssertions.zipContainsAmount(allBytes, 99.0))
                .as("All-locations XLSX must not include decoy stock quantity")
                .isFalse();

        Response singleA = inventoryFixture.exportRemaindersHierarchy(
                OWNER, ownerContext.storageAId(), null);
        assertThat(singleA.statusCode()).isEqualTo(200);
        byte[] singleABytes = singleA.asByteArray();
        assertThat(XlsxContentAssertions.zipContainsText(singleABytes, resourceA.getName())).isTrue();
        assertThat(XlsxContentAssertions.zipContainsText(singleABytes, resourceB.getName()))
                .as("Single-location export for A must not include storage-B-only resource")
                .isFalse();
        assertThat(XlsxContentAssertions.zipContainsText(singleABytes, decoyResource.getName()))
                .as("Single-location export for A must not include decoy from forbidden storage")
                .isFalse();
    }

    private void assertExportMatchesOwnerInventory(byte[] xlsx, Response inventoryList) {
        Set<Long> allowedStorages = Set.of(ownerContext.storageAId(), ownerContext.storageBId());
        List<MultiLocationStorageItemResponse> rows =
                inventoryList.jsonPath().getList("content", MultiLocationStorageItemResponse.class);
        assertThat(rows).isNotNull();

        Set<String> visibleResourceNames = rows.stream()
                .filter(row -> row.getResource() != null && row.getResource().getName() != null)
                .filter(row -> hasPositiveAmountOnStorages(row, allowedStorages))
                .map(row -> row.getResource().getName())
                .collect(Collectors.toSet());

        assertThat(visibleResourceNames)
                .as("Owner inventory API must expose both seeded resources")
                .contains(resourceA.getName(), resourceB.getName());
        assertThat(visibleResourceNames)
                .as("Owner inventory API must not expose decoy from forbidden storage")
                .doesNotContain(decoyResource.getName());

        for (String name : visibleResourceNames) {
            assertThat(XlsxContentAssertions.zipContainsText(xlsx, name))
                    .as("XLSX must include every resource visible in owner inventory API: %s", name)
                    .isTrue();
        }
    }

    private static boolean hasPositiveAmountOnStorages(
            MultiLocationStorageItemResponse row, Set<Long> storageIds) {
        if (row.getLocations() == null) {
            return false;
        }
        return row.getLocations().stream()
                .filter(Objects::nonNull)
                .anyMatch(loc -> isPositiveOnStorage(loc, storageIds));
    }

    private static boolean isPositiveOnStorage(StorageAmountResponse loc, Set<Long> storageIds) {
        if (loc.getStorage() == null || loc.getStorage().getId() == null) {
            return false;
        }
        return storageIds.contains(loc.getStorage().getId())
                && loc.getAmount() != null
                && loc.getAmount() > 0;
    }
}
