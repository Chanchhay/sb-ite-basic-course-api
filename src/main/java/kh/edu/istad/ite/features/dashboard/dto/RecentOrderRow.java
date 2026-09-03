package kh.edu.istad.ite.features.dashboard.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One row of the dashboard's recent orders table, ready to render.
 *
 * The screen used to build this by fetching fifty receipts and every customer
 * the business has, then joining the two in the browser to put a name against
 * an id. The join belongs where both sides already live.
 */
public record RecentOrderRow(
        UUID orderId,
        /** Already prefixed with "#", or falling back to the order's own id. */
        String reference,
        /** The customer's name, or "Walk-in Customer" when the sale had none. */
        String customerName,
        /** Their initials, for the avatar that stands in for a missing picture. */
        String customerInitials,
        String customerAvatarUrl,
        /** The first item, and how many others rode with it — "Latte +2 more". */
        String product,
        String category,
        BigDecimal amount,
        /** Said the way the table says it: Paid, Success, Processing, Failed. */
        String status
) {
}
