package kh.edu.istad.ite.features.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * One business's running invoice counter.
 *
 * Exists purely so `ddl-auto: update` creates the table this row lives in —
 * every actual read and write against it goes through a single atomic
 * upsert statement (see {@code OrderServiceImpl.nextInvoiceNumber}), never
 * through this entity's own JPA lifecycle. `COUNT(*) + 1` used to stand in
 * for this and was exactly as safe as that sounds: two checkouts landing in
 * the same instant both counted the same existing rows, both computed the
 * same "next" number, and the second one's insert died on
 * {@code uk_orders_business_invoice}. A dedicated counter, incremented by
 * one statement the database itself serializes, is what removes the gap
 * between reading a number and claiming it.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "invoice_sequences")
public class InvoiceSequence {

    @Id
    @Column(name = "business_id", nullable = false, updatable = false)
    private UUID businessId;

    @Column(name = "next_value", nullable = false)
    private long nextValue;
}
