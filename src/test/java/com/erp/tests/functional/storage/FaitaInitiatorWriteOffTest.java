package com.erp.tests.functional.storage;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.fixtures.CrewRegionFixture.CrewRegionScenario;
import com.erp.fixtures.FaitaResourceFixture;
import com.erp.models.response.ResourceResponse;
import com.erp.utils.helpers.ProductionStockAssertions;
import com.erp.utils.helpers.RelocationStockAssertions;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.restassured.response.Response;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deterministic API/DB checks of the completion rule. The DB fixture creates a FLIGHT
 * journal entry; PUT complete exercises the same service as the scheduled process.
 * These tests do not claim to verify Fight import or scheduler timing.
 */
@Epic("Inventory")
@Feature("FAITA initiator write-off")
public class FaitaInitiatorWriteOffTest extends CrewApiTestBase {
    private static final double AMOUNT = 3.0;
    private final List<Long> reconciliationIds = new ArrayList<>();
    private FaitaResourceFixture faitaFixture;
    private CrewRegionScenario scenario;
    private ResourceResponse externalResource;
    private ResourceResponse inHouseResource;
    private String externalId;
    private String inHouseId;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "setupCrewApiBase")
    public void setUp() {
        if (getDbHelper() == null) {
            throw new SkipException("Потрібен -Duse.database=true для FLIGHT journal fixture");
        }
        faitaFixture = new FaitaResourceFixture(testContext, apiExecutor);
        faitaFixture.probeAvailable();
        storageFixture.prepareContext();
        resourceFixture.fetchSharedUnit(3);
        resourceFixture.fetchSharedResourceCategory();
        externalResource = resourceFixture.createUniqueResourceWithSupplier("init-ext-", "Інший постачальник");
        inHouseResource = resourceFixture.createUniqueResourceWithSupplier("init-pm-", "ПМ Цукрарня");
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        externalId = "i-" + suffix + "1";
        inHouseId = "i-" + suffix + "2";
        reconciliationIds.addAll(faitaFixture.createFlightReconciliation(
                externalId, "Зовнішній ініціатор " + suffix, externalResource.getId()));
        reconciliationIds.addAll(faitaFixture.createFlightReconciliation(
                inHouseId, "Власний ініціатор " + suffix, inHouseResource.getId()));
    }

    @BeforeMethod(alwaysRun = true)
    public void setUpFlyPoint() {
        scenario = crewFixture.prepareAttachedCrewScenario("faita-init-");
        refreshRoleSessions(UserRole.ADMIN, UserRole.OWNER_1);
    }

    @AfterClass(alwaysRun = true)
    public void tearDown() {
        if (faitaFixture != null) {
            faitaFixture.deleteReconciliationsQuietly(reconciliationIds);
        }
    }

    @TestCaseId("TC-FAITA-INIT-001")
    @Description("Зіставлений ініціатор іншого постачальника: COMPLETED без залишку на точці")
    @Severity(SeverityLevel.CRITICAL)
    @Test
    public void externalSupplierCompletesWithoutStock() {
        long flyPointId = scenario.flyPoint().getId();
        ProductionStockAssertions.StockSnapshot before = stock(flyPointId, externalResource.getId());
        assertThat(before.amountOf(externalResource.getId())).isZero();
        long id = seedPending(externalId, externalResource);

        complete(id);

        assertThat(status(id)).isEqualTo("COMPLETED");
        RelocationStockAssertions.assertUnchanged(before, stock(flyPointId, externalResource.getId()),
                flyPointId, externalResource.getId(), "Зовнішній ініціатор не списується з точки");
    }

    @TestCaseId("TC-FAITA-INIT-003")
    @Description("Зіставлений ініціатор іншого постачальника: позитивний залишок точки не змінюється")
    @Severity(SeverityLevel.CRITICAL)
    @Test
    public void externalSupplierDoesNotDebitExistingStock() {
        long flyPointId = scenario.flyPoint().getId();
        relocationFixture.ensureStock(owner1StorageId, externalResource.getId(), 20.0);
        relocationFixture.createSendAndFinishBySender(UserRole.OWNER_1,
                scenario.memberStorageId(), scenario.crew().getId(), externalResource.getId(), 10.0);
        ProductionStockAssertions.StockSnapshot before = stock(flyPointId, externalResource.getId());
        assertThat(before.amountOf(externalResource.getId())).isGreaterThanOrEqualTo(AMOUNT);
        long id = seedPending(externalId, externalResource);

        complete(id);

        assertThat(status(id)).isEqualTo("COMPLETED");
        RelocationStockAssertions.assertUnchanged(before, stock(flyPointId, externalResource.getId()),
                flyPointId, externalResource.getId(), "Наявний залишок зовнішнього ініціатора не списується");
    }

    @TestCaseId("TC-FAITA-INIT-002")
    @Description("Зіставлений ініціатор ПМ Цукрарня: звичайне списання із залишку точки")
    @Severity(SeverityLevel.CRITICAL)
    @Test
    public void inHouseSupplierDebitsFlyPoint() {
        long flyPointId = scenario.flyPoint().getId();
        relocationFixture.ensureStock(owner1StorageId, inHouseResource.getId(), 20.0);
        relocationFixture.createSendAndFinishBySender(UserRole.OWNER_1,
                scenario.memberStorageId(), scenario.crew().getId(), inHouseResource.getId(), 10.0);
        ProductionStockAssertions.StockSnapshot before = stock(flyPointId, inHouseResource.getId());
        assertThat(before.amountOf(inHouseResource.getId())).isGreaterThanOrEqualTo(AMOUNT);
        long id = seedPending(inHouseId, inHouseResource);

        complete(id);

        assertThat(status(id)).isEqualTo("COMPLETED");
        RelocationStockAssertions.assertDebitedFromSender(before,
                stock(flyPointId, inHouseResource.getId()), flyPointId, inHouseResource.getId(),
                AMOUNT, "Ініціатор ПМ Цукрарня списується з точки");
    }

    private ProductionStockAssertions.StockSnapshot stock(long storageId, long resourceId) {
        return RelocationStockAssertions.capture(apiExecutor, storageId, UserRole.ADMIN,
                Set.of(resourceId), "initiator stock");
    }

    private void complete(long id) {
        Response response = apiExecutor.execute(ApiEndpointDefinition.INVENTORY_WRITE_OFF_PUT_COMPLETE,
                UserRole.ADMIN, Map.of("writeOffIdentifiers", List.of(id)));
        assertThat(response.statusCode()).as("Complete write-off id=%s: %s", id,
                response.getBody().asString()).isEqualTo(200);
    }

    private long seedPending(String externalResourceId, ResourceResponse resource) {
        String sql = """
                INSERT INTO storage_item_write_off
                    (date_time, storage_id, fly_point_storage_id, resources,
                     external_resource_id, external_resource_name, amount, source,
                     operation_comment, status, source_id)
                VALUES (?, ?, ?, ?::jsonb, ?, ?, ?, 'FLIGHT', ?, 'PENDING', ?)
                RETURNING id
                """;
        try (PreparedStatement ps = getDbHelper().getConnection().prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.from(Instant.now()));
            ps.setLong(2, scenario.crew().getId());
            ps.setLong(3, scenario.flyPoint().getId());
            ps.setString(4, "[{\"id\":" + resource.getId() + ",\"name\":"
                    + new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(resource.getName()) + "}]");
            ps.setString(5, externalResourceId);
            ps.setString(6, resource.getName());
            ps.setBigDecimal(7, java.math.BigDecimal.valueOf(AMOUNT));
            ps.setString(8, "erp-auto-test initiator supplier rule");
            ps.setString(9, "erp-auto-" + UUID.randomUUID());
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return rs.getLong(1);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Cannot seed initiator write-off", e);
        }
    }

    private String status(long id) {
        try (PreparedStatement ps = getDbHelper().getConnection().prepareStatement(
                "SELECT status FROM storage_item_write_off WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return rs.getString(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot read write-off status id=" + id, e);
        }
    }
}
