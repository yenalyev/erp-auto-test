package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.tech_map.TechnologicalMapDataFactory;
import com.erp.enums.StorageTechnologicalMapMode;
import com.erp.enums.UserRole;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.TechnologicalMapFixture;
import com.erp.models.request.ResourceUsageRequest;
import com.erp.models.request.TechnologicalMapRequest;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.pages.TechnologicalMapsListPage;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.qameta.allure.Allure;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI coverage for tech-map list search by product (output) and raw material (input)
 * on {@code /technological-maps} for OWNER_1 and ADMIN.
 */
@Slf4j
@Epic("Technological Maps")
@Feature("Tech map list search UI")
public class TechnologicalMapSearchUITest extends BaseUITest {

    private static final String RESOURCE_PREFIX = "ui_tm_srch_";

    private TechnologicalMapFixture techMapFixture;
    private ResourceFixture resourceFixture;
    private Long storageId;

    private ResourceResponse productA;
    private ResourceResponse productB;
    private ResourceResponse ingredientA;
    private ResourceResponse ingredientB;
    private ResourceResponse sharedOther;

    /** Map A: ingredientA → productA. Map B: ingredientB → productB. */
    private TechnologicalMapResponse mapA;
    private TechnologicalMapResponse mapB;

    private final List<TechnologicalMapResponse> createdMaps = new ArrayList<>();

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        techMapFixture = new TechnologicalMapFixture(testContext, apiExecutor);
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        techMapFixture.prepareContext();
        storageId = techMapFixture.getOwner1StorageId();

        String suffix = String.valueOf(System.currentTimeMillis());
        productA = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "prodA_" + suffix);
        productB = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "prodB_" + suffix);
        ingredientA = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "ingA_" + suffix);
        ingredientB = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "ingB_" + suffix);
        sharedOther = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "other_" + suffix);

        mapA = createProductionMap("ui-tm-mapA", ingredientA, sharedOther, productA);
        mapB = createProductionMap("ui-tm-mapB", ingredientB, sharedOther, productB);
    }

    @AfterClass(alwaysRun = true)
    public void cleanupArtifacts() {
        for (TechnologicalMapResponse map : createdMaps) {
            try {
                techMapFixture.deactivateTechMap(UserRole.ADMIN, map.getId(), storageId);
            } catch (Exception e) {
                log.warn("Failed to deactivate tech map {}: {}", map.getId(), e.getMessage());
            }
        }
        if (techMapFixture != null && storageId != null) {
            techMapFixture.setMode(storageId, StorageTechnologicalMapMode.READ_ONLY);
        }
    }

    @DataProvider(name = "adminAndOwnerRoles")
    public Object[][] adminAndOwnerRoles() {
        return new Object[][]{
                {UserRole.ADMIN},
                {UserRole.OWNER_1}
        };
    }

    @Test(priority = 5)
    @TestCaseId("TC-MFG-002")
    @Story("Tech maps list page smoke")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            REQ-MFG-001-01 AC-02: сторінка /technological-maps має title, пошук, кнопку
            «+ Нова тех. карта» та перелік активних техкарт користувача.
            """)
    public void techMapsListPageShowsExpectedChrome() {
        TechnologicalMapsListPage listPage = openListAs(UserRole.OWNER_1);

        Allure.step("Перевірити chrome сторінки списку техкарт", () -> {
            assertThat(listPage.isPageTitleVisible()).as("Title «Перегляд тех. карт»").isTrue();
            assertThat(listPage.isProductSearchVisible()).as("Пошук за продуктом").isTrue();
            assertThat(listPage.isIngredientSearchVisible()).as("Пошук за сировиною").isTrue();
            assertThat(listPage.isNewTechMapButtonVisible()).as("Кнопка «Нова тех. карта»").isTrue();
            assertThat(listPage.getDisplayedTechMapNames())
                    .as("Є хоча б одна активна техкарта (підготовлена фікстурою)")
                    .isNotEmpty();
            listPage.attachScreenshot("TC-MFG-002 — tech maps list chrome");
        });
    }

    @Test(dataProvider = "adminAndOwnerRoles", priority = 10)
    @TestCaseId("TC-UI-TM-SRCH-001")
    @Story("Search tech maps by product")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            OWNER_1 / ADMIN відкриває /technological-maps на локації Owner1.
            У полі «Пошук за продуктом» вводить унікальну назву output-ресурсу техкарти A.
            Таблиця показує техкарти з цим продуктом і не показує техкарту з іншим продуктом.
            """)
    public void searchTechMapsByProduct(UserRole role) {
        String productTerm = productA.getName().trim();
        String expectedMapName = mapA.getName().trim();
        String unexpectedMapName = mapB.getName().trim();

        Allure.parameter("role", role.name());
        Allure.parameter("productTerm", productTerm);
        Allure.parameter("expectedMap", expectedMapName);
        Allure.parameter("unexpectedMap", unexpectedMapName);

        TechnologicalMapsListPage listPage = openListAs(role);

        Allure.step("Пошук за продуктом: таблиця фільтрує техкарти", () -> {
            listPage.filterByProduct(productTerm);
            listPage.attachScreenshot("TC-UI-TM-SRCH-001 — filtered by product — " + role);

            assertThat(listPage.isTechMapNameVisible(expectedMapName))
                    .as("Техкарта з продуктом A має бути видима")
                    .isTrue();
            assertThat(listPage.isTechMapNameVisible(unexpectedMapName))
                    .as("Техкарта з продуктом B не повинна бути видима")
                    .isFalse();

            List<String> displayed = listPage.getDisplayedTechMapNames();
            assertThat(displayed)
                    .as("UI-таблиця містить техкарту з продуктом A")
                    .anyMatch(name -> name.contains(expectedMapName));
            assertThat(displayed)
                    .as("UI-таблиця не містить техкарту з продуктом B")
                    .noneMatch(name -> name.contains(unexpectedMapName));
        });

        log.info("TC-UI-TM-SRCH-001 PASSED — role={}, product={}", role, productTerm);
    }

    @Test(dataProvider = "adminAndOwnerRoles", priority = 20)
    @TestCaseId("TC-UI-TM-SRCH-002")
    @Story("Search tech maps by raw material")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            OWNER_1 / ADMIN відкриває /technological-maps на локації Owner1.
            У полі «Пошук за сировиною» вводить унікальну назву input-ресурсу техкарти.
            Таблиця показує техкарти з цією сировиною і не показує техкарту з іншою сировиною.
            """)
    public void searchTechMapsByIngredient(UserRole role) {
        String ingredientTerm = ingredientA.getName().trim();
        String expectedMapName = mapA.getName().trim();
        String unexpectedMapName = mapB.getName().trim();

        Allure.parameter("role", role.name());
        Allure.parameter("ingredientTerm", ingredientTerm);
        Allure.parameter("expectedMap", expectedMapName);
        Allure.parameter("unexpectedMap", unexpectedMapName);

        TechnologicalMapsListPage listPage = openListAs(role);

        Allure.step("Пошук за сировиною: таблиця фільтрує техкарти", () -> {
            listPage.filterByIngredient(ingredientTerm);
            listPage.attachScreenshot("TC-UI-TM-SRCH-002 — filtered by ingredient — " + role);

            assertThat(listPage.isTechMapNameVisible(expectedMapName))
                    .as("Техкарта із сировиною A має бути видима")
                    .isTrue();
            assertThat(listPage.isTechMapNameVisible(unexpectedMapName))
                    .as("Техкарта із сировиною B не повинна бути видима")
                    .isFalse();

            List<String> displayed = listPage.getDisplayedTechMapNames();
            assertThat(displayed)
                    .as("UI-таблиця містить техкарту із сировиною A")
                    .anyMatch(name -> name.contains(expectedMapName));
            assertThat(displayed)
                    .as("UI-таблиця не містить техкарту із сировиною B")
                    .noneMatch(name -> name.contains(unexpectedMapName));
        });

        log.info("TC-UI-TM-SRCH-002 PASSED — role={}, ingredient={}", role, ingredientTerm);
    }

    private TechnologicalMapsListPage openListAs(UserRole role) {
        injectRoleSession(role, storageId);
        if (page != null) {
            try {
                page.close();
            } catch (Exception ignored) {
                // discarded — fresh page needed after init script
            }
        }
        page = browserContext.newPage();
        int timeoutMs = ConfigProvider.getUiTimeoutSeconds() * 1000;
        page.setDefaultTimeout(timeoutMs);
        page.setDefaultNavigationTimeout(timeoutMs);

        return Allure.step("Відкрити /technological-maps як " + role, () ->
                new TechnologicalMapsListPage(page).openForStorage(storageId));
    }

    private TechnologicalMapResponse createProductionMap(
            String namePrefix,
            ResourceResponse input1,
            ResourceResponse input2,
            ResourceResponse output) {

        TechnologicalMapRequest request = TechnologicalMapDataFactory.createProductionMapWithStorages(
                namePrefix,
                List.of(
                        new ResourceUsageRequest(input1.getId(), 2.0),
                        new ResourceUsageRequest(input2.getId(), 1.0)),
                List.of(new ResourceUsageRequest(output.getId(), 1.0)),
                Set.of(storageId)).build();

        TechnologicalMapResponse created = techMapFixture.createTechMapWithRequest(UserRole.ADMIN, request);
        createdMaps.add(created);
        return created;
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
