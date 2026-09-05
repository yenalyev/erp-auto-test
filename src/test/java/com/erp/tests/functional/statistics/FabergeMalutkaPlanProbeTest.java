package com.erp.tests.functional.statistics;

import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.fixtures.PlanNeededResourcesFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.models.request.ExecutionFilterRequest;
import com.erp.models.response.NeededResourcePathStepResponse;
import com.erp.models.response.NeededResourceResponse;
import com.erp.models.response.NeededResourceSourceResponse;
import com.erp.models.response.PlanNeededResourcesResponse;
import com.erp.models.response.PlanResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.helpers.DatabaseIntegrityValidator;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class FabergeMalutkaPlanProbeTest extends BaseFunctionalTest {

    private static final long PRODUCT_ID = 2480L;
    private static final String STORAGE_QUERY = "Малютка";
    private static final double PLAN_QTY = 100.0;
    private static final long FALLBACK_STORAGE_ID = 33L;

    private PlanNeededResourcesFixture fixture;
    private StorageFixture storageFixture;
    private final StringBuilder report = new StringBuilder();

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void setup() {
        fixture = new PlanNeededResourcesFixture(testContext, apiExecutor);
        fixture.prepareContext();
        storageFixture = new StorageFixture(testContext, apiExecutor);
    }

    @Test
    public void ensurePlanOnMalutkaAndAnalyzeNeededResources() throws IOException {
        ResourceResponse product = findProduct();
        append("Product id=%d name=%s%n".formatted(product.getId(), product.getName()));

        List<StorageResponse> candidates = findMalutkaCandidates();
        append("Storages matching '%s': %d%n".formatted(STORAGE_QUERY, candidates.size()));
        for (StorageResponse s : candidates) {
            append("  id=%d name=%s alias=%s type=%s parent=%s%n".formatted(
                    s.getId(), s.getName(), s.getAlias(), s.getType(),
                    s.getParent() != null ? s.getParent().getName() : "-"));
        }

        Long storageId = resolveStorageWithTechMap(candidates, product.getId());
        if (storageId == null) {
            storageId = resolveStorageWithTechMap(
                    List.of(StorageResponse.builder().id(FALLBACK_STORAGE_ID).name("fallback-33").build()),
                    product.getId());
        }
        if (storageId == null) {
            throw new AssertionError("No storage with PRODUCTION tech map for product " + product.getId()
                    + "; candidates=" + candidates.stream().map(StorageResponse::getId).toList());
        }
        append("Selected storageId=%d%n".formatted(storageId));

        YearMonth period = YearMonth.now();
        PlanResponse plan = ensureCurrentMonthPlan(storageId, product.getId(), period, PLAN_QTY);
        append("Plan id=%d period=%d/%d qty=%.0f outputs=%s%n".formatted(
                plan.getId(), period.getYear(), period.getMonthValue(), PLAN_QTY,
                plan.getOutput() == null ? "[]" : plan.getOutput().stream()
                        .map(o -> o.getResource().getName() + "=" + o.getAmount())
                        .toList()));

        ExecutionFilterRequest filter = ExecutionFilterRequest.builder()
                .month(period.getMonthValue())
                .year(period.getYear())
                .includeStock(true)
                .includeProduced(false)
                .build();

        PlanNeededResourcesResponse body = fixture.requestNeeded(UserRole.ADMIN, storageId, filter);
        append("Needed rows=%d%n".formatted(body.getNeededResources().size()));

        body.getNeededResources().stream()
                .filter(row -> touchesFaberge(row) || isPetg(row))
                .forEach(this::appendRowWithDuplicateCheck);

        Path out = Path.of("target", "faberge-malutka-report.txt");
        Files.writeString(out, report.toString(), StandardCharsets.UTF_8);
        log.info("Report written to {}", out.toAbsolutePath());
    }

    private void append(String line) {
        report.append(line);
        log.info(line.strip());
    }

    private List<StorageResponse> findMalutkaCandidates() {
        List<StorageResponse> matches = storageFixture.getNames(UserRole.ADMIN, true, STORAGE_QUERY);
        if (matches.isEmpty()) {
            return List.of();
        }
        List<StorageResponse> exact = matches.stream()
                .filter(s -> s.getName() != null && s.getName().equalsIgnoreCase(STORAGE_QUERY))
                .toList();
        if (!exact.isEmpty()) {
            return exact;
        }
        return matches.stream()
                .filter(s -> s.getName() != null && s.getName().toLowerCase().contains(STORAGE_QUERY.toLowerCase()))
                .toList();
    }

    private Long resolveStorageWithTechMap(List<StorageResponse> candidates, Long productId) {
        for (StorageResponse candidate : candidates) {
            if (candidate.getId() == null) {
                continue;
            }
            if (hasProductionMapForOutput(candidate.getId(), productId)) {
                append("Tech map for product found on storage id=%d name=%s%n"
                        .formatted(candidate.getId(), candidate.getName()));
                return candidate.getId();
            }
            append("No tech map for product on storage id=%d name=%s%n"
                    .formatted(candidate.getId(), candidate.getName()));
        }
        return null;
    }

    private boolean hasProductionMapForOutput(Long storageId, Long productId) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.TECH_MAP_GET_BY_STORAGE,
                UserRole.ADMIN,
                null,
                String.valueOf(storageId));
        if (response.statusCode() != 200) {
            return false;
        }
        List<TechnologicalMapResponse> maps = DatabaseIntegrityValidator.extractList(response, TechnologicalMapResponse.class);
        return maps.stream()
                .filter(m -> m.getOutput() != null)
                .flatMap(m -> m.getOutput().stream())
                .anyMatch(o -> o.getResource() != null && productId.equals(o.getResource().getId()));
    }

    private ResourceResponse findProduct() {
        Map<String, Object> params = new HashMap<>();
        params.put("search", "Фаберже 1.5");
        params.put("size", 20);
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.RESOURCE_AUTOCOMPLETE, UserRole.ADMIN, params);
        List<ResourceResponse> list = DatabaseIntegrityValidator.extractList(response, ResourceResponse.class);
        return list.stream()
                .filter(r -> r.getId() != null && r.getId().equals(PRODUCT_ID))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Product id=" + PRODUCT_ID + " not found"));
    }

    private PlanResponse ensureCurrentMonthPlan(Long storageId, Long resourceId, YearMonth period, double qty) {
        List<PlanResponse> existing = fixture.techMaps().getLocationPlans(storageId).stream()
                .filter(p -> p.getMonth() == period.getMonthValue() && p.getYear() == period.getYear())
                .toList();
        if (!existing.isEmpty()) {
            PlanResponse plan = existing.getFirst();
            boolean hasProduct = plan.getOutput() != null && plan.getOutput().stream()
                    .anyMatch(o -> o.getResource() != null && resourceId.equals(o.getResource().getId()));
            if (hasProduct) {
                append("Reusing plan id=%d%n".formatted(plan.getId()));
                return plan;
            }
        }
        return fixture.createPlan(storageId, resourceId, period, qty);
    }

    private boolean touchesFaberge(NeededResourceResponse row) {
        return row.getSources() != null && row.getSources().stream()
                .anyMatch(s -> pathNames(s).stream().anyMatch(n -> n.toLowerCase().contains("фаберже")));
    }

    private boolean isPetg(NeededResourceResponse row) {
        return row.getResource() != null && row.getResource().getName() != null
                && row.getResource().getName().toLowerCase().contains("petg");
    }

    private void appendRowWithDuplicateCheck(NeededResourceResponse row) {
        appendRow(row);
        if (row.getSources() == null || row.getSources().size() < 2) {
            return;
        }
        List<List<String>> paths = row.getSources().stream().map(this::pathNames).toList();
        for (List<String> longPath : paths) {
            if (longPath.size() < 2) {
                continue;
            }
            List<String> tail = longPath.subList(1, longPath.size());
            for (List<String> other : paths) {
                if (other.equals(tail)) {
                    append("DUPLICATE: %s vs %s%n".formatted(
                            String.join(" → ", longPath), String.join(" → ", tail)));
                }
            }
        }
    }

    private void appendRow(NeededResourceResponse row) {
        String name = row.getResource() != null ? row.getResource().getName() : "?";
        double sourceSum = row.getSources() == null ? 0
                : row.getSources().stream().mapToDouble(s -> s.getAmount() != null ? s.getAmount() : 0).sum();
        append("ROW %s needed=%.2f sources=%d sourceSum=%.2f%n".formatted(
                name, row.getNeeded(), row.getSources() != null ? row.getSources().size() : 0, sourceSum));
        if (row.getSources() != null) {
            for (NeededResourceSourceResponse source : row.getSources()) {
                append("  %.2f %s%n".formatted(source.getAmount(), String.join(" → ", pathNames(source))));
            }
        }
    }

    private List<String> pathNames(NeededResourceSourceResponse source) {
        if (source.getPath() == null) {
            return List.of();
        }
        return new ArrayList<>(source.getPath().stream()
                .map(NeededResourcePathStepResponse::getName)
                .collect(Collectors.toList()));
    }
}
