package com.erp.models.request;

/** Request for PUT /storages/{storageId}/batch-selection-resources/{resourceId}. */
public record ResourceBatchSelectionRequest(boolean enabled) {
}
