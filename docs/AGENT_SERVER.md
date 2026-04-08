# Agent Server (Reference)

This is a summary reference for Hub developers. The canonical documentation lives in the Microbot client repo at `docs/AGENT_SERVER.md`.

## Overview

The Agent Server is an embedded HTTP server (port 8081, localhost only) in the Microbot client that exposes game state and interactions to external tools. The Hub uses it to control scripts at runtime during automated testing.

The server uses daemon threads and a JVM shutdown hook so it shuts down cleanly when the client exits. If the port is already in use from a previous session, the plugin automatically kills the old process and reclaims the port.

## Endpoints

| Path | Methods | Description |
|------|---------|-------------|
| `/state` | GET | Game state, player info |
| `/skills` | GET | Skill levels and XP |
| `/login` | GET, POST | Login status, trigger login, error detection |
| `/scripts` | GET | List all microbot plugins |
| `/scripts/start` | POST | Start a plugin by className or name |
| `/scripts/stop` | POST | Stop a running plugin |
| `/scripts/status` | GET | Plugin execution status and runtime |
| `/scripts/results` | GET, POST | Submit and retrieve test results |
| `/inventory` | GET, POST | Inventory items and interactions |
| `/npcs` | GET, POST | NPC queries and interactions |
| `/objects` | GET, POST | Object queries and interactions |
| `/ground-items` | GET, POST | Ground item queries and pickup |
| `/walk` | POST | Walk to coordinates |
| `/bank` | GET, POST | Banking operations |
| `/dialogue` | GET, POST | Dialogue state and interaction |
| `/widgets/list` | GET | List visible widgets |
| `/widgets/search` | GET | Search widgets by keyword |
| `/widgets/describe` | GET | Widget tree inspection |
| `/widgets/click` | POST | Click a widget |

## Login

`POST /login` **blocks by default** (up to 30s) until login succeeds or a fatal error is detected. Returns a definitive result — no polling needed.

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `world` | number | - | Target world |
| `wait` | boolean | `true` | Block until login resolves |
| `timeout` | number | `30` | Max wait seconds (max 120) |

`GET /login` returns current state including `loginIndex` and `loginError` when on the login screen.

**Login error codes:**

| loginIndex | loginError |
|------------|------------|
| 3 | Authentication failed - invalid credentials |
| 4 | Invalid credentials |
| 14 | Account is banned |
| 24 | Disconnected from server |
| 34 | Non-member account cannot login to members world |

**HTTP status codes for `POST /login`:**

| Status | Meaning |
|--------|---------|
| 200 | Login successful (or already logged in) |
| 400 | Login rejected (no profile, not on login screen) |
| 401 | Login failed (auth failure, banned, non-member, or timeout) |
| 409 | Login attempt already in progress |

**Auto-dismiss on retry:** When a login fails (e.g., non-member on members world), the game shows an error dialog. On the next `POST /login` call, the error dialog is automatically dismissed before retrying — no manual intervention needed. You can immediately retry with a different world.

## Script Result Submission (Java API)

Hub scripts running inside the JVM can submit results directly:

```java
import net.runelite.client.plugins.microbot.agentserver.handler.ScriptResultStore;

ScriptResultStore.submit("com.hub.MyPlugin", Map.of("passed", true, "kills", 10));
```

## Related Docs

- `docs/MICROBOT_CLI.md` — CLI command reference (this repo)
- `docs/SCRIPT_LIFECYCLE_API.md` — Script lifecycle HTTP API details (this repo)
- `docs/AGENT_SERVER.md` in Microbot client repo — Full HTTP API reference with examples
