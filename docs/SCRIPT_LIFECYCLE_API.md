# Script Lifecycle API

The Microbot client exposes HTTP endpoints that let the Hub start, stop, and monitor plugins at runtime. This is the mechanism for automated testing: the Hub spawns a client with Hub plugins on the classpath, then uses these endpoints to drive execution and collect results.

## Architecture

```
┌──────────────────────────────────────────────────┐
│  Microbot-Hub                                    │
│                                                  │
│  1. Build client with Hub plugins on classpath   │
│  2. Spawn client process                         │
│  3. POST /login (blocks until logged in)         │
│  4. POST /scripts/start {className: "..."}       │
│  5. Poll GET /scripts/status?className=...       │
│  6. GET /scripts/results?className=...           │
│  7. POST /scripts/stop {className: "..."}        │
└──────────────────────┬───────────────────────────┘
                       │ HTTP (localhost:8081)
┌──────────────────────▼───────────────────────────┐
│  Microbot Client (with Agent Server enabled)     │
│                                                  │
│  Server uses daemon threads + shutdown hook —     │
│  shuts down cleanly when client exits.           │
│  Auto-kills old server if port is already in use.│
│                                                  │
│  ScriptHandler receives requests, starts/stops   │
│  plugins via PluginManager on the EDT.           │
│                                                  │
│  ScriptResultStore holds test results in-memory. │
│  Scripts can submit results via Java API:        │
│    ScriptResultStore.submit(className, data)     │
└──────────────────────────────────────────────────┘
```

## Prerequisites

- Microbot client built with the Agent Server plugin
- Agent Server enabled in the client (default port 8081)
- Hub plugins on the client classpath

## Login

Before starting scripts, the client needs to be logged in. The `/login` endpoint handles this.

### GET /login

Returns login state, active profile, and error detection.

```bash
curl http://127.0.0.1:8081/login
```

When on the login screen, the response includes `loginIndex` and `loginError` to detect issues:

| loginIndex | loginError |
|------------|------------|
| 3 | Authentication failed - invalid credentials |
| 4 | Invalid credentials |
| 14 | Account is banned |
| 24 | Disconnected from server |
| 34 | Non-member account cannot login to members world |

### POST /login

**Blocks by default** until login succeeds or fails. Returns a definitive result — no polling needed.

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `world` | number | - | Target world (omit for profile default) |
| `wait` | boolean | `true` | Block until login resolves. Set `false` for fire-and-forget. |
| `timeout` | number | `30` | Max seconds to wait (max 120) |

```bash
# Blocks until logged in or failure (up to 30s)
curl -X POST -H 'Content-Type: application/json' \
  -d '{"world":360}' http://127.0.0.1:8081/login

# With custom timeout
curl -X POST -H 'Content-Type: application/json' \
  -d '{"world":360, "timeout":60}' http://127.0.0.1:8081/login

# Fire and forget (returns immediately)
curl -X POST -H 'Content-Type: application/json' \
  -d '{"wait":false}' http://127.0.0.1:8081/login
```

**Responses:**

```json
// Success
{"success": true, "message": "Login successful", "currentWorld": 360}

// Non-member on members world
{"success": false, "message": "Login failed: Non-member account cannot login to members world", "loginIndex": 34, "loginError": "Non-member account cannot login to members world"}

// Timeout
{"success": false, "message": "Login timed out after 30s", "currentWorld": 360}
```

| Status | Meaning |
|--------|---------|
| 200 | Login successful (or already logged in) |
| 400 | Login rejected (no profile, not on login screen) |
| 401 | Login failed (auth failure, banned, non-member, or timeout) |
| 409 | Login attempt already in progress |

**Auto-dismiss on retry:** When a login fails (e.g., non-member on members world), the game shows an error dialog. On the next `POST /login` call, the error dialog is automatically dismissed before retrying — no manual intervention needed. You can immediately retry with a different world:

```bash
curl -X POST -d '{"world":461,"timeout":30}' http://127.0.0.1:8081/login
# → {"success":false,"loginIndex":34,"loginError":"Non-member account..."}

curl -X POST -d '{"world":383,"timeout":30}' http://127.0.0.1:8081/login
# → {"success":true,"message":"Login successful","currentWorld":383}
```

### Login flow for automated testing

```bash
# Single blocking call — no polling loop needed
curl -X POST -H 'Content-Type: application/json' \
  -d '{"world":360, "timeout":60}' http://127.0.0.1:8081/login

# Check the response: success=true means logged in, success=false has loginError explaining why

# Then start the script
curl -X POST -d '{"className":"com.hub.MyPlugin"}' http://127.0.0.1:8081/scripts/start
```

Or use the CLI:

```bash
./microbot-cli login now --world 360
./microbot-cli scripts start --class "com.hub.MyPlugin"
```

## Script Endpoints

### GET /scripts

Lists all microbot plugins with active/enabled status.

```bash
curl http://127.0.0.1:8081/scripts
```

### POST /scripts/start

Starts a plugin. The `className` must be the fully qualified Java class name.

```bash
curl -X POST -H 'Content-Type: application/json' \
  -d '{"className":"net.runelite.client.plugins.microbot.aiofighter.AIOFighterPlugin"}' \
  http://127.0.0.1:8081/scripts/start
```

You can also use `{"name": "AIO Fighter"}` for partial name matching.

### POST /scripts/stop

```bash
curl -X POST -H 'Content-Type: application/json' \
  -d '{"className":"net.runelite.client.plugins.microbot.aiofighter.AIOFighterPlugin"}' \
  http://127.0.0.1:8081/scripts/stop
```

### GET /scripts/status

```bash
curl 'http://127.0.0.1:8081/scripts/status?className=net.runelite.client.plugins.microbot.aiofighter.AIOFighterPlugin'
```

Returns `status` (RUNNING/STOPPED/ERROR), `startedAt`, `runtimeMs`, and `error` if applicable.

### POST /scripts/results

Submit test results from a script or external harness.

```bash
curl -X POST -H 'Content-Type: application/json' \
  -d '{"className":"com.hub.MyPlugin","passed":true,"kills":10}' \
  http://127.0.0.1:8081/scripts/results
```

### GET /scripts/results

```bash
curl 'http://127.0.0.1:8081/scripts/results?className=com.hub.MyPlugin'
```

## Submitting Results from Java

Hub scripts running inside the JVM can submit results directly without HTTP:

```java
import net.runelite.client.plugins.microbot.agentserver.handler.ScriptResultStore;
import java.util.Map;

ScriptResultStore.submit(
    "net.runelite.client.plugins.microbot.myplugin.MyPlugin",
    Map.of("passed", true, "kills", 10, "runtime", 45000)
);
```

## Test Examples

- `src/test/java/net/runelite/client/ScriptLifecycleTest.java` — full lifecycle: login → start → poll → results → stop
- `src/test/java/net/runelite/client/LoginTest.java` — login-only test with non-member detection and F2P fallback

Run them with:

```bash
# ScriptLifecycleTest launches the client itself
./gradlew test --tests ScriptLifecycleTest

# LoginTest requires a running client with Agent Server enabled
java LoginTest          # login with profile defaults
java LoginTest 461      # force members world (triggers non-member detection for F2P accounts)
java LoginTest 383      # force F2P world
```
