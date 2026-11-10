package com.digitalbank.notification.infrastructure.messaging.listener;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.digitalbank.notification.application.service.NotificationService;
import com.digitalbank.notification.infrastructure.messaging.config.RabbitNames;
import com.digitalbank.notification.infrastructure.messaging.contract.TransferCompletedEvent;
import com.digitalbank.notification.infrastructure.messaging.exception.NonRetryableMessageException;

@Component
public class TransferCompletedListener {

    private final NotificationService notificationService;

    public TransferCompletedListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = RabbitNames.TRANSFER_COMPLETED_QUEUE)
    public void onTransferCompleted(TransferCompletedEvent event) {
        if (event == null || event.transferId() == null || event.fromAccountId() == null
                || event.toAccountId() == null || event.amount() == null) {
            throw new NonRetryableMessageException("Evento TransferCompleted inválido: " + event);
        }
        notificationService.notifyTransferCompleted(event);
    }
}
