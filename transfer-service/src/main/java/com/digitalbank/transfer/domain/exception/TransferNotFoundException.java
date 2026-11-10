package com.digitalbank.transfer.domain.exception;

import java.util.UUID;

public class TransferNotFoundException extends RuntimeException {

    public TransferNotFoundException(UUID id) {
        super("Transferência não encontrada: " + id);
    }
}
