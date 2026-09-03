package kh.edu.istad.ite.features.order.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * The one place an order's invoice number is decided, for every channel
 * that creates one — POS, the storefront, Telegram, Messenger.
 *
 * This used to be four copies of the same private method, one per checkout
 * service, each computing "how many orders does this business have so far"
 * and adding one — a read followed by a write, with nothing stopping two
 * checkouts on *any* channel, not just the same one, from reading the same
 * count and both claiming the number that follows it. The second insert
 * then died on {@code uk_orders_business_invoice}, surfaced to whoever was
 * checking out as a generic "conflicts with something already saved."
 *
 * {@code INSERT ... ON CONFLICT ... RETURNING} is a single statement
 * Postgres itself serializes per row, so there is no gap between reading a
 * number and claiming it for a second caller — on this channel or any
 * other — to land in. The count subquery only ever runs once per business,
 * the first time this is called for it, to seed the counter at wherever
 * the old {@code COUNT(*) + 1} scheme had already reached; every call
 * after that just increments the row {@code ON CONFLICT} finds.
 */
@Component
@RequiredArgsConstructor
public class InvoiceNumberGenerator {

    private static final DateTimeFormatter INVOICE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final EntityManager entityManager;

    public String next(UUID businessId) {
        String datePart = LocalDateTime.now().format(INVOICE_DATE);

        // Seeded from the highest sequence number actually stamped on an
        // order, not a row count: a deleted order lowers COUNT(*) without
        // undoing the number it once held, and reseeding from that count
        // reissues a number already sitting on a surviving row — exactly
        // the uk_orders_business_invoice collision this table exists to
        // prevent. The trailing digits are the same ones this method itself
        // always writes (see the format string below), so extracting and
        // maxing them recovers wherever numbering had actually reached.
        Number nextValue = (Number) entityManager.createNativeQuery("""
                INSERT INTO invoice_sequences (business_id, next_value)
                VALUES (:businessId, (
                    SELECT COALESCE(MAX(CAST(SUBSTRING(invoice_number FROM '[0-9]+$') AS BIGINT)), 0)
                    FROM orders WHERE business_owner_id = :businessId
                ) + 1)
                ON CONFLICT (business_id) DO UPDATE SET next_value = invoice_sequences.next_value + 1
                RETURNING next_value
                """)
                .setParameter("businessId", businessId)
                .getSingleResult();

        return "INV-" + datePart + "-" + String.format("%05d", nextValue.longValue());
    }
}
