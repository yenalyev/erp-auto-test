package com.erp.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class NotificationLogResponse {
    private Integer id;
    private String templateCode;
    private SimpleEntityResponse storage;
    @Builder.Default
    private List<String> recipientNames = new ArrayList<>();
    /** PENDING | SENDING | SENT | FAILED | CANCELED */
    private String state;
    private Integer attempt;
    private String createdAt;
    private String updatedAt;
}
