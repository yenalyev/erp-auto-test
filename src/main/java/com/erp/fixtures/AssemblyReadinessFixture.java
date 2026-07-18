package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.relocation.RelocationStockSeeder;
import com.erp.data.factories.tech_map.TechnologicalMapDataFactory;
import com.erp.enums.UserRole;
import com.erp.models.request.ResourceUsageRequest;
import com.erp.models.request.TechnologicalMapRequest;
import com.erp.models.response.AssemblyComponentResponse;
import com.erp.models.response.AssemblyReadinessResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.test_context.TestContext;
import com.erp.utils.helpers.DatabaseIntegrityValidator;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
public class AssemblyReadinessFixture extends BaseFixture {

    private final ResourceFixture resourceFixture;
    private final TechnologicalMapFixture techMapFixture;
    private final ProductionFixture productionFixture;

    public AssemblyReadinessFixture(TestContext testContext, ApiExecutor apiExecutor) {
        super(testContext, apiExecutor);
        this.resourceFixture = new ResourceFixture(testContext, apiExecutor);
        this.techMapFixture = new TechnologicalMapFixture(testContext, apiExecutor);
        this.productionFixture = new ProductionFixture(testContext, apiExecutor);
    }

    @Step("FIXTURE: Підготовка середовища для assembly-readiness")
    public void prepareContext() {
        techMapFixture.prepareContext();
    }

    @Value
    @Builder
    public static class TechMapSetup {
        ResourceResponse product;
        ResourceResponse input1;
        ResourceResponse input2;
        TechnologicalMapResponse techMap;
    }

    @Step("Створити ізольовану PRODUCTION техкарту з кастомними коефіцієнтами")
    public TechMapSetup createProductionTechMap(UserRole role,
                                                Long storageId,
                                                double input1Required,
                                                double input2Required) {
        String suffix = String.valueOf(System.nanoTime());
        ResourceResponse in1 = resourceFixture.createUniqueResource("AR-IN1-" + suffix);
        ResourceResponse in2 = resourceFixture.createUniqueResource("AR-IN2-" + suffix);
        ResourceResponse product = resourceFixture.createUniqueResource("AR-OUT-" + suffix);

        TechnologicalMapRequest request = TechnologicalMapDataFactory.createProductionMapWithStorages(
                "AR-TM",
                List.of(
                        new ResourceUsageRequest(in1.getId(), input1Required),
                        new ResourceUsageRequest(in2.getId(), input2Required)),
                List.of(new ResourceUsageRequest(product.getId(), 1.0)),
                Set.of(storageId)).build();

        TechnologicalMapResponse techMap = techMapFixture.createTechMapWithRequest(role, request);
        return TechMapSetup.builder()
                .product(product)
                .input1(in1)
                .input2(in2)
                .techMap(techMap)
                .build();
    }

    @Step("Створити другу PRODUCTION техкарту зі спільним компонентом")
    public TechnologicalMapResponse createSecondTechMapWithSharedInput(UserRole role,
                                                                     Long storageId,
                                                                     ResourceResponse sharedInput,
                                                                     ResourceResponse newInput,
                                                                     ResourceResponse product) {
        TechnologicalMapRequest request = TechnologicalMapDataFactory.createProductionMapWithStorages(
                "AR-TM-SHARED",
                List.of(
                        new ResourceUsageRequest(sharedInput.getId(), 1.0),
                        new ResourceUsageRequest(newInput.getId(), 1.0)),
                List.of(new ResourceUsageRequest(product.getId(), 1.0)),
                Set.of(storageId)).build();
        return techMapFixture.createTechMapWithRequest(role, request);
    }

    @Step("Створити DISASSEMBLE техкарту (не має потрапляти в assembly-readiness)")
    public TechnologicalMapResponse createDisassembleTechMap(UserRole role, Long storageId) {
        String suffix = String.valueOf(System.nanoTime());
        ResourceResponse in = resourceFixture.createUniqueResource("AR-DIS-IN-" + suffix);
        ResourceResponse out = resourceFixture.createUniqueResource("AR-DIS-OUT-" + suffix);
        List<ResourceResponse> resources = List.of(in, out);
        TechnologicalMapRequest request = TechnologicalMapDataFactory.createDisassembleTechMap(resources, storageId).build();
        return techMapFixture.createTechMapWithRequest(role, request);
    }

    @Step("Засіяти stock компонентів на складі {storageId}")
    public void seedComponentStock(Long storageId,
                                   UserRole role,
                                   Map<Long, Double> amountsByResourceId) {
        RelocationStockSeeder.receiveFromSupplier(apiExecutor, role, storageId, amountsByResourceId);
    }

    @Step("Забезпечити мінімальний stock для техкарти")
    public void seedStockForTechMap(Long storageId, TechnologicalMapResponse techMap, double minimum) {
        techMapFixture.seedStockForIsolatedTechMap(productionFixture, storageId, techMap, minimum);
    }

    @Step("API: GET assembly-readiness для storage {storageId}")
    public List<AssemblyReadinessResponse> getReadiness(UserRole role, Long storageId) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.ASSEMBLY_READINESS_GET_BY_STORAGE,
                role,
                String.valueOf(storageId));
        validateSuccess(response, "GET assembly-readiness for storage " + storageId);
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.ASSEMBLY_READINESS_GET_BY_STORAGE);
        return DatabaseIntegrityValidator.extractList(response, AssemblyReadinessResponse.class);
    }

    public Optional<AssemblyReadinessResponse> findByTechMapId(List<AssemblyReadinessResponse> rows, Long techMapId) {
        return rows.stream()
                .filter(row -> techMapId.equals(row.getTechnologicalMapId()))
                .findFirst();
    }

    public Optional<AssemblyComponentResponse> findComponent(AssemblyReadinessResponse row, Long resourceId) {
        if (row.getComponents() == null) {
            return Optional.empty();
        }
        return row.getComponents().stream()
                .filter(c -> c.getResource() != null && resourceId.equals(c.getResource().getId()))
                .findFirst();
    }

    /** floor(availableStock / requiredPerUnit) — mirrors tk-ui {@code possibleUnits}. */
    public static int possibleUnits(BigDecimal availableStock, BigDecimal requiredPerUnit) {
        if (requiredPerUnit == null || requiredPerUnit.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        BigDecimal stock = availableStock != null ? availableStock : BigDecimal.ZERO;
        return stock.divide(requiredPerUnit, 0, RoundingMode.FLOOR).intValue();
    }

    /** min(possibleUnits) across components — mirrors tk-ui {@code readyToAssembleQty}. */
    public static int computeReadyQty(AssemblyReadinessResponse row) {
        if (row.getComponents() == null || row.getComponents().isEmpty()) {
            return 0;
        }
        return row.getComponents().stream()
                .mapToInt(c -> possibleUnits(c.getAvailableStock(), c.getRequiredPerUnit()))
                .min()
                .orElse(0);
    }

    @Step("Cleanup: деактивувати техкарту {techMapId}")
    public void cleanupTechMap(UserRole role, Long techMapId, Long storageId) {
        try {
            techMapFixture.deactivateTechMap(role, techMapId, storageId);
        } catch (RuntimeException e) {
            log.warn("Cleanup tech map {} failed: {}", techMapId, e.getMessage());
        }
    }

    @Step("Cleanup: деактивувати ресурс {resourceId}")
    public void cleanupResource(UserRole role, Long resourceId) {
        try {
            resourceFixture.deactivate(role, resourceId);
        } catch (RuntimeException e) {
            log.warn("Cleanup resource {} failed: {}", resourceId, e.getMessage());
        }
    }
}
