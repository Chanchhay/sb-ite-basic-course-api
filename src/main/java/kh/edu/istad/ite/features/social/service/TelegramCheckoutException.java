package kh.edu.istad.ite.features.social.service;

public class TelegramCheckoutException extends RuntimeException {

    public TelegramCheckoutException(String userFacingKhmerMessage) {
        super(userFacingKhmerMessage);
    }

    public TelegramCheckoutException(String userFacingKhmerMessage, Throwable cause) {
        super(userFacingKhmerMessage, cause);
    }
}