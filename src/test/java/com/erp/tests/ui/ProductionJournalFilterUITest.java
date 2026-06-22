package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.models.common.ProductionJournalFilterScenario;
import com.erp.utils.helpers.ProductionJournalFilterCatalog;
import io.qameta.allure.*;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.Test;

@Slf4j
@Epic("Production")
@Feature("Production Journal Filters UI")
public class ProductionJournalFilterUITest extends ProductionJournalFilterUITestBase {

    @Test(priority = 10)
    @TestCaseId("TC-UI-PROD-FLT-001")
    @Story("Product filter UI vs API")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            OWNER_1 відкриває журнал виробництва (/production), вводить підрядок назви продукту
            у поле «Продукт» і перевіряє, що відфільтрована UI-таблиця збігається
            з GET /productions?product=… для тих самих параметрів.
            Скріншоти: початковий стан, після очищення, після фільтра, після верифікації.
            """)
    public void filterByProductUiMatchesApi() {
        ProductionJournalFilterScenario scenario = catalog.productFilter(productionFixture);
        runFilterUiScenario("TC-UI-PROD-FLT-001", scenario);
        log.info("TC-UI-PROD-FLT-001 PASSED — productTerm={}", scenario.productTerm());
    }

    @Test(priority = 20)
    @TestCaseId("TC-UI-PROD-FLT-002")
    @Story("Start date filter UI vs API")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            OWNER_1 застосовує фільтр «З» (startDate) на UI і перевіряє відповідність таблиці
            GET /productions?startDate=….
            """)
    public void filterByStartDateUiMatchesApi() {
        ProductionJournalFilterScenario scenario = catalog.startDateFilter(productionFixture);
        runFilterUiScenario("TC-UI-PROD-FLT-002", scenario);
        log.info("TC-UI-PROD-FLT-002 PASSED — startDate={}", scenario.startDate());
    }

    @Test(priority = 30)
    @TestCaseId("TC-UI-PROD-FLT-003")
    @Story("End date filter UI vs API")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            OWNER_1 застосовує фільтр «По» (endDate) на UI і перевіряє відповідність таблиці
            GET /productions?endDate=….
            """)
    public void filterByEndDateUiMatchesApi() {
        ProductionJournalFilterScenario scenario = catalog.endDateFilter(productionFixture);
        runFilterUiScenario("TC-UI-PROD-FLT-003", scenario);
        log.info("TC-UI-PROD-FLT-003 PASSED — endDate={}", scenario.endDate());
    }

    @Test(priority = 40)
    @TestCaseId("TC-UI-PROD-FLT-004")
    @Story("Date range filter UI vs API")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            OWNER_1 задає діапазон «З» + «По» на один день і перевіряє UI-таблицю
            проти GET /productions?startDate=&endDate=.
            """)
    public void filterByDateRangeUiMatchesApi() {
        ProductionJournalFilterScenario scenario = catalog.dateRangeFilter(productionFixture);
        runFilterUiScenario("TC-UI-PROD-FLT-004", scenario);
        log.info("TC-UI-PROD-FLT-004 PASSED — date={}", scenario.startDate());
    }

    @Test(priority = 50)
    @TestCaseId("TC-UI-PROD-FLT-005")
    @Story("Category filter UI vs API")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            OWNER_1 обирає категорію у dropdown «Категорія» і перевіряє UI-таблицю
            проти GET /productions?categoryId=….
            """)
    public void filterByCategoryUiMatchesApi() {
        ProductionJournalFilterScenario scenario =
                requireCategoryScenario(catalog.categoryFilter(productionFixture));
        runFilterUiScenario("TC-UI-PROD-FLT-005", scenario);
        log.info("TC-UI-PROD-FLT-005 PASSED — category={}", scenario.categoryName());
    }

    @Test(priority = 60)
    @TestCaseId("TC-UI-PROD-FLT-006")
    @Story("Combined filters UI vs API — product and date range")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Комбінований фільтр: продукт + діапазон дат (логічне AND).
            UI-таблиця порівнюється з GET /productions?product=&startDate=&endDate=.
            """)
    public void filterByProductAndDateRangeUiMatchesApi() {
        ProductionJournalFilterScenario scenario =
                catalog.productAndDateRangeFilter(productionFixture);
        runFilterUiScenario("TC-UI-PROD-FLT-006", scenario);
        log.info("TC-UI-PROD-FLT-006 PASSED — product={}, date={}",
                scenario.productTerm(), scenario.startDate());
    }

    @Test(priority = 70)
    @TestCaseId("TC-UI-PROD-FLT-007")
    @Story("Combined filters UI vs API — product and category")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Комбінований фільтр: продукт + категорія (логічне AND).
            UI-таблиця порівнюється з GET /productions?product=&categoryId=.
            """)
    public void filterByProductAndCategoryUiMatchesApi() {
        ProductionJournalFilterScenario scenario = requireCategoryScenario(
                catalog.productAndCategoryFilter(productionFixture));
        runFilterUiScenario("TC-UI-PROD-FLT-007", scenario);
        log.info("TC-UI-PROD-FLT-007 PASSED — product={}, category={}",
                scenario.productTerm(), scenario.categoryName());
    }

    @Test(priority = 80)
    @TestCaseId("TC-UI-PROD-FLT-008")
    @Story("Combined filters UI vs API — product, date range and category")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Комбінований фільтр: продукт + дати + категорія (логічне AND).
            UI-таблиця порівнюється з GET /productions?product=&startDate=&endDate=&categoryId=.
            """)
    public void filterByProductDateRangeAndCategoryUiMatchesApi() {
        ProductionJournalFilterScenario scenario = requireCategoryScenario(
                catalog.productDateAndCategoryFilter(productionFixture));
        runFilterUiScenario("TC-UI-PROD-FLT-008", scenario);
        log.info("TC-UI-PROD-FLT-008 PASSED — product={}, date={}, category={}",
                scenario.productTerm(), scenario.startDate(), scenario.categoryName());
    }
}
