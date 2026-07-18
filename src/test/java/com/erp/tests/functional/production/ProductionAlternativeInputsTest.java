package com.erp.tests.functional.production;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.production.ProductionDataFactory;
import com.erp.data.factories.tech_map.TechnologicalMapDataFactory;
import com.erp.enums.StorageTechnologicalMapMode;
import com.erp.enums.UserRole;
import com.erp.fixtures.ProductionFixture;
import com.erp.fixtures.TechnologicalMapFixture;
import com.erp.models.request.AlternativeInputRequest;
import com.erp.models.request.ManufacturingListRequest;
import com.erp.models.request.TechnologicalMapRequest;
import com.erp.models.response.ManufacturingItemResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.TechnologicalMapAlternativeGroupResourceResponse;
import com.erp.models.response.TechnologicalMapAlternativeGroupResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.ProductionStockAssertions;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * API coverage for production create with alternativeInputs from tech-map groups.
 */
@Slf4j
@Epic("Production")
@Feature("Alternative inputs")
public class ProductionAlternativeInputsTest extends BaseFunctionalTest {

    private static final double MIN_STOCK = 200.0;
    private static final double PRODUCE_AMOUNT = 5.0;

    private ProductionFixture productionFixture;
    private TechnologicalMapFixture techMapFixture;
    private Long storageId;
    private TechnologicalMapResponse techMap;
    private Long fixedInputId;
    private Long defaultAltId;
    private Long otherAltId;
    private Long outputId;
    private Long groupId;
    private Double defaultAltAmount;
    private Double otherAltAmount;
    private Double fixedInputAmount;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    @Step("Підготовка техкарти з альтернативною групою для production tests")
    public void setupAlternativeInputsTest() {
        productionFixture = new ProductionFixture(testContext, apiExecutor);
        techMapFixture = productionFixture.getTechMapFixture();
        techMapFixture.prepareContext();
        storageId = ConfigProvider.getOwner1StorageId();

        techMapFixture.setMode(storageId, StorageTechnologicalMapMode.EDIT_ALLOWED);
        techMap = techMapFixture.createTechMapWithAlternativeGroup(UserRole.ADMIN, storageId);

        fixedInputId = techMap.getInput().getFirst().getResource().getId();
        fixedInputAmount = techMap.getInput().getFirst().getAmount();
        outputId = techMap.getOutput().getFirst().getResource().getId();

        TechnologicalMapAlternativeGroupResponse group = techMap.getGroups().getFirst();
        groupId = group.getId();
        TechnologicalMapAlternativeGroupResourceResponse defaultRes = group.getAlternativeResources().stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsDefault()))
                .findFirst()
                .orElseThrow();
        TechnologicalMapAlternativeGroupResourceResponse otherRes = group.getAlternativeResources().stream()
                .filter(r -> !Boolean.TRUE.equals(r.getIsDefault()))
                .findFirst()
                .orElseThrow();
        defaultAltId = defaultRes.getResource().getId();
        defaultAltAmount = defaultRes.getAmount();
        otherAltId = otherRes.getResource().getId();
        otherAltAmount = otherRes.getAmount();
    }

    @BeforeMethod(alwaysRun = true)
    @Step("Поповнити запас fixed + обох альтернатив перед тестом")
    public void ensureStockBeforeTest() {
        productionFixture.ensureStockForTechMapInputs(storageId, techMap, MIN_STOCK);
    }

    @AfterClass(alwaysRun = true)
    @Step("Деактивувати техкарту та відновити READ_ONLY")
    public void cleanup() {
        if (techMap != null && techMapFixture != null && storageId != null) {
            techMapFixture.deactivateTechMap(UserRole.OWNER_1, techMap.getId(), storageId);
            techMapFixture.setMode(storageId, StorageTechnologicalMapMode.READ_ONLY);
        }
    }

    @Test(priority = 10)
    @TestCaseId("TC-PROD-ALT-001")
    @Story("Create production with default alternative")
    @Description("OWNER_1 POST productions з alternativeInputs = default → 200; stock − на default alt і fixed input; product +")
    @Severity(SeverityLevel.CRITICAL)
    public void testCreateProductionWithDefaultAlternative() {
        Set<Long> resourceIds = Set.of(fixedInputId, defaultAltId, otherAltId, outputId);
        ProductionStockAssertions.StockSnapshot before = ProductionStockAssertions.capture(
                apiExecutor, storageId, UserRole.OWNER_1, resourceIds, "before default alt production");

        ManufacturingItemResponse created = Allure.step("OWNER_1: create with default alternativeInputs", () ->
                productionFixture.createAs(
                        UserRole.OWNER_1, storageId, techMap, PRODUCE_AMOUNT,
                        ProductionDataFactory.uniqueBatchNumber()));

        assertThat(created.getId()).isNotNull();

        ProductionStockAssertions.StockSnapshot after = ProductionStockAssertions.capture(
                apiExecutor, storageId, UserRole.OWNER_1, resourceIds, "after default alt production");

        Map<Long, Double> expectedDelta = Map.of(
                fixedInputId, -(PRODUCE_AMOUNT * fixedInputAmount),
                defaultAltId, -(PRODUCE_AMOUNT * defaultAltAmount),
                otherAltId, 0.0,
                outputId, PRODUCE_AMOUNT * techMap.getOutput().getFirst().getAmount()
        );
        ProductionStockAssertions.assertDelta(before, after, expectedDelta, outputId);
    }

    @Test(priority = 11)
    @TestCaseId("TC-PROD-ALT-002")
    @Story("Create production with non-default alternative")
    @Description("OWNER_1 POST з вибором non-default ресурсу → stock − на B, A (default) без змін від alt")
    @Severity(SeverityLevel.CRITICAL)
    public void testCreateProductionWithNonDefaultAlternative() {
        Set<Long> resourceIds = Set.of(fixedInputId, defaultAltId, otherAltId, outputId);
        ProductionStockAssertions.StockSnapshot before = ProductionStockAssertions.capture(
                apiExecutor, storageId, UserRole.OWNER_1, resourceIds, "before non-default alt production");

        List<AlternativeInputRequest> choice = ProductionDataFactory.alternativeInputsChoosing(
                techMap, groupId, otherAltId);

        ManufacturingItemResponse created = Allure.step("OWNER_1: create with non-default alternative", () ->
                productionFixture.createAsWithAlternatives(
                        UserRole.OWNER_1, storageId, techMap, PRODUCE_AMOUNT, choice));

        assertThat(created.getId()).isNotNull();

        ProductionStockAssertions.StockSnapshot after = ProductionStockAssertions.capture(
                apiExecutor, storageId, UserRole.OWNER_1, resourceIds, "after non-default alt production");

        Map<Long, Double> expectedDelta = Map.of(
                fixedInputId, -(PRODUCE_AMOUNT * fixedInputAmount),
                defaultAltId, 0.0,
                otherAltId, -(PRODUCE_AMOUNT * otherAltAmount),
                outputId, PRODUCE_AMOUNT * techMap.getOutput().getFirst().getAmount()
        );
        ProductionStockAssertions.assertDelta(before, after, expectedDelta, outputId);
    }

    @Test(priority = 20)
    @TestCaseId("TC-PROD-ALT-003")
    @Story("alternativeInputs required")
    @Description("POST без alternativeInputs при наявній групі → 400, group.notSelected")
    @Severity(SeverityLevel.CRITICAL)
    public void testCreateRejectedWhenAlternativeGroupNotSelected() {
        ManufacturingListRequest request = ProductionDataFactory.buildCreateRequest(
                techMap, PRODUCE_AMOUNT, java.time.LocalDate.now(),
                ProductionDataFactory.uniqueBatchNumber(), new ArrayList<>());

        Response response = Allure.step("OWNER_1: create without alternativeInputs", () ->
                productionFixture.tryCreateAs(UserRole.OWNER_1, storageId, request));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.jsonPath().getString("errors[0].field")).contains("alternativeInputs");
        assertThat(response.jsonPath().getString("errors[0].messages[0]"))
                .contains("альтернативний ресурс");
    }

    @Test(priority = 21)
    @TestCaseId("TC-PROD-ALT-004")
    @Story("alternativeInputs validation")
    @Description("Невірний groupId / resourceId не з групи / amount ≠ map → 400")
    @Severity(SeverityLevel.NORMAL)
    public void testCreateRejectedForInvalidAlternativeInputs() {
        Allure.step("Invalid groupId", () -> {
            List<AlternativeInputRequest> alts = List.of(AlternativeInputRequest.builder()
                    .groupId(9_999_999L)
                    .resourceId(defaultAltId)
                    .amount(defaultAltAmount)
                    .build());
            ManufacturingListRequest request = ProductionDataFactory.buildCreateRequest(
                    techMap, PRODUCE_AMOUNT, java.time.LocalDate.now(),
                    ProductionDataFactory.uniqueBatchNumber(), alts);
            Response response = productionFixture.tryCreateAs(UserRole.OWNER_1, storageId, request);
            assertThat(response.statusCode()).isEqualTo(400);
            String body = response.asString();
            assertThat(body)
                    .as("Body should mention invalid alternative group")
                    .containsAnyOf("не знайдено", "альтернативн");
        });

        Allure.step("Resource not in group", () -> {
            List<AlternativeInputRequest> alts = List.of(AlternativeInputRequest.builder()
                    .groupId(groupId)
                    .resourceId(fixedInputId)
                    .amount(defaultAltAmount)
                    .build());
            ManufacturingListRequest request = ProductionDataFactory.buildCreateRequest(
                    techMap, PRODUCE_AMOUNT, java.time.LocalDate.now(),
                    ProductionDataFactory.uniqueBatchNumber(), alts);
            Response response = productionFixture.tryCreateAs(UserRole.OWNER_1, storageId, request);
            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(response.asString()).contains("не належить");
        });

        Allure.step("Amount mismatch", () -> {
            List<AlternativeInputRequest> alts = List.of(AlternativeInputRequest.builder()
                    .groupId(groupId)
                    .resourceId(defaultAltId)
                    .amount(defaultAltAmount + 1.0)
                    .build());
            ManufacturingListRequest request = ProductionDataFactory.buildCreateRequest(
                    techMap, PRODUCE_AMOUNT, java.time.LocalDate.now(),
                    ProductionDataFactory.uniqueBatchNumber(), alts);
            Response response = productionFixture.tryCreateAs(UserRole.OWNER_1, storageId, request);
            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(response.asString()).contains("не збігається");
        });
    }

    @Test(priority = 22)
    @TestCaseId("TC-PROD-ALT-005")
    @Story("alternativeInputs validation")
    @Description("Двічі той самий groupId у alternativeInputs → 400, group.duplicated")
    @Severity(SeverityLevel.NORMAL)
    public void testCreateRejectedWhenGroupDuplicated() {
        AlternativeInputRequest once = AlternativeInputRequest.builder()
                .groupId(groupId)
                .resourceId(defaultAltId)
                .amount(defaultAltAmount)
                .build();
        List<AlternativeInputRequest> duplicated = List.of(once, once.toBuilder().build());

        ManufacturingListRequest request = ProductionDataFactory.buildCreateRequest(
                techMap, PRODUCE_AMOUNT, java.time.LocalDate.now(),
                ProductionDataFactory.uniqueBatchNumber(), duplicated);

        Response response = Allure.step("OWNER_1: create with duplicated groupId", () ->
                productionFixture.tryCreateAs(UserRole.OWNER_1, storageId, request));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.jsonPath().getString("errors[0].messages[0]"))
                .contains("більше одного альтернативного ресурсу");
    }

    @Test(priority = 30)
    @TestCaseId("TC-PROD-ALT-006")
    @Story("Production with two alternative groups")
    @Description("Tech map з 2 групами → production з 2 alternativeInputs → stock delta на обидва default alt + fixed")
    @Severity(SeverityLevel.CRITICAL)
    public void testCreateProductionWithTwoAlternativeGroups() {
        TechnologicalMapFixture techMapFixtureLocal = productionFixture.getTechMapFixture();
        List<ResourceResponse> resources = techMapFixtureLocal.createTwoGroupAltResources();
        TechnologicalMapRequest request = TechnologicalMapDataFactory
                .createProductionMapWithTwoGroups(resources, storageId);
        TechnologicalMapResponse twoGroupMap = techMapFixtureLocal.createTechMapWithRequest(UserRole.ADMIN, request);

        Long fixedId = twoGroupMap.getInput().getFirst().getResource().getId();
        double fixedAmount = twoGroupMap.getInput().getFirst().getAmount();
        Long glueDefaultId = twoGroupMap.getGroups().get(0).getAlternativeResources().stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsDefault()))
                .map(r -> r.getResource().getId()).findFirst().orElseThrow();
        double glueDefaultAmount = twoGroupMap.getGroups().get(0).getAlternativeResources().stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsDefault()))
                .mapToDouble(r -> r.getAmount()).findFirst().orElseThrow();
        Long fuelDefaultId = twoGroupMap.getGroups().get(1).getAlternativeResources().stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsDefault()))
                .map(r -> r.getResource().getId()).findFirst().orElseThrow();
        double fuelDefaultAmount = twoGroupMap.getGroups().get(1).getAlternativeResources().stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsDefault()))
                .mapToDouble(r -> r.getAmount()).findFirst().orElseThrow();
        Long outputId = twoGroupMap.getOutput().getFirst().getResource().getId();

        productionFixture.ensureStockForTechMapInputs(storageId, twoGroupMap, MIN_STOCK);
        Set<Long> resourceIds = Set.of(fixedId, glueDefaultId, fuelDefaultId, outputId);
        ProductionStockAssertions.StockSnapshot before = ProductionStockAssertions.capture(
                apiExecutor, storageId, UserRole.OWNER_1, resourceIds, "before two-group production");

        ManufacturingItemResponse created = Allure.step("OWNER_1: create with defaults for both groups", () ->
                productionFixture.createAs(
                        UserRole.OWNER_1, storageId, twoGroupMap, PRODUCE_AMOUNT,
                        ProductionDataFactory.uniqueBatchNumber()));

        assertThat(created.getId()).isNotNull();

        ProductionStockAssertions.StockSnapshot after = ProductionStockAssertions.capture(
                apiExecutor, storageId, UserRole.OWNER_1, resourceIds, "after two-group production");

        Map<Long, Double> expectedDelta = Map.of(
                fixedId, -(PRODUCE_AMOUNT * fixedAmount),
                glueDefaultId, -(PRODUCE_AMOUNT * glueDefaultAmount),
                fuelDefaultId, -(PRODUCE_AMOUNT * fuelDefaultAmount),
                outputId, PRODUCE_AMOUNT * twoGroupMap.getOutput().getFirst().getAmount()
        );
        ProductionStockAssertions.assertDelta(before, after, expectedDelta, outputId);

        techMapFixture.deactivateTechMap(UserRole.OWNER_1, twoGroupMap.getId(), storageId);
    }

    @Test(priority = 31)
    @TestCaseId("TC-PROD-ALT-007")
    @Story("GET production reflects chosen alternative")
    @Description("GET production by id → input містить фактично обраний non-default ресурс альтернативної групи")
    @Severity(SeverityLevel.NORMAL)
    public void testGetProductionReflectsChosenAlternative() {
        List<AlternativeInputRequest> choice = ProductionDataFactory.alternativeInputsChoosing(
                techMap, groupId, otherAltId);

        ManufacturingItemResponse created = Allure.step("OWNER_1: create with non-default alt", () ->
                productionFixture.createAsWithAlternatives(
                        UserRole.OWNER_1, storageId, techMap, PRODUCE_AMOUNT, choice));

        ManufacturingItemResponse fetched = Allure.step("GET production by id", () ->
                productionFixture.getById(UserRole.OWNER_1, created.getId(), storageId));

        Allure.step("Assert input contains chosen alternative resource", () -> {
            assertThat(fetched.getInput()).isNotNull();
            boolean hasChosenAlt = fetched.getInput().stream()
                    .anyMatch(u -> u.getResource() != null && otherAltId.equals(u.getResource().getId()));
            boolean hasDefaultAlt = fetched.getInput().stream()
                    .anyMatch(u -> u.getResource() != null && defaultAltId.equals(u.getResource().getId()));
            assertThat(hasChosenAlt).as("chosen non-default alt in input").isTrue();
            assertThat(hasDefaultAlt).as("default alt not consumed").isFalse();
        });
    }
}
