package com.erp.tests.functional.tech_map;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.ProductionFixture;
import com.erp.fixtures.TechnologicalMapFixture;
import com.erp.models.response.ManufacturingItemResponse;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.HashtagTestData;
import io.qameta.allure.*;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Technological Maps")
@Feature("Hashtags / Notes")
public class TechnologicalMapHashtagApiTest extends BaseFunctionalTest {

    private static final double MIN_INPUT_STOCK = 500.0;

    private ProductionFixture productionFixture;
    private TechnologicalMapFixture techMapFixture;
    private long storageId;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void setupTechMapHashtagApiTests() {
        productionFixture = new ProductionFixture(testContext, apiExecutor);
        techMapFixture = productionFixture.getTechMapFixture();
        techMapFixture.prepareContext();
        storageId = ConfigProvider.getOwner1StorageId();
    }

    @Test(priority = 10)
    @TestCaseId("TC-TM-TAG-001")
    @Story("Tech map notes without hash clears notes")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            PATCH tech-map notes з #тегом → PATCH без # очищує notes (null).
            Наступне виробництво не успадковує тег.
            """)
    public void techMapNotesWithoutHashClearsNotes() {
        String tag = HashtagTestData.uniqueTag("тег");
        TechnologicalMapFixture.IsolatedTechMapContext isolated =
                techMapFixture.createIsolatedProductionTechMap(UserRole.ADMIN, storageId);
        techMapFixture.seedStockForIsolatedTechMap(
                productionFixture, storageId, isolated.getTechMap(), MIN_INPUT_STOCK);

        techMapFixture.updateNotes(
                UserRole.ADMIN, isolated.getTechMap().getId(), storageId, tag + " початково");

        techMapFixture.updateNotes(
                UserRole.ADMIN,
                isolated.getTechMap().getId(),
                storageId,
                "текст без решітки " + System.currentTimeMillis());

        Optional<String> storageNotes = techMapFixture.getStorageNotes(
                UserRole.OWNER_1, isolated.getTechMap().getId(), storageId);
        assertThat(storageNotes)
                .as("Примітки техкарти без # мають бути очищені (null)")
                .isEmpty();

        ManufacturingItemResponse production = productionFixture.createWithUniqueBatch(
                UserRole.OWNER_1, storageId, isolated.getTechMap(), 1.0);

        assertThat(production.getNotes())
                .as("Виробництво після очищення notes техкарти не має успадкувати тег")
                .isNull();

        log.info("TC-TM-TAG-001 PASSED — techMapId={}, productionId={}",
                isolated.getTechMap().getId(), production.getId());
    }
}
