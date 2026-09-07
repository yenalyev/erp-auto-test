package com.erp.tests.functional.alert;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UnitType;
import com.erp.enums.UserRole;
import com.erp.fixtures.AlertFixture;
import com.erp.models.response.ResourceAlertResponse;
import com.erp.models.response.StorageAlertResponse;
import com.erp.models.response.StorageItemResponse;
import com.erp.models.response.StorageResponse;
import com.erp.tests.functional.storage.CrewApiTestBase;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.PollUtils;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.qameta.allure.Allure;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@Epic("Inventory")
@Feature("REQ-ALERT")
public class AlertStorageTypeApiTest extends CrewApiTestBase {

    private AlertFixture alertFixture;
    private Long parentId;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "setupCrewApiBase")
    public void setupAlertTypeTests() {
        alertFixture = new AlertFixture(testContext, apiExecutor);
        resourceFixture.prepareContext();
        StorageResponse member = storageFixture.getById(UserRole.ADMIN, ConfigProvider.getOwner1StorageId());
        parentId = member.getParent() != null ? member.getParent().getId() : member.getId();
    }

    @DataProvider(name = "inventoryStorageTypes")
    public Object[][] inventoryStorageTypes() {
        return new Object[][]{
                {UnitType.STORAGE},
                {UnitType.UNIT},
                {UnitType.PRODUCTION},
                {UnitType.CREW},
                {UnitType.FLY_POINT}
        };
    }

    @Test(priority = 10, dataProvider = "inventoryStorageTypes")
    @TestCaseId("TC-ALERT-004")
    @Story("Alert CRUD for every storage type")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Регресія: на UNIT GET /alerts/storage/{id} / UI /alerts/{id} не показують пороги.
            Для кожного Storage.type — POST alert, GET by storage, GET inventory:
            рядок з порогом (weight=100) вище рядка без алерту (weight=0), навіть якщо
            за назвою він пізніше. SUPPLIER не входить.
            """)
    public void alertPersistsForEveryInventoryStorageType(UnitType type) {
        Allure.parameter("storageType", type.name());
        AlertFixture.TypedAlertSeed seed = alertFixture.seedAlertForType(
                storageFixture, resourceFixture, inventoryFixture,
                parentId, type, AlertFixture.DEFAULT_LIMIT);

        assertThat(seed.alert().getId()).as("POST alert id").isNotNull();
        assertThat(UnitType.valueOf(seed.storage().getType())).isEqualTo(type);

        StorageAlertResponse byStorage = alertFixture.getByStorageId(seed.storage().getId(), UserRole.ADMIN);
        assertThat(byStorage)
                .as("GET alerts by storage for type %s must not be empty", type)
                .isNotNull();
        assertThat(byStorage.getId()).isEqualTo(seed.alert().getId());
        assertThat(byStorage.getResourceAlerts())
                .as("resourceAlerts for type %s", type)
                .isNotEmpty();

        ResourceAlertResponse row = byStorage.getResourceAlerts().stream()
                .filter(alert -> alert.getResource() != null
                        && seed.resource().getId().equals(alert.getResource().getId()))
                .findFirst()
                .orElse(null);
        assertThat(row)
                .as("поріг для ресурсу %s на type=%s", seed.resource().getId(), type)
                .isNotNull();
        assertThat(row.getValue())
                .isCloseTo(BigDecimal.valueOf(AlertFixture.DEFAULT_LIMIT), within(new BigDecimal("0.01")));

        StorageItemResponse item = PollUtils.waitUntil(
                () -> inventoryFixture.findItemIncludingZero(
                        seed.storage().getId(), seed.alerted().getId(), UserRole.ADMIN),
                found -> found != null && Integer.valueOf(AlertFixture.RED_WEIGHT).equals(found.getWeight()),
                20_000,
                "inventory weight=" + AlertFixture.RED_WEIGHT + " for type " + type);
        assertThat(item.getAlertLimit())
                .as("alertLimit on /inventory for type %s", type)
                .isCloseTo(AlertFixture.DEFAULT_LIMIT, within(0.01));

        Set<Long> ours = Set.of(seed.alerted().getId(), seed.plain().getId());
        List<Long> ordered = inventoryFixture.listItems(seed.storage().getId(), UserRole.ADMIN).stream()
                .filter(stock -> stock.getResource() != null && ours.contains(stock.getResource().getId()))
                .map(stock -> stock.getResource().getId())
                .toList();
        assertThat(ordered)
                .as("спочатку рядки з алертом, потім решта (type=%s)", type)
                .containsExactly(seed.alerted().getId(), seed.plain().getId());
    }
}
