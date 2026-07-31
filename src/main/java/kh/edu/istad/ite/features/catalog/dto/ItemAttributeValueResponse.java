package kh.edu.istad.ite.features.catalog.dto;

public record ItemAttributeValueResponse(
        String value,
        String label,
        String colorHex,
        Boolean available
) {
}
