package com.erp.tests.functional.statistics;

import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.fixtures.PlanNeededResourcesFixture;
import com.erp.models.request.ExecutionFilterRequest;
import com.erp.models.response.NeededResourcePathStepResponse;
import com.erp.models.response.NeededResourceResponse;
import com.erp.models.response.NeededResourceSourceResponse;
import com.erp.models.response.PlanNeededResourcesResponse;
import com.erp.models.response.PlanResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.DatabaseIntegrityValidator;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class FabergeNeededResourcesDevProbeTest extends BaseFunctionalTest {

    private static final String PRODUCT_QUERY = "Фаберже 1.5";
    private static final String PRODUCT_FALLBACK = "корпус";

    private PlanNeededResourcesFixture fixture;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void setup() {
        fixture = new PlanNeededResourcesFixture(testContext, apiExecutor);
        fixture.prepareContext();
    }

    @Test
    public void probeFabergeCorpusOnDev() {
        List<ResourceResponse> matches = findResources(PRODUCT_QUERY);
        if (matches.isEmpty()) {
            matches = findResources(PRODUCT_FALLBACK).stream()
                    .filter(r -> r.getName() != null && r.getName().toLowerCase().contains("фаберже"))
                    .toList();
        }
        log.info("Resource matches for '{}': {}", PRODUCT_QUERY,
                matches.stream().map(r -> r.getId() + " :: " + r.getName()).toList());

        ResourceResponse product = matches.stream()
                .filter(r -> r.getName() != null && r.getName().contains("1.5") && r.getName().contains("корпус"))
                .findFirst()
                .orElse(matches.stream().findFirst().orElse(null));

        if (product == null) {
            log.warn("Product not found on dev");
            return;
        }

        ExecutionFilterRequest filter = ExecutionFilterRequest.builder()
                .month(YearMonth.now().getMonthValue())
                .year(YearMonth.now().getYear())
                .includeStock(true)
                .includeProduced(false)
                .build();
        log.info("Selected product id={} name={}", product.getId(), product.getName());

        List<PlanResponse> allPlans = findAllPlansWithProduct(product.getId(), filter.getMonth(), filter.getYear());
        if (allPlans.isEmpty()) {
            log.warn("No location plans for product id={} in {}/{}", product.getId(), filter.getMonth(), filter.getYear());
            scanKnownStorages(product, filter);
            return;
        }

        for (PlanResponse plan : allPlans) {
            Long storageId = plan.getStorage() != null ? plan.getStorage().getId() : null;
            if (storageId == null) {
                log.warn("Plan id={} has no storage", plan.getId());
                continue;
            }
            ExecutionFilterRequest planFilter = filter.toBuilder()
                    .month(plan.getMonth())
                    .year(plan.getYear())
                    .build();
            log.info("Probing plan period {}/{} on storageId={}", planFilter.getYear(), planFilter.getMonth(), storageId);
            probeStorage(storageId, product, planFilter, plan);
        }

        // Also try current month on storages that had Faberge in any plan
        probeStorage(33L, product, filter, null);
    }

    private void scanKnownStorages(ResourceResponse product, ExecutionFilterRequest filter) {
        long[] storageIds = {
                ConfigProvider.getOwner1StorageId(),
                ConfigProvider.getOwner2StorageId(),
                ConfigProvider.getUnitStorageId()
        };
        for (long storageId : storageIds) {
            probeStorage(storageId, product, filter, null);
        }
    }

    private List<PlanResponse> findAllPlansWithProduct(Long productId, int month, int year) {
        Response response = apiExecutor.execute(ApiEndpointDefinition.PLAN_GET_ALL_ADMIN, UserRole.ADMIN);
        if (response.statusCode() != 200) {
            log.warn("PLAN_GET_ALL_ADMIN -> {}", response.statusCode());
            return List.of();
        }
        List<PlanResponse> plans = DatabaseIntegrityValidator.extractList(response, PlanResponse.class);
        log.info("Total plans from admin API: {}", plans.size());

        List<PlanResponse> monthMatch = plans.stream()
                .filter(p -> p.getMonth() == month && p.getYear() == year)
                .filter(p -> p.getOutput() != null && p.getOutput().stream()
                        .anyMatch(o -> o.getResource() != null && productId.equals(o.getResource().getId())))
                .toList();

        if (!monthMatch.isEmpty()) {
            return monthMatch;
        }

        List<PlanResponse> anyMonth = plans.stream()
                .filter(p -> p.getOutput() != null && p.getOutput().stream()
                        .anyMatch(o -> o.getResource() != null && productId.equals(o.getResource().getId())))
                .toList();
        log.info("Plans with product id={} any month: {}", productId,
                anyMonth.stream()
                        .map(p -> p.getYear() + "/" + p.getMonth() + " storage="
                                + (p.getStorage() != null ? p.getStorage().getId() : "?"))
                        .toList());

        List<PlanResponse> nameMatch = plans.stream()
                .filter(p -> p.getMonth() == month && p.getYear() == year)
                .filter(p -> p.getOutput() != null && p.getOutput().stream()
                        .anyMatch(o -> o.getResource() != null
                                && o.getResource().getName() != null
                                && o.getResource().getName().toLowerCase().contains("фаберже")
                                && o.getResource().getName().contains("корпус")))
                .toList();
        log.info("Plans with Faberge corpus name in {}/{}: {}", month, year,
                nameMatch.stream()
                        .map(p -> "storage=" + (p.getStorage() != null ? p.getStorage().getId() : "?")
                                + " outputs=" + p.getOutput().stream()
                                .map(o -> o.getResource().getName() + "=" + o.getAmount()).toList())
                        .toList());
        return nameMatch.isEmpty() ? anyMonth : nameMatch;
    }

    private void probeStorage(long storageId, ResourceResponse product, ExecutionFilterRequest filter, PlanResponse plan) {
        if (plan == null) {
            List<PlanResponse> plans = fixture.techMaps().getLocationPlans(storageId);
            plan = plans.stream()
                    .filter(p -> p.getMonth() == filter.getMonth() && p.getYear() == filter.getYear())
                    .findFirst()
                    .orElse(null);
            if (plan == null) {
                log.info("storageId={} — no plan for {}/{}", storageId, filter.getMonth(), filter.getYear());
                return;
            }
        }

        boolean productInPlan = plan.getOutput().stream()
                .anyMatch(o -> o.getResource() != null && product.getId().equals(o.getResource().getId()));
        log.info("storageId={} planId={} outputs={}",
                storageId,
                plan.getId(),
                plan.getOutput().stream()
                        .map(o -> (o.getResource() != null ? o.getResource().getName() : "?") + "=" + o.getAmount())
                        .toList());
        if (!productInPlan) {
            log.info("storageId={} — '{}' not in plan output", storageId, product.getName());
        }

        PlanNeededResourcesResponse body;
        try {
            body = fixture.requestNeeded(UserRole.ADMIN, storageId, filter);
        } catch (Exception e) {
            log.warn("storageId={} needed-resources failed: {}", storageId, e.getMessage());
            return;
        }

        log.info("=== storageId={} needed rows={} ===", storageId, body.getNeededResources().size());

        body.getNeededResources().stream()
                .filter(row -> row.getSources() != null && row.getSources().size() >= 2)
                .forEach(row -> {
                    log.warn("Multi-source row: {}", row.getResource().getName());
                    logRow(row);
                });

        body.getNeededResources().forEach(this::logRow);
    }

    private boolean touchesProduct(NeededResourceResponse row, String productName) {
        if (row.getSources() == null) {
            return false;
        }
        return row.getSources().stream()
                .anyMatch(s -> pathNames(s).stream().anyMatch(n -> n.toLowerCase().contains("фаберже") || n.equals(productName)));
    }

    private List<ResourceResponse> findResources(String search) {
        Map<String, Object> params = new HashMap<>();
        params.put("search", search);
        params.put("size", 50);
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.RESOURCE_AUTOCOMPLETE, UserRole.ADMIN, params);
        if (response.statusCode() != 200) {
            log.warn("autocomplete '{}' -> {}", search, response.statusCode());
            return List.of();
        }
        return DatabaseIntegrityValidator.extractList(response, ResourceResponse.class);
    }

    private void logRow(NeededResourceResponse row) {
        String name = row.getResource() != null ? row.getResource().getName() : "?";
        double sourceSum = row.getSources() == null ? 0
                : row.getSources().stream().mapToDouble(s -> s.getAmount() != null ? s.getAmount() : 0).sum();
        log.info("ROW {} produced={} needed={} inStock={} shortage={} sources={} sourceSum={}",
                name, row.isProduced(), row.getNeeded(), row.getInStock(), row.getShortage(),
                row.getSources() != null ? row.getSources().size() : 0, sourceSum);
        if (row.getSources() != null) {
            for (NeededResourceSourceResponse source : row.getSources()) {
                log.info("  amount={} path={}", source.getAmount(), formatPath(source));
            }
        }
    }

    private static List<String> pathNames(NeededResourceSourceResponse source) {
        if (source.getPath() == null) {
            return List.of();
        }
        return source.getPath().stream()
                .map(NeededResourcePathStepResponse::getName)
                .collect(Collectors.toList());
    }

    private static String formatPath(NeededResourceSourceResponse source) {
        return String.join(" → ", pathNames(source));
    }
}
