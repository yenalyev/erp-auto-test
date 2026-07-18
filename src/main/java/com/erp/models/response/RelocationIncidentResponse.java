package com.erp.models.response;

import com.erp.models.request.IncidentResourceRequest;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RelocationIncidentResponse {
    private Integer id;
    private Instant dateTime;
    private Long relocationId;
    private String description;
    @Builder.Default
    private List<IncidentResourceRequest> resources = new ArrayList<>();
    @Builder.Default
    private List<IncidentAttachmentResponse> attachments = new ArrayList<>();
}
