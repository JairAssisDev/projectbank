package com.digitalbank.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.digitalbank.notification.domain.NotificationLog;
import com.digitalbank.notification.domain.NotificationLogRepository;
import com.digitalbank.notification.messaging.contract.TransferCompletedEvent;
import com.digitalbank.notification.service.NotificationService.NotificationProviderException;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    NotificationLogRepository repository;

    private final TransferCompletedEvent event = new TransferCompletedEvent(UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("150.00"), NOW);

    @Test
    void notifiesSenderAndReceiver() {
        var service = new NotificationService(repository, CLOCK, 0.0, RandomGenerator.getDefault());

        assertThat(service.notifyTransferCompleted(event)).isTrue();

        var captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(repository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(NotificationLog::getRecipientAccountId, NotificationLog::getKind)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(event.fromAccountId(), NotificationLog.Kind.TRANSFER_SENT),
                        org.assertj.core.groups.Tuple.tuple(event.toAccountId(),
                                NotificationLog.Kind.TRANSFER_RECEIVED));
        assertThat(captor.getAllValues().getFirst().getMessage()).contains("150,00");
    }

    @Test
    void duplicatedEventIsIgnored() {
        var service = new NotificationService(repository, CLOCK, 0.0, RandomGenerator.getDefault());
        when(repository.existsByTransferId(event.transferId())).thenReturn(true);

        assertThat(service.notifyTransferCompleted(event)).isFalse();
        verify(repository, never()).save(any());
    }

    @Test
    void providerFailureIsPropagatedSoTheMessageIsRetried() {
        RandomGenerator alwaysFail = () -> 0L;  // nextDouble() == 0.0 < taxa
        var service = new NotificationService(repository, CLOCK, 1.0, alwaysFail);

        assertThatThrownBy(() -> service.notifyTransferCompleted(event))
                .isInstanceOf(NotificationProviderException.class);
        verify(repository, never()).save(any());
    }
}
