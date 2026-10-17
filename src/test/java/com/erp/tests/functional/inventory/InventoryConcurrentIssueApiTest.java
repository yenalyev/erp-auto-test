package com.erp.tests.functional.inventory;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.InventoryConcurrentIssueFixture;
import com.erp.models.request.InventoryRequest;
import com.erp.models.response.StorageItemResponse;
import com.erp.tests.functional.BaseFunctionalTest;
import io.qameta.allure.*;
import io.restassured.response.Response;
import org.testng.annotations.*;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Inventory")
@Feature("REQ-WMS-003 Concurrent issue")
public class InventoryConcurrentIssueApiTest extends BaseFunctionalTest {
    private InventoryConcurrentIssueFixture fixture;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void prepareConcurrentInventory() {
        fixture = new InventoryConcurrentIssueFixture(testContext, apiExecutor);
        fixture.prepare();
    }

    @BeforeMethod(alwaysRun = true)
    public void resetOwnedStock() { fixture.reset(); }

    @AfterMethod(alwaysRun = true)
    public void closeOwnedSession() { if (fixture != null) fixture.closeSession(); }

    @AfterClass(alwaysRun = true)
    public void cleanupOwnedData() { if (fixture != null) fixture.cleanup(); }

    @Test
    @TestCaseId("TC-INV-CONCURRENT-API-001")
    @Severity(SeverityLevel.CRITICAL)
    @Description("A:10→15 у формі; після snapshot видано B:7/20 і C:30/30. PUT зі старими версіями має повернути 412 атомарно.")
    public void staleFormDoesNotRestoreIssuedResourcesOrWriteHistory() {
        InventoryRequest stale = editedRequest();
        fixture.issueMixed();
        assertRejected(stale, List.of(fixture.partial.getId(), fixture.exhausted.getId()));
    }

    @Test
    @TestCaseId("TC-INV-CONCURRENT-API-002")
    @Severity(SeverityLevel.CRITICAL)
    public void acceptingCurrentIssuedAmountsSavesOnlyIntentionalDelta() {
        InventoryRequest stale = editedRequest();
        fixture.issueMixed();
        assertRejected(stale, List.of(fixture.partial.getId(), fixture.exhausted.getId()));
        String comment = fixture.marker + " accepted current";
        InventoryRequest resolved = fixture.request(fixture.snapshot(), Map.of(fixture.edited.getId(), 15.0), comment);
        fixture.inventory.conductInventory(fixture.source.getId(), UserRole.ADMIN, resolved);
        fixture.assertStock(15, 13, 0);
        fixture.assertOnlyEditedDiff(5, comment);
        assertThat(fixture.inventoryHistory().stream().filter(r -> comment.equals(r.getComment())).toList())
                .singleElement().satisfies(row -> {
                    assertThat(row.getResource().getId()).isEqualTo(fixture.edited.getId());
                    assertThat(row.getResourceOperationType()).isEqualTo("ADDED_INV");
                    assertThat(row.getAmount()).isEqualTo(5.0);
                });
    }

    @Test
    @TestCaseId("TC-INV-CONCURRENT-API-003")
    @Severity(SeverityLevel.CRITICAL)
    public void secondIssueAfterConflictResolutionRequiresAnotherResolution() {
        InventoryRequest stale = editedRequest();
        fixture.issueMixed();
        assertRejected(stale, List.of(fixture.partial.getId(), fixture.exhausted.getId()));
        InventoryRequest resolved = fixture.request(fixture.snapshot(), Map.of(fixture.edited.getId(), 15.0), fixture.marker);
        fixture.issueAgain();
        assertRejected(resolved, List.of(fixture.partial.getId()));
        fixture.assertStock(10, 11, 0);
        fixture.inventory.conductInventory(fixture.source.getId(), UserRole.ADMIN,
                fixture.request(fixture.snapshot(), Map.of(fixture.edited.getId(), 15.0), fixture.marker));
        fixture.assertStock(15, 11, 0);
        fixture.assertOnlyEditedDiff(5, fixture.marker);
    }

    @Test
    @TestCaseId("TC-INV-CONCURRENT-API-004")
    @Severity(SeverityLevel.CRITICAL)
    public void issueOfTheEditedResourceAlsoRequiresExplicitResolution() {
        InventoryRequest stale = editedRequest();
        fixture.trackIssue(fixture.relocations.createSend(UserRole.ADMIN, fixture.source.getId(),
                fixture.recipient.getId(), fixture.edited.getId(), 4.0).getId());
        assertRejected(stale, List.of(fixture.edited.getId()));
        fixture.assertStock(6, 20, 30);
        // User explicitly keeps 15 after seeing the current quantity 6; delta must be +9.
        fixture.inventory.conductInventory(fixture.source.getId(), UserRole.ADMIN,
                fixture.request(fixture.snapshot(), Map.of(fixture.edited.getId(), 15.0), fixture.marker));
        fixture.assertStock(15, 20, 30);
        fixture.assertOnlyEditedDiff(9, fixture.marker);
    }

    private InventoryRequest editedRequest() {
        return fixture.request(fixture.snapshot(), Map.of(fixture.edited.getId(), 15.0), fixture.marker);
    }

    private void assertRejected(InventoryRequest request, List<Long> expectedConflicts) {
        var before = fixture.stock();
        var history = fixture.inventoryHistory();
        Response response = fixture.inventory.conductInventoryRaw(fixture.source.getId(), UserRole.ADMIN, request);
        assertThat(response.statusCode()).as("Stale inventory PUT: %s", response.asString()).isEqualTo(412);
        assertThat(response.jsonPath().getList("modifiedResourceIds", Long.class))
                .containsExactlyInAnyOrderElementsOf(expectedConflicts);
        List<StorageItemResponse> snapshot = response.jsonPath().getList("snapshot", StorageItemResponse.class);
        assertThat(snapshot).isNotEmpty();
        for (Long id : expectedConflicts) {
            var current = snapshot.stream().filter(i -> i.getResource().getId().equals(id)).findFirst();
            // A fully issued resource may be absent; absence represents zero, never its stale amount.
            assertThat(current.map(StorageItemResponse::getAmount).orElse(0.0))
                    .isEqualTo(before.getOrDefault(id, 0.0));
            current.ifPresent(item -> assertThat(item.getVersion()).isNotNull());
        }
        assertThat(fixture.stock()).isEqualTo(before);
        assertThat(fixture.inventoryHistory()).usingRecursiveFieldByFieldElementComparator().containsExactlyElementsOf(history);
        var process = fixture.currentProcess();
        assertThat(process.getState()).isEqualTo("OPEN");
        assertThat(process.getClosedAt()).isNull();
        assertThat(process.getCompletedComment()).isNull();
        assertThat(process.getCompletedBy()).isNull();
        assertThat(process.getDiff() == null || process.getDiff().isNull() || process.getDiff().isEmpty()).isTrue();
    }
}
