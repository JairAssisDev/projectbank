package com.digitalbank.transfer.domain.exception;

public class InvalidTransferException extends RuntimeException {

    public InvalidTransferException(String message) {
        super(message);
    }
}
