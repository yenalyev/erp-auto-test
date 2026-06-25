package com.erp.data.factories.storage;

import com.erp.data.FakerProvider;
import com.erp.enums.StorageAccessMode;
import com.erp.enums.StorageRelation;
import com.erp.enums.UnitType;
import com.erp.models.request.StorageRequest;
import com.erp.models.response.StorageResponse;
import com.erp.utils.data.DataUtils;
import lombok.NonNull;

import java.util.function.Consumer;

public class StorageDataFactory {

    public static final int TEXT_FIELD_MAX_LENGTH = 255;

    public static String exactLengthString(int length) {
        return "x".repeat(Math.max(0, length));
    }

    public static String uniqueNameAtMaxLength() {
        return DataUtils.generateWithUniqueSuffix(TEXT_FIELD_MAX_LENGTH);
    }

    public static String uniqueTextAtMaxLength() {
        return DataUtils.generateWithUniqueSuffix(TEXT_FIELD_MAX_LENGTH);
    }

    /** Короткий alias для функціональних тестів (не length-boundary). */
    public static String shortAlias() {
        String alias = "a" + System.currentTimeMillis();
        int maxShort = 20;
        return alias.length() <= maxShort ? alias : alias.substring(alias.length() - maxShort);
    }

    public static String aliasAtMaxLength() {
        return exactLengthString(TEXT_FIELD_MAX_LENGTH);
    }

    public static String uniqueName(String prefix) {
        return DataUtils.makeUnique(prefix + "-loc");
    }

    public static StorageRequest.StorageRequestBuilder randomStorage() {
        return StorageRequest.builder()
                .name(FakerProvider.ukrainian().company().name());
    }

    public static StorageRequest.StorageRequestBuilder childStorage(@NonNull Long parentId) {
        return StorageRequest.builder()
                .name(FakerProvider.ukrainian().company().name())
                .type(UnitType.STORAGE)
                .parentId(parentId)
                .relation(StorageRelation.INTERNAL)
                .accessMode(StorageAccessMode.FULL_ACCESS);
    }

    public static StorageRequest.StorageRequestBuilder childStorage(@NonNull Long parentId, String namePrefix) {
        return childStorage(parentId).name(uniqueName(namePrefix));
    }

    /**
     * Rebuilds a PUT request from an existing storage, preserving fields required by the backend validator.
     */
    public static StorageRequest.StorageRequestBuilder fromExisting(@NonNull StorageResponse existing) {
        StorageRequest.StorageRequestBuilder builder = StorageRequest.builder()
                .name(existing.getName())
                .alias(existing.getAlias())
                .identifierNumber(existing.getIdentifierNumber())
                .nameForInvoices(existing.getNameForInvoices());

        if (existing.getParent() != null) {
            builder.parentId(existing.getParent().getId());
        }
        if (existing.getType() != null) {
            builder.type(UnitType.valueOf(existing.getType()));
        } else {
            builder.type(UnitType.STORAGE);
        }
        if (existing.getRelation() != null) {
            builder.relation(StorageRelation.valueOf(existing.getRelation()));
        } else {
            builder.relation(StorageRelation.INTERNAL);
        }
        if (existing.getAccessMode() != null) {
            builder.accessMode(StorageAccessMode.valueOf(existing.getAccessMode()));
        } else {
            builder.accessMode(StorageAccessMode.FULL_ACCESS);
        }
        return builder;
    }

    public static StorageRequest updateFromExisting(
            @NonNull StorageResponse existing,
            Consumer<StorageRequest.StorageRequestBuilder> customizer) {
        StorageRequest.StorageRequestBuilder builder = fromExisting(existing);
        if (customizer != null) {
            customizer.accept(builder);
        }
        return builder.build();
    }

    public static StorageRequest updateNameFromExisting(
            @NonNull StorageResponse existingStorage,
            String newName) {
        return fromExisting(existingStorage).name(newName).build();
    }

    /**
     * Повне PUT-тіло як у UI: нові значення для всіх полів, крім {@code relation}.
     */
    public static StorageRequest buildUpdateAllExceptRelation(
            @NonNull StorageResponse existing,
            @NonNull Long newParentId) {
        StorageRelation unchangedRelation = existing.getRelation() != null
                ? StorageRelation.valueOf(existing.getRelation())
                : StorageRelation.INTERNAL;
        UnitType newType = existing.getType() != null
                ? UnitType.valueOf(existing.getType())
                : UnitType.STORAGE;

        return StorageRequest.builder()
                .name(uniqueName("upd-"))
                .alias(shortAlias())
                .parentId(newParentId)
                .type(newType)
                .relation(unchangedRelation)
                .identifierNumber("111")
                .accessMode(StorageAccessMode.REGIONS)
                .nameForInvoices("Inv-" + shortAlias())
                .build();
    }
}
