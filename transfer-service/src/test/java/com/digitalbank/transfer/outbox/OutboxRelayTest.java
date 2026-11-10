package com.digitalbank.transfer.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.transaction.support.TransactionOperations;

import com.digitalbank.transfer.messaging.ReliablePublisher;
import com.digitalbank.transfer.messaging.ReliablePublisher.MessagePublishException;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    @Mock
    OutboxRepository repository;
    @Mock
    ReliablePublisher publisher;

    OutboxRelay relay;

    @BeforeEach
    void setUp() {
        relay = new OutboxRelay(repository, publisher, TransactionOperations.withoutTransaction(),
                Clock.fixed(NOW, ZoneOffset.UTC), 100);
    }

    private static OutboxMessage message(String routingKey) {
        return new OutboxMessage(UUID.randomUUID(), UUID.randomUUID(), "AccountOperationCommand",
                "bank.saga.exchange", routingKey, "{\"amount\":10.00}", NOW);
    }

    @Test
    void marksMessagesAsPublishedAfterBrokerConfirm() {
        var m = message("account.debit");
        when(repository.lockNextBatch(100)).thenReturn(List.of(m));

        relay.publishPending();

        assertThat(m.getPublishedAt()).isEqualTo(NOW);
        assertThat(m.getAttempts()).isEqualTo(1);
    }

    @Test
    void failedMessageStaysPendingAndDoesNotBlockOthers() {
        var failing = message("account.credit");
        var ok = message("account.debit");
        when(repository.lockNextBatch(100)).thenReturn(List.of(failing, ok));
        lenient().doThrow(new MessagePublishException("NACK"))
                .when(publisher).publish(any(), eq("account.credit"), any(), any());

        relay.publishPending();

        assertThat(failing.getPublishedAt()).isNull();
        assertThat(failing.getAttempts()).isEqualTo(1);
        assertThat(failing.getLastError()).isEqualTo("NACK");
        assertThat(ok.getPublishedAt()).isEqualTo(NOW);
    }

    @Test
    void amqpMessageCarriesJsonAndIdentifiers() {
        var m = message("account.debit");

        var amqp = OutboxRelay.toAmqp(m);

        assertThat(amqp.getMessageProperties().getContentType()).isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
        assertThat(amqp.getMessageProperties().getMessageId()).isEqualTo(m.getId().toString());
        assertThat(new String(amqp.getBody())).isEqualTo(m.getPayload());
    }
}
