package kh.edu.istad.ite.features.migration.join;

import java.util.UUID;

/**
 * A pair of columns that look like they identify the same thing.
 *
 * Offered, never applied. The confidence is shown rather than acted on for the
 * same reason column matching shows it: an operator who can see that two
 * columns overlap on 98% of their values and that another pair overlaps on 31%
 * knows immediately which to trust, and neither number is safe to act on
 * unattended.
 *
 * @param confidence 0 to 1, weighing how alike the headings are, how nearly
 *                   unique the values are, and how much the two sets overlap
 */
public record JoinSuggestion(
        UUID leftSourceId,
        String leftSourceName,
        String leftColumn,
        UUID rightSourceId,
        String rightSourceName,
        String rightColumn,
        double confidence,
        String reason,
        JoinQuality quality
) {

    public boolean isHigh() {
        return confidence >= 0.85;
    }
}
