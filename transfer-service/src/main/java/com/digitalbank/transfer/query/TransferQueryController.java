package com.digitalbank.transfer.query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.digitalbank.transfer.domain.TransferNotFoundException;
import com.digitalbank.transfer.domain.TransferStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Lado Query do CQRS: só lê o read model {@link TransferView}. */
@RestController
@RequestMapping("/transfers")
@Tag(name = "Transferências - consultas", description = "Status e histórico de movimentações (read model)")
@Transactional(readOnly = true)
public class TransferQueryController {

    private final TransferViewRepository views;

    public TransferQueryController(TransferViewRepository views) {
        this.views = views;
    }

    public record TransferViewResponse(UUID id, UUID fromAccountId, UUID toAccountId, BigDecimal amount,
                                       TransferStatus status, String failureReason, Instant createdAt,
                                       Instant updatedAt) {

        static TransferViewResponse from(TransferView v) {
            return new TransferViewResponse(v.getId(), v.getFromAccountId(), v.getToAccountId(), v.getAmount(),
                    v.getStatus(), v.getFailureReason(), v.getCreatedAt(), v.getUpdatedAt());
        }
    }

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta o status de uma transferência")
    public TransferViewResponse get(@PathVariable UUID id) {
        return views.findById(id).map(TransferViewResponse::from)
                .orElseThrow(() -> new TransferNotFoundException(id));
    }

    @GetMapping
    @Operation(summary = "Movimentações (enviadas e recebidas) de uma conta, mais recentes primeiro")
    public PageResponse<TransferViewResponse> byAccount(@RequestParam UUID accountId,
                                                        @PageableDefault(size = 20) Pageable pageable) {
        var page = views.findByAccount(accountId, pageable).map(TransferViewResponse::from);
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages());
    }
}
