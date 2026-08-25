package com.erp.tests.functional.shift;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.fixtures.ShiftFixture;
import com.erp.models.request.ShiftRequest;
import com.erp.models.response.ShiftResponse;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Master Data")
@Feature("Shifts")
public class ShiftCrudApiTest extends BaseFunctionalTest {

    private ShiftFixture fixture;
    private long storageId;
    private Long createdShiftId;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void setupShiftTests() {
        fixture = new ShiftFixture(testContext, apiExecutor);
        storageId = ConfigProvider.getOwner1StorageId();
    }

    @AfterMethod(alwaysRun = true)
    public void deleteCreatedShift() {
        if (createdShiftId != null) {
            fixture.deleteRaw(UserRole.ADMIN, createdShiftId, storageId);
            createdShiftId = null;
        }
    }

    @Test(priority = 10)
    @TestCaseId("TC-SHIFT-001")
    @Story("Shift CRUD")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Admin GET/POST/PUT/DELETE shifts на локації.")
    public void adminCanCreateUpdateDeleteShift() {
        ShiftRequest create = fixture.uniqueRequest("erp-shift-");
        ShiftResponse created = fixture.create(UserRole.ADMIN, storageId, create);
        createdShiftId = created.getId();
        assertThat(created.getName()).isEqualTo(create.getName());

        List<ShiftResponse> listed = fixture.getAll(UserRole.ADMIN, storageId);
        assertThat(listed.stream().anyMatch(s -> created.getId().equals(s.getId()))).isTrue();

        ShiftRequest update = create.toBuilder().description("updated").workerQty(6).build();
        ShiftResponse updated = fixture.update(UserRole.ADMIN, created.getId(), storageId, update);
        assertThat(updated.getWorkerQty()).isEqualTo(6);

        Response deleted = fixture.deleteRaw(UserRole.ADMIN, created.getId(), storageId);
        assertThat(deleted.statusCode()).isIn(200, 204);
        createdShiftId = null;
    }

    @Test(priority = 20)
    @TestCaseId("TC-SHIFT-002")
    @Story("Shift RBAC")
    @Severity(SeverityLevel.CRITICAL)
    public void outsiderCannotCreateShiftOnOwner1() {
        ShiftRequest request = fixture.uniqueRequest("erp-shift-x-");
        Response denied = apiExecutor.execute(
                ApiEndpointDefinition.SHIFT_POST_CREATE,
                UserRole.OWNER_2,
                request,
                String.valueOf(storageId));
        assertThat(denied.statusCode()).isIn(403, 400);
    }
}
