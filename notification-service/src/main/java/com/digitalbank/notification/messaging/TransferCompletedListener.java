package com.digitalbank.notification.messaging;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.digitalbank.notification.messaging.contract.TransferCompletedEvent;
import com.digitalbank.notification.service.NotificationService;

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
