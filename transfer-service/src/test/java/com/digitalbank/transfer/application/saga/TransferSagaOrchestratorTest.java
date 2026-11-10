package com.digitalbank.transfer.application.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.digitalbank.transfer.application.query.TransferProjector;
import com.digitalbank.transfer.domain.model.Transfer;
import com.digitalbank.transfer.domain.model.TransferStatus;
import com.digitalbank.transfer.infrastructure.messaging.config.RabbitNames;
import com.digitalbank.transfer.infrastructure.messaging.contract.AccountOperation;
import com.digitalbank.transfer.infrastructure.messaging.contract.AccountOperationCommand;
import com.digitalbank.transfer.infrastructure.messaging.contract.SagaReply;
import com.digitalbank.transfer.infrastructure.messaging.contract.TransferCompletedEvent;
import com.digitalbank.transfer.infrastructure.messaging.exception.NonRetryableMessageException;
import com.digitalbank.transfer.infrastructure.outbox.OutboxService;
import com.digitalbank.transfer.infrastructure.persistence.TransferRepository;

@ExtendWith(MockitoExtension.class)
class TransferSagaOrchestratorTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final UUID FROM = UUID.randomUUID();
    private static final UUID TO = UUID.randomUUID();

    @Mock
    TransferRepository transfers;
    @Mock
    TransferProjector projector;
    @Mock
    OutboxService outbox;

    TransferSagaOrchestrator orchestrator;
    Transfer transfer;

    @BeforeEach
    void setUp() {
        orchestrator = new TransferSagaOrchestrator(transfers, projector, outbox, Clock.fixed(NOW, ZoneOffset.UTC));
        transfer = Transfer.request(FROM, TO, new BigDecimal("50.00"), null, NOW);
    }

    private SagaReply reply(AccountOperation op, boolean success, String reason) {
        when(transfers.findByIdForUpdate(transfer.getId())).thenReturn(Optional.of(transfer));
        return new SagaReply(UUID.randomUUID(), transfer.getId(), op, success, reason, NOW);
    }

    @Test
    void debitOkSchedulesCreditOnDestinationAccount() {
        orchestrator.onReply(reply(AccountOperation.DEBIT, true, null));

        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.DEBITED);
        var payload = ArgumentCaptor.forClass(Object.class);
        verify(outbox).enqueue(any(), eq(transfer.getId()), eq(RabbitNames.SAGA_EXCHANGE),
                eq(RabbitNames.CREDIT_RK), payload.capture());
        assertThat(((AccountOperationCommand) payload.getValue()).accountId()).isEqualTo(TO);
        verify(projector).project(transfer);
    }

    @Test
    void debitFailureMarksTransferAsFailedWithReason() {
        orchestrator.onReply(reply(AccountOperation.DEBIT, false, "INSUFFICIENT_FUNDS"));

        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.FAILED);
        assertThat(transfer.getFailureReason()).isEqualTo("INSUFFICIENT_FUNDS");
        verifyNoInteractions(outbox);
    }

    @Test
    void creditOkCompletesAndPublishesEvent() {
        transfer.moveTo(TransferStatus.DEBITED, null, NOW);

        orchestrator.onReply(reply(AccountOperation.CREDIT, true, null));

        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        var payload = ArgumentCaptor.forClass(Object.class);
        verify(outbox).enqueue(any(), eq(transfer.getId()), eq(RabbitNames.SAGA_EXCHANGE),
                eq(RabbitNames.TRANSFER_COMPLETED_RK), payload.capture());
        var event = (TransferCompletedEvent) payload.getValue();
        assertThat(event.fromAccountId()).isEqualTo(FROM);
        assertThat(event.toAccountId()).isEqualTo(TO);
    }

    @Test
    void creditFailureCompensatesSourceAccount() {
        transfer.moveTo(TransferStatus.DEBITED, null, NOW);

        orchestrator.onReply(reply(AccountOperation.CREDIT, false, "ACCOUNT_NOT_FOUND"));

        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.COMPENSATING);
        var payload = ArgumentCaptor.forClass(Object.class);
        verify(outbox).enqueue(any(), eq(transfer.getId()), eq(RabbitNames.SAGA_EXCHANGE),
                eq(RabbitNames.REFUND_RK), payload.capture());
        assertThat(((AccountOperationCommand) payload.getValue()).accountId()).isEqualTo(FROM);
    }

    @Test
    void refundOkKeepsOriginalFailureReason() {
        transfer.moveTo(TransferStatus.COMPENSATING, "ACCOUNT_NOT_FOUND", NOW);

        orchestrator.onReply(reply(AccountOperation.REFUND, true, null));

        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.COMPENSATED);
        assertThat(transfer.getFailureReason()).isEqualTo("ACCOUNT_NOT_FOUND");
        verifyNoInteractions(outbox);
    }

    @Test
    void duplicatedReplyIsIgnored() {
        transfer.moveTo(TransferStatus.COMPLETED, null, NOW);

        orchestrator.onReply(reply(AccountOperation.CREDIT, true, null));

        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        verifyNoInteractions(outbox, projector);
    }

    @Test
    void unknownTransferIsNotRetryable() {
        var unknown = new SagaReply(UUID.randomUUID(), UUID.randomUUID(), AccountOperation.DEBIT, true, null, NOW);
        when(transfers.findByIdForUpdate(unknown.transferId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orchestrator.onReply(unknown)).isInstanceOf(NonRetryableMessageException.class);
    }
}
