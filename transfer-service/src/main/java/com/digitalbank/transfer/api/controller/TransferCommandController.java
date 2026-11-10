package com.digitalbank.transfer.api.controller;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.digitalbank.transfer.api.dto.TransferDtos.CreateTransferRequest;
import com.digitalbank.transfer.api.dto.TransferDtos.TransferAcceptedResponse;
import com.digitalbank.transfer.application.command.CreateTransferCommand;
import com.digitalbank.transfer.application.command.TransferCommandHandler;
import com.digitalbank.transfer.infrastructure.security.CurrentAccount;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/** Lado Command do CQRS. */
@RestController
@RequestMapping("/transfers")
@Tag(name = "Transferências - comandos", description = "Solicitação de transferência (processada via Saga)")
public class TransferCommandController {

    private final TransferCommandHandler handler;

    public TransferCommandController(TransferCommandHandler handler) {
        this.handler = handler;
    }

    @PostMapping
    @Operation(summary = "Transfere da minha conta para outra conta",
            description = "A conta de origem vem do token (usuário logado). Retorna 202 Accepted: o débito/crédito "
                    + "ocorre de forma assíncrona (Saga). Consulte o status em GET /transfers/{id}.")
    public ResponseEntity<TransferAcceptedResponse> create(
            @Valid @RequestBody CreateTransferRequest request,
            @Parameter(description = "Chave para tornar a requisição idempotente (ex.: UUID gerado pelo cliente)")
            @RequestHeader(value = "Idempotency-Key", required = false) @Size(max = 100) String idempotencyKey,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        // Ninguém movimenta a conta de outra pessoa: a origem é a conta do token.
        UUID fromAccountId = CurrentAccount.requireOwner(jwt, request.fromAccountId());
        var result = handler.handle(new CreateTransferCommand(fromAccountId, request.toAccountId(),
                request.amount(), idempotencyKey));
        var location = URI.create("/transfers/" + result.transferId());
        return ResponseEntity.accepted()
                .location(location)
                .header("Idempotent-Replayed", String.valueOf(result.replayed()))
                .body(new TransferAcceptedResponse(result.transferId(), result.status(), location.toString()));
    }
}
