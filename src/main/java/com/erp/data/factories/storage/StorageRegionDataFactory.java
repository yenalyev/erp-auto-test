package com.erp.data.factories.storage;

import com.erp.enums.StorageAccessMode;
import com.erp.models.request.StorageRegionRequest;
import com.erp.models.response.StorageResponse;
import lombok.NonNull;

public class StorageRegionDataFactory {

    public static String uniqueRegionName(String prefix) {
        return StorageDataFactory.uniqueName(prefix);
    }

    public static StorageRegionRequest.StorageRegionRequestBuilder region(
            @NonNull String name,
            @NonNull StorageAccessMode accessMode,
            @NonNull Long recipientStorageId) {
        return StorageRegionRequest.builder()
                .name(name)
                .accessMode(accessMode)
                .recipientStorage(recipientStorageId);
    }

    public static StorageRegionRequest createRegion(
            @NonNull StorageResponse recipient,
            @NonNull StorageAccessMode accessMode,
            String namePrefix) {
        return region(uniqueRegionName(namePrefix), accessMode, recipient.getId()).build();
    }

    public static StorageRegionRequest updateRegion(
            @NonNull String newName,
            @NonNull StorageAccessMode accessMode,
            @NonNull Long recipientStorageId) {
        return region(newName, accessMode, recipientStorageId).build();
    }
}
