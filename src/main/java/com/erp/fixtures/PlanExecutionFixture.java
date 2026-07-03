package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.relocation.RelocationStockSeeder;
import com.erp.enums.UserRole;
import com.erp.models.query.ProductionJournalQuery;
import com.erp.models.response.ManufacturingItemResponse;
import com.erp.models.response.PlanResponse;
import com.erp.models.response.ResourceUsageResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.test_context.TestContext;
import com.erp.utils.helpers.DatabaseIntegrityValidator;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;

import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Test-data setup for the "Виконання плану" (Plan Execution) UI, {@code /plan-execution}.
 *
 * <p>Backend ({@code StatisticsFacade.getExecution} in {@code tk}) includes a product row only
 * when it has an active PRODUCTION tech map on the storage AND (planGoal &gt; 0 OR totalProduced
 * &gt; 0). A storage has at most one Plan document per month ({@code PlanController} /
 * {@code PlanDecorator}), so "clearing the plan" means deleting that single per-month document.
 */
@Slf4j
public class PlanExecutionFixture extends BaseFixture {

    private final TechnologicalMapFixture techMapFixture;
    private final ProductionFixture productionFixture;

    public PlanExecutionFixture(TestContext testContext, ApiExecutor apiExecutor) {
        super(testContext, apiExecutor);
        this.techMapFixture = new TechnologicalMapFixture(testContext, apiExecutor);
        this.productionFixture = new ProductionFixture(testContext, apiExecutor);
    }

    @Step("FIXTURE: Підготовка середовища для тестів виконання плану")
    public void prepareContext() {
        techMapFixture.prepareContext();
    }

    /**
     * Deletes the storage's current-month Plan document, if one exists. A storage can have at
     * most one Plan per calendar month, so this fully resets the "план" side of the page for the
     * current period.
     */
    @Step("API: ADMIN — видалити (якщо існує) план поточного місяця для сховища {storageId}")
    public void ensureNoPlanForCurrentMonth(Long storageId) {
        YearMonth now = YearMonth.now();
        techMapFixture.getLocationPlans(storageId).stream()
                .filter(plan -> plan.getYear() != null && plan.getMonth() != null)
                .filter(plan -> plan.getYear() == now.getYear() && plan.getMonth() == now.getMonthValue())
                .forEach(plan -> {
                    log.info("Deleting existing current-month plan id={} for storage={}", plan.getId(), storageId);
                    techMapFixture.deleteLocationPlan(plan.getId());
                });
    }

    /**
     * Fail-fast precondition check for the "немає плану і немає виробництва" scenario: verifies
     * that the storage genuinely has no production recorded this month. Without this guard,
     * unrelated background activity (other automated tests, demo usage) on a shared dev/staging
     * storage could leave the lead/lag card visible regardless of our own test setup, turning a
     * real product defect (or a real pass) into a flaky, hard-to-diagnose failure. We prefer a
     * clear diagnostic failure over a silently wrong assertion.
     */
    @Step("Перевірити, що сховище {storageId} не має виробництва за поточний місяць (передумова для 'немає плану і немає виробництва')")
    public void assertNoProductionThisMonth(Long storageId) {
        YearMonth now = YearMonth.now();
        ProductionJournalQuery query = ProductionJournalQuery.builder()
                .storageId(storageId)
                .startDate(now.atDay(1))
                .endDate(now.atEndOfMonth())
                .pageSize(1)
                .build();

        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.PRODUCTION_GET_JOURNAL_PAGE,
                UserRole.ADMIN,
                query.toQueryParams());
        validateSuccess(response, "Check current-month production for storage " + storageId);

        long total = DatabaseIntegrityValidator.extractPageTotalElements(response);
        if (total > 0) {
            throw new IllegalStateException(
                    "Середовище забруднене: сховище " + storageId + " вже має " + total
                            + " запис(и) виробництва за " + now + ", створені поза цим тестом. "
                            + "Сценарій 'немає плану і немає виробництва' неможливо гарантовано "
                            + "перевірити на такому сховищі — перевірте фонову активність "
                            + "(інші тести/демо) на цьому складі і повторіть запуск.");
        }
    }

    /** Creates a brand-new, uniquely-named product with an active PRODUCTION tech map on {@code storageId}. */
    @Step("Створити ізольований продукт з активною виробничою техкартою на сховищі {storageId}")
    public TechnologicalMapFixture.IsolatedTechMapContext createIsolatedProduct(Long storageId) {
        return techMapFixture.createIsolatedProductionTechMap(UserRole.ADMIN, storageId);
    }

    /** Creates the storage's current-month plan with a single output target for {@code resourceId}. */
    @Step("API: створити план поточного місяця для продукту {resourceId} на сховищі {storageId} (ціль {target})")
    public PlanResponse createCurrentMonthPlan(Long storageId, Long resourceId, double target) {
        return techMapFixture.createLocationPlan(storageId, resourceId, YearMonth.now(), target);
    }

    /** Seeds input stock and creates a current-month production batch for {@code techMap}'s output product. */
    @Step("API: створити виробництво поточного місяця — {amount} од. на сховищі {storageId}")
    public ManufacturingItemResponse createCurrentMonthProduction(Long storageId,
                                                                   TechnologicalMapResponse techMap,
                                                                   double amount) {
        seedInputsForTechMap(storageId, techMap, amount);
        String batchNumber = "plan-exec-" + System.currentTimeMillis();
        return productionFixture.createAs(UserRole.ADMIN, storageId, techMap, amount, batchNumber);
    }

    @Step("Видалити план {plan.id}, якщо він був створений цим тестом")
    public void cleanupPlan(PlanResponse plan) {
        if (plan != null && plan.getId() != null) {
            techMapFixture.deleteLocationPlan(plan.getId());
        }
    }

    /**
     * Deletes a production batch created by a test. Without this, production history left behind
     * by {@link #createCurrentMonthProduction} would accumulate on shared dev/staging storages
     * across suite re-runs and eventually break {@link #assertNoProductionThisMonth}'s fail-fast
     * precondition for unrelated "no plan / no production" scenarios.
     */
    @Step("Видалити виробництво {production.id}, якщо воно було створене цим тестом")
    public void cleanupProduction(ManufacturingItemResponse production, Long storageId) {
        if (production != null && production.getId() != null) {
            productionFixture.deleteAs(UserRole.ADMIN, production.getId(), storageId);
        }
    }

    @Step("Деактивувати ізольовану техкарту {techMap.id} на сховищі {storageId}")
    public void cleanupTechMap(TechnologicalMapResponse techMap, Long storageId) {
        if (techMap != null && techMap.getId() != null) {
            techMapFixture.deactivateTechMap(UserRole.ADMIN, techMap.getId(), storageId);
        }
    }

    private void seedInputsForTechMap(Long storageId, TechnologicalMapResponse techMap, double outputAmount) {
        Map<Long, Double> inputs = new HashMap<>(techMap.getInput().stream()
                .collect(Collectors.toMap(
                        (ResourceUsageResponse usage) -> usage.getResource().getId(),
                        usage -> usage.getAmount() * outputAmount * 2)));
        RelocationStockSeeder.receiveFromSupplier(apiExecutor, UserRole.ADMIN, storageId, inputs);
    }
}
