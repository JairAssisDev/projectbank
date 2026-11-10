package com.digitalbank.transfer.domain;

public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String key) {
        super("Idempotency-Key '" + key + "' já foi usada com outros dados de transferência");
    }
}
