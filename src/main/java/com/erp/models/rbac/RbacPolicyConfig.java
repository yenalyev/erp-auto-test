package com.erp.models.rbac;

import lombok.Data;

import java.util.List;

@Data
public class RbacPolicyConfig {
    private List<RuleConfig> rules;
}
