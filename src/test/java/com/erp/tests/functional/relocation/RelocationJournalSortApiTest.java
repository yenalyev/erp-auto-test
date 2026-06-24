package com.erp.tests.functional.relocation;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.relocation.RelocationDataFactory;
import com.erp.enums.UserRole;
import com.erp.fixtures.RelocationFixture;
import com.erp.models.query.RelocationJournalQuery;
import com.erp.models.response.RelocationResponse;
import com.erp.test_context.ContextKey;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.DatabaseIntegrityValidator;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression: GET /relocations with journal filters and sort by «Від» / «До» columns.
 */
@Slf4j
@Epic("Relocation")
@Feature("Relocation journal — filter and sort")
public class RelocationJournalSortApiTest extends BaseFunctionalTest {

    private RelocationFixture fixture;
    private Long owner1Storage;
    private Long resourceId;
    private Long categoryId;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void setupRelocationJournalSortTests() {
        fixture = new RelocationFixture(testContext, apiExecutor);
        fixture.prepareContext();
        owner1Storage = ConfigProvider.getOwner1StorageId();
        resourceId = testContext.get(ContextKey.RELOCATION_RESOURCE_ID);
        categoryId = fixture.getSharedCategoryId();

        fixture.createExternalReceive(
                UserRole.OWNER_1,
                owner1Storage,
                resourceId,
                5.0,
                RelocationDataFactory.uniqueBatchNumber());
        log.info("Relocation journal sort tests ready — storageId={}, categoryId={}", owner1Storage, categoryId);
    }

    @BeforeMethod(alwaysRun = true)
    public void ensureStock() {
        fixture.ensureStock(owner1Storage, resourceId, 200.0);
    }

    @Test(priority = 1)
    @TestCaseId("TC-REL-007")
    @Story("Filter + sort on received history")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Отримано + фільтр категорії + сортування за «До» (recipient.name ASC)")
    public void receivedHistoryCategoryFilterSortByRecipientNameAsc() {
        RelocationJournalQuery query = RelocationJournalQuery.receivedHistoryUi(owner1Storage)
                .toBuilder()
                .categoryId(categoryId)
                .sortField(RelocationJournalQuery.SortField.RECIPIENT_NAME)
                .build();

        verifyFilterWithSort(query);
    }

    @Test(priority = 2)
    @TestCaseId("TC-REL-008")
    @Story("Filter + sort on received history")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Отримано + фільтр категорії + сортування за «Від» (sender.name ASC)")
    public void receivedHistoryCategoryFilterSortBySenderNameAsc() {
        RelocationJournalQuery query = RelocationJournalQuery.receivedHistoryUi(owner1Storage)
                .toBuilder()
                .categoryId(categoryId)
                .sortField(RelocationJournalQuery.SortField.SENDER_NAME)
                .build();

        verifyFilterWithSort(query);
    }

    @Test(priority = 3)
    @TestCaseId("TC-REL-009")
    @Story("Filter + sort on received history")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Отримано + фільтр продукту + сортування за «До» (recipient.name DESC)")
    public void receivedHistoryProductFilterSortByRecipientNameDesc() {
        RelocationJournalQuery query = RelocationJournalQuery.receivedHistoryUi(owner1Storage)
                .toBuilder()
                .productId(resourceId)
                .sortField(RelocationJournalQuery.SortField.RECIPIENT_NAME)
                .sortDesc(true)
                .build();

        verifyFilterWithSort(query);
    }

    @Test(priority = 4)
    @TestCaseId("TC-REL-047")
    @Story("Filter + sort on sent history")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Видано + фільтр категорії + сортування за «Від» (sender.name ASC)")
    public void sentHistoryCategoryFilterSortBySenderNameAsc() {
        RelocationJournalQuery query = RelocationJournalQuery.sentHistoryUi(owner1Storage)
                .toBuilder()
                .categoryId(categoryId)
                .sortField(RelocationJournalQuery.SortField.SENDER_NAME)
                .build();

        verifyFilterWithSort(query);
    }

    @Test(priority = 5)
    @TestCaseId("TC-REL-048")
    @Story("Filter + sort on sent history")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Видано + фільтр продукту + сортування за «До» (recipient.name ASC)")
    public void sentHistoryProductFilterSortByRecipientNameAsc() {
        RelocationJournalQuery query = RelocationJournalQuery.sentHistoryUi(owner1Storage)
                .toBuilder()
                .productId(resourceId)
                .sortField(RelocationJournalQuery.SortField.RECIPIENT_NAME)
                .build();

        verifyFilterWithSort(query);
    }

    @Step("GET /relocations з фільтром і сортуванням — очікується HTTP 200")
    private void verifyFilterWithSort(RelocationJournalQuery query) {

        Allure.parameter("queryParams", query.toQueryParams().toString());

        Response response = Allure.step("GET relocations with filter + sort (Admin)", () ->
                fixture.getJournalPageResponse(query, UserRole.ADMIN));

        assertThat(response.statusCode())
                .as("Фільтр + сортування не повинні повертати 500")
                .isEqualTo(200);
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.RELOCATION_GET_PAGE);

        List<RelocationResponse> page = DatabaseIntegrityValidator.extractList(response, RelocationResponse.class);
        assertThat(page).isNotNull();
        Allure.parameter("contentSize", page.size());
    }
}
