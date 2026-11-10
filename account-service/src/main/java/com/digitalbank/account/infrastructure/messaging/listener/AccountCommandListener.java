package com.digitalbank.account.infrastructure.messaging.listener;

import java.time.Clock;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.digitalbank.account.application.service.AccountService;
import com.digitalbank.account.domain.model.OperationResult;
import com.digitalbank.account.infrastructure.messaging.config.RabbitNames;
import com.digitalbank.account.infrastructure.messaging.contract.AccountOperation;
import com.digitalbank.account.infrastructure.messaging.contract.AccountOperationCommand;
import com.digitalbank.account.infrastructure.messaging.contract.SagaReply;
import com.digitalbank.account.infrastructure.messaging.exception.NonRetryableMessageException;
import com.digitalbank.account.infrastructure.messaging.publisher.ReliablePublisher;

/**
 * Participante da Saga: executa o passo e responde ao orquestrador.
 * <p>
 * Falha de negócio (saldo insuficiente, conta inexistente) → resposta {@code success=false}.
 * Falha técnica (banco fora, broker sem confirmação) → exceção → retry → DLQ.
 * A reexecução é segura porque cada passo é idempotente (constraint única no livro-razão).
 */
@Component
public class AccountCommandListener {

    private static final Logger log = LoggerFactory.getLogger(AccountCommandListener.class);

    private final AccountService accountService;
    private final ReliablePublisher publisher;
    private final Clock clock;

    public AccountCommandListener(AccountService accountService, ReliablePublisher publisher, Clock clock) {
        this.accountService = accountService;
        this.publisher = publisher;
        this.clock = clock;
    }

    @RabbitListener(queues = RabbitNames.DEBIT_QUEUE)
    public void onDebit(AccountOperationCommand command) {
        validate(command);
        reply(command, AccountOperation.DEBIT,
                accountService.debit(command.transferId(), command.accountId(), command.amount()));
    }

    @RabbitListener(queues = RabbitNames.CREDIT_QUEUE)
    public void onCredit(AccountOperationCommand command) {
        validate(command);
        reply(command, AccountOperation.CREDIT,
                accountService.credit(command.transferId(), command.accountId(), command.amount()));
    }

    @RabbitListener(queues = RabbitNames.REFUND_QUEUE)
    public void onRefund(AccountOperationCommand command) {
        validate(command);
        reply(command, AccountOperation.REFUND,
                accountService.refund(command.transferId(), command.accountId(), command.amount()));
    }

    private void reply(AccountOperationCommand command, AccountOperation operation, OperationResult result) {
        SagaReply reply = switch (result) {
            case OperationResult.Success s -> SagaReply.success(command.transferId(), operation, clock.instant());
            case OperationResult.Failure(var reason) ->
                    SagaReply.failure(command.transferId(), operation, reason.name(), clock.instant());
        };
        log.info("Transferência {}: {} na conta {} → {}", command.transferId(), operation, command.accountId(),
                reply.success() ? "OK" : reply.failureReason());
        publisher.publish(RabbitNames.SAGA_EXCHANGE,
                RabbitNames.REPLY_RK_PREFIX + operation.name().toLowerCase(Locale.ROOT), reply);
    }

    static void validate(AccountOperationCommand c) {
        if (c == null || c.transferId() == null || c.accountId() == null
                || c.amount() == null || c.amount().signum() <= 0) {
            throw new NonRetryableMessageException("Comando de conta inválido: " + c);
        }
    }
}
