package kh.edu.istad.ite.features.dataimport.commit;

import kh.edu.istad.ite.features.dataimport.canonical.ImportRecord;
import kh.edu.istad.ite.features.dataimport.canonical.MappingPlan;
import kh.edu.istad.ite.features.dataimport.entity.ImportJob;
import kh.edu.istad.ite.shared.enums.ImportTargetType;

import java.util.UUID;

/**
 * Writes one checked row into the catalogue, through the ordinary services.
 *
 * Never straight into the tables. The catalogue and the inventory ledger hold
 * rules an import has no business restating — how a slug is made unique, how a
 * stock layer is opened and costed, what a second opening balance means — and
 * the fastest way to end up with a catalogue that half the application cannot
 * read is to insert rows around them.
 *
 * That the checking step already found these rows acceptable does not make
 * this a formality: the shop has been trading in the meantime, and the
 * services get the last word.
 */
public interface ImportCommitter {

    ImportTargetType targetType();

    /**
     * @param matchedEntityId what checking found this row already exists as,
     *                        or null if it is new
     */
    CommitOutcome commit(
            ImportJob job,
            ImportRecord record,
            UUID matchedEntityId,
            MappingPlan plan
    );
}
