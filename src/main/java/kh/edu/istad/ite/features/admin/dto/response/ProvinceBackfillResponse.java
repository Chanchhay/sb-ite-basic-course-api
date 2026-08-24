package kh.edu.istad.ite.features.admin.dto.response;

import java.util.List;
import java.util.UUID;

/** Result of the one-time cityOrProvince -> provinceName auto-match backfill. */
public record ProvinceBackfillResponse(
        int matchedCount,
        int unmatchedCount,
        List<UnmatchedBusiness> unmatched
) {
    /** A business whose old cityOrProvince text didn't match any of the 25 provinces — left for the owner to fix via the map picker. */
    public record UnmatchedBusiness(
            UUID id,
            String name,
            String cityOrProvince
    ) {
    }
}
