package kh.edu.istad.ite.features.order.dto;

import java.util.UUID;

/** Null detaches whichever customer the order currently has. */
public record UpdateOrderCustomerRequest(
        UUID customerId
) {
}
