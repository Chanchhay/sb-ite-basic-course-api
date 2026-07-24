package kh.edu.istad.ite.shared.exception;

public record FiledErrorResponse(
        String field,
        String reason
) {
}