package com.digitalbank.transfer.application.command;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import com.digitalbank.transfer.application.query.TransferProjector;
import com.digitalbank.transfer.domain.exception.IdempotencyConflictException;
import com.digitalbank.transfer.domain.model.Transfer;
import com.digitalbank.transfer.infrastructure.messaging.config.RabbitNames;
import com.digitalbank.transfer.infrastructure.messaging.contract.AccountOperationCommand;
import com.digitalbank.transfer.infrastructure.outbox.OutboxService;
import com.digitalbank.transfer.infrastructure.persistence.TransferRepository;

/**
 * Command Handler: valida o pedido, cria a Saga em PENDING e agenda (via outbox) o primeiro passo — o débito.
 * Tudo em UMA transação local; a resposta HTTP é 202 e o restante acontece de forma assíncrona.
 */
@Service
public class TransferCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(TransferCommandHandler.class);

    private final TransferRepository transfers;
    private final TransferProjector projector;
    private final OutboxService outbox;
    private final TransactionOperations tx;
    private final Clock clock;

    public TransferCommandHandler(TransferRepository transfers, TransferProjector projector, OutboxService outbox,
                                  TransactionOperations tx, Clock clock) {
        this.transfers = transfers;
        this.projector = projector;
        this.outbox = outbox;
        this.tx = tx;
        this.clock = clock;
    }

    public TransferResult handle(CreateTransferCommand command) {
        String key = command.idempotencyKey();
        Optional<TransferResult> replay = findReplay(command);
        if (replay.isPresent()) {
            return replay.get();
        }
        try {
            return tx.execute(status -> start(command));
        } catch (DataIntegrityViolationException e) {
            // Corrida: duas requisições simultâneas com a mesma Idempotency-Key — a outra venceu.
            if (key == null) {
                throw e;
            }
            return findReplay(command).orElseThrow(() -> e);
        }
    }

    private TransferResult start(CreateTransferCommand command) {
        Transfer transfer = Transfer.request(command.fromAccountId(), command.toAccountId(), command.amount(),
                command.idempotencyKey(), clock.instant());
        transfers.saveAndFlush(transfer);
        projector.project(transfer);

        UUID messageId = UUID.randomUUID();
        outbox.enqueue(messageId, transfer.getId(), RabbitNames.SAGA_EXCHANGE, RabbitNames.DEBIT_RK,
                new AccountOperationCommand(messageId, transfer.getId(), transfer.getFromAccountId(),
                        transfer.getAmount(), clock.instant()));

        log.info("Transferência {} iniciada: {} → {} valor {}", transfer.getId(), transfer.getFromAccountId(),
                transfer.getToAccountId(), transfer.getAmount());
        return TransferResult.created(transfer);
    }

    private Optional<TransferResult> findReplay(CreateTransferCommand command) {
        if (command.idempotencyKey() == null) {
            return Optional.empty();
        }
        return transfers.findByIdempotencyKey(command.idempotencyKey()).map(existing -> {
            if (!existing.sameRequestAs(command.fromAccountId(), command.toAccountId(), command.amount())) {
                throw new IdempotencyConflictException(command.idempotencyKey());
            }
            return TransferResult.replayed(existing);
        });
    }
}
