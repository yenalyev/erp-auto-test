package com.erp.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ErrorResponse {

    private Integer status;
    private String error;
    private String message;
    private String path;
    private LocalDateTime timestamp;

    /**
     * Перевіряє чи це помилка доступу (403)
     */
    public boolean isForbidden() {
        return status != null && status == 403;
    }

    /**
     * Перевіряє чи це помилка автентифікації (401)
     */
    public boolean isUnauthorized() {
        return status != null && status == 401;
    }

    /**
     * Перевіряє чи повідомлення містить інформацію про заборону доступу
     */
    public boolean hasAccessDenialMessage() {
        if (message == null && error == null) {
            return false;
        }

        String text = ((message != null ? message : "") + " " +
                (error != null ? error : "")).toLowerCase();

        return text.contains("forbidden") ||
                text.contains("access denied") ||
                text.contains("unauthorized") ||
                text.contains("permission") ||
                text.contains("not allowed") ||
                text.contains("insufficient");
    }
}
