package com.digitalbank.account.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.digitalbank.account.api.AccountDtos.AccountResponse;
import com.digitalbank.account.api.AccountDtos.CreateAccountRequest;
import com.digitalbank.account.api.AccountDtos.StatementEntryResponse;
import com.digitalbank.account.service.AccountService;

import io.swagger.v3.oas.annotations.Operation;
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
    @Operation(summary = "Abre uma conta com saldo inicial")
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
        var account = service.open(request.ownerName(), request.document(), request.initialBalance());
        return ResponseEntity.created(URI.create("/accounts/" + account.getId()))
                .body(AccountResponse.from(account));
    }

    @GetMapping
    @Operation(summary = "Lista as contas")
    public List<AccountResponse> list() {
        return service.list().stream().map(AccountResponse::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta conta e saldo")
    public AccountResponse get(@PathVariable UUID id) {
        return AccountResponse.from(service.get(id));
    }

    @GetMapping("/{id}/statement")
    @Operation(summary = "Extrato (movimentações) da conta, mais recentes primeiro")
    public List<StatementEntryResponse> statement(@PathVariable UUID id,
                                                  @PageableDefault(size = 50) Pageable pageable) {
        return service.statement(id, pageable).stream().map(StatementEntryResponse::from).toList();
    }
}
