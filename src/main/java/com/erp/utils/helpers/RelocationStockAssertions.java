package com.erp.utils.helpers;

import com.erp.api.clients.ApiExecutor;
import com.erp.enums.UserRole;
import io.qameta.allure.Allure;
import lombok.experimental.UtilityClass;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@UtilityClass
public class RelocationStockAssertions {

    public static ProductionStockAssertions.StockSnapshot capture(ApiExecutor apiExecutor,
                                                                  Long storageId,
                                                                  UserRole role,
                                                                  Set<Long> resourceIds,
                                                                  String phaseLabel) {
        return ProductionStockAssertions.capture(apiExecutor, storageId, role, resourceIds, phaseLabel);
    }

    public static void assertStockDelta(ProductionStockAssertions.StockSnapshot before,
                                        ProductionStockAssertions.StockSnapshot after,
                                        Long storageId,
                                        Map<Long, Double> expectedDelta,
                                        String explanation) {
        Allure.step("Перевірка зміни залишків на складі " + storageId + " — " + explanation, () -> {
            Allure.parameter("storageId", storageId);
            ProductionStockAssertions.assertDelta(before, after, expectedDelta, null);
        });
    }

    public static void assertDebitedFromSender(ProductionStockAssertions.StockSnapshot before,
                                               ProductionStockAssertions.StockSnapshot after,
                                               Long senderStorageId,
                                               Long resourceId,
                                               double expectedDebit,
                                               String explanation) {
        Allure.step(String.format(
                "Коректно списано з відправника (склад %d): очікувано −%.2f од. — %s",
                senderStorageId, expectedDebit, explanation), () -> {
            double delta = after.amountOf(resourceId) - before.amountOf(resourceId);
            Allure.parameter("senderStorageId", senderStorageId);
            Allure.parameter("resourceId", resourceId);
            Allure.parameter("expectedDebit", expectedDebit);
            Allure.parameter("actualDelta", delta);
            assertThat(delta)
                    .as("Списання зі складу %d для ресурсу id=%d", senderStorageId, resourceId)
                    .isCloseTo(-expectedDebit, within(0.01));
        });
    }

    public static void assertCreditedToRecipient(ProductionStockAssertions.StockSnapshot before,
                                                 ProductionStockAssertions.StockSnapshot after,
                                                 Long recipientStorageId,
                                                 Long resourceId,
                                                 double expectedCredit,
                                                 String explanation) {
        Allure.step(String.format(
                "Коректно додано на отримувача (склад %d): очікувано +%.2f од. — %s",
                recipientStorageId, expectedCredit, explanation), () -> {
            double delta = after.amountOf(resourceId) - before.amountOf(resourceId);
            Allure.parameter("recipientStorageId", recipientStorageId);
            Allure.parameter("resourceId", resourceId);
            Allure.parameter("expectedCredit", expectedCredit);
            Allure.parameter("actualDelta", delta);
            assertThat(delta)
                    .as("Зарахування на склад %d для ресурсу id=%d", recipientStorageId, resourceId)
                    .isCloseTo(expectedCredit, within(0.01));
        });
    }

    public static void assertUnchanged(ProductionStockAssertions.StockSnapshot before,
                                       ProductionStockAssertions.StockSnapshot after,
                                       Long storageId,
                                       Long resourceId,
                                       String explanation) {
        Allure.step("Залишки на складі " + storageId + " не змінились — " + explanation, () -> {
            double delta = after.amountOf(resourceId) - before.amountOf(resourceId);
            assertThat(delta).as("Залишок не повинен змінитись").isCloseTo(0.0, within(0.01));
        });
    }
}
