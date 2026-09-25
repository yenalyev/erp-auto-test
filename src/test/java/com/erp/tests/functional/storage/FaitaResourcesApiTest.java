package com.erp.tests.functional.storage;

import com.erp.annotations.TestCaseId;
import com.erp.fixtures.FaitaResourceFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.models.response.FaitaResourceResponse;
import com.erp.models.response.ResourceResponse;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Step;
import io.qameta.allure.Story;
import lombok.extern.slf4j.Slf4j;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Зіставлення FAITA→ERP (1→N) і заміна набору implicit resources.
 * GET /integrations/faita/resources має бути 200; skip лише за faita.integration.enabled=false.
 */
@Slf4j
@Epic("Integration")
@Feature("FAITA Resources")
@Story("FLIGHT reconciliation and implicit CRUD")
public class FaitaResourcesApiTest extends StorageApiTestBase {

    private static final String RESOURCE_PREFIX = "faita-rec-";

    private ResourceFixture resourceFixture;
    private FaitaResourceFixture faitaFixture;
    private boolean faitaApiAvailable;

    private final List<Long> reconciliationIdsToCleanup = new ArrayList<>();
    private final List<String> implicitExternalIdsToClear = new ArrayList<>();

    @BeforeClass(alwaysRun = true, dependsOnMethods = "setupStorageApiBase")
    @Step("Підготовка Resource/FAITA fixtures")
    public void setupFaitaResourcesApi() {
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        faitaFixture = new FaitaResourceFixture(testContext, apiExecutor);
        resourceFixture.fetchSharedUnit(3);
        resourceFixture.fetchSharedResourceCategory();
        faitaApiAvailable = faitaFixture.probeAvailable();
        if (!faitaApiAvailable) {
            log.warn("faita.integration.enabled=false — FaitaResourcesApiTest буде skipped");
        }
    }

    @AfterClass(alwaysRun = true)
    @Step("Cleanup: implicit + FLIGHT reconciliations")
    public void cleanupFaitaResources() {
        for (String externalId : implicitExternalIdsToClear) {
            faitaFixture.clearImplicitQuietly(externalId, externalId);
        }
        faitaFixture.deleteReconciliationsQuietly(reconciliationIdsToCleanup);
    }

    @Test(priority = 10)
    @TestCaseId("TC-FAITA-REC-001")
    @Description(StorageRegionsAllureDescriptions.TC_FAITA_REC_001)
    @Severity(SeverityLevel.CRITICAL)
    public void testCreateOneFaitaToTwoErp() {
        requireFaitaApi();
        ResourceResponse erp1 = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "a-");
        ResourceResponse erp2 = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "b-");
        String externalId = faitaFixture.newExternalId("erp-rec1-");
        String externalName = "FAITA rec1 " + externalId;

        List<Long> ids = faitaFixture.createFlightReconciliation(
                externalId, externalName, erp1.getId(), erp2.getId());
        reconciliationIdsToCleanup.addAll(ids);
        assertThat(ids).as("CREATE має повернути 2 reconciliation id").hasSize(2);

        FaitaResourceResponse product = faitaFixture.requireByExternalId(externalId);
        assertThat(FaitaResourceFixture.reconciliationResourceIds(product))
                .containsExactlyInAnyOrder(erp1.getId(), erp2.getId());
        assertThat(FaitaResourceFixture.reconciliationResourceNames(product))
                .contains(erp1.getName(), erp2.getName());
    }

    @Test(priority = 20)
    @TestCaseId("TC-FAITA-REC-002")
    @Description(StorageRegionsAllureDescriptions.TC_FAITA_REC_002)
    @Severity(SeverityLevel.CRITICAL)
    public void testAddThirdErpToExistingProduct() {
        requireFaitaApi();
        ResourceResponse erp1 = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "c1-");
        ResourceResponse erp2 = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "c2-");
        ResourceResponse erp3 = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "c3-");
        String externalId = faitaFixture.newExternalId("erp-rec2-");
        String externalName = "FAITA rec2 " + externalId;

        reconciliationIdsToCleanup.addAll(faitaFixture.createFlightReconciliation(
                externalId, externalName, erp1.getId(), erp2.getId()));
        reconciliationIdsToCleanup.addAll(faitaFixture.createFlightReconciliation(
                externalId, externalName, erp3.getId()));

        FaitaResourceResponse product = faitaFixture.requireByExternalId(externalId);
        assertThat(FaitaResourceFixture.reconciliationResourceIds(product))
                .containsExactlyInAnyOrder(erp1.getId(), erp2.getId(), erp3.getId());
    }

    @Test(priority = 30)
    @TestCaseId("TC-FAITA-REC-003")
    @Description(StorageRegionsAllureDescriptions.TC_FAITA_REC_003)
    @Severity(SeverityLevel.CRITICAL)
    public void testDeleteOneOfTwoReconciliations() {
        requireFaitaApi();
        ResourceResponse erp1 = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "d1-");
        ResourceResponse erp2 = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "d2-");
        String externalId = faitaFixture.newExternalId("erp-rec3-");
        String externalName = "FAITA rec3 " + externalId;

        List<Long> ids = faitaFixture.createFlightReconciliation(
                externalId, externalName, erp1.getId(), erp2.getId());
        reconciliationIdsToCleanup.addAll(ids);
        assertThat(ids).hasSize(2);

        faitaFixture.deleteReconciliationById(ids.get(0));

        FaitaResourceResponse product = faitaFixture.requireByExternalId(externalId);
        assertThat(FaitaResourceFixture.reconciliationResourceIds(product))
                .hasSize(1)
                .containsAnyOf(erp1.getId(), erp2.getId());
    }

    @Test(priority = 40)
    @TestCaseId("TC-FAITA-IMPL-003")
    @Description(StorageRegionsAllureDescriptions.TC_FAITA_IMPL_003)
    @Severity(SeverityLevel.CRITICAL)
    public void testReplaceImplicitSetRemoveOne() {
        requireFaitaApi();
        ResourceResponse productErp = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "p-");
        ResourceResponse impl1Erp = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "i1-");
        ResourceResponse impl2Erp = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "i2-");

        String productId = faitaFixture.newExternalId("erp-impl3-");
        String productName = "FAITA impl3 product " + productId;
        String impl1Id = faitaFixture.newExternalId("erp-impl3a-");
        String impl1Name = "FAITA impl3 a " + impl1Id;
        String impl2Id = faitaFixture.newExternalId("erp-impl3b-");
        String impl2Name = "FAITA impl3 b " + impl2Id;

        reconciliationIdsToCleanup.addAll(faitaFixture.createFlightReconciliation(
                productId, productName, productErp.getId()));
        reconciliationIdsToCleanup.addAll(faitaFixture.createFlightReconciliation(
                impl1Id, impl1Name, impl1Erp.getId()));
        reconciliationIdsToCleanup.addAll(faitaFixture.createFlightReconciliation(
                impl2Id, impl2Name, impl2Erp.getId()));
        implicitExternalIdsToClear.add(productId);

        FaitaResourceResponse saved = faitaFixture.putImplicitResources(
                productId, productName, List.of(
                        FaitaResourceFixture.implicitRef(impl1Id, impl1Name, 2),
                        FaitaResourceFixture.implicitRef(impl2Id, impl2Name, 4)));
        assertThat(FaitaResourceFixture.implicitExternalIds(saved))
                .containsExactlyInAnyOrder(impl1Id, impl2Id);
        assertThat(FaitaResourceFixture.implicitCountsByExternalId(saved))
                .containsEntry(impl1Id, 2)
                .containsEntry(impl2Id, 4);

        FaitaResourceResponse remaining = faitaFixture.putImplicitResources(
                productId, productName, List.of(FaitaResourceFixture.implicitRef(impl1Id, impl1Name, 2)));
        assertThat(FaitaResourceFixture.implicitExternalIds(remaining))
                .containsExactly(impl1Id);
        assertThat(FaitaResourceFixture.implicitCountsByExternalId(remaining))
                .containsExactlyEntriesOf(java.util.Map.of(impl1Id, 2));

        FaitaResourceResponse fromList = faitaFixture.requireByExternalId(productId);
        assertThat(FaitaResourceFixture.implicitExternalIds(fromList))
                .containsExactly(impl1Id);
        assertThat(FaitaResourceFixture.implicitCountsByExternalId(fromList))
                .containsExactlyEntriesOf(java.util.Map.of(impl1Id, 2));
    }

    @Test(priority = 50)
    @TestCaseId("TC-FAITA-IMPL-004")
    @Description(StorageRegionsAllureDescriptions.TC_FAITA_IMPL_004)
    @Severity(SeverityLevel.CRITICAL)
    public void testEditImplicitCountPreservesOtherResources() {
        requireFaitaApi();
        ResourceResponse productErp = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "count-p-");
        ResourceResponse impl1Erp = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "count-i1-");
        ResourceResponse impl2Erp = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "count-i2-");

        String productId = faitaFixture.newExternalId("erp-count-");
        String productName = "FAITA count product " + productId;
        String impl1Id = faitaFixture.newExternalId("erp-count-a-");
        String impl1Name = "FAITA count A " + impl1Id;
        String impl2Id = faitaFixture.newExternalId("erp-count-c-");
        String impl2Name = "FAITA count C " + impl2Id;

        reconciliationIdsToCleanup.addAll(faitaFixture.createFlightReconciliation(
                productId, productName, productErp.getId()));
        reconciliationIdsToCleanup.addAll(faitaFixture.createFlightReconciliation(
                impl1Id, impl1Name, impl1Erp.getId()));
        reconciliationIdsToCleanup.addAll(faitaFixture.createFlightReconciliation(
                impl2Id, impl2Name, impl2Erp.getId()));
        implicitExternalIdsToClear.add(productId);

        faitaFixture.putImplicitResources(productId, productName, List.of(
                FaitaResourceFixture.implicitRef(impl1Id, impl1Name, 2),
                FaitaResourceFixture.implicitRef(impl2Id, impl2Name, 3)));

        FaitaResourceResponse updated = faitaFixture.putImplicitResources(productId, productName, List.of(
                FaitaResourceFixture.implicitRef(impl1Id, impl1Name, 5),
                FaitaResourceFixture.implicitRef(impl2Id, impl2Name, 3)));
        assertThat(FaitaResourceFixture.implicitCountsByExternalId(updated))
                .containsExactlyInAnyOrderEntriesOf(java.util.Map.of(impl1Id, 5, impl2Id, 3));

        FaitaResourceResponse persisted = faitaFixture.requireByExternalId(productId);
        assertThat(FaitaResourceFixture.implicitCountsByExternalId(persisted))
                .containsExactlyInAnyOrderEntriesOf(java.util.Map.of(impl1Id, 5, impl2Id, 3));
    }

    private void requireFaitaApi() {
        if (!faitaApiAvailable) {
            throw new SkipException("FAITA explicitly disabled: faita.integration.enabled=false");
        }
    }
}
