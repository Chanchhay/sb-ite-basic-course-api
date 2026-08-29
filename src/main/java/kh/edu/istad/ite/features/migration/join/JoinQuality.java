package kh.edu.istad.ite.features.migration.join;

import kh.edu.istad.ite.shared.enums.JoinCardinality;

/**
 * What a proposed join would actually do, counted before anybody agrees to it.
 *
 * The numbers an operator needs are not "does this work" but "what does it
 * miss". A join matching 8,350 of 8,500 products is fine and the 150 are worth
 * knowing about; the same join matching 400 means the two files identify
 * products differently and approving it would import a catalogue with almost
 * no stock.
 *
 * @param duplicateLeftKeys  keys appearing more than once on the left
 * @param duplicateRightKeys keys appearing more than once on the right
 */
public record JoinQuality(
        int leftRows,
        int rightRows,
        int matchedLeftRows,
        int unmatchedLeftRows,
        int unmatchedRightRows,
        int duplicateLeftKeys,
        int duplicateRightKeys,
        JoinCardinality cardinality
) {

    /**
     * Whether this join can be used at all.
     *
     * Many-to-many is the one refusal. There is no honest way to decide which
     * left row a repeated right row belongs to, so joining would multiply rows
     * — a shop would end up with more items than they sent us, and no way to
     * tell which are real.
     */
    public boolean isUsable() {
        return cardinality != JoinCardinality.MANY_TO_MANY;
    }

    /** How much of the left side this join actually reaches, 0 to 1. */
    public double coverage() {
        return leftRows == 0 ? 0 : (double) matchedLeftRows / leftRows;
    }
}
