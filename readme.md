#  Financial Reconciliation & Settlement Engine

[![Java](https://img.shields.io/badge/Java-17-orange.svg?style=flat&logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7%2B-brightgreen.svg?style=flat&logo=springboot)](https://spring.io/projects/spring-boot)
[![Angular](https://img.shields.io/badge/Angular-16%2B-red.svg?style=flat&logo=angular)](https://angular.io/)
[![RabbitMQ](https://img.shields.io/badge/RabbitMQ-AMQP%20%2B%20DLQ-orange.svg?style=flat&logo=rabbitmq)](https://www.rabbitmq.com/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue.svg?style=flat&logo=postgresql)](https://www.postgresql.org/)
[![Testcontainers](https://img.shields.io/badge/Tested%20with-Testcontainers-black.svg?style=flat&logo=docker)](https://testcontainers.com/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

Motor distribuído de conciliação financeira e liquidação assíncrona concebido com **Clean Architecture**, resiliência transacional contra *race conditions* via **Pessimistic Locking**, controle de concorrência com `@Version`, mensageria orientada a eventos com Dead Letter Queue (DLQ) e interface reativa orientada a fluxos com **Angular & RxJS**.

---

##  Arquitetura do Ecossistema

O sistema adota o padrão de desacoplamento assíncrono entre a ingestão das ordens e a conciliação bancária:

```text
[ Cliente / Frontend ]
          │
    (1) POST /api/v1/transactions (JWT RBAC)
          ▼
┌───────────────────────── [ Spring Boot Core ] ─────────────────────────┐
│                                                                        │
│  [ TransactionController ] ── Idempotência por Referência Externa      │
│            │                                                           │
│            ▼ (Persiste PENDING)                                        │
│  [ PostgreSQL 15 ]                                                     │
│            │                                                           │
│            ▼ (Disparo de Evento)                                       │
│  [ RabbitTemplate ] ──► (Exchange: financial.exchange)                 │
└─────────────────────────────────────┬──────────────────────────────────┘
                                      │
                                      ▼
                        ┌───────────────────────────┐
                        │   RabbitMQ AMQP Broker    │
                        │                           │
                        │  financial.transaction    │
                        │        .reconcile         │
                        └─────────────┬─────────────┘
                                      │
                                      ▼ (Consumo Assíncrono)
┌───────────────────────── [ Engine de Liquidação ] ─────────────────────┐
│                                                                        │
│  [ ReconciliationConsumer ]                                            │
│            │                                                           │
│            ▼                                                           │
│  [ ReconciliationService ] ── PESSIMISTIC_WRITE Lock + Version Check   │
│            │                                                           │
│            ├──► [ External Payment Gateway Client ] (Resilience4j)    │
│            │          │                                                │
│            │          ├── Sucesso ──► UPDATE status = RECONCILED       │
│            │          └── Falha   ──► DLQ (Dead Letter Queue)          │
│            ▼                                                           │
│  [ PostgreSQL 15 ] (Audit Trail & Version Increment)                   │
└────────────────────────────────────────────────────────────────────────┘
```

---

## Destaques Técnicos de Engenharia

* **Prevenção Concorrente Estrita:** 
  Uso combinado de `@Lock(LockModeType.PESSIMISTIC_WRITE)` na leitura da transação para bloqueio ao nível de linha no PostgreSQL e `@Version` para detecção de concorrência otimista.
* **Garantia de Idempotência:**
  Validação de unicidade transacional baseada em `externalReference`, prevenindo duplicidade de débitos ou créditos em caso de novas tentativas de rede.
* **Tolerância a Falhas e DLQ:**
  Isolamento de falhas críticas via `RabbitMQ Dead Letter Exchange (financial.dlx.exchange)` e retenção de payloads inválidos na fila de contingência (`financial.transaction.reconcile.dlq`).
* **Testes de Integração com Infraestrutura Real:**
  Suíte ponta a ponta com **Testcontainers** instanciando instâncias descartáveis de PostgreSQL e RabbitMQ via Docker, orquestradas com **Awaitility** para validação determinística de fluxos não-bloqueantes.
* **Frontend Reativo e Desacoplado:**
  Dashboard em Angular com TypeScript em modo estrito, RxJS com operadores de cancelamento e polling (`timer`, `switchMap`, `takeUntil`), e `HttpInterceptor` para injeção automática de Bearer Token.

---

## 🛠 Stack Tecnológica

### Backend
* **Linguagem:** Java 17 (LTS)
* **Framework:** Spring Boot 2.7+ (Data JPA, Security, AMQP)
* **Controle de Acesso:** Spring Security com autenticação JWT e RBAC (`ROLE_ADMIN`, `ROLE_OPERATOR`)
* **Base de Dados:** PostgreSQL 15
* **Mensageria:** RabbitMQ 3.11 com gestão AMQP
* **Resiliência:** Resilience4j (Circuit Breaker / Retry)
* **Documentação:** Springdoc OpenAPI 3.0 / Swagger UI
* **Testes:** JUnit 5, Mockito, Testcontainers, Awaitility

### Frontend
* **Framework:** Angular 16+
* **Linguagem:** TypeScript 5.1+ (Strict Mode)
* **Reatividade:** RxJS 7.8
* **Estilização:** SCSS modularizado
* **Web Server de Produção:** Nginx Alpine

---

## Como Executar o Ecossistema

### Pré-requisitos
* [Docker Desktop](https://www.docker.com/) instalado e em execução
* [Git](https://git-scm.com/)

### Execução via Docker Compose (Recomendado)

Clone o repositório e execute a stack unificada:

```bash
git clone https://github.com/adisiojunior/reconciliation-engine.git
cd reconciliation-engine
docker compose up --build
```

Serviços disponíveis após a inicialização:
* **Frontend Dashboard:** [http://localhost:4200](http://localhost:4200)
* **Swagger UI / Documentação da API:** [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
* **RabbitMQ Management Dashboard:** [http://localhost:15672](http://localhost:15672) (`guest` / `guest`)
* **PostgreSQL:** `localhost:5432` (database: `reconciliation_db`)

---

## Execução da Suíte de Testes

Para executar os testes unitários com Mockito e os testes de integração com contêineres reais via Testcontainers:

```bash
# Na pasta do backend
./mvnw clean test
```

---

## Endpoints Principais da API

| Método | Endpoint | Descrição | Permissão |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/auth/login` | Emissão de Bearer Token JWT | Público |
| `POST` | `/api/v1/transactions` | Cria e despacha ordem para conciliação | `ROLE_ADMIN` |
| `GET` | `/api/v1/transactions` | Consulta paginada do Audit Trail | `ROLE_OPERATOR`, `ROLE_ADMIN` |
| `GET` | `/v3/api-docs` | Especificação OpenAPI 3.0 | Público |

---

## Autor

Desenvolvido por **Adísio Pereira Fialho Júnior**.  
* GitHub: [@adisiojunior](https://github.com/adisiojunior)
* Especialidade: Arquitetura de Microsserviços, Backend Java / Spring Boot, Angular e Sistemas Distribuídos.
