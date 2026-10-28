import com.erp.api.clients.*;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.utils.auth.*;
import com.erp.utils.config.ConfigProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Read-only independent verification of the exact owned IDs from completed run manifests. */
public class MshpCleanupReadback {
    public static void main(String[] args) throws Exception {
        assertThat(System.getProperty("env")).isEqualTo("dev");
        assertThat(ConfigProvider.getBaseUrl()).isEqualTo("https://dev.cukerochka.sbs");
        var mapper = new ObjectMapper();
        List<Map<String, Object>> checks = new ArrayList<>();
        try (var provider = new PlaywrightSessionProvider(ConfigProvider.getBackendUrl())) {
            var auth = new AuthService(ConfigProvider.getBackendUrl());
            auth.setPlaywrightSessionProvider(provider);
            var api = new ApiExecutor(new SessionClient(), auth);
            try (var paths = Files.list(Path.of(args[0]))) {
                for (var path : paths.filter(p -> p.toString().endsWith(".json")).toList()) {
                    var manifest = mapper.readTree(path.toFile());
                    String marker = manifest.path("marker").asText();
                    assertThat(marker).matches("mshp-[a-f0-9]{8}");
                    assertThat(manifest.path("operations").size()).as("No tracked operations remain").isZero();
                    assertThat(manifest.path("receives").size()).as("No tracked receives remain").isZero();
                    for (String type : List.of("locations", "resources")) {
                        for (var entry : manifest.path(type)) {
                            long id = entry.path("id").asLong();
                            var endpoint = type.equals("locations") ? ApiEndpointDefinition.STORAGE_GET_BY_ID : ApiEndpointDefinition.RESOURCE_GET_BY_ID;
                            var response = api.execute(endpoint, UserRole.ADMIN, String.valueOf(id));
                            assertThat(response.statusCode()).isEqualTo(200);
                            assertThat(response.jsonPath().getString("name")).isEqualTo(entry.path("name").asText()).startsWith(marker);
                            assertThat(response.jsonPath().getBoolean("active")).as("Archived %s %s", type, id).isFalse();
                            checks.add(Map.of("marker", marker, "type", type, "id", id, "active", false));
                        }
                    }
                }
            }
        }
        assertThat(checks).isNotEmpty();
        mapper.writerWithDefaultPrettyPrinter().writeValue(Path.of(args[1]).toFile(), Map.of("environment", "dev", "result", "SUCCESS", "checks", checks));
        System.out.println("Verified archived owned entities: " + checks.size());
    }
}
