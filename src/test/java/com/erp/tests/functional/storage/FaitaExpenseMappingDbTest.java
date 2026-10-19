package com.erp.tests.functional.storage;

import com.erp.annotations.TestCaseId;
import com.erp.fixtures.FaitaExpenseMappingFixture;
import com.erp.fixtures.FaitaExpenseMappingFixture.Kind;
import io.qameta.allure.*;
import org.testng.annotations.*;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;

/** Deployed schema checks and actual V156 alias ALTERs on transaction-local TEMP tables. */
@Epic("Integration")
@Feature("Словник мапінгу Файта — Цукерка для витрат")
public class FaitaExpenseMappingDbTest {
    private FaitaExpenseMappingFixture fixture;
    @BeforeClass(alwaysRun = true) public void connect() { fixture = new FaitaExpenseMappingFixture(); }
    @AfterClass(alwaysRun = true) public void disconnect() throws Exception { if (fixture != null) fixture.close(); }
    private void schema(Kind kind) throws Exception {
        assertThat(fixture.hasUpdatedBy(kind)).as("faita.%s must contain updated_by", kind.table).isTrue();
        try (var statement = fixture.connection().prepareStatement("SELECT data_type FROM information_schema.columns "
                + "WHERE table_schema='faita' AND table_name=? AND column_name='updated_by'")) {
            statement.setString(1, kind.table);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isIn("text", "character varying", "character");
            }
        }
        try (var statement = fixture.connection().createStatement(); var rows = statement.executeQuery(
                "SELECT count(*), count(*) FILTER (WHERE updated_by='system'), "
                        + "count(*) FILTER (WHERE updated_by IS NULL OR btrim(updated_by)='') FROM faita." + kind.table)) {
            rows.next();
            Allure.addAttachment(kind.table + " deployed audit summary", "text/plain",
                    "total=" + rows.getLong(1) + ", system=" + rows.getLong(2) + ", empty=" + rows.getLong(3));
            assertThat(rows.getLong(3)).as("All deployed aliases have an author").isZero();
        }
    }
    private void execute(String sql) throws Exception {
        try (var statement = fixture.connection().createStatement()) { statement.execute(sql); }
    }
    private void backfill(Kind kind) throws Exception {
        fixture.connection().setAutoCommit(false);
        try {
            execute("SET LOCAL statement_timeout='10s'");
            for (Kind target : Kind.values()) {
                execute("CREATE TEMP TABLE " + target.table + " (id bigint PRIMARY KEY, name text NOT NULL, resource_id bigint, "
                        + target.sourceName + " text NOT NULL, " + (target == Kind.AMMO ? "ammunition_id" : "initiator_id")
                        + " bigint" + (target == Kind.AMMO ? ", multiplier numeric NOT NULL, match_by_name boolean" : "")
                        + ") ON COMMIT DROP");
                execute("INSERT INTO pg_temp." + target.table + " VALUES (1,'Legacy mapped',41,'Old source',101"
                        + (target == Kind.AMMO ? ",1.5,true" : "") + "),(2,'Legacy unmapped',NULL,'Other source',102"
                        + (target == Kind.AMMO ? ",3,false" : "") + ")");
            }
            String sql;
            try (var stream = getClass().getResourceAsStream("/migrations/faita/V156__faita_mapping.sql")) {
                if (stream == null) throw new IllegalStateException("Missing reviewed V156 snapshot");
                sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
            // Only ADD/DROP alias statements. Sequence repairs and view indexes are outside this case.
            String alters = sql.substring(0, sql.indexOf("-- Aliases were entered"))
                    .replaceAll("(?m)--.*$", "").trim();
            for (String statement : alters.split(";")) {
                if (statement.isBlank()) continue;
                assertThat(statement.trim()).matches("(?s)ALTER TABLE faita\\.resource_alias_with_(ammo|initiators) (ADD|DROP) COLUMN .*");
                execute(statement.replace("faita.resource_alias_with_", "pg_temp.resource_alias_with_"));
            }
            Allure.addAttachment("V156 alias ALTERs executed only on pg_temp", "text/plain", alters);
            try (var statement = fixture.connection().createStatement(); var rows = statement.executeQuery(
                    "SELECT id,name,resource_id,updated_by," + (kind == Kind.AMMO ? "ammunition_id" : "initiator_id")
                            + " AS source_id" + (kind == Kind.AMMO ? ",multiplier,match_by_name" : "")
                            + " FROM pg_temp." + kind.table + " ORDER BY id")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong("id")).isEqualTo(1);
                assertThat(rows.getString("name")).isEqualTo("Legacy mapped");
                assertThat(rows.getLong("resource_id")).isEqualTo(41);
                assertThat(rows.getLong("source_id")).isEqualTo(101);
                assertThat(rows.getString("updated_by")).isEqualTo("system");
                if (kind == Kind.AMMO) {
                    assertThat(rows.getBigDecimal("multiplier")).isEqualByComparingTo("1.5");
                    assertThat(rows.getBoolean("match_by_name")).isTrue();
                }
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong("id")).isEqualTo(2);
                assertThat(rows.getString("name")).isEqualTo("Legacy unmapped");
                assertThat(rows.getObject("resource_id")).isNull();
                assertThat(rows.getLong("source_id")).isEqualTo(102);
                assertThat(rows.getString("updated_by")).isEqualTo("system");
                if (kind == Kind.AMMO) assertThat(rows.getBigDecimal("multiplier")).isEqualByComparingTo("3");
                assertThat(rows.next()).isFalse();
            }
        } finally {
            fixture.connection().rollback();
            fixture.connection().setAutoCommit(true);
        }
    }
    @Test @TestCaseId("TC-FMAP-DB-001") public void ammoHasUpdatedBy() throws Exception { schema(Kind.AMMO); }
    @Test @TestCaseId("TC-FMAP-DB-002") public void initiatorsHaveUpdatedBy() throws Exception { schema(Kind.INITIATORS); }
    @Test @TestCaseId("TC-FMAP-DB-003")
    @Description("Actual V156 alias ALTERs on TEMP legacy mapped/unmapped rows; audit=system; business fields preserved; rollback.")
    public void ammoLegacyRowsBackfilledWithoutDataLoss() throws Exception { backfill(Kind.AMMO); }
    @Test @TestCaseId("TC-FMAP-DB-004")
    @Description("Actual V156 alias ALTERs on TEMP legacy mapped/unmapped rows; audit=system; business fields preserved; rollback.")
    public void initiatorLegacyRowsBackfilledWithoutDataLoss() throws Exception { backfill(Kind.INITIATORS); }
}
