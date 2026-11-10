package com.digitalbank.transfer.api.controller;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.digitalbank.transfer.api.dto.TransferDtos.PageResponse;
import com.digitalbank.transfer.api.dto.TransferDtos.TransferViewResponse;
import com.digitalbank.transfer.application.query.TransferQueryService;
import com.digitalbank.transfer.infrastructure.security.CurrentAccount;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Lado Query do CQRS: só lê o read model (transfer_view). */
@RestController
@RequestMapping("/transfers")
@Tag(name = "Transferências - consultas", description = "Status e histórico de movimentações (read model)")
public class TransferQueryController {

    private final TransferQueryService queries;

    public TransferQueryController(TransferQueryService queries) {
        this.queries = queries;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta o status de uma transferência que enviei ou recebi")
    public TransferViewResponse get(@PathVariable UUID id, @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return TransferViewResponse.from(queries.findVisibleTo(id, CurrentAccount.id(jwt)));
    }

    @GetMapping
    @Operation(summary = "Minhas movimentações (enviadas e recebidas), mais recentes primeiro")
    public PageResponse<TransferViewResponse> byAccount(
            @Parameter(description = "Opcional; se informado, precisa ser a conta do usuário logado (senão 403)")
            @RequestParam(required = false) UUID accountId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(size = 20) Pageable pageable) {
        UUID me = CurrentAccount.requireOwner(jwt, accountId);
        return PageResponse.of(queries.findByAccount(me, pageable).map(TransferViewResponse::from));
    }
}
