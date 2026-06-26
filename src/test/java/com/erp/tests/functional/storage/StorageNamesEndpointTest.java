package com.erp.tests.functional.storage;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.models.response.StorageResponse;
import com.erp.utils.helpers.DatabaseIntegrityValidator;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.*;
import io.restassured.response.Response;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Контракт і цілісність відповіді {@code GET /api/v1/storages/names?isActive=true}
 * — той самий ендпоінт, що живить dropdown «Кому відправляю» у формі видачі.
 */
@Epic("Master Data")
@Feature("Storages")
@Story("Storage Names Endpoint")
public class StorageNamesEndpointTest extends StorageApiTestBase {

    @BeforeClass(alwaysRun = true)
    @Step("Підготовка: fixture для /storages/names")
    public void setupStorageNamesEndpointTest() {
        SchemaRegistry.logSchemaCoverage();
    }

    @Test(priority = 10)
    @TestCaseId("TC-STR-NAMES-001")
    @Description("""
            GET /storages/names?isActive=true повертає 200, проходить JSON Schema
            і містить обов'язкові поля id + name для кожного елемента.
            """)
    @Severity(SeverityLevel.CRITICAL)
    public void testActiveNamesResponseContract() {
        Response response = storageFixture.getNamesRaw(UserRole.OWNER_1, true);

        assertThat(response.statusCode()).isEqualTo(200);
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.STORAGE_GET_NAMES);

        List<StorageResponse> names = DatabaseIntegrityValidator.extractList(response, StorageResponse.class);
        assertThat(names).isNotEmpty();
        assertThat(names).allSatisfy(item -> {
            assertThat(item.getId()).as("id").isNotNull();
            assertThat(item.getName()).as("name").isNotBlank();
        });
    }

    @Test(priority = 20)
    @TestCaseId("TC-STR-NAMES-002")
    @Description("""
            Кожен storage.id у відповіді /storages/names?isActive=true зустрічається рівно один раз.
            Дублікати id (наприклад, однаковий підрозділ з кількох областей видимості)
            ламають Combobox у формі видачі.
            """)
    @Severity(SeverityLevel.CRITICAL)
    public void testActiveNamesListHasUniqueStorageIds() {
        List<StorageResponse> names = storageFixture.getNames(UserRole.OWNER_1, true, null);

        List<Long> ids = names.stream().map(StorageResponse::getId).toList();
        Set<Long> uniqueIds = new HashSet<>(ids);

        assertThat(uniqueIds)
                .as("Кожен id у /storages/names?isActive=true має бути унікальним")
                .hasSize(ids.size());

        List<String> duplicateLabels = names.stream()
                .collect(Collectors.groupingBy(StorageResponse::getId, Collectors.counting()))
                .entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .map(e -> names.stream()
                        .filter(s -> s.getId().equals(e.getKey()))
                        .map(StorageResponse::getName)
                        .findFirst()
                        .orElse("id=" + e.getKey()))
                .toList();

        assertThat(duplicateLabels)
                .as("Дублікати id у /storages/names: %s", duplicateLabels)
                .isEmpty();
    }
}
