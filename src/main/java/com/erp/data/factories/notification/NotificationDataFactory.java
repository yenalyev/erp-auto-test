package com.erp.data.factories.notification;

import com.erp.data.FakerProvider;
import com.erp.models.request.NotificationRecipientRequest;
import com.erp.models.request.NotificationSubscriptionRequest;
import com.erp.models.request.RemoveNotificationSubscriptionRequest;

import java.util.List;
import java.util.UUID;

public final class NotificationDataFactory {

    public static final String CAPTION_PREFIX = "autotest-notif-";
    public static final String ESC_STORAGE_PREFIX = "autotest-notif-esc-";
    public static final String STATE_ACTIVE = "ACTIVE";
    public static final String STATE_DISABLED = "DISABLED";
    public static final String TYPE_WHATSAPP = "WHATSAPP";
    public static final String TYPE_WEB_PUSH = "WEB_PUSH";
    public static final String TEMPLATE_STOCK_RED = "stock_red";
    public static final String TEMPLATE_STOCK_YELLOW = "stock_yellow";
    public static final String TEMPLATE_TECH_MAP = "tech_map_mode_changed";

    /** Special chars that may affect WhatsApp / templates (not JSON-critical). */
    public static String storageNameWithMarkupChars() {
        return ESC_STORAGE_PREFIX + UUID.randomUUID().toString().substring(0, 6)
                + " * ( ) : - _";
    }

    /** WhatsApp bold/italic pitfall: *текст* */
    public static String storageNameWithWhatsAppBoldTrap() {
        return ESC_STORAGE_PREFIX + UUID.randomUUID().toString().substring(0, 6)
                + " wrap *текст* here";
    }

    /** JSON-critical characters for escapeJson coverage. */
    public static String storageNameWithJsonCriticalChars() {
        return ESC_STORAGE_PREFIX + UUID.randomUUID().toString().substring(0, 6)
                + " q\"ote\\slash";
    }

    private NotificationDataFactory() {
    }

    public static NotificationRecipientRequest newActiveRecipient() {
        return recipient(uniqueCaption(), randomPhone(), STATE_ACTIVE);
    }

    public static NotificationRecipientRequest recipient(
            String caption, String addressInfo, String state) {
        return NotificationRecipientRequest.builder()
                .type(TYPE_WHATSAPP)
                .caption(caption)
                .addressInfo(addressInfo)
                .state(state)
                .build();
    }

    public static NotificationSubscriptionRequest subscription(
            Integer recipientId, String templateCode, List<Long> storages) {
        return NotificationSubscriptionRequest.builder()
                .recipientId(recipientId)
                .templateCode(templateCode)
                .storages(storages)
                .build();
    }

    public static RemoveNotificationSubscriptionRequest removeSubscription(
            Integer recipientId, String templateCode) {
        return RemoveNotificationSubscriptionRequest.builder()
                .recipientId(recipientId)
                .templateCode(templateCode)
                .build();
    }

    public static String uniqueCaption() {
        return CAPTION_PREFIX + UUID.randomUUID().toString().substring(0, 8);
    }

    /** 12-digit UA-style phone so masking yields first3****last2. */
    public static String randomPhone() {
        return "38050" + String.format("%07d", FakerProvider.english().number().numberBetween(1_000_000, 9_999_999));
    }

    public static String alternatePhone() {
        return "38067" + String.format("%07d", FakerProvider.english().number().numberBetween(1_000_000, 9_999_999));
    }
}
