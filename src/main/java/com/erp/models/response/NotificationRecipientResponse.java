package com.erp.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class NotificationRecipientResponse {
    private Integer id;
    /** WHATSAPP | WEB_PUSH */
    private String type;
    private String caption;
    /** Masked on read (e.g. 380****33). */
    private String addressInfo;
    /** ACTIVE | DISABLED */
    private String state;
}
