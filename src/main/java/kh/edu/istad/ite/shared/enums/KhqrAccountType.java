package kh.edu.istad.ite.shared.enums;

public enum KhqrAccountType {
    /** Solo merchant / personal Bakong wallet. Requires bakongAccountId only. */
    INDIVIDUAL,
    /** Registered merchant. Additionally requires merchantId and acquiringBank. */
    MERCHANT
}
