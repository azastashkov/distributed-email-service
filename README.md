# Distributed Email Service

A reference distributed email backend in Java 21 / Spring Boot 3.3, designed for reliability,
scalability, and high availability. Data is partitioned by `user_id` so each user's mail
lives on a single Cassandra shard. Two replicas of every app service sit behind nginx.

## Architecture

![Components](diagrams/components.drawio)

> The diagram is in **drawio XML format**. Open in [diagrams.net](https://app.diagrams.net) or the VS Code drawio extension to view/edit.

```
        Browser / Load Tester
                  |
   +--------------+--------------+
   |                             |
nginx-web (round-robin)   nginx-ws (ip_hash sticky)
   |       |                    |       |
 web-1   web-2            realtime-1  realtime-2
   |       |                    |       |
   |       |    +-- Kafka (email-events, partitioned by userId) --+
   |       |    |                                                  |
   |       |    +---> realtime consumer (push)                     |
   |       |    +---> search-indexer consumer (web-server-1 only)  |
   |       |                                                       |
   +-> Cassandra (3 nodes, RF=3)  Redis (cache + WS registry + pub/sub)
   +-> MinIO (presigned URLs)     OpenSearch (full-text)

Prometheus scrapes /actuator/prometheus on every app
Grafana reads Prometheus
```

## Tech Stack

- **Java 21** with virtual threads, **Spring Boot 3.3**, multi-module **Groovy Gradle**, **Lombok**
- **Cassandra 5.0** — 3-node cluster, RF=3 — query-driven schema partitioned by `user_id`
- **Redis 7** — recent-email cache, WebSocket session registry, cross-instance pub/sub
- **MinIO** — S3-compatible object store for attachments (presigned URLs)
- **OpenSearch 2.x** — full-text index over `subject + body`
- **Kafka (KRaft)** — single durable topic `email-events` (12 partitions, key=userId)
- **Spring Security + JWT (HS256)** — stateless auth
- **Spring WebSocket + STOMP** — real-time push, sticky-session via nginx `ip_hash`
- **Micrometer + Prometheus + Grafana** — observability
- **Testcontainers** — integration tests use real Cassandra/Kafka/Redis/MinIO/OpenSearch

## Module layout

```
common/             event records, DTOs, JWT util  (pure Java, no Spring beans)
web-server/         REST API + Kafka producer + OpenSearch indexer (profile-gated)
realtime-server/    STOMP/WebSocket + Kafka push consumer + Redis registry
load-client/        Spring Boot CLI driver, exposes Micrometer metrics
```

## Quick Start

### 1. Build everything

```bash
./gradlew clean build
```

### 2. Bring up the stack

```bash
docker compose up -d
```

Wait ~3 minutes for the **3-node Cassandra cluster** to come up (nodes bootstrap serially: ~90s for the first to become healthy, ~60s each for the next two).

```bash
docker compose ps   # all services should be 'healthy' or 'Up'
                    # init jobs (cassandra-init, kafka-init, minio-init) should be 'Exited (0)'
```

### 3. Smoke test

```bash
# Sign up
TOKEN=$(curl -s -X POST http://localhost:28080/api/v1/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@test.com","password":"password123","displayName":"Alice"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['token'])")

# List folders (4 system folders auto-created on signup)
curl -s http://localhost:28080/api/v1/folders -H "Authorization: Bearer $TOKEN" | python3 -m json.tool

# Send an email to self
EMAIL_ID=$(curl -s -X POST http://localhost:28080/api/v1/emails/draft \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"to":["alice@test.com"],"cc":[],"bcc":[],"subject":"Hi","body":"hello world","attachmentNames":[]}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['emailId'])")
curl -s -X POST "http://localhost:28080/api/v1/emails/$EMAIL_ID/send" -H "Authorization: Bearer $TOKEN"
```

### 4. Run the load test

```bash
docker compose --profile test up load-client
```

Open Grafana at <http://localhost:23000> (admin/admin or anonymous viewer) — the **"Email Service"** dashboard auto-loads with 12 panels covering RPS, latency, errors, JVM, WebSocket sessions, cross-instance forwards, and end-to-end delivery lag.

## REST API

| Method | Path | Notes |
|---|---|---|
| POST | `/api/v1/auth/signup` | `{email, password, displayName}` → `{userId, token, expiresAt}` |
| POST | `/api/v1/auth/login` | `{email, password}` → `{userId, token, expiresAt}` |
| GET  | `/api/v1/profile` | Bearer required |
| GET  | `/api/v1/folders` | List user's folders |
| POST | `/api/v1/folders` | `{name}` → custom folder |
| DEL  | `/api/v1/folders/{id}` | 409 if system folder |
| GET  | `/api/v1/folders/{id}/emails?limit=&before=` | paged by `received_at` |
| GET  | `/api/v1/emails?status=read|unread&limit=` | reads `emails_by_read_status` (last 3 months) |
| GET  | `/api/v1/emails/{id}` | full body, **Redis-cached** |
| POST | `/api/v1/emails/draft` | returns presigned PUT URLs per attachment |
| POST | `/api/v1/emails/{id}/send` | validates attachments, fans out to recipient INBOXes, publishes Kafka events |
| DEL  | `/api/v1/emails/{id}` | publishes `EMAIL_DELETED` |
| PATCH| `/api/v1/emails/{id}/read` | `{isRead}` — three-table dual-write |
| GET  | `/api/v1/search?q=&limit=&from=` | OpenSearch `multi_match` |
| GET  | `/actuator/health` | |
| GET  | `/actuator/prometheus` | |

All `/api/v1/*` except `/auth/*` require `Authorization: Bearer <jwt>`.

Attachment bytes never traverse the API — clients PUT/GET MinIO directly using presigned URLs.

## WebSocket API (STOMP)

Endpoint: `ws://localhost:28090/ws`

Authentication: `Authorization: Bearer <jwt>` header on the **STOMP CONNECT frame** (not the HTTP upgrade — browsers can't set those).

Server-pushed destinations:

| Destination | Payload |
|---|---|
| `/user/queue/email.new` | `{eventType, userId, emailId, folderId, subject, fromAddr, preview, receivedAt, occurredAt}` |
| `/user/queue/email.read` | `{eventType, userId, emailId, isRead, occurredAt}` |
| `/user/queue/email.deleted` | `{eventType, userId, emailId, occurredAt}` |

Cross-instance delivery: the realtime instance owning the Kafka partition for `userId` pushes locally if the user is connected; otherwise it forwards via `PUBLISH ws:instance:<otherId>` to the right instance.

## Cassandra schema

Schema is at `docker/cassandra/init.cql`. Tables:

- `users_by_email`, `users_by_id` — auth
- `folders_by_user (PK = user_id)` — answers Q1: list folders
- `emails_by_folder (PK = (user_id, folder_id), CK = (received_at DESC, email_id))` — answers Q2
- `emails_by_id (PK = user_id, CK = email_id)` — answers Q3 (get/delete)
- `emails_by_read_status (PK = (user_id, is_read, received_month))` — answers Q4 (read/unread)

The `received_month` bucket in the partition key bounds partition size and avoids a hot partition for unread mail. Mark-as-read does an app-level dual-write across partitions (delete from old, insert into new) — eventual consistency on the read flag is acceptable.

## Kafka

- Topic: `email-events` — 12 partitions, key = `userId`
- Producers: `web-server` (idempotent, acks=all)
- Consumers:
  - group `realtime` → `realtime-server` (push to WS)
  - group `search-indexer` → `web-server-1` (one instance only, profile-gated)

## Monitoring

- Prometheus: <http://localhost:29090>
- Grafana: <http://localhost:23000>
- Dashboard "Email Service" auto-provisioned

Key panels:
- Web RPS, latency p95, 5xx rate per endpoint and per instance (proves nginx LB balance)
- JVM heap
- WS active sessions per instance
- WS messages sent / cross-instance forwards
- Load client RPS, latency, error count
- WS delivery lag p95 (end-to-end, from publish time to STOMP receive)

## Host port map

(All ports moved to a high range to avoid conflicts with other docker-compose projects on this machine.)

| Service | Host port | In-cluster |
|---|---|---|
| nginx-web (REST LB) | 28080 | 80 |
| web-server-1 direct | 28081 | 8080 |
| web-server-2 direct | 28082 | 8080 |
| nginx-ws (WS LB) | 28090 | 80 |
| realtime-server-1 direct | 28181 | 8080 |
| realtime-server-2 direct | 28182 | 8080 |
| Grafana | 23000 | 3000 |
| Prometheus | 29090 | 9090 |
| Redis | 26379 | 6379 |
| MinIO API | 29000 | 9000 |
| MinIO console | 29001 | 9001 |
| OpenSearch | 29200 | 9200 |
| Kafka | 29092 | 9092 |
| load-client metrics | 29100 | 9100 |

## Troubleshooting

- **Cassandra slow startup:** the 3-node cluster bootstraps serially. The first node takes ~90s; the second ~60s; the third ~60s. The `cassandra-init` schema applier waits for all three to be healthy before running.
- **realtime-server actuator returning 401:** the realtime app uses Spring Security but its config permits all HTTP requests (auth happens on the STOMP CONNECT frame). If you ever see 401, restart the realtime container.
- **Kafka topic not created:** re-run `docker compose run --rm kafka-init`.
- **MinIO bucket missing:** re-run `docker compose run --rm minio-init`.
- **WS delivery lag spikes:** check `ws_cross_instance_forwards_total` — high values mean nginx isn't being sticky enough; check `ip_hash` config and that `nginx-ws` is in front of WS connections.
- **Search returns nothing right after sending:** the indexer is async via Kafka — wait 1–2 seconds (indexed with `refresh=true` so visible immediately after the consumer processes).

## Known limitations

- **No transactional outbox** — small failure window between Cassandra write and Kafka publish. Acceptable for a demo.
- **No SMTP transport** — recipients must exist in `users_by_email`. External addresses are silently dropped.
- **Single-DC Cassandra** — meets the "replicate across nodes" requirement; multi-DC out of scope.
- **Single-node OpenSearch** — set up for dev convenience.
- **HS256 JWT with shared secret** — appropriate for a self-contained docker-compose project; production would use RS256 + JWKS.
- **OpenSearch indexer pinned to web-server-1** — single-instance for ordering simplicity; could be split into its own service for HA.
- **Free email accounts** — anyone can sign up. No rate limiting.

## Tests

- Unit tests: `./gradlew test` (all modules)
- Run only one module's tests: `./gradlew :web-server:test`
- End-to-end smoke: `./scripts/smoke-test.sh` (requires running stack)
- Live metrics report from Prometheus: `./scripts/load-test-report.sh`

Most unit tests are pure Java with Jackson/JUnit/AssertJ; integration coverage for the
storage and event layers is exercised end-to-end by the load test, which runs against
the same Docker stack.

## Reference load-test result

A 5-minute, 50-virtual-user, 7-operation-mix load test against the full stack
on a developer laptop (M-series Mac, Docker Desktop) produced:

| Metric | Value |
|---|---|
| Total operations | **27,915** (~93 ops/sec sustained) |
| Server-side 5xx errors | **0** |
| Client transient errors | 41 (0.15%, all from a mid-test nginx restart) |
| Per-op p95 latency | getEmail 9.3ms · listFolder 6.2ms · listUnread 3.5ms · markRead 16.3ms · search 6.4ms · sendNoAtt 27.7ms · sendWithAtt 32.9ms |
| Web RPS, web-server-1 vs -2 | balanced within 5% (after fresh nginx) |
| WS messages pushed | **14,328** |
| WS cross-instance forwards | **10,646** (proves Redis-backed forward path) |
| WS end-to-end delivery lag | **p50 1.2ms · p95 2.3ms · p99 3.1ms** |
| Cassandra ring | all 3 nodes UN, 100% effective ownership each, 10MB+ per node (replicated) |

## License

For internal reference / educational use.
