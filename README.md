# RAG App — Microservices

Applicazione **RAG** (Retrieval-Augmented Generation) basata su architettura a microservizi.
Permette agli utenti di caricare documenti, generare embedding vettoriali e chattare con un LLM
che utilizza il contenuto dei documenti come contesto. Le risposte arrivano in streaming tramite SSE.

---

## Architettura

```
Browser
  │
  ▼
Nginx (porta 3000 / 80)        ← SPA React + proxy verso Gateway
  │
  ▼
API Gateway (porta 8080)        ← JWT auth, Redis blacklist, routing
  │
  ├──▶ User Service (8081)      ← Auth, OAuth2, refresh token, config LLM
  ├──▶ Chat Service (8082)      ← Sessioni chat, RAG, SSE
  └──▶ Document Service (8083)  ← Upload, chunking, embedding
              │
              ▼
        LLM Service (8000)      ← FastAPI, generazione testo ed embedding (Ollama)

Tutti i servizi si registrano su Eureka Server (8761)
Upload asincrono tramite RabbitMQ
Short-lived JWT + Refresh Token + Redis token blacklist
```

---

## Tech Stack

| Componente | Tecnologia |
|---|---|
| Java Services | Spring Boot 3.4.5, Java 21 |
| Service Discovery | Spring Cloud Netflix Eureka |
| API Gateway | Spring Cloud Gateway (WebFlux) |
| Auth | Spring Security + JWT (JJWT 0.12) |
| OAuth2 | Spring OAuth2 Client (Google, GitHub) |
| Token Management | Refresh Token Rotation + Redis Blacklist |
| Cache / Session | Redis 7 |
| Database | PostgreSQL 17 + pgvector |
| Message Broker | RabbitMQ 3 |
| LLM Service | Python 3.12, FastAPI, LangChain |
| Frontend | React 18, TypeScript, Vite, Tailwind CSS v4 |
| Container | Docker + Docker Compose |
| Proxy | Nginx (SPA serving + API proxy) |

---

## Servizi

### Eureka Server — porta 8761
Service registry. Tutti i microservizi si registrano al boot con il proprio nome logico
(`USER-SERVICE`, `CHAT-SERVICE`, ecc.). Il gateway risolve i nomi via load balancing.

### API Gateway — porta 8080
Unico punto di ingresso. Ad ogni richiesta:
1. Controlla se il path è pubblico (login, register, OAuth2)
2. Valida il JWT (firma + scadenza)
3. Controlla la blacklist Redis sul `jti` del token
4. Oppure valida un SSE ticket monouso da Redis
5. Inietta header `X-User-Id`, `X-User-Username`, `X-User-Roles`
6. Instrada verso il servizio corretto

| Route | Servizio |
|---|---|
| `/auth/**` | User Service |
| `/users/**` | User Service |
| `/model/**` | User Service |
| `/admin/**` | User Service |
| `/oauth2/authorization/**` | User Service |
| `/login/oauth2/code/**` | User Service |
| `/chat/**` | Chat Service |
| `/documents/**` | Document Service |

### User Service — porta 8081
Gestione utenti, autenticazione e configurazione LLM.

**Auth endpoints (pubblici):**
- `POST /auth/register` — Registrazione
- `POST /auth/login` — Login → `{ token (15min), refreshToken (7gg) }`
- `POST /auth/refresh` — Rinnova access token tramite refresh token (rotation)
- `GET /oauth2/authorization/google` — Avvia OAuth2 Google
- `GET /oauth2/authorization/github` — Avvia OAuth2 GitHub

**Auth endpoints (autenticati):**
- `POST /auth/logout` — Invalida token (blacklist Redis) + revoca refresh token
- `GET /auth/sse-ticket` — Genera ticket monouso 30s per aprire SSE

**Altri:**
- `GET/PUT /users/me` — Profilo utente
- `GET/PUT /admin/users` — Gestione utenti (admin)
- `GET/POST /model/settings` — Configurazione provider LLM

### Chat Service — porta 8082
Sessioni chat con pipeline RAG. Ad ogni messaggio:
1. Genera l'embedding della domanda (LLM Service)
2. Recupera i 5 chunk più simili dai documenti dell'utente (pgvector cosine similarity)
3. Costruisce il prompt con il contesto trovato
4. Chiama il LLM in modo asincrono (`@Async`)
5. Pubblica la risposta tramite SSE all'EventSource del frontend

- `POST /chat/sessions` — Crea sessione
- `GET /chat/sessions` — Lista sessioni
- `POST /chat/sessions/{id}/messages` — Invia messaggio (risposta via SSE)
- `GET /chat/sessions/{id}/messages` — Storico
- `GET /chat/sessions/{id}/stream` — Stream SSE (autenticato con `?ticket=`)

### Document Service — porta 8083
Upload, parsing PDF, chunking e generazione embedding.

- `POST /documents/upload` — Upload (max 50MB, elaborazione asincrona via RabbitMQ)
- `GET /documents/` — Lista documenti utente
- `DELETE /documents/{id}` — Elimina documento + chunk + embedding
- `GET /documents/admin/all` — Tutti i documenti (admin)

**Pipeline upload:**
1. Salva il file su disco
2. Pubblica messaggio su RabbitMQ
3. Consumer estrae testo (PDFBox), divide in chunk (400 token, overlap 80)
4. Per ogni chunk genera embedding tramite LLM Service
5. Salva chunk + vettore in PostgreSQL (pgvector)

### LLM Service — porta 8000
Servizio Python che astrae i provider LLM.

- `POST /generate` — Generazione testo
- `POST /embeddings` — Generazione embedding vettoriale

**Provider supportati:**

| Provider | Generazione | Embedding |
|---|---|---|
| Ollama (locale) | ✓ | ✓ |
| OpenAI | ✓ | ✓ |
| Anthropic | ✓ | — |

Modello di default per CPU: `qwen2.5:3b` (embedding: `nomic-embed-text`).

---

## Sicurezza

### Flusso JWT completo

```
Login  →  access token (15 min, contiene jti UUID)
       +  refresh token (7 giorni, salvato in DB)

Chiamata normale  →  Authorization: Bearer <access_token>
                 →  Gateway: verifica firma + controlla Redis blacklist su jti

Token scaduto  →  Axios interceptor cattura 401
               →  POST /auth/refresh con refresh token
               →  nuovo access token + nuovo refresh token (rotation)
               →  riprova la richiesta originale

Logout  →  POST /auth/logout
        →  jti → Redis blacklist (TTL = vita residua del token)
        →  tutti i refresh token → revoked = true nel DB

SSE  →  GET /auth/sse-ticket  (Bearer token)
     →  ticket UUID salvato in Redis, TTL 30s
     →  EventSource apre con ?ticket=<uuid>
     →  Gateway: getAndDelete atomico → valida → apre stream
```

### OAuth2 Login

I bottoni "Accedi con Google/GitHub" sulla pagina di login avviano l'Authorization Code Flow
completo tramite il gateway. Dopo il consenso, il user-service:
- trova o crea l'utente locale (collega l'account se l'email coincide con un utente esistente)
- genera access token + refresh token
- reindirizza su `/oauth2/callback?token=...&refreshToken=...&username=...`

Redirect URI da registrare nelle console dei provider:
```
http://localhost/login/oauth2/code/google
http://localhost/login/oauth2/code/github
```

---

## Quick Start

### Prerequisiti
- Docker e Docker Compose
- Account Google e/o GitHub (opzionale, per OAuth2)

### 1. Configurazione `.env`

```env
JWT_SECRET=<stringa-casuale-di-almeno-64-caratteri>
DB_USERNAME=postgres
DB_PASSWORD=<password-a-scelta>
RABBITMQ_DEFAULT_USER=guest
RABBITMQ_DEFAULT_PASS=<password-a-scelta>

# Opzionale — OAuth2 (lascia vuoto se non usi il login sociale)
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
GITHUB_CLIENT_ID=
GITHUB_CLIENT_SECRET=

# Opzionale — URL frontend in produzione
FRONTEND_URL=http://localhost
```

### 2. Avvio

```bash
docker compose up --build
```

### 3. Primo accesso

Il primo utente che si registra riceve automaticamente il ruolo ADMIN.

Vai su `http://localhost:3000` e registra il tuo account.

### 4. Verifica servizi

| URL | Servizio |
|---|---|
| http://localhost:3000 | Frontend React |
| http://localhost:8761 | Eureka Dashboard |
| http://localhost:15672 | RabbitMQ Management |
| http://localhost:6379 | Redis (no UI, usa redis-cli) |

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
├── pom.xml                      # Parent POM (aggregator per IDE)
├── ROADMAP.md                   # Catalogo di tutti i concetti implementati
├── security-fixes.md            # Log delle fix di sicurezza
├── eureka-server/
├── api-gateway/                 # Spring Cloud Gateway + JWT filter + Redis
├── user-service/                # Auth, OAuth2, refresh token
├── chat-service/                # RAG, SSE, async LLM
├── document-service/            # Upload, chunking, pgvector
├── llm-service/                 # Python FastAPI
│   └── app/
│       ├── main.py
│       ├── routers/
│       └── services/
└── frontend/                    # React + TypeScript + Vite + Tailwind
    ├── src/
    │   ├── pages/
    │   ├── components/
    │   ├── contexts/
    │   └── services/
    └── nginx.conf               # Serve SPA + proxy verso Gateway
```
