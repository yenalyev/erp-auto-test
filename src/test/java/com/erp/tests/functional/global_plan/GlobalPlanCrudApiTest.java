package com.erp.tests.functional.global_plan;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.RequestBodyFactory;
import com.erp.data.factories.global_plan.GlobalPlanDataFactory;
import com.erp.enums.UserRole;
import com.erp.models.common.GlobalPlanChainContext;
import com.erp.models.request.GlobalPlanRequest;
import com.erp.models.request.RequirementsExportRequest;
import com.erp.models.response.GlobalPlanResponse;
import com.erp.models.response.PlanResponse;
import com.erp.test_context.ContextKey;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Production Planning")
@Feature("Global Plans")
public class GlobalPlanCrudApiTest extends GlobalPlanApiTestBase {

    @Test(priority = 10)
    @TestCaseId("TC-GP-001")
    @Story("List global plans")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            **Мета:** перевірити, що список глобальних планів фільтрується за календарним місяцем і роком.
            
            **Ендпоінт:** `GET /api/v1/global-plans?month={m}&year={y}`
            **Роль:** ADMIN
            
            **Параметри:**
            - Попередньо створюється глобальний план (`POST /global-plans`) з output ресурсу A = 10 од., унікальний місяць/рік.
            - Query: `month` і `year` беруться з відповіді створеного плану.
            
            **Перевірки:**
            - HTTP 200 і валідація JSON Schema списку.
            - У масиві результатів є план з `id`, що збігається зі створеним.
            """)
    public void testGetAllWithYearMonthFilter() {
        GlobalPlanResponse created = globalPlanFixture.createGlobalPlan(10.0);
        trackGlobalPlan(created.getId());

        Map<String, Object> query = new HashMap<>();
        query.put("year", created.getYear());
        query.put("month", created.getMonth());

        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.GLOBAL_PLAN_GET_ALL,
                UserRole.ADMIN,
                query);

        assertThat(response.statusCode()).isEqualTo(200);
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.GLOBAL_PLAN_GET_ALL);

        List<GlobalPlanResponse> plans = response.jsonPath().getList("", GlobalPlanResponse.class);
        assertThat(plans).anyMatch(p -> created.getId().equals(p.getId()));
    }

    @Test(priority = 20)
    @TestCaseId("TC-GP-002")
    @Story("Create global plan")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            **Мета:** підтвердити успішне створення глобального плану з одним рядком output.
            
            **Ендпоінт:** `POST /api/v1/global-plans`
            **Роль:** ADMIN
            
            **Параметри тіла:**
            - `month`, `year` — унікальний період (автоінкремент від now+3 міс.).
            - `output[0]`: ізольований ресурс A з ланцюга M1/M2/M3, `amount = 10.0`.
            - `description` — автогенерований префікс `GP-{month}/{year}-…`.
            
            **Перевірки:**
            - `id`, `month`, `year`, `from`, `to` — не null.
            - `output` містить рівно 1 позицію.
            """)
    public void testCreateGlobalPlan() {
        GlobalPlanResponse created = globalPlanFixture.createGlobalPlan(10.0);
        trackGlobalPlan(created.getId());

        assertThat(created.getId()).isNotNull();
        assertThat(created.getMonth()).isNotNull();
        assertThat(created.getYear()).isNotNull();
        assertThat(created.getFrom()).isNotNull();
        assertThat(created.getTo()).isNotNull();
        assertThat(created.getOutput()).hasSize(1);
    }

    @Test(priority = 30)
    @TestCaseId("TC-GP-003")
    @Story("Duplicate month rejected")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            **Мета:** бекенд має забороняти два глобальні плани на один календарний місяць.
            
            **Ендпоінт:** `POST /api/v1/global-plans` (другий виклик)
            **Роль:** ADMIN
            
            **Параметри:**
            - Перший план: output A = 10, унікальний місяць M.
            - Другий запит: той самий `month`/`year`, ресурс A, `amount = 5.0`.
            
            **Перевірки:**
            - Другий `POST` повертає HTTP 400, поле `month` (повідомлення про існуючий план на місяць).
            """)
    public void testCreateDuplicateMonthReturns400() {
        GlobalPlanResponse first = globalPlanFixture.createGlobalPlan(10.0);
        trackGlobalPlan(first.getId());

        GlobalPlanChainContext chain = globalPlanFixture.requireChain();
        GlobalPlanRequest duplicate = GlobalPlanDataFactory.createPlan(
                first.getMonth(),
                first.getYear(),
                chain.getResourceA().getId(),
                5.0).build();

        Response response = apiExecutor.execute(
                ApiEndpointDefinition.GLOBAL_PLAN_POST_CREATE,
                UserRole.ADMIN,
                duplicate);

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test(priority = 40)
    @TestCaseId("TC-GP-004")
    @Story("Non-producible resource rejected")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            **Мета:** відхилити глобальний план, якщо output-ресурс не має жодної техкарти (не «виробляється»).
            
            **Ендпоінт:** `POST /api/v1/global-plans`
            **Роль:** ADMIN
            
            **Параметри:**
            - `output[0]`: ресурс Z з ізольованого ланцюга (Z лише вхід M3, без карти на виробництво Z).
            - `month`/`year` — наступний унікальний період.
            - `amount = 10.0`.
            
            **Перевірки:**
            - HTTP 400 (валідація виробничої придатності ресурсу).
            """)
    public void testCreateNonProducibleResourceReturns400() {
        GlobalPlanChainContext chain = globalPlanFixture.requireChain();
        YearMonth uniquePeriod = globalPlanFixture.nextUniquePeriod();
        GlobalPlanRequest request = GlobalPlanDataFactory.nonProducibleOutput(
                chain.getResourceZ(),
                uniquePeriod.getMonthValue(),
                uniquePeriod.getYear());

        Response response = apiExecutor.execute(
                ApiEndpointDefinition.GLOBAL_PLAN_POST_CREATE,
                UserRole.ADMIN,
                request);

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test(priority = 50)
    @TestCaseId("TC-GP-005")
    @Story("Update global plan output")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            **Мета:** оновлення глобального плану змінює output/опис, але не зсуває календарний період.
            
            **Ендпоінт:** `PUT /api/v1/global-plans/{id}`
            **Роль:** ADMIN
            
            **Параметри:**
            - Створений план: output A = 10, унікальний місяць.
            - Тіло PUT: копія існуючого плану з суфіксом `UPDATED` у `description` (RequestBodyFactory).
            
            **Перевірки:**
            - HTTP 200 і JSON Schema відповіді.
            - `month`, `year`, `from`, `to` без змін відносно створеного плану.
            - `description` містить `UPDATED`.
            """)
    public void testUpdateReplacesOutputKeepsPeriod() {
        GlobalPlanResponse created = globalPlanFixture.createGlobalPlan(10.0);
        trackGlobalPlan(created.getId());
        testContext.set(ContextKey.GLOBAL_PLAN, created);

        Object body = RequestBodyFactory.generate(ApiEndpointDefinition.GLOBAL_PLAN_PUT_UPDATE, testContext);
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.GLOBAL_PLAN_PUT_UPDATE,
                UserRole.ADMIN,
                body,
                created.getId());

        assertThat(response.statusCode()).isEqualTo(200);
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.GLOBAL_PLAN_PUT_UPDATE);

        GlobalPlanResponse updated = response.as(GlobalPlanResponse.class);
        assertThat(updated.getMonth()).isEqualTo(created.getMonth());
        assertThat(updated.getYear()).isEqualTo(created.getYear());
        assertThat(updated.getFrom()).isEqualTo(created.getFrom());
        assertThat(updated.getTo()).isEqualTo(created.getTo());
        assertThat(updated.getDescription()).contains("UPDATED");
    }

    @Test(priority = 60)
    @TestCaseId("TC-GP-006")
    @Story("Get by id after generation")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            **Мета:** після генерації локальних планів GET за id повертає знімок декомпозиції та посилання на згенеровані плани.
            
            **Ендпоінти:**
            - `POST /api/v1/global-plans/{id}/generate` — повна декомпозиція (3 блоки, single-output M1→M2→M3).
            - `GET /api/v1/global-plans/{id}`
            **Роль:** ADMIN
            
            **Параметри генерації:**
            - Блок 1: A=10 на L1 через M1.
            - Блок 2: B — 12 на L1 + 8 на L2 через M2.
            - Блок 3: C=20 на L1 через M3.
            
            **Перевірки:**
            - `generatedPlans` не порожній.
            - `decomposition` не null.
            """)
    public void testGetByIdIncludesGeneratedPlans() {
        GlobalPlanResponse created = globalPlanFixture.createGlobalPlan(10.0);
        trackGlobalPlan(created.getId());

        var generation = globalPlanFixture.generate(created.getId(), globalPlanFixture.buildCompleteDecomposition());
        trackGeneratedPlans(generation.getPlans().stream()
                .map(gp -> gp.getPlan().getId())
                .toList());

        GlobalPlanResponse fetched = globalPlanFixture.getById(created.getId());
        assertThat(fetched.getGeneratedPlans()).isNotEmpty();
        assertThat(fetched.getDecomposition()).isNotNull();
    }

    @Test(priority = 70)
    @TestCaseId("TC-GP-007")
    @Story("Delete global plan keeps location plans")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            **Мета:** видалення глобального плану не каскадно видаляє вже згенеровані плани на локаціях.
            
            **Ендпоінти:**
            - `POST /global-plans/{id}/generate` — повна декомпозиція.
            - `DELETE /global-plans/{id}`
            - `GET /plans` (фільтр за storage L1) — перевірка збереження.
            **Роль:** ADMIN
            
            **Параметри:** глобальний план output A=10, генерація на L1/L2 з ланцюга M1/M2/M3.
            
            **Перевірки:**
            - DELETE повертає 2xx.
            - Список планів L1 все ще містить id згенерованих location-планів.
            """)
    public void testDeleteGlobalPlanKeepsLocationPlans() {
        GlobalPlanResponse created = globalPlanFixture.createGlobalPlan(10.0);
        trackGlobalPlan(created.getId());
        var generation = globalPlanFixture.generate(created.getId(), globalPlanFixture.buildCompleteDecomposition());
        List<Long> planIds = generation.getPlans().stream().map(gp -> gp.getPlan().getId()).toList();
        trackGeneratedPlans(planIds);

        Response deleteResponse = apiExecutor.execute(
                ApiEndpointDefinition.GLOBAL_PLAN_DELETE,
                UserRole.ADMIN,
                String.valueOf(created.getId()));
        assertThat(deleteResponse.statusCode()).isBetween(200, 299);
        globalPlanIdsToCleanup.remove(created.getId());

        GlobalPlanChainContext chain = globalPlanFixture.requireChain();
        List<PlanResponse> l1Plans = globalPlanFixture.getLocationPlans(chain.getL1StorageId());
        assertThat(l1Plans).anyMatch(p -> planIds.contains(p.getId()));
    }

    @Test(priority = 80)
    @TestCaseId("TC-GP-008")
    @Story("Export requirements")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            **Мета:** експорт зведення потреб у форматі Excel без прив'язки до конкретного глобального плану.
            
            **Ендпоінт:** `POST /api/v1/global-plans/requirements/export`
            **Роль:** ADMIN
            
            **Параметри тіла:**
            - `periodLabel`: «Тестовий період».
            - `semiFinished[0]`: напівфабрикат, required=10 од., stock=2.
            - `rawMaterials[0]`: сировина, required=5 кг, stock=1.
            
            **Перевірки:**
            - HTTP 200.
            - Content-Type — spreadsheet/excel/octet-stream.
            - Тіло відповіді (байти XLSX) не порожнє.
            """)
    public void testExportRequirementsReturnsXlsx() {
        RequirementsExportRequest request = RequirementsExportRequest.builder()
                .periodLabel("Тестовий період")
                .semiFinished(List.of(
                        RequirementsExportRequest.RequirementsExportRow.builder()
                                .name("Напівфабрикат")
                                .requiredAmount(10.0)
                                .unitShortName("од")
                                .totalStock(2.0)
                                .build()))
                .rawMaterials(List.of(
                        RequirementsExportRequest.RequirementsExportRow.builder()
                                .name("Сировина")
                                .requiredAmount(5.0)
                                .unitShortName("кг")
                                .totalStock(1.0)
                                .build()))
                .build();

        Response response = apiExecutor.execute(
                ApiEndpointDefinition.GLOBAL_PLAN_REQUIREMENTS_EXPORT,
                UserRole.ADMIN,
                request);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.getContentType()).containsAnyOf("spreadsheet", "excel", "octet-stream");
        assertThat(response.asByteArray()).isNotEmpty();
    }

    @Test(priority = 90)
    @TestCaseId("TC-GP-009")
    @Story("Delete global plan returns 404 on GET")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            **Мета:** після DELETE глобальний план недоступний через GET,
            але згенеровані location-плани залишаються (доповнення до TC-GP-007).
            
            **Ендпоінти:**
            - `POST /global-plans/{id}/generate`
            - `DELETE /global-plans/{id}`
            - `GET /global-plans/{id}` → 404
            - `GET /plans` (L1) — location plans лишаються
            **Роль:** ADMIN
            """)
    public void testDeleteGlobalPlanReturns404OnGet() {
        GlobalPlanResponse created = globalPlanFixture.createGlobalPlan(10.0);
        trackGlobalPlan(created.getId());
        var generation = globalPlanFixture.generate(created.getId(), globalPlanFixture.buildCompleteDecomposition());
        List<Long> planIds = generation.getPlans().stream().map(gp -> gp.getPlan().getId()).toList();
        trackGeneratedPlans(planIds);

        Response deleteResponse = apiExecutor.execute(
                ApiEndpointDefinition.GLOBAL_PLAN_DELETE,
                UserRole.ADMIN,
                String.valueOf(created.getId()));
        assertThat(deleteResponse.statusCode()).isBetween(200, 299);
        globalPlanIdsToCleanup.remove(created.getId());

        Response getResponse = apiExecutor.execute(
                ApiEndpointDefinition.GLOBAL_PLAN_GET_BY_ID,
                UserRole.ADMIN,
                String.valueOf(created.getId()));
        assertThat(getResponse.statusCode()).isEqualTo(404);

        GlobalPlanChainContext chain = globalPlanFixture.requireChain();
        List<PlanResponse> l1Plans = globalPlanFixture.getLocationPlans(chain.getL1StorageId());
        assertThat(l1Plans).anyMatch(p -> planIds.contains(p.getId()));
    }
}
