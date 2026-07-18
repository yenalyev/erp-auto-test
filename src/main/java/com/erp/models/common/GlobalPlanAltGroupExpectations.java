package com.erp.models.common;

/**
 * Expected decomposition math for {@link GlobalPlanAltGroupContext} when global output P = 10.
 * <p>
 * Map: 1F + group&#123;D@2, E@3&#125; → 1P.
 * Whichever alternative is the <em>used</em> one for planning (currently via {@code isDefault})
 * is counted: used=D → D=20; used=E → E=30. Fixed F=10 always.
 */
public final class GlobalPlanAltGroupExpectations {

    public static final double OUTPUT_P = 10.0;

    public static final double DEFAULT_ALT_AMOUNT = 2.0;
    public static final double OTHER_ALT_AMOUNT = 3.0;
    public static final double FIXED_AMOUNT = 1.0;

    /** batches × DEFAULT_ALT_AMOUNT */
    public static final double RAW_D = OUTPUT_P * DEFAULT_ALT_AMOUNT;
    public static final double RAW_F = OUTPUT_P * FIXED_AMOUNT;

    /** After swapping default to E@3 */
    public static final double RAW_E_AFTER_SWAP = OUTPUT_P * OTHER_ALT_AMOUNT;

    private GlobalPlanAltGroupExpectations() {
    }
}
