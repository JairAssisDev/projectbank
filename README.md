# Digital Bank — Microsserviços com Saga, CQRS e RabbitMQ

API REST de um banco digital simplificado: cadastro de contas, **transferência entre contas** com alta
concorrência e consistência, consulta de movimentações e **notificação** após transferências concluídas.

| Serviço | Porta | Responsabilidade | Banco |
|---|---|---|---|
| `account-service` | 8081 | Clientes, contas, saldo, extrato; participante da Saga (débito/crédito/estorno) | `account_db` |
| `transfer-service` | 8082 | Comandos e consultas de transferência (CQRS), **orquestração da Saga**, Outbox | `transfer_db` |
| `notification-service` | 8083 | Consome `TransferCompletedEvent` e simula o envio de notificações | `notification_db` |

**Stack:** Java 25 (Virtual Threads) · Spring Boot 3.5 · Spring AMQP · RabbitMQ 4.1 · PostgreSQL 17 · Flyway ·
springdoc-openapi (Swagger) · JUnit 5 / Mockito / AssertJ · Maven (multi-módulo) · Docker / Docker Compose.

Diagramas detalhados (sequência, máquina de estados, topologia de filas): [docs/architecture.md](docs/architecture.md).

---

## Como rodar

Pré-requisito: **Docker** (com Docker Compose v2). Java e Maven **não** são necessários na máquina — a
compilação acontece dentro do `Dockerfile` multi-stage.

```bash
docker compose up --build -d
```

```bash
docker compose ps
```

Aguarde todos ficarem `healthy` (o primeiro build baixa as dependências Maven e leva alguns minutos).
Opcionalmente copie `.env.example` para `.env` para trocar senhas ou ativar falhas simuladas.

| O quê | URL |
|---|---|
| Swagger — contas | http://localhost:8081/swagger-ui.html |
| Swagger — transferências | http://localhost:8082/swagger-ui.html |
| Swagger — notificações | http://localhost:8083/swagger-ui.html |
| RabbitMQ Management | http://localhost:15672 (usuário/senha `bank` / `bank`) |

### Contas pré-carregadas

| ID | Titular | Saldo |
|---|---|---|
| `11111111-1111-1111-1111-111111111111` | Ana Souza | 10.000,00 |
| `22222222-2222-2222-2222-222222222222` | Bruno Lima | 5.000,00 |
| `33333333-3333-3333-3333-333333333333` | Carla Mendes | 1.000,00 |
| `44444444-4444-4444-4444-444444444444` | Diego Ferreira | 0,00 |

### Roteiro rápido

```bash
# 1) Transferência (202 Accepted — processamento assíncrono via Saga)
curl -i -X POST http://localhost:8082/transfers \
  -H "Content-Type: application/json" -H "Idempotency-Key: demo-1" \
  -d '{"fromAccountId":"11111111-1111-1111-1111-111111111111","toAccountId":"22222222-2222-2222-2222-222222222222","amount":150.00}'

# 2) Status (PENDING → DEBITED → COMPLETED em milissegundos)
curl http://localhost:8082/transfers/<transferId>

# 3) Saldos e extrato
curl http://localhost:8081/accounts/11111111-1111-1111-1111-111111111111
curl http://localhost:8081/accounts/11111111-1111-1111-1111-111111111111/statement

# 4) Movimentações de uma conta (read model CQRS)
curl "http://localhost:8082/transfers?accountId=11111111-1111-1111-1111-111111111111"

# 5) Notificações enviadas
curl "http://localhost:8083/notifications?accountId=22222222-2222-2222-2222-222222222222"
```

Cenários de falha:

* **Saldo insuficiente** — transferir R$ 50,00 a partir de Diego (`4444…`) → status `FAILED`, `failureReason=INSUFFICIENT_FUNDS`.
* **Compensação** — transferir para um UUID inexistente → débito ok, crédito falha, estorno executado →
  status `COMPENSATED`, saldo de origem restaurado (o extrato mostra `DEBIT` e `REFUND`).
* **Retry / DLQ / auditoria** — suba com `NOTIFICATION_FAILURE_RATE=1.0` e faça uma transferência: o
  notification-service tenta 3 vezes (backoff 1s → 2s), a mensagem vai para
  `notification.transfer-completed.q.dlq` e aparece em `GET http://localhost:8083/errors`.
  Publicar um JSON inválido numa fila pelo painel do RabbitMQ produz o mesmo efeito em qualquer serviço.
* **Idempotência** — repetir o `POST` com a mesma `Idempotency-Key` devolve a mesma transferência
  (`Idempotent-Replayed: true`); com outro corpo → `409 Conflict`.

### Testes

Sem Java local, via Docker:

```bash
docker run --rm -v "${PWD}:/workspace" -w /workspace maven:3.9-eclipse-temurin-25 mvn -B test
```

Ou por serviço: `docker build -f account-service/Dockerfile --target test .` — com JDK 25 + Maven
instalados, basta `mvn test`.

Destaques:
* `AccountDebitConcurrencyTest` — 300 virtual threads debitam R$ 1,00 de uma conta com R$ 100,00 ao mesmo
  tempo: exatamente 100 débitos passam, saldo final 0, nenhum lost update.
* `TransferSagaTest` / `TransferSagaOrchestratorTest` — todas as transições, compensação e respostas duplicadas.
* `TransferCommandHandlerTest` — validações e idempotência (inclusive corrida com a mesma chave).
* `OutboxRelayTest`, `AccountServiceTest`, `NotificationServiceTest`.

---

## Decisões de design e arquitetura

### 1. Microsserviços com database-per-service
Cada serviço tem seu próprio PostgreSQL e nenhum acessa o banco do outro. Isso garante autonomia de deploy e
escala, mas elimina transações ACID entre serviços — daí a Saga.

### 2. Saga **orquestrada** (não coreografada)
O `transfer-service` é dono do fluxo: guarda o estado (`PENDING → DEBITED → COMPLETED`, ou
`COMPENSATING → COMPENSATED`) e decide o próximo passo. A lógica de transição é uma função pura
(`TransferSaga.decide`) — trivial de testar e de auditar. Com coreografia o fluxo ficaria espalhado em
listeners de vários serviços, mais difícil de rastrear em um domínio financeiro.
*Trade-off:* o orquestrador é um ponto central de lógica; mitigado por ser stateless (estado no banco) e
escalável horizontalmente.

### 3. Transactional Outbox
Mudança de estado da Saga e a mensagem a publicar são gravadas **na mesma transação**; o `OutboxRelay`
(agendado, em virtual thread) publica com *publisher confirms* e marca como enviada. Elimina o *dual-write*
(gravar no banco e cair antes de publicar, ou vice-versa). `FOR UPDATE SKIP LOCKED` permite várias
instâncias do relay em paralelo. Entrega é *at-least-once* → todos os consumidores são idempotentes.

### 4. Concorrência de saldo: UPDATE atômico condicional
`UPDATE account SET balance = balance - :v WHERE id = :id AND balance >= :v` — o PostgreSQL reavalia o
`WHERE` sob o lock de linha, então centenas de débitos simultâneos nunca deixam o saldo negativo nem perdem
atualizações, sem `SELECT ... FOR UPDATE` nem retries de *optimistic locking*. Uma `CHECK (balance >= 0)` no
banco é a última linha de defesa. Valores sempre `BigDecimal` / `NUMERIC(19,2)`.

### 5. Idempotência em todos os consumidores
* account: `UNIQUE (transfer_id, account_id, entry_type)` no livro-razão — um passo nunca é aplicado duas vezes.
* transfer: a máquina de estados ignora respostas duplicadas/fora de ordem; `SELECT ... FOR UPDATE` na
  transferência serializa respostas concorrentes da mesma Saga.
* notification: `UNIQUE (transfer_id, recipient_account_id)`.
* API: header `Idempotency-Key` com constraint única (inclui tratamento da corrida entre duas requisições).

### 6. CQRS
Lado **Command** (`TransferCommandController` → `TransferCommandHandler` / `TransferSagaOrchestrator`) escreve
no agregado `transfer`; lado **Query** (`TransferQueryController`) lê apenas o read model `transfer_view`,
desnormalizado e indexado por conta. A projeção é atualizada na mesma transação (CQRS lógico, consistência
imediata). Para escalar leituras basta mover a projeção para um consumidor de eventos/banco de leitura,
sem tocar no lado de escrita.

### 7. RabbitMQ: Retry, DLQ e auditoria de erros
* Exchange topic `bank.saga.exchange` para comandos, respostas e eventos; DLX `bank.dlx`.
* Toda fila tem `x-dead-letter-exchange` e uma `<fila>.dlq`.
* Retry **local** stateless com backoff exponencial (3 tentativas, esperas de 1s e 2s, configurável em
  `bank.messaging.retry.*`). Exceções não-retentáveis (`MessageConversionException`, payload inválido) vão
  direto para a DLQ.
* Ao esgotar, `RepublishMessageRecoverer` envia à DLX com cabeçalhos `x-exception-message`/`stacktrace`.
* `DeadLetterAuditListener` consome as DLQs e grava em `error_audit` (consultável em `GET /errors`).
* **Falha de negócio ≠ falha técnica:** saldo insuficiente não é exceção, é resposta `success=false` da Saga.
* Publisher confirms + `mandatory` em quem publica: mensagem só é considerada enviada após ACK do broker.

### 8. Java 25 + Virtual Threads
`spring.threads.virtual.enabled=true`: requisições Tomcat, listeners RabbitMQ (`VirtualThreadTaskExecutor`)
e o relay agendado rodam em virtual threads. Bloquear aguardando o *publisher confirm* ou o backoff do retry
custa praticamente nada. Com isso o limitador real de concorrência passa a ser o pool de conexões (Hikari),
dimensionado explicitamente. Código usa `record`, `sealed interface` e *pattern matching* em `switch`.

### 9. Contratos de mensagem sem biblioteca compartilhada
Cada serviço declara seus próprios `record`s de mensagem; o contrato é o JSON. O conversor usa o tipo do
parâmetro do listener (não o nome da classe Java do produtor), então serviços evoluem e fazem deploy
independentemente. *Trade-off:* duplicação pequena de código de infraestrutura (RabbitConfig, auditoria),
aceita em troca de zero acoplamento binário.

### 10. Spring Boot 3.5 em vez de 4.0
Spring Boot 3.5.x já suporta Java 25 e tem o ecossistema (Spring AMQP, springdoc, spring-retry) mais
maduro. `-Dnet.bytebuddy.experimental=true` é passado à JVM para o Byte Buddy (Hibernate/Mockito) aceitar o
bytecode Java 25 independentemente da versão.

### Possíveis evoluções
* Timeout de Saga (job que detecta transferências presas em `PENDING/DEBITED` e dispara compensação).
* Tratamento operacional de `COMPENSATION_FAILED` (alerta + reprocessamento manual da DLQ).
* Testes de integração com Testcontainers (PostgreSQL + RabbitMQ) e teste de carga (k6/Gatling).
* Observabilidade: tracing distribuído (Micrometer Tracing/OpenTelemetry) propagando `transferId`.
* API Gateway + autenticação (OAuth2/JWT).
