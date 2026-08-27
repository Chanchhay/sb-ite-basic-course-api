package kh.edu.istad.ite.features.dataimport.validation;

import kh.edu.istad.ite.features.dataimport.canonical.ImportRecord;
import kh.edu.istad.ite.features.dataimport.canonical.MappingPlan;
import kh.edu.istad.ite.shared.enums.ImportTargetType;

/**
 * Judges one canonical record against the shop's rules, before anything is
 * written.
 *
 * Implementations look things up through {@link ValidationContext} rather than
 * the repositories, so checking a large file stays a handful of queries; and
 * they record what each row claims back into the context, so the row after it
 * can be told it is a duplicate of the row before.
 *
 * Deliberately not a re-statement of the domain's rules. The catalogue and
 * inventory services still have the last word at commit time — what happens
 * here is that the shop gets to see the refusal in advance, per row, instead
 * of discovering it half-way through.
 */
public interface ImportRowValidator {

    ImportTargetType targetType();

    RowVerdict validate(ImportRecord record, int rowNumber, ValidationContext context, MappingPlan plan);
}
