# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Microbot Hub is a community plugin repository for the Microbot RuneLite client. It maintains a separation between core client functionality and community-contributed plugins, allowing rapid plugin development without affecting client stability. Each plugin is independently built, versioned, and packaged for GitHub Releases.

## Build System Architecture

The build system uses **Gradle with custom plugin discovery and packaging**:

- **Dynamic Plugin Discovery**: `build.gradle` scans `src/main/java/net/runelite/client/plugins/microbot/` for directories containing `*Plugin.java` files
- **Per-Plugin Source Sets**: Each discovered plugin gets its own Gradle source set, compile task, and shadow JAR task
- **Gradle Helper Scripts**: Core build logic lives in:
  - `gradle/project-config.gradle` - centralized configuration (JDK version, paths, GitHub release URLs, client version)
  - `gradle/plugin-utils.gradle` - plugin discovery, descriptor parsing, JAR creation, SHA256 hashing

### Build Commands

```bash
# Build all plugins
./gradlew clean build

# Build specific plugin(s) only (much faster for iteration)
./gradlew build -PpluginList=PestControlPlugin
./gradlew build -PpluginList=PestControlPlugin,AutoMiningPlugin

# Run tests (tests have access to all plugin source sets)
./gradlew test

# Generate plugins.json metadata file with SHA256 hashes (requires exact JDK 11)
./gradlew generatePluginsJson

# Copy plugin documentation to public/docs/
./gradlew copyPluginDocs

# Launch RuneLite debug session with plugins from Microbot.java
./gradlew run --args='--debug'

# Validate JDK version
./gradlew validateJdkVersion
```

## Plugin Structure

Each plugin lives in its own package under `src/main/java/net/runelite/client/plugins/microbot/<pluginname>/`:

```
<pluginname>/
├── <PluginName>Plugin.java    # Main plugin class with @PluginDescriptor
├── <PluginName>Script.java    # Script logic extending Script class
├── <PluginName>Config.java    # Configuration interface (optional)
├── <PluginName>Overlay.java   # UI overlay (optional)
└── Additional support classes
```

Matching resources under `src/main/resources/net/runelite/client/plugins/microbot/<pluginname>/`:

```
<pluginname>/
├── dependencies.txt           # Maven coordinates (optional)
└── docs/
    ├── README.md              # Plugin documentation
    └── assets/                # Screenshots, icons, etc.
```

## Plugin Descriptor Anatomy

Every plugin **must** have a `@PluginDescriptor` annotation with these **required** fields:

- `name` - Display name (use `PluginConstants.DEFAULT_PREFIX` or create custom prefix)
- `version` - Semantic version string (store in `static final String version` field)
- `minClientVersion` - Minimum Microbot client version required

Important **optional** fields:

- `authors` - Array of author names
- `description` - Brief description shown in plugin panel
- `tags` - Array of tags for categorization
- `iconUrl` - URL to icon image (shown in client hub)
- `cardUrl` - URL to card image (shown on website)
- `enabledByDefault` - Use `PluginConstants.DEFAULT_ENABLED` (currently `false`)
- `isExternal` - Use `PluginConstants.IS_EXTERNAL` (currently `true`)

Example:
```java
@PluginDescriptor(
    name = PluginConstants.MOCROSOFT + "Pest Control",
    description = "Supports all boats, portals, and shields.",
    tags = {"pest control", "minigames"},
    authors = { "Mocrosoft" },
    version = PestControlPlugin.version,
    minClientVersion = "1.9.6",
    iconUrl = "https://chsami.github.io/Microbot-Hub/PestControlPlugin/assets/icon.png",
    cardUrl = "https://chsami.github.io/Microbot-Hub/PestControlPlugin/assets/card.png",
    enabledByDefault = PluginConstants.DEFAULT_ENABLED,
    isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class PestControlPlugin extends Plugin {
    static final String version = "2.2.7";
    // ...
}
```

## PluginConstants

The `PluginConstants.java` file is **shared across all plugins** (included in each JAR during build). It contains:

- Standardized plugin name prefixes (e.g., `DEFAULT_PREFIX`, `MOCROSOFT`, `BOLADO`)
- Global defaults: `DEFAULT_ENABLED = false`, `IS_EXTERNAL = true`

When creating a new plugin prefix, add it to `PluginConstants.java` for consistency.

## Adding External Dependencies

If a plugin needs additional libraries beyond the Microbot client:

1. Create `src/main/resources/net/runelite/client/plugins/microbot/<pluginname>/dependencies.txt`
2. Add Maven coordinates, one per line:
   ```
   com.google.guava:guava:33.2.0-jre
   org.apache.commons:commons-lang3:3.14.0
   ```
3. The build system automatically includes these in the plugin's shadow JAR

## Testing and Debugging Plugins

**Before chasing a "script does nothing" bug, read [`docs/PLUGIN_DEBUGGING_NOTES.md`](docs/PLUGIN_DEBUGGING_NOTES.md).** It documents the recurring failure modes in Hub plugins (instanced-region coordinate mismatches, the new Queryable API not auto-walking, null-guard predicates masking broken lookups, static field leakage across plugin restarts, etc.) and the agent-server `curl` workflow for inspecting live state instead of theorizing from code.

### Running Plugins in Debug Mode

1. Edit `src/test/java/net/runelite/client/Microbot.java`
2. Add your plugin class to the `debugPlugins` array:
   ```java
   private static final Class<?>[] debugPlugins = {
       YourPlugin.class,
       AutoLoginPlugin.class
   };
   ```
3. Run `./gradlew run --args='--debug'` or use your IDE's run configuration

### Running Tests

- Tests live in `src/test/java/`
- Test classes have access to all plugin source sets (configured in `build.gradle`)
- Use `./gradlew test` to run all tests

## Version Management

- **Always increment the plugin version** when making changes (even small fixes)
- Store version in a static field: `static final String version = "1.2.3";`
- Follow semantic versioning: `MAJOR.MINOR.PATCH`
- The version is used for JAR naming, GitHub release assets, and `plugins.json` generation

## Git Workflow

Based on recent commits:

- Use conventional commit prefixes: `fix:`, `feat:`, `docs:`, etc.
- Include PR references when applicable: `fix: description (#123)`
- Work on feature branches, merge to `development`, create PRs to `main`
- Current branch: `development`, main branch: `main`

## Publishing Workflow

1. Build plugins: `./gradlew build`
2. Generate metadata: `./gradlew generatePluginsJson` (requires JDK 11 exactly)
3. Copy documentation: `./gradlew copyPluginDocs`
4. Upload `build/libs/<pluginname>-<version>.jar` and updated `public/docs/plugins.json` as assets on the GitHub release tagged with `<version>` (or `latest-release` for the stable tag): `https://github.com/chsami/Microbot-Hub/releases/download/<tag>/<pluginname>-<version>.jar`

## Important Implementation Details

- **Local Microbot Client Source**: The latest Microbot client source lives in the sibling `Microbot` folder (`../Microbot/`) on the `development` branch. This is the authoritative, up-to-date client codebase. When you need to look up client APIs, utility classes (e.g., `Rs2Bank`, `Rs2Inventory`, `Rs2Walker`), or understand how the client works, reference that repository directly.
- **Java Version**: JDK 11 (configured in `project-config.gradle` with `TARGET_JDK_VERSION = 11`, vendor `ADOPTIUM`)
- **Microbot Client Dependency**: Defaults to the latest version resolved via `https://microbot.cloud/api/version/client`, falling back to `2.0.61` if lookup fails. Artifacts come from GitHub Releases (`https://github.com/chsami/Microbot/releases/download/<version>/microbot-<version>.jar`). Override with `-PmicrobotClientVersion=<version>` or `-PmicrobotClientVersion=latest`, or supply a local JAR for offline work via `-PmicrobotClientPath=/absolute/path/to/microbot-<version>.jar`
- **Plugin Release Tag**: `plugins.json` uses a stable release tag (`latest-release`) so download URLs stay constant: `https://github.com/chsami/Microbot-Hub/releases/download/latest-release/<plugin>-<version>.jar`. Override with `-PpluginsReleaseTag=<tag>` if needed.
- **Shadow JAR Excludes**: Common exclusions defined in `plugin-utils.gradle` include `docs/**`, `dependencies.txt`, metadata files, and module-info
- **Reproducible Builds**: JAR tasks disable file timestamps, use reproducible file order, and normalize file permissions to `0644`
- **Descriptor Parsing**: Build system uses regex to extract plugin metadata from Java source files (see `getPluginDescriptorInfo` in `plugin-utils.gradle`)

## Plugin Discovery Logic

When you run `./gradlew build`:

1. Scans `src/main/java/net/runelite/client/plugins/microbot/` for directories
2. Finds directories containing a file matching `*Plugin.java`
3. Creates a plugin object with: `name` (class name without .java), `sourceSetName` (directory name), `dir`, `javaFile`
4. Filters by `-PpluginList` if provided
5. For each plugin:
   - Creates dedicated source set
   - Configures compilation classpath with Microbot client
   - Creates shadow JAR task with plugin-specific dependencies
   - Parses `@PluginDescriptor` for metadata
   - Computes SHA256 hash of JAR for `plugins.json`

## Microbot CLI & Agent Server

The Microbot client embeds an HTTP server (Agent Server plugin, port 8081) that the Hub uses for automated testing. Reference docs are mirrored in this repo:

- **CLI command reference**: `docs/MICROBOT_CLI.md` — login, script lifecycle, inventory, NPCs, walking, banking, etc.
- **HTTP API summary**: `docs/AGENT_SERVER.md` — all endpoints, login error detection, script result submission.
- **Script lifecycle API**: `docs/SCRIPT_LIFECYCLE_API.md` — start/stop/status/results endpoints and automated testing flow.
- **Test example**: `src/test/java/net/runelite/client/ScriptLifecycleTest.java` — demonstrates the full login → start → poll → results → stop cycle.

Key capabilities for Hub plugin testing:
- **Login control**: `POST /login` blocks until login succeeds or fails, returning a definitive `success` boolean and `loginError` on failure (non-member on members world, bans, auth failures). Auto-dismisses error dialogs on retry — no manual intervention needed.
- **Script lifecycle**: Start/stop plugins by class name via HTTP, poll runtime status, submit and retrieve structured test results.
- **Java result API**: Hub scripts can call `ScriptResultStore.submit(className, data)` directly from within the JVM.

## Dynamic Script Deployment (Hot-Reload)

**Core Mechanism:** Compile Java source → load via custom URLClassLoader → inject into Guice → start as a RuneLite plugin. No client restart needed.

**HTTP endpoints (127.0.0.1:8081):**
- `POST /scripts/deploy` — compile & start
- `POST /scripts/deploy/reload` — recompile in place
- `POST /scripts/deploy/undeploy` — stop & unload
- `GET /scripts/deploy` — list deployments

**Core files:** `agentserver/scripting/DynamicScriptManager.java`, `DynamicScriptCompiler.java`, `handler/DynamicScriptDeployHandler.java`

### Supporting Systems

1. **microbot-cli** — bash wrapper for the agent server (`./microbot-cli scripts deploy|reload|undeploy|health|results`)
2. **Agent Server API** — query state (NPCs, objects, inventory, widgets) and drive the client (walk, interact, dialogue). Full reference in `docs/AGENT_SERVER.md`.
3. **Probe plugin pattern** — minimal `@PluginDescriptor` classes deployed to inspect engine state, iterate with hot-reload. Used by the `/debugger` skill.
4. **StateMachineScript** — base class for multi-phase scripts; transitions observable via `GET /debug/snapshot?script=Name`.
5. **ScriptResultStore** — scripts submit results in-process; retrieve via `/scripts/results` for test workflows.
6. **`scripts/test_hot_reload.py`** — working end-to-end example of the deploy/reload/undeploy lifecycle.

### Typical Loop

```
edit source → microbot-cli scripts reload → microbot-cli scripts health → observe logs → repeat
```

Everything binds to `127.0.0.1` only.

## Common Patterns

- Plugins extending `SchedulablePlugin` implement `getStartCondition()` and `getStopCondition()` for scheduler integration
- Use `@Inject` for dependency injection (configs, overlays, scripts)
- Config classes use `@Provides` methods to register with `ConfigManager`
- Overlays are registered in `startUp()`, unregistered in `shutDown()`
- Use `@Subscribe` for event handling (ChatMessage, GameTick, etc.)

## Threading

Scripts run on a scheduled executor thread, but certain RuneLite API calls (widgets, game objects, etc.) must run on the client thread:

```java
// Use invoke() for client thread operations
TrialInfo info = Microbot.getClientThread().invoke(() -> TrialInfo.getCurrent(client));

// For void operations
Microbot.getClientThread().invoke(() -> {
    // client thread code here
});
```

**Always use `Microbot.getClientThread().invoke()`** when accessing:
- Widgets (`client.getWidget()`, `widget.isHidden()`)
- Game objects that aren't cached
- Player world view (`client.getLocalPlayer().getWorldView()`)
- Varbits (`client.getVarbitValue()`)
- `BoatLocation.fromLocal()` - accesses player world view internally
- `TrialInfo.getCurrent()` - accesses widgets internally
- `Rs2BoatCache.getLocalBoat()` - accesses player world view
- `Rs2BoatModel.isNavigating()` - accesses varbits
- `Rs2BoatModel.isMovingForward()` - accesses varbits
- `Rs2BoatModel.getHeading()` - accesses varbits
- Any RuneLite API that throws "must be called on client thread"

## Event Subscription from Scripts

**Important:** `@Subscribe` event handlers (`onGameTick`, `onClientTick`, `onVarbitChanged`, `onGameObjectSpawned`, etc.) **run on the client thread automatically**. You do NOT need to wrap client API calls in `Microbot.getClientThread().invoke()` inside these handlers.

Scripts can subscribe to RuneLite events by injecting the EventBus:

```java
@Slf4j
public class MyScript {
    private final EventBus[AutoRunScript.java](src/main/java/net/runelite/client/plugins/microbot/qualityoflife/scripts/AutoRunScript.java) eventBus;

    @Inject
    public MyScript(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    public void register() {
        eventBus.register(this);
    }

    public void unregister() {
        eventBus.unregister(this);
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        // Handle game tick
    }
}
```

In the plugin, call `register()` in `startUp()` and `unregister()` in `shutDown()`:

```java
@Override
protected void startUp() {
    myScript.register();
}

protected void shutDown() {
    myScript.unregister();
}
```
