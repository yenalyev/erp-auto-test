package com.erp.tests.functional.production;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.models.response.ManufacturingItemResponse;
import com.erp.utils.helpers.HashtagTestData;
import com.erp.utils.helpers.ProductionTagAssertions;
import io.qameta.allure.*;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Production")
@Feature("Hashtags / Notes")
public class ProductionHashtagApiTest extends ProductionHashtagApiTestBase {

    @Test(priority = 10)
    @TestCaseId("TC-PRD-TAG-001")
    @Story("Happy path — inherit tags from tech map")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            PATCH tech-map notes з #тегами → POST production → notes успадковуються,
            фільтр GET /productions?tags=… та tag-statistics повертають запис.
            """)
    public void inheritTagsFromTechMapOnCreate() {
        String tagHeat = HashtagTestData.uniqueTag("нагрів");
        String tagControl = HashtagTestData.uniqueTag("контроль");
        TaggedTechMapContext context = prepareIsolatedTechMapWithNotes(tagHeat, tagControl);

        ManufacturingItemResponse production = createProductionFrom(context);
        attachTagContext(tagHeat, tagControl, production.getId());

        assertThat(production.getNotes())
                .as("Примітки виробництва мають збігатися з техкартою")
                .isEqualTo(context.getNotes());

        ProductionTagAssertions.assertFilteredByTag(productionFixture, storageId, tagHeat, production.getId());
        ProductionTagAssertions.assertFilteredByTag(productionFixture, storageId, tagControl, production.getId());
        ProductionTagAssertions.assertTagStatisticsContains(productionFixture, storageId, tagHeat, 1);

        log.info("TC-PRD-TAG-001 PASSED — productionId={}, notes={}", production.getId(), production.getNotes());
    }

    @Test(priority = 20)
    @TestCaseId("TC-PRD-TAG-002")
    @Story("Production notes without hash — text kept, tags cleared")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            PATCH production notes без # — текст зберігається, але запис більше не фільтрується
            за попереднім тегом з техкарти.
            """)
    public void productionNotesWithoutHashClearsTags() {
        String inheritedTag = HashtagTestData.uniqueTag("успадкований");
        TaggedTechMapContext context = prepareIsolatedTechMapWithNotes(inheritedTag, null);
        ManufacturingItemResponse production = createProductionFrom(context);
        attachTagContext(inheritedTag, null, production.getId());

        String plainNotes = "Звичайна примітка без тегів " + System.currentTimeMillis();
        ManufacturingItemResponse updated = productionFixture.updateNotes(
                UserRole.OWNER_1, production.getId(), storageId, plainNotes);

        assertThat(updated.getNotes())
                .as("Текст примітки без # має зберегтися")
                .isEqualTo(plainNotes);

        ProductionTagAssertions.assertNotFilteredByTag(
                productionFixture, storageId, inheritedTag, production.getId());

        log.info("TC-PRD-TAG-002 PASSED — productionId={}", production.getId());
    }

    @Test(priority = 30)
    @TestCaseId("TC-PRD-TAG-003")
    @Story("Space inside attempted multi-word tag")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Текст «#два слова в тексті» парситься regex #[\\S]+ — тегом є лише #два.
            Фільтр tags=#два знаходить запис, tags=#два слова — ні.
            """)
    public void spaceInsideTagParsesFirstTokenOnly() {
        String notes = "#два слова в тексті";
        TaggedTechMapContext context = prepareIsolatedTechMapWithoutNotes();
        techMapFixture.updateNotes(UserRole.ADMIN, context.getTechMap().getId(), storageId, notes);

        ManufacturingItemResponse production = createProductionFrom(context);
        attachTagContext("#два", null, production.getId());

        assertThat(production.getNotes()).isEqualTo(notes);

        ProductionTagAssertions.assertFilteredByTag(productionFixture, storageId, "#два", production.getId());
        ProductionTagAssertions.assertNotFilteredByTag(
                productionFixture, storageId, "#два слова", production.getId());

        log.info("TC-PRD-TAG-003 PASSED — productionId={}", production.getId());
    }

    @Test(priority = 40)
    @TestCaseId("TC-PRD-TAG-004")
    @Story("Manual production tag — not inherited from tech map")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Техкарта без notes → production без тегів → PATCH production notes з #ручний-тег.
            Лише перший запис фільтрується; каталог production-process-tags містить тег.
            """)
    public void manualProductionTagNotInheritedFromTechMap() {
        TaggedTechMapContext context = prepareIsolatedTechMapWithoutNotes();
        ManufacturingItemResponse tagged = createProductionFrom(context);
        ManufacturingItemResponse other = createProductionFrom(context);

        String manualTag = HashtagTestData.uniqueTag("ручний");
        String manualNotes = manualTag + " додано вручну";
        productionFixture.updateNotes(UserRole.OWNER_1, tagged.getId(), storageId, manualNotes);
        attachTagContext(manualTag, null, tagged.getId());

        ProductionTagAssertions.assertFilteredByTag(productionFixture, storageId, manualTag, tagged.getId());
        ProductionTagAssertions.assertNotFilteredByTag(productionFixture, storageId, manualTag, other.getId());
        ProductionTagAssertions.assertProductionProcessTagsCatalogContains(
                productionFixture, storageId, manualTag);

        log.info("TC-PRD-TAG-004 PASSED — taggedId={}, otherId={}", tagged.getId(), other.getId());
    }

    @Test(priority = 50)
    @TestCaseId("TC-PRD-TAG-005")
    @Story("Tech map tag change does not affect existing productions")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Техкарта #старий → create A → змінити техкарту на #новий → create B.
            A зберігає #старий, B отримує #новий; фільтри не перетинаються.
            """)
    public void techMapTagChangeDoesNotMutateExistingProductions() {
        String oldTag = HashtagTestData.uniqueTag("старий");
        String newTag = HashtagTestData.uniqueTag("новий");

        TaggedTechMapContext initial = prepareIsolatedTechMapWithNotes(oldTag, null);
        ManufacturingItemResponse productionA = createProductionFrom(initial);

        techMapFixture.updateNotes(
                UserRole.ADMIN, initial.getTechMap().getId(), storageId, newTag + " оновлено");

        ManufacturingItemResponse productionB = createProductionFrom(
                TaggedTechMapContext.builder()
                        .techMap(initial.getTechMap())
                        .notes(newTag + " оновлено")
                        .primaryTag(newTag)
                        .build());

        attachTagContext(oldTag, newTag, productionA.getId());
        Allure.parameter("productionBId", productionB.getId());

        assertThat(productionA.getNotes()).contains(oldTag);
        assertThat(productionB.getNotes()).contains(newTag);

        ProductionTagAssertions.assertFilteredByTag(productionFixture, storageId, oldTag, productionA.getId());
        ProductionTagAssertions.assertNotFilteredByTag(productionFixture, storageId, oldTag, productionB.getId());
        ProductionTagAssertions.assertFilteredByTag(productionFixture, storageId, newTag, productionB.getId());
        ProductionTagAssertions.assertNotFilteredByTag(productionFixture, storageId, newTag, productionA.getId());

        log.info("TC-PRD-TAG-005 PASSED — A={}, B={}", productionA.getId(), productionB.getId());
    }

    @Test(priority = 60)
    @TestCaseId("TC-PRD-TAG-006")
    @Story("Multi-tag filter OR semantics")
    @Severity(SeverityLevel.NORMAL)
    @Description("Запис з #a #b знаходиться і по tags=#a, і по tags=#b.")
    public void multiTagFilterUsesOrSemantics() {
        String tagA = HashtagTestData.uniqueTag("a");
        String tagB = HashtagTestData.uniqueTag("b");
        TaggedTechMapContext context = prepareIsolatedTechMapWithNotes(tagA, tagB);
        ManufacturingItemResponse production = createProductionFrom(context);
        attachTagContext(tagA, tagB, production.getId());

        ProductionTagAssertions.assertFilteredByTag(productionFixture, storageId, tagA, production.getId());
        ProductionTagAssertions.assertFilteredByTag(productionFixture, storageId, tagB, production.getId());

        log.info("TC-PRD-TAG-006 PASSED — productionId={}", production.getId());
    }

    @Test(priority = 70)
    @TestCaseId("TC-PRD-TAG-007")
    @Story("Cyrillic tags")
    @Severity(SeverityLevel.NORMAL)
    @Description("Тег #контроль з кирилицею парситься і фільтрується.")
    public void cyrillicTagIsFilterable() {
        String cyrillicTag = "#контроль-" + System.currentTimeMillis();
        TaggedTechMapContext context = prepareIsolatedTechMapWithNotes(cyrillicTag, null);
        ManufacturingItemResponse production = createProductionFrom(context);
        attachTagContext(cyrillicTag, null, production.getId());

        ProductionTagAssertions.assertFilteredByTag(productionFixture, storageId, cyrillicTag, production.getId());

        log.info("TC-PRD-TAG-007 PASSED — tag={}, productionId={}", cyrillicTag, production.getId());
    }
}
