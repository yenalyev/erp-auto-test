package com.erp.utils.helpers;

import io.qameta.allure.Allure;
import io.qameta.allure.AllureLifecycle;
import io.qameta.allure.model.Attachment;
import io.qameta.allure.model.Stage;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Attaches PNG screenshots to the Allure test case (not AfterMethod fixtures),
 * so they appear in the report Attachments panel.
 */
@Slf4j
public final class AllureScreenshots {

    private static final ThreadLocal<String> CURRENT_TEST_UUID = new ThreadLocal<>();

    private AllureScreenshots() {
    }

    /** Remember the active Allure test UUID (call from {@code @BeforeMethod} / teardown). */
    public static void rememberCurrentTest() {
        Allure.getLifecycle().getCurrentTestCase().ifPresent(CURRENT_TEST_UUID::set);
    }

    public static void clear() {
        CURRENT_TEST_UUID.remove();
    }

    public static void attachPng(String label, byte[] pngBytes) {
        if (pngBytes == null || pngBytes.length == 0) {
            log.warn("Skip empty screenshot '{}'", label);
            return;
        }
        AllureLifecycle lifecycle = Allure.getLifecycle();
        lifecycle.getCurrentTestCase().ifPresent(CURRENT_TEST_UUID::set);

        String testUuid = CURRENT_TEST_UUID.get();
        if (testUuid == null) {
            testUuid = lifecycle.getCurrentTestCase().orElse(null);
        }
        if (testUuid == null) {
            log.warn("Cannot attach screenshot '{}': no Allure test UUID", label);
            return;
        }

        String source = UUID.randomUUID() + "-attachment.png";
        Attachment attachment = new Attachment()
                .setName(label)
                .setType("image/png")
                .setSource(source);

        AtomicReference<Stage> stage = new AtomicReference<>();
        lifecycle.updateTestCase(testUuid, tr -> {
            tr.getAttachments().add(attachment);
            stage.set(tr.getStage());
        });
        lifecycle.writeAttachment(source, new ByteArrayInputStream(pngBytes));

        // If AllureTestNg already finished+wrote the case (@AfterMethod), rewrite JSON.
        // Do NOT write while RUNNING — that removes the case from Allure storage.
        if (stage.get() == Stage.FINISHED) {
            lifecycle.writeTestCase(testUuid);
        }
        log.info("Screenshot attached to Allure test case: {}", label);
    }
}
