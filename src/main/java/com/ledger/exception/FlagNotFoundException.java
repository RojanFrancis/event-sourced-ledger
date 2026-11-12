package com.ledger.exception;

public class FlagNotFoundException extends RuntimeException {
    public FlagNotFoundException(String message) {
        super(message);
    }
}
