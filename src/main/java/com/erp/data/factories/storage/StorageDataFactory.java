package com.erp.data.factories.storage;


import com.erp.data.FakerProvider;
import com.erp.models.request.StorageRequest;

public class StorageDataFactory {
    // Повертаємо Storage
    public static StorageRequest.StorageRequestBuilder defaultStorage() {
        return StorageRequest.builder()
                .name(FakerProvider.ukrainian().company().name());
    }
}
