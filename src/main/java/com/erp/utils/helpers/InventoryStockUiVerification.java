package com.erp.utils.helpers;

import com.erp.pages.UnitManagementPage;
import io.qameta.allure.Allure;
import lombok.experimental.UtilityClass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@UtilityClass
public class InventoryStockUiVerification {

    public static void assertResourceVisible(UnitManagementPage stockPage,
                                             String resourceName,
                                             String stepLabel) {
        Allure.step(stepLabel, () -> {
            Allure.parameter("resourceName", resourceName);
            if (!stockPage.isResourceVisibleInTable(resourceName)) {
                stockPage.attachScreenshot(stepLabel + " — resource not in table");
            }
            assertThat(stockPage.isResourceVisibleInTable(resourceName))
                    .as("Ресурс «%s» має бути видимий у таблиці", resourceName)
                    .isTrue();
        });
    }

    public static void assertResourceAmountGreaterThan(UnitManagementPage stockPage,
                                                       String resourceName,
                                                       double minExclusive,
                                                       String stepLabel) {
        Allure.step(stepLabel, () -> {
            double uiAmount = stockPage.getResourceAmount(resourceName);
            Allure.parameter("resourceName", resourceName);
            Allure.parameter("uiAmount", uiAmount);
            Allure.parameter("minExclusive", minExclusive);
            if (uiAmount <= minExclusive) {
                stockPage.attachScreenshot(stepLabel + " — unexpected amount");
            }
            assertThat(uiAmount)
                    .as("Кількість «%s» на UI має бути більше %.2f", resourceName, minExclusive)
                    .isGreaterThan(minExclusive);
        });
    }

    public static void assertResourceAmountOnPage(UnitManagementPage stockPage,
                                                  String resourceName,
                                                  double expectedAmount,
                                                  String stepLabel) {
        Allure.step(stepLabel, () -> {
            stockPage.searchAndWaitForResource(resourceName, resourceName);
            String uiText = stockPage.getResourceAmountText(resourceName);
            double uiAmount = stockPage.getResourceAmount(resourceName);

            Allure.parameter("resourceName", resourceName);
            Allure.parameter("expectedAmount", expectedAmount);
            Allure.parameter("uiAmountText", uiText);
            Allure.parameter("uiAmount", uiAmount);

            if (Math.abs(uiAmount - expectedAmount) > 0.01) {
                stockPage.attachScreenshot(stepLabel + " — amount mismatch");
            }
            assertThat(uiAmount)
                    .as("Кількість «%s» на UI має відповідати очікуваній", resourceName)
                    .isCloseTo(expectedAmount, within(0.01));
        });
    }

    public static void assertResourceNotVisibleOrZero(UnitManagementPage stockPage,
                                                      String resourceName,
                                                      String stepLabel) {
        Allure.step(stepLabel, () -> {
            stockPage.search(resourceName);
            Allure.parameter("resourceName", resourceName);
            if (stockPage.isResourceVisibleInTable(resourceName)) {
                assertThat(stockPage.getResourceAmount(resourceName))
                        .as("Якщо ресурс лишився в таблиці, кількість має бути 0")
                        .isCloseTo(0.0, within(0.01));
            }
        });
    }
}
