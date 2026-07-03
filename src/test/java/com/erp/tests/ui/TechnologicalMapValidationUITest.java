package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.tech_map.TechnologicalMapDataFactory;
import com.erp.enums.StorageTechnologicalMapMode;
import com.erp.enums.UserRole;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.TechnologicalMapFixture;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.pages.TechnologicalMapFormPage;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI coverage for input/output overlap validation on technological map create/update forms.
 * Mirrors API cases TC-MFG-031…034 (TC-UI-TM-031…034) — backend returns 400;
 * tk-ui shows {@code errors[0].messages[0]}.
 *
 * <p>Jira: CPMA-603
 */
@Slf4j
@Issue("CPMA-603")
@Epic("Technological Maps")
@Feature("Input/output validation UI")
public class TechnologicalMapValidationUITest extends BaseUITest {

    private static final String RESOURCE_PREFIX = "ui_tm_val_";
    private static final String OVERLAP_MESSAGE_FRAGMENT = "не може бути одночасно вхідним і вихідним";

    private TechnologicalMapFixture techMapFixture;
    private ResourceFixture resourceFixture;
    private Long storageId;
    private ResourceResponse resourceA;
    private ResourceResponse resourceB;
    private ResourceResponse resourceC;
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
        resourceA = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "A_" + suffix);
        resourceB = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "B_" + suffix);
        resourceC = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "C_" + suffix);

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
    @TestCaseId("TC-UI-TM-031")
    @Story("Create tech map — input/output overlap rejected")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            /technological-maps/create, тип «Виготовлення»: input [A], output [A] — той самий ресурс.
            Після «Зберегти» форма лишається на create, показує помилку бекенду; техкарта не створюється.
            """)
    public void testCannotCreateProductionTechMapWithInputOutputOverlapViaUi() {
        String mapName = uniqueMapName("ui-prod-overlap");
        String resourceName = resourceA.getName().trim();

        TechnologicalMapFormPage form = new TechnologicalMapFormPage(page).openCreate();
        form.selectType(TechnologicalMapFormPage.TYPE_PRODUCTION)
                .fillName(mapName)
                .selectInputResource(0, resourceName)
                .fillInputAmount(0, "2")
                .selectOutputResource(0, resourceName)
                .submit();

        assertOverlapRejectedOnCreate(form, mapName, resourceName);
    }

    @Test(priority = 11)
    @TestCaseId("TC-UI-TM-032")
    @Story("Create tech map — input/output overlap rejected")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            /technological-maps/create, тип «Розбирання»: input [A], output [A].
            Після «Зберегти» — помилка overlap, запис не створюється.
            """)
    public void testCannotCreateDisassembleTechMapWithInputOutputOverlapViaUi() {
        String mapName = uniqueMapName("ui-dis-overlap");
        String resourceName = resourceA.getName().trim();

        TechnologicalMapFormPage form = new TechnologicalMapFormPage(page).openCreate();
        form.selectType(TechnologicalMapFormPage.TYPE_DISASSEMBLE)
                .fillName(mapName)
                .selectInputResource(0, resourceName)
                .selectOutputResource(0, resourceName)
                .fillOutputAmount(0, "0.5")
                .submit();

        assertOverlapRejectedOnCreate(form, mapName, resourceName);
    }

    @Test(priority = 12)
    @TestCaseId("TC-UI-TM-033")
    @Story("Create tech map — input/output overlap rejected")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            /technological-maps/create, тип «Виготовлення»: input [C, B], output [B].
            Overlap на другому input-ресурсі — помилка, техкарта не створюється.
            """)
    public void testCannotCreateTechMapWithMultiInputOutputOverlapViaUi() {
        String mapName = uniqueMapName("ui-multi-overlap");
        String overlappingName = resourceB.getName().trim();
        String otherInputName = resourceC.getName().trim();

        TechnologicalMapFormPage form = new TechnologicalMapFormPage(page).openCreate();
        form.selectType(TechnologicalMapFormPage.TYPE_PRODUCTION)
                .fillName(mapName)
                .selectInputResource(0, otherInputName)
                .fillInputAmount(0, "2")
                .clickAddInputRow()
                .selectInputResource(1, overlappingName)
                .fillInputAmount(1, "1")
                .selectOutputResource(0, overlappingName)
                .submit();

        assertOverlapRejectedOnCreate(form, mapName, overlappingName);
    }

    @Test(priority = 20)
    @TestCaseId("TC-UI-TM-034")
    @Story("Update tech map — input/output overlap rejected")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Arrange: DISASSEMBLE техкарта (input A, output B) через API.
            /technological-maps/update/{id}: змінити output на ресурс A (той самий що input) → помилка overlap.
            Дані техкарти через API без змін.
            """)
    public void testCannotUpdateDisassembleTechMapWithInputOutputOverlapViaUi() {
        List<ResourceResponse> resources = List.of(resourceA, resourceB);
        TechnologicalMapResponse source = techMapFixture.createTechMapWithRequest(
                UserRole.OWNER_1,
                TechnologicalMapDataFactory.createDisassembleTechMap(resources, storageId).build());
        techMapForCleanup = source;

        String inputResourceName = source.getInput().getFirst().getResource().getName().trim();
        String originalOutputName = source.getOutput().getFirst().getResource().getName().trim();

        TechnologicalMapFormPage form = new TechnologicalMapFormPage(page).openUpdate(source.getId());
        form.selectOutputResource(0, inputResourceName)
                .fillOutputAmount(0, "0.5")
                .submit();

        assertThat(form.isErrorVisible()).as("Повідомлення про помилку").isTrue();
        assertThat(form.getErrorText())
                .contains(OVERLAP_MESSAGE_FRAGMENT)
                .contains(inputResourceName);
        assertThat(form.isOnUpdatePage()).as("Форма лишається на update після відмови").isTrue();
        form.attachScreenshot("TC-UI-TM-034 — overlap error on update");

        List<TechnologicalMapResponse> found = techMapFixture.getTechMapsByName(
                storageId, UserRole.ADMIN, source.getName());
        TechnologicalMapResponse current = found.stream()
                .filter(m -> source.getId().equals(m.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Tech map not found: " + source.getId()));

        assertThat(current.getOutput().getFirst().getResource().getName().trim())
                .isEqualTo(originalOutputName);
    }

    private void assertOverlapRejectedOnCreate(
            TechnologicalMapFormPage form,
            String mapName,
            String overlappingResourceName) {

        assertThat(form.isErrorVisible()).as("Повідомлення про помилку").isTrue();
        assertThat(form.getErrorText())
                .contains(OVERLAP_MESSAGE_FRAGMENT)
                .contains(overlappingResourceName);
        assertThat(form.isOnCreatePage()).as("Форма лишається на create після відмови").isTrue();
        form.attachScreenshot("overlap error — " + mapName);

        long count = techMapFixture.countTechMapsByName(storageId, UserRole.ADMIN, mapName);
        assertThat(count).as("Техкарта з ім'ям %s не повинна бути створена", mapName).isZero();
    }

    private String uniqueMapName(String prefix) {
        return prefix + "-" + System.currentTimeMillis();
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
