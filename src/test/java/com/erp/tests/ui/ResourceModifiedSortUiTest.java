package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.ResourceModifiedSortFixture;
import com.erp.pages.ResourcesListPage;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.*;
import org.testng.annotations.*;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Master Data")
@Feature("Resources — сортування за Змінено")
public class ResourceModifiedSortUiTest extends BaseUITest {
    private ResourceModifiedSortFixture fixture;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void prepareSortData() {
        fixture = new ResourceModifiedSortFixture(testContext, apiExecutor);
        fixture.prepare();
        injectSessionCookies(cachedSessionCookies(UserRole.ADMIN), sessionCookieDomain());
        injectWorkspaceView(UserRole.ADMIN, fixture.storageId());
    }

    @AfterClass(alwaysRun = true)
    public void cleanupSortData() { if (fixture != null) fixture.cleanup(); }

    @Test
    @TestCaseId("TC-RES-SORT-UI-001")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Ресурси: кліки Змінено → ASC, DESC, скидання; HTTP 200, точний порядок рядків і дат, жодного 5xx.")
    public void modifiedHeaderSortsBothDirectionsAndResets() {
        List<String> serverErrors = new ArrayList<>();
        page.onResponse(response -> {
            if (response.url().contains("/api/v1/resources?") && response.status() >= 500) {
                serverErrors.add(response.status() + " " + response.url());
            }
        });
        page.navigate(ConfigProvider.getBaseUrl() + "/resources");
        var dictionary = new ResourcesListPage(page).waitForLoaded().searchAndWait(fixture.marker);
        assertThat(dictionary.getVisibleResourceNames()).hasSize(3);
        String zone = (String) page.evaluate("Intl.DateTimeFormat().resolvedOptions().timeZone");
        var formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.of(zone));
        for (String direction : List.of("ASC", "DESC")) {
            var response = dictionary.clickModifiedSort(direction);
            Allure.addAttachment("Resource list " + direction, "application/json", response.text());
            assertThat(response.status()).isEqualTo(200);
            assertThat(response.url()).contains("storageId=" + fixture.storageId());
            assertThat(ResourceModifiedSortFixture.rows(response.text())).isEqualTo(fixture.expected(direction));
            List<String> names = fixture.expected(direction).stream().map(ResourceModifiedSortFixture.Row::name).toList();
            page.waitForCondition(() -> dictionary.getVisibleResourceNames().equals(names));
            assertThat(dictionary.getVisibleResourceNames()).containsExactlyElementsOf(names);
            assertThat(dictionary.getVisibleModifiedValues()).containsExactlyElementsOf(fixture.expected(direction)
                    .stream().map(row -> formatter.format(row.updatedAt())).toList());
            attachScreenshot("Змінено " + direction);
        }
        var reset = dictionary.clickModifiedSort(null);
        assertThat(reset.status()).isEqualTo(200);
        List<String> names = fixture.expected("ASC").stream().map(ResourceModifiedSortFixture.Row::name).sorted().toList();
        page.waitForCondition(() -> dictionary.getVisibleResourceNames().equals(names));
        assertThat(dictionary.getVisibleResourceNames()).containsExactlyElementsOf(names);
        assertThat(serverErrors).as("Сортування ресурсів не спричиняє HTTP 5xx").isEmpty();
        attachScreenshot("Змінено — скидання сортування");
    }
}
