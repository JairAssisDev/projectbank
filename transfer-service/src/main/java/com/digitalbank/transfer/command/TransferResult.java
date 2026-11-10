package com.digitalbank.transfer.command;

import java.util.UUID;

import com.digitalbank.transfer.domain.Transfer;
import com.digitalbank.transfer.domain.TransferStatus;

/** @param replayed true quando a Idempotency-Key já existia e a transferência original foi devolvida */
public record TransferResult(UUID transferId, TransferStatus status, boolean replayed) {

    static TransferResult created(Transfer t) {
        return new TransferResult(t.getId(), t.getStatus(), false);
    }

    static TransferResult replayed(Transfer t) {
        return new TransferResult(t.getId(), t.getStatus(), true);
    }
}
