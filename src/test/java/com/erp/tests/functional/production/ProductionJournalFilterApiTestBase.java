package com.erp.tests.functional.production;

import com.erp.fixtures.ProductionFixture;
import com.erp.models.common.ProductionJournalFilterScenario;
import com.erp.models.response.ManufacturingItemResponse;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.ProductionJournalApiAssertions;
import com.erp.utils.helpers.ProductionJournalFilterCatalog;
import io.qameta.allure.Allure;
import io.qameta.allure.Step;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.SkipException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
abstract class ProductionJournalFilterApiTestBase extends BaseFunctionalTest {

    protected ProductionFixture productionFixture;
    protected ProductionJournalFilterCatalog catalog;
    protected long storageId;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    @Step("Завантажити базовий журнал виробництва для сценаріїв фільтрів")
    public void loadProductionJournalFilterCatalog() {
        productionFixture = new ProductionFixture(testContext, apiExecutor);
        storageId = ConfigProvider.getOwner1StorageId();
        catalog = ProductionJournalFilterCatalog.load(productionFixture, storageId);
        log.info("Production journal filter catalog ready — storageId={}, baselineSize={}, unfilteredTotal={}",
                storageId, catalog.baseline().size(), catalog.unfilteredTotal());
    }

    protected void verifyApiFilter(ProductionJournalFilterScenario scenario) {
        attachScenarioParameters(scenario);

        List<ManufacturingItemResponse> filtered = productionFixture.getJournalPage(
                scenario.query().toBuilder().pageSize(500).build());
        long totalElements = productionFixture.getJournalTotalElements(scenario.query());

        assertThat(totalElements)
                .as("Фільтр «%s» має повертати принаймні один запис", scenario.name())
                .isGreaterThan(0);
        assertThat(filtered)
                .as("Перша сторінка API для фільтра «%s» не повинна бути порожньою", scenario.name())
                .isNotEmpty();

        catalog.ensureProductCategories(productionFixture, filtered);
        ProductionJournalApiAssertions.assertAnchorPresent(filtered, scenario.anchor(), scenario.name());
        ProductionJournalApiAssertions.assertAllMatchQuery(
                filtered, scenario, catalog.productCategoryMap());
        if (catalog.baselineCoversJournal()) {
            ProductionJournalApiAssertions.assertFilteredSubsetOfBaseline(
                    filtered, catalog.baseline(), scenario.name());
        } else {
            Allure.parameter("subsetCheck", "skipped — unfiltered journal exceeds baseline page");
        }

        Allure.parameter("totalElements", totalElements);
        Allure.parameter("firstPageSize", filtered.size());
    }

    protected ProductionJournalFilterScenario requireCategoryScenario(
            java.util.Optional<ProductionJournalFilterScenario> scenario) {
        return scenario.orElseThrow(() ->
                new SkipException("Немає записів виробництва з відомою категорією для тесту фільтра"));
    }

    private void attachScenarioParameters(ProductionJournalFilterScenario scenario) {
        Allure.parameter("filter", scenario.name());
        Allure.parameter("anchorProductionId", scenario.anchor().getId());
        if (scenario.productTerm() != null) {
            Allure.parameter("productTerm", scenario.productTerm());
        }
        if (scenario.startDate() != null) {
            Allure.parameter("startDate", scenario.startDate());
        }
        if (scenario.endDate() != null) {
            Allure.parameter("endDate", scenario.endDate());
        }
        if (scenario.categoryId() != null) {
            Allure.parameter("categoryId", scenario.categoryId());
            Allure.parameter("categoryName", scenario.categoryName());
        }
    }
}
