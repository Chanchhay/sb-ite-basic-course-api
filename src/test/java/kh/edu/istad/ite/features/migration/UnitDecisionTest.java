package kh.edu.istad.ite.features.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import kh.edu.istad.ite.features.dataimport.canonical.DeclaredUnit;
import kh.edu.istad.ite.features.migration.resolve.UnitConflicts;
import kh.edu.istad.ite.features.migration.transform.TransformResult;
import kh.edu.istad.ite.shared.enums.UnitCategory;

/**
 * A unit is a word and a measure, and only the second one matters silently.
 *
 * Getting the name wrong is visible on every screen the shop opens. Getting
 * the measure wrong is invisible and changes what every quantity counted in
 * that unit means — so a unit the shop already has must not be quietly
 * redefined on the way in.
 */
class UnitDecisionTest {

    private DeclaredUnit unit(String name, String symbol, UnitCategory category) {
        return new DeclaredUnit(name, symbol, category, null);
    }

    private UnitConflicts.ExistingUnit theirs(String name, UnitCategory category) {
        return new UnitConflicts.ExistingUnit(name, category);
    }

    /** §11 — the shop counts boxes; this migration would weigh them. */
    @Test
    void refusesToRedefineWhatAnExistingUnitMeasures() {
        List<TransformResult.Finding> findings = UnitConflicts.find(
                List.of(unit("Box", "box", UnitCategory.MASS)),
                List.of(theirs("Box", UnitCategory.COUNT)));

        assertThat(findings)
                .singleElement()
                .satisfies(finding -> {
                    assertThat(finding.code()).isEqualTo("UNIT_TYPE_CONFLICT");
                    assertThat(finding.blocking()).isTrue();
                    assertThat(finding.message()).contains("counts").contains("weighs");
                });
    }

    /** §9 — the same unit they already have is reuse, not a conflict. */
    @Test
    void saysNothingWhenTheUnitAlreadyMeansTheSameThing() {
        assertThat(UnitConflicts.find(
                List.of(unit("Piece", "pc", UnitCategory.COUNT)),
                List.of(theirs("Piece", UnitCategory.COUNT))))
                .isEmpty();
    }

    /** §10 — a unit the shop has never had cannot conflict with anything. */
    @Test
    void saysNothingAboutAUnitTheShopDoesNotHave() {
        assertThat(UnitConflicts.find(
                List.of(unit("Pair", "pair", UnitCategory.COUNT)),
                List.of(theirs("Piece", UnitCategory.COUNT))))
                .isEmpty();
    }

    /**
     * A shop's own unit may share a name with the platform's.
     *
     * If either already measures what the migration means, nothing is being
     * redefined — blocking on the other one would refuse a migration entirely
     * consistent with the catalogue it is going into.
     */
    @Test
    void acceptsAUnitThatAgreesWithAnyOfTheSameName() {
        assertThat(UnitConflicts.find(
                List.of(unit("Box", "box", UnitCategory.COUNT)),
                List.of(theirs("Box", UnitCategory.MASS), theirs("Box", UnitCategory.COUNT))))
                .isEmpty();
    }

    /** Names are compared the way people write them, not byte for byte. */
    @Test
    void comparesNamesWithoutCaringAboutCaseOrSpacing() {
        assertThat(UnitConflicts.find(
                List.of(unit(" box ", "box", UnitCategory.MASS)),
                List.of(theirs("Box", UnitCategory.COUNT))))
                .hasSize(1);
    }
}
