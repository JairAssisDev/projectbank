package com.digitalbank.notification.service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Clock;
import java.util.Locale;
import java.util.random.RandomGenerator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.digitalbank.notification.domain.NotificationLog;
import com.digitalbank.notification.domain.NotificationLog.Kind;
import com.digitalbank.notification.domain.NotificationLogRepository;
import com.digitalbank.notification.messaging.contract.TransferCompletedEvent;

/**
 * Simula o envio de notificações (e-mail/push/SMS) para remetente e destinatário.
 * Idempotente: um evento reentregue não gera notificação duplicada.
 * {@code notification.simulated-failure-rate} permite simular instabilidade do provedor e ver Retry/DLQ em ação.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final Locale PT_BR = Locale.of("pt", "BR");

    private final NotificationLogRepository repository;
    private final Clock clock;
    private final double simulatedFailureRate;
    private final RandomGenerator random;

    @Autowired
    public NotificationService(NotificationLogRepository repository, Clock clock,
                               @Value("${notification.simulated-failure-rate:0.0}") double simulatedFailureRate) {
        this(repository, clock, simulatedFailureRate, RandomGenerator.getDefault());
    }

    NotificationService(NotificationLogRepository repository, Clock clock, double simulatedFailureRate,
                        RandomGenerator random) {
        this.repository = repository;
        this.clock = clock;
        this.simulatedFailureRate = simulatedFailureRate;
        this.random = random;
    }

    /** @return true se notificou, false se o evento já havia sido processado */
    @Transactional
    public boolean notifyTransferCompleted(TransferCompletedEvent event) {
        if (repository.existsByTransferId(event.transferId())) {
            log.info("Notificações da transferência {} já enviadas (evento duplicado ignorado)", event.transferId());
            return false;
        }
        callProvider();

        String value = brl(event.amount());
        var sent = new NotificationLog(event.transferId(), event.fromAccountId(), Kind.TRANSFER_SENT,
                "Você enviou %s para a conta %s.".formatted(value, event.toAccountId()), clock.instant());
        var received = new NotificationLog(event.transferId(), event.toAccountId(), Kind.TRANSFER_RECEIVED,
                "Você recebeu %s da conta %s.".formatted(value, event.fromAccountId()), clock.instant());
        repository.save(sent);
        repository.save(received);

        log.info("[NOTIFICAÇÃO] conta {} → {}", sent.getRecipientAccountId(), sent.getMessage());
        log.info("[NOTIFICAÇÃO] conta {} → {}", received.getRecipientAccountId(), received.getMessage());
        return true;
    }

    private void callProvider() {
        if (simulatedFailureRate > 0 && random.nextDouble() < simulatedFailureRate) {
            throw new NotificationProviderException("Provedor de notificação indisponível (falha simulada)");
        }
    }

    private static String brl(BigDecimal amount) {
        return NumberFormat.getCurrencyInstance(PT_BR).format(amount);
    }

    public static class NotificationProviderException extends RuntimeException {

        public NotificationProviderException(String message) {
            super(message);
        }
    }
}
