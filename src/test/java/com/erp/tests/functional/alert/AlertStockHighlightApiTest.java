package com.erp.tests.functional.alert;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.AlertFixture;
import com.erp.fixtures.InventoryFixture;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.models.response.StorageItemResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.tests.functional.storage.StorageApiTestBase;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@Epic("Inventory")
@Feature("REQ-ALERT")
public class AlertStockHighlightApiTest extends StorageApiTestBase {

    private AlertFixture alertFixture;
    private InventoryFixture inventoryFixture;
    private RelocationFixture relocationFixture;
    private ResourceFixture resourceFixture;

    @BeforeClass(alwaysRun = true)
    public void setupAlertHighlight() {
        alertFixture = new AlertFixture(testContext, apiExecutor);
        inventoryFixture = new InventoryFixture(testContext, apiExecutor);
        relocationFixture = new RelocationFixture(testContext, apiExecutor);
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        relocationFixture.prepareContext();
        resourceFixture.prepareContext();
    }

    @Test(priority = 10)
    @TestCaseId("TC-ALERT-002")
    @Story("Weight thresholds")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Ізольований склад + POST /alerts з value=10 для red/yellow/green.
            GET /storages/{id}/inventory (як список залишків, size=500, дефолтний Pageable).
            Очікування StorageItemService.calculateWeight:
            amount≤0 → 100, 0<amount≤limit → 60, amount>limit → 40, без порогу → 0.
            """)
    public void weightThresholdsAssignRedYellowGreen() {
        AlertFixture.StockHighlightSeed seed = seedScenario();

        StorageItemResponse red = requireRow(seed.storage().getId(), seed.red().getId());
        StorageItemResponse yellow = requireRow(seed.storage().getId(), seed.yellow().getId());
        StorageItemResponse green = requireRow(seed.storage().getId(), seed.green().getId());
        StorageItemResponse plain = requireRow(seed.storage().getId(), seed.plain().getId());

        assertThat(red.getWeight()).as("red: amount≤0").isEqualTo(AlertFixture.RED_WEIGHT);
        assertThat(red.getAlertLimit()).isCloseTo(AlertFixture.DEFAULT_LIMIT, within(0.01));

        assertThat(yellow.getWeight()).as("yellow: amount≤limit").isEqualTo(AlertFixture.YELLOW_WEIGHT);
        assertThat(yellow.getAmount()).isLessThanOrEqualTo(AlertFixture.DEFAULT_LIMIT);
        assertThat(yellow.getAlertLimit()).isCloseTo(AlertFixture.DEFAULT_LIMIT, within(0.01));

        assertThat(green.getWeight()).as("green: amount>limit").isEqualTo(AlertFixture.GREEN_WEIGHT);
        assertThat(green.getAmount()).isGreaterThan(AlertFixture.DEFAULT_LIMIT);
        assertThat(green.getAlertLimit()).isCloseTo(AlertFixture.DEFAULT_LIMIT, within(0.01));

        assertThat(plain.getWeight()).as("plain: немає порогу").isEqualTo(AlertFixture.ZERO_WEIGHT);
        assertThat(plain.getAlertLimit()).isNull();
    }

    @Test(priority = 20)
    @TestCaseId("TC-ALERT-003")
    @Story("Pin to top by weight")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Назви навмисно алфавітні: aaa-ok (green), bbb-plain, mmm-low (yellow), zzz-out (red).
            GET /storages/{id}/inventory без клієнтського sort — дефолт tk:
            @PageableDefault(sort = {weight, resource.name}, DESC).
            Очікування: red → yellow → green → plain, не aaa → bbb → mmm → zzz.
            """)
    public void alertedRowsPinnedAboveNameSort() {
        AlertFixture.StockHighlightSeed seed = seedScenario();
        long storageId = seed.storage().getId();
        Set<Long> ours = Set.of(
                seed.red().getId(), seed.yellow().getId(),
                seed.green().getId(), seed.plain().getId());

        List<Long> ordered = inventoryFixture.listItems(storageId, UserRole.ADMIN).stream()
                .filter(item -> item.getResource() != null && ours.contains(item.getResource().getId()))
                .map(item -> item.getResource().getId())
                .toList();

        assertThat(ordered)
                .as("weight DESC ігнорує алфавітний порядок назв")
                .containsExactly(
                        seed.red().getId(),
                        seed.yellow().getId(),
                        seed.green().getId(),
                        seed.plain().getId());
    }

    @Test(priority = 30)
    @TestCaseId("TC-ALERT-006")
    @Story("Zero stock with alert in inventory hierarchy")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            GET /storages/inventory?parentStorageId=...&showZeroStock=false
            повертає нульовий ресурс з порогом, але не нульовий ресурс без порогу.
            За showZeroStock=true обидва ресурси присутні.
            """)
    public void alertedZeroStockIsIncludedWhenShowZeroStockIsFalse() {
        AlertFixture.StockHighlightSeed seed = seedScenario();
        long storageId = seed.storage().getId();
        ResourceResponse unalertedZero = resourceFixture.createUniqueResource(
                seed.searchToken() + "-zero-without-alert-");
        inventoryFixture.resetResourceStock(storageId, unalertedZero.getId(), 1.0, UserRole.ADMIN);
        inventoryFixture.depleteToZero(storageId, unalertedZero.getId());

        StorageItemResponse alertedItem = requireRow(storageId, seed.red().getId());
        StorageItemResponse unalertedItem = requireRow(storageId, unalertedZero.getId());
        assertThat(alertedItem.getAmount()).isZero();
        assertThat(alertedItem.getAlertLimit()).isCloseTo(AlertFixture.DEFAULT_LIMIT, within(0.01));
        assertThat(unalertedItem.getAmount()).isZero();
        assertThat(unalertedItem.getAlertLimit()).isNull();

        assertThat(inventoryFixture.hierarchyContainsResource(
                storageId, seed.red().getId(), UserRole.ADMIN, false))
                .as("нульовий ресурс з алертом у hierarchy при showZeroStock=false")
                .isTrue();
        assertThat(inventoryFixture.hierarchyContainsResource(
                storageId, unalertedZero.getId(), UserRole.ADMIN, false))
                .as("нульовий ресурс без алерту у hierarchy при showZeroStock=false")
                .isFalse();
        assertThat(inventoryFixture.hierarchyContainsResource(
                storageId, seed.red().getId(), UserRole.ADMIN, true)).isTrue();
        assertThat(inventoryFixture.hierarchyContainsResource(
                storageId, unalertedZero.getId(), UserRole.ADMIN, true)).isTrue();
    }

    private AlertFixture.StockHighlightSeed seedScenario() {
        return alertFixture.seedHighlightScenario(
                storageFixture, resourceFixture, relocationFixture, inventoryFixture);
    }

    private StorageItemResponse requireRow(long storageId, long resourceId) {
        StorageItemResponse item = inventoryFixture.findItemIncludingZero(
                storageId, resourceId, UserRole.ADMIN);
        assertThat(item)
                .as("рядок ресурсу %s на складі %s", resourceId, storageId)
                .isNotNull();
        return item;
    }
}
