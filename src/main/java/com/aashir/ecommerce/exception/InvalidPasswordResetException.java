package com.aashir.ecommerce.exception;

public class InvalidPasswordResetException extends RuntimeException {
    public InvalidPasswordResetException() {
        super("Invalid or expired code");
    }
}
