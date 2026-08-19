package kh.edu.istad.ite.config.database;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

/**
 * Runs the hand-written SQL in {@code database/} on boot.
 *
 * Hibernate runs with {@code ddl-auto: update}, which adds tables and columns
 * but never drops, narrows, or backfills. Everything it cannot do has always
 * lived in {@code database/} as numbered scripts — and being hand-run, they
 * were only ever as reliable as somebody remembering. {@code orders.tax_amount}
 * is what that costs: Hibernate could not add a NOT NULL column to a table with
 * rows in it, logged the failure, carried on booting, and every read of an
 * order failed until the script was run by hand weeks later. In a container the
 * problem is worse than forgetfulness — the image holds only the jar, so the
 * scripts were not present to run at all.
 *
 * <p>Each script runs once, tracked in {@code schema_scripts}. They are all
 * written to be safe to run twice, which is what makes it safe to adopt a
 * database where they were already applied by hand: the tracking table starts
 * empty there, every script runs again, and every one of them finds its work
 * already done.
 *
 * <p>This runs after Hibernate, not before. That ordering is the whole reason
 * this is a {@link CommandLineRunner} rather than Flyway: several scripts
 * operate on columns and tables Hibernate itself creates, and Flyway's
 * migration step is wired to run <em>before</em> the entity manager is built.
 * Under Flyway, {@code 003} would find {@code add_on_id} missing and skip its
 * constraint for good.
 */
@Slf4j
@Component
@RequiredArgsConstructor
// Ahead of the data seeders: they insert rows, and a seeder that runs against
// a schema still waiting to be repaired is the failure this class exists for.
@Order(0)
// Off under "test": the context test is built to boot with no database.
@Profile("!test")
@ConditionalOnProperty(
        name = "app.database.auto-migrate",
        havingValue = "true",
        matchIfMissing = true)
public class SchemaScriptRunner implements CommandLineRunner {

    private static final String SCRIPTS = "classpath:db/migration/*.sql";

    /**
     * Names this lock so two instances booting together cannot both migrate.
     * An arbitrary constant; it only has to be the same in every instance.
     */
    private static final long LOCK_KEY = 8_014_233_517_907_441L;

    private final DataSource dataSource;

    @Override
    public void run(String... args) throws Exception {
        Resource[] scripts = new PathMatchingResourcePatternResolver().getResources(SCRIPTS);

        if (scripts.length == 0) {
            // Not fatal on its own, but it means the packaging step that copies
            // database/ into the jar has been lost, and the next script written
            // will silently never run.
            log.warn("No schema scripts found on the classpath at {}", SCRIPTS);
            return;
        }

        java.util.Arrays.sort(scripts, Comparator.comparing(Resource::getFilename));

        // One connection throughout: the advisory lock is held by the session
        // that took it, so releasing it from a pooled connection picked up
        // later would release nothing.
        try (Connection connection = dataSource.getConnection()) {
            lock(connection);
            try {
                createTrackingTable(connection);
                Map<String, String> applied = readApplied(connection);

                for (Resource script : scripts) {
                    apply(connection, script, applied);
                }
            } finally {
                // A failed script leaves its own BEGIN open and aborted — its
                // COMMIT never ran — and Postgres refuses everything else on
                // that connection until the block ends. Without this, the
                // unlock below fails too and its "current transaction is
                // aborted" buries the error that actually stopped the boot.
                rollbackQuietly(connection);
                unlock(connection);
            }
        }
    }

    private void apply(Connection connection, Resource script, Map<String, String> applied)
            throws Exception {

        String name = script.getFilename();
        String sql = script.getContentAsString(StandardCharsets.UTF_8);
        String checksum = checksum(sql);
        String previous = applied.get(name);

        if (previous != null) {
            if (!previous.equals(checksum)) {
                // Not re-run and not fatal: the script's work is already in the
                // database, and re-running an edited script is how a careful
                // change becomes a destructive one. The convention is a new
                // numbered script, so say plainly that one is missing.
                log.warn(
                        "{} has changed since it was applied. Edits to an applied script are"
                                + " ignored — add a new numbered script instead.",
                        name);
            }
            return;
        }

        log.info("Applying schema script {}", name);

        // The whole file in one call. The scripts carry their own BEGIN/COMMIT
        // and contain $$-quoted blocks, so splitting them on semicolons would
        // cut a DO block in half.
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }

        try (PreparedStatement record = connection.prepareStatement(
                "insert into schema_scripts (filename, checksum) values (?, ?)")) {
            record.setString(1, name);
            record.setString(2, checksum);
            record.executeUpdate();
        }

        log.info("Applied schema script {}", name);
    }

    private void createTrackingTable(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                    """
                    create table if not exists schema_scripts (
                        filename   varchar(255) primary key,
                        checksum   varchar(64)  not null,
                        applied_at timestamptz  not null default now()
                    )
                    """);
        }
    }

    private Map<String, String> readApplied(Connection connection) throws Exception {
        Map<String, String> applied = new HashMap<>();

        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "select filename, checksum from schema_scripts")) {
            while (rows.next()) {
                applied.put(rows.getString("filename"), rows.getString("checksum"));
            }
        }

        return applied;
    }

    private void lock(Connection connection) throws Exception {
        // Blocks rather than failing: a second instance booting during a deploy
        // should wait for the first to finish, not fall over.
        try (PreparedStatement statement =
                connection.prepareStatement("select pg_advisory_lock(?)")) {
            statement.setLong(1, LOCK_KEY);
            statement.execute();
        }
    }

    /**
     * Ends whatever transaction a failed script left open.
     *
     * The scripts carry their own BEGIN and COMMIT. When one fails partway the
     * COMMIT never runs, so the connection sits in an aborted block and every
     * later statement on it — including releasing the lock — fails with
     * "current transaction is aborted" instead of doing its job. Postgres has
     * already discarded the script's work; this only closes the block.
     */
    private void rollbackQuietly(Connection connection) {
        try (Statement statement = connection.createStatement()) {
            statement.execute("rollback");
        } catch (Exception e) {
            log.debug("Nothing to roll back after the schema scripts", e);
        }
    }

    private void unlock(Connection connection) {
        try (PreparedStatement statement =
                connection.prepareStatement("select pg_advisory_unlock(?)")) {
            statement.setLong(1, LOCK_KEY);
            statement.execute();
        } catch (Exception e) {
            // Closing the connection releases it anyway, so this is worth
            // knowing about but not worth failing a boot that otherwise worked.
            log.warn("Could not release the migration advisory lock", e);
        }
    }

    private static String checksum(String sql) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(sql.getBytes(StandardCharsets.UTF_8)));
    }
}
