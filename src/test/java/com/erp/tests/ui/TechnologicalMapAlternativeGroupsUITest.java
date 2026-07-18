package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.StorageTechnologicalMapMode;
import com.erp.enums.UserRole;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.TechnologicalMapFixture;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.TechnologicalMapAlternativeGroupResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.pages.TechnologicalMapFormPage;
import com.erp.pages.TechnologicalMapsListPage;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Comparator;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI coverage for alternative resource groups on technological map create form.
 */
@Slf4j
@Epic("Technological Maps")
@Feature("Alternative groups UI")
public class TechnologicalMapAlternativeGroupsUITest extends BaseUITest {

    private static final String RESOURCE_PREFIX = "ui_tm_alt_";

    private TechnologicalMapFixture techMapFixture;
    private ResourceFixture resourceFixture;
    private Long storageId;
    private ResourceResponse fixedInput;
    private ResourceResponse defaultAlt;
    private ResourceResponse otherAlt;
    private ResourceResponse output;
    private TechnologicalMapResponse techMapForCleanup;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        techMapFixture = new TechnologicalMapFixture(testContext, apiExecutor);
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        techMapFixture.prepareContext();
        storageId = techMapFixture.getOwner1StorageId();

        String suffix = String.valueOf(System.currentTimeMillis());
        fixedInput = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "F_" + suffix);
        defaultAlt = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "D_" + suffix);
        otherAlt = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "E_" + suffix);
        output = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "P_" + suffix);

        techMapFixture.setMode(storageId, StorageTechnologicalMapMode.EDIT_ALLOWED);
    }

    @AfterClass(alwaysRun = true)
    public void restoreReadOnlyMode() {
        if (techMapFixture != null && storageId != null) {
            techMapFixture.setMode(storageId, StorageTechnologicalMapMode.READ_ONLY);
        }
    }

    @BeforeMethod(alwaysRun = true)
    public void prepareUiSession() {
        injectRoleSession(UserRole.OWNER_1, storageId);
    }

    @AfterMethod(alwaysRun = true)
    public void cleanupArtifacts() {
        if (techMapForCleanup != null && techMapFixture != null && storageId != null) {
            techMapFixture.deactivateTechMap(UserRole.OWNER_1, techMapForCleanup.getId(), storageId);
            techMapForCleanup = null;
        }
    }

    @Test(priority = 10)
    @TestCaseId("TC-UI-TM-ALT-001")
    @Story("Alternative groups section for PRODUCTION")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            /technological-maps/create: тип «Виготовлення» показує секцію
            «Групи альтернативних (взаємозамінних) ресурсів»;
            тип «Розбирання» — секція прихована.
            """)
    public void testAlternativeGroupsSectionVisibleOnlyForProduction() {
        TechnologicalMapFormPage form = new TechnologicalMapFormPage(page).openCreate();

        form.selectType(TechnologicalMapFormPage.TYPE_PRODUCTION);
        assertThat(form.isAlternativeGroupsSectionVisible())
                .as("Секція alt groups видима для Виготовлення")
                .isTrue();
        form.attachScreenshot("TC-UI-TM-ALT-001 — PRODUCTION shows alt groups");

        form.selectType(TechnologicalMapFormPage.TYPE_DISASSEMBLE);
        assertThat(form.isAlternativeGroupsSectionVisible())
                .as("Секція alt groups прихована для Розбирання")
                .isFalse();
        form.attachScreenshot("TC-UI-TM-ALT-001 — DISASSEMBLE hides alt groups");
    }

    @Test(priority = 20)
    @TestCaseId("TC-UI-TM-ALT-002")
    @Story("Create tech map with alternative group via UI")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            OWNER_1 створює PRODUCTION техкарту з групою «Клей»:
            default ресурс D@2, alt E@2.5, fixed input + output.
            Після збереження API GET підтверджує groups і рівно один isDefault.
            Перший ресурс у новій групі — default (преселект UI).
            """)
    public void testCreateTechMapWithAlternativeGroupViaUi() {
        String mapName = "ui-alt-group-" + System.currentTimeMillis();

        TechnologicalMapFormPage form = new TechnologicalMapFormPage(page).openCreate();
        form.selectType(TechnologicalMapFormPage.TYPE_PRODUCTION)
                .fillName(mapName)
                .selectInputResource(0, fixedInput.getName().trim())
                .fillInputAmount(0, "1")
                .selectOutputResource(0, output.getName().trim())
                .clickAddAlternativeGroup()
                .fillAlternativeGroupName(0, "Клей")
                .selectAlternativeGroupResource(0, 0, defaultAlt.getName().trim())
                .fillAlternativeGroupAmount(0, 0, "2")
                .clickAddResourceInAlternativeGroup(0)
                .selectAlternativeGroupResource(0, 1, otherAlt.getName().trim())
                .fillAlternativeGroupAmount(0, 1, "2.5");

        assertThat(form.isAlternativeGroupDefaultChecked(0, 0))
                .as("Перший ресурс у групі — default (Осн.)")
                .isTrue();

        form.submit();
        page.waitForURL(
                url -> !url.contains("/technological-maps/create"),
                new com.microsoft.playwright.Page.WaitForURLOptions().setTimeout(30_000));

        TechnologicalMapResponse created = techMapFixture.getTechMapsByName(
                        storageId, UserRole.ADMIN, mapName)
                .stream()
                .findFirst()
                .orElseThrow(() -> new AssertionError("Created tech map not found: " + mapName));
        techMapForCleanup = created;

        assertThat(created.getGroups()).as("groups").isNotNull().hasSize(1);
        TechnologicalMapAlternativeGroupResponse group = created.getGroups().getFirst();
        assertThat(group.getName()).isEqualTo("Клей");
        assertThat(group.getAlternativeResources()).hasSize(2);

        long defaults = group.getAlternativeResources().stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsDefault()))
                .count();
        assertThat(defaults).isEqualTo(1);

        Long defaultId = group.getAlternativeResources().stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsDefault()))
                .map(r -> r.getResource().getId())
                .findFirst()
                .orElseThrow();
        assertThat(defaultId).isEqualTo(defaultAlt.getId());
    }

    @Test(priority = 21)
    @TestCaseId("TC-UI-TM-ALT-003")
    @Story("Change default alternative via UI")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Створення техкарти з 2 альтернативами: спочатку default на першому,
            потім radio «Осн.» на другому → збереження → API: isDefault на otherAlt.
            """)
    public void testCreateTechMapWithSwitchedDefaultViaUi() {
        String mapName = "ui-alt-def-" + System.currentTimeMillis();

        TechnologicalMapFormPage form = new TechnologicalMapFormPage(page).openCreate();
        form.selectType(TechnologicalMapFormPage.TYPE_PRODUCTION)
                .fillName(mapName)
                .selectInputResource(0, fixedInput.getName().trim())
                .fillInputAmount(0, "1")
                .selectOutputResource(0, output.getName().trim())
                .clickAddAlternativeGroup()
                .fillAlternativeGroupName(0, "Пальне")
                .selectAlternativeGroupResource(0, 0, defaultAlt.getName().trim())
                .fillAlternativeGroupAmount(0, 0, "1.5")
                .clickAddResourceInAlternativeGroup(0)
                .selectAlternativeGroupResource(0, 1, otherAlt.getName().trim())
                .fillAlternativeGroupAmount(0, 1, "1.8")
                .setAlternativeGroupDefault(0, 1);

        assertThat(form.isAlternativeGroupDefaultChecked(0, 1)).isTrue();
        assertThat(form.isAlternativeGroupDefaultChecked(0, 0)).isFalse();

        form.submit();
        page.waitForURL(
                url -> !url.contains("/technological-maps/create"),
                new com.microsoft.playwright.Page.WaitForURLOptions().setTimeout(30_000));

        TechnologicalMapResponse created = techMapFixture.getTechMapsByName(
                        storageId, UserRole.ADMIN, mapName)
                .stream()
                .findFirst()
                .orElseThrow(() -> new AssertionError("Created tech map not found: " + mapName));
        techMapForCleanup = created;

        Long defaultId = created.getGroups().getFirst().getAlternativeResources().stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsDefault()))
                .map(r -> r.getResource().getId())
                .findFirst()
                .orElseThrow();
        assertThat(defaultId).isEqualTo(otherAlt.getId());
    }

    @Test(priority = 30)
    @TestCaseId("TC-UI-TM-ALT-004")
    @Story("Update tech map default via UI")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Update form: swap default radio → save → API version+1")
    public void testUpdateTechMapSwapsDefaultViaUi() {
        TechnologicalMapResponse source = techMapFixture.createTechMapWithAlternativeGroup(UserRole.ADMIN, storageId);
        long versionBefore = source.getVersion() != null ? source.getVersion() : 0L;

        TechnologicalMapFormPage form = new TechnologicalMapFormPage(page).openUpdate(source.getId());
        form.setAlternativeGroupDefault(0, 1).submit();
        page.waitForURL(
                url -> !url.contains("/technological-maps/update/"),
                new com.microsoft.playwright.Page.WaitForURLOptions().setTimeout(30_000));

        TechnologicalMapResponse updated = techMapFixture.getActiveTechMapsByName(storageId, UserRole.ADMIN, source.getName())
                .stream()
                .max(Comparator.comparing(TechnologicalMapResponse::getVersion))
                .orElseThrow(() -> new AssertionError("Updated tech map not found"));
        techMapForCleanup = updated;

        Long newDefaultId = updated.getGroups().getFirst().getAlternativeResources().stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsDefault()))
                .map(r -> r.getResource().getId())
                .findFirst()
                .orElseThrow();
        assertThat(newDefaultId).isEqualTo(
                source.getGroups().getFirst().getAlternativeResources().stream()
                        .filter(r -> !Boolean.TRUE.equals(r.getIsDefault()))
                        .map(r -> r.getResource().getId())
                        .findFirst()
                        .orElseThrow());
        assertThat(updated.getVersion()).isGreaterThan(versionBefore);
    }

    @Test(priority = 31)
    @TestCaseId("TC-UI-TM-ALT-005")
    @Story("List shows interchangeable column")
    @Severity(SeverityLevel.NORMAL)
    @Description("Список техкарт: колонка «Взаємозамінні» показує назву групи та ★ default")
    public void testListShowsInterchangeableColumnForAltGroup() {
        TechnologicalMapResponse created = techMapFixture.createTechMapWithAlternativeGroup(UserRole.ADMIN, storageId);
        techMapForCleanup = created;

        TechnologicalMapsListPage listPage = new TechnologicalMapsListPage(page).openForStorage(storageId);
        listPage.waitForTableSettled();

        String columnText = listPage.getInterchangeableColumnTextForTechMap(created.getName());
        assertThat(columnText).contains("Клей");
        assertThat(columnText).contains("★");
    }

    @Test(priority = 32)
    @TestCaseId("TC-UI-TM-ALT-006")
    @Story("Clone pre-fills alternative groups")
    @Severity(SeverityLevel.NORMAL)
    @Description("Clone via ?cloneId= → секція alt groups видима, назва групи заповнена")
    public void testClonePreFillsAlternativeGroups() {
        TechnologicalMapResponse source = techMapFixture.createTechMapWithAlternativeGroup(UserRole.ADMIN, storageId);
        techMapForCleanup = source;

        TechnologicalMapFormPage form = new TechnologicalMapFormPage(page).openClone(source.getId());
        assertThat(form.isAlternativeGroupsSectionVisible()).isTrue();
        assertThat(page.getByPlaceholder("Назва групи (напр.: Пальне)").inputValue()).contains("Клей");
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
