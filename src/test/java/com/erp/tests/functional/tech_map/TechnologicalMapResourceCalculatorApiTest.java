package com.erp.tests.functional.tech_map;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.tech_map.TechnologicalMapDataFactory;
import com.erp.enums.UserRole;
import com.erp.fixtures.ResourceCalculatorFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.models.request.ResourceUsageRequest;
import com.erp.models.request.TechnologicalMapRequest;
import com.erp.models.request.TechnologicalMapUsageExportRequest;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageResponse;
import com.erp.models.response.TechnologicalMapComponentResponse;
import com.erp.models.response.TechnologicalMapResourceUsageResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.XlsxContentAssertions;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Technological Maps")
@Feature("Калькулятор розхідників (REQ-MFG-001-CALC)")
public class TechnologicalMapResourceCalculatorApiTest extends BaseFunctionalTest {

    private ResourceCalculatorFixture fixture;
    private StorageFixture storageFixture;

    private final List<CleanupMap> maps = new ArrayList<>();
    private final List<Long> storagesNewestFirst = new ArrayList<>();

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void setup() {
        fixture = new ResourceCalculatorFixture(testContext, apiExecutor);
        fixture.prepareContext();
        storageFixture = new StorageFixture(testContext, apiExecutor);
    }

    @AfterMethod(alwaysRun = true)
    public void cleanup() {
        for (CleanupMap map : maps) {
            try {
                fixture.cleanupTechMap(map.techMap(), map.storageId());
            } catch (Exception e) {
                log.warn("Tech map cleanup failed: {}", e.getMessage());
            }
        }
        maps.clear();
        for (Long storageId : storagesNewestFirst) {
            try {
                storageFixture.archiveStorage(UserRole.ADMIN, storageId);
                storageFixture.untrackForCleanup(storageId);
            } catch (Exception e) {
                log.warn("Storage archive failed {}: {}", storageId, e.getMessage());
            }
        }
        storagesNewestFirst.clear();
        storageFixture.deactivateTrackedStorages(UserRole.ADMIN);
    }

    @Test(priority = 10)
    @TestCaseId("TC-TM-CALC-003")
    @Story("RBAC")
    @Severity(SeverityLevel.CRITICAL)
    @Description("OWNER_2 не має tech-map-list::read на склад OWNER_1 → 403.")
    public void calculateForbiddenWithoutRead() {
        Response response = fixture.calculateRaw(
                UserRole.OWNER_2,
                ConfigProvider.getOwner1StorageId(),
                1L,
                "1",
                List.of());
        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test(priority = 20)
    @TestCaseId("TC-TM-CALC-004")
    @Story("Access")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Корінь техкарти прив’язаний до storageId: чужа карта → 404; своя рахується без чужої як кореня.")
    public void foreignRootMapNotFoundAndOwnMapIsolated() {
        Long storageA = newStorage("calc-a-");
        Long storageB = newStorage("calc-b-");
        ResourceCalculatorFixture.Chain chainA = trackChain(storageA, fixture.createCanonicalChain(storageA));
        ResourceCalculatorFixture.Chain chainB = trackChain(storageB, fixture.createCanonicalChain(storageB));

        Response foreign = fixture.calculateRaw(
                UserRole.ADMIN, storageA, chainB.getProductMap().getId(), "1", List.of());
        assertThat(foreign.statusCode()).isEqualTo(404);

        TechnologicalMapResourceUsageResponse own = fixture.calculate(
                UserRole.ADMIN, storageA, chainA.getProductMap().getId(), "1");
        assertThat(own.getId()).isEqualTo(chainA.getProductMap().getId());
        assertThat(own.getName()).isEqualTo(chainA.getProductMap().getName());
        assertThat(fixture.hasComponent(own, chainB.getChip().getName()))
                .as("чужий ланцюжок не підмішується в розрахунок карти A")
                .isFalse();
    }

    @Test(priority = 30)
    @TestCaseId("TC-TM-CALC-010")
    @Story("Explosion")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Виріб ←2 корпус ←3 плата ←4 чіп, amount=10 → 20 / 60 / 240. Чіп — лист.")
    public void threeLevelExplosionScaledByAmount() {
        IsolatedChain isolated = canonicalChain();
        TechnologicalMapResourceUsageResponse root = fixture.calculate(
                UserRole.ADMIN,
                isolated.storageId(),
                isolated.chain().getProductMap().getId(),
                "10");

        assertThat(root.getAmount()).isEqualTo(10.0);
        TechnologicalMapComponentResponse body = fixture.requireComponent(root, isolated.chain().getBody().getName());
        fixture.assertAmount(body.getAmount(), 20);
        assertThat(body.getIsRequiresChoice()).isFalse();

        TechnologicalMapComponentResponse board = fixture.requireComponent(root, isolated.chain().getBoard().getName());
        fixture.assertAmount(board.getAmount(), 60);

        TechnologicalMapComponentResponse chip = fixture.requireComponent(root, isolated.chain().getChip().getName());
        fixture.assertAmount(chip.getAmount(), 240);
        assertThat(chip.getIsRequiresChoice()).isFalse();
        assertThat(chip.getTechnolMaps()).isEmpty();
        assertThat(chip.getComponents()).isEmpty();
    }

    @Test(priority = 40)
    @TestCaseId("TC-TM-CALC-011")
    @Story("Explosion")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Продукт карти відсутній у components; вхід сусідньої карти не з’являється.")
    public void productExcludedAndSiblingMapIsolated() {
        Long storageId = newStorage("calc-iso-");
        ResourceCalculatorFixture.Chain chain = trackChain(storageId, fixture.createCanonicalChain(storageId));
        ResourceResponse siblingIn = fixture.resources().createUniqueResource("CALC-SIB-IN-" + System.currentTimeMillis());
        ResourceResponse siblingOut = fixture.resources().createUniqueResource("CALC-SIB-OUT-" + System.currentTimeMillis());
        TechnologicalMapResponse sibling = fixture.techMaps().createTechMapWithRequest(
                UserRole.ADMIN,
                TechnologicalMapDataFactory.createProductionMapWithStorages(
                        "CALC-sib",
                        List.of(new ResourceUsageRequest(siblingIn.getId(), 1.0)),
                        List.of(new ResourceUsageRequest(siblingOut.getId(), 1.0)),
                        Set.of(storageId)).build());
        maps.add(new CleanupMap(sibling, storageId));

        TechnologicalMapResourceUsageResponse root = fixture.calculate(
                UserRole.ADMIN, storageId, chain.getProductMap().getId(), "1");

        assertThat(fixture.hasComponent(root, chain.getProduct().getName())).isFalse();
        assertThat(fixture.hasComponent(root, chain.getBody().getName())).isTrue();
        assertThat(fixture.hasComponent(root, siblingIn.getName()))
                .as("вхід сусідньої карти не входить у розрахунок")
                .isFalse();
        assertThat(fixture.hasComponent(root, siblingOut.getName())).isFalse();
    }

    @Test(priority = 50)
    @TestCaseId("TC-TM-CALC-012")
    @Story("Explosion")
    @Severity(SeverityLevel.CRITICAL)
    @Description("DISASSEMBLE-карта з тим самим виходом не є виробником компонента.")
    public void disassembleMapIsNotProducer() {
        Long storageId = newStorage("calc-dis-");
        String suffix = String.valueOf(System.currentTimeMillis());
        ResourceResponse glue = fixture.resources().createUniqueResource("CALC-GLUE-" + suffix);
        ResourceResponse scrap = fixture.resources().createUniqueResource("CALC-SCRAP-" + suffix);
        ResourceResponse product = fixture.resources().createUniqueResource("CALC-GLP-" + suffix);

        TechnologicalMapResponse productMap = fixture.techMaps().createTechMapWithRequest(
                UserRole.ADMIN,
                TechnologicalMapDataFactory.createProductionMapWithStorages(
                        "CALC-glue-prd",
                        List.of(new ResourceUsageRequest(glue.getId(), 2.0)),
                        List.of(new ResourceUsageRequest(product.getId(), 1.0)),
                        Set.of(storageId)).build());
        maps.add(new CleanupMap(productMap, storageId));
        TechnologicalMapResponse disassemble = fixture.techMaps().createTechMapWithRequest(
                UserRole.ADMIN,
                fixture.disassembleMap("CALC-glue-dis", glue, scrap, storageId));
        maps.add(new CleanupMap(disassemble, storageId));

        TechnologicalMapResourceUsageResponse root = fixture.calculate(
                UserRole.ADMIN, storageId, productMap.getId(), "3");
        TechnologicalMapComponentResponse glueRow = fixture.requireComponent(root, glue.getName());
        fixture.assertAmount(glueRow.getAmount(), 6);
        assertThat(glueRow.getTechnolMaps())
                .as("DISASSEMBLE не потрапляє в technolMaps")
                .isEmpty();
        assertThat(glueRow.getComponents()).isEmpty();
    }

    @Test(priority = 60)
    @TestCaseId("TC-TM-CALC-013")
    @Story("Explosion")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Alt-group: у вибух входить лише default. amount=3, норма 2 → 6.")
    public void alternativeGroupUsesDefaultOnly() {
        Long storageId = newStorage("calc-alt-");
        List<ResourceResponse> resources = fixture.techMaps().createAltGroupResources();
        ResourceResponse defaultAlt = resources.get(1);
        ResourceResponse otherAlt = resources.get(2);
        TechnologicalMapRequest request = TechnologicalMapDataFactory.createProductionMapGroupsOnly(
                List.of(defaultAlt, otherAlt, resources.get(3)), storageId);
        TechnologicalMapResponse map = fixture.techMaps().createTechMapWithRequest(UserRole.ADMIN, request);
        maps.add(new CleanupMap(map, storageId));

        TechnologicalMapResourceUsageResponse root = fixture.calculate(
                UserRole.ADMIN, storageId, map.getId(), "3");
        assertThat(root.getComponents()).hasSize(1);
        TechnologicalMapComponentResponse row = fixture.requireComponent(root, defaultAlt.getName());
        fixture.assertAmount(row.getAmount(), 4.5);
        assertThat(fixture.hasComponent(root, otherAlt.getName())).isFalse();
    }

    @Test(priority = 70)
    @TestCaseId("TC-TM-CALC-014")
    @Story("Explosion")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Цикл виріб↔деталь обривається на другій появі карти.")
    public void resourceCycleStopsRecursion() {
        Long storageId = newStorage("calc-cyc-");
        String suffix = String.valueOf(System.currentTimeMillis());
        ResourceResponse product = fixture.resources().createUniqueResource("CALC-CYC-P-" + suffix);
        ResourceResponse part = fixture.resources().createUniqueResource("CALC-CYC-D-" + suffix);

        TechnologicalMapResponse productMap = fixture.techMaps().createTechMapWithRequest(
                UserRole.ADMIN,
                TechnologicalMapDataFactory.createProductionMapWithStorages(
                        "CALC-cyc-prd",
                        List.of(new ResourceUsageRequest(part.getId(), 2.0)),
                        List.of(new ResourceUsageRequest(product.getId(), 1.0)),
                        Set.of(storageId)).build());
        TechnologicalMapResponse partMap = fixture.techMaps().createTechMapWithRequest(
                UserRole.ADMIN,
                TechnologicalMapDataFactory.createProductionMapWithStorages(
                        "CALC-cyc-part",
                        List.of(new ResourceUsageRequest(product.getId(), 3.0)),
                        List.of(new ResourceUsageRequest(part.getId(), 1.0)),
                        Set.of(storageId)).build());
        maps.add(new CleanupMap(productMap, storageId));
        maps.add(new CleanupMap(partMap, storageId));

        TechnologicalMapResourceUsageResponse root = fixture.calculate(
                UserRole.ADMIN, storageId, productMap.getId(), "10");
        TechnologicalMapComponentResponse partRow = fixture.requireComponent(root, part.getName());
        TechnologicalMapComponentResponse nestedProduct = fixture.requireComponent(root, product.getName());
        fixture.assertAmount(nestedProduct.getAmount(), 60);
        assertThat(nestedProduct.getTechnolMaps())
                .as("карта виробу вже на гілці — другий вибух не стартує")
                .isEmpty();
        assertThat(nestedProduct.getComponents()).isEmpty();
        assertThat(partRow.getComponents()).isNotEmpty();
    }

    @Test(priority = 80)
    @TestCaseId("TC-TM-CALC-015")
    @Story("Validation")
    @Severity(SeverityLevel.CRITICAL)
    @Description("amount=0 і від’ємний amount → 400, поле amount.")
    public void nonPositiveAmountRejected() {
        IsolatedChain isolated = canonicalChain();
        Response zero = fixture.calculateRaw(
                UserRole.ADMIN, isolated.storageId(), isolated.chain().getProductMap().getId(), "0", List.of());
        assertThat(zero.statusCode()).isEqualTo(400);
        assertThat(zero.jsonPath().getString("errors[0].field")).isEqualTo("amount");

        Response negative = fixture.calculateRaw(
                UserRole.ADMIN, isolated.storageId(), isolated.chain().getProductMap().getId(), "-1", List.of());
        assertThat(negative.statusCode()).isEqualTo(400);
        assertThat(negative.jsonPath().getString("errors[0].field")).isEqualTo("amount");
    }

    @Test(priority = 90)
    @TestCaseId("TC-TM-CALC-016")
    @Story("Validation")
    @Severity(SeverityLevel.NORMAL)
    @Description("GET без tmIds → 400.")
    public void missingTmIdsRejected() {
        Long storageId = newStorage("calc-ids-");
        Response response = fixture.calculateRaw(UserRole.ADMIN, storageId, null, "1", List.of());
        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test(priority = 100)
    @TestCaseId("TC-TM-CALC-020")
    @Story("Choice")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Два виробники плати → isRequiresChoice=true, гілка не розкладена.")
    public void severalProducersRequireChoice() {
        IsolatedChoice isolated = choiceChain();
        TechnologicalMapResourceUsageResponse root = fixture.calculate(
                UserRole.ADMIN,
                isolated.productStorageId(),
                isolated.chain().getProductMap().getId(),
                "10");
        TechnologicalMapComponentResponse board = fixture.requireComponent(root, isolated.chain().getBoard().getName());
        assertThat(board.getIsRequiresChoice()).isTrue();
        assertThat(board.getSelectedTmId()).isNull();
        assertThat(board.getTechnolMaps()).hasSize(2);
        assertThat(board.getComponents()).isEmpty();
    }

    @Test(priority = 110)
    @TestCaseId("TC-TM-CALC-021")
    @Story("Choice")
    @Severity(SeverityLevel.CRITICAL)
    @Description("chosenTmIds розкладає гілку обраної карти B (чіп 5 × плата 2 × 10 = 100).")
    public void chosenTechMapExpandsBranch() {
        IsolatedChoice isolated = choiceChain();
        TechnologicalMapResourceUsageResponse root = fixture.calculate(
                UserRole.ADMIN,
                isolated.productStorageId(),
                isolated.chain().getProductMap().getId(),
                "10",
                List.of(isolated.chain().getBoardMapB().getId()));
        TechnologicalMapComponentResponse board = fixture.requireComponent(root, isolated.chain().getBoard().getName());
        assertThat(board.getIsRequiresChoice()).isFalse();
        assertThat(board.getSelectedTmId()).isEqualTo(isolated.chain().getBoardMapB().getId());
        TechnologicalMapComponentResponse chip = fixture.requireComponent(root, isolated.chain().getChip().getName());
        fixture.assertAmount(chip.getAmount(), 100);
    }

    @Test(priority = 120)
    @TestCaseId("TC-TM-CALC-032")
    @Story("Export")
    @Severity(SeverityLevel.CRITICAL)
    @Description("POST export рендерить xlsx з рядків payload; pending — «не розкладено».")
    public void exportWorkbookMirrorsPayload() {
        TechnologicalMapUsageExportRequest request = TechnologicalMapUsageExportRequest.builder()
                .technologicalMapName("виріб map")
                .amount(10.0)
                .unit("шт")
                .groups(List.of(
                        TechnologicalMapUsageExportRequest.Group.builder()
                                .storageLabel("Storage1")
                                .rows(List.of(TechnologicalMapUsageExportRequest.Row.builder()
                                        .name("лист сталі").amount(50.0).unit("кг").isPending(false).build()))
                                .build(),
                        TechnologicalMapUsageExportRequest.Group.builder()
                                .storageLabel("Storage2")
                                .rows(List.of(TechnologicalMapUsageExportRequest.Row.builder()
                                        .name("плата").amount(12.5).unit("шт").isPending(true).build()))
                                .build()))
                .build();

        Response response = fixture.exportSummary(UserRole.ADMIN, request);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.getContentType()).contains("spreadsheetml");
        byte[] body = response.asByteArray();
        assertThat(body.length).isGreaterThan(100);
        assertThat(XlsxContentAssertions.zipContainsText(body, "виріб map")).isTrue();
        assertThat(XlsxContentAssertions.zipContainsText(body, "лист сталі")).isTrue();
        assertThat(XlsxContentAssertions.zipContainsText(body, "плата")).isTrue();
        assertThat(XlsxContentAssertions.zipContainsText(body, "не розкладено")).isTrue();
    }

    @Test(priority = 130)
    @TestCaseId("TC-TM-CALC-034")
    @Story("Explosion")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Після деактивації карти корпусу компонент стає листом; плата/чіп не з’являються.")
    public void inactiveProducerIsNotExploded() {
        IsolatedChain isolated = canonicalChain();
        fixture.cleanupTechMap(isolated.chain().getBodyMap(), isolated.storageId());
        maps.removeIf(item -> isolated.chain().getBodyMap().getId().equals(item.techMap().getId()));

        TechnologicalMapResourceUsageResponse root = fixture.calculate(
                UserRole.ADMIN, isolated.storageId(), isolated.chain().getProductMap().getId(), "10");
        TechnologicalMapComponentResponse body = fixture.requireComponent(root, isolated.chain().getBody().getName());
        assertThat(body.getTechnolMaps()).isEmpty();
        assertThat(body.getComponents()).isEmpty();
        assertThat(fixture.hasComponent(root, isolated.chain().getBoard().getName())).isFalse();
        assertThat(fixture.hasComponent(root, isolated.chain().getChip().getName())).isFalse();
    }

    private IsolatedChain canonicalChain() {
        Long storageId = newStorage("calc-can-");
        return new IsolatedChain(storageId, trackChain(storageId, fixture.createCanonicalChain(storageId)));
    }

    private IsolatedChoice choiceChain() {
        Long productStorage = newStorage("calc-ch-a-");
        Long otherStorage = newStorage("calc-ch-b-");
        ResourceCalculatorFixture.ChoiceChain chain = fixture.createChoiceChain(productStorage, otherStorage);
        maps.add(new CleanupMap(chain.getProductMap(), productStorage));
        maps.add(new CleanupMap(chain.getBoardMapA(), productStorage));
        maps.add(new CleanupMap(chain.getBoardMapB(), otherStorage));
        return new IsolatedChoice(productStorage, otherStorage, chain);
    }

    private ResourceCalculatorFixture.Chain trackChain(Long storageId, ResourceCalculatorFixture.Chain chain) {
        maps.add(new CleanupMap(chain.getProductMap(), storageId));
        maps.add(new CleanupMap(chain.getBodyMap(), storageId));
        maps.add(new CleanupMap(chain.getBoardMap(), storageId));
        return chain;
    }

    private Long newStorage(String prefix) {
        StorageResponse storage = storageFixture.createChildStorage(prefix);
        storagesNewestFirst.add(0, storage.getId());
        return storage.getId();
    }

    private record IsolatedChain(Long storageId, ResourceCalculatorFixture.Chain chain) {
    }

    private record IsolatedChoice(Long productStorageId, Long otherStorageId,
                                  ResourceCalculatorFixture.ChoiceChain chain) {
    }

    private record CleanupMap(TechnologicalMapResponse techMap, Long storageId) {
    }
}
