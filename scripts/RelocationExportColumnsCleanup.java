import com.erp.api.clients.*;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.fixtures.*;
import com.erp.models.response.*;
import com.erp.test_context.GlobalTestContext;
import com.erp.utils.auth.*;
import com.erp.utils.config.ConfigProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Resume ONLY artifacts evidenced by successful POSTs in the supplied manifest. */
public class RelocationExportColumnsCleanup {
    public static void main(String[] args) throws Exception {
        assertThat(System.getProperty("env")).isEqualTo("dev");
        assertThat(ConfigProvider.getBaseUrl()).isEqualTo("https://dev.cukerochka.sbs");
        assertThat(args).hasSize(2);
        var mapper=new ObjectMapper();
        var manifest=mapper.readTree(Path.of(args[0]).toFile());
        assertThat(manifest.path("environment").asText()).isEqualTo("dev");
        Set<Long> productIds=new LinkedHashSet<>();
        for(var resource:manifest.path("resources")) productIds.add(resource.path("id").asLong());
        assertThat(productIds).isNotEmpty();
        var results=new ArrayList<Map<String,Object>>();
        try(var browser=new PlaywrightSessionProvider(ConfigProvider.getBackendUrl())) {
            var auth=new AuthService(ConfigProvider.getBackendUrl());
            auth.setPlaywrightSessionProvider(browser);
            var api=new ApiExecutor(new SessionClient(),auth);
            var context=new GlobalTestContext();
            var inventory=new InventoryFixture(context,api);
            var storages=new StorageFixture(context,api);
            for(var node:manifest.path("storages")) {
                long id=node.path("id").asLong();
                var storage=storages.getById(UserRole.ADMIN,id);
                assertThat(storage.getName()).startsWith("rel-columns-").isEqualTo(node.path("name").asText());
                var before=inventory.listItems(id,UserRole.ADMIN,Map.of("showZeroStock",true,"size",1000));
                assertThat(before).allSatisfy(item -> assertThat(item.getResource().getId()).isIn(productIds));
                for(long productId:productIds) inventory.removeResourceFromStorage(id,productId,UserRole.ADMIN);
                var after=inventory.listItems(id,UserRole.ADMIN,Map.of("showZeroStock",true,"size",1000));
                assertThat(after).isEmpty();
                if(Boolean.TRUE.equals(storage.getActive())) assertThat(storages.deactivate(UserRole.ADMIN,id).statusCode()).isBetween(200,299);
                assertThat(storages.getById(UserRole.ADMIN,id).getActive()).isFalse();
                results.add(Map.of("storageId",id,"beforeRows",before.size(),"afterRows",after.size(),"active",false));
            }
            var resources=new ResourceFixture(context,api);
            for(var node:manifest.path("resources")) {
                long id=node.path("id").asLong();
                var resource=api.execute(ApiEndpointDefinition.RESOURCE_GET_BY_ID,UserRole.ADMIN,String.valueOf(id)).as(ResourceResponse.class);
                assertThat(resource.getName()).startsWith("rel-columns-").isEqualTo(node.path("name").asText());
                if(Boolean.TRUE.equals(resource.getActive())) assertThat(resources.deactivate(UserRole.ADMIN,id).statusCode()).isBetween(200,299);
                assertThat(api.execute(ApiEndpointDefinition.RESOURCE_GET_BY_ID,UserRole.ADMIN,String.valueOf(id))
                        .as(ResourceResponse.class).getActive()).isFalse();
            }
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(Path.of(args[1]).toFile(),
                Map.of("result","SUCCESS","environment","dev","sourceManifest",args[0],"storages",results,
                        "archivedResourceIds",productIds,"history","Completed history retained by API policy"));
    }
}
