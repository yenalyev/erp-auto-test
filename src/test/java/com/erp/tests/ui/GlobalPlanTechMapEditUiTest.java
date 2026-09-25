package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.global_plan.GlobalPlanDataFactory;
import com.erp.enums.StorageTechnologicalMapMode;
import com.erp.enums.UserRole;
import com.erp.fixtures.GlobalPlanFixture;
import com.erp.fixtures.TechnologicalMapFixture;
import com.erp.fixtures.TechnologicalMapFixture.IsolatedTechMapContext;
import com.erp.models.request.DecompositionRequest;
import com.erp.models.request.GlobalPlanRequest;
import com.erp.models.response.GenerationResponse;
import com.erp.models.response.GlobalPlanResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.pages.TechnologicalMapFormPage;
import com.erp.utils.config.ConfigProvider;
import com.erp.validators.SchemaRegistry;
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

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Production Planning")
@Feature("Global Plans UI")
public class GlobalPlanTechMapEditUiTest extends BaseUITest {

    private static final String STRUCTURAL_EDIT_TEST_ID = "TC-GP-UI-063";
    private static final String NOTIFICATION_TEST_ID = "TC-GP-UI-064";
    private static final List<String> UKRAINIAN_MONTHS = List.of(
            "Січень", "Лютий", "Березень", "Квітень", "Травень", "Червень",
            "Липень", "Серпень", "Вересень", "Жовтень", "Листопад", "Грудень");

    private GlobalPlanFixture globalPlanFixture;
    private TechnologicalMapFixture techMapFixture;
    private Long storageId;
    private final List<String> techMapNamesToCleanup = new ArrayList<>();
    private final List<Long> globalPlanIdsToCleanup = new ArrayList<>();
    private final List<Long> generatedPlanIdsToCleanup = new ArrayList<>();

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        globalPlanFixture = new GlobalPlanFixture(testContext, apiExecutor);
        techMapFixture = new TechnologicalMapFixture(testContext, apiExecutor);
        globalPlanFixture.getResourceFixture().fetchSharedUnit(1);
        globalPlanFixture.getResourceFixture().fetchSharedResourceCategory();
        storageId = ConfigProvider.getOwner1StorageId();
        techMapFixture.setMode(storageId, StorageTechnologicalMapMode.EDIT_ALLOWED);

        Map<String, String> cookies = cachedSessionCookies(UserRole.ADMIN);
        injectSessionCookies(cookies, sessionCookieDomain());
        injectAllLocationsView();
    }

    @AfterClass(alwaysRun = true)
    public void cleanup() {
        if (globalPlanFixture != null) {
            globalPlanFixture.cleanupGeneratedPlans(generatedPlanIdsToCleanup);
            for (Long planId : globalPlanIdsToCleanup) {
                try {
                    globalPlanFixture.deleteGlobalPlan(planId);
                } catch (AssertionError e) {
                    log.warn("Global plan cleanup failed for id {}: {}", planId, e.getMessage());
                }
            }
        }
        if (techMapFixture != null && storageId != null) {
            for (String techMapName : techMapNamesToCleanup) {
                for (TechnologicalMapResponse map : techMapFixture.getActiveTechMapsByName(
                        storageId, UserRole.ADMIN, techMapName)) {
                    try {
                        techMapFixture.deactivateTechMap(UserRole.ADMIN, map.getId(), storageId);
                    } catch (Exception e) {
                        log.warn("Tech map cleanup failed for id {}: {}", map.getId(), e.getMessage());
                    }
                }
            }
        }
        if (techMapFixture != null && storageId != null) {
            techMapFixture.setMode(storageId, StorageTechnologicalMapMode.READ_ONLY);
        }
    }

    @Test(priority = 5)
    @TestCaseId(NOTIFICATION_TEST_ID)
    @Story("Confirmation popup for a tech map used by a live global plan")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            **Мета:** після структурної зміни техкарти з актуального global-plan snapshot і натискання
            «Зберегти» UI показує confirmation popup про нову версію та повторний розподіл.

            **Підготовка (API):** ізольована M1 + майбутній GP + decompose/generate зі snapshot M1.
            **Очікування:** popup має точні title/description і кнопки «Скасувати»/«Зберегти»;
            скасування закриває popup і не створює нову версію.
            """)
    public void structuralEditConfirmationShowsExactPopup() {
        TechMapInPlan context = Allure.step(
                "API arrange: ізольована M1 у snapshot майбутнього GP",
                () -> arrangeTechMapInPlan("GP-UI-064-M1"));
        TechnologicalMapResponse techMap = context.techMap();
        GlobalPlanResponse globalPlan = context.globalPlan();

        double originalInputAmount = techMap.getInput().getFirst().getAmount();
        TechnologicalMapFormPage form = Allure.step("UI: відкрити M1 і викликати confirmation popup", () ->
                new TechnologicalMapFormPage(page)
                        .openUpdate(techMap.getId(), storageId)
                        .waitForLiveGlobalPlanWarning(globalPlan.getDescription())
                        .fillInputAmount(0, String.valueOf(originalInputAmount + 1.0))
                        .requestStructuralUpdateConfirmation());

        Allure.step("Assert: точний текст і кнопки popup", () -> {
            String expectedDescription = normalizeWhitespace("""
                    Розподіл у глобальних планах %s стане недійсним для цієї картки.

                    Після збереження відкрийте кожен план, призначте ресурс знову й повторно
                    згенеруйте плани на локації.
                    """.formatted(globalPlanLabel(globalPlan)));

            form.attachScreenshot(NOTIFICATION_TEST_ID + " — structural edit confirmation popup");
            assertThat(form.isReassignConfirmationVisible()).isTrue();
            assertThat(form.getReassignConfirmationTitle())
                    .isEqualTo("Створити нову версію техкарти?");
            assertThat(normalizeWhitespace(form.getReassignConfirmationDescription()))
                    .isEqualTo(expectedDescription);
            assertThat(form.isReassignConfirmationButtonVisible("Скасувати")).isTrue();
            assertThat(form.isReassignConfirmationButtonVisible("Зберегти")).isTrue();
        });

        Allure.step("UI + API assert: скасувати popup без створення нової версії", () -> {
            form.dismissReassignConfirmation();
            assertThat(form.isReassignConfirmationVisible()).isFalse();

            TechnologicalMapResponse afterCancel = techMapFixture.getById(
                    UserRole.ADMIN, techMap.getId(), storageId);
            assertThat(afterCancel.getVersion()).isEqualTo(techMap.getVersion());
            assertThat(afterCancel.getInput().getFirst().getAmount()).isEqualTo(originalInputAmount);
        });
    }

    @Test(priority = 10)
    @TestCaseId(STRUCTURAL_EDIT_TEST_ID)
    @Story("Confirm structural tech-map update used by a live global plan")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            **Мета:** UI дозволяє структурно змінити техкарту зі snapshot актуального глобального плану,
            але попереджає про плани, показує посилання на них і вимагає явного підтвердження.

            **Підготовка (API):** ізольована M1 + майбутній GP + decompose/generate зі snapshot M1.
            **Кроки:** відкрити update M1; змінити норму input; натиснути «Зберегти»; підтвердити modal.
            **Очікування:** warning містить GP і link; modal пояснює повторне призначення; PUT=200;
            створена активна version+1, а snapshot плану продовжує містити id M1.
            """)
    public void structuralEditShowsWarningAndCreatesNewVersionAfterConfirmation() {
        TechMapInPlan context = Allure.step(
                "API arrange: M1 у snapshot майбутнього GP",
                () -> arrangeTechMapInPlan("GP-UI-063-M1"));
        TechnologicalMapResponse oldVersion = context.techMap();
        GlobalPlanResponse globalPlan = context.globalPlan();
        String techMapName = oldVersion.getName();
        double newInputAmount = oldVersion.getInput().getFirst().getAmount() + 1.0;

        TechnologicalMapFormPage form = Allure.step("UI: відкрити M1 і перевірити warning з GP", () -> {
            TechnologicalMapFormPage opened = new TechnologicalMapFormPage(page)
                    .openUpdate(oldVersion.getId(), storageId)
                    .waitForLiveGlobalPlanWarning(globalPlan.getDescription());
            opened.attachScreenshot(STRUCTURAL_EDIT_TEST_ID + " — live global plan warning");
            assertThat(opened.getLiveGlobalPlanWarningText())
                    .contains(globalPlan.getDescription())
                    .contains("доведеться перепризначити")
                    .contains("повторно згенеруйте");
            assertThat(opened.getGlobalPlanLinkHref(globalPlan.getId()))
                    .isEqualTo("/global-plans/" + globalPlan.getId());
            return opened;
        });

        Allure.step("UI: structural change вимагає confirmation", () -> {
            form.fillInputAmount(0, String.valueOf(newInputAmount))
                    .requestStructuralUpdateConfirmation();
            form.attachScreenshot(STRUCTURAL_EDIT_TEST_ID + " — structural update confirmation");
            assertThat(form.isReassignConfirmationVisible()).isTrue();
            assertThat(form.getReassignConfirmationText())
                    .contains("Створити нову версію техкарти?")
                    .contains(globalPlan.getDescription())
                    .contains("призначте ресурс знову");
        });

        Allure.step("UI + API assert: підтвердити, створити version+1, snapshot не переписати", () -> {
            assertThat(form.confirmReassignAndSubmit()).isEqualTo(200);

            TechnologicalMapResponse newVersion = techMapFixture.getActiveTechMapsByName(
                            storageId, UserRole.ADMIN, techMapName).stream()
                    .max(Comparator.comparing(TechnologicalMapResponse::getVersion))
                    .orElseThrow(() -> new AssertionError("Active updated tech map not found"));
            assertThat(newVersion.getId()).isNotEqualTo(oldVersion.getId());
            assertThat(newVersion.getVersion()).isEqualTo(oldVersion.getVersion() + 1);
            assertThat(newVersion.getInput().getFirst().getAmount()).isEqualTo(newInputAmount);

            GlobalPlanResponse planAfter = globalPlanFixture.getById(globalPlan.getId());
            assertThat(GlobalPlanFixture.snapshotTechMapIds(planAfter))
                    .contains(oldVersion.getId())
                    .doesNotContain(newVersion.getId());
        });
    }

    private TechMapInPlan arrangeTechMapInPlan(String namePrefix) {
        IsolatedTechMapContext isolated = techMapFixture.createIsolatedProductionTechMap(
                UserRole.ADMIN, storageId, namePrefix);
        TechnologicalMapResponse techMap = isolated.getTechMap();
        techMapNamesToCleanup.add(techMap.getName());
        Long productId = isolated.getProduct().getId();

        YearMonth period = globalPlanFixture.nextUniquePeriod();
        GlobalPlanRequest request = GlobalPlanDataFactory.createPlan(
                period.getMonthValue(), period.getYear(), productId, 10.0).build();
        Response createResponse = apiExecutor.execute(
                ApiEndpointDefinition.GLOBAL_PLAN_POST_CREATE,
                UserRole.ADMIN,
                request);
        assertThat(createResponse.statusCode()).isEqualTo(200);
        SchemaRegistry.validateIfSuccess(createResponse, ApiEndpointDefinition.GLOBAL_PLAN_POST_CREATE);
        GlobalPlanResponse globalPlan = createResponse.as(GlobalPlanResponse.class);
        globalPlanIdsToCleanup.add(globalPlan.getId());

        DecompositionRequest decomposition = DecompositionRequest.builder()
                .blocks(List.of(GlobalPlanDataFactory.block(GlobalPlanDataFactory.item(
                        productId,
                        GlobalPlanDataFactory.assignment(storageId, techMap.getId(), "10")))))
                .build();
        globalPlanFixture.decompose(globalPlan.getId(), decomposition);
        GenerationResponse generation = globalPlanFixture.generate(globalPlan.getId(), decomposition);
        generatedPlanIdsToCleanup.addAll(generation.getPlans().stream()
                .map(item -> item.getPlan().getId())
                .toList());

        GlobalPlanResponse generated = globalPlanFixture.getById(globalPlan.getId());
        assertThat(GlobalPlanFixture.snapshotTechMapIds(generated)).contains(techMap.getId());
        return new TechMapInPlan(generated, techMap);
    }

    private static String normalizeWhitespace(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }

    private static String globalPlanLabel(GlobalPlanResponse globalPlan) {
        String period = UKRAINIAN_MONTHS.get(globalPlan.getFrom().getMonthValue() - 1)
                + " " + globalPlan.getFrom().getYear();
        String description = globalPlan.getDescription() == null ? "" : globalPlan.getDescription().trim();
        return !description.isBlank() && !description.equals(period)
                ? description + " (" + period + ")"
                : period;
    }

    private record TechMapInPlan(GlobalPlanResponse globalPlan, TechnologicalMapResponse techMap) {}
}
