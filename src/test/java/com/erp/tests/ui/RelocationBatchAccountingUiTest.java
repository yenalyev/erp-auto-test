package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.FaitaResourceFixture;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.models.response.ResourceResponse;
import com.erp.pages.RelocationCreateInputPage;
import com.erp.pages.RelocationPage;
import com.erp.test_context.ContextKey;
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
    private String supplierName;
    private String accountingName;
    private String secondAccountingName;
    private String normalizedAccountingName;

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
        supplierName = new StorageFixture(testContext, apiExecutor)
                .getById(UserRole.ADMIN, testContext.get(ContextKey.RELOCATION_SUPPLIER_ID))
                .getName();
        accountingName = "UI бухгалтерська назва "
                + UUID.randomUUID().toString().substring(0, 8);
        String accountingId = reconciliationFixture.newExternalId("ui-acc-");
        reconciliationIds.addAll(reconciliationFixture.createAccountingReconciliation(
                accountingId, accountingName, resource.getId()));
        secondAccountingName = "UI інша бухгалтерська назва "
                + UUID.randomUUID().toString().substring(0, 8);
        reconciliationIds.addAll(reconciliationFixture.createAccountingReconciliation(
                reconciliationFixture.newExternalId("ui-acc-second-"),
                secondAccountingName, resource.getId()));
        normalizedAccountingName = "  " + accountingName.toUpperCase(java.util.Locale.ROOT) + "  ";
        reconciliationIds.addAll(reconciliationFixture.createAccountingReconciliation(
                reconciliationFixture.newExternalId("ui-acc-normalized-"),
                normalizedAccountingName, resource.getId()));

        injectSessionCookies(cachedSessionCookies(UserRole.OWNER_1), sessionCookieDomain());
        injectWorkspaceView(UserRole.OWNER_1, storageId);
    }

    @AfterClass(alwaysRun = true)
    public void deleteAccountingData() {
        if (reconciliationFixture != null) {
            reconciliationFixture.deleteReconciliationsQuietly(reconciliationIds);
        }
    }

    @Test(priority = 10)
    @TestCaseId("TC-REL-ACC-UI-001")
    @Story("Receive form limits displayed amounts to two decimal places")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Порожня сума та два десяткові знаки дозволені; введене 1.234 поле показує як 1.23.")
    public void accountingFieldsAreShownWithTwoDecimalAmountConstraint() {
        RelocationCreateInputPage form = openReceiveForm()
                .selectSourceByName(supplierName)
                .selectResourceByName(resourceName)
                .fillQuantity(0, "1");

        assertThat(form.isPaidAmountVisible(0)).isTrue();
        assertThat(form.isAccountingSelectVisible(0)).isTrue();
        assertThat(form.isSubmitEnabled())
                .as("Порожня сума є допустимою")
                .isTrue();
        form.fillPaidAmount(0, "1.23");
        assertThat(form.isSubmitEnabled())
                .as("Два десяткові знаки є допустимими")
                .isTrue();
        form.fillPaidAmount(0, "1.234");
        assertThat(form.paidAmountValue(0))
                .as("Поле обмежує відображення суми двома десятковими знаками")
                .isEqualTo("1.23");
        assertThat(form.isSubmitEnabled())
                .as("Після нормалізації введення форму можна підтвердити")
                .isTrue();
        form.attachScreenshot("TC-REL-ACC-UI-001 — accounting fields");
    }

    @Test(priority = 20)
    @TestCaseId("TC-REL-ACC-UI-002")
    @Story("Duplicate accounting name for the same resource is blocked")
    @Severity(SeverityLevel.BLOCKER)
    public void duplicateAccountingNameShowsErrorAndDisablesSubmit() {
        RelocationCreateInputPage form = openReceiveForm()
                .selectSourceByName(supplierName)
                .selectResourceByName(0, resourceName)
                .fillQuantity(0, "2")
                .selectAccountingName(0, accountingName);
        assertThat(form.isSubmitEnabled())
                .as("Один валідний рядок можна підтвердити")
                .isTrue();
        form.clickAddPosition()
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

    @Test(priority = 21)
    @TestCaseId("TC-REL-ACC-UI-003")
    @Story("Accounting names are compared after trimming and case folding")
    @Severity(SeverityLevel.BLOCKER)
    public void normalizedDuplicateAccountingNameShowsErrorAndDisablesSubmit() {
        RelocationCreateInputPage form = openReceiveForm()
                .selectSourceByName(supplierName)
                .selectResourceByName(0, resourceName)
                .fillQuantity(0, "2")
                .selectAccountingName(0, accountingName);
        assertThat(form.isSubmitEnabled())
                .as("Один валідний рядок можна підтвердити")
                .isTrue();
        form.clickAddPosition()
                .selectResourceByName(1, resourceName)
                .fillQuantity(1, "3")
                .selectAccountingNameExact(1, normalizedAccountingName);

        assertThat(form.isDuplicateAccountingErrorVisible())
                .as("Назви з різним регістром і крайніми пробілами є дублікатом")
                .isTrue();
        assertThat(form.isSubmitEnabled()).isFalse();
        assertThat(form.productRowCount()).isEqualTo(2);
        form.attachScreenshot("TC-REL-ACC-UI-003 — normalized duplicate");
    }

    @Test(priority = 30)
    @TestCaseId("TC-REL-ACC-UI-004")
    @Story("Receive form hides paid total while all amounts are empty")
    @Severity(SeverityLevel.CRITICAL)
    public void totalCostIsHiddenWhenAllAmountsAreEmpty() {
        RelocationCreateInputPage form = openReceiveForm()
                .selectSourceByName(supplierName)
                .selectResourceByName(0, resourceName)
                .fillQuantity(0, "10")
                .selectAccountingName(0, accountingName)
                .clickAddPosition()
                .selectResourceByName(1, resourceName)
                .fillQuantity(1, "2")
                .selectAccountingName(1, secondAccountingName);

        assertThat(form.isPaidTotalVisible())
                .as("Підсумок прихований, поки всі суми порожні")
                .isFalse();
        assertThat(form.isSubmitEnabled())
                .as("Обидві порожні суми є допустимими")
                .isTrue();
    }

    @Test(priority = 31)
    @TestCaseId("TC-REL-ACC-UI-004")
    @Story("Explicit zero shows the paid total")
    @Severity(SeverityLevel.CRITICAL)
    public void explicitZeroShowsTotalCost() {
        RelocationCreateInputPage form = openReceiveForm()
                .selectSourceByName(supplierName)
                .selectResourceByName(resourceName)
                .fillQuantity(0, "10")
                .fillPaidAmount(0, "0.00");

        assertThat(form.isPaidTotalVisible())
                .as("Введене 0.00 є сумою і має показувати підсумок")
                .isTrue();
        assertThat(form.paidTotalValue()).isEqualTo("0.00");
        form.fillPaidAmount(0, "");
        assertThat(form.isPaidTotalVisible())
                .as("Після очищення єдиної суми верхнє поле зникає")
                .isFalse();
    }

    @Test(priority = 32)
    @TestCaseId({"TC-REL-ACC-010", "TC-REL-ACC-UI-004"})
    @Story("Paid total sums entered full costs and ignores empty amounts")
    @Severity(SeverityLevel.CRITICAL)
    public void totalCostSumsFullAmountsWithoutMultiplyingByQuantity() {
        RelocationCreateInputPage form = openReceiveForm()
                .selectSourceByName(supplierName)
                .selectResourceByName(0, resourceName)
                .fillQuantity(0, "10")
                .selectAccountingName(0, accountingName)
                .fillPaidAmount(0, "1200.10")
                .clickAddPosition()
                .selectResourceByName(1, resourceName)
                .fillQuantity(1, "2")
                .selectAccountingName(1, secondAccountingName)
                .fillPaidAmount(1, "300.20");

        assertThat(form.isPaidTotalVisible())
                .as("Підсумок двох заповнених сум має бути видимим")
                .isTrue();
        assertThat(form.paidTotalValue())
                .as("1200.10 + 300.20 = 1500.30; кількості 10 і 2 не змінюють суму")
                .isEqualTo("1500.30");

        form.attachScreenshot("TC-REL-ACC-UI-004 — sum of full costs");
    }

    @Test(priority = 33)
    @TestCaseId("TC-REL-ACC-UI-004")
    @Story("Empty amounts do not change paid total")
    @Severity(SeverityLevel.CRITICAL)
    public void emptyAmountIsIgnoredInTotalCost() {
        RelocationCreateInputPage form = openReceiveForm()
                .selectSourceByName(supplierName)
                .selectResourceByName(0, resourceName)
                .fillQuantity(0, "10")
                .selectAccountingName(0, accountingName)
                .fillPaidAmount(0, "1200.10")
                .clickAddPosition()
                .selectResourceByName(1, resourceName)
                .fillQuantity(1, "2")
                .selectAccountingName(1, secondAccountingName);

        assertThat(form.paidAmountValue(1)).isEmpty();
        assertThat(form.isPaidTotalVisible())
                .as("Порожній другий рядок не приховує введену суму")
                .isTrue();
        assertThat(form.paidTotalValue())
                .as("Порожній другий рядок не додається до підсумку")
                .isEqualTo("1200.10");
        form.attachScreenshot("TC-REL-ACC-UI-004 — empty amount ignored");
    }

    @Test(priority = 40)
    @TestCaseId("TC-REL-ACC-020")
    @Story("Accounting controls use Ukrainian labels and stable decimal format")
    public void accountingLabelsAndDecimalInputFormatAreLocalized() {
        RelocationCreateInputPage form = openReceiveForm()
                .selectResourceByName(resourceName)
                .fillQuantity(0, "1")
                .fillPaidAmount(0, "1234.50");

        assertThat(form.paidAmountPlaceholder(0)).isEqualTo("Оплачено");
        assertThat(form.paidAmountValue(0)).isEqualTo("1234.50");
        form.openAccountingSelect(0);
        assertThat(form.isAccountingSearchPlaceholderVisible())
                .as("Поле вибору бухгалтерської назви має український placeholder")
                .isTrue();
        form.attachScreenshot("TC-REL-ACC-020 — localized accounting controls");
    }

    private RelocationCreateInputPage openReceiveForm() {
        return new RelocationPage(page).open().clickReceive();
    }
}
