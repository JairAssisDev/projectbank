package com.digitalbank.transfer.query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.digitalbank.transfer.domain.TransferNotFoundException;
import com.digitalbank.transfer.domain.TransferStatus;
import com.digitalbank.transfer.security.CurrentAccount;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
    @Operation(summary = "Consulta o status de uma transferência que enviei ou recebi")
    public TransferViewResponse get(@PathVariable UUID id, @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        UUID me = CurrentAccount.id(jwt);
        // Transferência de terceiros responde 404 (e não 403) para não revelar que o ID existe.
        return views.findById(id)
                .filter(v -> me.equals(v.getFromAccountId()) || me.equals(v.getToAccountId()))
                .map(TransferViewResponse::from)
                .orElseThrow(() -> new TransferNotFoundException(id));
    }

    @GetMapping
    @Operation(summary = "Minhas movimentações (enviadas e recebidas), mais recentes primeiro")
    public PageResponse<TransferViewResponse> byAccount(
            @Parameter(description = "Opcional; se informado, precisa ser a conta do usuário logado (senão 403)")
            @RequestParam(required = false) UUID accountId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(size = 20) Pageable pageable) {
        UUID me = CurrentAccount.requireOwner(jwt, accountId);
        var page = views.findByAccount(me, pageable).map(TransferViewResponse::from);
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages());
    }
}
