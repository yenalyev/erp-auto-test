package com.erp.data.factories.storage;


import com.erp.data.FakerProvider;
import com.erp.models.request.StorageRequest;
import com.erp.models.response.StorageResponse;
import lombok.NonNull;

public class StorageDataFactory {
    // Повертаємо Storage
    public static StorageRequest.StorageRequestBuilder randomStorage() {
        return StorageRequest.builder()
                .name(FakerProvider.ukrainian().company().name());
    }

    /**
     * 🔥 Створює реквест на основі існуючої відповіді.
     * Це дозволяє взяти існуючий об'єкт і змінити в ньому лише одне поле.
     */
    public static StorageRequest.StorageRequestBuilder updateNameFromExisting(
            @NonNull StorageResponse existingStorage,
            String newName) {
        return StorageRequest.builder()
                .name(newName);
    }
}
