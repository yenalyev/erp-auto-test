package com.erp.data.factories.measurement_unit;

import com.erp.api.endpoints.ApiEndpointDefinition;

import static com.erp.data.RequestBodyFactory.register;

public class MeasurementUnitResponseBodyFactory {
    public static void registerStrategies() {
        // POST /api/v1/measurement-unit
        register(ApiEndpointDefinition.MEASUREMENT_UNIT_POST_CREATE, context -> {
                    return MeasurementUnitResponseDataFactory.defaultMeasurementUnit().build();
                }
        );

//        // POST /api/v1/measurement-unit
//        register(ApiEndpointDefinition.MEASUREMENT_UNIT_POST_CREATE_VALID_NAME, context -> {
//                    return MeasurementUnitResponseDataFactory.create("Кубічні метри", "м3").build();
//                }
//        );

        // POST /api/v1/measurement-unit
        register(ApiEndpointDefinition.MEASUREMENT_UNIT_POST_CREATE_INVALID_NAME, context -> {
                    return MeasurementUnitResponseDataFactory.create("Кубічні метри", "").build();
                }
        );
    }
}
