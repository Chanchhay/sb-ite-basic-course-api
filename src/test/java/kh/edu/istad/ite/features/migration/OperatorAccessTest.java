package kh.edu.istad.ite.features.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * The operator's door has to stay the size it is.
 *
 * Assisted migration needs a support operator to reach a shop they are not
 * staff of. The cheap way to arrange that was to let them past the tenant
 * check, which guards more than a hundred places across a dozen features — so
 * they would have gained that shop's carts, orders, customers and discounts to
 * buy a handover of three calls.
 *
 * These read the source rather than exercise behaviour, which is unusual and
 * deliberate: what matters is that nobody widens the shared check again, and
 * that is a fact about the code rather than about any one request.
 */
class OperatorAccessTest {

    private static final Path HELPER =
            Path.of("src/main/java/kh/edu/istad/ite/shared/helper/BusinessHelper.java");
    private static final Path MIGRATION = Path.of(
            "src/main/java/kh/edu/istad/ite/features/migration/service/AssistedMigrationService.java");

    private String read(Path path) {
        try {
            return Files.readString(path);
        } catch (Exception e) {
            throw new IllegalStateException("Could not read " + path, e);
        }
    }

    /**
     * The tenant check must not know about operators at all. Every feature that
     * shares it — cart, orders, customers, discounts — relies on it meaning
     * exactly one thing.
     */
    @Test
    void leavesTheSharedTenantCheckAlone() {
        String helper = read(HELPER);
        int accessible = helper.indexOf("public Business findAccessibleBusiness");
        int nextMethod = helper.indexOf("public ", accessible + 10);
        String body = helper.substring(accessible, nextMethod < 0 ? helper.length() : nextMethod);

        assertThat(body)
                .as("the shared tenant check must not admit platform operators")
                .doesNotContain("isPlatformOperator");
    }

    /** And the operator's own door has to actually check the authority. */
    @Test
    void guardsTheOperatorDoorWithTheAuthority() {
        String helper = read(HELPER);

        assertThat(helper).contains("public Business findBusinessForOperator");

        int operator = helper.indexOf("public Business findBusinessForOperator");
        int nextMethod = helper.indexOf("public ", operator + 10);
        String body = helper.substring(operator, nextMethod < 0 ? helper.length() : nextMethod);

        assertThat(body).contains("isPlatformOperator");
        assertThat(body).contains("FORBIDDEN");
    }

    /**
     * Two callers, and they are the two ends of one handover: migration asking
     * for the shop, and the importer's own operator methods letting it in. The
     * moment a third feature wants this, it deserves the same conversation
     * rather than a quiet reuse.
     */
    @Test
    void isUsedByMigrationAndTheImportersOwnDoorOnly() throws Exception {
        try (Stream<Path> sources = Files.walk(Path.of("src/main/java"))) {
            List<String> callers = sources
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.endsWith("BusinessHelper.java"))
                    .filter(path -> read(path).contains("findBusinessForOperator("))
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .toList();

            assertThat(callers)
                    .containsExactly("AssistedMigrationService.java", "ImportJobServiceImpl.java");
        }
    }

    /**
     * And migration reaches the importer only through the three methods meant
     * for it — not by calling the shop-facing ones.
     */
    @Test
    void reachesTheImporterOnlyThroughItsOwnDoor() {
        String migration = read(MIGRATION);

        assertThat(migration)
                .as("migration must not call the shop-facing import methods")
                .doesNotContain("importJobService.upload(")
                .doesNotContain("importJobService.saveMapping(")
                .doesNotContain("importJobService.findJob(");

        assertThat(migration)
                .contains("uploadAsOperator")
                .contains("saveMappingAsOperator")
                .contains("findJobAsOperator");
    }
}
