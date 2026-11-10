package com.digitalbank.transfer.infrastructure.outbox;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Enfileira mensagens no outbox — exige transação ativa (a do caso de uso que mudou o estado). */
@Service
public class OutboxService {

    private final OutboxRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxService(OutboxRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(UUID messageId, UUID aggregateId, String exchange, String routingKey, Object payload) {
        repository.save(new OutboxMessage(messageId, aggregateId, payload.getClass().getSimpleName(), exchange,
                routingKey, toJson(payload), clock.instant()));
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar mensagem do outbox", e);
        }
    }
}
