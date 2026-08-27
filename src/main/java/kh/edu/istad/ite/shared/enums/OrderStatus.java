package kh.edu.istad.ite.shared.enums;

public enum OrderStatus {
    PENDING,
    /** Staff has accepted the order and its stock has already left the shelf — payment can still come later. */
    CONFIRMED,
    PAID,
    FAILED,
    CANCELLED
}
