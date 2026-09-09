package kh.edu.istad.ite.features.dashboard.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One row of the best-selling table: a catalogue item beside what it sold.
 *
 * Ranking these needs the sales figures and the catalogue at once, which is
 * why the screen used to pull the entire catalogue down to sort it. The rank
 * is settled here, so a page of rows is a page of the real ranking rather
 * than a page sorted among itself.
 */
public record BestSellingRow(
        UUID itemId,
        String name,
        String category,
        /** Revenue over the range asked for. Zero for an item that has not sold. */
        BigDecimal sales,
        long sold,
        String imageUrl
) {
}
