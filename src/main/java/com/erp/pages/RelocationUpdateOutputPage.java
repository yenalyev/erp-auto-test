package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class RelocationUpdateOutputPage extends BasePage {

    private static final String TITLE = "Редагування видачі";
    private static final String SUBMIT = "Підтвердити";
    private static final String QUANTITY_PLACEHOLDER = "Кількість";
    private static final Pattern UPDATE_OUTPUT_ID = Pattern.compile("/update-output/(\\d+)");
    private static final ObjectMapper JSON = new ObjectMapper();

    public RelocationUpdateOutputPage(Page page) {
        super(page);
    }

    public RelocationUpdateOutputPage waitForLoaded() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(TITLE))
                .waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        confirmButton().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        return this;
    }

    public RelocationUpdateOutputPage fillDescription(String description) {
        Locator notes = page.locator("#description");
        notes.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        notes.fill(description);
        return this;
    }

    /**
     * Submit equipment send edit. The SPA copies {@code version} from journal
     * {@code location.state}; async invoice generation can bump {@code @Version} after that
     * snapshot. The PUT body is rewritten with a just-fetched journal version so a 409
     * optimistic lock does not hide the history-description assertion.
     */
    public RelocationPage submitVersionedEquipmentSend() {
        ensureIssuerFilled();
        long relocationId = relocationIdFromUrl();
        Long version = fetchJournalVersion(relocationId);
        String routeGlob = "**/relocations/equipment/" + relocationId + "/send*";
        page.route(routeGlob, route -> resumeEquipmentSendWithVersion(route, version));
        try {
            var response = page.waitForResponse(
                    r -> r.url().contains("/relocations/equipment/" + relocationId + "/send")
                            && "PUT".equals(r.request().method()),
                    new Page.WaitForResponseOptions().setTimeout(uiTimeoutMs()),
                    () -> confirmButton().click());
            if (response.status() < 200 || response.status() >= 300) {
                attachScreenshot("PUT equipment send edit failed — status " + response.status());
                throw new IllegalStateException(
                        "PUT /relocations/equipment/{id}/send failed with status " + response.status()
                                + " body=" + safeResponseText(response));
            }
        } finally {
            page.unroute(routeGlob);
        }
        return new RelocationPage(page).waitForLoaded();
    }

    private void resumeEquipmentSendWithVersion(Route route, Long version) {
        if (!"PUT".equalsIgnoreCase(route.request().method())) {
            route.resume();
            return;
        }
        String postData = route.request().postData();
        if (version == null || postData == null || postData.isBlank()) {
            route.resume();
            return;
        }
        route.resume(new Route.ResumeOptions().setPostData(withVersion(postData, version)));
    }

    private Long fetchJournalVersion(long relocationId) {
        Object storageId = page.evaluate("() => localStorage.getItem('selectedStorageId')");
        if (storageId == null) {
            return null;
        }
        String url = ConfigProvider.getBackendUrl() + "/api/v1/relocations"
                + "?page=0&size=100"
                + "&senderIds=" + storageId
                + "&receiverIds=" + storageId
                + "&isOr=true"
                + "&states=CREATED&states=CANCELLED";
        try {
            APIResponse response = page.request().get(url);
            if (response.status() != 200) {
                log.warn("Journal GET for edit-send version returned {}", response.status());
                return null;
            }
            JsonNode root = JSON.readTree(response.text());
            JsonNode content = root.path("content");
            if (!content.isArray()) {
                return null;
            }
            for (JsonNode item : content) {
                if (item.path("id").asLong() == relocationId && item.hasNonNull("version")) {
                    return item.get("version").asLong();
                }
            }
        } catch (Exception e) {
            log.warn("Could not read journal version for relocation {}: {}", relocationId, e.getMessage());
        }
        return null;
    }

    private static String withVersion(String postData, long version) {
        try {
            JsonNode node = JSON.readTree(postData);
            if (node instanceof ObjectNode objectNode) {
                objectNode.put("version", version);
                return JSON.writeValueAsString(objectNode);
            }
        } catch (Exception e) {
            log.warn("Could not patch equipment send version: {}", e.getMessage());
        }
        return postData;
    }

    private long relocationIdFromUrl() {
        Matcher matcher = UPDATE_OUTPUT_ID.matcher(page.url());
        if (!matcher.find()) {
            throw new IllegalStateException("Cannot parse relocation id from " + page.url());
        }
        return Long.parseLong(matcher.group(1));
    }

    private static String safeResponseText(com.microsoft.playwright.Response response) {
        try {
            return response.text();
        } catch (RuntimeException e) {
            return "<unreadable>";
        }
    }

    /**
     * «Видав» is a {@code required} input that the form only prefills from the Keycloak profile name,
     * which test users lack. Left empty, native constraint validation swallows the click and no request
     * is ever sent.
     */
    public RelocationUpdateOutputPage ensureIssuerFilled() {
        Locator issuer = page.locator("input[name='sendingPersonName']");
        if (issuer.count() > 0 && issuer.first().inputValue().isBlank()) {
            issuer.first().fill("Test");
        }
        return this;
    }

    public RelocationUpdateOutputPage waitForBookedLimitHint() {
        page.getByText("заброньовано")
                .first()
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));
        return this;
    }

    /**
     * Fills the first enabled quantity field (row total, or a batch amount when the total is locked).
     */
    public RelocationUpdateOutputPage fillProductAmount(double amount) {
        Locator qty = page.getByPlaceholder(QUANTITY_PLACEHOLDER);
        qty.first().waitFor();
        Locator target = qty.first();
        for (int i = 0; i < qty.count(); i++) {
            if (qty.nth(i).isEnabled()) {
                target = qty.nth(i);
                break;
            }
        }
        target.fill(String.valueOf(amount));
        target.press("Tab");
        page.waitForCondition(
                () -> confirmButton().isDisabled() || showsAmountOverAvailable(),
                new Page.WaitForConditionOptions().setTimeout(5_000));
        return this;
    }

    public boolean hasBookedUnavailableHint() {
        return page.getByText("заброньовано").count() > 0;
    }

    public boolean showsAmountOverAvailable() {
        return page.getByText("Макс:").count() > 0
                || page.getByText("разом").count() > 0
                || page.locator("input.border-red-500, input.text-red-600").count() > 0;
    }

    public boolean isConfirmDisabled() {
        return confirmButton().isDisabled();
    }

    public RelocationPage submit() {
        confirmButton().click();
        return new RelocationPage(page);
    }

    private Locator confirmButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(SUBMIT));
    }
}
