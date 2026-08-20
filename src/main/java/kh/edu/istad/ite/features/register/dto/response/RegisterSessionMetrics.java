package kh.edu.istad.ite.features.register.dto.response;

import java.math.BigDecimal;

/**
 * The headline figures above a page of register sessions.
 *
 * Totalled over everything the filter matched, not over the page being shown.
 * A page is an arbitrary twenty rows; a total that only counted those would
 * change every time the reader clicked "next", which is not what anyone reads
 * a total for.
 *
 * @param activeCount        drawers open right now — a fact about the shop this
 *                           minute, so deliberately not narrowed by the filter
 * @param totalOpening       cash counted into the filtered drawers at the start
 * @param totalCashSales     cash those shifts took
 * @param totalDiscrepancies how far the counts were out, over and short alike,
 *                           added as distances rather than netted off: a day
 *                           that was ten over and ten short is not a day that
 *                           balanced
 */
public record RegisterSessionMetrics(
        long activeCount,
        BigDecimal totalOpening,
        BigDecimal totalCashSales,
        BigDecimal totalDiscrepancies
) {
}
