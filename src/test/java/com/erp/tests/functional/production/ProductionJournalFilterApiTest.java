package com.erp.tests.functional.production;

import com.erp.annotations.TestCaseId;
import com.erp.models.common.ProductionJournalFilterScenario;
import com.erp.utils.helpers.ProductionJournalFilterCatalog;
import io.qameta.allure.*;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.Test;

@Slf4j
@Epic("Production")
@Feature("Production Journal Filters API")
public class ProductionJournalFilterApiTest extends ProductionJournalFilterApiTestBase {

    @Test(priority = 10)
    @TestCaseId("TC-PRD-FLT-001")
    @Story("Product filter")
    @Severity(SeverityLevel.CRITICAL)
    @Description("GET /productions?product=… — підрядок назви продукту з реального запису журналу (логічне AND з іншими параметрами за замовчуванням).")
    public void filterByProduct() {
        ProductionJournalFilterScenario scenario = catalog.productFilter(productionFixture);
        verifyApiFilter(scenario);
        log.info("TC-PRD-FLT-001 PASSED — productTerm={}", scenario.productTerm());
    }

    @Test(priority = 20)
    @TestCaseId("TC-PRD-FLT-002")
    @Story("Start date filter")
    @Severity(SeverityLevel.CRITICAL)
    @Description("GET /productions?startDate=… — дата «З» з реального запису журналу.")
    public void filterByStartDate() {
        ProductionJournalFilterScenario scenario = catalog.startDateFilter(productionFixture);
        verifyApiFilter(scenario);
        log.info("TC-PRD-FLT-002 PASSED — startDate={}", scenario.startDate());
    }

    @Test(priority = 30)
    @TestCaseId("TC-PRD-FLT-003")
    @Story("End date filter")
    @Severity(SeverityLevel.CRITICAL)
    @Description("GET /productions?endDate=… — дата «По» з реального запису журналу.")
    public void filterByEndDate() {
        ProductionJournalFilterScenario scenario = catalog.endDateFilter(productionFixture);
        verifyApiFilter(scenario);
        log.info("TC-PRD-FLT-003 PASSED — endDate={}", scenario.endDate());
    }

    @Test(priority = 40)
    @TestCaseId("TC-PRD-FLT-004")
    @Story("Date range filter")
    @Severity(SeverityLevel.CRITICAL)
    @Description("GET /productions?startDate=&endDate= — діапазон одного дня з реального запису.")
    public void filterByDateRange() {
        ProductionJournalFilterScenario scenario = catalog.dateRangeFilter(productionFixture);
        verifyApiFilter(scenario);
        log.info("TC-PRD-FLT-004 PASSED — date={}", scenario.startDate());
    }

    @Test(priority = 50)
    @TestCaseId("TC-PRD-FLT-005")
    @Story("Category filter")
    @Severity(SeverityLevel.CRITICAL)
    @Description("GET /productions?categoryId=… — категорія продукту з реального запису журналу.")
    public void filterByCategory() {
        ProductionJournalFilterScenario scenario =
                requireCategoryScenario(catalog.categoryFilter(productionFixture));
        verifyApiFilter(scenario);
        log.info("TC-PRD-FLT-005 PASSED — categoryId={}, name={}",
                scenario.categoryId(), scenario.categoryName());
    }

    @Test(priority = 60)
    @TestCaseId("TC-PRD-FLT-006")
    @Story("Combined filters — product and date range")
    @Severity(SeverityLevel.CRITICAL)
    @Description("GET /productions з product + startDate + endDate (логічне AND) для одного опорного запису.")
    public void filterByProductAndDateRange() {
        ProductionJournalFilterScenario scenario =
                catalog.productAndDateRangeFilter(productionFixture);
        verifyApiFilter(scenario);
        log.info("TC-PRD-FLT-006 PASSED — product={}, date={}",
                scenario.productTerm(), scenario.startDate());
    }

    @Test(priority = 70)
    @TestCaseId("TC-PRD-FLT-007")
    @Story("Combined filters — product and category")
    @Severity(SeverityLevel.CRITICAL)
    @Description("GET /productions з product + categoryId (логічне AND).")
    public void filterByProductAndCategory() {
        ProductionJournalFilterScenario scenario = requireCategoryScenario(
                catalog.productAndCategoryFilter(productionFixture));
        verifyApiFilter(scenario);
        log.info("TC-PRD-FLT-007 PASSED — product={}, category={}",
                scenario.productTerm(), scenario.categoryName());
    }

    @Test(priority = 80)
    @TestCaseId("TC-PRD-FLT-008")
    @Story("Combined filters — product, date range and category")
    @Severity(SeverityLevel.CRITICAL)
    @Description("GET /productions з product + startDate + endDate + categoryId (логічне AND).")
    public void filterByProductDateRangeAndCategory() {
        ProductionJournalFilterScenario scenario = requireCategoryScenario(
                catalog.productDateAndCategoryFilter(productionFixture));
        verifyApiFilter(scenario);
        log.info("TC-PRD-FLT-008 PASSED — product={}, date={}, category={}",
                scenario.productTerm(), scenario.startDate(), scenario.categoryName());
    }
}
