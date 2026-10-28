package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.InventoryNameFilterFixture;
import com.erp.pages.InventoryNameFilterPage;
import com.erp.utils.assertions.InventoryProcessExcelAssertions;
import com.erp.utils.assertions.InventoryProcessExcelAssertions.Difference;
import io.qameta.allure.*;
import org.testng.annotations.*;
import java.io.*;
import java.util.*;

@Epic("Inventory")
@Feature("REQ-INV-NAME")
@Story("Інвентаризація → Закриті — пошук та Excel у браузері")
public class InventoryNameFilterUiTest extends BaseUITest {
    private InventoryNameFilterFixture data;
    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        data = new InventoryNameFilterFixture(testContext, apiExecutor);
        data.arrange();
    }
    @AfterMethod(alwaysRun = true)
    public void unchanged() { if (data != null) data.assertUnchanged(); }
    @AfterClass(alwaysRun = true)
    public void cleanup() { if (data != null) data.cleanup(); }

    @Test
    @TestCaseId("TC-INV-NAME-UI-001")
    @Description("Видиме поле Назва ресурсу: повна назва → тільки A; частина назви кирилицею у верхньому регістрі → A та Б, без Кабелю у тому самому процесі.")
    public void fullAndPartialUnicodeNameShowOnlyMatchingDifferences() {
        var history = open();
        history.search(data.alpha.getName()).expectProcesses(data.mixed.getId());
        history.expectDifferences(data.mixed.getId(), data.alpha.getName());
        history.search("  АКУМУЛЯТОР  ").expectProcesses(data.mixed.getId());
        history.expectDifferences(data.mixed.getId(), data.alpha.getName(), data.beta.getName());
        history.attachScreenshot("TC-INV-NAME-UI-001 — only matching resource differences");
    }

    @Test
    @TestCaseId("TC-INV-NAME-UI-002")
    @Description("Зміна A → Кабель → немає збігів → очищення. Точні process ID, відновлення всіх 3 розбіжностей і рядків без diff; пробіли трактуються як порожній пошук.")
    public void changedEmptyAndClearedNameRestoreTheCompleteTable() {
        var history = open();
        history.search(data.alpha.getName()).expectProcesses(data.mixed.getId());
        history.search(data.cable.getName()).expectProcesses(data.mixed.getId(), data.cableOnly.getId());
        history.expectDifferences(data.mixed.getId(), data.cable.getName());
        history.expectDifferences(data.cableOnly.getId(), data.cable.getName());
        history.search("missing-" + data.marker).expectProcesses();
        history.attachScreenshot("TC-INV-NAME-UI-002 — no matching inventories");
        history.search("   ").expectProcesses(data.mixed.getId(), data.cableOnly.getId(), data.manual.getId(), data.rejected.getId());
        history.expectDifferences(data.mixed.getId(), data.alpha.getName(), data.beta.getName(), data.cable.getName());
    }

    @Test
    @TestCaseId("TC-INV-NAME-UI-003")
    @Description("Реальна кнопка Excel: A → файл з A; Кабель → новий файл з двома процесами. Перевіряються resourceName у запиті й точні process/resource/delta у XLSX.")
    public void excelButtonUsesTheCurrentNameAfterChangingTheFilter() throws IOException {
        var history = open();
        history.search(data.alpha.getName()).expectProcesses(data.mixed.getId());
        history.expectDifferences(data.mixed.getId(), data.alpha.getName());
        excel(history, data.alpha.getName(), List.of(new Difference(data.mixed.getId(), data.alpha.getName(), 2.25)));
        history.search(data.cable.getName()).expectProcesses(data.mixed.getId(), data.cableOnly.getId());
        history.expectDifferences(data.mixed.getId(), data.cable.getName());
        excel(history, data.cable.getName(), List.of(new Difference(data.mixed.getId(), data.cable.getName(), 4.0),
                new Difference(data.cableOnly.getId(), data.cable.getName(), 1.0)));
        history.attachScreenshot("TC-INV-NAME-UI-003 — current name exported");
    }

    @Test
    @TestCaseId("TC-INV-NAME-UI-004")
    @Description("Excel без збігів має тільки заголовки; після очищення реальна кнопка повертає всі 6 рядків, зокрема процеси без розбіжностей.")
    public void emptyExcelAndResetMatchTheVisibleSelection() throws IOException {
        var history = open();
        history.search("missing-" + data.marker).expectProcesses();
        excel(history, "missing-" + data.marker, List.of());
        history.search("").expectProcesses(data.mixed.getId(), data.cableOnly.getId(), data.manual.getId(), data.rejected.getId());
        excel(history, "", InventoryProcessExcelAssertions.all(data));
    }

    private InventoryNameFilterPage open() {
        injectSessionCookies(cachedSessionCookies(UserRole.ADMIN), sessionCookieDomain());
        injectWorkspaceView(UserRole.ADMIN, data.storage.getId());
        return new InventoryNameFilterPage(page).open()
                .expectProcesses(data.mixed.getId(), data.cableOnly.getId(), data.manual.getId(), data.rejected.getId());
    }

    private void excel(InventoryNameFilterPage history, String search, List<Difference> expected) throws IOException {
        var bytes = history.download(search);
        Allure.addAttachment("Inventory processes filtered Excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new ByteArrayInputStream(bytes), ".xlsx");
        InventoryProcessExcelAssertions.assertWorkbook(bytes, expected);
    }
}
