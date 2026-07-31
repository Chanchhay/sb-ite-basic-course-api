package kh.edu.istad.ite.features.cart.dto;

public record CartCountResponse(
        int totalItems,
        int storeCount
) {
}