package com.erp.tests.functional.tech_map;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.ProductionFixture;
import com.erp.fixtures.TechnologicalMapFixture;
import com.erp.models.response.ManufacturingItemResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.HashtagTestData;
import com.erp.utils.helpers.TechnologicalMapTagAssertions;
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

        // Staging bug: updateNotes без # null'ить notes, але НЕ очищає TEXT[] tags —
        // тому GET ?tags=… ще може знаходити карту. Перевіряємо успадкування у виробництво.
        techMapFixture.seedStockForIsolatedTechMap(
                productionFixture, storageId, isolated.getTechMap(), 500.0);
        ManufacturingItemResponse production = productionFixture.createWithUniqueBatch(
                UserRole.OWNER_1, storageId, isolated.getTechMap(), 1.0);

        assertThat(production.getNotes())
                .as("Виробництво після очищення notes техкарти не має успадкувати текст notes")
                .isNull();

        log.info("TC-TM-TAG-001 PASSED — techMapId={}, productionId={}",
                isolated.getTechMap().getId(), production.getId());
    }

    @Test(priority = 20)
    @TestCaseId("TC-TM-TAG-002")
    @Story("Happy path — tag filter, statistics, catalog")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            PATCH tech-map notes з #тегами → фільтр GET /technological-maps?tags=…,
            tag-statistics та каталог technological-map-tags повертають запис.
            """)
    public void tagFilterStatisticsAndCatalog() {
        String tagHeat = HashtagTestData.uniqueTag("нагрів");
        String tagControl = HashtagTestData.uniqueTag("контроль");
        TechnologicalMapResponse techMap = createTaggedTechMap(tagHeat, tagControl);
        Allure.parameter("techMapId", techMap.getId());
        Allure.parameter("tagHeat", tagHeat);
        Allure.parameter("tagControl", tagControl);

        TechnologicalMapTagAssertions.assertFilteredByTag(
                techMapFixture, storageId, tagHeat, techMap.getId());
        TechnologicalMapTagAssertions.assertFilteredByTag(
                techMapFixture, storageId, tagControl, techMap.getId());
        TechnologicalMapTagAssertions.assertTagStatisticsContains(
                techMapFixture, storageId, tagHeat, 1);
        TechnologicalMapTagAssertions.assertTechnologicalMapTagsCatalogContains(
                techMapFixture, storageId, tagHeat);
        TechnologicalMapTagAssertions.assertTechnologicalMapTagsCatalogContains(
                techMapFixture, storageId, tagControl);

        log.info("TC-TM-TAG-002 PASSED — techMapId={}", techMap.getId());
    }

    @Test(priority = 30)
    @TestCaseId("TC-TM-TAG-003")
    @Story("Space inside attempted multi-word tag")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Текст «#два слова в тексті» парситься regex #[\\S]+ — тегом є лише #два.
            Фільтр tags=#два знаходить запис, tags=#два слова — ні.
            """)
    public void spaceInsideTagParsesFirstTokenOnly() {
        String notes = "#два слова в тексті";
        TechnologicalMapFixture.IsolatedTechMapContext isolated =
                techMapFixture.createIsolatedProductionTechMap(UserRole.ADMIN, storageId);
        techMapFixture.updateNotes(UserRole.ADMIN, isolated.getTechMap().getId(), storageId, notes);

        Optional<String> storageNotes = techMapFixture.getStorageNotes(
                UserRole.OWNER_1, isolated.getTechMap().getId(), storageId);
        assertThat(storageNotes).contains(notes);

        TechnologicalMapTagAssertions.assertFilteredByTag(
                techMapFixture, storageId, "#два", isolated.getTechMap().getId());
        TechnologicalMapTagAssertions.assertNotFilteredByTag(
                techMapFixture, storageId, "#два слова", isolated.getTechMap().getId());

        log.info("TC-TM-TAG-003 PASSED — techMapId={}", isolated.getTechMap().getId());
    }

    @Test(priority = 40)
    @TestCaseId("TC-TM-TAG-004")
    @Story("Manual tag isolates maps")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Дві техкарти без notes → PATCH notes лише на першій з #ручний-тег.
            Фільтр знаходить лише першу; каталог містить тег.
            """)
    public void manualTagIsolatesMaps() {
        TechnologicalMapFixture.IsolatedTechMapContext taggedCtx =
                techMapFixture.createIsolatedProductionTechMap(UserRole.ADMIN, storageId);
        TechnologicalMapFixture.IsolatedTechMapContext otherCtx =
                techMapFixture.createIsolatedProductionTechMap(UserRole.ADMIN, storageId);

        String manualTag = HashtagTestData.uniqueTag("ручний");
        techMapFixture.updateNotes(
                UserRole.ADMIN, taggedCtx.getTechMap().getId(), storageId, manualTag + " вручну");

        TechnologicalMapTagAssertions.assertFilteredByTag(
                techMapFixture, storageId, manualTag, taggedCtx.getTechMap().getId());
        TechnologicalMapTagAssertions.assertNotFilteredByTag(
                techMapFixture, storageId, manualTag, otherCtx.getTechMap().getId());
        TechnologicalMapTagAssertions.assertTechnologicalMapTagsCatalogContains(
                techMapFixture, storageId, manualTag);

        log.info("TC-TM-TAG-004 PASSED — taggedId={}, otherId={}",
                taggedCtx.getTechMap().getId(), otherCtx.getTechMap().getId());
    }

    @Test(priority = 50)
    @TestCaseId("TC-TM-TAG-005")
    @Story("Multi-tag filter OR semantics")
    @Severity(SeverityLevel.NORMAL)
    @Description("Техкарта з #a #b знаходиться і по tags=#a, і по tags=#b.")
    public void multiTagFilterUsesOrSemantics() {
        String tagA = HashtagTestData.uniqueTag("a");
        String tagB = HashtagTestData.uniqueTag("b");
        TechnologicalMapResponse techMap = createTaggedTechMap(tagA, tagB);

        TechnologicalMapTagAssertions.assertFilteredByTag(
                techMapFixture, storageId, tagA, techMap.getId());
        TechnologicalMapTagAssertions.assertFilteredByTag(
                techMapFixture, storageId, tagB, techMap.getId());

        log.info("TC-TM-TAG-005 PASSED — techMapId={}", techMap.getId());
    }

    @Test(priority = 60)
    @TestCaseId("TC-TM-TAG-006")
    @Story("Cyrillic tags")
    @Severity(SeverityLevel.NORMAL)
    @Description("Тег #контроль з кирилицею парситься і фільтрується.")
    public void cyrillicTagIsFilterable() {
        String cyrillicTag = "#контроль-" + System.currentTimeMillis();
        TechnologicalMapResponse techMap = createTaggedTechMap(cyrillicTag, null);

        TechnologicalMapTagAssertions.assertFilteredByTag(
                techMapFixture, storageId, cyrillicTag, techMap.getId());
        TechnologicalMapTagAssertions.assertTagStatisticsContains(
                techMapFixture, storageId, cyrillicTag, 1);

        log.info("TC-TM-TAG-006 PASSED — tag={}, techMapId={}", cyrillicTag, techMap.getId());
    }

    private TechnologicalMapResponse createTaggedTechMap(String primaryTag, String secondaryTag) {
        TechnologicalMapFixture.IsolatedTechMapContext isolated =
                techMapFixture.createIsolatedProductionTechMap(UserRole.ADMIN, storageId);
        String notes = HashtagTestData.notesWithTags(primaryTag, secondaryTag);
        return techMapFixture.updateNotes(
                UserRole.ADMIN, isolated.getTechMap().getId(), storageId, notes);
    }
}
