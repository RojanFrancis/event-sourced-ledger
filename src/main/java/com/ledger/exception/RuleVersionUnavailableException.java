package com.ledger.exception;

/** A flag was raised by a rule version this build no longer contains, so it cannot be replayed. */
public class RuleVersionUnavailableException extends RuntimeException {
    public RuleVersionUnavailableException(String message) {
        super(message);
    }
}
