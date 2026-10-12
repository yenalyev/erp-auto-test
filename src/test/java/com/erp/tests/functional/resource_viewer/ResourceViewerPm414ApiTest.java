package com.erp.tests.functional.resource_viewer;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.LocationFeature;
import com.erp.enums.LocationProfile;
import com.erp.enums.StorageKind;
import com.erp.enums.UserRole;
import com.erp.fixtures.LocationProfileFixture;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.models.response.PagedResourceRelocationViewerResponse;
import com.erp.models.response.RelocationResponse;
import com.erp.models.response.ResourceRelocationViewerResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageResponse;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.helpers.DatabaseIntegrityValidator;
import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Feature("Відстеження ресурсів: фільтр ПМ 414")
public class ResourceViewerPm414ApiTest extends BaseFunctionalTest {
    private static final String FILTER_PM_414 = "ПМ 414";
    private static final String FILTER_OTHER = "Інші";

    private LocationProfileFixture locationProfileFixture;
    private RelocationFixture relocationFixture;
    private ResourceFixture resourceFixture;
    private Long senderId;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void prepareFixtures() {
        locationProfileFixture = new LocationProfileFixture(testContext, apiExecutor);
        relocationFixture = new RelocationFixture(testContext, apiExecutor);
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        relocationFixture.prepareContext();
        resourceFixture.prepareContext();
        senderId = locationProfileFixture.create(LocationProfile.TSUK_PRODUCTION, 1)
                .locations().getFirst().getId();
    }

    @AfterClass(alwaysRun = true)
    public void cleanupFixtures() {
        if (locationProfileFixture != null) {
            locationProfileFixture.cleanup();
        }
    }

    @TestCaseId("TC-WOLF-001")
    @Test
    @Story("ПМ 414 та конкретний отримувач")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Отримувач у ПМ 414 залишається в перетині фільтрів і не потрапляє до групи Інші.")
    public void fixedPm414ReceiverIntersection() {
        List<StorageResponse> locations = DatabaseIntegrityValidator.extractList(
                apiExecutor.execute(ApiEndpointDefinition.STORAGE_GET_ALL, UserRole.ADMIN),
                StorageResponse.class);
        Map<Long, StorageResponse> byId = locations.stream()
                .collect(Collectors.toMap(StorageResponse::getId, Function.identity(), (left, right) -> left));
        StorageResponse root = locations.stream()
                .filter(s -> FILTER_PM_414.equalsIgnoreCase(s.getName()))
                .findFirst()
                .orElseThrow(() -> new SkipException("Локація «ПМ 414» відсутня у середовищі"));
        Long tsukRootId = byId.get(senderId).getParent().getId();
        List<StorageResponse> receivers = locations.stream()
                .filter(s -> isInTree(s, root.getId(), byId))
                .filter(s -> !isInTree(s, tsukRootId, byId))
                .filter(s -> Boolean.TRUE.equals(s.getActive()) || s.getActive() == null)
                .filter(s -> s.getKind() == StorageKind.LOCATION)
                .filter(s -> s.getFeatures() != null && s.getFeatures().contains(LocationFeature.RELOCATIONS))
                .toList();
        if (receivers.isEmpty()) {
            throw new SkipException("У ПМ 414 немає активного отримувача поза деревом складу-відправника");
        }

        ResourceResponse resource = resourceFixture.createUniqueResource(
                "RVW-PM414-" + System.currentTimeMillis());
        relocationFixture.ensureStock(senderId, resource.getId(), 100.0);
        StorageResponse receiver = receivers.getFirst();
        RelocationResponse sent = relocationFixture.createSend(
                UserRole.ADMIN, senderId, receiver.getId(), resource.getId(), 5.0);

        Map<String, Object> params = new HashMap<>();
        params.put("resourceIds", List.of(resource.getId()));
        params.put("receiverIds", receiver.getId());
        assertThat(journalIds(params)).as("передачу видно за конкретним отримувачем")
                .contains(sent.getId());

        params.put("unit414Pm", FILTER_PM_414);
        assertThat(journalIds(params)).as("ПМ 414 та його отримувач показують передачу")
                .contains(sent.getId());

        params.remove("unit414Pm");
        params.put("unitsOther", FILTER_OTHER);
        assertThat(journalIds(params)).as("Інші не показують передачу отримувачу ПМ 414")
                .doesNotContain(sent.getId());
    }

    private static boolean isInTree(StorageResponse location, Long rootId,
                                    Map<Long, StorageResponse> byId) {
        StorageResponse current = location;
        while (current != null) {
            if (rootId.equals(current.getId())) {
                return true;
            }
            current = current.getParent() == null ? null : byId.get(current.getParent().getId());
        }
        return false;
    }

    private List<Long> journalIds(Map<String, Object> params) {
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.RESOURCE_VIEWER_RELOCATIONS_GET, UserRole.ADMIN, params);
        assertThat(response.statusCode()).isEqualTo(200);
        PagedResourceRelocationViewerResponse page = response.as(PagedResourceRelocationViewerResponse.class);
        if (page.getContent() == null) {
            return List.of();
        }
        return page.getContent().stream()
                .map(ResourceRelocationViewerResponse::getRelocationId)
                .toList();
    }
}
