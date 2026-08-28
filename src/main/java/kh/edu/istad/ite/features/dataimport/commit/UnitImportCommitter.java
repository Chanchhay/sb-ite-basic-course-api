package kh.edu.istad.ite.features.dataimport.commit;

import kh.edu.istad.ite.features.catalog.dto.BusinessUnitRequest;
import kh.edu.istad.ite.features.catalog.entity.Unit;
import kh.edu.istad.ite.features.catalog.repository.UnitRepository;
import kh.edu.istad.ite.features.catalog.service.BusinessUnitService;
import kh.edu.istad.ite.features.dataimport.canonical.DeclaredUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Brings the units a workbook declared into being, before anything needs them.
 *
 * First of the dependency order, because everything downstream is measured in
 * one: an item cannot be created without a unit, and stock cannot be counted
 * without an item. Getting this wrong does not fail loudly — it fails as a
 * whole file of items refused one by one for a unit that was sitting in the
 * same workbook all along.
 *
 * Idempotent on purpose. A retried import must find the units its first
 * attempt created and reuse them rather than making a second Carton.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UnitImportCommitter {

    private final BusinessUnitService businessUnitService;
    private final UnitRepository unitRepository;

    /** How many units this brought into being. */
    public record UnitCommitOutcome(int created, int reused) {
    }

    /**
     * Creates every declared unit the shop has not got already.
     *
     * Its own transaction, and deliberately not the one the items are written
     * in: a unit created here has to be visible to every item that names it,
     * including the ones written in transactions that later fail.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UnitCommitOutcome commitUnits(UUID businessId, List<DeclaredUnit> declared) {
        if (declared == null || declared.isEmpty()) {
            return new UnitCommitOutcome(0, 0);
        }

        int created = 0;
        int reused = 0;

        for (DeclaredUnit unit : declared) {
            if (alreadyThere(businessId, unit)) {
                reused++;
                continue;
            }

            try {
                businessUnitService.createUnit(businessId, new BusinessUnitRequest(
                        unit.name(),
                        symbolFor(unit),
                        unit.category(),
                        unit.note()
                ));
                created++;
            } catch (RuntimeException e) {
                /*
                 * Left for the rows to report. A unit that could not be created
                 * makes every item naming it fail with a message about that
                 * item, which is where someone can act on it — stopping the
                 * whole import here would take the rows that were fine with it.
                 */
                log.warn("Import could not create unit {}: {}", unit.name(), e.getMessage());
            }
        }

        return new UnitCommitOutcome(created, reused);
    }

    /**
     * Whether the shop can already count in this.
     *
     * Asked by name rather than by symbol, because the name is what the
     * catalogue holds unique. A second import of the same workbook finds the
     * units the first one made and leaves them alone.
     */
    private boolean alreadyThere(UUID businessId, DeclaredUnit unit) {
        return unitRepository.findSelectableUnitsNamed(businessId, unit.name())
                .stream()
                .anyMatch(existing -> existing.getCategory() == unit.category());
    }

    /** A unit needs a symbol; a sheet that left it blank gets the name back. */
    private String symbolFor(DeclaredUnit unit) {
        if (unit.symbol() != null && !unit.symbol().isBlank()) {
            return unit.symbol().trim();
        }

        String name = unit.name().trim();

        return name.length() <= 20 ? name : name.substring(0, 20);
    }

    /** The id of a declared unit once it exists, for binding items to it. */
    public UUID findId(UUID businessId, String name) {
        return unitRepository.findSelectableUnitsNamed(businessId, name)
                .stream()
                .findFirst()
                .map(Unit::getId)
                .orElse(null);
    }
}
