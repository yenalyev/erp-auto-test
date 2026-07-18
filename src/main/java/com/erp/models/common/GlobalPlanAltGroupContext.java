package com.erp.models.common;

import com.erp.models.response.ResourceResponse;
import com.erp.models.response.TechnologicalMapResponse;
import lombok.Builder;
import lombok.Data;

/**
 * Chain for global-plan tests covering technological-map alternative groups.
 * <p>
 * {@code mapProduct}: fixed F + group &#123;D default, E alt&#125; → P.
 * D and E have no PRODUCTION maps as output → both treated as raw candidates;
 * decomposer counts only the default (D).
 */
@Data
@Builder
public class GlobalPlanAltGroupContext {
    private Long l1StorageId;
    private ResourceResponse resourceP;
    private ResourceResponse resourceD;
    private ResourceResponse resourceE;
    private ResourceResponse resourceF;
    private TechnologicalMapResponse mapProduct;
}
