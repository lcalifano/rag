# RAG App - Microservices

Applicazione RAG (Retrieval-Augmented Generation) basata su architettura a microservizi. Permette agli utenti di caricare documenti, generare embedding vettoriali e chattare con un LLM che utilizza il contenuto dei documenti come contesto.

## Architettura

```
Client
  │
  ▼
API Gateway (8080)
  ├──▶ User Service (8081)      ── Auth, configurazione LLM
  ├──▶ Chat Service (8082)      ── Sessioni chat, RAG
  └──▶ Document Service (8083)  ── Upload e chunking documenti
                │
                ▼
         LLM Service (8000)     ── Generazione testo ed embedding

Tutti i servizi si registrano su Eureka Server (8761)
```

## Tech Stack

| Componente | Tecnologia |
|---|---|
| Java Services | Spring Boot 3.4.5, Java 21 |
| Service Discovery | Spring Cloud Netflix Eureka |
| API Gateway | Spring Cloud Gateway |
| Auth | Spring Security + JWT |
| Database | PostgreSQL 17 + pgvector |
| Message Broker | RabbitMQ 3 |
| LLM Service | Python 3.12, FastAPI, LangChain |
| Container | Docker + Docker Compose |

## Servizi

### Eureka Server (porta 8761)
Service registry per la discovery dei microservizi.

### API Gateway (porta 8080)
Entry point unico per tutti i client. Gestisce il routing e la validazione JWT.

| Route | Servizio |
|---|---|
| `/auth/**` | User Service |
| `/model/**` | User Service |
| `/chat/**` | Chat Service |
| `/documents/**` | Document Service |

### User Service (porta 8081)
Gestione utenti, autenticazione e configurazione dei provider LLM.

- `POST /auth/register` — Registrazione utente
- `POST /auth/login` — Login (restituisce JWT)
- `GET /model/settings/my` — Configurazioni LLM dell'utente
- `POST /model/settings/` — Salva configurazione LLM

### Chat Service (porta 8082)
Gestione sessioni chat con supporto RAG.

- `POST /chat/sessions` — Crea sessione
- `GET /chat/sessions` — Lista sessioni utente
- `POST /chat/sessions/{id}/messages` — Invia messaggio
- `GET /chat/sessions/{id}/messages` — Storico messaggi

Ad ogni messaggio, il servizio recupera i 5 chunk più simili dai documenti dell'utente e li passa come contesto al LLM.

### Document Service (porta 8083)
Upload, estrazione testo, chunking e generazione embedding.

- `POST /documents/upload` — Upload documento (max 50MB)
- `GET /documents/` — Lista documenti utente
- `GET /documents/{id}` — Dettaglio documento
- `DELETE /documents/{id}` — Elimina documento
- `GET /documents/search?query=...&limit=5` — Ricerca semantica

Configurazione chunking: 400 token per chunk, overlap di 80 token.

### LLM Service (porta 8000)
Servizio Python che astrae i provider LLM.

- `POST /generate` — Generazione testo (supporta streaming SSE)
- `POST /embeddings` — Generazione embedding

**Provider supportati:**

| Provider | Generazione | Embedding |
|---|---|---|
| OpenAI | ✓ | ✓ |
| Anthropic | ✓ | — |
| Ollama | ✓ | ✓ |

## Quick Start

### Prerequisiti

- Docker e Docker Compose
- (Opzionale) Java 21 e Maven per sviluppo locale

### 1. Configurazione ambiente

Crea un file `.env` nella root del progetto:

```env
JWT_SECRET=<chiave-segreta-di-almeno-256-bit>
DB_USERNAME=postgres
DB_PASSWORD=<password-database>
RABBITMQ_DEFAULT_USER=guest
RABBITMQ_DEFAULT_PASS=<password-rabbitmq>
```

### 2. Avvio

```bash
docker compose up --build
```

Questo avvia tutti i servizi, inclusi PostgreSQL (con estensione pgvector) e RabbitMQ.

### 3. Verifica

- Eureka Dashboard: http://localhost:8761
- RabbitMQ Management: http://localhost:15672
- API Gateway: http://localhost:8080

## Database

Il progetto utilizza 3 database PostgreSQL, creati automaticamente da `init-databases.sql`:

| Database | Servizio | Descrizione |
|---|---|---|
| `users_db` | User Service | Utenti, ruoli, configurazioni LLM |
| `chat_db` | Chat Service | Sessioni e messaggi chat |
| `document_db` | Document Service | Documenti, chunk ed embedding (pgvector) |

## Struttura del progetto

```
ragapp/
├── docker-compose.yml
├── init-databases.sql
├── pom.xml                    # Parent POM
├── eureka-server/
├── api-gateway/
├── user-service/
├── chat-service/
├── document-service/
└── llm-service/
    └── app/
        ├── main.py
        ├── routers/
        ├── services/
        └── models/
```
