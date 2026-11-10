package com.digitalbank.transfer.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionOperations;

import com.digitalbank.transfer.application.query.TransferProjector;
import com.digitalbank.transfer.domain.exception.IdempotencyConflictException;
import com.digitalbank.transfer.domain.exception.InvalidTransferException;
import com.digitalbank.transfer.domain.model.Transfer;
import com.digitalbank.transfer.domain.model.TransferStatus;
import com.digitalbank.transfer.infrastructure.messaging.config.RabbitNames;
import com.digitalbank.transfer.infrastructure.messaging.contract.AccountOperationCommand;
import com.digitalbank.transfer.infrastructure.outbox.OutboxService;
import com.digitalbank.transfer.infrastructure.persistence.TransferRepository;

@ExtendWith(MockitoExtension.class)
class TransferCommandHandlerTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final UUID FROM = UUID.randomUUID();
    private static final UUID TO = UUID.randomUUID();
    private static final BigDecimal AMOUNT = new BigDecimal("150.00");

    @Mock
    TransferRepository transfers;
    @Mock
    TransferProjector projector;
    @Mock
    OutboxService outbox;

    TransferCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new TransferCommandHandler(transfers, projector, outbox, TransactionOperations.withoutTransaction(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsPendingTransferAndSchedulesDebit() {
        var result = handler.handle(new CreateTransferCommand(FROM, TO, AMOUNT, null));

        assertThat(result.status()).isEqualTo(TransferStatus.PENDING);
        assertThat(result.replayed()).isFalse();
        verify(transfers).saveAndFlush(any(Transfer.class));
        verify(projector).project(any(Transfer.class));

        var payload = ArgumentCaptor.forClass(Object.class);
        verify(outbox).enqueue(any(), eq(result.transferId()), eq(RabbitNames.SAGA_EXCHANGE),
                eq(RabbitNames.DEBIT_RK), payload.capture());
        var command = (AccountOperationCommand) payload.getValue();
        assertThat(command.accountId()).isEqualTo(FROM);
        assertThat(command.amount()).isEqualByComparingTo(AMOUNT);
        assertThat(command.transferId()).isEqualTo(result.transferId());
    }

    @Test
    void rejectsTransferToSameAccount() {
        assertThatThrownBy(() -> handler.handle(new CreateTransferCommand(FROM, FROM, AMOUNT, null)))
                .isInstanceOf(InvalidTransferException.class);
        verifyNoInteractions(outbox);
    }

    @Test
    void rejectsNonPositiveAmount() {
        assertThatThrownBy(() -> handler.handle(new CreateTransferCommand(FROM, TO, BigDecimal.ZERO, null)))
                .isInstanceOf(InvalidTransferException.class);
    }

    @Test
    void rejectsMoreThanTwoDecimals() {
        assertThatThrownBy(() -> handler.handle(new CreateTransferCommand(FROM, TO, new BigDecimal("1.001"), null)))
                .isInstanceOf(InvalidTransferException.class);
    }

    @Test
    void sameIdempotencyKeyReturnsOriginalTransferWithoutNewSaga() {
        var existing = Transfer.request(FROM, TO, AMOUNT, "key-1", NOW);
        when(transfers.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

        var result = handler.handle(new CreateTransferCommand(FROM, TO, AMOUNT, "key-1"));

        assertThat(result.transferId()).isEqualTo(existing.getId());
        assertThat(result.replayed()).isTrue();
        verify(transfers, never()).saveAndFlush(any());
        verifyNoInteractions(outbox);
    }

    @Test
    void sameIdempotencyKeyWithDifferentPayloadIsConflict() {
        var existing = Transfer.request(FROM, TO, AMOUNT, "key-1", NOW);
        when(transfers.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> handler.handle(new CreateTransferCommand(FROM, TO, new BigDecimal("1.00"), "key-1")))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void concurrentRequestWithSameKeyReturnsWinner() {
        var winner = Transfer.request(FROM, TO, AMOUNT, "key-2", NOW);
        when(transfers.findByIdempotencyKey("key-2"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(transfers.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        var result = handler.handle(new CreateTransferCommand(FROM, TO, AMOUNT, "key-2"));

        assertThat(result.transferId()).isEqualTo(winner.getId());
        assertThat(result.replayed()).isTrue();
    }
}
