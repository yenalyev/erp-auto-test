package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.storage.StorageDataFactory;
import com.erp.enums.StorageAccessMode;
import com.erp.enums.UserRole;
import com.erp.fixtures.InventoryFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.StorageRegionFixture;
import com.erp.fixtures.TestArtifactCleanup;
import com.erp.models.request.StorageRequest;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageRegionResponse;
import com.erp.models.response.StorageResponse;
import com.erp.pages.InventoryEditPage;
import com.erp.tests.functional.storage.StorageRegionsAllureDescriptions;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.*;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * UI: область видимості ресурсів CREW береться з батьківського UNIT (RESOURCES region).
 * Фікс додавання ресурсів під час інвентаризації екіпажу.
 */
@Slf4j
@Epic("Inventory")
@Feature("Crew resource visibility")
@Story("CREW inherits parent UNIT RESOURCES scope")
public class CrewResourceVisibilityUiTest extends BaseUITest {

    private static final String SCENARIO_PREFIX = "ui-crew-res-";
    private static final String RESOURCE_PREFIX = "ui-crew-res-";
    private static final double ADD_AMOUNT = 3.0;

    private StorageFixture storageFixture;
    private StorageRegionFixture regionFixture;
    private ResourceFixture resourceFixture;
    private InventoryFixture inventoryFixture;

    private Long crewId;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        storageFixture = new StorageFixture(testContext, apiExecutor);
        regionFixture = new StorageRegionFixture(testContext, apiExecutor);
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        inventoryFixture = new InventoryFixture(testContext, apiExecutor);

        resourceFixture.fetchSharedUnit(3);
        resourceFixture.fetchSharedResourceCategory();
    }

    @AfterMethod(alwaysRun = true)
    public void cleanupScenario() {
        if (crewId != null) {
            try {
                inventoryFixture.ensureClosed(crewId);
            } catch (Exception e) {
                log.warn("Failed to close inventory session for crew {}: {}", crewId, e.getMessage());
            }
            crewId = null;
        }
        TestArtifactCleanup.cleanupRegionsAndStorages(regionFixture, storageFixture);
    }

    @AfterClass(alwaysRun = true)
    public void cleanupClassArtifacts() {
        TestArtifactCleanup.cleanupRegionsAndStorages(regionFixture, storageFixture);
    }

    @Test
    @TestCaseId("TC-UI-STR-RES-012")
    @Severity(SeverityLevel.CRITICAL)
    @Description(StorageRegionsAllureDescriptions.TC_UI_STR_RES_012)
    public void crewInventoryFormAddsResourceFromParentVisibilityScope() {
        ResourceResponse visible = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "vis-");
        ResourceResponse hidden = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "hid-");
        String visibleName = visible.getName().trim().replaceAll("\\s+", " ");
        String hiddenName = hidden.getName().trim().replaceAll("\\s+", " ");

        RestrictedCrewSetup setup = Allure.step(
                "API: parent UNIT (REGIONS) + RESOURCES region + CREW",
                () -> prepareRestrictedCrewWithVisibleResource(visible.getId()));
        crewId = setup.crew().getId();

        Allure.step("API: відкрити сесію інвентаризації на CREW", () -> {
            inventoryFixture.ensureClosed(crewId);
            inventoryFixture.openSession(crewId);
            assertThat(inventoryFixture.getResourceStock(crewId, visible.getId(), UserRole.ADMIN))
                    .as("До інвентаризації visible ще немає на екіпажі")
                    .isEqualTo(0.0);
        });

        injectRoleSession(UserRole.ADMIN, crewId);

        InventoryEditPage edit = Allure.step(
                "UI: форма інвентаризації /inventory/{crewId}",
                () -> new InventoryEditPage(page).open(crewId));

        Allure.step("UI: autocomplete наслідує область ресурсів parent UNIT", () -> {
            assertThat(edit.isAddResourceOptionVisible(visibleName))
                    .as("Ресурс з області parent має бути в «Оберіть ресурс» для CREW")
                    .isTrue();
            edit.closeAddResourceAutocomplete();

            assertThat(edit.isAddResourceOptionVisible(hiddenName))
                    .as("Ресурс поза областю parent не повинен бути в autocomplete CREW")
                    .isFalse();
            edit.closeAddResourceAutocomplete();
            edit.attachScreenshot("TC-UI-STR-RES-012 — autocomplete scope");
        });

        Allure.step("UI: додати visible ресурс і зберегти", () -> {
            edit.addResource(visibleName, String.valueOf((int) ADD_AMOUNT));
            assertThat(edit.isResourceListed(visibleName))
                    .as("Ресурс має з'явитися на формі після «Додати»")
                    .isTrue();
            assertThat(edit.getResourceAmountInputValue(visibleName))
                    .isEqualTo(String.valueOf((int) ADD_AMOUNT));
            edit.attachScreenshot("TC-UI-STR-RES-012 — form after add");
            edit.saveChanges();
        });

        Allure.step("API: stock на CREW оновлено", () -> {
            assertThat(inventoryFixture.getResourceStock(crewId, visible.getId(), UserRole.ADMIN))
                    .as("Після UI inventory visible з області parent зарахований на CREW")
                    .isCloseTo(ADD_AMOUNT, within(0.01));
        });
    }

    private RestrictedCrewSetup prepareRestrictedCrewWithVisibleResource(long visibleResourceId) {
        StorageResponse parentUnit = storageFixture.resolveParentUnit();
        StorageRequest unitRequest = StorageDataFactory.restrictedStorage(
                parentUnit.getId(), SCENARIO_PREFIX + "unit-").build();
        StorageResponse unit = storageFixture.createStorage(unitRequest);

        StorageRegionResponse region = regionFixture.createRegion(
                unit, StorageAccessMode.RESOURCES, SCENARIO_PREFIX + "reg-");
        regionFixture.addRegionMembers(region.getId(), unit.getId());
        regionFixture.addRegionResources(region.getId(), visibleResourceId);

        StorageResponse crew = storageFixture.createStorage(
                StorageDataFactory.crewStorage(unit.getId(), SCENARIO_PREFIX + "crew-")
                        .accessMode(StorageAccessMode.REGIONS)
                        .build());

        return new RestrictedCrewSetup(unit, crew, region);
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

    private record RestrictedCrewSetup(
            StorageResponse unit,
            StorageResponse crew,
            StorageRegionResponse region) {
    }
}
