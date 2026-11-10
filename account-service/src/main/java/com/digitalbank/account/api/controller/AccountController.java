package com.digitalbank.account.api.controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.digitalbank.account.api.dto.AccountDtos.AccountResponse;
import com.digitalbank.account.api.dto.AccountDtos.CreateAccountRequest;
import com.digitalbank.account.api.dto.AccountDtos.StatementEntryResponse;
import com.digitalbank.account.application.service.AccountService;
import com.digitalbank.account.infrastructure.security.CurrentAccount;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/accounts")
@Tag(name = "Contas", description = "Cadastro de clientes/contas, saldo e extrato")
public class AccountController {

    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    @PostMapping
    @SecurityRequirements // endpoint público (cadastro)
    @Operation(summary = "Abre uma conta com senha e saldo inicial")
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
        var account = service.open(request.ownerName(), request.document(), request.password(),
                request.initialBalance());
        return ResponseEntity.created(URI.create("/accounts/" + account.getId()))
                .body(AccountResponse.from(account));
    }

    @GetMapping("/me")
    @Operation(summary = "Minha conta e meu saldo (conta do usuário logado)")
    public AccountResponse me(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return AccountResponse.from(service.get(CurrentAccount.id(jwt)));
    }

    @GetMapping("/me/statement")
    @Operation(summary = "Meu extrato, mais recentes primeiro")
    public List<StatementEntryResponse> myStatement(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
                                                    @PageableDefault(size = 50) Pageable pageable) {
        return service.statement(CurrentAccount.id(jwt), pageable).stream().map(StatementEntryResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta conta e saldo (somente a própria conta; outra conta → 403)")
    public AccountResponse get(@PathVariable UUID id, @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        CurrentAccount.requireOwner(jwt, id);
        return AccountResponse.from(service.get(id));
    }

    @GetMapping("/{id}/statement")
    @Operation(summary = "Extrato da conta (somente a própria conta; outra conta → 403)")
    public List<StatementEntryResponse> statement(@PathVariable UUID id,
                                                  @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
                                                  @PageableDefault(size = 50) Pageable pageable) {
        CurrentAccount.requireOwner(jwt, id);
        return service.statement(id, pageable).stream().map(StatementEntryResponse::from).toList();
    }
}
