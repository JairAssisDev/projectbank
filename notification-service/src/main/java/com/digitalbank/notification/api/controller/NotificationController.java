package com.digitalbank.notification.api.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.digitalbank.notification.api.dto.NotificationResponse;
import com.digitalbank.notification.application.service.NotificationService;
import com.digitalbank.notification.infrastructure.security.CurrentAccount;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/notifications")
@Tag(name = "Notificações", description = "Notificações enviadas após transferências concluídas")
public class NotificationController {

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    @Operation(summary = "Minhas últimas notificações (da conta do usuário logado)")
    public List<NotificationResponse> list(
            @Parameter(description = "Opcional; se informado, precisa ser a conta do usuário logado (senão 403)")
            @RequestParam(required = false) UUID accountId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        UUID me = CurrentAccount.requireOwner(jwt, accountId);
        return notifications.recentFor(me).stream().map(NotificationResponse::from).toList();
    }
}
