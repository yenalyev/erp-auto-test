package com.erp.data.factories;

import com.erp.models.request.ResourceRequest;
import com.erp.data.FakerProvider;
import com.erp.models.response.ResourceResponse;
import lombok.NonNull;

public class ResourceDataFactory {

    // Повертаємо ResourceRequestBuilder
    public static ResourceRequest.ResourceRequestBuilder defaultResource(@NonNull Long measurementUnitId,
                                                                        @NonNull Long categoryId) {
        return ResourceRequest.builder()
                .name(FakerProvider.ukrainian().commerce().productName())
                .measurementUnitId(measurementUnitId)
                .categoryId(categoryId);
    }

    public static ResourceRequest uniqueResource(@NonNull String namePrefix,
                                                 @NonNull Long measurementUnitId,
                                                 @NonNull Long categoryId) {
        return defaultResource(measurementUnitId, categoryId)
                .name(namePrefix + com.erp.utils.data.DataUtils.getUniqueSuffix())
                .build();
    }

    /**
     * Створює реквест на основі існуючої відповіді.
     * Це дозволяє взяти існуючий об'єкт і змінити в ньому лише одне поле.
     *
     * @param categoryId required by PUT /resources/{id}
     */
    public static ResourceRequest.ResourceRequestBuilder fromExisting(@NonNull ResourceResponse existingResource,
                                                                     @NonNull Long categoryId) {
        return ResourceRequest.builder()
                .name(existingResource.getName())
                .measurementUnitId(existingResource.getUnit().getId())
                .categoryId(categoryId);
    }
}