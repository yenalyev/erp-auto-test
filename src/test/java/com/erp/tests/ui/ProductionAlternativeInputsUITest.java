package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.StorageTechnologicalMapMode;
import com.erp.enums.UserRole;
import com.erp.fixtures.ProductionFixture;
import com.erp.fixtures.TechnologicalMapFixture;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.pages.ProductionCreateFormPage;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.ProductionStockAssertions;
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

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI coverage for alternative resource selection on production create wizard.
 */
@Slf4j
@Epic("Production")
@Feature("Alternative inputs UI")
public class ProductionAlternativeInputsUITest extends BaseUITest {

    private static final double MIN_STOCK = 200.0;
    private static final double PRODUCE_AMOUNT = 5.0;

    private ProductionFixture productionFixture;
    private TechnologicalMapFixture techMapFixture;
    private Long storageId;
    private TechnologicalMapResponse techMap;
    private Long fixedInputId;
    private Long defaultAltId;
    private Long otherAltId;
    private Long outputId;
    private Double defaultAltAmount;
    private Double otherAltAmount;
    private Double fixedInputAmount;
    private String defaultAltName;
    private String otherAltName;
    private String productName;
    private String techMapName;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        productionFixture = new ProductionFixture(testContext, apiExecutor);
        techMapFixture = productionFixture.getTechMapFixture();
        techMapFixture.prepareContext();
        storageId = ConfigProvider.getOwner1StorageId();

        techMapFixture.setMode(storageId, StorageTechnologicalMapMode.EDIT_ALLOWED);
        techMap = techMapFixture.createTechMapWithAlternativeGroup(UserRole.ADMIN, storageId);

        fixedInputId = techMap.getInput().getFirst().getResource().getId();
        fixedInputAmount = techMap.getInput().getFirst().getAmount();
        outputId = techMap.getOutput().getFirst().getResource().getId();
        productName = techMap.getOutput().getFirst().getResource().getName().trim();
        techMapName = techMap.getName();

        var group = techMap.getGroups().getFirst();
        var defaultRes = group.getAlternativeResources().stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsDefault()))
                .findFirst()
                .orElseThrow();
        var otherRes = group.getAlternativeResources().stream()
                .filter(r -> !Boolean.TRUE.equals(r.getIsDefault()))
                .findFirst()
                .orElseThrow();
        defaultAltId = defaultRes.getResource().getId();
        defaultAltAmount = defaultRes.getAmount();
        otherAltId = otherRes.getResource().getId();
        otherAltAmount = otherRes.getAmount();
        defaultAltName = defaultRes.getResource().getName().trim();
        otherAltName = otherRes.getResource().getName().trim();
    }

    @AfterClass(alwaysRun = true)
    public void cleanup() {
        if (techMap != null && techMapFixture != null && storageId != null) {
            techMapFixture.deactivateTechMap(UserRole.OWNER_1, techMap.getId(), storageId);
            techMapFixture.setMode(storageId, StorageTechnologicalMapMode.READ_ONLY);
        }
    }

    @BeforeMethod(alwaysRun = true)
    public void prepareUiSession() {
        productionFixture.ensureStockForTechMapInputs(storageId, techMap, MIN_STOCK);
        injectRoleSession(UserRole.OWNER_1, storageId);
    }

    @Test(priority = 10)
    @TestCaseId("TC-UI-PROD-ALT-001")
    @Story("Default alternative preselected")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Wizard: tech map з групою → default preselected у select «Клей»")
    public void testDefaultAlternativePreselectedInWizard() {
        ProductionCreateFormPage form = new ProductionCreateFormPage(page).open()
                .ensureShiftSelected()
                .selectProduct(productName)
                .selectTechMap(techMapName)
                .fillAmount(String.valueOf((int) PRODUCE_AMOUNT));

        assertThat(form.isAlternativeResourcesSectionVisible()).isTrue();
        String selected = form.getSelectedAlternativeResourceLabel("Клей");
        assertThat(selected).contains(defaultAltName);
        form.attachScreenshot("TC-UI-PROD-ALT-001 — default preselected");
    }

    @Test(priority = 11)
    @TestCaseId("TC-UI-PROD-ALT-002")
    @Story("Switch non-default alternative and submit")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Switch на non-default → submit → stock delta через API verify")
    public void testSwitchNonDefaultAlternativeAndSubmit() {
        Set<Long> resourceIds = Set.of(fixedInputId, defaultAltId, otherAltId, outputId);
        ProductionStockAssertions.StockSnapshot before = ProductionStockAssertions.capture(
                apiExecutor, storageId, UserRole.OWNER_1, resourceIds, "before UI non-default production");

        ProductionCreateFormPage form = new ProductionCreateFormPage(page).open()
                .ensureShiftSelected()
                .selectProduct(productName)
                .selectTechMap(techMapName)
                .fillAmount(String.valueOf((int) PRODUCE_AMOUNT))
                .selectAlternativeResource("Клей", otherAltName);

        assertThat(form.isSubmitEnabled()).isTrue();
        form.submit();

        ProductionStockAssertions.StockSnapshot after = ProductionStockAssertions.capture(
                apiExecutor, storageId, UserRole.OWNER_1, resourceIds, "after UI non-default production");

        Map<Long, Double> expectedDelta = Map.of(
                fixedInputId, -(PRODUCE_AMOUNT * fixedInputAmount),
                defaultAltId, 0.0,
                otherAltId, -(PRODUCE_AMOUNT * otherAltAmount),
                outputId, PRODUCE_AMOUNT * techMap.getOutput().getFirst().getAmount()
        );
        ProductionStockAssertions.assertDelta(before, after, expectedDelta, outputId);
    }

    @Test(priority = 12)
    @TestCaseId("TC-UI-PROD-ALT-003")
    @Story("Submit disabled when group unselected")
    @Severity(SeverityLevel.NORMAL)
    @Description("Clear select альтернативи → кнопка «Зберегти всі» disabled")
    public void testSubmitDisabledWhenAlternativeUnselected() {
        ProductionCreateFormPage form = new ProductionCreateFormPage(page).open()
                .ensureShiftSelected()
                .selectProduct(productName)
                .selectTechMap(techMapName)
                .fillAmount(String.valueOf((int) PRODUCE_AMOUNT))
                .clearAlternativeSelection("Клей");

        assertThat(form.isSubmitEnabled()).isFalse();
        form.attachScreenshot("TC-UI-PROD-ALT-003 — submit disabled");
    }

    private void injectRoleSession(UserRole role, long selectedStorageId) {
        Map<String, String> cookies = getPlaywrightSessionProvider()
                .getSession(role.getUsername(), role.getPassword());
        String domain = ConfigProvider.getBaseUrl()
                .replaceFirst("https?://", "")
                .split("/")[0];
        injectSessionCookies(cookies, domain);
        browserContext.addInitScript(
                "localStorage.setItem('selectedStorageId', '" + selectedStorageId + "');");
    }
}
