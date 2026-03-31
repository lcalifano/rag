# RAG App — Microservices

Applicazione **RAG** (Retrieval-Augmented Generation) basata su architettura a microservizi.
Permette agli utenti di caricare documenti, generare embedding vettoriali e chattare con un LLM
che utilizza il contenuto dei documenti come contesto. Le risposte arrivano in tempo reale tramite WebSocket.

---

## Architettura

```
Browser
  │
  ▼
Nginx (porta 3000 / 80)        ← SPA React + proxy HTTP/WebSocket verso Gateway
  │
  ├── /api/*     → HTTP proxy
  ├── /api/ws/*  → WebSocket proxy (Upgrade + Connection)
  │
  ▼
API Gateway (porta 8080)        ← JWT auth, Redis blacklist, routing
  │
  ├──▶ User Service (8081)      ← Auth, OAuth2, refresh token, config LLM
  ├──▶ Chat Service (8082)      ← Sessioni chat, RAG, WebSocket
  └──▶ Document Service (8083)  ← Upload, chunking, embedding
              │
              ▼
        LLM Service (8000)      ← FastAPI, generazione testo ed embedding (Ollama)

Tutti i servizi si registrano su Eureka Server (8761)
```

---

## Tech Stack

| Componente | Tecnologia |
|---|---|
| Java Services | Spring Boot 3.4.5, Java 21 |
| Service Discovery | Spring Cloud Netflix Eureka |
| API Gateway | Spring Cloud Gateway (WebFlux) |
| Auth | Spring Security + JWT + OAuth2 (Google, GitHub) |
| Token Management | Refresh Token Rotation + Redis Blacklist |
| Database | PostgreSQL 17 + pgvector |
| Real-time | WebSocket (Spring WebSocket) |
| LLM Service | Python 3.12, FastAPI, LangChain |
| LLM Runtime | Ollama (default: `qwen2.5:3b`, embedding: `nomic-embed-text`) |
| Frontend | React 18, TypeScript, Vite, Tailwind CSS v4 |
| Infrastruttura | Docker Compose, Nginx, Redis 7 |

---

## Servizi

### Eureka Server — porta 8761
Service registry. Tutti i microservizi si registrano al boot e il gateway risolve i nomi via load balancing.

### API Gateway — porta 8080
Unico punto di ingresso. Valida JWT o ticket monouso (Redis), controlla la blacklist, inietta header `X-User-*` e instrada verso il servizio corretto.

| Route | Servizio | Protocollo |
|---|---|---|
| `/auth/**`, `/users/**`, `/model/**`, `/admin/**` | User Service | HTTP |
| `/oauth2/**`, `/login/oauth2/**` | User Service | HTTP |
| `/ws/chat/**` | Chat Service | WebSocket |
| `/chat/**` | Chat Service | HTTP |
| `/documents/**` | Document Service | HTTP |

### User Service — porta 8081
Gestione utenti, autenticazione (locale + OAuth2 Google/GitHub) e configurazione LLM per utente.

- `POST /auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout`
- `GET /auth/sse-ticket` — Ticket monouso (30s) per autenticazione WebSocket
- `GET/PUT /users/me` — Profilo utente
- `GET/PUT /admin/users` — Gestione utenti (admin)
- `GET/POST /model/settings` — Configurazione provider LLM

### Chat Service — porta 8082
Sessioni chat con pipeline RAG e comunicazione bidirezionale via WebSocket.

Ad ogni messaggio: genera embedding della domanda → recupera chunk simili (pgvector) → costruisce prompt con contesto → chiama LLM async → invia risposta via WebSocket.

- `POST /chat/sessions` — Crea sessione
- `GET /chat/sessions` — Lista sessioni
- `GET /chat/sessions/{id}/messages` — Storico messaggi
- `PUT /chat/sessions/{id}` — Aggiorna titolo
- `ws:///ws/chat/sessions/{id}?ticket=<uuid>` — WebSocket bidirezionale

### Document Service — porta 8083
Upload, parsing PDF, chunking e generazione embedding (elaborazione asincrona).

- `POST /documents/upload` — Upload (max 50MB)
- `GET /documents/` — Lista documenti utente
- `DELETE /documents/{id}` — Elimina documento + chunk + embedding
- `GET /documents/admin/all` — Tutti i documenti (admin)

### LLM Service — porta 8000
Servizio Python che astrae i provider LLM (Ollama).

- `POST /generate` — Generazione testo
- `POST /embeddings` — Generazione embedding vettoriale

---

## Quick Start

### Prerequisiti
- Docker e Docker Compose

### 1. Configurazione `.env`

```env
JWT_SECRET=<stringa-casuale-di-almeno-64-caratteri>
DB_USERNAME=postgres
DB_PASSWORD=<password-a-scelta>
```

### 2. Avvio

```bash
docker compose up --build
```

### 3. Primo accesso

Il primo utente che si registra riceve automaticamente il ruolo ADMIN.

Vai su `http://localhost:3000` e registra il tuo account.

---

## Database

Tre database PostgreSQL separati, creati da `init-databases.sql`:

| Database | Servizio | Contenuto |
|---|---|---|
| `users_db` | User Service | utenti, ruoli, refresh_tokens, config LLM |
| `chat_db` | Chat Service | sessioni chat, messaggi |
| `document_db` | Document Service | documenti, chunk, embedding (pgvector) |

---

## Struttura del progetto

```
ragapp/
├── docker-compose.yml
├── init-databases.sql
├── eureka-server/
├── api-gateway/                 # Spring Cloud Gateway + JWT filter + Redis
├── user-service/                # Auth, OAuth2, refresh token
├── chat-service/                # RAG, WebSocket, async LLM
│   └── src/.../
│       ├── controllers/         # REST endpoints
│       ├── websocket/           # ChatWebSocketHandler + AuthInterceptor
│       ├── services/            # ChatService, AsyncLlmService, WebSocketSessionService
│       └── config/              # WebSocketConfig, SecurityConfig, AsyncConfig
├── document-service/            # Upload, chunking, pgvector
├── llm-service/                 # Python FastAPI + LangChain + Ollama
└── frontend/                    # React + TypeScript + Vite + Tailwind
    └── nginx.conf               # SPA serving + proxy HTTP/WebSocket
```
