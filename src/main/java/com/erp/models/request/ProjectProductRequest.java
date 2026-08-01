package com.erp.models.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Mirrors backend {@code org.pm.tk.dto.request.projectproduction.ProjectProductRequest}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectProductRequest {
    private Long projectCategoryId;
    private String name;
    private String description;
    @Builder.Default
    private List<ProjectProductPropertyRequest> properties = new ArrayList<>();
}
