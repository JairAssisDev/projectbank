package com.digitalbank.transfer.infrastructure.outbox;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxRepository extends JpaRepository<OutboxMessage, UUID> {

    /**
     * Próximo lote pendente. FOR UPDATE SKIP LOCKED permite várias instâncias do serviço
     * rodando o relay em paralelo sem publicar a mesma mensagem duas vezes.
     */
    @Query(value = """
            SELECT * FROM outbox_message
             WHERE published_at IS NULL
             ORDER BY created_at
             LIMIT :limit
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxMessage> lockNextBatch(@Param("limit") int limit);
}
