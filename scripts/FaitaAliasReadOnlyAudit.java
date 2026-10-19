import com.erp.utils.helpers.DatabaseHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Read-only dev snapshot. No fixtures, imports, refreshes, or DML. Run with the test runtime classpath. */
public class FaitaAliasReadOnlyAudit {
    private static final Map<String, String> QUERIES = new LinkedHashMap<>();
    static {
        QUERIES.put("snapshot", "SELECT current_setting('transaction_read_only') AS read_only, "
                + "current_setting('transaction_isolation') AS isolation, now()::text AS captured_at");
        for (String kind : List.of("ammo", "initiators")) {
            String table = "faita.resource_alias_with_" + kind;
            String source = kind.equals("ammo") ? "ammunition_id" : "initiator_id";
            QUERIES.put(kind + "Summary", "SELECT count(*) AS total_aliases, "
                    + "count(*) FILTER (WHERE a.resource_id IS NULL) AS null_resource_id, "
                    + "count(*) FILTER (WHERE a.resource_id IS NOT NULL AND r.id IS NULL) AS dangling_resource_id, "
                    + "count(*) FILTER (WHERE r.id IS NOT NULL AND NOT r.is_active) AS inactive_existing_resource, "
                    + "count(*) FILTER (WHERE a.name LIKE '%fmap-%') AS test_marker_aliases "
                    + "FROM " + table + " a LEFT JOIN public.resource r ON r.id=a.resource_id");
            QUERIES.put(kind + "InvalidAliases", "SELECT a.id, a.name, a." + source + " AS faita_id, "
                    + "a.resource_id, a.created_by, a.updated_by, a.created_at::text, a.updated_at::text, "
                    + "CASE WHEN a.resource_id IS NULL THEN 'NULL_RESOURCE_ID' ELSE 'DANGLING_RESOURCE_ID' END AS scenario "
                    + "FROM " + table + " a LEFT JOIN public.resource r ON r.id=a.resource_id "
                    + "WHERE r.id IS NULL ORDER BY a.id");
        }
        QUERIES.put("aliasResourceColumns", "SELECT table_name,column_name,is_nullable,data_type "
                + "FROM information_schema.columns WHERE table_schema='faita' "
                + "AND table_name IN ('resource_alias_with_ammo','resource_alias_with_initiators') "
                + "AND column_name='resource_id' ORDER BY table_name");
        QUERIES.put("aliasConstraints", "SELECT c.conrelid::regclass::text AS table_name,c.conname,c.contype::text, "
                + "pg_get_constraintdef(c.oid) AS definition FROM pg_constraint c "
                + "WHERE c.conrelid IN ('faita.resource_alias_with_ammo'::regclass, "
                + "'faita.resource_alias_with_initiators'::regclass) ORDER BY table_name,c.conname");
        QUERIES.put("importAndAliasTriggers", "SELECT n.nspname AS schema_name,c.relname AS table_name,t.tgname, "
                + "pg_get_triggerdef(t.oid) AS definition,pg_get_functiondef(t.tgfoid) AS function_definition "
                + "FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid JOIN pg_namespace n ON n.oid=c.relnamespace "
                + "WHERE NOT t.tgisinternal AND ((n.nspname='faita' AND c.relname IN "
                + "('resource_alias_with_ammo','resource_alias_with_initiators','ammo_agg_v3','ammunition','initiators')) "
                + "OR (n.nspname='public' AND c.relname='resource')) ORDER BY n.nspname,c.relname,t.tgname");
    }
    public static void main(String[] args) throws Exception {
        if (!"dev".equals(System.getProperty("env"))) throw new IllegalArgumentException("Requires -Denv=dev");
        if (!"https://dev.cukerochka.sbs".equals(com.erp.utils.config.ConfigProvider.getBaseUrl()))
            throw new IllegalStateException("Dev configuration resources must precede dependencies on the classpath");
        DatabaseHelper helper = new DatabaseHelper();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("environment", "dev");
        report.put("fixturesCreated", false);
        report.put("importExecuted", false);
        report.put("queries", QUERIES);
        try {
            Connection connection = helper.getConnection();
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            for (var query : QUERIES.entrySet()) {
                try (Statement statement = connection.createStatement()) {
                    statement.setQueryTimeout(60);
                    try (ResultSet rows = statement.executeQuery(query.getValue())) {
                        List<Map<String, Object>> results = new ArrayList<>();
                        var metadata = rows.getMetaData();
                        while (rows.next()) {
                            Map<String, Object> row = new LinkedHashMap<>();
                            for (int i = 1; i <= metadata.getColumnCount(); i++) row.put(metadata.getColumnLabel(i), rows.getObject(i));
                            results.add(row);
                        }
                        report.put(query.getKey(), results);
                    }
                }
            }
            connection.rollback();
        } finally {
            helper.closeConnection();
        }
        Path destination = Path.of(args[0]);
        Files.createDirectories(destination.getParent());
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(destination.toFile(), report);
        System.out.println("Read-only audit saved: " + destination);
    }
}
