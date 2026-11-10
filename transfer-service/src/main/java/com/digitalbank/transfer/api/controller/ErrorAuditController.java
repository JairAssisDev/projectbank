package com.digitalbank.transfer.api.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.digitalbank.transfer.infrastructure.audit.ErrorAudit;
import com.digitalbank.transfer.infrastructure.audit.ErrorAuditRepository;

@RestController
@RequestMapping("/errors")
@Tag(name = "Auditoria de erros", description = "Mensagens que falharam definitivamente (DLQ)")
public class ErrorAuditController {

    private final ErrorAuditRepository repository;

    public ErrorAuditController(ErrorAuditRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    @Operation(summary = "Últimas 100 falhas definitivas de processamento")
    public List<ErrorAudit> latest() {
        return repository.findTop100ByOrderByCreatedAtDesc();
    }
}
