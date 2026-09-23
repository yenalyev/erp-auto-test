package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.FaitaResourceFixture;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.models.response.ResourceResponse;
import com.erp.pages.RelocationCreateInputPage;
import com.erp.pages.RelocationPage;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Relocation")
@Feature("Receive form batch accounting")
public class RelocationBatchAccountingUiTest extends BaseUITest {

    private final List<Long> reconciliationIds = new ArrayList<>();

    private FaitaResourceFixture reconciliationFixture;
    private long storageId;
    private String resourceName;
    private String accountingName;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        RelocationFixture relocationFixture = new RelocationFixture(testContext, apiExecutor);
        reconciliationFixture = new FaitaResourceFixture(testContext, apiExecutor);
        ResourceFixture resourceFixture = new ResourceFixture(testContext, apiExecutor);
        relocationFixture.prepareContext();

        storageId = ConfigProvider.getOwner1StorageId();
        List<ResourceResponse> resources = resourceFixture.autocompleteForStorage(
                UserRole.OWNER_1, storageId, "", false);
        assertThat(resources)
                .as("Owner1 storage має повертати хоча б один ресурс в autocomplete")
                .isNotEmpty();
        ResourceResponse resource = resources.getFirst();
        resourceName = resource.getName();
        accountingName = "UI бухгалтерська назва "
                + UUID.randomUUID().toString().substring(0, 8);
        String accountingId = reconciliationFixture.newExternalId("ui-acc-");
        reconciliationIds.addAll(reconciliationFixture.createAccountingReconciliation(
                accountingId, accountingName, resource.getId()));

        injectSessionCookies(cachedSessionCookies(UserRole.OWNER_1), sessionCookieDomain());
        browserContext.addInitScript(
                "localStorage.setItem('selectedStorageId', '" + storageId + "');");
    }

    @AfterClass(alwaysRun = true)
    public void deleteAccountingData() {
        if (reconciliationFixture != null) {
            reconciliationFixture.deleteReconciliationsQuietly(reconciliationIds);
        }
    }

    @Test(priority = 10)
    @TestCaseId("TC-REL-ACC-UI-001")
    @Story("Accounting controls are available for every received resource")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Сума опціональна, невід'ємна і дозволяє не більше двох знаків після коми.")
    public void accountingFieldsAreShownWithTwoDecimalAmountConstraint() {
        RelocationCreateInputPage form = openReceiveForm()
                .selectResourceByName(resourceName);

        assertThat(form.isPaidAmountVisible(0)).isTrue();
        assertThat(form.isAccountingSelectVisible(0)).isTrue();
        assertThat(form.paidAmountMin(0)).isEqualTo("0");
        assertThat(form.paidAmountStep(0))
                .as("HTML number input має обмежувати paidAmount двома десятковими знаками")
                .isEqualTo("0.01");
        form.attachScreenshot("TC-REL-ACC-UI-001 — accounting fields");
    }

    @Test(priority = 20)
    @TestCaseId({"TC-REL-ACC-UI-002", "TC-REL-ACC-UI-003"})
    @Story("Duplicate accounting name for the same resource is blocked")
    @Severity(SeverityLevel.BLOCKER)
    public void duplicateAccountingNameShowsErrorAndDisablesSubmit() {
        RelocationCreateInputPage form = openReceiveForm()
                .selectResourceByName(0, resourceName)
                .fillQuantity(0, "2")
                .selectAccountingName(0, accountingName)
                .clickAddPosition()
                .selectResourceByName(1, resourceName)
                .fillQuantity(1, "3")
                .selectAccountingName(1, accountingName);

        assertThat(form.productRowCount()).isEqualTo(2);
        assertThat(form.isDuplicateAccountingErrorVisible())
                .as("UI показує помилку для дубльованої бухгалтерської назви")
                .isTrue();
        assertThat(form.isSubmitEnabled())
                .as("Форму з дубльованим resource + accountingName не можна підтвердити")
                .isFalse();
        form.attachScreenshot("TC-REL-ACC-UI-002 — duplicate accounting name");
    }

    @Test(priority = 30)
    @TestCaseId("TC-REL-ACC-UI-004")
    @Story("Receive form shows total cost when at least one cost is entered")
    @Severity(SeverityLevel.CRITICAL)
    public void totalCostIsHiddenForEmptyAmountsAndShownForZeroOrPositiveAmount() {
        RelocationCreateInputPage form = openReceiveForm()
                .selectResourceByName(resourceName)
                .fillQuantity(0, "10");

        assertThat(form.isTotalCostVisible())
                .as("Підсумок прихований, поки всі суми порожні")
                .isFalse();

        form.fillPaidAmount(0, "0.00");
        assertThat(form.isTotalCostVisible())
                .as("Введене 0.00 є сумою і має показувати підсумок")
                .isTrue();
        assertThat(form.totalCostText()).contains("0.00");

        form.fillPaidAmount(0, "1200.00");
        assertThat(form.totalCostText()).contains("1200.00");
        form.attachScreenshot("TC-REL-ACC-UI-004 — total cost");
    }

    private RelocationCreateInputPage openReceiveForm() {
        return new RelocationPage(page).open().clickReceive();
    }
}
