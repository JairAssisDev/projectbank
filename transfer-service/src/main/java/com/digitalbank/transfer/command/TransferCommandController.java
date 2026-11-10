package com.digitalbank.transfer.command;

import java.math.BigDecimal;
import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.digitalbank.transfer.domain.TransferStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
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

    public record CreateTransferRequest(
            @Schema(example = "11111111-1111-1111-1111-111111111111") @NotNull UUID fromAccountId,
            @Schema(example = "22222222-2222-2222-2222-222222222222") @NotNull UUID toAccountId,
            @Schema(example = "150.00") @NotNull @DecimalMin(value = "0.01") @Digits(integer = 17, fraction = 2)
            BigDecimal amount) {
    }

    public record TransferAcceptedResponse(UUID transferId, TransferStatus status, String statusUrl) {
    }

    @PostMapping
    @Operation(summary = "Solicita uma transferência",
            description = "Retorna 202 Accepted: o débito/crédito ocorre de forma assíncrona (Saga). "
                    + "Consulte o status em GET /transfers/{id}.")
    public ResponseEntity<TransferAcceptedResponse> create(
            @Valid @RequestBody CreateTransferRequest request,
            @Parameter(description = "Chave para tornar a requisição idempotente (ex.: UUID gerado pelo cliente)")
            @RequestHeader(value = "Idempotency-Key", required = false) @Size(max = 100) String idempotencyKey) {
        var result = handler.handle(new CreateTransferCommand(request.fromAccountId(), request.toAccountId(),
                request.amount(), idempotencyKey));
        var location = URI.create("/transfers/" + result.transferId());
        return ResponseEntity.accepted()
                .location(location)
                .header("Idempotent-Replayed", String.valueOf(result.replayed()))
                .body(new TransferAcceptedResponse(result.transferId(), result.status(), location.toString()));
    }
}
