package com.digitalbank.notification.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.digitalbank.notification.domain.NotificationLog;
import com.digitalbank.notification.domain.NotificationLogRepository;
import com.digitalbank.notification.security.CurrentAccount;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/notifications")
@Tag(name = "Notificações", description = "Notificações enviadas após transferências concluídas")
public class NotificationController {

    private final NotificationLogRepository repository;

    public NotificationController(NotificationLogRepository repository) {
        this.repository = repository;
    }

    public record NotificationResponse(UUID id, UUID transferId, UUID recipientAccountId, NotificationLog.Kind kind,
                                       String message, Instant sentAt) {

        static NotificationResponse from(NotificationLog n) {
            return new NotificationResponse(n.getId(), n.getTransferId(), n.getRecipientAccountId(), n.getKind(),
                    n.getMessage(), n.getSentAt());
        }
    }

    @GetMapping
    @Operation(summary = "Minhas últimas notificações (da conta do usuário logado)")
    public List<NotificationResponse> list(
            @Parameter(description = "Opcional; se informado, precisa ser a conta do usuário logado (senão 403)")
            @RequestParam(required = false) UUID accountId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        UUID me = CurrentAccount.requireOwner(jwt, accountId);
        return repository.findTop100ByRecipientAccountIdOrderBySentAtDesc(me).stream()
                .map(NotificationResponse::from).toList();
    }
}
