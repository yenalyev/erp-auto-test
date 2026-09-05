package com.erp.tests.functional.statistics;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.PlanNeededResourcesFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.models.request.ExecutionFilterRequest;
import com.erp.models.request.ResourceUsageRequest;
import com.erp.models.response.NeededResourcePathStepResponse;
import com.erp.models.response.NeededResourceResponse;
import com.erp.models.response.NeededResourceSourceResponse;
import com.erp.models.response.PlanNeededResourcesResponse;
import com.erp.models.response.PlanResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.config.ConfigProvider;
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

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@Slf4j
@Epic("Plans")
@Feature("Потрібні ресурси (Виконання плану)")
public class PlanNeededResourcesApiTest extends BaseFunctionalTest {

    private static final double EPS = 0.05;

    private PlanNeededResourcesFixture fixture;
    private StorageFixture storageFixture;

    private final List<PlanResponse> plans = new ArrayList<>();
    private final List<CleanupProduction> productions = new ArrayList<>();
    private final List<CleanupMap> maps = new ArrayList<>();
    private final List<Long> storagesNewestFirst = new ArrayList<>();

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void setup() {
        fixture = new PlanNeededResourcesFixture(testContext, apiExecutor);
        fixture.prepareContext();
        storageFixture = new StorageFixture(testContext, apiExecutor);
    }

    @AfterMethod(alwaysRun = true)
    public void cleanup() {
        for (PlanResponse plan : plans) {
            try {
                fixture.techMaps().deleteLocationPlan(plan.getId());
            } catch (Exception e) {
                log.warn("Plan cleanup failed: {}", e.getMessage());
            }
        }
        plans.clear();
        for (CleanupProduction production : productions) {
            try {
                fixture.production().deleteAs(UserRole.ADMIN, production.id(), production.storageId());
            } catch (Exception e) {
                log.warn("Production cleanup failed: {}", e.getMessage());
            }
        }
        productions.clear();
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
    @TestCaseId("TC-PLN-NR-004")
    @Story("RBAC")
    @Severity(SeverityLevel.CRITICAL)
    @Description("OWNER_2 не має statistics-plan-execution::read на склад OWNER_1 → 403.")
    public void neededResourcesForbiddenWithoutRead() {
        Response response = fixture.requestNeededRaw(
                UserRole.OWNER_2,
                ConfigProvider.getOwner1StorageId(),
                fixture.currentMonth());
        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test(priority = 20)
    @TestCaseId("TC-PLN-NR-005")
    @Story("Explosion")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Продукт плану відсутній у neededResources; є лише входи техкарти.")
    public void planProductExcludedFromNeededList() {
        IsolatedChain isolated = canonicalIsolatedChain();
        PlanNeededResourcesResponse body = fixture.requestNeeded(
                UserRole.ADMIN, isolated.storageId(), fixture.currentMonth());

        assertThat(fixture.hasRow(body, isolated.chain().getProduct().getName())).isFalse();
        assertThat(fixture.hasRow(body, isolated.chain().getIntermediate().getName())).isTrue();
        assertThat(fixture.hasRow(body, isolated.chain().getRaw().getName())).isTrue();
        body.getNeededResources().forEach(row -> {
            assertThat(row.getResource()).isNotNull();
            assertThat(row.getNeeded()).isNotNull();
            assertThat(row.getInStock()).isNotNull();
            assertThat(row.getShortage()).isNotNull();
            assertThat(row.getSources()).isNotNull();
        });
    }

    @Test(priority = 30)
    @TestCaseId("TC-PLN-NR-010")
    @Story("Explosion")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Багаторівневий вибух з netting при includeProduced=true.")
    public void multiLevelExplosionWithProducedNetting() {
        IsolatedChain isolated = canonicalIsolatedChain();
        PlanNeededResourcesResponse body = fixture.requestNeeded(
                UserRole.ADMIN,
                isolated.storageId(),
                fixture.currentMonth().toBuilder().includeProduced(true).includeStock(true).build());

        assertThat(body.getNeededResources()).hasSize(2);
        NeededResourceResponse intermediate = fixture.requireRow(body, isolated.chain().getIntermediate().getName());
        assertQty(intermediate.getNeeded(), 140);
        assertQty(intermediate.getInStock(), 40);
        assertQty(intermediate.getShortage(), 100);
        assertThat(intermediate.isProduced()).isTrue();

        NeededResourceResponse raw = fixture.requireRow(body, isolated.chain().getRaw().getName());
        assertQty(raw.getNeeded(), 300);
        assertQty(raw.getInStock(), 50);
        assertQty(raw.getShortage(), 250);
        assertThat(raw.isProduced()).isFalse();
        assertThat(raw.getResource().getCategory())
                .as("needed-resources передає category для client-side фільтра вкладки")
                .isNotNull();
        assertThat(raw.getResource().getCategory().getId()).isNotNull();
        assertThat(raw.getResource().getCategory().getName()).isNotBlank();
        assertThat(intermediate.getResource().getCategory()).isNotNull();
    }

    @Test(priority = 40)
    @TestCaseId("TC-PLN-NR-011")
    @Story("Explosion")
    @Severity(SeverityLevel.CRITICAL)
    @Description("За замовчуванням потреба від повної цілі плану, виготовлене ігнорується.")
    public void fullPlanGoalByDefault() {
        IsolatedChain isolated = canonicalIsolatedChain();
        PlanNeededResourcesResponse body = fixture.requestNeeded(
                UserRole.ADMIN, isolated.storageId(), fixture.currentMonth());

        NeededResourceResponse intermediate = fixture.requireRow(body, isolated.chain().getIntermediate().getName());
        assertQty(intermediate.getNeeded(), 200);
        assertQty(intermediate.getShortage(), 160);
        NeededResourceResponse raw = fixture.requireRow(body, isolated.chain().getRaw().getName());
        assertQty(raw.getNeeded(), 480);
        assertQty(raw.getShortage(), 430);
    }

    @Test(priority = 50)
    @TestCaseId("TC-PLN-NR-012")
    @Story("Explosion")
    @Severity(SeverityLevel.NORMAL)
    @Description("includeStock=false — gross, shortage=needed, inStock довідково.")
    public void grossNeedsWhenStockExcluded() {
        IsolatedChain isolated = canonicalIsolatedChain();
        PlanNeededResourcesResponse body = fixture.requestNeeded(
                UserRole.ADMIN,
                isolated.storageId(),
                fixture.currentMonth().toBuilder().includeStock(false).build());

        NeededResourceResponse intermediate = fixture.requireRow(body, isolated.chain().getIntermediate().getName());
        assertQty(intermediate.getNeeded(), 200);
        assertQty(intermediate.getInStock(), 40);
        assertQty(intermediate.getShortage(), 200);
        NeededResourceResponse raw = fixture.requireRow(body, isolated.chain().getRaw().getName());
        assertQty(raw.getNeeded(), 600);
        assertQty(raw.getInStock(), 50);
        assertQty(raw.getShortage(), 600);
    }

    @Test(priority = 60)
    @TestCaseId("TC-PLN-NR-013")
    @Story("Explosion")
    @Severity(SeverityLevel.NORMAL)
    @Description("includeProduced=true і remaining≤0 — продукт не вибухає.")
    public void fullyProducedPlanDoesNotExplode() {
        Long storageId = newStorage("nr-done-");
        PlanNeededResourcesFixture.Chain chain = createChain(storageId);
        trackPlan(fixture.createCurrentMonthPlan(storageId, chain.getProduct().getId(), 10));
        trackProduction(storageId, fixture.produce(storageId, chain.getProductMap(), 10));

        PlanNeededResourcesResponse body = fixture.requestNeeded(
                UserRole.ADMIN,
                storageId,
                fixture.currentMonth().toBuilder().includeProduced(true).build());
        assertThat(fixture.hasRow(body, chain.getIntermediate().getName())).isFalse();
        assertThat(fixture.hasRow(body, chain.getRaw().getName())).isFalse();
    }

    @Test(priority = 70)
    @TestCaseId("TC-PLN-NR-014")
    @Story("Explosion")
    @Severity(SeverityLevel.NORMAL)
    @Description("План без PRODUCTION-техкарти не дає рядків потреби.")
    public void planWithoutRecipeExcluded() {
        Long storageId = newStorage("nr-norecipe-");
        ResourceResponse product = fixture.createResource("NR-NOP-");
        trackPlan(fixture.createCurrentMonthPlan(storageId, product.getId(), 10));

        PlanNeededResourcesResponse body = fixture.requestNeeded(
                UserRole.ADMIN, storageId, fixture.currentMonth());
        assertThat(body.getNeededResources()).isEmpty();
    }

    @Test(priority = 80)
    @TestCaseId("TC-PLN-NR-015")
    @Story("Alternative groups")
    @Severity(SeverityLevel.NORMAL)
    @Description("Alt-group: у потребу входить лише default alternative.")
    public void alternativeGroupUsesDefaultOnly() {
        Long storageId = newStorage("nr-alt-");
        TechnologicalMapResponse map = fixture.createAltGroupMap(storageId, 5.0, 7.0);
        maps.add(new CleanupMap(map, storageId));
        Long productId = map.getOutput().getFirst().getResource().getId();
        Long defaultAltId = map.getGroups().getFirst().getAlternativeResources().stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsDefault()))
                .map(r -> r.getResource().getId())
                .findFirst()
                .orElseThrow();
        Long otherAltId = map.getGroups().getFirst().getAlternativeResources().stream()
                .filter(r -> !Boolean.TRUE.equals(r.getIsDefault()))
                .map(r -> r.getResource().getId())
                .findFirst()
                .orElseThrow();
        Long fixedId = map.getInput().getFirst().getResource().getId();
        trackPlan(fixture.createCurrentMonthPlan(storageId, productId, 10));

        PlanNeededResourcesResponse body = fixture.requestNeeded(
                UserRole.ADMIN, storageId, fixture.currentMonth());
        assertQty(requireById(body, defaultAltId).getNeeded(), 50);
        assertQty(requireById(body, fixedId).getNeeded(), 10);
        assertThat(body.getNeededResources())
                .noneMatch(row -> row.getResource() != null && otherAltId.equals(row.getResource().getId()));
    }

    @Test(priority = 85)
    @TestCaseId("TC-PLN-NR-018")
    @Story("Explosion")
    @Severity(SeverityLevel.NORMAL)
    @Description("resourceIds лишає вибух лише обраних продуктів плану.")
    public void resourceIdsFilterExplodesOnlySelectedPlanProducts() {
        Long storageId = newStorage("nr-rid-");
        ResourceResponse raw = fixture.createResource("NR-RID-RAW-");
        ResourceResponse productA = fixture.createResource("NR-RID-A-");
        ResourceResponse productB = fixture.createResource("NR-RID-B-");
        TechnologicalMapResponse mapA = fixture.techMaps().createTechMapWithRequest(
                UserRole.ADMIN,
                com.erp.data.factories.tech_map.TechnologicalMapDataFactory.createProductionMapWithStorages(
                        "NR-RID-A",
                        List.of(new ResourceUsageRequest(raw.getId(), 1.0)),
                        List.of(new ResourceUsageRequest(productA.getId(), 1.0)),
                        Set.of(storageId)).build());
        TechnologicalMapResponse mapB = fixture.techMaps().createTechMapWithRequest(
                UserRole.ADMIN,
                com.erp.data.factories.tech_map.TechnologicalMapDataFactory.createProductionMapWithStorages(
                        "NR-RID-B",
                        List.of(new ResourceUsageRequest(raw.getId(), 1.0)),
                        List.of(new ResourceUsageRequest(productB.getId(), 1.0)),
                        Set.of(storageId)).build());
        maps.add(new CleanupMap(mapA, storageId));
        maps.add(new CleanupMap(mapB, storageId));
        trackPlan(fixture.createCurrentMonthPlan(storageId, List.of(
                new ResourceUsageRequest(productA.getId(), 10.0),
                new ResourceUsageRequest(productB.getId(), 4.0))));

        PlanNeededResourcesResponse filtered = fixture.requestNeeded(
                UserRole.ADMIN,
                storageId,
                fixture.currentMonth().toBuilder().resourceIds(List.of(productA.getId())).build());
        NeededResourceResponse rawRow = fixture.requireRow(filtered, raw.getName());
        assertQty(rawRow.getNeeded(), 10);
        assertThat(rawRow.getSources()).isNotEmpty();
        assertThat(sourceNames(rawRow.getSources().getFirst())).contains(productA.getName());
        assertThat(filtered.getNeededResources())
                .noneMatch(row -> row.getSources().stream()
                        .flatMap(source -> source.getPath().stream())
                        .anyMatch(step -> productB.getName().equals(step.getName())));
    }

    @Test(priority = 88)
    @TestCaseId("TC-PLN-NR-019")
    @Story("Explosion")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Наскрізний контракт AC-02: канонічний ланцюг + alt-group, чотири POST на одному складі.")
    public void ac02ContractWalkthrough() {
        Long storageId = newStorage("nr-ac02-");
        PlanNeededResourcesFixture.Chain chain = createChain(storageId);
        TechnologicalMapResponse altMap = fixture.createAltGroupMap(storageId, 5.0, 7.0);
        maps.add(new CleanupMap(altMap, storageId));
        Long altProductId = altMap.getOutput().getFirst().getResource().getId();
        Long defaultAltId = altMap.getGroups().getFirst().getAlternativeResources().stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsDefault()))
                .map(r -> r.getResource().getId())
                .findFirst()
                .orElseThrow();
        Long otherAltId = altMap.getGroups().getFirst().getAlternativeResources().stream()
                .filter(r -> !Boolean.TRUE.equals(r.getIsDefault()))
                .map(r -> r.getResource().getId())
                .findFirst()
                .orElseThrow();
        Long altFixedId = altMap.getInput().getFirst().getResource().getId();

        trackPlan(fixture.createCurrentMonthPlan(storageId, List.of(
                new ResourceUsageRequest(chain.getProduct().getId(), PlanNeededResourcesFixture.PLAN_GOAL),
                new ResourceUsageRequest(altProductId, 10.0))));
        trackProduction(storageId, fixture.seedCanonicalProductionAndStock(storageId, chain));

        PlanNeededResourcesResponse defaults = fixture.requestNeeded(
                UserRole.ADMIN, storageId, fixture.currentMonth());
        assertThat(fixture.hasRow(defaults, chain.getProduct().getName())).isFalse();
        NeededResourceResponse intermediate = fixture.requireRow(defaults, chain.getIntermediate().getName());
        assertQty(intermediate.getNeeded(), 200);
        assertQty(intermediate.getInStock(), 40);
        assertQty(intermediate.getShortage(), 160);
        assertThat(intermediate.isProduced()).isTrue();
        assertThat(intermediate.getResource().getCategory()).isNotNull();
        NeededResourceResponse raw = fixture.requireRow(defaults, chain.getRaw().getName());
        assertQty(raw.getNeeded(), 480);
        assertQty(raw.getInStock(), 50);
        assertQty(raw.getShortage(), 430);
        assertThat(raw.isProduced()).isFalse();
        assertThat(raw.getResource().getCategory()).isNotNull();
        assertQty(requireById(defaults, defaultAltId).getNeeded(), 50);
        assertQty(requireById(defaults, altFixedId).getNeeded(), 10);
        assertThat(defaults.getNeededResources())
                .noneMatch(row -> row.getResource() != null && otherAltId.equals(row.getResource().getId()));

        PlanNeededResourcesResponse produced = fixture.requestNeeded(
                UserRole.ADMIN,
                storageId,
                fixture.currentMonth().toBuilder().includeProduced(true).includeStock(true).build());
        assertQty(fixture.requireRow(produced, chain.getIntermediate().getName()).getNeeded(), 140);
        assertQty(fixture.requireRow(produced, chain.getIntermediate().getName()).getShortage(), 100);
        assertQty(fixture.requireRow(produced, chain.getRaw().getName()).getNeeded(), 300);
        assertQty(fixture.requireRow(produced, chain.getRaw().getName()).getShortage(), 250);

        PlanNeededResourcesResponse gross = fixture.requestNeeded(
                UserRole.ADMIN,
                storageId,
                fixture.currentMonth().toBuilder().includeStock(false).build());
        assertQty(fixture.requireRow(gross, chain.getIntermediate().getName()).getNeeded(), 200);
        assertQty(fixture.requireRow(gross, chain.getIntermediate().getName()).getInStock(), 40);
        assertQty(fixture.requireRow(gross, chain.getIntermediate().getName()).getShortage(), 200);
        assertQty(fixture.requireRow(gross, chain.getRaw().getName()).getNeeded(), 600);
        assertQty(fixture.requireRow(gross, chain.getRaw().getName()).getShortage(), 600);

        PlanNeededResourcesResponse filtered = fixture.requestNeeded(
                UserRole.ADMIN,
                storageId,
                fixture.currentMonth().toBuilder().resourceIds(List.of(chain.getProduct().getId())).build());
        assertThat(filtered.getNeededResources()).hasSize(2);
        assertThat(fixture.hasRow(filtered, chain.getIntermediate().getName())).isTrue();
        assertThat(fixture.hasRow(filtered, chain.getRaw().getName())).isTrue();
        assertThat(filtered.getNeededResources())
                .noneMatch(row -> row.getResource() != null
                        && (defaultAltId.equals(row.getResource().getId())
                        || altFixedId.equals(row.getResource().getId())
                        || otherAltId.equals(row.getResource().getId())));
    }

    @Test(priority = 90)
    @TestCaseId("TC-PLN-NR-030")
    @Story("Drill-down")
    @Severity(SeverityLevel.NORMAL)
    @Description("Прямий вхід: path з одного кроку — продукт плану.")
    public void directInputSourcePathIsPlanProduct() {
        IsolatedChain isolated = canonicalIsolatedChain();
        PlanNeededResourcesResponse body = fixture.requestNeeded(
                UserRole.ADMIN, isolated.storageId(), fixture.currentMonth());
        NeededResourceResponse intermediate = fixture.requireRow(body, isolated.chain().getIntermediate().getName());
        assertThat(intermediate.getSources()).hasSize(1);
        List<String> names = sourceNames(intermediate.getSources().getFirst());
        assertThat(names).containsExactly(isolated.chain().getProduct().getName());
        assertThat(sourceTechMaps(intermediate.getSources().getFirst()))
                .containsExactly(isolated.chain().getProductMap().getName());
        assertQty(intermediate.getSources().getFirst().getAmount(), intermediate.getNeeded());
    }

    @Test(priority = 100)
    @TestCaseId("TC-PLN-NR-031")
    @Story("Drill-down")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Сировина: path = [продукт, проміжний] з назвами техкарт.")
    public void multiLevelSourcePath() {
        IsolatedChain isolated = canonicalIsolatedChain();
        PlanNeededResourcesResponse body = fixture.requestNeeded(
                UserRole.ADMIN, isolated.storageId(), fixture.currentMonth());
        NeededResourceResponse raw = fixture.requireRow(body, isolated.chain().getRaw().getName());
        assertThat(raw.getSources()).hasSize(1);
        assertThat(sourceNames(raw.getSources().getFirst())).containsExactly(
                isolated.chain().getProduct().getName(),
                isolated.chain().getIntermediate().getName());
        assertThat(sourceTechMaps(raw.getSources().getFirst())).containsExactly(
                isolated.chain().getProductMap().getName(),
                isolated.chain().getIntermediateMap().getName());
    }

    @Test(priority = 110)
    @TestCaseId("TC-PLN-NR-032")
    @Story("Drill-down")
    @Severity(SeverityLevel.NORMAL)
    @Description("Один raw з двох виробів плану — кілька sources, сума = needed.")
    public void multiplePlanProductsShareRawSources() {
        Long storageId = newStorage("nr-share-");
        ResourceResponse raw = fixture.createResource("NR-SHR-RAW-");
        ResourceResponse productA = fixture.createResource("NR-SHR-A-");
        ResourceResponse productB = fixture.createResource("NR-SHR-B-");
        TechnologicalMapResponse mapA = fixture.techMaps().createTechMapWithRequest(
                UserRole.ADMIN,
                com.erp.data.factories.tech_map.TechnologicalMapDataFactory.createProductionMapWithStorages(
                        "NR-SHR-A",
                        List.of(new com.erp.models.request.ResourceUsageRequest(raw.getId(), 1.0)),
                        List.of(new com.erp.models.request.ResourceUsageRequest(productA.getId(), 1.0)),
                        Set.of(storageId)).build());
        TechnologicalMapResponse mapB = fixture.techMaps().createTechMapWithRequest(
                UserRole.ADMIN,
                com.erp.data.factories.tech_map.TechnologicalMapDataFactory.createProductionMapWithStorages(
                        "NR-SHR-B",
                        List.of(new com.erp.models.request.ResourceUsageRequest(raw.getId(), 1.0)),
                        List.of(new com.erp.models.request.ResourceUsageRequest(productB.getId(), 1.0)),
                        Set.of(storageId)).build());
        maps.add(new CleanupMap(mapA, storageId));
        maps.add(new CleanupMap(mapB, storageId));
        trackPlan(fixture.createCurrentMonthPlan(storageId, List.of(
                new ResourceUsageRequest(productA.getId(), 10.0),
                new ResourceUsageRequest(productB.getId(), 4.0))));

        PlanNeededResourcesResponse body = fixture.requestNeeded(
                UserRole.ADMIN, storageId, fixture.currentMonth());
        NeededResourceResponse rawRow = fixture.requireRow(body, raw.getName());
        assertThat(rawRow.getSources().size()).isGreaterThanOrEqualTo(2);
        double sourceSum = rawRow.getSources().stream().mapToDouble(NeededResourceSourceResponse::getAmount).sum();
        assertQty(sourceSum, rawRow.getNeeded());
    }

    @Test(priority = 120)
    @TestCaseId("TC-PLN-NR-040")
    @Story("Parent location")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Парент UNIT сумує цілі планів дочірніх складів.")
    public void parentSumsChildPlanGoals() {
        ParentTree tree = parentTreeWithPlans(100, 50);
        PlanNeededResourcesResponse body = fixture.requestNeeded(
                UserRole.ADMIN, tree.parentId(), fixture.currentMonth());
        NeededResourceResponse intermediate = fixture.requireRow(body, tree.chain().getIntermediate().getName());
        assertQty(intermediate.getNeeded(), 300);
    }

    @Test(priority = 130)
    @TestCaseId("TC-PLN-NR-041")
    @Story("Parent location")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Парент пулить залишки; спільні техкарти не подвоюють потребу.")
    public void parentPoolsStockWithoutDoubleCountingMaps() {
        ParentTree tree = parentTreeWithPlans(100, 50);
        fixture.seedStock(tree.childAId(), tree.chain().getIntermediate().getId(), 40);
        fixture.seedStock(tree.childAId(), tree.chain().getRaw().getId(), 50);
        fixture.seedStock(tree.childBId(), tree.chain().getRaw().getId(), 100);

        PlanNeededResourcesResponse body = fixture.requestNeeded(
                UserRole.ADMIN, tree.parentId(), fixture.currentMonth());
        NeededResourceResponse intermediate = fixture.requireRow(body, tree.chain().getIntermediate().getName());
        assertQty(intermediate.getNeeded(), 300);
        assertQty(intermediate.getInStock(), 40);
        assertQty(intermediate.getShortage(), 260);
        NeededResourceResponse raw = fixture.requireRow(body, tree.chain().getRaw().getName());
        assertQty(raw.getNeeded(), 780);
        assertQty(raw.getInStock(), 150);
        assertQty(raw.getShortage(), 630);
    }

    @Test(priority = 140)
    @TestCaseId("TC-PLN-NR-042")
    @Story("Parent location")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Листовий склад бачить лише власний план і залишки.")
    public void leafStorageSeesOnlyOwnPlanAndStock() {
        ParentTree tree = parentTreeWithPlans(100, 50);
        fixture.seedStock(tree.childAId(), tree.chain().getIntermediate().getId(), 40);
        fixture.seedStock(tree.childBId(), tree.chain().getIntermediate().getId(), 10);

        PlanNeededResourcesResponse childA = fixture.requestNeeded(
                UserRole.ADMIN, tree.childAId(), fixture.currentMonth());
        PlanNeededResourcesResponse childB = fixture.requestNeeded(
                UserRole.ADMIN, tree.childBId(), fixture.currentMonth());
        assertQty(fixture.requireRow(childA, tree.chain().getIntermediate().getName()).getNeeded(), 200);
        assertQty(fixture.requireRow(childA, tree.chain().getIntermediate().getName()).getInStock(), 40);
        assertQty(fixture.requireRow(childB, tree.chain().getIntermediate().getName()).getNeeded(), 100);
        assertQty(fixture.requireRow(childB, tree.chain().getIntermediate().getName()).getInStock(), 10);
    }

    @Test(priority = 150)
    @TestCaseId("TC-PLN-NR-043")
    @Story("Parent location")
    @Severity(SeverityLevel.NORMAL)
    @Description("На паренті залишки child B зменшують потребу від плану child A.")
    public void parentNetsStockAcrossChildren() {
        ParentTree tree = parentTreeWithPlans(100, 0);
        fixture.seedStock(tree.childBId(), tree.chain().getRaw().getId(), 50);

        PlanNeededResourcesResponse childA = fixture.requestNeeded(
                UserRole.ADMIN, tree.childAId(), fixture.currentMonth());
        PlanNeededResourcesResponse parent = fixture.requestNeeded(
                UserRole.ADMIN, tree.parentId(), fixture.currentMonth());
        NeededResourceResponse rawOnA = fixture.requireRow(childA, tree.chain().getRaw().getName());
        NeededResourceResponse rawOnParent = fixture.requireRow(parent, tree.chain().getRaw().getName());
        assertQty(rawOnA.getInStock(), 0);
        assertQty(rawOnParent.getInStock(), 50);
        assertThat(rawOnParent.getShortage()).isLessThan(rawOnA.getShortage() + EPS);
    }

    @Test(priority = 160)
    @TestCaseId("TC-PLN-NR-045")
    @Story("Parent location")
    @Severity(SeverityLevel.NORMAL)
    @Description("CREW/FLY_POINT діти не входять у пул потреби парента.")
    public void crewAndFlyPointExcludedFromParentPool() {
        ParentTree tree = parentTreeWithPlans(100, 0);
        StorageResponse crew = storageFixture.createCrewStorage(tree.parentId(), "nr-crew-");
        storagesNewestFirst.add(0, crew.getId());
        StorageResponse fly = storageFixture.createFlyPointStorage(tree.parentId(), "nr-fly-");
        storagesNewestFirst.add(0, fly.getId());
        fixture.seedStock(crew.getId(), tree.chain().getRaw().getId(), 999);
        fixture.seedStock(fly.getId(), tree.chain().getRaw().getId(), 888);

        PlanNeededResourcesResponse parent = fixture.requestNeeded(
                UserRole.ADMIN, tree.parentId(), fixture.currentMonth());
        NeededResourceResponse raw = fixture.requireRow(parent, tree.chain().getRaw().getName());
        assertThat(raw.getInStock()).isLessThan(100);
    }

    @Test(priority = 170)
    @TestCaseId("TC-PLN-NR-046")
    @Story("Parent location")
    @Severity(SeverityLevel.CRITICAL)
    @Description("OWNER_2 не читає парент чужого піддерева; ADMIN читає.")
    public void parentForbiddenForForeignOwner() {
        ParentTree tree = parentTreeWithPlans(10, 0);
        Response forbidden = fixture.requestNeededRaw(UserRole.OWNER_2, tree.parentId(), fixture.currentMonth());
        assertThat(forbidden.statusCode()).isEqualTo(403);
        PlanNeededResourcesResponse allowed = fixture.requestNeeded(
                UserRole.ADMIN, tree.parentId(), fixture.currentMonth());
        assertThat(allowed.getNeededResources()).isNotEmpty();
    }

    @Test(priority = 180)
    @TestCaseId("TC-PLN-NR-047")
    @Story("Parent location")
    @Severity(SeverityLevel.NORMAL)
    @Description("Grandparent UNIT рекурсивно пулить внучатий склад.")
    public void grandparentPoolsGrandchild() {
        StorageResponse grand = storageFixture.createUnitStorage(
                storageFixture.resolveParentUnit().getId(), "nr-grand-");
        StorageResponse mid = storageFixture.createUnitStorage(grand.getId(), "nr-mid-");
        StorageResponse leaf = storageFixture.createChildStorage(mid.getId(), "nr-leaf-");
        storagesNewestFirst.add(leaf.getId());
        storagesNewestFirst.add(mid.getId());
        storagesNewestFirst.add(grand.getId());

        PlanNeededResourcesFixture.Chain chain = fixture.createTwoLevelChain(
                Set.of(leaf.getId()),
                testContext.get(com.erp.test_context.ContextKey.SHARED_RESOURCE_CATEGORY_ID),
                testContext.get(com.erp.test_context.ContextKey.SHARED_RESOURCE_CATEGORY_ID));
        maps.add(new CleanupMap(chain.getProductMap(), leaf.getId()));
        maps.add(new CleanupMap(chain.getIntermediateMap(), leaf.getId()));
        trackPlan(fixture.createCurrentMonthPlan(leaf.getId(), chain.getProduct().getId(), 10));

        PlanNeededResourcesResponse grandBody = fixture.requestNeeded(
                UserRole.ADMIN, grand.getId(), fixture.currentMonth());
        PlanNeededResourcesResponse midBody = fixture.requestNeeded(
                UserRole.ADMIN, mid.getId(), fixture.currentMonth());
        assertQty(fixture.requireRow(grandBody, chain.getIntermediate().getName()).getNeeded(), 20);
        assertQty(fixture.requireRow(midBody, chain.getIntermediate().getName()).getNeeded(), 20);
    }

    private IsolatedChain canonicalIsolatedChain() {
        Long storageId = newStorage("nr-can-");
        PlanNeededResourcesFixture.Chain chain = createChain(storageId);
        trackProduction(storageId, fixture.seedCanonicalPlanProductionAndStock(storageId, chain));
        fixture.techMaps().getLocationPlans(storageId).stream()
                .filter(p -> YearMonth.now().getMonthValue() == p.getMonth()
                        && YearMonth.now().getYear() == p.getYear())
                .forEach(this::trackPlan);
        return new IsolatedChain(storageId, chain);
    }

    private PlanNeededResourcesFixture.Chain createChain(Long storageId) {
        PlanNeededResourcesFixture.Chain chain = fixture.createTwoLevelChain(storageId);
        maps.add(new CleanupMap(chain.getProductMap(), storageId));
        maps.add(new CleanupMap(chain.getIntermediateMap(), storageId));
        return chain;
    }

    private ParentTree parentTreeWithPlans(double planA, double planB) {
        StorageResponse parent = storageFixture.createUnitStorage(
                storageFixture.resolveParentUnit().getId(), "nr-par-");
        StorageResponse childA = storageFixture.createChildStorage(parent.getId(), "nr-ca-");
        StorageResponse childB = storageFixture.createChildStorage(parent.getId(), "nr-cb-");
        storagesNewestFirst.add(childB.getId());
        storagesNewestFirst.add(childA.getId());
        storagesNewestFirst.add(parent.getId());

        PlanNeededResourcesFixture.Chain chain = fixture.createTwoLevelChain(
                Set.of(childA.getId(), childB.getId()),
                testContext.get(com.erp.test_context.ContextKey.SHARED_RESOURCE_CATEGORY_ID),
                testContext.get(com.erp.test_context.ContextKey.SHARED_RESOURCE_CATEGORY_ID));
        maps.add(new CleanupMap(chain.getProductMap(), childA.getId()));
        maps.add(new CleanupMap(chain.getIntermediateMap(), childA.getId()));
        if (planA > 0) {
            trackPlan(fixture.createCurrentMonthPlan(childA.getId(), chain.getProduct().getId(), planA));
        }
        if (planB > 0) {
            trackPlan(fixture.createCurrentMonthPlan(childB.getId(), chain.getProduct().getId(), planB));
        }
        return new ParentTree(parent.getId(), childA.getId(), childB.getId(), chain);
    }

    private Long newStorage(String prefix) {
        StorageResponse storage = storageFixture.createChildStorage(prefix);
        storagesNewestFirst.add(0, storage.getId());
        return storage.getId();
    }

    private void trackPlan(PlanResponse plan) {
        if (plan != null && plans.stream().noneMatch(p -> p.getId().equals(plan.getId()))) {
            plans.add(plan);
        }
    }

    private void trackProduction(Long storageId, com.erp.models.response.ManufacturingItemResponse production) {
        if (production != null) {
            productions.add(new CleanupProduction(production.getId(), storageId));
        }
    }

    private static NeededResourceResponse requireById(PlanNeededResourcesResponse body, Long resourceId) {
        return body.getNeededResources().stream()
                .filter(row -> row.getResource() != null && resourceId.equals(row.getResource().getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing resource id " + resourceId));
    }

    private static List<String> sourceNames(NeededResourceSourceResponse source) {
        return source.getPath().stream().map(NeededResourcePathStepResponse::getName).toList();
    }

    private static List<String> sourceTechMaps(NeededResourceSourceResponse source) {
        return source.getPath().stream().map(NeededResourcePathStepResponse::getTechMapName).toList();
    }

    private static void assertQty(Double actual, double expected) {
        assertThat(actual).isNotNull().isCloseTo(expected, within(EPS));
    }

    private record IsolatedChain(Long storageId, PlanNeededResourcesFixture.Chain chain) {
    }

    private record ParentTree(Long parentId, Long childAId, Long childBId, PlanNeededResourcesFixture.Chain chain) {
    }

    private record CleanupMap(TechnologicalMapResponse techMap, Long storageId) {
    }

    private record CleanupProduction(Long id, Long storageId) {
    }
}
