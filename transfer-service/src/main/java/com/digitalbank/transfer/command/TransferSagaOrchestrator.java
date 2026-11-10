package com.digitalbank.transfer.command;

import java.time.Clock;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.digitalbank.transfer.domain.Transfer;
import com.digitalbank.transfer.domain.TransferSaga;
import com.digitalbank.transfer.domain.TransferSaga.SagaAction;
import com.digitalbank.transfer.domain.TransferSaga.Transition;
import com.digitalbank.transfer.messaging.NonRetryableMessageException;
import com.digitalbank.transfer.messaging.RabbitNames;
import com.digitalbank.transfer.messaging.contract.AccountOperationCommand;
import com.digitalbank.transfer.messaging.contract.SagaReply;
import com.digitalbank.transfer.messaging.contract.TransferCompletedEvent;
import com.digitalbank.transfer.outbox.OutboxService;
import com.digitalbank.transfer.query.TransferProjector;
import com.digitalbank.transfer.repository.TransferRepository;

/**
 * Orquestrador da Saga: reage às respostas do account-service, avança a máquina de estados e
 * agenda o próximo passo (ou a compensação) no outbox — tudo na mesma transação local.
 */
@Service
public class TransferSagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(TransferSagaOrchestrator.class);

    private final TransferRepository transfers;
    private final TransferProjector projector;
    private final OutboxService outbox;
    private final Clock clock;

    public TransferSagaOrchestrator(TransferRepository transfers, TransferProjector projector, OutboxService outbox,
                                    Clock clock) {
        this.transfers = transfers;
        this.projector = projector;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public void onReply(SagaReply reply) {
        Transfer transfer = transfers.findByIdForUpdate(reply.transferId())
                .orElseThrow(() -> new NonRetryableMessageException(
                        "Resposta para transferência desconhecida: " + reply.transferId()));

        switch (TransferSaga.decide(transfer.getStatus(), reply.operation(), reply.success())) {
            case Transition.Ignore(var reason) ->
                    log.warn("Transferência {}: resposta {} ignorada — {}", transfer.getId(), reply.operation(), reason);
            case Transition.Advance(var next, var action) -> {
                var previous = transfer.getStatus();
                transfer.moveTo(next, reply.success() ? null : reply.failureReason(), clock.instant());
                projector.project(transfer);
                dispatch(action, transfer);
                log.info("Transferência {}: {} → {} (resposta {} {})", transfer.getId(), previous, next,
                        reply.operation(), reply.success() ? "OK" : reply.failureReason());
            }
        }
    }

    private void dispatch(SagaAction action, Transfer t) {
        switch (action) {
            case SEND_CREDIT -> sendAccountCommand(RabbitNames.CREDIT_RK, t, t.getToAccountId());
            case SEND_REFUND -> sendAccountCommand(RabbitNames.REFUND_RK, t, t.getFromAccountId());
            case PUBLISH_COMPLETED -> {
                UUID eventId = UUID.randomUUID();
                outbox.enqueue(eventId, t.getId(), RabbitNames.SAGA_EXCHANGE, RabbitNames.TRANSFER_COMPLETED_RK,
                        new TransferCompletedEvent(eventId, t.getId(), t.getFromAccountId(), t.getToAccountId(),
                                t.getAmount(), clock.instant()));
            }
            case NONE -> {
                // estado terminal sem efeitos colaterais
            }
        }
    }

    private void sendAccountCommand(String routingKey, Transfer t, UUID accountId) {
        UUID messageId = UUID.randomUUID();
        outbox.enqueue(messageId, t.getId(), RabbitNames.SAGA_EXCHANGE, routingKey,
                new AccountOperationCommand(messageId, t.getId(), accountId, t.getAmount(), clock.instant()));
    }
}
