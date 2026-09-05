package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.tech_map.TechnologicalMapDataFactory;
import com.erp.enums.UserRole;
import com.erp.models.request.ResourceUsageRequest;
import com.erp.models.request.TechnologicalMapRequest;
import com.erp.models.request.TechnologicalMapUsageExportRequest;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.TechnologicalMapComponentResponse;
import com.erp.models.response.TechnologicalMapResourceUsageResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.test_context.TestContext;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Isolated PRODUCTION BOM helpers for {@code GET /technological-maps/calculate-resource-usage}.
 */
@Slf4j
public class ResourceCalculatorFixture extends BaseFixture {

    public static final double BODY_PER_PRODUCT = 2.0;
    public static final double BOARD_PER_BODY = 3.0;
    public static final double CHIP_PER_BOARD = 4.0;
    public static final double CANONICAL_AMOUNT = 10.0;

    private final TechnologicalMapFixture techMapFixture;
    private final ResourceFixture resourceFixture;

    public ResourceCalculatorFixture(TestContext testContext, ApiExecutor apiExecutor) {
        super(testContext, apiExecutor);
        this.techMapFixture = new TechnologicalMapFixture(testContext, apiExecutor);
        this.resourceFixture = new ResourceFixture(testContext, apiExecutor);
    }

    @Step("FIXTURE: довідники для калькулятора розхідників")
    public void prepareContext() {
        techMapFixture.prepareContext();
    }

    public TechnologicalMapFixture techMaps() {
        return techMapFixture;
    }

    public ResourceFixture resources() {
        return resourceFixture;
    }

    @Step("Створити ланцюжок виріб←корпус←плата←чіп на локації {storageId}")
    public Chain createCanonicalChain(Long storageId) {
        return createCanonicalChain(Set.of(storageId), Set.of(storageId), Set.of(storageId));
    }

    @Step("Створити ланцюжок виріб/корпус/плата на заданих локаціях")
    public Chain createCanonicalChain(Set<Long> productStorageIds, Set<Long> bodyStorageIds, Set<Long> boardStorageIds) {
        String suffix = String.valueOf(System.currentTimeMillis());
        ResourceResponse chip = resourceFixture.createUniqueResource("CALC-CHIP-" + suffix);
        ResourceResponse board = resourceFixture.createUniqueResource("CALC-BOARD-" + suffix);
        ResourceResponse body = resourceFixture.createUniqueResource("CALC-BODY-" + suffix);
        ResourceResponse product = resourceFixture.createUniqueResource("CALC-PROD-" + suffix);

        TechnologicalMapResponse boardMap = techMapFixture.createTechMapWithRequest(
                UserRole.ADMIN,
                TechnologicalMapDataFactory.createProductionMapWithStorages(
                        "CALC-board-map",
                        List.of(new ResourceUsageRequest(chip.getId(), CHIP_PER_BOARD)),
                        List.of(new ResourceUsageRequest(board.getId(), 1.0)),
                        boardStorageIds).build());
        TechnologicalMapResponse bodyMap = techMapFixture.createTechMapWithRequest(
                UserRole.ADMIN,
                TechnologicalMapDataFactory.createProductionMapWithStorages(
                        "CALC-body-map",
                        List.of(new ResourceUsageRequest(board.getId(), BOARD_PER_BODY)),
                        List.of(new ResourceUsageRequest(body.getId(), 1.0)),
                        bodyStorageIds).build());
        TechnologicalMapResponse productMap = techMapFixture.createTechMapWithRequest(
                UserRole.ADMIN,
                TechnologicalMapDataFactory.createProductionMapWithStorages(
                        "CALC-prd-map",
                        List.of(new ResourceUsageRequest(body.getId(), BODY_PER_PRODUCT)),
                        List.of(new ResourceUsageRequest(product.getId(), 1.0)),
                        productStorageIds).build());
        return Chain.builder()
                .product(product)
                .body(body)
                .board(board)
                .chip(chip)
                .productMap(productMap)
                .bodyMap(bodyMap)
                .boardMap(boardMap)
                .build();
    }

    @Step("Створити виріб←плата з двома виробниками плати")
    public ChoiceChain createChoiceChain(Long productStorageId, Long otherProducerStorageId) {
        String suffix = String.valueOf(System.currentTimeMillis());
        ResourceResponse chip = resourceFixture.createUniqueResource("CALC-CHP-" + suffix);
        ResourceResponse board = resourceFixture.createUniqueResource("CALC-PLT-" + suffix);
        ResourceResponse product = resourceFixture.createUniqueResource("CALC-ITM-" + suffix);

        TechnologicalMapResponse boardMapA = techMapFixture.createTechMapWithRequest(
                UserRole.ADMIN,
                TechnologicalMapDataFactory.createProductionMapWithStorages(
                        "CALC-board-A",
                        List.of(new ResourceUsageRequest(chip.getId(), 3.0)),
                        List.of(new ResourceUsageRequest(board.getId(), 1.0)),
                        Set.of(productStorageId)).build());
        TechnologicalMapResponse boardMapB = techMapFixture.createTechMapWithRequest(
                UserRole.ADMIN,
                TechnologicalMapDataFactory.createProductionMapWithStorages(
                        "CALC-board-B",
                        List.of(new ResourceUsageRequest(chip.getId(), 5.0)),
                        List.of(new ResourceUsageRequest(board.getId(), 1.0)),
                        Set.of(otherProducerStorageId)).build());
        TechnologicalMapResponse productMap = techMapFixture.createTechMapWithRequest(
                UserRole.ADMIN,
                TechnologicalMapDataFactory.createProductionMapWithStorages(
                        "CALC-choice-prd",
                        List.of(new ResourceUsageRequest(board.getId(), 2.0)),
                        List.of(new ResourceUsageRequest(product.getId(), 1.0)),
                        Set.of(productStorageId)).build());
        return ChoiceChain.builder()
                .product(product)
                .board(board)
                .chip(chip)
                .productMap(productMap)
                .boardMapA(boardMapA)
                .boardMapB(boardMapB)
                .build();
    }

    @Step("Створити виріб на {productStorageId} з платою, яку виробляє лише {producerStorageId}")
    public ChoiceChain createRemoteProducerChain(Long productStorageId, Long producerStorageId) {
        String suffix = String.valueOf(System.currentTimeMillis());
        ResourceResponse chip = resourceFixture.createUniqueResource("CALC-RCHP-" + suffix);
        ResourceResponse board = resourceFixture.createUniqueResource("CALC-RPLT-" + suffix);
        ResourceResponse product = resourceFixture.createUniqueResource("CALC-RITM-" + suffix);

        TechnologicalMapResponse boardMap = techMapFixture.createTechMapWithRequest(
                UserRole.ADMIN,
                TechnologicalMapDataFactory.createProductionMapWithStorages(
                        "CALC-remote-board",
                        List.of(new ResourceUsageRequest(chip.getId(), 4.0)),
                        List.of(new ResourceUsageRequest(board.getId(), 1.0)),
                        Set.of(producerStorageId)).build());
        TechnologicalMapResponse productMap = techMapFixture.createTechMapWithRequest(
                UserRole.ADMIN,
                TechnologicalMapDataFactory.createProductionMapWithStorages(
                        "CALC-remote-prd",
                        List.of(new ResourceUsageRequest(board.getId(), 2.0)),
                        List.of(new ResourceUsageRequest(product.getId(), 1.0)),
                        Set.of(productStorageId)).build());
        return ChoiceChain.builder()
                .product(product)
                .board(board)
                .chip(chip)
                .productMap(productMap)
                .boardMapA(boardMap)
                .boardMapB(boardMap)
                .build();
    }

    @Step("GET calculate-resource-usage storageId={storageId} tmId={tmId} amount={amount}")
    public Response calculateRaw(UserRole role, Long storageId, Long tmId, String amount, List<Long> chosenTmIds) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (storageId != null) {
            params.put("storageId", storageId);
        }
        if (tmId != null) {
            params.put("tmIds", tmId);
        }
        if (amount != null) {
            params.put("amount", amount);
        }
        if (chosenTmIds != null && !chosenTmIds.isEmpty()) {
            params.put("chosenTmIds", chosenTmIds);
        }
        return apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.TECH_MAP_CALCULATE_RESOURCE_USAGE, role, params);
    }

    @Step("GET calculate-resource-usage (очікується 200)")
    public TechnologicalMapResourceUsageResponse calculate(
            UserRole role, Long storageId, Long tmId, String amount, List<Long> chosenTmIds) {
        Response response = calculateRaw(role, storageId, tmId, amount, chosenTmIds);
        validateSuccess(response, "GET calculate-resource-usage as " + role);
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.TECH_MAP_CALCULATE_RESOURCE_USAGE);
        TechnologicalMapResourceUsageResponse[] roots =
                response.as(TechnologicalMapResourceUsageResponse[].class);
        assertThat(roots)
                .as("calculate-resource-usage returns one root map")
                .hasSize(1);
        return roots[0];
    }

    public TechnologicalMapResourceUsageResponse calculate(
            UserRole role, Long storageId, Long tmId, String amount) {
        return calculate(role, storageId, tmId, amount, List.of());
    }

    @Step("POST calculate-resource-usage/export")
    public Response exportSummary(UserRole role, TechnologicalMapUsageExportRequest request) {
        return apiExecutor.execute(ApiEndpointDefinition.TECH_MAP_EXPORT_RESOURCE_USAGE, role, request);
    }

    @Step("Деактивувати техкарту {techMap.id} на складі {storageId}")
    public void cleanupTechMap(TechnologicalMapResponse techMap, Long storageId) {
        if (techMap == null || techMap.getId() == null || storageId == null) {
            return;
        }
        techMapFixture.deactivateTechMap(UserRole.ADMIN, techMap.getId(), storageId);
    }

    public TechnologicalMapRequest disassembleMap(String namePrefix, ResourceResponse output, ResourceResponse input,
                                                  Long storageId) {
        return TechnologicalMapDataFactory.createProductionMapWithStorages(
                        namePrefix,
                        List.of(new ResourceUsageRequest(input.getId(), 1.0)),
                        List.of(new ResourceUsageRequest(output.getId(), 1.0)),
                        Set.of(storageId))
                .type(TechnologicalMapDataFactory.TYPE_DISASSEMBLE)
                .build();
    }

    public TechnologicalMapComponentResponse requireComponent(
            TechnologicalMapResourceUsageResponse root, String resourceName) {
        TechnologicalMapComponentResponse found = findComponent(root.getComponents(), resourceName);
        assertThat(found)
                .as("Missing calculator component %s; present=%s", resourceName, collectNames(root.getComponents()))
                .isNotNull();
        return found;
    }

    public boolean hasComponent(TechnologicalMapResourceUsageResponse root, String resourceName) {
        return findComponent(root.getComponents(), resourceName) != null;
    }

    public TechnologicalMapComponentResponse findComponent(
            List<TechnologicalMapComponentResponse> components, String resourceName) {
        if (components == null) {
            return null;
        }
        for (TechnologicalMapComponentResponse component : components) {
            if (component.getResource() != null && resourceName.equals(component.getResource().getName())) {
                return component;
            }
            TechnologicalMapComponentResponse nested = findComponent(component.getComponents(), resourceName);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    public List<String> collectNames(List<TechnologicalMapComponentResponse> components) {
        List<String> names = new ArrayList<>();
        walkNames(components, names);
        return names;
    }

    private void walkNames(List<TechnologicalMapComponentResponse> components, List<String> names) {
        if (components == null) {
            return;
        }
        for (TechnologicalMapComponentResponse component : components) {
            if (component.getResource() != null) {
                names.add(component.getResource().getName());
            }
            walkNames(component.getComponents(), names);
        }
    }

    public void assertAmount(Double actual, double expected) {
        assertThat(actual)
                .as("amount")
                .isNotNull()
                .isCloseTo(expected, within(0.05));
    }

    @Value
    @Builder
    public static class Chain {
        ResourceResponse product;
        ResourceResponse body;
        ResourceResponse board;
        ResourceResponse chip;
        TechnologicalMapResponse productMap;
        TechnologicalMapResponse bodyMap;
        TechnologicalMapResponse boardMap;
    }

    @Value
    @Builder
    public static class ChoiceChain {
        ResourceResponse product;
        ResourceResponse board;
        ResourceResponse chip;
        TechnologicalMapResponse productMap;
        TechnologicalMapResponse boardMapA;
        TechnologicalMapResponse boardMapB;
    }
}
