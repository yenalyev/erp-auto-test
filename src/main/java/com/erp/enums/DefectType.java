package com.erp.enums;

/**
 * Source type of a defect ("Брак"), mirrors backend {@code org.pm.tk.entity.defect.DefectType}.
 *
 * <ul>
 *   <li>{@code PRODUCTION} — defect originated from a production process (linked via productionProcessId)</li>
 *   <li>{@code RELOCATION} — defect originated from an inbound relocation/receipt (linked via relocationId)</li>
 *   <li>{@code RELOCATION_FROM_UNIT} — defect originated from a return relocation from a UNIT</li>
 *   <li>{@code STORAGE} — defect declared directly on storage stock (FIFO or explicit batches)</li>
 * </ul>
 */
public enum DefectType {
    PRODUCTION,
    RELOCATION,
    RELOCATION_FROM_UNIT,
    STORAGE
}
