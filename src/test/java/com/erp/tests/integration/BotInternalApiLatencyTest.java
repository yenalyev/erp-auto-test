package com.erp.tests.integration;

import com.erp.annotations.TestCaseId;
import com.erp.api.clients.BotInternalApiClient;
import com.erp.models.response.RelocationInternalResponse;
import com.erp.models.response.StorageInternalResponse;
import com.erp.models.response.StorageViewInternalResponse;
import com.erp.tests.BaseTest;
import com.erp.utils.auth.BotOAuth2Client;
import com.erp.utils.config.ConfigProvider;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.qameta.allure.Allure;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import lombok.extern.slf4j.Slf4j;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SLA smoke for ERP internal API endpoints consumed by external bots:
 * <ul>
 *   <li>whatsapp-bot — {@code GET /api/v1/internal/storages}</li>
 *   <li>delivery-bot — {@code GET /api/v1/internal/relocations}</li>
 *   <li>delivery-bot — {@code GET /api/v1/internal/storages/structure}</li>
 * </ul>
 * Both bots authenticate via OAuth2 {@code client_credentials} (Keycloak).
 * <p>
 * Run: {@code mvn test -Denv=dev -Dsuite=bots} or {@code mvn test -Denv=staging -Dsuite=bots}
 */
@Slf4j
@Epic("Integrations")
@Feature("Bot Internal API")
public class BotInternalApiLatencyTest extends BaseTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String WHATSAPP_BOT = "whatsapp-bot";
    private static final String DELIVERY_BOT = "delivery-bot";

    private BotOAuth2Client botOAuth2Client;
    private BotInternalApiClient botInternalApiClient;
    private int maxResponseSeconds;
    private String accessToken;

    @BeforeClass(alwaysRun = true)
    public void requireBotOAuthConfig() {
        if (!ConfigProvider.isBotApiTestsEnabled()) {
            throw new SkipException(
                    "Bot API tests skipped: set CLIENT_ID, CLIENT_SECRET, GET_TOKEN_URL "
                            + "in .env.dev / .env.staging (or bot.oauth.* via -D / OS env)");
        }
        maxResponseSeconds = ConfigProvider.getBotApiMaxResponseSeconds();
        botOAuth2Client = new BotOAuth2Client();
        botInternalApiClient = new BotInternalApiClient();
    }

    @Test(priority = 1)
    @TestCaseId("TC-BOT-001")
    @Story("OAuth2 client_credentials")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Keycloak token endpoint (спільний для whatsapp-bot і delivery-bot) відповідає за ≤30 с")
    public void testBotOAuth2TokenWithinSla() {
        BotOAuth2Client.TimedTokenResponse timed = botOAuth2Client.requestAccessToken();

        Allure.parameter("elapsedMs", timed.elapsedMs());
        Allure.parameter("maxResponseSeconds", maxResponseSeconds);

        assertThat(timed.elapsedMs())
                .as("OAuth2 token має прийти протягом %d с", maxResponseSeconds)
                .isLessThanOrEqualTo(maxResponseSeconds * 1000L);
        assertThat(timed.accessToken())
                .as("access_token не повинен бути порожнім")
                .isNotBlank();

        accessToken = timed.accessToken();
        log.info("TC-BOT-001 PASSED — OAuth2 token in {} ms", timed.elapsedMs());
    }

    @Test(priority = 2, dependsOnMethods = "testBotOAuth2TokenWithinSla")
    @TestCaseId("TC-BOT-002")
    @Story("whatsapp-bot inventory sync")
    @Severity(SeverityLevel.CRITICAL)
    @Description("GET /api/v1/internal/storages (whatsapp-bot) — HTTP 200, відповідь ≤30 с; Allure: request/response body, параметр storagesCount")
    public void testWhatsappBotInternalStoragesWithinSla() throws Exception {
        String dataUrl = ConfigProvider.getBotWhatsappDataUrl();
        BotInternalApiClient.TimedResponse timed = botInternalApiClient.get(dataUrl, accessToken);

        attachGetExchange(dataUrl, timed);
        assertResponseWithinSla(WHATSAPP_BOT, dataUrl, timed);

        List<StorageInternalResponse> storages = JSON.readValue(
                timed.body(), new TypeReference<>() {});

        assertThat(storages)
                .as("Список складів має бути валідним JSON-масивом")
                .isNotNull();

        Allure.parameter("storagesCount", storages.size());
        log.info("TC-BOT-002 PASSED — {} storages in {} ms", storages.size(), timed.elapsedMs());
    }

    @Test(priority = 3, dependsOnMethods = "testBotOAuth2TokenWithinSla")
    @TestCaseId("TC-BOT-003")
    @Story("delivery-bot relocation sync")
    @Severity(SeverityLevel.CRITICAL)
    @Description("GET /api/v1/internal/relocations (delivery-bot) — HTTP 200, відповідь ≤30 с; Allure: request/response body, параметр relocationsCount")
    public void testDeliveryBotInternalRelocationsWithinSla() throws Exception {
        String dataUrl = ConfigProvider.getBotDeliveryDataUrl();
        BotInternalApiClient.TimedResponse timed = botInternalApiClient.get(dataUrl, accessToken);

        attachGetExchange(dataUrl, timed);
        assertResponseWithinSla(DELIVERY_BOT, dataUrl, timed);

        List<RelocationInternalResponse> relocations = JSON.readValue(
                timed.body(), new TypeReference<>() {});

        assertThat(relocations)
                .as("Список переміщень має бути валідним JSON-масивом")
                .isNotNull();

        Allure.parameter("relocationsCount", relocations.size());
        log.info("TC-BOT-003 PASSED — {} relocations in {} ms", relocations.size(), timed.elapsedMs());
    }

    @Test(priority = 4, dependsOnMethods = "testBotOAuth2TokenWithinSla")
    @TestCaseId("TC-BOT-004")
    @Story("delivery-bot location structure")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            GET /api/v1/internal/storages/structure (delivery-bot) — HTTP 200, відповідь ≤30 с;
            плоский масив {id, name, parentId}; Allure: request/response, параметр nodesCount""")
    public void testDeliveryBotInternalStorageStructureWithinSla() throws Exception {
        String dataUrl = ConfigProvider.getBotDeliveryStructureUrl();
        BotInternalApiClient.TimedResponse timed = botInternalApiClient.get(dataUrl, accessToken);

        attachGetExchange(dataUrl, timed);
        assertResponseWithinSla(DELIVERY_BOT, dataUrl, timed);

        List<StorageViewInternalResponse> nodes = JSON.readValue(
                timed.body(), new TypeReference<>() {});

        assertThat(nodes)
                .as("Структура локацій має бути валідним JSON-масивом")
                .isNotNull()
                .isNotEmpty();

        assertThat(nodes)
                .as("Кожен вузол має id і name; parentId може бути null")
                .allSatisfy(node -> {
                    assertThat(node.getId()).as("id").isNotNull();
                    assertThat(node.getName()).as("name").isNotBlank();
                });

        Allure.parameter("nodesCount", nodes.size());
        log.info("TC-BOT-004 PASSED — {} structure nodes in {} ms", nodes.size(), timed.elapsedMs());
    }

    private void assertResponseWithinSla(String botName, String path, BotInternalApiClient.TimedResponse timed) {
        Allure.parameter("bot", botName);
        Allure.parameter("path", path);
        Allure.parameter("elapsedMs", timed.elapsedMs());
        Allure.parameter("httpStatus", timed.statusCode());
        Allure.parameter("maxResponseSeconds", maxResponseSeconds);

        assertThat(timed.elapsedMs())
                .as("[%s] %s має відповісти протягом %d с", botName, path, maxResponseSeconds)
                .isLessThanOrEqualTo(maxResponseSeconds * 1000L);
        assertThat(timed.statusCode())
                .as("[%s] %s — очікується HTTP 200", botName, path)
                .isEqualTo(200);
    }

    private void attachGetExchange(String url, BotInternalApiClient.TimedResponse timed) {
        String requestInfo = """
                GET %s
                Authorization: Bearer ***
                Accept: application/json
                Accept-Encoding: gzip
                """.formatted(url).strip();
        Allure.addAttachment("Request", "text/plain", requestInfo);

        Allure.addAttachment("Response Status", String.valueOf(timed.statusCode()));
        String body = formatJsonForAttachment(timed.body());
        if (!body.isBlank()) {
            Allure.addAttachment("Response Body", "application/json", body, "json");
        }
    }

    private String formatJsonForAttachment(byte[] body) {
        if (body.length == 0) {
            return "";
        }
        try {
            Object parsed = JSON.readValue(body, Object.class);
            return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(parsed);
        } catch (Exception e) {
            return new String(body, StandardCharsets.UTF_8);
        }
    }
}
