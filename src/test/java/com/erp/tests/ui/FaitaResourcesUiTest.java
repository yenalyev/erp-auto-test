package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.FaitaResourceFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.models.response.ResourceResponse;
import com.erp.pages.FaitaResourceDetailPage;
import com.erp.pages.FaitaResourceListPage;
import com.erp.tests.functional.storage.StorageRegionsAllureDescriptions;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import lombok.extern.slf4j.Slf4j;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI /faita-resources (REQ-FAITA-001 AC-03). ADMIN — resource-reconciliation create/update/delete.
 */
@Slf4j
@Epic("Integration")
@Feature("FAITA Resources UI")
public class FaitaResourcesUiTest extends BaseUITest {

    private static final String RESOURCE_PREFIX = "ui-faita-";

    private ResourceFixture resourceFixture;
    private FaitaResourceFixture faitaFixture;
    private boolean faitaApiAvailable;
    private long selectedStorageId;

    private final List<Long> reconciliationIdsToCleanup = new ArrayList<>();
    private final List<String> implicitExternalIdsToClear = new ArrayList<>();

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        faitaFixture = new FaitaResourceFixture(testContext, apiExecutor);
        resourceFixture.fetchSharedUnit(3);
        resourceFixture.fetchSharedResourceCategory();
        selectedStorageId = ConfigProvider.getOwner1StorageId();
        faitaApiAvailable = faitaFixture.probeAvailable();
        if (!faitaApiAvailable) {
            log.warn("faita.integration.enabled=false — FaitaResourcesUiTest буде skipped");
        }
    }

    @AfterClass(alwaysRun = true)
    public void cleanupFaitaUiArtifacts() {
        for (String externalId : implicitExternalIdsToClear) {
            faitaFixture.clearImplicitQuietly(externalId, externalId);
        }
        faitaFixture.deleteReconciliationsQuietly(reconciliationIdsToCleanup);
    }

    @BeforeMethod(alwaysRun = true)
    public void prepareUiSession() {
        if (!faitaApiAvailable) {
            return;
        }
        injectRoleSession(UserRole.ADMIN, selectedStorageId);
    }

    @Test(priority = 10)
    @TestCaseId("TC-UI-FAITA-001")
    @Story("FAITA list search and columns")
    @Severity(SeverityLevel.CRITICAL)
    @Description(StorageRegionsAllureDescriptions.TC_UI_FAITA_001)
    public void testListSearchShowsReconciliationAndImplicit() {
        requireFaitaApi();
        SeededProduct seed = seedProductWithImplicit();

        FaitaResourceListPage list = new FaitaResourceListPage(page).openViaSidebar();
        assertThat(list.hasTypeFilter("Боєприпаси")).as("фільтр Боєприпаси").isTrue();
        assertThat(list.hasTypeFilter("Ініціатори")).as("фільтр Ініціатори").isTrue();
        assertThat(list.hasTypeFilter("Технічні засоби")).as("фільтр Технічні засоби").isTrue();

        list.searchByName(seed.productName());
        assertThat(list.rowByExternalId(seed.productId()).isVisible())
                .as("рядок виробу після пошуку")
                .isTrue();
        assertThat(list.rowByExternalId(seed.productId()).getByText(seed.erpName()).count())
                .as("колонка Зіставлення містить ERP")
                .isGreaterThan(0);
        assertThat(list.rowByExternalId(seed.productId()).getByText(seed.implicitName()).count())
                .as("колонка Додаткові містить implicit")
                .isGreaterThan(0);
        list.attachScreenshot("TC-UI-FAITA-001 — list search");
    }

    @Test(priority = 20)
    @TestCaseId("TC-UI-FAITA-002")
    @Story("Add second reconciliation on card")
    @Severity(SeverityLevel.CRITICAL)
    @Description(StorageRegionsAllureDescriptions.TC_UI_FAITA_002)
    public void testAddSecondReconciliationOnCard() {
        requireFaitaApi();
        ResourceResponse erp1 = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "r1-");
        ResourceResponse erp2 = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "r2-");
        String productId = faitaFixture.newExternalId("ui-rec2-");
        String productName = "UI FAITA rec2 " + productId;
        reconciliationIdsToCleanup.addAll(faitaFixture.createFlightReconciliation(
                productId, productName, erp1.getId()));

        FaitaResourceDetailPage card = new FaitaResourceDetailPage(page).open(productId);
        assertThat(card.isReconciliationListed(erp1.getName())).isTrue();
        card.addReconciliation(erp2.getName());
        assertThat(card.isReconciliationListed(erp1.getName())).as("перше зіставлення лишилось").isTrue();
        assertThat(card.isReconciliationListed(erp2.getName())).as("друге зіставлення додано").isTrue();
        card.attachScreenshot("TC-UI-FAITA-002 — two ERP");
    }

    @Test(priority = 30)
    @TestCaseId("TC-UI-FAITA-003")
    @Story("Remove one reconciliation on card")
    @Severity(SeverityLevel.CRITICAL)
    @Description(StorageRegionsAllureDescriptions.TC_UI_FAITA_003)
    public void testRemoveOneReconciliationOnCard() {
        requireFaitaApi();
        ResourceResponse erp1 = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "d1-");
        ResourceResponse erp2 = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "d2-");
        String productId = faitaFixture.newExternalId("ui-rec3-");
        String productName = "UI FAITA rec3 " + productId;
        reconciliationIdsToCleanup.addAll(faitaFixture.createFlightReconciliation(
                productId, productName, erp1.getId(), erp2.getId()));

        FaitaResourceDetailPage card = new FaitaResourceDetailPage(page).open(productId);
        card.removeReconciliation(erp1.getName());
        assertThat(card.isReconciliationListed(erp1.getName())).as("видалений ERP").isFalse();
        assertThat(card.isReconciliationListed(erp2.getName())).as("другий ERP лишився").isTrue();
        assertThat(card.isImplicitAddVisible())
                .as("кнопка implicit видима, доки є зіставлення")
                .isTrue();
        card.attachScreenshot("TC-UI-FAITA-003 — one ERP left");
    }

    @Test(priority = 40)
    @TestCaseId("TC-UI-FAITA-004")
    @Story("Add and remove implicit on card")
    @Severity(SeverityLevel.CRITICAL)
    @Description(StorageRegionsAllureDescriptions.TC_UI_FAITA_004)
    public void testAddAndRemoveImplicitOnCard() {
        requireFaitaApi();
        ResourceResponse productErp = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "p-");
        ResourceResponse implicitErp = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "i-");
        String productId = faitaFixture.newExternalId("ui-impl-");
        String productName = "UI FAITA impl " + productId;
        String implicitId = faitaFixture.newExternalId("ui-impl-opt-");
        String implicitName = "UI FAITA impl opt " + implicitId;
        reconciliationIdsToCleanup.addAll(faitaFixture.createFlightReconciliation(
                productId, productName, productErp.getId()));
        reconciliationIdsToCleanup.addAll(faitaFixture.createFlightReconciliation(
                implicitId, implicitName, implicitErp.getId()));
        implicitExternalIdsToClear.add(productId);

        FaitaResourceDetailPage card = new FaitaResourceDetailPage(page).open(productId);
        card.addImplicit(implicitName);
        assertThat(card.isImplicitListed(implicitName)).as("implicit додано").isTrue();
        card.removeImplicit(implicitName);
        assertThat(card.isImplicitListed(implicitName)).as("implicit прибрано").isFalse();
        card.attachScreenshot("TC-UI-FAITA-004 — implicit removed");
    }

    private SeededProduct seedProductWithImplicit() {
        ResourceResponse productErp = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "list-p-");
        ResourceResponse implicitErp = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "list-i-");
        String productId = faitaFixture.newExternalId("ui-list-");
        String productName = "UI FAITA list " + productId;
        String implicitId = faitaFixture.newExternalId("ui-list-i-");
        String implicitName = "UI FAITA list impl " + implicitId;
        reconciliationIdsToCleanup.addAll(faitaFixture.createFlightReconciliation(
                productId, productName, productErp.getId()));
        reconciliationIdsToCleanup.addAll(faitaFixture.createFlightReconciliation(
                implicitId, implicitName, implicitErp.getId()));
        implicitExternalIdsToClear.add(productId);
        faitaFixture.putImplicitResources(
                productId, productName, List.of(FaitaResourceFixture.implicitRef(implicitId, implicitName)));
        return new SeededProduct(productId, productName, productErp.getName(), implicitName);
    }

    private void requireFaitaApi() {
        if (!faitaApiAvailable) {
            throw new SkipException("FAITA explicitly disabled: faita.integration.enabled=false");
        }
    }

    private void injectRoleSession(UserRole role, long storageId) {
        Map<String, String> cookies = getPlaywrightSessionProvider()
                .getSession(role.getUsername(), role.getPassword());
        injectSessionCookies(cookies, sessionCookieDomain());
        browserContext.addInitScript(
                "localStorage.setItem('selectedStorageId', '" + storageId + "');");
    }

    private record SeededProduct(String productId, String productName, String erpName, String implicitName) {
    }
}
