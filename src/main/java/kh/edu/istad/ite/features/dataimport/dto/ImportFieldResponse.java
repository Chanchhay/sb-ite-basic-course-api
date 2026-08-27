package kh.edu.istad.ite.features.dataimport.dto;

import kh.edu.istad.ite.features.dataimport.field.ImportFieldRequirement;
import kh.edu.istad.ite.features.dataimport.field.ImportFieldType;

/**
 * One FluxiBiz field a column can be matched to, described for the screen.
 *
 * The matching screen builds its dropdowns from this rather than from a list
 * kept in the frontend, so a field added on the server appears there without a
 * second change.
 */
public record ImportFieldResponse(
        String field,
        String label,
        String help,
        ImportFieldType type,
        ImportFieldRequirement requirement
) {
}
