package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.models.request.ResourcePropertyRequest;
import com.erp.models.request.ResourceRequest;
import com.erp.models.response.RelocationResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.test_context.TestContext;
import io.qameta.allure.Allure;
import io.restassured.response.Response;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Reuses the owned mixed cargo and cleanup; only owned resource properties are changed. */
public class RelocationSupplierFiltersFixture extends RelocationFiltersFixture {
    public static final String MOU = "МОУ";
    public static final String OTHER = "Інші";
    private final ApiExecutor executor;

    public RelocationSupplierFiltersFixture(TestContext context, ApiExecutor executor) {
        super(context, executor);
        this.executor = executor;
    }

    @Override
    public void prepare() {
        super.prepare();
        supplier(first, MOU);
        supplier(sameCategory, OTHER);
        assertThat(supplierValue(otherCategory)).isNull();
        Allure.addAttachment("Supplier fixture identity", "marker=" + marker + "; first=" + first.getId()
                + "; other=" + sameCategory.getId() + "; missing=" + otherCategory.getId()
                + "; source=" + source.getId() + "; destination=" + destination.getId() + "; rows=" + ids);
    }

    public void supplier(ResourceResponse resource, String value) {
        resources.update(UserRole.ADMIN, resource.getId(), ResourceRequest.builder()
                .name(resource.getName()).measurementUnitId(resource.getUnit().getId())
                .categoryId(resource.getCategory().getId())
                .properties(value == null ? List.of() : List.of(ResourcePropertyRequest.builder()
                        .name("Постачальник").value(value).build())).build());
        assertThat(supplierValue(resource)).as("Persisted supplier for %s", resource.getId()).isEqualTo(value);
    }

    public String supplierValue(ResourceResponse resource) {
        Response response = executor.execute(ApiEndpointDefinition.RESOURCE_GET_BY_ID, UserRole.ADMIN,
                null, resource.getId());
        assertThat(response.statusCode()).isEqualTo(200);
        List<Map<String, Object>> properties = response.jsonPath().getList("properties");
        return properties == null ? null : properties.stream()
                .filter(p -> "Постачальник".equals(p.get("name")))
                .map(p -> (String) p.get("value")).findFirst().orElse(null);
    }

    public void assertRows(Map<String, Object> query, List<Long> ids, ResourceResponse... expected) {
        List<RelocationResponse> rows = rows(query);
        assertThat(rows).as("Exact relocation IDs: %s", query).extracting(RelocationResponse::getId)
                .containsExactlyInAnyOrderElementsOf(ids);
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.getEquipmentItems()).isEmpty();
            assertThat(row.getItems()).as("Exact trimmed cargo of %s", row.getId())
                    .extracting(item -> item.getResource().getId())
                    .containsExactlyInAnyOrderElementsOf(Arrays.stream(expected).map(ResourceResponse::getId).toList());
            row.getItems().forEach(item -> assertThat(item.getAmount()).isEqualByComparingTo(amount(item.getResource())));
        });
    }

    public String amount(ResourceResponse resource) {
        return resource.getId().equals(first.getId()) ? "2" : resource.getId().equals(sameCategory.getId()) ? "3" : "5";
    }

    public void assertExcel(List<List<String>> workbook, int count, ResourceResponse... expected) {
        List<String> header = workbook.stream().filter(row -> row.contains("Назва") && row.contains("Кількість"))
                .findFirst().orElseThrow(() -> new AssertionError("Missing Excel headers: " + workbook));
        int name = header.indexOf("Назва"), amount = header.indexOf("Кількість");
        List<List<String>> rows = workbook.stream().skip(workbook.indexOf(header) + 1L)
                .filter(row -> row.size() > name && !row.get(name).isBlank()).toList();
        Map<String, Long> expectedCounts = Arrays.stream(expected).collect(Collectors.toMap(ResourceResponse::getName,
                resource -> (long) count));
        assertThat(rows.stream().collect(Collectors.groupingBy(row -> row.get(name), Collectors.counting())))
                .as("Exact Excel cargo, including exclusion of resources without supplier").isEqualTo(expectedCounts);
        for (List<String> row : rows) {
            ResourceResponse resource = Arrays.stream(expected).filter(r -> r.getName().equals(row.get(name)))
                    .findFirst().orElseThrow();
            assertThat(new BigDecimal(row.get(amount).replace(",", "."))).isEqualByComparingTo(amount(resource));
        }
    }

    public void assertTotals(Map<String, Object> query, String expected) {
        Response response = executor.executeWithQueryParams(ApiEndpointDefinition.RELOCATION_GET_TOTALS, UserRole.ADMIN, query);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.jsonPath().getInt("equipmentCount")).isZero();
        List<Map<String, Object>> units = response.jsonPath().getList("units");
        if (new BigDecimal(expected).signum() == 0) assertThat(units).isEmpty();
        else {
            assertThat(units).hasSize(1);
            assertThat(units.getFirst().get("unit")).isEqualTo(first.getUnit().getShortName());
            assertThat(new BigDecimal(units.getFirst().get("amount").toString())).isEqualByComparingTo(expected);
        }
    }

    public Response page(Map<String, Object> query) {
        Response response = executor.executeWithQueryParams(ApiEndpointDefinition.RELOCATION_GET_PAGE, UserRole.ADMIN, query);
        assertThat(response.statusCode()).isEqualTo(200);
        return response;
    }
}
