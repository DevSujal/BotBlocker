# BotBlocker — Backend Engineering Assignment

A high-performance Spring Boot microservice acting as a central API gateway and guardrail system. It handles concurrent bot interactions, manages distributed state via Redis, and implements smart notification batching with event-driven scheduling.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.x |
| Database | PostgreSQL 15 |
| Cache / State | Redis 7 |
| ORM | Spring Data JPA / Hibernate |
| Build | Maven |
| Infrastructure | Docker Compose |

---

## Quick Start

### 1. Start Infrastructure

```bash
docker-compose up -d
```

This spins up PostgreSQL on port `5432` and Redis on port `6379`.

### 2. Run the Application

```bash
./mvnw spring-boot:run
```

The service starts on `http://localhost:8080`. Hibernate auto-creates all tables on first boot.

### 3. Import Postman Collection

Import `BotBlocker.postman_collection.json` into Postman. Set the `base_url` variable to `http://localhost:8080`.

---

## API Reference

### Users

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/users` | Create a new user |
| GET | `/api/users/{id}` | Get user by ID |

### Bots

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/bots` | Register a new bot |
| GET | `/api/bots/{id}` | Get bot by ID |

### Posts

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/posts` | Create a new post |
| POST | `/api/posts/{postId}/comments` | Add a comment (subject to all guardrails if BOT) |
| POST | `/api/posts/{postId}/like?authorType=USER` | Like a post |
| GET | `/api/posts/{postId}/virality` | Get live virality score and bot reply count from Redis |

---

## Phase 1 — Database Schema

Four JPA entities mapped to PostgreSQL:

- **User** — `id`, `username`, `is_premium`
- **Bot** — `id`, `name`, `persona_description`
- **Post** — `id`, `author_id`, `author_type` (USER/BOT), `content`, `created_at`
- **Comment** — `id`, `post_id`, `author_id`, `author_type`, `content`, `depth_level`, `created_at`

---

## Phase 2 — Redis Virality Engine & Atomic Locks

### Virality Score

Every interaction with a post updates `post:{id}:virality_score` in Redis:

| Interaction | Points |
|---|---|
| Bot reply | +1 |
| Human like | +20 |
| Human comment | +50 |

### The Three Guardrails (`BotGuardrailService`)

Before a bot comment is saved to Postgres, all three checks run in order:

#### 1. Vertical Cap — Max thread depth: 20

The `parentCommentId` field in the comment request tells the API which comment is being replied to. The service fetches the parent comment's `depth_level` from Postgres and adds 1. If the result exceeds 20, the request is rejected with `400 Bad Request`.

#### 2. Horizontal Cap — Max 100 bot replies per post

**Key:** `post:{id}:bot_count`

This is the most critical guardrail from a concurrency standpoint. A naive approach (INCR → check → DECR on failure) is **not safe** under concurrent load. Under 200 simultaneous requests, all threads can pass the check before any rollback completes, resulting in 101+ comments.

**Our solution: A Redis Lua script.**

```lua
local current = redis.call('INCR', KEYS[1])
if current > tonumber(ARGV[1]) then
    redis.call('DECR', KEYS[1])
    return -1
end
return current
```

Redis executes Lua scripts **atomically** — the server processes the entire script in a single CPU tick before serving any other command. This makes the increment-and-check an indivisible operation. Under 200 concurrent requests, exactly 100 will receive an approved slot and 100 will receive `-1` and be rejected with `429 Too Many Requests`.

#### 3. Cooldown Cap — 1 interaction per bot-human pair per 10 minutes

**Key:** `cooldown:bot_{botId}:human_{userId}`

Uses Redis `SET NX EX` via Spring's `setIfAbsent(key, value, 10, MINUTES)`. This is a single atomic Redis command — if the key already exists (bot already interacted within 10 minutes), it returns `false` and the request is rejected with `429`. The key auto-expires after 10 minutes, resetting the cooldown automatically with no cleanup needed.

---

## Phase 3 — Notification Engine (Smart Batching)

Implemented in `NotificationService` and `NotificationSweeper`.

### Redis Throttler (`NotificationService`)

When a bot comments on a user's post:

1. Check for key `notif_cooldown:user_{userId}` in Redis.
2. **If the key does NOT exist** (first notification in 15 minutes): Log `"Push Notification Sent to User X"` to console, and set the cooldown key with a 15-minute TTL.
3. **If the key EXISTS** (within 15-minute window): Push the notification message string into the Redis List `user:{userId}:pending_notifs`.

### CRON Sweeper (`NotificationSweeper`)

A `@Scheduled(fixedRate = 300000)` task (every 5 minutes) that:

1. Scans Redis for all keys matching `user:*:pending_notifs`.
2. For each key found, pops all messages from the list atomically and deletes the list.
3. Logs a summarized notification: `"Bot X and [N] others interacted with your posts."`

---

## Phase 4 — Concurrency, Statelessness & Data Integrity

### Race Condition Test (200 Concurrent Bots)

The Lua script in `BotGuardrailService.enforceHorizontalCap()` guarantees exactly 100 approvals regardless of concurrency. Redis's single-threaded execution model means the script is never interleaved with other commands. The counter will stop at exactly 100 — no 101st comment can reach the database.

### Statelessness

The application holds **zero in-memory state**. All counters, cooldowns, pending notifications, and virality scores are stored exclusively in Redis:

| Data | Redis Key Pattern |
|---|---|
| Virality score | `post:{id}:virality_score` |
| Bot reply count | `post:{id}:bot_count` |
| Bot-human cooldown | `cooldown:bot_{id}:human_{id}` |
| Notification cooldown | `notif_cooldown:user_{id}` |
| Pending notifications | `user:{id}:pending_notifs` |

No `HashMap`, `static` variables, or `@Scope("singleton")` mutable fields exist in the codebase.

### Data Integrity

Redis acts as the **gatekeeper** — all guardrail checks run before the `@Transactional` boundary commits the comment to Postgres. If a guardrail throws an exception, the Spring transaction rolls back and no row is inserted. Postgres only ever receives data that has successfully passed all Redis checks.

---

## Project Structure

```
src/main/java/com/assignment/backendengineering/
├── BackendengineeringApplication.java   # Entry point, @EnableScheduling
├── controller/
│   ├── PostController.java              # Posts, comments, likes, virality endpoint
│   ├── UserController.java              # User CRUD
│   └── BotController.java              # Bot CRUD
├── service/
│   ├── PostService.java                 # Core business logic, orchestration
│   ├── BotGuardrailService.java         # All three Redis atomic guardrails (Phase 2)
│   └── NotificationService.java         # Notification throttler (Phase 3)
├── scheduler/
│   └── NotificationSweeper.java         # CRON sweeper, runs every 5 minutes (Phase 3)
├── entity/
│   ├── User.java
│   ├── Bot.java
│   ├── Post.java
│   └── Comment.java
├── repository/
│   ├── UserRepository.java
│   ├── BotRepository.java
│   ├── PostRepository.java
│   └── CommentRepository.java
├── dto/
│   ├── requestDTO/
│   │   ├── PostRequestDTO.java
│   │   └── CommentRequestDTO.java       # Includes parentCommentId for depth tracking
│   └── responseDTO/
│       ├── PostResponseDTO.java
│       └── CommentResponseDTO.java
├── exception/
│   └── GlobalExceptionHandler.java      # Handles ResponseStatusException properly
└── utils/
    └── AuthorType.java                  # Enum: USER, BOT
```

---

## How Thread Safety is Guaranteed

The horizontal cap (the most race-condition-prone guardrail) uses a **Redis Lua script** executed via `redisTemplate.execute(script, keys, args)`.

Redis is single-threaded for command execution. When a Lua script is submitted, Redis completes the entire script atomically — no other client can issue commands between the `INCR` and the conditional `DECR`. This eliminates the TOCTOU (Time-Of-Check-Time-Of-Use) race condition that exists in any solution that uses separate INCR and GET/check commands.

The cooldown cap uses `SET NX EX` which is also a single atomic Redis command. `setIfAbsent` maps directly to this command — there is no window between the existence check and the key creation.
