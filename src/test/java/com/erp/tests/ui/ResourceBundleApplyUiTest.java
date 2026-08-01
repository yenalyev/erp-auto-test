package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.ResourceBundleFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.models.response.ResourceResponse;
import com.erp.pages.RelocationCreateOutputCrewPage;
import com.erp.pages.RelocationCreateOutputPage;
import com.erp.pages.RelocationPage;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Relocation")
@Feature("Resource Bundles UI — apply")
@Story("REQ-WMS-009 AC-05")
public class ResourceBundleApplyUiTest extends BaseUITest {

    private static final String RESOURCE_PREFIX = "ui-bundle-apply-";
    private static final String TOAST_ALREADY =
            "Усі ресурси з цього комплекту вже додано";
    private static final String TOAST_NO_STOCK =
            "Немає доступних ресурсів з цього комплекту на цьому складі";

    private ResourceBundleFixture bundleFixture;
    private ResourceFixture resourceFixture;
    private long storageId;
    private ResourceResponse resourceWithStock;
    private ResourceResponse resourceNoStock;
    private String applyBundleName;
    private String noStockBundleName;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        bundleFixture = new ResourceBundleFixture(testContext, apiExecutor);
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        bundleFixture.prepareContext();
        resourceFixture.fetchSharedUnit(3);
        resourceFixture.fetchSharedResourceCategory();

        storageId = ConfigProvider.getOwner1StorageId();
        resourceWithStock = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "ok-");
        resourceNoStock = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "zero-");
        bundleFixture.relocation().ensureStock(storageId, resourceWithStock.getId(), 40.0);

        applyBundleName = bundleFixture.uniqueBundleName("ui-apply-");
        noStockBundleName = bundleFixture.uniqueBundleName("ui-nostock-");
        bundleFixture.createBundle(
                UserRole.OWNER_1, storageId, applyBundleName, List.of(resourceWithStock.getId()));
        bundleFixture.createBundle(
                UserRole.OWNER_1, storageId, noStockBundleName, List.of(resourceNoStock.getId()));

        injectOwner1Session();
    }

    @BeforeMethod(alwaysRun = true)
    public void ensureStock() {
        bundleFixture.relocation().ensureStock(storageId, resourceWithStock.getId(), 40.0);
    }

    @AfterClass(alwaysRun = true)
    public void cleanup() {
        if (bundleFixture != null) {
            bundleFixture.cleanupCreatedBundles();
        }
    }

    @Test(priority = 1)
    @TestCaseId("TC-BUNDLE-UI-020")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Форма видачі: badge комплекту видимий; hover показує ресурси.")
    public void badgeVisibleWithHoverResources() {
        RelocationCreateOutputPage form = openSendForm();
        assertThat(form.isBundleBadgeVisible(applyBundleName)).isTrue();
        form.hoverBundleBadge(applyBundleName);
        String resourceHint = resourceWithStock.getName().length() > 12
                ? resourceWithStock.getName().substring(0, 12)
                : resourceWithStock.getName();
        page.waitForCondition(() -> page.getByText(resourceHint).count() > 0);
        assertThat(page.getByText(resourceHint).count()).isGreaterThan(0);
        attachScreenshot("TC-BUNDLE-UI-020");
    }

    @Test(priority = 2)
    @TestCaseId("TC-BUNDLE-UI-021")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Click badge: рядки з порожньою кількістю (партії не авто-обрані).")
    public void applyFillsRowsWithEmptyQuantity() {
        RelocationCreateOutputPage form = openSendForm();
        int before = form.productRowCount();
        form.clickBundleBadge(applyBundleName);
        page.waitForCondition(() -> form.productRowCount() >= 1
                && !form.getSelectedResourceValue(0).isBlank());
        assertThat(form.productRowCount()).isGreaterThanOrEqualTo(1);
        assertThat(form.getOutputQuantityValue(0))
                .as("Quantity must stay empty after apply")
                .isBlank();
        assertThat(form.getSelectedResourceValue(0))
                .as("Resource from bundle must be selected")
                .containsIgnoringCase(resourceWithStock.getName().substring(0, Math.min(8, resourceWithStock.getName().length())));
        attachScreenshot("TC-BUNDLE-UI-021 — before=" + before);
    }

    @Test(priority = 3)
    @TestCaseId("TC-BUNDLE-UI-022")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Повторний apply → toast «Усі ресурси з цього комплекту вже додано».")
    public void reapplyShowsAlreadyAddedToast() {
        RelocationCreateOutputPage form = openSendForm();
        form.clickBundleBadge(applyBundleName);
        page.waitForCondition(() -> !form.getSelectedResourceValue(0).isBlank());
        form.clickBundleBadge(applyBundleName);
        form.waitForToast(TOAST_ALREADY);
        assertThat(form.isToastVisible(TOAST_ALREADY)).isTrue();
        attachScreenshot("TC-BUNDLE-UI-022");
    }

    @Test(priority = 4)
    @TestCaseId("TC-BUNDLE-UI-023")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Ресурси без stock → toast про недоступність на складі.")
    public void noStockShowsToast() {
        RelocationCreateOutputPage form = openSendForm();
        form.clickBundleBadge(noStockBundleName);
        form.waitForToast(TOAST_NO_STOCK);
        assertThat(form.isToastVisible(TOAST_NO_STOCK)).isTrue();
        attachScreenshot("TC-BUNDLE-UI-023");
    }

    @Test(priority = 5)
    @TestCaseId("TC-BUNDLE-UI-024")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Частковий apply: уже є 1 ресурс → додаються лише відсутні зі stock.")
    public void partialApplyAddsOnlyMissing() {
        String multiName = bundleFixture.uniqueBundleName("ui-partial-");
        ResourceResponse second = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "p2-");
        bundleFixture.relocation().ensureStock(storageId, second.getId(), 20.0);
        bundleFixture.createBundle(
                UserRole.OWNER_1, storageId, multiName,
                List.of(resourceWithStock.getId(), second.getId()));

        RelocationCreateOutputPage form = openSendForm();
        form.selectOutputResourceByName(resourceWithStock.getName());
        int rowsBefore = form.productRowCount();
        form.clickBundleBadge(multiName);
        page.waitForCondition(() -> form.productRowCount() > rowsBefore
                || form.isToastVisible(TOAST_ALREADY));
        // at least one additional row for second resource (or merge into list)
        assertThat(form.productRowCount()).isGreaterThanOrEqualTo(rowsBefore);
        boolean secondPresent = false;
        for (int i = 0; i < form.productRowCount(); i++) {
            String val = form.getSelectedResourceValue(i);
            if (val != null && val.contains(second.getName().substring(0, Math.min(8, second.getName().length())))) {
                secondPresent = true;
                assertThat(form.getOutputQuantityValue(i)).isBlank();
            }
        }
        assertThat(secondPresent).as("Second bundle resource must be added").isTrue();
        attachScreenshot("TC-BUNDLE-UI-024");
    }

    @Test(priority = 6)
    @TestCaseId("TC-BUNDLE-UI-025")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Apply на /relocation/create-output-crew.")
    public void applyOnCrewIssuanceForm() {
        // Direct navigation: CTA «Видати на екіпаж» requires hasCrews for the location.
        page.navigate(ConfigProvider.getBaseUrl() + RelocationCreateOutputCrewPage.PATH);
        RelocationCreateOutputCrewPage form = new RelocationCreateOutputCrewPage(page).waitForLoaded();
        assertThat(form.isBundleBadgeVisible(applyBundleName)).isTrue();
        form.clickBundleBadge(applyBundleName);
        page.waitForCondition(() -> form.productRowCount() >= 1);
        assertThat(form.getQuantityValue(0)).isBlank();
        attachScreenshot("TC-BUNDLE-UI-025");
    }

    @Test(priority = 7)
    @TestCaseId("TC-BUNDLE-UI-026")
    @Severity(SeverityLevel.NORMAL)
    @Description("На receive формі badges комплектів відсутні.")
    public void badgesAbsentOnReceiveForm() {
        new RelocationPage(page).open().clickReceive();
        assertThat(page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                new com.microsoft.playwright.Page.GetByRoleOptions().setName(applyBundleName)).count())
                .as("Receive form must not show issuance bundle badges")
                .isZero();
        attachScreenshot("TC-BUNDLE-UI-026");
    }

    private RelocationCreateOutputPage openSendForm() {
        return new RelocationPage(page).open().clickSend();
    }

    private void injectOwner1Session() {
        Map<String, String> cookies = getPlaywrightSessionProvider()
                .getSession(UserRole.OWNER_1.getUsername(), UserRole.OWNER_1.getPassword());
        String domain = ConfigProvider.getBaseUrl()
                .replaceFirst("https?://", "")
                .split("/")[0];
        injectSessionCookies(cookies, domain);
        browserContext.addInitScript(
                "localStorage.setItem('selectedStorageId', '" + storageId + "');");
    }
}
