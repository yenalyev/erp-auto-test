package com.erp.models.common;

/**
 * Expected decomposition math for the standard M1/M2/M3 chain when global output A = 10.
 * <p>
 * M1: 2B + 3x → 1A; M2: 2y + 1C → 1B; M3: 1z → 1C.
 * Full decomposition: A=10@L1/M1, B=12@L1+8@L2/M2, C=20@L1/M3.
 */
public final class GlobalPlanChainExpectations {

    public static final double OUTPUT_A = 10.0;

    public static final double RAW_X = 30.0;
    public static final double RAW_Y = 40.0;
    public static final double RAW_Z = 20.0;

    public static final double SEMI_B = 20.0;
    public static final double SEMI_C = 20.0;

    /** Gross semi-finished B when A=10 and direct output B=5 (inputDemand 20 + seedDemand 5). */
    public static final double SEMI_B_WITH_DIRECT_OUTPUT_5 = 25.0;
    public static final double SEMI_C_WITH_DIRECT_B_5 = 25.0;
    public static final double RAW_Y_WITH_DIRECT_B_5 = 50.0;
    public static final double RAW_Z_WITH_DIRECT_B_5 = 25.0;

    public static final double L1_OUTPUT_A = 10.0;
    /** L2 ships surplus B; B/C produced at L1 are netted out of the L1 draft. */
    public static final double L2_OUTPUT_B = 8.0;

    /** M1 ratio: for output A=15, B requirement = 30. */
    public static final double OUTPUT_A_EDITED = 15.0;
    public static final double SEMI_B_FOR_A15 = 30.0;
    public static final double RAW_X_FOR_A15 = 45.0;

    private GlobalPlanChainExpectations() {
    }
}
