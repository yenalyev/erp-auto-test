package com.erp.models.request;

import com.erp.enums.StorageAccessMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageRegionRequest {
    private Long id;
    private String name;
    private StorageAccessMode accessMode;
    private Long recipientStorage;
}
