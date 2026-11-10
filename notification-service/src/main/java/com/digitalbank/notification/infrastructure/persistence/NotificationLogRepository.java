package com.digitalbank.notification.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.digitalbank.notification.domain.model.NotificationLog;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

    boolean existsByTransferId(UUID transferId);

    List<NotificationLog> findTop100ByRecipientAccountIdOrderBySentAtDesc(UUID recipientAccountId);

    List<NotificationLog> findTop100ByOrderBySentAtDesc();
}
