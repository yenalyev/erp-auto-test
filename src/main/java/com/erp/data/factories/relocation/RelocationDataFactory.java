package com.erp.data.factories.relocation;

import com.erp.data.FakerProvider;
import com.erp.models.request.ProductionRequest;
import com.erp.models.request.RelocationRequest;
import com.erp.models.request.ResourceUsageRequest;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.ResourceUsageResponse;

import java.util.ArrayList;
import java.util.List;

public class RelocationDataFactory {
    /**
     *
     * @param fromStorageId
     * @param toStorageId
     * @param resource
     * @param amount
     * @return RelocationRequestBuilder - simple relocation for 1 resource
     */
    public static RelocationRequest.RelocationRequestBuilder simpleRelocation(Long fromStorageId,
                                                                              Long toStorageId,
                                                                              ResourceResponse resource,
                                                                              Double amount) {
        if (null==fromStorageId){
            throw new IllegalStateException("ERROR - Test Setup Error: 'fromStorageId' is null ");
        }

        if (null==toStorageId){
            throw new IllegalStateException("ERROR - Test Setup Error: 'toStorageId' is null ");
        }

        if (null==resource){
            throw new IllegalStateException("ERROR - Test Setup Error: 'resource' is null ");
        }

        if (null==amount){
            throw new IllegalStateException("ERROR - Test Setup Error: 'amount' is null ");
        }

        ResourceUsageRequest usageRequest = new ResourceUsageRequest(resource.getId(), amount);

        return RelocationRequest.builder()
                .description(FakerProvider.ukrainian().commerce().department())
                .fromStorageId(fromStorageId)
                .toStorageId(toStorageId)
                .items(List.of(usageRequest));
    }
}
