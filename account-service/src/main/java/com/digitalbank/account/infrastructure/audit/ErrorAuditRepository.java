package com.digitalbank.account.infrastructure.audit;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ErrorAuditRepository extends JpaRepository<ErrorAudit, UUID> {

    List<ErrorAudit> findTop100ByOrderByCreatedAtDesc();
}
