package com.erp.tests.functional.production;

import com.erp.data.factories.production.ProductionDataFactory;
import com.erp.enums.UserRole;
import com.erp.fixtures.ProductionFixture;
import com.erp.fixtures.TechnologicalMapFixture;
import com.erp.models.response.ManufacturingItemResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.HashtagTestData;
import io.qameta.allure.Allure;
import io.qameta.allure.Step;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;

@Slf4j
abstract class ProductionHashtagApiTestBase extends BaseFunctionalTest {

    protected static final double MIN_INPUT_STOCK = 500.0;
    protected static final double PRODUCTION_AMOUNT = 1.0;

    protected ProductionFixture productionFixture;
    protected TechnologicalMapFixture techMapFixture;
    protected long storageId;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    @Step("Підготовка середовища для hashtag API тестів")
    public void setupHashtagApiTests() {
        productionFixture = new ProductionFixture(testContext, apiExecutor);
        techMapFixture = productionFixture.getTechMapFixture();
        techMapFixture.prepareContext();
        storageId = ConfigProvider.getOwner1StorageId();
        log.info("Hashtag API tests ready — storageId={}", storageId);
    }

    @Value
    @Builder
    protected static class TaggedTechMapContext {
        TechnologicalMapResponse techMap;
        String notes;
        String primaryTag;
        String secondaryTag;
    }

    protected TaggedTechMapContext prepareIsolatedTechMapWithNotes(String primaryTag, String secondaryTag) {
        TechnologicalMapFixture.IsolatedTechMapContext isolated =
                techMapFixture.createIsolatedProductionTechMap(UserRole.ADMIN, storageId);
        techMapFixture.seedStockForIsolatedTechMap(
                productionFixture, storageId, isolated.getTechMap(), MIN_INPUT_STOCK);

        String notes = HashtagTestData.notesWithTags(primaryTag, secondaryTag);
        techMapFixture.updateNotes(UserRole.ADMIN, isolated.getTechMap().getId(), storageId, notes);

        return TaggedTechMapContext.builder()
                .techMap(isolated.getTechMap())
                .notes(notes)
                .primaryTag(primaryTag)
                .secondaryTag(secondaryTag)
                .build();
    }

    protected TaggedTechMapContext prepareIsolatedTechMapWithoutNotes() {
        TechnologicalMapFixture.IsolatedTechMapContext isolated =
                techMapFixture.createIsolatedProductionTechMap(UserRole.ADMIN, storageId);
        techMapFixture.seedStockForIsolatedTechMap(
                productionFixture, storageId, isolated.getTechMap(), MIN_INPUT_STOCK);
        return TaggedTechMapContext.builder()
                .techMap(isolated.getTechMap())
                .build();
    }

    protected ManufacturingItemResponse createProductionFrom(TaggedTechMapContext context) {
        return productionFixture.createWithUniqueBatch(
                UserRole.OWNER_1, storageId, context.getTechMap(), PRODUCTION_AMOUNT);
    }

    protected void attachTagContext(String primaryTag, String secondaryTag, Long productionId) {
        Allure.parameter("storageId", storageId);
        if (primaryTag != null) {
            Allure.parameter("primaryTag", primaryTag);
        }
        if (secondaryTag != null) {
            Allure.parameter("secondaryTag", secondaryTag);
        }
        if (productionId != null) {
            Allure.parameter("productionId", productionId);
        }
    }

    protected String uniqueBatch() {
        return ProductionDataFactory.uniqueBatchNumber();
    }
}
