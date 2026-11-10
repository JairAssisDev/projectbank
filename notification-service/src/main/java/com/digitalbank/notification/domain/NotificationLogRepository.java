package com.digitalbank.notification.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

    boolean existsByTransferId(UUID transferId);

    List<NotificationLog> findTop100ByRecipientAccountIdOrderBySentAtDesc(UUID recipientAccountId);

    List<NotificationLog> findTop100ByOrderBySentAtDesc();
}
