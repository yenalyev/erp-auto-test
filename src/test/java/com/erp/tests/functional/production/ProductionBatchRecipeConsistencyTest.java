package com.erp.tests.functional.production;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.production.ProductionDataFactory;
import com.erp.data.factories.tech_map.TechnologicalMapDataFactory;
import com.erp.enums.StorageTechnologicalMapMode;
import com.erp.enums.UserRole;
import com.erp.fixtures.ProductionFixture;
import com.erp.fixtures.ShiftFixture;
import com.erp.fixtures.TechnologicalMapFixture;
import com.erp.models.request.AlternativeInputRequest;
import com.erp.models.request.ManufacturingListRequest;
import com.erp.models.request.ShiftSnapshotRequest;
import com.erp.models.response.BatchRecipeResponse;
import com.erp.models.response.ManufacturingItemResponse;
import com.erp.models.response.ShiftResponse;
import com.erp.models.response.TechnologicalMapAlternativeGroupResourceResponse;
import com.erp.models.response.TechnologicalMapAlternativeGroupResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.ProductionStockAssertions;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Production")
@Feature("One recipe per produced batch")
public class ProductionBatchRecipeConsistencyTest extends BaseFunctionalTest {

    private static final double MIN_STOCK = 300.0;
    private static final double AMOUNT = 2.0;

    private final List<Long> createdProductionIds = new ArrayList<>();
    private final List<TechnologicalMapResponse> createdMaps = new ArrayList<>();

    private ProductionFixture productionFixture;
    private TechnologicalMapFixture techMapFixture;
    private ShiftFixture shiftFixture;
    private Long storageId;
    private TechnologicalMapResponse oneGroupMap;
    private TechnologicalMapResponse alternateMap;
    private TechnologicalMapResponse twoGroupMap;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void setupBatchRecipeTests() {
        productionFixture = new ProductionFixture(testContext, apiExecutor);
        productionFixture.prepareContext();
        techMapFixture = productionFixture.getTechMapFixture();
        shiftFixture = new ShiftFixture(testContext, apiExecutor);
        storageId = ConfigProvider.getOwner1StorageId();

        techMapFixture.setMode(storageId, StorageTechnologicalMapMode.EDIT_ALLOWED);
        oneGroupMap = registerMap(techMapFixture.createTechMapWithAlternativeGroup(UserRole.ADMIN, storageId));
        alternateMap = registerMap(techMapFixture.createAlternateActiveTechMap(UserRole.ADMIN, oneGroupMap));
        twoGroupMap = registerMap(techMapFixture.createTechMapWithRequest(
                UserRole.ADMIN,
                TechnologicalMapDataFactory.createProductionMapWithTwoGroups(
                        techMapFixture.createTwoGroupAltResources(), storageId)));
    }

    @BeforeMethod(alwaysRun = true)
    public void ensureRecipeInputStock() {
        productionFixture.ensureStockForTechMapInputs(storageId, oneGroupMap, MIN_STOCK);
        productionFixture.ensureStockForTechMapInputs(storageId, alternateMap, MIN_STOCK);
        productionFixture.ensureStockForTechMapInputs(storageId, twoGroupMap, MIN_STOCK);
    }

    @AfterMethod(alwaysRun = true)
    public void cleanupProductions() {
        List<Long> reverse = new ArrayList<>(createdProductionIds);
        Collections.reverse(reverse);
        for (Long id : reverse) {
            try {
                productionFixture.deleteAs(UserRole.ADMIN, id, storageId);
            } catch (RuntimeException cleanupError) {
                log.warn("Failed to delete production {} during cleanup", id, cleanupError);
            }
        }
        createdProductionIds.clear();
    }

    @AfterClass(alwaysRun = true)
    public void cleanupMaps() {
        List<TechnologicalMapResponse> reverse = new ArrayList<>(createdMaps);
        Collections.reverse(reverse);
        for (TechnologicalMapResponse map : reverse) {
            try {
                techMapFixture.deactivateTechMap(UserRole.ADMIN, map.getId(), storageId);
            } catch (RuntimeException cleanupError) {
                log.warn("Failed to deactivate tech map {} during cleanup", map.getId(), cleanupError);
            }
        }
        try {
            techMapFixture.setMode(storageId, StorageTechnologicalMapMode.READ_ONLY);
        } catch (RuntimeException cleanupError) {
            log.warn("Failed to restore READ_ONLY tech-map mode", cleanupError);
        }
    }

    @Test(priority = 10)
    @TestCaseId("TC-PROD-BATCH-001")
    @Story("Empty and homogeneous batch")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Порожня партія приймає перший рецепт; другий запис з тією самою ТК та альтернативами успішний.")
    public void sameRecipeCanBeSavedTwiceAndReturnedAsBatchRecipe() {
        String batch = ProductionDataFactory.uniqueBatchNumber();
        Long productId = outputId(oneGroupMap);

        BatchRecipeResponse empty = productionFixture.getBatchRecipe(
                UserRole.OWNER_1, storageId, productId, batch);
        assertThat(empty.getTechMapId()).isNull();
        assertThat(empty.getSelections()).isEmpty();

        ManufacturingItemResponse first = track(productionFixture.createAs(
                UserRole.OWNER_1, storageId, oneGroupMap, AMOUNT, batch));
        ManufacturingItemResponse second = track(productionFixture.createAs(
                UserRole.OWNER_1, storageId, oneGroupMap, AMOUNT, batch));

        assertThat(first.getBatchNumber()).isEqualTo(batch);
        assertThat(second.getBatchNumber()).isEqualTo(batch);

        BatchRecipeResponse recipe = productionFixture.getBatchRecipe(
                UserRole.OWNER_1, storageId, productId, batch);
        assertThat(recipe.getTechMapId()).isEqualTo(oneGroupMap.getId());
        assertThat(recipe.getTechMapVersion()).isEqualTo(oneGroupMap.getVersion());
        assertThat(recipe.getSelections()).hasSize(oneGroupMap.getGroups().size());
    }

    @Test(priority = 20)
    @TestCaseId("TC-PROD-BATCH-002")
    @Story("Different technological map")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Другий запис тієї самої product+batch з іншою ТК → 400; склад і партія не змінюються.")
    public void differentTechMapIsRejectedWithoutStockSideEffects() {
        String batch = ProductionDataFactory.uniqueBatchNumber();
        track(productionFixture.createAs(UserRole.OWNER_1, storageId, oneGroupMap, AMOUNT, batch));
        Set<Long> resources = resourceIds(oneGroupMap, alternateMap);
        ProductionStockAssertions.StockSnapshot before = snapshot(resources, "before rejected map mismatch");

        ManufacturingListRequest request = ProductionDataFactory.buildCreateRequest(
                alternateMap, AMOUNT, LocalDate.now(), batch);
        Response response = productionFixture.tryCreateAs(UserRole.OWNER_1, storageId, request);

        String message = assertValidation(response, "batchNumber");
        assertThat(message)
                .contains("Одна партія виробів повинна робитися за одним рецептом")
                .contains("за різними техкартами")
                .contains(oneGroupMap.getName())
                .contains(alternateMap.getName());
        assertStockUnchanged(before, snapshot(resources, "after rejected map mismatch"));
    }

    @Test(priority = 21)
    @TestCaseId("TC-PROD-BATCH-003")
    @Story("Different alternative in one group")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Та сама ТК, але інший resource в одній alternative group → 400 з назвою групи та ресурсів.")
    public void differentAlternativeInOneGroupIsRejected() {
        String batch = ProductionDataFactory.uniqueBatchNumber();
        TechnologicalMapAlternativeGroupResponse group = oneGroupMap.getGroups().getFirst();
        TechnologicalMapAlternativeGroupResourceResponse reference = defaultAlternative(group);
        TechnologicalMapAlternativeGroupResourceResponse candidate = nonDefaultAlternative(group);

        track(productionFixture.createAsWithAlternatives(
                UserRole.OWNER_1, storageId, oneGroupMap, AMOUNT, batch,
                alternativeInputs(oneGroupMap, Map.of(group.getId(), reference.getResource().getId()))));

        ManufacturingListRequest request = ProductionDataFactory.buildCreateRequest(
                oneGroupMap, AMOUNT, LocalDate.now(), batch,
                alternativeInputs(oneGroupMap, Map.of(group.getId(), candidate.getResource().getId())));
        Response response = productionFixture.tryCreateAs(UserRole.OWNER_1, storageId, request);

        String message = assertValidation(response, "batchNumber");
        assertThat(message)
                .contains(oneGroupMap.getName())
                .contains(group.getName())
                .contains(reference.getResource().getName())
                .contains(candidate.getResource().getName());
    }

    @Test(priority = 22)
    @TestCaseId("TC-PROD-BATCH-004")
    @Story("Different alternatives in two groups")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Розбіжність у двох alternative groups → обидві групи в одному повідомленні, розділені ';'.")
    public void differentAlternativesInTwoGroupsAreAllReported() {
        String batch = ProductionDataFactory.uniqueBatchNumber();
        Map<Long, Long> defaults = new LinkedHashMap<>();
        Map<Long, Long> alternatives = new LinkedHashMap<>();
        for (TechnologicalMapAlternativeGroupResponse group : twoGroupMap.getGroups()) {
            defaults.put(group.getId(), defaultAlternative(group).getResource().getId());
            alternatives.put(group.getId(), nonDefaultAlternative(group).getResource().getId());
        }

        track(productionFixture.createAsWithAlternatives(
                UserRole.OWNER_1, storageId, twoGroupMap, AMOUNT, batch,
                alternativeInputs(twoGroupMap, defaults)));
        ManufacturingListRequest request = ProductionDataFactory.buildCreateRequest(
                twoGroupMap, AMOUNT, LocalDate.now(), batch,
                alternativeInputs(twoGroupMap, alternatives));

        Response response = productionFixture.tryCreateAs(UserRole.OWNER_1, storageId, request);
        String message = assertValidation(response, "batchNumber");
        assertThat(message).contains(";");
        for (TechnologicalMapAlternativeGroupResponse group : twoGroupMap.getGroups()) {
            assertThat(message).contains(group.getName());
        }
    }

    @Test(priority = 23)
    @TestCaseId("TC-PROD-BATCH-005")
    @Story("Different version of one technological map")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Друга версія ТК з тією самою назвою → 400; обидві версії явно названі в повідомленні.")
    public void differentVersionOfSameMapNamesBothVersions() {
        TechnologicalMapResponse source = registerMap(
                techMapFixture.createTechMapWithAlternativeGroup(UserRole.ADMIN, storageId));
        productionFixture.ensureStockForTechMapInputs(storageId, source, MIN_STOCK);
        String batch = ProductionDataFactory.uniqueBatchNumber();
        track(productionFixture.createAs(UserRole.OWNER_1, storageId, source, AMOUNT, batch));

        double changedInputAmount = source.getInput().getFirst().getAmount() + 0.5;
        Response update = techMapFixture.updateTechMap(
                UserRole.ADMIN,
                source.getId(),
                TechnologicalMapDataFactory.withFirstInputAmount(source, changedInputAmount));
        assertThat(update.statusCode()).isEqualTo(200);
        TechnologicalMapResponse nextVersion = registerMap(update.as(TechnologicalMapResponse.class));
        productionFixture.ensureStockForTechMapInputs(storageId, nextVersion, MIN_STOCK);

        Response response = productionFixture.tryCreateAs(
                UserRole.OWNER_1,
                storageId,
                ProductionDataFactory.buildCreateRequest(nextVersion, AMOUNT, LocalDate.now(), batch));

        String message = assertValidation(response, "batchNumber");
        assertThat(message)
                .contains(source.getName() + " (версія " + source.getVersion() + ")")
                .contains(nextVersion.getName() + " (версія " + nextVersion.getVersion() + ")");
    }

    @Test(priority = 30)
    @TestCaseId("TC-PROD-BATCH-006")
    @Story("Stored technological map is immutable")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Навіть єдиний запис партії не дозволяє змінити technologicalMapId через PUT.")
    public void singleStoredRecordTechMapIsImmutable() {
        String batch = ProductionDataFactory.uniqueBatchNumber();
        ManufacturingItemResponse stored = track(productionFixture.createAs(
                UserRole.OWNER_1, storageId, oneGroupMap, AMOUNT, batch));

        Response response = productionFixture.updateRaw(
                UserRole.OWNER_1, stored.getId(), storageId, alternateMap, AMOUNT, batch);

        assertThat(assertValidation(response, "technologicalMapId"))
                .isEqualTo("Технологічну карту збереженого запису змінити не можна. "
                        + "Створіть новий запис за потрібною картою");
        ManufacturingItemResponse unchanged = productionFixture.getById(
                UserRole.OWNER_1, stored.getId(), storageId);
        assertThat(unchanged.getTechnologicalMap().getId()).isEqualTo(oneGroupMap.getId());
    }

    @Test(priority = 31)
    @TestCaseId("TC-PROD-BATCH-007")
    @Story("Stored technological map is immutable in multi-record batch")
    @Severity(SeverityLevel.CRITICAL)
    public void techMapChangeInMultiRecordBatchReturnsImmutableError() {
        String batch = ProductionDataFactory.uniqueBatchNumber();
        ManufacturingItemResponse first = track(productionFixture.createAs(
                UserRole.OWNER_1, storageId, oneGroupMap, AMOUNT, batch));
        track(productionFixture.createAs(UserRole.OWNER_1, storageId, oneGroupMap, AMOUNT, batch));

        Response response = productionFixture.updateRaw(
                UserRole.OWNER_1, first.getId(), storageId, alternateMap, AMOUNT, batch);

        assertThat(assertValidation(response, "technologicalMapId"))
                .contains("Технологічну карту збереженого запису змінити не можна");
    }

    @Test(priority = 32)
    @TestCaseId("TC-PROD-BATCH-008")
    @Story("Stored batch number is immutable")
    @Severity(SeverityLevel.CRITICAL)
    @Description("PUT не може перенести запис ані в непорожню, ані в порожню партію.")
    public void storedBatchNumberCannotBeChangedToOccupiedOrEmptyBatch() {
        String originalBatch = ProductionDataFactory.uniqueBatchNumber();
        String occupiedBatch = ProductionDataFactory.uniqueBatchNumber();
        String emptyBatch = ProductionDataFactory.uniqueBatchNumber();
        ManufacturingItemResponse stored = track(productionFixture.createAs(
                UserRole.OWNER_1, storageId, oneGroupMap, AMOUNT, originalBatch));
        track(productionFixture.createAs(UserRole.OWNER_1, storageId, oneGroupMap, AMOUNT, occupiedBatch));

        for (String target : List.of(occupiedBatch, emptyBatch)) {
            Response response = productionFixture.updateRaw(
                    UserRole.OWNER_1, stored.getId(), storageId, oneGroupMap, AMOUNT, target);
            assertThat(assertValidation(response, "batchNumber"))
                    .isEqualTo("Номер партії збереженого запису змінити не можна - запис лишається в партії "
                            + originalBatch + ". Створіть новий запис з потрібним номером партії");
        }

        assertThat(productionFixture.getById(UserRole.OWNER_1, stored.getId(), storageId).getBatchNumber())
                .isEqualTo(originalBatch);
    }

    @Test(priority = 33)
    @TestCaseId("TC-PROD-BATCH-009")
    @Story("Non-recipe fields remain editable")
    @Severity(SeverityLevel.CRITICAL)
    @Description("PUT змінює amount, date і shift snapshot без зміни ТК/партії; запис не конфліктує сам із собою.")
    public void amountDateAndShiftCanBeEditedWithoutChangingRecipe() {
        String batch = ProductionDataFactory.uniqueBatchNumber();
        ManufacturingItemResponse stored = track(productionFixture.createAs(
                UserRole.OWNER_1, storageId, oneGroupMap, AMOUNT, batch));
        ShiftResponse shift = shiftFixture.getAll(UserRole.OWNER_1, storageId).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No production shift available at storage " + storageId));
        LocalDate changedDate = LocalDate.now().minusDays(1);
        double changedAmount = AMOUNT + 1.0;
        ManufacturingListRequest request = ProductionDataFactory.buildCreateRequest(
                oneGroupMap, changedAmount, changedDate, batch);
        request.setShift(ShiftSnapshotRequest.builder()
                .shiftId(shift.getId())
                .name(shift.getName())
                .workerQty(shift.getWorkerQty())
                .timeStart(shift.getTimeStart())
                .timeEnd(shift.getTimeEnd())
                .build());

        Response response = productionFixture.updateRaw(
                UserRole.OWNER_1, stored.getId(), storageId, request);

        assertThat(response.statusCode()).isEqualTo(200);
        ManufacturingItemResponse updated = response.as(ManufacturingItemResponse.class);
        assertThat(updated.getAmount()).isEqualTo(changedAmount);
        assertThat(updated.getDate()).isEqualTo(changedDate);
        assertThat(updated.getBatchNumber()).isEqualTo(batch);
        assertThat(updated.getTechnologicalMap().getId()).isEqualTo(oneGroupMap.getId());
        assertThat(updated.getShift()).isNotNull();
        assertThat(updated.getShift().getShiftId()).isEqualTo(shift.getId());
    }

    @Test(priority = 40)
    @TestCaseId("TC-PROD-BATCH-010")
    @Story("Bulk entry point and atomic rejection")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Один POST містить два items однієї партії з різними ТК → 400; жоден item і складський рух не збережено.")
    public void bulkRequestWithDifferentRecipesIsRejectedAtomically() {
        String batch = ProductionDataFactory.uniqueBatchNumber();
        ManufacturingListRequest first = ProductionDataFactory.buildCreateRequest(
                oneGroupMap, AMOUNT, LocalDate.now(), batch);
        ManufacturingListRequest second = ProductionDataFactory.buildCreateRequest(
                alternateMap, AMOUNT, LocalDate.now(), batch);
        ManufacturingListRequest bulk = ManufacturingListRequest.builder()
                .items(List.of(first.getItems().getFirst(), second.getItems().getFirst()))
                .build();
        Set<Long> resources = resourceIds(oneGroupMap, alternateMap);
        ProductionStockAssertions.StockSnapshot before = snapshot(resources, "before rejected bulk request");

        Response response = productionFixture.tryCreateAs(UserRole.OWNER_1, storageId, bulk);

        assertThat(assertValidation(response, "batchNumber")).contains("за різними техкартами");
        assertStockUnchanged(before, snapshot(resources, "after rejected bulk request"));
        BatchRecipeResponse recipe = productionFixture.getBatchRecipe(
                UserRole.OWNER_1, storageId, outputId(oneGroupMap), batch);
        assertThat(recipe.getTechMapId()).isNull();
    }

    private TechnologicalMapResponse registerMap(TechnologicalMapResponse map) {
        createdMaps.add(map);
        return map;
    }

    private ManufacturingItemResponse track(ManufacturingItemResponse production) {
        createdProductionIds.add(production.getId());
        return production;
    }

    private Long outputId(TechnologicalMapResponse map) {
        return map.getOutput().getFirst().getResource().getId();
    }

    private String assertValidation(Response response, String field) {
        assertThat(response.statusCode()).isEqualTo(400);
        List<Map<String, Object>> errors = response.jsonPath().getList("errors");
        assertThat(errors).isNotNull();
        Map<String, Object> error = errors.stream()
                .filter(item -> field.equals(item.get("field")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No validation error for field " + field
                        + "; body=" + response.asString()));
        Object rawMessages = error.get("messages");
        assertThat(rawMessages).isInstanceOf(List.class);
        List<?> messages = (List<?>) rawMessages;
        assertThat(messages).isNotEmpty();
        return String.valueOf(messages.getFirst());
    }

    private TechnologicalMapAlternativeGroupResourceResponse defaultAlternative(
            TechnologicalMapAlternativeGroupResponse group) {
        return group.getAlternativeResources().stream()
                .filter(item -> Boolean.TRUE.equals(item.getIsDefault()))
                .findFirst()
                .orElseThrow();
    }

    private TechnologicalMapAlternativeGroupResourceResponse nonDefaultAlternative(
            TechnologicalMapAlternativeGroupResponse group) {
        return group.getAlternativeResources().stream()
                .filter(item -> !Boolean.TRUE.equals(item.getIsDefault()))
                .findFirst()
                .orElseThrow();
    }

    private List<AlternativeInputRequest> alternativeInputs(
            TechnologicalMapResponse map,
            Map<Long, Long> selectedResourceByGroup) {
        List<AlternativeInputRequest> result = new ArrayList<>();
        for (TechnologicalMapAlternativeGroupResponse group : map.getGroups()) {
            Long resourceId = selectedResourceByGroup.get(group.getId());
            TechnologicalMapAlternativeGroupResourceResponse selected = group.getAlternativeResources().stream()
                    .filter(item -> item.getResource().getId().equals(resourceId))
                    .findFirst()
                    .orElseThrow();
            result.add(AlternativeInputRequest.builder()
                    .groupId(group.getId())
                    .resourceId(resourceId)
                    .amount(selected.getAmount())
                    .build());
        }
        return result;
    }

    private Set<Long> resourceIds(TechnologicalMapResponse... maps) {
        Set<Long> ids = new LinkedHashSet<>();
        for (TechnologicalMapResponse map : maps) {
            map.getInput().forEach(input -> ids.add(input.getResource().getId()));
            map.getOutput().forEach(output -> ids.add(output.getResource().getId()));
            map.getGroups().forEach(group -> group.getAlternativeResources()
                    .forEach(alternative -> ids.add(alternative.getResource().getId())));
        }
        return ids;
    }

    private ProductionStockAssertions.StockSnapshot snapshot(Set<Long> resourceIds, String label) {
        return ProductionStockAssertions.capture(
                apiExecutor, storageId, UserRole.OWNER_1, resourceIds, label);
    }

    private void assertStockUnchanged(ProductionStockAssertions.StockSnapshot before,
                                      ProductionStockAssertions.StockSnapshot after) {
        assertThat(after.amounts()).containsExactlyInAnyOrderEntriesOf(before.amounts());
    }
}
