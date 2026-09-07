package com.erp.tests.functional.global_plan;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.global_plan.GlobalPlanDataFactory;
import com.erp.data.factories.tech_map.TechnologicalMapDataFactory;
import com.erp.enums.StorageTechnologicalMapMode;
import com.erp.enums.UserRole;
import com.erp.fixtures.TechnologicalMapFixture;
import com.erp.fixtures.TechnologicalMapFixture.IsolatedTechMapContext;
import com.erp.models.common.GlobalPlanChainContext;
import com.erp.models.request.DecompositionRequest;
import com.erp.models.request.GlobalPlanRequest;
import com.erp.models.request.TechnologicalMapRequest;
import com.erp.models.response.GenerationResponse;
import com.erp.models.response.GlobalPlanResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Production Planning")
@Feature("Global Plans")
@Story("Tech map deactivation guard")
public class GlobalPlanTechMapDeactivationGuardTest extends GlobalPlanApiTestBase {

    private TechnologicalMapFixture techMapFixture;
    private GlobalPlanChainContext chain;
    private Long l1StorageId;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "setupGlobalPlanApiSuite")
    @Step("Підготовка режиму EDIT_ALLOWED для деактивації техкарт на L1")
    public void setupTechMapDeactivationGuard() {
        techMapFixture = new TechnologicalMapFixture(testContext, apiExecutor);
        chain = globalPlanFixture.requireChain();
        l1StorageId = chain.getL1StorageId();
        techMapFixture.setMode(l1StorageId, StorageTechnologicalMapMode.EDIT_ALLOWED);
    }

    @AfterClass(alwaysRun = true)
    @Step("Відновити READ_ONLY для режиму техкарт локації L1")
    public void restoreTechMapMode() {
        if (techMapFixture != null && l1StorageId != null) {
            techMapFixture.setMode(l1StorageId, StorageTechnologicalMapMode.READ_ONLY);
        }
    }

    @DataProvider(name = "ownerAndAdminRoles")
    public Object[][] ownerAndAdminRoles() {
        return new Object[][]{
                {UserRole.OWNER_1},
                {UserRole.ADMIN}
        };
    }

    @Test(priority = 10, dataProvider = "ownerAndAdminRoles")
    @TestCaseId("TC-GP-046")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            **Мета:** заборонити деактивацію техкарти, якщо вона збережена в decomposition snapshot
            глобального плану на **майбутній** місяць.
            
            **Ендпоінт:** `DELETE /api/v1/technological-maps/{id}?storageId={storageId}`
            
            **Arrange:**
            1. POST /global-plans — output A, унікальний майбутній місяць.
            2. POST /decompose + POST /generate — повна декомпозиція з M1 @ L1 (snapshot зберігається лише після generate).
            
            **Act:** DELETE M1 @ L1.
            
            **Очікування:** HTTP 400, повідомлення про використання в глобальному плані; M1 лишається active.
            """)
    public void testCannotDeactivateTechMapWhenUsedInFutureGlobalPlan(UserRole role) {
        GlobalPlanResponse globalPlan = arrangeGlobalPlanWithGeneratedDecomposition(globalPlanFixture.nextUniquePeriod());

        long activeCountBefore = techMapFixture.countActiveTechMapsByName(
                l1StorageId, UserRole.ADMIN, chain.getMapM1().getName());

        Response response = Allure.step(role + ": DELETE deactivate M1 (expected failure)", () ->
                techMapFixture.deactivateTechMap(role, chain.getMapM1().getId(), l1StorageId));

        Allure.step("Assert: відмова через глобальний план «" + globalPlan.getDescription() + "»", () -> {
            techMapFixture.assertUsedInGlobalPlanRejection(response, globalPlan.getDescription());
            assertThat(techMapFixture.countActiveTechMapsByName(
                    l1StorageId, UserRole.ADMIN, chain.getMapM1().getName()))
                    .isEqualTo(activeCountBefore);
        });
    }

    private GlobalPlanResponse currentMonthGuardPlan;

    @Test(priority = 11, dataProvider = "ownerAndAdminRoles")
    @TestCaseId("TC-GP-047")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            **Мета:** заборонити деактивацію техкарти, якщо вона в decomposition snapshot
            глобального плану на **поточний** календарний місяць.
            
            Snapshot з'являється після POST /generate, не після /decompose.
            Arrange GP виконується один раз на клас-інстанс і перевикористовується для OWNER_1 та ADMIN.
            """)
    public void testCannotDeactivateTechMapWhenUsedInCurrentMonthGlobalPlan(UserRole role) {
        GlobalPlanResponse globalPlan = ensureCurrentMonthGuardPlan();

        long activeCountBefore = techMapFixture.countActiveTechMapsByName(
                l1StorageId, UserRole.ADMIN, chain.getMapM1().getName());

        Response response = Allure.step(role + ": DELETE deactivate M1 (expected failure)", () ->
                techMapFixture.deactivateTechMap(role, chain.getMapM1().getId(), l1StorageId));

        Allure.step("Assert: відмова через глобальний план поточного місяця", () -> {
            techMapFixture.assertUsedInGlobalPlanRejection(response, globalPlan.getDescription());
            assertThat(techMapFixture.countActiveTechMapsByName(
                    l1StorageId, UserRole.ADMIN, chain.getMapM1().getName()))
                    .isEqualTo(activeCountBefore);
        });
    }

    /**
     * Shared arrange for TC-GP-047: create current-month GP once so ADMIN does not skip
     * after OWNER_1 occupied the period.
     */
    private GlobalPlanResponse ensureCurrentMonthGuardPlan() {
        if (currentMonthGuardPlan != null) {
            return currentMonthGuardPlan;
        }
        YearMonth currentMonth = allocateCurrentMonthIfFree();
        if (currentMonth == null) {
            throw new SkipException("Поточний місяць вже зайнятий іншим глобальним планом на staging/dev");
        }
        currentMonthGuardPlan = arrangeGlobalPlanWithGeneratedDecomposition(currentMonth);
        return currentMonthGuardPlan;
    }

    @Test(priority = 12, dataProvider = "ownerAndAdminRoles")
    @TestCaseId("TC-GP-048")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            **Мета:** глобальний план блокує деактивацію конкретної техкарти зі snapshot,
            навіть якщо для продукту існує інша активна техкарта (на відміну від per-location guard TC-MFG-028).
            
            **Arrange:** друга активна техкарта на ресурс A; generate з M1 у snapshot.
            **Act:** DELETE M1.
            **Очікування:** HTTP 400.
            """)
    public void testCannotDeactivateTechMapWhenAlternateActiveMapExists(UserRole role) {
        TechnologicalMapResponse alternate = Allure.step("Arrange: друга активна техкарта на ресурс A", () ->
                techMapFixture.createAlternateActiveTechMap(UserRole.ADMIN, chain.getMapM1()));

        GlobalPlanResponse globalPlan = arrangeGlobalPlanWithGeneratedDecomposition(globalPlanFixture.nextUniquePeriod());

        long activeCountBefore = techMapFixture.countActiveTechMapsByName(
                l1StorageId, UserRole.ADMIN, chain.getMapM1().getName());

        Response response = Allure.step(role + ": DELETE deactivate M1 despite alternate map (expected failure)", () ->
                techMapFixture.deactivateTechMap(role, chain.getMapM1().getId(), l1StorageId));

        Allure.step("Assert: деактивація заблокована глобальним планом «" + globalPlan.getDescription() + "»", () -> {
            techMapFixture.assertUsedInGlobalPlanRejection(response, globalPlan.getDescription());
            assertThat(techMapFixture.countActiveTechMapsByName(
                    l1StorageId, UserRole.ADMIN, chain.getMapM1().getName()))
                    .isEqualTo(activeCountBefore);
            assertThat(techMapFixture.getActiveTechMapsByName(l1StorageId, UserRole.ADMIN, alternate.getName()))
                    .anyMatch(m -> alternate.getId().equals(m.getId()));
        });
    }

    @Test(priority = 15, dataProvider = "ownerAndAdminRoles")
    @TestCaseId("TC-GP-053")
    @Story("Name update allowed in global plan")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            **Мета:** дозволити редагування **лише назви** техкарти, якщо вона в decomposition snapshot
            глобального плану — зміна назви не створює нову версію (id і version без змін).
            
            **Ендпоінт:** `PUT /api/v1/technological-maps/{id}`
            
            **Arrange:** global plan + generate, M1 @ L1 у snapshot.
            **Act:** PUT — нова назва, input/output без змін.
            **Очікування:** HTTP 200; той самий id і version; назва оновлена.
            """)
    public void testCanUpdateTechMapNameWhenUsedInFutureGlobalPlan(UserRole role) {
        GlobalPlanResponse globalPlan = arrangeGlobalPlanWithGeneratedDecomposition(globalPlanFixture.nextUniquePeriod());

        TechnologicalMapResponse mapBefore = techMapFixture.getById(
                UserRole.ADMIN, chain.getMapM1().getId(), l1StorageId);
        String newName = mapBefore.getName() + "-renamed-" + System.currentTimeMillis();
        TechnologicalMapRequest updateRequest = TechnologicalMapDataFactory.withRenamed(mapBefore, newName);

        Response response = Allure.step(role + ": PUT rename M1 (expected success)", () ->
                techMapFixture.updateTechMap(role, chain.getMapM1().getId(), updateRequest));

        Allure.step("Assert: назва оновлена, id і version без змін (GP «" + globalPlan.getDescription() + "»)", () -> {
            assertThat(response.statusCode()).isEqualTo(200);
            TechnologicalMapResponse updated = response.as(TechnologicalMapResponse.class);
            assertThat(updated.getId()).isEqualTo(mapBefore.getId());
            assertThat(updated.getVersion()).isEqualTo(mapBefore.getVersion());
            assertThat(updated.getName()).isEqualTo(newName);

            TechnologicalMapResponse fetched = techMapFixture.getById(
                    UserRole.ADMIN, chain.getMapM1().getId(), l1StorageId);
            assertThat(fetched.getName()).isEqualTo(newName);
        });
    }

    @Test(priority = 16, dataProvider = "ownerAndAdminRoles")
    @TestCaseId("TC-GP-054")
    @Story("Structural update blocked by global plan")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            **Мета:** заборонити PUT, що змінює структуру техкарти (input/output/groups), якщо вона в snapshot
            глобального плану — така зміна створила б нову версію (новий id), що зламає decomposition.
            
            **Ендпоінт:** `PUT /api/v1/technological-maps/{id}`
            
            **Arrange:** ізольована техкарта + global plan + generate (не shared M1 — structural PUT може деактивувати map).
            **Act:** PUT — змінити норму першого input (version bump).
            **Очікування:** HTTP 400; id, version і input без змін.
            
            **Відомий дефект (dev):** guard відсутній на structural PUT — можливий 200 і createNewVersion.
            Тест — regression до фіксу в `tk`.
            """)
    public void testCannotUpdateTechMapStructureWhenUsedInFutureGlobalPlan(UserRole role) {
        TechMapInGlobalPlan context = arrangeIsolatedTechMapInGlobalPlan();
        GlobalPlanResponse globalPlan = context.globalPlan();
        TechnologicalMapResponse mapBefore = context.techMap();

        TechnologicalMapResponse mapFetched = techMapFixture.getById(
                UserRole.ADMIN, mapBefore.getId(), l1StorageId);
        double originalAmount = mapFetched.getInput().getFirst().getAmount();
        double modifiedAmount = originalAmount + 1.0;
        TechnologicalMapRequest updateRequest = TechnologicalMapDataFactory.withFirstInputAmount(mapFetched, modifiedAmount);

        long activeCountBefore = techMapFixture.countActiveTechMapsByName(
                l1StorageId, UserRole.ADMIN, mapFetched.getName());

        Response response = Allure.step(role + ": PUT change input amount (expected failure)", () ->
                techMapFixture.updateTechMap(role, mapBefore.getId(), updateRequest));

        Allure.step("Assert: відмова через глобальний план «" + globalPlan.getDescription() + "»", () -> {
            techMapFixture.assertUsedInGlobalPlanRejection(response, globalPlan.getDescription());

            TechnologicalMapResponse mapAfter = techMapFixture.getById(
                    UserRole.ADMIN, mapBefore.getId(), l1StorageId);
            assertThat(mapAfter.getVersion())
                    .as("Version не має змінитись")
                    .isEqualTo(mapFetched.getVersion());
            assertThat(mapAfter.getInput().getFirst().getAmount())
                    .as("Норма input не має змінитись")
                    .isEqualTo(originalAmount);
            assertThat(techMapFixture.countActiveTechMapsByName(
                    l1StorageId, UserRole.ADMIN, mapFetched.getName()))
                    .as("Не має з'явитись нова активна версія")
                    .isEqualTo(activeCountBefore);
        });
    }

    @Test(priority = 20, dataProvider = "ownerAndAdminRoles")
    @TestCaseId("TC-GP-049")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            **Мета:** деактивація дозволена, якщо глобальний план створено без generate —
            decomposition snapshot відсутній, guard не спрацьовує.
            
            Використовується ізольована техкарта, щоб не конфліктувати з іншими тестами класу.
            """)
    public void testCanDeactivateTechMapWhenGlobalPlanHasNoDecompositionSnapshot(UserRole role) {
        IsolatedTechMapContext context = techMapFixture.createIsolatedProductionTechMap(UserRole.ADMIN, l1StorageId);
        TechnologicalMapResponse techMap = context.getTechMap();

        Allure.step("Arrange: глобальний план без generate (decomposition=null)", () -> {
            YearMonth period = globalPlanFixture.nextUniquePeriod();
            GlobalPlanResponse created = globalPlanFixture.createGlobalPlanForPeriod(
                    period.getMonthValue(),
                    period.getYear(),
                    10.0);
            trackGlobalPlan(created.getId());
            assertThat(globalPlanFixture.getById(created.getId()).getDecomposition()).isNull();
        });

        long activeCountBefore = techMapFixture.countActiveTechMapsByName(
                l1StorageId, UserRole.ADMIN, techMap.getName());

        Response response = Allure.step(role + ": DELETE deactivate isolated tech map", () ->
                techMapFixture.deactivateTechMap(role, techMap.getId(), l1StorageId));

        Allure.step("Assert: деактивація дозволена — snapshot відсутній", () -> {
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(techMapFixture.countActiveTechMapsByName(
                    l1StorageId, UserRole.ADMIN, techMap.getName()))
                    .isEqualTo(activeCountBefore - 1);
        });
    }

    private GlobalPlanResponse arrangeGlobalPlanWithGeneratedDecomposition(YearMonth period) {
        return Allure.step("Arrange: global plan " + period + " + decompose + generate", () -> {
            GlobalPlanResponse created = globalPlanFixture.createGlobalPlanForPeriod(
                    period.getMonthValue(), period.getYear(), 10.0);
            trackGlobalPlan(created.getId());

            DecompositionRequest decomposition = globalPlanFixture.buildCompleteDecomposition();
            globalPlanFixture.decompose(created.getId(), decomposition);
            GenerationResponse generation = globalPlanFixture.generate(created.getId(), decomposition);
            trackGeneratedPlans(generation.getPlans().stream()
                    .map(gp -> gp.getPlan().getId())
                    .toList());

            GlobalPlanResponse fetched = globalPlanFixture.getById(created.getId());
            assertThat(fetched.getDecomposition()).isNotNull();
            return fetched;
        });
    }

    private record TechMapInGlobalPlan(GlobalPlanResponse globalPlan, TechnologicalMapResponse techMap) {}

    private TechMapInGlobalPlan arrangeIsolatedTechMapInGlobalPlan() {
        return Allure.step("Arrange: isolated tech map in global plan + generate", () -> {
            IsolatedTechMapContext isolated = techMapFixture.createIsolatedProductionTechMap(UserRole.ADMIN, l1StorageId);
            TechnologicalMapResponse techMap = isolated.getTechMap();
            Long outputResourceId = techMapFixture.getOutputResourceId(techMap);

            YearMonth period = globalPlanFixture.nextUniquePeriod();
            GlobalPlanRequest gpRequest = GlobalPlanDataFactory.createPlan(
                    period.getMonthValue(), period.getYear(), outputResourceId, 10.0).build();

            Response createResponse = apiExecutor.execute(
                    ApiEndpointDefinition.GLOBAL_PLAN_POST_CREATE,
                    UserRole.ADMIN,
                    gpRequest);
            assertThat(createResponse.statusCode()).isEqualTo(200);
            SchemaRegistry.validateIfSuccess(createResponse, ApiEndpointDefinition.GLOBAL_PLAN_POST_CREATE);
            GlobalPlanResponse created = createResponse.as(GlobalPlanResponse.class);
            trackGlobalPlan(created.getId());

            DecompositionRequest decomposition = DecompositionRequest.builder()
                    .blocks(List.of(GlobalPlanDataFactory.block(GlobalPlanDataFactory.item(
                            outputResourceId,
                            GlobalPlanDataFactory.assignment(l1StorageId, techMap.getId(), "10")))))
                    .build();
            globalPlanFixture.decompose(created.getId(), decomposition);
            GenerationResponse generation = globalPlanFixture.generate(created.getId(), decomposition);
            trackGeneratedPlans(generation.getPlans().stream()
                    .map(gp -> gp.getPlan().getId())
                    .toList());

            GlobalPlanResponse fetched = globalPlanFixture.getById(created.getId());
            assertThat(fetched.getDecomposition()).isNotNull();
            return new TechMapInGlobalPlan(fetched, techMap);
        });
    }

    private YearMonth allocateCurrentMonthIfFree() {
        YearMonth current = YearMonth.now();
        boolean occupied = globalPlanFixture.getAllGlobalPlans().stream()
                .anyMatch(p -> p.getYear() == current.getYear()
                        && p.getMonth() == current.getMonthValue());
        return occupied ? null : current;
    }
}
