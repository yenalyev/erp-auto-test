package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.ResourceFixture;
import com.erp.models.response.ResourceResponse;
import com.erp.pages.ResourceEditPage;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.AllureHelper;
import io.qameta.allure.Allure;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI: кнопка «Видалити фото» на формі ресурсу реально прибирає фото після збереження.
 */
@Slf4j
@Epic("Master Data")
@Feature("Resources dictionary")
public class ResourcePhotoUiTest extends BaseUITest {

    private ResourceFixture resourceFixture;
    private long storageId;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        resourceFixture.fetchSharedUnit(3);
        resourceFixture.fetchSharedResourceCategory();
        storageId = ConfigProvider.getOwner1StorageId();
    }

    @Test
    @TestCaseId("TC-UI-RES-030")
    @Story("Delete resource photo")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Ресурс — видалити фото не видаляє фото (регресія):
            API створює ресурс із фото, UI /resources/update/{id} натискає «Видалити фото»
            і «Зберегти». Після повторного відкриття форми прев’ю немає,
            GET /resources/{id}/image → 404.
            """)
    public void deletePhotoPersistsAfterSave() {
        ResourceResponse resource = Allure.step("API: ресурс з фото", () -> {
            ResourceResponse created = resourceFixture.createUniqueResource("ui-res-photo-");
            resourceFixture.uploadPng(UserRole.ADMIN, created.getId());
            ResourceResponse withPhoto = resourceFixture.getById(UserRole.ADMIN, created.getId());
            assertThat(withPhoto.getImagePath())
                    .as("Arrange: ресурс має imagePath після upload")
                    .isNotBlank();
            return created;
        });
        Long resourceId = resource.getId();
        Allure.parameter("resourceId", resourceId);
        Allure.parameter("name", resource.getName());
        Allure.parameter("storageId", storageId);

        injectRoleSession(UserRole.ADMIN, storageId);

        ResourceEditPage editPage = Allure.step("UI: відкрити форму з прев’ю фото", () -> {
            ResourceEditPage pageObject = new ResourceEditPage(page).open(resourceId, storageId);
            pageObject.waitForPhotoAssigned();
            pageObject.attachScreenshot("TC-UI-RES-030 — photo before delete");
            return pageObject;
        });

        Allure.step("UI: Видалити фото і Зберегти", () -> {
            editPage.clickDeletePhoto();
            assertThat(editPage.isPhotoAssigned())
                    .as("Кнопка «Видалити фото» зникає одразу після кліку")
                    .isFalse();
            editPage.fillEmptyRequiredSelects();
            editPage.saveAndReturnToList();
        });

        Allure.step("UI: після reload фото відсутнє", () -> {
            ResourceEditPage reopened = new ResourceEditPage(page).open(resourceId, storageId);
            assertThat(reopened.isPhotoAssigned())
                    .as("Після збереження фото не повинно повернутися на форму")
                    .isFalse();
            reopened.attachScreenshot("TC-UI-RES-030 — photo after delete");
        });

        Allure.step("API: GET image → 404", () -> {
            Response image = resourceFixture.getImage(UserRole.ADMIN, resourceId);
            AllureHelper.attachResponseDetails(image);
            assertThat(image.statusCode())
                    .as("Бекенд не повинен віддавати видалене фото")
                    .isEqualTo(404);
            ResourceResponse after = resourceFixture.getById(UserRole.ADMIN, resourceId);
            Allure.addAttachment("GET by id imagePath", String.valueOf(after.getImagePath()));
            assertThat(after.getImagePath())
                    .as("GET by id без imagePath")
                    .isNullOrEmpty();
        });
    }

    private void injectRoleSession(UserRole role, long selectedStorageId) {
        Map<String, String> cookies = cachedSessionCookies(role);
        injectSessionCookies(cookies, sessionCookieDomain());
        browserContext.addInitScript(
                "localStorage.setItem('selectedStorageId', '" + selectedStorageId + "');");
        if (page != null && !page.isClosed()) {
            page.close();
        }
        page = browserContext.newPage();
        int timeoutMs = ConfigProvider.getUiTimeoutSeconds() * 1000;
        page.setDefaultTimeout(timeoutMs);
        page.setDefaultNavigationTimeout(timeoutMs);
    }
}
