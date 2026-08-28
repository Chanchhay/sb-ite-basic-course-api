package kh.edu.istad.ite.features.dataimport.dto;

import java.util.List;

/**
 * What an import will do about units, counted before anything is written.
 *
 * Units are the first dependency: every item is counted in one, and an item
 * whose unit cannot be resolved never gets created. Saying so on the review
 * step — rather than in the report afterwards — is the difference between a
 * shop adding a line to a sheet and a shop undoing an import.
 *
 * @param toReuse  names of units the shop already has
 * @param toCreate labels of units this file declares and will bring into being
 */
public record ImportUnitSummary(
        int reused,
        int created,
        int conflicts,
        List<String> toReuse,
        List<String> toCreate
) {
}
