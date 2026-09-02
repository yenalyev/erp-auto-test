package com.erp.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserNotificationConfigResponse {
    @Builder.Default
    private Map<String, Object> templates = new LinkedHashMap<>();
    @Builder.Default
    private List<NotificationUserSubscriptionResponse> subscriptions = new ArrayList<>();
}
