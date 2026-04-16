# Q-Comm Backbone

Intelligent quick-commerce network optimization platform that computes minimum-cost restock connectivity (MST) across dark-store belts, applies AI-assisted risk weighting, and produces a warehouse-to-store dispatch plan on an interactive command center map.

## What This Project Does

Q-Comm Backbone solves a core logistics problem:

- You have a set of dark stores (nodes) connected by roads/corridors (edges).
- You want to connect all stores at minimum total cost.
- Real-world disruptions (rain, congestion, roadwork) and live traffic can change edge costs.
- You also need a practical dispatch sequence starting from an operation warehouse.

This project combines:

- **DAA engine**: Kruskal + Prim MST implementations
- **AI risk engine**: event-aware edge weight adjustment with reason generation
- **Traffic signal integration**: Google Distance Matrix travel-time estimates
- **Enterprise backend**: Spring Boot + PostgreSQL + JWT security
- **Cinematic frontend**: Next.js dashboard with interactive map, compare mode, telemetry, and dispatch modal

## Core Features

- Multiple city belts with seeded data:
  - `mumbai_borivali_andheri`
  - `navi_mumbai_vashi_belapur`
  - `pune_hinjewadi_baner_kothrud`
  - `bengaluru_orr_whitefield`
- Algorithm execution:
  - Prim's MST
  - Kruskal's MST (Union-Find with path compression + union by rank)
- Compare mode:
  - Runs both algorithms on the same prepared graph
  - Recommends one based on cost and execution time
- Dispatch routing:
  - Starts from warehouse (default belt warehouse or custom coordinates)
  - Generates step-by-step traversal with cumulative cost
- AI risk tagging:
  - Risk is only tagged when increase is **material** and reason exists
  - Returns human-readable reason per impacted leg
- Security:
  - JWT login endpoint
  - Protected network/admin APIs

## High-Level Architecture

```
Next.js Dashboard (Frontend)
  `-- Axios API client
      `-- Spring Boot REST APIs (Backend)
          |-- Auth + JWT filter
          |-- NetworkOptimizationService
          |   |-- Load stores/edges from PostgreSQL
          |   |-- TrafficSignalService (Google Maps)
          |   |-- SLMOrchestrator (Ollama/TinyLlama risk adjustments)
          |   |-- Build Graph
          |   |-- Run Prim/Kruskal
          |   |-- Build dispatch plan from warehouse
          |   `-- Persist routing history
          `-- Admin seed/reset operations
```

## Tech Stack

- **Backend**: Java 25, Spring Boot 3.5.x, Spring Data JPA, Spring AI, PostgreSQL, JWT
- **Frontend**: Next.js 16 (App Router), TypeScript, Tailwind CSS, Framer Motion, Google Maps JS API
- **Database**: PostgreSQL (Supabase-compatible configuration)
- **AI Runtime**: Ollama (default model: TinyLlama)

## Repository Layout

```
q-comm backbone/
|-- backend-springboot/
|   |-- src/main/java/com/qcomm/engine/
|   |   |-- ai/                  # LLM + traffic services
|   |   |-- auth/                # login service/controller
|   |   |-- config/              # CORS, belts, seeder
|   |   |-- controller/          # network/admin APIs
|   |   |-- daa/                 # graph + Prim/Kruskal implementations
|   |   |-- dto/                 # response/request records
|   |   |-- model/               # JPA entities
|   |   |-- repository/          # Spring Data repositories
|   |   |-- security/            # JWT security filter/config
|   |   `-- service/             # orchestration logic
|   `-- src/test/                # strict algorithm + integration tests
`-- frontend-nextjs/
    |-- app/                     # pages/layout
    |-- components/              # map, metrics, modals
    |-- lib/api.ts               # axios client + API bindings
    `-- types/                   # TypeScript DTO contracts
```

## Prerequisites

- **Java 25**
- **Node.js 20+** and npm
- **PostgreSQL** database (or Supabase Postgres URL)
- **Ollama** (optional but recommended for LLM-driven risk analysis)
- **Google Maps API key** with Distance Matrix and Maps JavaScript enabled

## Environment Configuration

### 1) Backend env (`backend-springboot/.env`)

Create `backend-springboot/.env` with:

```env
DB_URL=jdbc:postgresql://<host>:<port>/<db>
DB_USERNAME=<db_user>
DB_PASSWORD=<db_password>

OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_MODEL=tinyllama

PORT=8080
GOOGLE_MAPS_API_KEY=<your_google_maps_api_key>

APP_AUTH_USERNAME=admin
APP_AUTH_PASSWORD=admin123
APP_AUTH_ROLE=ADMIN

JWT_SECRET=<min_32_char_secret>
JWT_EXPIRATION_SECONDS=36000
```

### 2) Frontend env (`frontend-nextjs/.env.local`)

```env
NEXT_PUBLIC_GOOGLE_MAPS_API_KEY=<your_google_maps_api_key>
```

Important:

- Keep real keys and passwords in `.env` / `.env.local` only.
- Do not commit secrets.

## Running the Project

Use two terminals from the project root.

### Terminal A: Backend

```powershell
cd backend-springboot
.\mvnw.cmd spring-boot:run
```

Backend starts at: `http://localhost:8080`

### Terminal B: Frontend

```powershell
cd frontend-nextjs
npm install
npm run dev
```

Frontend starts at: `http://localhost:3000`

## Authentication Flow

1. Login from UI (or API):
   - default: `admin / admin123` (override via env)
2. Backend returns JWT token.
3. Frontend stores token in localStorage (`qcomm_auth_token`).
4. Protected routes include bearer token automatically.

## API Overview

Base URL: `http://localhost:8080/api/v1`

### Auth

- `POST /auth/login`

### Network (JWT required)

- `GET /network/stores?belt=<beltCode>`
- `GET /network/edges?belt=<beltCode>`
- `POST /network/optimize`
- `POST /network/compare`
- `GET /network/history?limit=20`
- `GET /network/history/summary`
- `GET /network/history/export.csv?limit=100`

### Admin (JWT + ADMIN role)

- `GET /admin/belts`
- `POST /admin/seed/{beltCode}`
- `POST /admin/reset-and-seed/{beltCode}`
- `POST /admin/seed-all`
- `POST /admin/reset-and-seed-all`
- `GET /admin/alerts/status`

## Sample API Usage

### Login

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

### Optimize (replace `<TOKEN>`)

```bash
curl -X POST http://localhost:8080/api/v1/network/optimize \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "algorithm":"prim",
    "event":"Heavy rain and waterlogging affecting arterial roads.",
    "beltCode":"mumbai_borivali_andheri"
  }'
```

## DAA Notes

- **Kruskal**: sorts edges then unions components.
- **Prim**: grows MST using min-priority queue.
- Both are valid MST algorithms and typically produce the same minimum total cost for a connected weighted graph.
- Edge set may differ when multiple optimal MSTs exist.
- Compare mode helps inspect overlap and execution deltas.

## Risk Assessment Logic (Current)

- Base and live traffic are used to derive baseline operational weight.
- For event scenarios, AI proposes adjusted weight with timeout + heuristic fallback.
- Risk is tagged only when:
  - uplift is material (`>= max(2 minutes, 10% of baseline)`), and
  - reason text is available.
- Each risk-tagged leg/edge carries `riskReason`, shown in UI.

## Testing & Quality Checks

### Backend tests

```powershell
cd backend-springboot
.\mvnw.cmd -q test
```

Includes strict algorithm validation and service-level dispatch tests.

### Frontend checks

```powershell
cd frontend-nextjs
npm run lint
npm run build
```

## Troubleshooting

- **`Driver org.postgresql.Driver claims to not accept jdbcUrl, ${DB_URL}`**
  - `DB_URL` is missing/not loaded in backend `.env`.
- **Frontend map says key missing**
  - Add `NEXT_PUBLIC_GOOGLE_MAPS_API_KEY` to `frontend-nextjs/.env.local`.
  - Restart `npm run dev`.
- **401 Unauthorized**
  - Login again; token may be expired.
- **Ollama unavailable**
  - Risk weighting falls back to heuristic behavior.

## Security Notes

- Rotate JWT secrets and API keys before production use.
- Restrict Google Maps key by referrer/IP in Google Cloud Console.
- Do not commit `.env` or `.env.local`.

## Current Status

This project is set up as an end-to-end DAA + AI logistics command center with:

- secure backend APIs,
- seeded multi-belt datasets,
- dynamic risk-aware MST optimization,
- dispatch visualization from warehouse origin,
- and comparison/analytics workflows for project evaluation.
