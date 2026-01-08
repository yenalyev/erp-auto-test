package com.erp.data.factories.production;

import com.erp.data.FakerProvider;
import com.erp.models.request.ProductionRequest;
import com.erp.models.response.TechnologicalMapResponse;

import java.util.ArrayList;

public class ProductionDataFactory {

    //create production with simple tech map (without alternatives)
    public static ProductionRequest.ProductionRequestBuilder simpleProduction(Long storageId,
                                                                              TechnologicalMapResponse techMap,
                                                                              Double amountOfProduction) {
        if (null==storageId){
            throw new IllegalStateException("ERROR - Test Setup Error: 'storageId' is null ");
        }

        if (null==techMap){
            throw new IllegalStateException("ERROR - Test Setup Error: 'techMap' is null ");
        }

        if (!techMap.getAlternatives().isEmpty()){
            throw new IllegalStateException("ERROR - Test Setup Error: 'techMap' has " +
                    "Alternative Groups - it shouldn't ");
        }

        return ProductionRequest.builder()
                .description(FakerProvider.ukrainian().commerce().productName())
                .amount(amountOfProduction)
                .technologicalMapId(techMap.getId())
                .storageId(storageId)
                .selectedOptionIds(new ArrayList<>());
    }
}
