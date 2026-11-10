# Arquitetura — Banco Digital

## Visão geral

```mermaid
flowchart LR
    client([Cliente HTTP])

    subgraph transfer[transfer-service :8082]
        cmd[Command API<br/>POST /transfers]
        qry[Query API<br/>GET /transfers]
        orch[Saga Orchestrator]
        outbox[(outbox_message)]
        relay[Outbox Relay]
        tdb[(transfer_db<br/>transfer + transfer_view)]
    end

    subgraph account[account-service :8081]
        auth[Auth API<br/>POST /auth/login → JWT]
        acc[Account API]
        part[Participante da Saga<br/>debit / credit / refund]
        adb[(account_db<br/>account + account_ledger)]
    end

    subgraph notification[notification-service :8083]
        napi[Notification API<br/>GET /notifications]
        notif[TransferCompleted Listener]
        ndb[(notification_db)]
    end

    mq{{RabbitMQ<br/>bank.saga.exchange<br/>bank.dlx}}

    client -- documento + senha --> auth
    auth --> adb
    client -- Bearer JWT --> cmd & qry & acc & napi
    acc --> adb
    qry --> tdb
    napi --> ndb
    cmd --> tdb
    cmd --> outbox
    relay --> outbox
    relay -- comandos/eventos --> mq
    mq -- account.debit/credit/refund --> part
    part --> adb
    part -- transfer.reply.* --> mq
    mq -- respostas --> orch
    orch --> tdb
    orch --> outbox
    mq -- transfer.completed --> notif
    notif --> ndb
```

Os serviços **não se chamam por HTTP**: toda comunicação entre eles passa pelo RabbitMQ. O JWT é validado
localmente em cada serviço com o segredo compartilhado `BANK_JWT_SECRET`.

## Autenticação e autorização

```mermaid
sequenceDiagram
    autonumber
    participant C as Cliente
    participant A as account-service
    participant T as transfer-service
    participant N as notification-service

    C->>A: POST /auth/login {document, password}
    A->>A: confere o hash BCrypt (account.password_hash)
    A-->>C: 200 {accessToken (JWT HS256, sub = ID da conta), expiresAt}
    C->>T: POST /transfers — Authorization: Bearer JWT
    T->>T: valida assinatura e expiração localmente<br/>origem da transferência = sub do token
    T-->>C: 202 Accepted
    C->>N: GET /notifications — Authorization: Bearer JWT
    N->>N: valida localmente e devolve só as notificações do sub
    N-->>C: 200
```

| Situação | Resposta |
|---|---|
| Sem token, token inválido ou expirado | `401` |
| Documento ou senha errados no login (mesma mensagem nos dois casos) | `401` |
| Informar no pedido uma conta que não é a do token (`fromAccountId`, `accountId`, `/accounts/{id}`) | `403` |
| Consultar transferência da qual não é remetente nem destinatário | `404` (não revela que o ID existe) |
| Rotas públicas | `POST /auth/login`, `POST /accounts`, Swagger, `/actuator/**` |

## Sequência — caminho feliz

```mermaid
sequenceDiagram
    autonumber
    participant C as Cliente
    participant T as transfer-service
    participant MQ as RabbitMQ
    participant A as account-service
    participant N as notification-service

    C->>T: POST /transfers (Bearer JWT, Idempotency-Key)
    T->>T: origem = conta do token
    T->>T: TX: transfer=PENDING + view + outbox(Debit)
    T-->>C: 202 Accepted {transferId}
    T->>MQ: relay publica account.debit (confirm)
    MQ->>A: DebitCommand
    A->>A: TX: UPDATE saldo WHERE saldo>=valor + ledger(DEBIT)
    A->>MQ: transfer.reply.debit (success)
    MQ->>T: SagaReply
    T->>T: TX: DEBITED + outbox(Credit)
    T->>MQ: account.credit
    MQ->>A: CreditCommand
    A->>A: TX: saldo += valor + ledger(CREDIT)
    A->>MQ: transfer.reply.credit (success)
    MQ->>T: SagaReply
    T->>T: TX: COMPLETED + outbox(TransferCompleted)
    T->>MQ: transfer.completed
    MQ->>N: TransferCompletedEvent
    N->>N: TX: notification_log (remetente e destinatário)
    C->>T: GET /transfers/{id} → COMPLETED
```

## Sequência — compensação (conta destino inexistente)

```mermaid
sequenceDiagram
    autonumber
    participant T as transfer-service
    participant MQ as RabbitMQ
    participant A as account-service

    T->>MQ: account.debit
    MQ->>A: Debit → OK
    A->>MQ: reply.debit success
    MQ->>T: DEBITED → outbox(Credit)
    T->>MQ: account.credit
    MQ->>A: Credit → ACCOUNT_NOT_FOUND
    A->>MQ: reply.credit failure
    MQ->>T: COMPENSATING → outbox(Refund)
    T->>MQ: account.refund
    MQ->>A: Refund (ledger REFUND) → OK
    A->>MQ: reply.refund success
    MQ->>T: COMPENSATED (failureReason = ACCOUNT_NOT_FOUND)
```

## Máquina de estados da Saga

```mermaid
stateDiagram-v2
    [*] --> PENDING
    PENDING --> DEBITED: debit OK
    PENDING --> FAILED: debit falhou
    DEBITED --> COMPLETED: credit OK
    DEBITED --> COMPENSATING: credit falhou
    COMPENSATING --> COMPENSATED: refund OK
    COMPENSATING --> COMPENSATION_FAILED: refund falhou
    COMPLETED --> [*]
    FAILED --> [*]
    COMPENSATED --> [*]
    COMPENSATION_FAILED --> [*]
```

A decisão de transição é a função pura `TransferSaga.decide(status, SagaStep, sucesso)` (pacote
`domain/saga`). O `TransferSagaOrchestrator` (pacote `application/saga`) traduz a resposta recebida
(`SagaReply.operation`) para `SagaStep`, aplica a transição e grava o próximo comando no outbox. Respostas
duplicadas ou fora de ordem são ignoradas.

## Camadas do código

Os três serviços têm a mesma organização de pacotes (`com.digitalbank.<serviço>`):

```mermaid
flowchart TB
    subgraph api[api — entrada HTTP]
        ctrl[controller]
        dto[dto]
        adv[exception<br/>ProblemDetail]
    end
    subgraph app[application — casos de uso e transações]
        svc[service<br/>command · query · saga]
    end
    subgraph dom[domain — regras de negócio]
        model[model]
        saga[saga<br/>máquina de estados]
        dex[exception]
    end
    subgraph infra[infrastructure — detalhes técnicos]
        pers[persistence<br/>Spring Data]
        msg[messaging<br/>config · contract · listener · publisher]
        obx[outbox]
        sec[security<br/>JWT]
        aud[audit<br/>DLQ → error_audit]
    end

    ctrl --> svc
    ctrl --> sec
    msg -- listener --> svc
    svc --> model & saga
    svc --> pers & obx
    obx --> msg
    msg -- mensagens das DLQs --> aud
```

O `domain` não importa nada das outras camadas. Exemplo no fluxo de transferência:

| Camada | Classes |
|---|---|
| `api` | `TransferCommandController`, `TransferQueryController`, `TransferDtos`, `ApiExceptionHandler` |
| `application` | `TransferCommandHandler`, `TransferSagaOrchestrator`, `TransferQueryService`, `TransferProjector`, `TransferView` |
| `domain` | `Transfer`, `TransferStatus`, `TransferSaga`, `SagaStep` |
| `infrastructure` | `TransferRepository`, `OutboxService`, `OutboxRelay`, `SagaReplyListener`, `ReliablePublisher`, `RabbitConfig`, `SecurityConfig`, `DeadLetterAuditListener` |

## Topologia RabbitMQ

| Fila | Dono | Binding em `bank.saga.exchange` | DLQ (binding em `bank.dlx`) |
|---|---|---|---|
| `account.debit.command.q` | account | `account.debit` | `account.debit.command.q.dlq` (`error.account.debit`) |
| `account.credit.command.q` | account | `account.credit` | `account.credit.command.q.dlq` (`error.account.credit`) |
| `account.refund.command.q` | account | `account.refund` | `account.refund.command.q.dlq` (`error.account.refund`) |
| `transfer.saga.reply.q` | transfer | `transfer.reply.#` | `transfer.saga.reply.q.dlq` (`error.transfer.reply.#`) |
| `notification.transfer-completed.q` | notification | `transfer.completed` | `notification.transfer-completed.q.dlq` (`error.transfer.completed`) |

### Caminho de uma mensagem com falha

```mermaid
flowchart LR
    Q[fila principal] --> L[listener]
    L -- exceção técnica --> R{tentativa < 3?}
    R -- sim, backoff 1s→2s --> L
    R -- não / exceção não-retentável --> P[RepublishMessageRecoverer<br/>+ x-exception-message/stacktrace]
    P --> DLX[bank.dlx<br/>rk = error.&lt;rk original&gt;]
    DLX --> DLQ[fila.dlq]
    DLQ --> AUD[DeadLetterAuditListener]
    AUD --> DB[(error_audit)]
```

Falhas de **negócio** (saldo insuficiente, conta inexistente) não são exceções: viram uma resposta
`success=false` e seguem o fluxo normal da Saga — nunca vão para a DLQ.
