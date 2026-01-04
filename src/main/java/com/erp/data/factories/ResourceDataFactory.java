package com.erp.data.factories;

import com.erp.models.request.ResourceRequest;
import com.erp.data.FakerProvider;
import com.erp.models.response.ResourceResponse;
import lombok.NonNull;

public class ResourceDataFactory {

    // Повертаємо ResourceRequestBuilder
    public static ResourceRequest.ResourceRequestBuilder defaultResource(@NonNull Long measurementUnitId) {
        return ResourceRequest.builder()
                .name(FakerProvider.ukrainian().commerce().productName())
                .measurementUnitId(measurementUnitId);
    }

    /**
     * 🔥 Створює реквест на основі існуючої відповіді.
     * Це дозволяє взяти існуючий об'єкт і змінити в ньому лише одне поле.
     */
    public static ResourceRequest.ResourceRequestBuilder fromExisting(@NonNull ResourceResponse existingResource) {
        return ResourceRequest.builder()
                .name(existingResource.getName())
                .measurementUnitId(existingResource.getUnit().getId());
    }
}