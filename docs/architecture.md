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
        acc[Account API]
        part[Participante da Saga<br/>debit / credit / refund]
        adb[(account_db<br/>account + account_ledger)]
    end

    subgraph notification[notification-service :8083]
        notif[TransferCompleted Listener]
        ndb[(notification_db)]
    end

    mq{{RabbitMQ<br/>bank.saga.exchange<br/>bank.dlx}}

    client --> cmd & qry & acc
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

## Sequência — caminho feliz

```mermaid
sequenceDiagram
    autonumber
    participant C as Cliente
    participant T as transfer-service
    participant MQ as RabbitMQ
    participant A as account-service
    participant N as notification-service

    C->>T: POST /transfers (Idempotency-Key)
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
