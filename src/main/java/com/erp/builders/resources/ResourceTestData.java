package com.erp.builders.resources;

import com.erp.builders.common.FakerProvider;
import com.erp.builders.common.TestDataBuilder;
import com.erp.builders.units.MeasurementUnitTestData;
import com.erp.models.request.ResourceRequest;
import com.erp.models.response.ResourceResponse;
import com.github.javafaker.Faker;

import java.util.concurrent.atomic.AtomicInteger;

public class ResourceTestData implements TestDataBuilder<ResourceRequest, ResourceRequest> {
    private final Faker faker = FakerProvider.ukrainian();
    private final AtomicInteger counter = new AtomicInteger(1);

    // Якщо API дозволяє створювати вкладено, залишаємо білдер.
    // Якщо потрібен тільки ID — тут має бути логіка отримання ID.
    private final MeasurementUnitTestData unitBuilder = new MeasurementUnitTestData();

    @Override
    public ResourceRequest random() {
        return ResourceRequest.builder()
                // Додаємо лічильник для 100% унікальності імен у базі
                .name(faker.commerce().productName() + " #" + counter.getAndIncrement())
                .measurementUnitId(1L)
                .build();
    }

    @Override
    public ResourceRequest fixed() {
        return ResourceRequest.builder()
                .name("Стандартний ресурс")
                .measurementUnitId(1L)
                .build();
    }

    @Override
    public Class<ResourceRequest> getResponseClass() {
        return ResourceRequest.class;
    }
}