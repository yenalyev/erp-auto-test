package com.erp.data.factories.measurement_unit;

import com.erp.data.FakerProvider;
import com.erp.models.response.MeasurementUnitResponse;
import lombok.NonNull;

public class MeasurementUnitResponseDataFactory {

    public static MeasurementUnitResponse.MeasurementUnitResponseBuilder defaultMeasurementUnit() {
        return MeasurementUnitResponse.builder()
                .name(FakerProvider.ukrainian().commerce().material())
                .shortName(FakerProvider.ukrainian().commerce().color());
    }

    public static MeasurementUnitResponse.MeasurementUnitResponseBuilder create(String name,
                                                                                String shortName) {
        return MeasurementUnitResponse.builder()
                .name(name)
                .shortName(shortName);
    }

    /**
     * 🔥 Створює реквест на основі існуючої відповіді.
     * Це дозволяє взяти існуючий об'єкт і змінити в ньому лише одне поле.
     */
    public static MeasurementUnitResponse.MeasurementUnitResponseBuilder fromExisting(@NonNull MeasurementUnitResponse unitResponse) {
        return MeasurementUnitResponse.builder()
                .name(unitResponse.getName())
                .shortName(unitResponse.getShortName());
    }
}
