package com.erp.tests.functional.storage;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.storage.StorageDataFactory;
import com.erp.enums.UserRole;
import com.erp.models.request.StorageRequest;
import com.erp.models.response.StorageResponse;
import com.erp.utils.data.DataUtils;
import com.erp.utils.helpers.AllureHelper;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.function.Supplier;

import static com.erp.data.factories.storage.StorageDataFactory.TEXT_FIELD_MAX_LENGTH;
import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Master Data")
@Feature("Storages")
public class StorageFieldLengthValidationTest extends StorageApiTestBase {

    private static final int OVER_LIMIT_LENGTH = TEXT_FIELD_MAX_LENGTH + 1;

    @BeforeClass(alwaysRun = true)
    @Step("Підготовка середовища для тестів довжини полів локації")
    public void setupStorageLengthTest() {
        storageFixture.prepareContext();
    }

    /**
     * Поля з валідацією довжини на бекенді ({@code StorageValidatorImpl}, ліміт 255).
     */
    @Getter
    @RequiredArgsConstructor
    private enum StorageTextField {
        NAME("name", StorageDataFactory::uniqueNameAtMaxLength),
        ALIAS("alias", StorageDataFactory::aliasAtMaxLength),
        IDENTIFIER_NUMBER("identifierNumber",
                () -> StorageDataFactory.exactLengthString(TEXT_FIELD_MAX_LENGTH)),
        NAME_FOR_INVOICES("nameForInvoices",
                () -> StorageDataFactory.exactLengthString(TEXT_FIELD_MAX_LENGTH));

        private final String jsonField;
        private final Supplier<String> maxLengthValueSupplier;
    }

    @Getter
    @RequiredArgsConstructor
    private enum ValidatedLengthField {
        NAME("name"),
        ALIAS("alias");

        private final String jsonField;
    }

    @DataProvider(name = "textFieldAtMaxLength")
    public Object[][] textFieldAtMaxLength() {
        return Arrays.stream(StorageTextField.values())
                .map(field -> new Object[] { field })
                .toArray(Object[][]::new);
    }

    @DataProvider(name = "textFieldOverMaxLength")
    public Object[][] textFieldOverMaxLength() {
        return Arrays.stream(ValidatedLengthField.values())
                .map(field -> new Object[] { field })
                .toArray(Object[][]::new);
    }

    @Test(dataProvider = "textFieldAtMaxLength", priority = 10)
    @TestCaseId("TC-STR-009")
    @Story("Validation Rules - Field Length")
    @Description("POST: текстові поля приймають рівно 255 символів → 200")
    public void testCreateAcceptsTextFieldAtMaxLength(StorageTextField field) {
        Allure.getLifecycle().updateTestCase(tc ->
                tc.setName("Create at 255 chars: " + field.getJsonField()));

        StorageResponse parent = storageFixture.resolveParentUnit();
        StorageRequest body = withField(
                StorageDataFactory.childStorage(parent.getId(), "len-").build(),
                field,
                field.getMaxLengthValueSupplier().get());

        Response response = apiExecutor.execute(
                ApiEndpointDefinition.STORAGE_POST_CREATE, UserRole.ADMIN, body);

        Allure.step("Assert: 255 символів → 200", () -> {
            if (response.statusCode() != 200) {
                AllureHelper.attachResponseDetails(response);
            }
            assertThat(response.statusCode())
                    .as("255 символів у полі %s", field.getJsonField())
                    .isEqualTo(200);
            StorageResponse created = response.as(StorageResponse.class);
            storageFixture.trackForCleanup(created.getId());
            assertFieldLengthInResponse(created, field);
        });
    }

    @Test(dataProvider = "textFieldOverMaxLength", priority = 20)
    @TestCaseId("TC-STR-010")
    @Story("Validation Rules - Field Length")
    @Description("POST: name/alias з 256 символами → 400 (не 500)")
    public void testCreateRejectsTextFieldOverMaxLength(ValidatedLengthField field) {
        Allure.getLifecycle().updateTestCase(tc ->
                tc.setName("Create at 256 chars: " + field.getJsonField()));

        StorageResponse parent = storageFixture.resolveParentUnit();
        StorageTextField textField = StorageTextField.valueOf(field.name());
        String tooLongValue = valueOfLength(textField, OVER_LIMIT_LENGTH);
        StorageRequest body = withField(
                StorageDataFactory.childStorage(parent.getId(), "len-").build(),
                textField,
                tooLongValue);

        Response response = apiExecutor.execute(
                ApiEndpointDefinition.STORAGE_POST_CREATE, UserRole.ADMIN, body);

        Allure.step("Assert: 256 символів → 400", () -> {
            if (response.statusCode() != 400) {
                AllureHelper.attachResponseDetails(response);
            }
            assertRejectedForLength(field.getJsonField(), response);
        });
    }

    @Test(dataProvider = "textFieldAtMaxLength", priority = 30)
    @TestCaseId("TC-STR-011")
    @Story("Validation Rules - Field Length")
    @Description("PUT: текстові поля приймають рівно 255 символів → 200")
    public void testUpdateAcceptsTextFieldAtMaxLength(StorageTextField field) {
        Allure.getLifecycle().updateTestCase(tc ->
                tc.setName("Update at 255 chars: " + field.getJsonField()));

        StorageResponse existing = storageFixture.createUniqueStorage("len-upd-");
        StorageRequest updateBody = withField(
                StorageDataFactory.fromExisting(storageFixture.getById(UserRole.ADMIN, existing.getId())).build(),
                field,
                field.getMaxLengthValueSupplier().get());

        Response response = storageFixture.update(UserRole.ADMIN, existing.getId(), updateBody);

        Allure.step("Assert: 255 символів → 200", () -> {
            if (response.statusCode() != 200) {
                AllureHelper.attachResponseDetails(response);
            }
            assertThat(response.statusCode())
                    .as("255 символів у полі %s", field.getJsonField())
                    .isEqualTo(200);
            assertFieldLengthInResponse(response.as(StorageResponse.class), field);
        });
    }

    @Test(dataProvider = "textFieldOverMaxLength", priority = 40)
    @TestCaseId("TC-STR-012")
    @Story("Validation Rules - Field Length")
    @Description("PUT: name/alias з 256 символами → 400 (не 500)")
    public void testUpdateRejectsTextFieldOverMaxLength(ValidatedLengthField field) {
        Allure.getLifecycle().updateTestCase(tc ->
                tc.setName("Update at 256 chars: " + field.getJsonField()));

        StorageResponse existing = storageFixture.createUniqueStorage("len-bad-");
        StorageResponse fullExisting = storageFixture.getById(UserRole.ADMIN, existing.getId());
        StorageTextField textField = StorageTextField.valueOf(field.name());
        StorageRequest updateBody = withField(
                StorageDataFactory.fromExisting(fullExisting).build(),
                textField,
                valueOfLength(textField, OVER_LIMIT_LENGTH));

        Response response = storageFixture.update(UserRole.ADMIN, existing.getId(), updateBody);

        Allure.step("Assert: 256 символів → 400", () -> {
            if (response.statusCode() != 400) {
                AllureHelper.attachResponseDetails(response);
            }
            assertRejectedForLength(field.getJsonField(), response);
        });
    }

    private static StorageRequest withField(StorageRequest base, StorageTextField field, String value) {
        StorageRequest.StorageRequestBuilder builder = base.toBuilder();
        switch (field) {
            case NAME -> builder.name(value);
            case ALIAS -> builder.alias(value);
            case IDENTIFIER_NUMBER -> builder.identifierNumber(value);
            case NAME_FOR_INVOICES -> builder.nameForInvoices(value);
        }
        return builder.build();
    }

    private static String valueOfLength(StorageTextField field, int length) {
        if (field == StorageTextField.NAME) {
            return DataUtils.generateWithUniqueSuffix(length);
        }
        return StorageDataFactory.exactLengthString(length);
    }

    private static void assertFieldLengthInResponse(StorageResponse response, StorageTextField field) {
        String actual = switch (field) {
            case NAME -> response.getName();
            case ALIAS -> response.getAlias();
            case IDENTIFIER_NUMBER -> response.getIdentifierNumber();
            case NAME_FOR_INVOICES -> response.getNameForInvoices();
        };
        assertThat(actual).isNotNull();
        assertThat(actual.length())
                .as("Довжина %s у response", field.getJsonField())
                .isEqualTo(TEXT_FIELD_MAX_LENGTH);
    }

    private static void assertRejectedForLength(String jsonField, Response response) {
        assertThat(response.statusCode())
                .as("256 символів у полі %s не повинні давати 500", jsonField)
                .isNotEqualTo(500);
        assertThat(response.statusCode())
                .as("256 символів у полі %s мають повертати 400", jsonField)
                .isEqualTo(400);
        assertThat(response.jsonPath().getString("errors[0].field"))
                .isEqualTo(jsonField);
        assertThat(response.jsonPath().getString("errors[0].messages[0]")).isNotBlank();
    }
}
