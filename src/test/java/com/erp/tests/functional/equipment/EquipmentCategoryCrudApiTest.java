package com.erp.tests.functional.equipment;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.models.request.EquipmentCategoryRequest;
import com.erp.models.response.EquipmentCategoryResponse;
import com.erp.tests.functional.BaseFunctionalTest;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Equipment")
@Feature("Equipment categories")
public class EquipmentCategoryCrudApiTest extends BaseFunctionalTest {

    private Long createdId;

    @AfterMethod(alwaysRun = true)
    public void deleteCreated() {
        if (createdId != null) {
            apiExecutor.execute(ApiEndpointDefinition.EQUIPMENT_CATEGORY_DELETE, UserRole.ADMIN, null, createdId);
            createdId = null;
        }
    }

    @Test(priority = 10)
    @TestCaseId("TC-EQU-CAT-001")
    @Story("Equipment category CRUD")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Admin GET all / POST / PUT / DELETE категорії обладнання.")
    public void adminCrudEquipmentCategory() {
        Response list = apiExecutor.execute(ApiEndpointDefinition.EQUIPMENT_CATEGORY_GET_ALL, UserRole.ADMIN);
        assertThat(list.statusCode()).isEqualTo(200);

        EquipmentCategoryRequest create = EquipmentCategoryRequest.builder()
                .name("erp-eqcat-" + System.currentTimeMillis() % 1_000_000)
                .build();
        Response created = apiExecutor.execute(
                ApiEndpointDefinition.EQUIPMENT_CATEGORY_POST_CREATE, UserRole.ADMIN, create);
        assertThat(created.statusCode()).isBetween(200, 299);
        EquipmentCategoryResponse body = created.as(EquipmentCategoryResponse.class);
        createdId = body.getId();
        assertThat(createdId).isNotNull();

        EquipmentCategoryRequest update = create.toBuilder().name(create.getName() + "-u").build();
        Response updated = apiExecutor.execute(
                ApiEndpointDefinition.EQUIPMENT_CATEGORY_PUT_UPDATE, UserRole.ADMIN, update, createdId);
        assertThat(updated.statusCode()).isBetween(200, 299);
    }
}
