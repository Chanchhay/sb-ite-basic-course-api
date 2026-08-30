package kh.edu.istad.ite.features.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import kh.edu.istad.ite.features.dataimport.dto.ImportJobResponse;
import kh.edu.istad.ite.features.dataimport.field.ImportField;
import kh.edu.istad.ite.features.dataimport.parser.SourceRow;
import kh.edu.istad.ite.features.dataimport.parser.XlsxSourceFileParser;
import kh.edu.istad.ite.features.migration.dto.AssistedMigrationDtos;
import kh.edu.istad.ite.features.migration.entity.AssistedMigration;
import kh.edu.istad.ite.features.migration.entity.MigrationIssue;
import kh.edu.istad.ite.features.migration.service.AssistedMigrationService;
import kh.edu.istad.ite.features.minio.MinioService;
import kh.edu.istad.ite.shared.enums.ImportTargetType;

/**
 * The half of the test plan that only a real database can answer.
 *
 * Unit reuse, conflicting units and handover idempotency are all statements
 * about a shop's existing catalogue, and a test that stubs the catalogue is
 * only testing the stub. This drives the real service against a copy of the
 * development database, whose units table already contains the Box that makes
 * the conflict case real.
 *
 * Off unless asked for, because no build server can conjure a shop's data. To
 * run it, take a copy — never the original, since this writes migrations,
 * decisions and an import job:
 *
 * <pre>
 * createdb -h localhost -U postgres -T template0 ipos_db_verify
 * pg_dump -h localhost -U postgres ipos_db --no-owner --no-privileges \
 *   | psql -h localhost -U postgres -d ipos_db_verify
 * VERIFY_DB=ipos_db_verify ./gradlew test --tests '*LiveDatabaseVerification*'
 * </pre>
 *
 * The copy is disposable: drop it and make it again whenever the development
 * database has moved on.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/ipos_db_verify",
        "spring.datasource.username=postgres",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=true",
        "app.database.auto-migrate=false"
})
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(
        named = "VERIFY_DB",
        matches = ".+",
        disabledReason = "Needs a copy of a real FluxiBiz database, which CI has no way to make."
                + " See the class comment for how to make one and run this.")
class LiveDatabaseVerification {

    private static final UUID BUSINESS = UUID.fromString("3801ec4b-0814-4da6-b769-4651fbe06087");

    /**
     * No unit, no item type, no track stock — the shape the plan is about.
     * Four categories so a scoped decision has something to be scoped to.
     */
    private static final String CATALOGUE = """
            product_name,product_code,department
            Shirt,P001,Clothing
            Sneakers,P002,Shoes
            Rice,P003,Food
            Haircut,P004,Services
            """;

    @Autowired
    private AssistedMigrationService migrations;

    @MockitoBean
    private MinioService minio;

    /**
     * Stands in for MinIO, and behaves like it.
     *
     * Keyed rather than fixed, because a settings migration holds five files
     * at once and a stub that returns the same bytes for every key would have
     * every source read whichever file was attached last — which is a test
     * that passes for the wrong reason, or fails mysteriously.
     */
    private final Map<String, byte[]> stored = new LinkedHashMap<>();

    @BeforeEach
    void actAsAnOperator() {
        Jwt jwt = Jwt.withTokenValue("test")
                .header("alg", "none")
                .subject(UUID.randomUUID().toString())
                .claim("scope", "admin-business:manage")
                .build();

        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority("SCOPE_admin-business:manage"))));

        stored.clear();

        when(minio.uploadImportFile(any(), any())).thenAnswer(call -> {
            org.springframework.web.multipart.MultipartFile file = call.getArgument(0);
            String key = "verify/" + UUID.randomUUID();

            stored.put(key, file.getBytes());

            return key;
        });

        when(minio.readImportFile(anyString()))
                .thenAnswer(call -> new ByteArrayInputStream(stored.get(call.getArgument(0))));
    }

    /** A migration with the file attached, read, and its columns matched. */
    private AssistedMigration started() {
        AssistedMigration migration =
                migrations.create(BUSINESS, ImportTargetType.ITEM, LocalDateTime.now());

        migrations.addSource(BUSINESS, migration.getId(), new MockMultipartFile(
                "file", "verify.csv", "text/csv",
                CATALOGUE.getBytes(StandardCharsets.UTF_8)), null);

        migrations.analyzeAll(BUSINESS, migration.getId());

        Map<String, String> mapping = new LinkedHashMap<>();
        mapping.put("product_name", ImportField.NAME.name());
        mapping.put("product_code", ImportField.SKU.name());
        mapping.put("department", ImportField.ITEM_GROUP.name());

        migrations.saveMapping(BUSINESS, migration.getId(), mapping, Map.of());

        return migrations.transform(BUSINESS, migration.getId());
    }

    private MigrationIssue openIssueFor(UUID migrationId, ImportField field) {
        return migrations.findIssues(BUSINESS, migrationId).stream()
                .filter(issue -> "FIELD_MISSING".equals(issue.getCode()))
                .filter(issue -> field.name().equals(issue.getTargetField()))
                .filter(MigrationIssue::isBlocking)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no open " + field + " question"));
    }

    private void decide(UUID migrationId, ImportField field, Map<String, Object> resolution) {
        migrations.resolve(
                BUSINESS, migrationId, openIssueFor(migrationId, field).getId(), resolution);
        migrations.transform(BUSINESS, migrationId);
    }

    /**
     * The whole plan, on one migration, against the real catalogue.
     *
     * Kept as one test on purpose: the plan's claims are about a sequence — a
     * decision, then the count that follows it, then the file that results —
     * and splitting them would mean either re-running the sequence four times
     * or sharing state between tests that then have to run in order.
     */
    @Test
    void resolvesAWholeCatalogueWithGroupedDecisions() throws Exception {
        AssistedMigration migration = started();
        UUID id = migration.getId();

        // Three questions, one per field, each covering all four rows. (§13)
        assertThat(migrations.missingFields(BUSINESS, id).fields())
                .filteredOn(status -> status.blocking() && status.missing() > 0)
                .extracting(status -> status.field().name())
                .containsExactlyInAnyOrder("UNIT", "ITEM_TYPE", "TRACK_INVENTORY");

        assertThat(migration.getUnresolvedIssueCount()).isEqualTo(3);

        // §3 — services are services; everything else is physical.
        decide(id, ImportField.ITEM_TYPE, Map.of(
                "value", "Service", "scope", "CATEGORY", "scopeValue", "Services"));
        decide(id, ImportField.ITEM_TYPE, Map.of("value", "Physical", "scope", "ALL"));

        // §5 — the service's stock question answered itself, and was not asked.
        assertThat(migrations.missingFields(BUSINESS, id).fields())
                .filteredOn(status -> status.field() == ImportField.TRACK_INVENTORY)
                .singleElement()
                .satisfies(status -> assertThat(status.missing()).isEqualTo(3));

        // §10 — a unit this shop has never had. §2 — scoped to one category.
        decide(id, ImportField.UNIT, Map.of(
                "value", "pair", "name", "Pair", "category", "COUNT",
                "scope", "CATEGORY", "scopeValue", "Shoes"));

        // §13 — the rest of the group is still counted, and still asked about.
        assertThat(migrations.missingFields(BUSINESS, id).fields())
                .filteredOn(status -> status.field() == ImportField.UNIT)
                .singleElement()
                .satisfies(status -> {
                    assertThat(status.filled()).isEqualTo(1);
                    assertThat(status.missing()).isEqualTo(3);
                });

        // §9 — a unit the shop already owns, chosen for the remainder.
        decide(id, ImportField.UNIT, Map.of(
                "value", "pc", "name", "Piece", "category", "COUNT", "scope", "ALL"));

        decide(id, ImportField.TRACK_INVENTORY, Map.of("value", "Yes", "scope", "ALL"));

        // §17 — nothing left to decide.
        AssistedMigration ready = migrations.transform(BUSINESS, id);
        assertThat(ready.getUnresolvedIssueCount()).isZero();

        // §9 and §10 — reuse counted as reuse, invention counted as invention.
        AssistedMigrationDtos.PreparedSummary summary = migrations.summarise(BUSINESS, id);

        assertThat(summary.items()).isEqualTo(4);
        assertThat(summary.unitsExisting()).isEqualTo(1);
        assertThat(summary.unitsToCreate()).isEqualTo(1);
        assertThat(summary.unitsToCreateNames()).containsExactly("Pair (pair)");
        assertThat(summary.blocking()).isZero();

        // §20 — the workbook says exactly what the decisions said.
        byte[] workbook = migrations.preparedWorkbook(BUSINESS, id);
        XlsxSourceFileParser parser = new XlsxSourceFileParser();
        List<SourceRow> rows = new ArrayList<>();

        parser.readRows(new ByteArrayInputStream(workbook), 100, rows::add);

        assertThat(rows).extracting(row -> row.value("Unit"))
                .containsExactly("pc", "pair", "pc", "pc");
        assertThat(rows).extracting(row -> row.value("Item Type"))
                .containsExactly("Physical", "Physical", "Physical", "Service");
        assertThat(rows).extracting(row -> row.value("Track Stock"))
                .containsExactly("Yes", "Yes", "Yes", "No");

        // The invented unit is declared, not merely used.
        assertThat(parser.readNamedSheet(
                new ByteArrayInputStream(workbook), XlsxSourceFileParser.UNITS_SHEET, 50))
                .extracting(row -> row.value("Name"))
                .contains("Pair");

        // §19 — handing over twice hands over the same job.
        ImportJobResponse first = migrations.prepareImport(BUSINESS, id);
        ImportJobResponse again = migrations.prepareImport(BUSINESS, id);

        assertThat(again.id()).isEqualTo(first.id());
    }

    /**
     * §11 — the shop counts boxes, and this migration would weigh them.
     *
     * The catalogue this runs against really does have a Box that counts, which
     * is the point: a stubbed catalogue would prove only that the stub was
     * wired up.
     */
    @Test
    void blocksAMigrationThatWouldRedefineAUnitTheShopAlreadyHas() {
        AssistedMigration migration = started();
        UUID id = migration.getId();

        decide(id, ImportField.ITEM_TYPE, Map.of("value", "Physical", "scope", "ALL"));
        decide(id, ImportField.TRACK_INVENTORY, Map.of("value", "Yes", "scope", "ALL"));
        decide(id, ImportField.UNIT, Map.of(
                "value", "box", "name", "Box", "category", "MASS", "scope", "ALL"));

        assertThat(migrations.findIssues(BUSINESS, id))
                .filteredOn(issue -> "UNIT_TYPE_CONFLICT".equals(issue.getCode()))
                .singleElement()
                .satisfies(issue -> assertThat(issue.isBlocking()).isTrue());

        assertThatThrownBy(() -> migrations.prepareImport(BUSINESS, id))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("still need a decision");
    }

    /** §12 — a unit decision that could not be acted on is refused outright. */
    @Test
    void refusesAUnitDecisionWithNoMeasure() {
        AssistedMigration migration = started();
        UUID id = migration.getId();
        UUID issueId = openIssueFor(id, ImportField.UNIT).getId();

        assertThatThrownBy(() -> migrations.resolve(
                BUSINESS, id, issueId, Map.of("value", "sk", "name", "Sack", "scope", "ALL")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("counts, weighs or measures");

        assertThatThrownBy(() -> migrations.resolve(
                BUSINESS, id, issueId,
                Map.of("value", "sk", "name", "Sack", "category", "LENGTH", "scope", "ALL")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not something a unit can measure");
    }
}
