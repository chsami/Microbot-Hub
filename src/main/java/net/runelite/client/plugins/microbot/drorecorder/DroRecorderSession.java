package net.runelite.client.plugins.microbot.drorecorder;

import com.google.gson.Gson;
import lombok.Getter;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.RuneLite;

import javax.annotation.Nullable;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

final class DroRecorderSession
{
    private static final String RECORDER_VERSION = "1.3";

    private static final DateTimeFormatter SESSION_FORMAT =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneId.systemDefault());

    private final Client client;
    private final Gson gson;
    private final DroRecorderConfig config;
    private final DroRecorderWriter writer;
    private final DroRecorderSpatial spatial;
    private final long startedAtMs;
    private final long startedAtNanos = System.nanoTime();
    private final AtomicLong sequence = new AtomicLong();
    private volatile int observedTick;
    private volatile int observedCycle;
    private volatile long lastTickNanos;
    private final DroRecorderIdentityMap<NPC> npcIdentities = new DroRecorderIdentityMap<>();
    private final DroRecorderIdentityMap<Projectile> projectileIdentities = new DroRecorderIdentityMap<>();
    private long nextIdentity;
    private final Map<Long, Integer> recentActions = new LinkedHashMap<>();
    private String previousDialogue;
    private String pendingBaseline = "recording_started";
    private long previousDropped;
    private int pauseTick;
    private long pauseNanos;


    private volatile String lastSceneKey;
    private int npcContextTick = Integer.MIN_VALUE;
    private String npcContextScene;
    private long npcContextSequence;

    private final AtomicLong rawCount = new AtomicLong();
    private final AtomicLong eventCount = new AtomicLong();
    private final AtomicLong tickCount = new AtomicLong();

    @Getter
    private volatile boolean paused;

    private volatile long lastMouseSampleMs;
    private volatile int lastMouseX = Integer.MIN_VALUE;
    private volatile int lastMouseY = Integer.MIN_VALUE;

    /** Local engine that drives this run (plugin name and version); null for manual recordings. */
    private String engineVersion;

    private List<String> previousActivePrayers = new ArrayList<>();
    private boolean prayerStateInitialized;

    DroRecorderSession(Client client, Gson gson, DroRecorderConfig config)
    {
        this(client, gson, config, RuneLite.RUNELITE_DIR.toPath().resolve("dro-recorder")
            .resolve(SESSION_FORMAT.format(Instant.now())));
    }

    DroRecorderSession(Client client, Gson gson, DroRecorderConfig config, Path directory)
    {
        this.client = client;
        this.gson = gson;
        this.config = config;
        this.spatial = new DroRecorderSpatial(client, config);
        this.startedAtMs = System.currentTimeMillis();
        this.writer = new DroRecorderWriter(directory, gson, config.compressRecordings());
    }

    void engineVersion(String version)
    {
        engineVersion = version;
    }

    void start()
    {
        observeClock();
        writer.start();
        Map<String, Object> event = base("session_start");
        event.put("outputDirectory", writer.getSessionDirectory().toString());
        event.put("clientBuild", safeClientBuild());
        event.put("aioVersion", null);
        event.put("engineVersion", engineVersion);
        event.put("recorderVersion", RECORDER_VERSION);
        event.put("compressed", config.compressRecordings());
        event.put("recordingSizeLimit", "none");
        event.put("combatContextEnabled", config.recordCombatContext());
        event.put("fightCavesClassification", config.fightCavesClassification());
        event.put("activityEventsEnabled", true);
        event.put("tileKeyEncoding", "plane[29:28],x[27:14],y[13:0]; scope with worldViewId and sceneKey");
        event.put("attackCatalogue", "Elemental spell/magic-dart and common arrow/bolt/dart/javelin projectile IDs; Fight Caves role+animation IDs; unmapped UNKNOWN");
        writeEvent(event);
    }

    /** Named arena records (state sent, command arguments, safety net) keep their own type; unnamed ones are
     * arena input/prayer command stages. Same records as Dro's recorder 1.3.4. */
    static String diagnosticType(Map<String,Object> values) {
        Object named=values.get("record");
        if(named instanceof String&&(((String)named).startsWith("arena_")||((String)named).startsWith("firecape_")))return (String)named;
        return "arena_input".equals(values.get("kind"))?"arena_input_command":"arena_prayer_command";
    }

    /** Trusted local diagnostic metadata only; no socket payloads or credentials. */
    void recordDiagnostic(Map<String,Object> values) {
        Map<String,Object> event=base(diagnosticType(values));
        event.put("diagnostic",new LinkedHashMap<>(values));
        writeEvent(event);
    }

    /** Round trip to the current game world, measured off the client thread; -1 when it could not be measured. */
    void recordWorldPing(int world, int pingMs)
    {
        Map<String, Object> event = base("network_ping");
        event.put("world", world);
        event.put("pingMs", pingMs);
        writeEvent(event);
    }

    void stop()
    {
        Map<String, Object> event = base("session_stop");
        event.put("rawRecords", rawCount.get());
        event.put("eventRecords", eventCount.get());
        event.put("ticks", tickCount.get());
        event.put("droppedLines", writer.getDroppedLines());
        event.put("droppedByType", writer.getDroppedByType());
        writeEventForce(event);
        writer.stop();
    }

    java.util.concurrent.CompletableFuture<Void> getCompletion()
    {
        return writer.getCompletion();
    }

    Path getOutputDirectory()
    {
        return writer.getSessionDirectory();
    }

    long getStartedAtMs()
    {
        return startedAtMs;
    }

    long getEventCount()
    {
        return eventCount.get();
    }

    long getTickCount()
    {
        return tickCount.get();
    }

    long getDroppedLines()
    {
        return writer.getDroppedLines();
    }

    void togglePaused()
    {
        observeClock();
        paused = !paused;
        if (paused) { pauseTick = observedTick; pauseNanos = System.nanoTime(); }
        if (!paused)
        {
            // Do not report prayer changes that happened while recording was intentionally paused.
            prayerStateInitialized = false;
            lastSceneKey = null;
            pendingBaseline = "recording_resumed";
            npcContextTick = Integer.MIN_VALUE;
            recentActions.clear();
        }
        Map<String, Object> event = base(paused ? "recording_paused" : "recording_resumed");
        event.put("paused", paused);
        if (!paused)
        {
            event.put("gapTicks", observedTick - pauseTick);
            event.put("gapNanos", System.nanoTime() - pauseNanos);
        }
        writeEventForce(event);
    }


    private boolean isCaptureHeld() { return paused || !writer.isRunning(); }

    void recordGameTick()
    {
        observeClock();
        lastTickNanos = System.nanoTime();
        recentActions.values().removeIf(tick -> observedTick - tick > 12);
        projectileIdentities.removeIf(p -> p.getEndCycle() < observedCycle);
        if (isCaptureHeld())
        {
            return;
        }

        if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null)
        {
            return;
        }

        tickCount.incrementAndGet();
        recordPrayerTransitionIfChanged(activePrayers());
        recordDialogueIfChanged();
        recordSceneSnapshotIfChanged();
        recordLossIfChanged();
        if (config.recordObjectEvents())
        {
            List<Map<String, Object>> changes = spatial.objectTransformChanges();
            if (!changes.isEmpty())
            {
                Map<String, Object> event = base("object_transformed");
                event.put("objects", changes);
                writeEvent(event);
            }
        }
        if (!config.recordTickSnapshots())
        {
            return;
        }

        List<String> prayers = activePrayers();
        List<Map<String, Object>> npcs = npcSnapshot();
        List<Map<String, Object>> projectiles = projectileSnapshot();

        Map<String, Object> record = base("tick");
        record.put("gameState", client.getGameState().name());
        record.put("fps", client.getFPS());
        record.put("player", playerSnapshot());
        record.put("camera", cameraSnapshot());
        record.put("mouse", mouseSnapshot());
        record.put("prayers", prayers);
        record.put("protectionPrayer", activeProtectionPrayer(prayers));
        record.put("inventory", itemContainerSnapshot(client.getItemContainer(InventoryID.INVENTORY)));
        record.put("equipment", itemContainerSnapshot(client.getItemContainer(InventoryID.EQUIPMENT)));
        record.put("scene", spatial.sceneMetadata());
        record.put("npcs", npcs);
        record.put("projectiles", projectiles);
        record.put("graphicsObjects", graphicsSnapshot());
        record.put("activity", activityContext());
        Map<String, Object> skills = new LinkedHashMap<>();
        for (Skill skill : Skill.values())
        {
            if (skill == Skill.OVERALL) { continue; }
            skills.put(skill.name(), new int[]{client.getRealSkillLevel(skill), client.getBoostedSkillLevel(skill), client.getSkillExperience(skill)});
        }
        record.put("skills", skills);
        if (config.recordCombatContext())
        {
            record.put("combatContext", combatContext(npcs, projectiles, prayers));
        }

        writeRaw(record);
        recordPrayerTransitionIfChanged(prayers);
        recordSceneSnapshotIfChanged();
    }

    void recordMenuClick(MenuOptionClicked click)
    {
        if (isCaptureHeld())
        {
            return;
        }

        Map<String, Object> event = base("menu_click");
        event.put("option", click.getMenuOption());
        event.put("target", click.getMenuTarget());
        event.put("action", click.getMenuAction() == null ? null : click.getMenuAction().name());
        event.put("identifier", click.getId());
        event.put("param0", click.getParam0());
        event.put("param1", click.getParam1());
        event.put("itemOp", click.isItemOp());
        event.put("consumedAtCapture", click.isConsumed());
        if (click.isItemOp())
        {
            event.put("itemId", click.getItemId());
            event.put("itemOpIndex", click.getItemOp());
        }
        event.put("mouse", mouseSnapshot());
        event.put("player", compactPlayerContext());
        event.put("selectedSceneTile", selectedSceneTile());
        event.put("resolvedTarget", resolveMenuTarget(click));
        recentActions.put((Long) event.get("sequence"), observedTick);
        while (recentActions.size() > 16) { recentActions.remove(recentActions.keySet().iterator().next()); }
        event.put("outcomeSemantics", "Subsequent observed state only; correlation does not prove causation");
        writeEvent(event);
    }

    void recordNpcLifecycle(String type, NPC npc)
    {
        if (npc == null) { return; }
        if (isCaptureHeld() || !config.recordNpcEvents())
        {
            if ("npc_despawned".equals(type)) { npcIdentities.remove(npc); }
            return;
        }

        long contextSequence = recordNpcContext();
        Map<String, Object> event = base(type);
        event.put("npc", npcRecord(npc));
        event.put("player", compactPlayerContext());
        event.put("scene", spatial.sceneMetadata());
        event.put("npcContextSequence", contextSequence);
        writeEvent(event);
        if ("npc_despawned".equals(type)) { npcIdentities.remove(npc); }
    }

    void recordTileObjectLifecycle(String type, String objectKind, TileObject object)
    {
        spatial.invalidatePaths();
        if ("object_despawned".equals(type)) { spatial.forgetObject(object); }
        if (isCaptureHeld() || !config.recordObjectEvents() || object == null)
        {
            return;
        }

        Map<String, Object> event = base(type);
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("kind", objectKind);
        record.put("id", object.getId());
        record.put("hash", object.getHash());
        record.put("world", worldPointRecord(object.getWorldLocation()));
        record.put("local", localPointRecord(object.getLocalLocation()));
        record.put("plane", object.getPlane());
        spatial.enrichObjectRecord(record, object);
        event.put("object", record);
        event.put("player", compactPlayerContext());
        writeEvent(event);
        if ("object_despawned".equals(type)) { spatial.forgetObject(object); }
    }

    void recordAnimation(Actor actor)
    {
        if (isCaptureHeld() || !config.recordCombatEvents() || actor == null)
        {
            return;
        }

        Map<String, Object> event = base("animation_changed");
        Map<String, Object> actorData = actorRecord(actor);
        if (actor instanceof NPC)
        {
            spatial.enrichNpcRecord(actorData, (NPC) actor, client.getLocalPlayer());
        }
        List<String> prayers = activePrayers();
        event.put("actor", actorData);
        event.put("animation", actor.getAnimation());
        event.put("poseAnimation", actor.getPoseAnimation());
        event.put("prayers", prayers);
        event.put("protectionPrayer", activeProtectionPrayer(prayers));
        event.put("targetsLocalPlayer", actor.getInteracting() == client.getLocalPlayer());
        writeEvent(event);
    }

    void recordInteraction(Actor source, @Nullable Actor target)
    {
        if (isCaptureHeld() || !config.recordCombatEvents() || source == null)
        {
            return;
        }

        long contextSequence = recordNpcContext();
        Map<String, Object> event = base("interaction_changed");
        List<String> prayers = activePrayers();
        event.put("source", actorRecord(source));
        event.put("target", target == null ? null : actorRecord(target));
        event.put("sourceTargetsLocalPlayer", source.getInteracting() == client.getLocalPlayer());
        event.put("targetIsLocalPlayer", target == client.getLocalPlayer());
        event.put("prayers", prayers);
        event.put("protectionPrayer", activeProtectionPrayer(prayers));
        event.put("scene", spatial.sceneMetadata());
        event.put("npcContextSequence", contextSequence);
        writeEvent(event);
    }

    void recordHitsplat(Actor actor, Hitsplat hitsplat)
    {
        if (isCaptureHeld() || !config.recordCombatEvents() || actor == null || hitsplat == null)
        {
            return;
        }

        Map<String, Object> event = base("hitsplat");
        event.put("actor", actorRecord(actor));
        event.put("amount", hitsplat.getAmount());
        event.put("hitsplatType", hitsplat.getHitsplatType());
        event.put("mine", hitsplat.isMine());
        event.put("others", hitsplat.isOthers());
        List<String> prayers = activePrayers();
        event.put("prayers", prayers);
        event.put("protectionPrayer", activeProtectionPrayer(prayers));
        writeEvent(event);
    }

    void recordProjectile(Projectile projectile, @Nullable LocalPoint targetPosition, int targetZ)
    {
        if (isCaptureHeld() || !config.recordCombatEvents() || projectile == null)
        {
            return;
        }

        Map<String, Object> event = base("projectile_moved");
        List<String> prayers = activePrayers();
        event.put("projectile", projectileRecord(projectile));
        event.put("targetLocal", localPointRecord(targetPosition));
        event.put("targetZ", targetZ);
        event.put("prayers", prayers);
        event.put("protectionPrayer", activeProtectionPrayer(prayers));
        event.put("targetsLocalPlayer", projectile.getTargetActor() == client.getLocalPlayer());
        writeEvent(event);
    }

    void recordItemContainerChanged(ItemContainerChanged changed)
    {
        if (isCaptureHeld() || !config.recordInventoryEvents() || changed == null)
        {
            return;
        }

        // Filter to the two containers relevant to Fight Caves training.
        int inventoryId = InventoryID.INVENTORY.getId();
        int equipmentId = InventoryID.EQUIPMENT.getId();
        if (changed.getContainerId() != inventoryId && changed.getContainerId() != equipmentId)
        {
            return;
        }

        Map<String, Object> event = base("item_container_changed");
        event.put("containerId", changed.getContainerId());
        event.put("container", changed.getContainerId() == inventoryId ? "inventory" : "equipment");
        event.put("items", itemContainerSnapshot(changed.getItemContainer()));
        event.put("player", compactPlayerContext());
        writeEvent(event);
    }

    void recordGameState(GameState state)
    {
        if (state != GameState.LOGGED_IN)
        {
            lastSceneKey = null;
            pendingBaseline = "game_state_transition";
            npcContextTick = Integer.MIN_VALUE;
            prayerStateInitialized = false;
            previousDialogue = null;
            recentActions.clear();
            npcIdentities.clear();
            projectileIdentities.clear();
            spatial.clearScene();
        }
        Map<String, Object> event = base("game_state_changed");
        event.put("gameState", state == null ? null : state.name());
        writeEventForce(event);
    }

    void recordMouseMove(MouseEvent e, boolean dragged)
    {
        if (isCaptureHeld() || !config.recordMouseMovement() || e == null)
        {
            return;
        }

        long now = System.currentTimeMillis();
        if (!DroRecorderDiagnostic.fullRateMouse() && now - lastMouseSampleMs < config.mouseSampleMs())
        {
            return;
        }

        int x = e.getX();
        int y = e.getY();
        if (x == lastMouseX && y == lastMouseY)
        {
            return;
        }

        lastMouseSampleMs = now;
        lastMouseX = x;
        lastMouseY = y;

        Map<String, Object> record = base(dragged ? "mouse_dragged" : "mouse_moved");
        record.put("x", x);
        record.put("y", y);
        record.put("button", e.getButton());
        record.put("modifiersEx", e.getModifiersEx());
        writeRaw(record);
    }

    void recordMouseButton(String type, MouseEvent e)
    {
        if (isCaptureHeld() || e == null)
        {
            return;
        }

        Map<String, Object> event = base(type);
        event.put("x", e.getX());
        event.put("y", e.getY());
        event.put("button", e.getButton());
        event.put("clickCount", e.getClickCount());
        event.put("modifiersEx", e.getModifiersEx());
        event.put("popupTrigger", e.isPopupTrigger());
        writeEvent(event);
    }

    void recordFocus(String scope, boolean focused, String reason, boolean temporary)
    {
        if (isCaptureHeld()) return;
        Map<String, Object> event = base("focus_changed");
        event.put("scope", scope);
        event.put("focused", focused);
        event.put("reason", reason);
        event.put("temporary", temporary);
        writeEvent(event);
    }

    void recordMouseBoundary(String type, MouseEvent e)
    {
        if (isCaptureHeld() || e == null)
        {
            return;
        }

        Map<String, Object> event = base(type);
        event.put("x", e.getX());
        event.put("y", e.getY());
        event.put("canvasWidth", client.getCanvasWidth());
        event.put("canvasHeight", client.getCanvasHeight());
        writeEvent(event);
    }

    void annotate(String label)
    {
        if (isCaptureHeld())
        {
            return;
        }

        List<String> prayers = activePrayers();
        List<Map<String, Object>> npcs = npcSnapshot();
        List<Map<String, Object>> projectiles = projectileSnapshot();

        Map<String, Object> event = base("annotation");
        event.put("label", label);
        event.put("markerIntent", markerIntent(label));
        event.put("player", compactPlayerContext());
        event.put("mouse", mouseSnapshot());
        event.put("prayers", prayers);
        event.put("protectionPrayer", activeProtectionPrayer(prayers));
        event.put("npcs", npcs);
        event.put("projectiles", projectiles);
        if (config.recordCombatContext())
        {
            event.put("combatContext", combatContext(npcs, projectiles, prayers));
        }
        event.put("inventory", itemContainerSnapshot(client.getItemContainer(InventoryID.INVENTORY)));
        event.put("equipment", itemContainerSnapshot(client.getItemContainer(InventoryID.EQUIPMENT)));
        event.put("localCollision", spatial.localCollisionWindow(config.localCollisionRadius()));

        boolean forceFullSpatial = "SAFESPOT".equals(label) || "LURE".equals(label) || "RECOVERY".equals(label);
        event.put("fullSceneSnapshot", spatial.fullSceneSnapshot(
            "annotation_" + label.toLowerCase(), npcs, forceFullSpatial));
        writeEvent(event);
    }

    private Map<String, Object> base(String type)
    {
        Map<String, Object> map = new LinkedHashMap<>();
        long now = System.currentTimeMillis();
        map.put("type", type);
        map.put("timestampMs", now);
        map.put("sessionMs", now - startedAtMs);
        // Mouse callbacks run on AWT: use the latest client-thread observation there.
        if (client.isClientThread()) { observeClock(); }
        map.put("tick", observedTick);
        map.put("gameCycle", observedCycle);
        map.put("sequence", sequence.incrementAndGet());
        map.put("elapsedNanos", System.nanoTime() - startedAtNanos);
        map.put("sinceTickNanos", lastTickNanos == 0 ? null : System.nanoTime() - lastTickNanos);
        map.put("clockSource", client.isClientThread() ? "CLIENT_THREAD" : "LAST_CLIENT_OBSERVATION");
        if (client.isClientThread()) { map.put("recentActionSequences", new ArrayList<>(recentActions.keySet())); }
        map.put("schemaVersion", RECORDER_VERSION);
        return map;
    }

    private Map<String, Object> playerSnapshot()
    {
        Player player = client.getLocalPlayer();
        Map<String, Object> map = actorRecord(player);
        map.put("hp", client.getBoostedSkillLevel(Skill.HITPOINTS));
        map.put("hpBase", client.getRealSkillLevel(Skill.HITPOINTS));
        map.put("prayer", client.getBoostedSkillLevel(Skill.PRAYER));
        map.put("prayerBase", client.getRealSkillLevel(Skill.PRAYER));
        map.put("ranged", client.getBoostedSkillLevel(Skill.RANGED));
        map.put("rangedBase", client.getRealSkillLevel(Skill.RANGED));
        map.put("defence", client.getBoostedSkillLevel(Skill.DEFENCE));
        map.put("defenceBase", client.getRealSkillLevel(Skill.DEFENCE));
        map.put("activity", activityContext());
        map.put("runEnergyRaw", client.getEnergy());
        map.put("runEnergyPct", client.getEnergy() / 100.0);
        return map;
    }

    private Map<String, Object> compactPlayerContext()
    {
        Player player = client.getLocalPlayer();
        Map<String, Object> map = new LinkedHashMap<>();
        if (player == null)
        {
            return map;
        }

        map.put("world", worldPointRecord(player.getWorldLocation()));
        map.put("local", localPointRecord(player.getLocalLocation()));
        map.put("animation", player.getAnimation());
        map.put("poseAnimation", player.getPoseAnimation());
        map.put("orientation", player.getOrientation());
        map.put("hp", client.getBoostedSkillLevel(Skill.HITPOINTS));
        map.put("prayer", client.getBoostedSkillLevel(Skill.PRAYER));
        map.put("activity", activityContext());
        map.put("runEnergyRaw", client.getEnergy());
        map.put("interacting", actorReference(player.getInteracting()));
        return map;
    }

    private Map<String, Object> cameraSnapshot()
    {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("x", client.getCameraX());
        map.put("y", client.getCameraY());
        map.put("z", client.getCameraZ());
        map.put("yaw", client.getCameraYaw());
        map.put("pitch", client.getCameraPitch());
        map.put("yawTarget", client.getCameraYawTarget());
        map.put("pitchTarget", client.getCameraPitchTarget());
        map.put("scale", client.getScale());
        return map;
    }

    private Map<String, Object> mouseSnapshot()
    {
        Map<String, Object> map = new LinkedHashMap<>();
        Point p = client.getMouseCanvasPosition();
        if (p != null)
        {
            map.put("x", p.getX());
            map.put("y", p.getY());
        }
        map.put("button", client.getMouseCurrentButton());
        map.put("idleTicks", client.getMouseIdleTicks());
        map.put("lastPressedMs", client.getMouseLastPressedMillis());
        return map;
    }

    private List<String> activePrayers()
    {
        List<String> active = new ArrayList<>();
        for (Prayer prayer : Prayer.values())
        {
            if (client.isPrayerActive(prayer))
            {
                active.add(prayer.name());
            }
        }
        return active;
    }

    private List<Map<String, Object>> itemContainerSnapshot(@Nullable ItemContainer container)
    {
        List<Map<String, Object>> items = new ArrayList<>();
        if (container == null || container.getItems() == null)
        {
            return items;
        }

        Item[] array = container.getItems();
        for (int slot = 0; slot < array.length; slot++)
        {
            Item item = array[slot];
            if (item == null || item.getId() <= 0 || item.getQuantity() <= 0)
            {
                continue;
            }

            Map<String, Object> record = new LinkedHashMap<>();
            record.put("slot", slot);
            record.put("id", item.getId());
            record.put("quantity", item.getQuantity());
            record.put("name", client.getItemDefinition(item.getId()).getName());
            items.add(record);
        }
        return items;
    }

    private List<Map<String, Object>> npcSnapshot()
    {
        List<Map<String, Object>> npcs = new ArrayList<>();
        Player player = client.getLocalPlayer();
        WorldPoint playerPoint = player == null ? null : player.getWorldLocation();

        for (NPC npc : (Iterable<NPC>) Microbot.getRs2NpcCache().getStream().map(n -> n.getNpc())::iterator)
        {
            Map<String, Object> record = npcRecord(npc);
            if (playerPoint != null && npc.getWorldLocation() != null)
            {
                record.put("distanceToPlayer", npc.getWorldLocation().distanceTo(playerPoint));
            }
            spatial.enrichNpcRecord(record, npc, player);
            npcs.add(record);
        }
        return npcs;
    }

    private Map<String, Object> npcRecord(NPC npc)
    {
        Map<String, Object> record = actorRecord(npc);
        record.put("id", npc.getId());
        record.put("index", npc.getIndex());
        record.put("entityId", npcIdentity(npc));
        record.put("combatLevel", npc.getCombatLevel());
        record.put("footprintSize", npcFootprintSize(npc));
        if (config.fightCavesClassification())
        {
            String role = fightCavesRole(npc.getName());
            if (role != null)
            {
                record.put("fightCavesRole", role);
            }
        }
        return record;
    }

    @Nullable
    static Integer npcFootprintSize(NPC npc)
    {
        // The actor accessor returned 102 for a 2x2 Tar Monster and can throw after despawn.
        // Use the transformed definition's tile size; an unavailable definition is unknown.
        NPCComposition definition = npc.getTransformedComposition();
        return definition == null ? null : definition.getSize();
    }

    // Crowd interactions/spawns can fire thousands of times per tick. Keep the full
    // context once, and reference it from each event instead of copying it every time.
    private long recordNpcContext()
    {
        if (client.isClientThread()) { observeClock(); }
        String scene = spatial.sceneKey();
        if (npcContextTick != observedTick || !Objects.equals(npcContextScene, scene))
        {
            Map<String, Object> context = base("npc_context");
            context.put("npcs", npcSnapshot());
            context.put("scene", spatial.sceneMetadata());
            npcContextTick = observedTick;
            npcContextScene = scene;
            npcContextSequence = (Long) context.get("sequence");
            writeEvent(context);
        }
        return npcContextSequence;
    }

    private Map<String, Object> actorRecord(@Nullable Actor actor)
    {
        Map<String, Object> record = new LinkedHashMap<>();
        if (actor == null)
        {
            return record;
        }

        if (actor instanceof NPC)
        {
            NPC npc = (NPC) actor;
            record.put("actorType", "npc");
            record.put("id", npc.getId());
            record.put("index", npc.getIndex());
            record.put("entityId", npcIdentity(npc));
        }
        else if (actor instanceof Player)
        {
            record.put("actorType", actor == client.getLocalPlayer() ? "local_player" : "player");
        }
        else
        {
            record.put("actorType", actor.getClass().getSimpleName());
        }

        if (actor.getWorldView() != null) { record.put("worldViewId", actor.getWorldView().getId()); }
        record.put("name", actor instanceof NPC ? actor.getName() : null);
        record.put("templateWorld", actor.getWorldLocation() == null ? null : templatePoint(actor.getLocalLocation(), actor.getWorldLocation().getPlane()));
        record.put("spotAnimations", spotAnimations(actor));
        record.put("attack", attackClassification(actor));
        record.put("world", worldPointRecord(actor.getWorldLocation()));
        record.put("local", localPointRecord(actor.getLocalLocation()));
        record.put("animation", actor.getAnimation());
        record.put("animationFrame", actor.getAnimationFrame());
        record.put("poseAnimation", actor.getPoseAnimation());
        record.put("orientation", actor.getOrientation());
        record.put("currentOrientation", actor.getCurrentOrientation());
        record.put("healthRatio", actor.getHealthRatio());
        record.put("healthScale", actor.getHealthScale());
        record.put("dead", actor.isDead());
        record.put("interacting", actorReference(actor.getInteracting()));
        return record;
    }

    @Nullable
    private Map<String, Object> actorReference(@Nullable Actor actor)
    {
        if (actor == null)
        {
            return null;
        }

        Map<String, Object> ref = new LinkedHashMap<>();
        if (actor instanceof NPC)
        {
            NPC npc = (NPC) actor;
            ref.put("actorType", "npc");
            ref.put("id", npc.getId());
            ref.put("index", npc.getIndex());
            ref.put("entityId", npcIdentity(npc));
        }
        else if (actor instanceof Player)
        {
            ref.put("actorType", actor == client.getLocalPlayer() ? "local_player" : "player");
        }
        else
        {
            ref.put("actorType", actor.getClass().getSimpleName());
        }
        ref.put("name", actor instanceof NPC ? actor.getName() : null);
        if (actor.getWorldView() != null) { ref.put("worldViewId", actor.getWorldView().getId()); }
        ref.put("world", worldPointRecord(actor.getWorldLocation()));
        return ref;
    }

    private List<Map<String, Object>> projectileSnapshot()
    {
        List<Map<String, Object>> projectiles = new ArrayList<>();
        if (!config.recordCombatEvents())
        {
            return projectiles;
        }

        for (Projectile projectile : client.getProjectiles())
        {
            projectiles.add(projectileRecord(projectile));
        }
        return projectiles;
    }

    private Map<String, Object> projectileRecord(Projectile projectile)
    {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", projectile.getId());
        record.put("entityId", projectileIdentities.computeIfAbsent(projectile, p -> ++nextIdentity));
        record.put("attack", DroRecorderAttack.projectile(projectile.getId()));
        if (projectile.getSourceActor() != null) { record.put("observedSourceAttack", attackClassification(projectile.getSourceActor())); }
        record.put("sourceLevel", projectile.getSourceLevel());
        record.put("targetLevel", projectile.getTargetLevel());
        record.put("orientation", projectile.getOrientation());
        record.put("slope", projectile.getSlope());
        record.put("startHeight", projectile.getStartHeight());
        record.put("endHeight", projectile.getEndHeight());
        record.put("sourcePoint", worldPointRecord(projectile.getSourcePoint()));
        record.put("targetPoint", worldPointRecord(projectile.getTargetPoint()));
        record.put("sourceActor", actorReference(projectile.getSourceActor()));
        record.put("targetActor", actorReference(projectile.getTargetActor()));
        record.put("startCycle", projectile.getStartCycle());
        record.put("endCycle", projectile.getEndCycle());
        record.put("remainingCycles", projectile.getRemainingCycles());
        record.put("targetsLocalPlayer", projectile.getTargetActor() == client.getLocalPlayer());
        if (config.fightCavesClassification() && projectile.getSourceActor() != null)
        {
            String sourceRole = fightCavesRole(projectile.getSourceActor().getName());
            if (sourceRole != null)
            {
                record.put("sourceFightCavesRole", sourceRole);
            }
        }
        record.put("x", projectile.getX());
        record.put("y", projectile.getY());
        record.put("z", projectile.getZ());
        return record;
    }

    private void recordPrayerTransitionIfChanged(List<String> currentPrayers)
    {
        if (!config.recordPrayerTransitions())
        {
            previousActivePrayers = new ArrayList<>(currentPrayers);
            prayerStateInitialized = true;
            return;
        }

        if (!prayerStateInitialized)
        {
            previousActivePrayers = new ArrayList<>(currentPrayers);
            prayerStateInitialized = true;
            return;
        }

        LinkedHashSet<String> previous = new LinkedHashSet<>(previousActivePrayers);
        LinkedHashSet<String> current = new LinkedHashSet<>(currentPrayers);
        if (previous.equals(current))
        {
            return;
        }

        List<String> activated = new ArrayList<>();
        for (String prayer : current)
        {
            if (!previous.contains(prayer))
            {
                activated.add(prayer);
            }
        }

        List<String> deactivated = new ArrayList<>();
        for (String prayer : previous)
        {
            if (!current.contains(prayer))
            {
                deactivated.add(prayer);
            }
        }

        Map<String, Object> event = base("prayer_state_changed");
        event.put("activated", activated);
        event.put("deactivated", deactivated);
        event.put("activePrayers", currentPrayers);
        event.put("protectionPrayer", activeProtectionPrayer(currentPrayers));
        event.put("player", compactPlayerContext());
        writeEvent(event);

        previousActivePrayers = new ArrayList<>(currentPrayers);
    }

    @Nullable
    private String activeProtectionPrayer(List<String> prayers)
    {
        if (prayers == null)
        {
            return null;
        }

        for (String prayer : prayers)
        {
            if ("PROTECT_FROM_MAGIC".equals(prayer)
                || "PROTECT_FROM_MISSILES".equals(prayer)
                || "PROTECT_FROM_MELEE".equals(prayer))
            {
                return prayer;
            }
        }
        return null;
    }

    private Map<String, Object> combatContext(
        List<Map<String, Object>> npcs,
        List<Map<String, Object>> projectiles,
        List<String> prayers)
    {
        Map<String, Object> context = new LinkedHashMap<>();
        Player player = client.getLocalPlayer();

        context.put("protectionPrayer", activeProtectionPrayer(prayers));
        context.put("activePrayers", prayers);
        context.put("player", compactPlayerContext());
        Map<String, Object> playerTarget = player == null ? null : actorReference(player.getInteracting());
        context.put("playerTarget", playerTarget);

        List<Map<String, Object>> combatNpcs = new ArrayList<>();
        int attackersOnPlayer = 0;
        int fightCavesHealers = 0;
        boolean jadPresent = false;

        if (npcs != null)
        {
            for (Map<String, Object> npc : npcs)
            {
                Object levelObj = npc.get("combatLevel");
                int combatLevel = levelObj instanceof Number ? ((Number) levelObj).intValue() : 0;
                if (combatLevel <= 0)
                {
                    continue;
                }

                Map<String, Object> threat = new LinkedHashMap<>();
                copyIfPresent(npc, threat,
                    "id", "index", "entityId", "worldViewId", "attack", "templateWorld", "name", "fightCavesRole", "combatLevel", "world", "local",
                    "distanceToPlayer", "areaDistanceToPlayer", "animation", "poseAnimation",
                    "orientation", "dead", "interacting", "inMeleeDistance",
                    "lineOfSightNpcToPlayer", "lineOfSightPlayerToNpc", "pathToPlayer");

                boolean targetsLocalPlayer = isActorReferenceLocalPlayer(npc.get("interacting"));
                threat.put("targetsLocalPlayer", targetsLocalPlayer);
                threat.put("targetedByLocalPlayer", sameNpcReference(playerTarget, npc));
                if (targetsLocalPlayer)
                {
                    attackersOnPlayer++;
                }

                Object role = npc.get("fightCavesRole");
                if ("JAD".equals(role))
                {
                    jadPresent = true;
                }
                else if ("HEALER".equals(role))
                {
                    fightCavesHealers++;
                }

                combatNpcs.add(threat);
            }
        }

        List<Map<String, Object>> incomingProjectiles = new ArrayList<>();
        if (projectiles != null)
        {
            for (Map<String, Object> projectile : projectiles)
            {
                if (!Boolean.TRUE.equals(projectile.get("targetsLocalPlayer")))
                {
                    continue;
                }
                Map<String, Object> incoming = new LinkedHashMap<>();
                copyIfPresent(projectile, incoming,
                    "id", "entityId", "attack", "observedSourceAttack", "sourcePoint", "targetPoint", "sourceActor", "targetActor",
                    "sourceFightCavesRole", "remainingCycles", "startCycle", "endCycle");
                incomingProjectiles.add(incoming);
            }
        }

        context.put("sceneCombatNpcCount", combatNpcs.size());
        context.put("attackersOnPlayer", attackersOnPlayer);
        context.put("multiThreat", attackersOnPlayer > 1);
        context.put("jadPresent", jadPresent);
        context.put("fightCavesHealerCount", fightCavesHealers);
        context.put("combatNpcs", combatNpcs);
        context.put("incomingProjectiles", incomingProjectiles);
        return context;
    }

    private boolean sameNpcReference(@Nullable Map<String, Object> actorRef, Map<String, Object> npcRecord)
    {
        if (actorRef == null || npcRecord == null || !"npc".equals(actorRef.get("actorType")))
        {
            return false;
        }
        return Objects.equals(actorRef.get("id"), npcRecord.get("id"))
            && Objects.equals(actorRef.get("index"), npcRecord.get("index"));
    }

    private boolean isActorReferenceLocalPlayer(Object value)
    {
        if (!(value instanceof Map))
        {
            return false;
        }
        Object actorType = ((Map<?, ?>) value).get("actorType");
        return "local_player".equals(actorType);
    }

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String... keys)
    {
        for (String key : keys)
        {
            if (source.containsKey(key))
            {
                target.put(key, source.get(key));
            }
        }
    }

    @Nullable
    private String fightCavesRole(@Nullable String npcName)
    {
        if (npcName == null)
        {
            return null;
        }

        String name = npcName.toLowerCase(Locale.ROOT);
        if (name.contains("tz-kih"))
        {
            return "BAT_PRAYER_DRAIN";
        }
        if (name.contains("tz-kek"))
        {
            return "BLOB_SPLITTER";
        }
        if (name.contains("tok-xil"))
        {
            return "RANGER";
        }
        if (name.contains("yt-mejkot"))
        {
            return "MELEE";
        }
        if (name.contains("ket-zek"))
        {
            return "MAGER";
        }
        if (name.contains("tztok-jad") || name.contains("tz-tok jad") || name.contains("jad"))
        {
            return "JAD";
        }
        if (name.contains("yt-hurkot"))
        {
            return "HEALER";
        }
        return null;
    }

    private String markerIntent(String label)
    {
        if (label == null)
        {
            return "unspecified";
        }

        switch (label)
        {
            case "IMPORTANT":
                return "general tactical decision/action";
            case "SAFESPOT":
                return "stable safespot or anchor reached";
            case "LURE":
                return "successful lure/manipulation result";
            case "MISTAKE":
                return "bad action/position; do not learn as correct";
            case "TARGET_PRIORITY":
                return "human target-priority decision";
            case "PRAYER_DECISION":
                return "human prayer decision/switch";
            case "HEALER_TAG":
                return "Jad healer tag/management decision";
            case "RECOVERY":
                return "successful recovery from unstable/bad state";
            default:
                return "custom marker";
        }
    }

    @Nullable
    private Map<String, Object> worldPointRecord(@Nullable WorldPoint point)
    {
        if (point == null)
        {
            return null;
        }

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("x", point.getX());
        map.put("y", point.getY());
        map.put("plane", point.getPlane());
        map.put("regionId", point.getRegionID());
        map.put("tileKey", DroRecorderSpatial.tileKey(point));
        return map;
    }

    @Nullable
    private Map<String, Object> localPointRecord(@Nullable LocalPoint point)
    {
        if (point == null)
        {
            return null;
        }

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("x", point.getX());
        map.put("y", point.getY());
        map.put("sceneX", point.getSceneX());
        map.put("sceneY", point.getSceneY());
        return map;
    }

    private void recordSceneSnapshotIfChanged()
    {
        String currentSceneKey = spatial.sceneKey();
        if (currentSceneKey == null || "no-scene".equals(currentSceneKey) || currentSceneKey.equals(lastSceneKey))
        {
            return;
        }

        lastSceneKey = currentSceneKey;
        List<Map<String, Object>> npcs = npcSnapshot();
        Map<String, Object> event = base("scene_snapshot");
        event.put("snapshot", spatial.fullSceneSnapshot(pendingBaseline == null ? "scene_changed" : pendingBaseline, npcs, true));
        event.put("groundItems", spatial.groundItems());
        event.put("graphicsObjects", graphicsSnapshot());
        event.put("activity", activityContext());
        pendingBaseline = null;
        writeEvent(event);
    }

    @Nullable
    private Map<String, Object> selectedSceneTile()
    {
        if (client.getSelectedSceneTile() == null)
        {
            return null;
        }
        return worldPointRecord(client.getSelectedSceneTile().getWorldLocation());
    }

    private String safeClientBuild()
    {
        try
        {
            return client.getBuildID();
        }
        catch (Exception ignored)
        {
            return "unknown";
        }
    }

    private void observeClock()
    {
        observedTick = client.getTickCount();
        observedCycle = client.getGameCycle();
    }

    private long npcIdentity(NPC npc)
    {
        return npcIdentities.computeIfAbsent(npc, n -> ++nextIdentity);
    }

    private Map<String, Object> templatePoint(LocalPoint point, int plane)
    {
        return point == null || client.getWorldView(point.getWorldView()) == null ? null
            : worldPointRecord(WorldPoint.fromLocalInstance(client, point, plane));
    }

    private List<Map<String, Object>> spotAnimations(Actor actor)
    {
        List<Map<String, Object>> result = new ArrayList<>();
        if (actor.getSpotAnims() == null) { return result; }
        for (ActorSpotAnim animation : actor.getSpotAnims())
        {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", animation.getId());
            entry.put("startCycle", animation.getStartCycle());
            entry.put("frame", animation.getFrame());
            entry.put("height", animation.getHeight());
            result.add(entry);
        }
        return result;
    }

    private Map<String, Object> attackClassification(Actor actor)
    {
        return DroRecorderAttack.animation(actor instanceof NPC ? fightCavesRole(actor.getName()) : null, actor.getAnimation());
    }

    private Map<String, Object> activityContext()
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("specialAttackEnergyRaw", client.getVarpValue(VarPlayer.SPECIAL_ATTACK_PERCENT));
        result.put("specialAttackEnabled", client.getVarpValue(VarPlayer.SPECIAL_ATTACK_ENABLED));
        result.put("attackStyleRaw", client.getVarpValue(VarPlayer.ATTACK_STYLE));
        int poison = client.getVarpValue(VarPlayer.POISON);
        result.put("poisonRaw", poison);
        result.put("poisonState", poison >= 1000000 ? "VENOM" : poison > 0 ? "POISON" : poison < 0 ? "IMMUNE" : "NONE");
        result.put("destinationLocal", localPointRecord(client.getLocalDestinationLocation()));
        Map<String, Object> bag = lootingBagBankContext(client);
        if (!bag.isEmpty()) result.put("lootingBagBank", bag);
        result.put("microbotPaused", Microbot.pauseAllScripts.get());
        result.put("aioInputHeld", false);
        result.put("humanInputHeld", net.runelite.client.plugins.microbot.util.input.InputArbiter.isHuman());
        Player player = client.getLocalPlayer();
        if (player != null)
        {
            LocalPoint destination = client.getLocalDestinationLocation();
            result.put("destinationWorld", destination == null ? null : worldPointRecord(
                WorldPoint.fromLocal(player.getWorldView(), destination.getX(), destination.getY(), player.getWorldLocation().getPlane())));
        }
        return result;
    }

    /** Capture only bag-related interface evidence; never include unrelated menu targets. */
    static Map<String, Object> lootingBagBankContext(Client client)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!client.isClientThread()) return result;
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        Item[] items = inventory == null ? null : inventory.getItems();
        if (items == null) return result;
        for (int slot = 0; slot < items.length; slot++)
        {
            Item item = items[slot];
            if (item == null || (item.getId() != 11941 && item.getId() != 22586)) continue;
            int containerId = net.runelite.api.gameval.InterfaceID.Bankside.ITEMS;
            Widget bank = client.getWidget(net.runelite.api.gameval.InterfaceID.Bankmain.UNIVERSE);
            boolean open = bank != null && !bank.isHidden();
            Widget container = open ? client.getWidget(containerId) : null;
            Widget cell = container == null || container.isHidden() ? null : container.getChild(slot);
            result.put("itemId", item.getId());result.put("slot", slot);result.put("bankOpen", open);
            result.put("widgetItemId", cell == null ? null : cell.getItemId());
            result.put("widgetActions", cell == null ? null : cell.getActions());
            Widget panel = client.getWidget(net.runelite.api.gameval.InterfaceID.Bankside.LOOTINGBAG_CONTAINER);
            result.put("viewOpen", panel != null && !panel.isHidden());
            List<Map<String, Object>> menu = new ArrayList<>();
            MenuEntry[] entries = client.getMenuEntries();
            if (open && entries != null) for (MenuEntry entry : entries)
            {
                if (entry == null || entry.getParam0() != slot || entry.getParam1() != containerId || menu.size() >= 16) continue;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("option", entry.getOption());row.put("identifier", entry.getIdentifier());
                row.put("itemId", entry.getItemId());row.put("type", entry.getType() == null ? null : entry.getType().name());
                menu.add(row);
            }
            result.put("menu", menu);
            break;
        }
        return result;
    }

    private Map<String, Object> graphicsRecord(GraphicsObject graphic)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", graphic.getId());
        result.put("worldViewId", graphic.getWorldView().getId());
        result.put("local", localPointRecord(graphic.getLocation()));
        result.put("world", worldPointRecord(WorldPoint.fromLocal(graphic.getWorldView(),
            graphic.getLocation().getX(), graphic.getLocation().getY(), graphic.getLevel())));
        result.put("templateWorld", templatePoint(graphic.getLocation(), graphic.getLevel()));
        result.put("startCycle", graphic.getStartCycle());
        result.put("frame", graphic.getAnimationFrame());
        result.put("z", graphic.getZ());
        result.put("finished", graphic.finished());
        return result;
    }

    private List<Map<String, Object>> graphicsSnapshot()
    {
        List<Map<String, Object>> result = new ArrayList<>();
        if (!config.recordCombatEvents()) { return result; }
        Player player = client.getLocalPlayer();
        if (player != null && player.getWorldView() != null)
        {
            for (GraphicsObject graphic : player.getWorldView().getGraphicsObjects()) { result.add(graphicsRecord(graphic)); }
        }
        return result;
    }

    void recordGraphic(GraphicsObject graphic)
    {
        if (isCaptureHeld() || !config.recordCombatEvents()) { return; }
        Map<String, Object> event = base("graphics_object_created");
        event.put("graphic", graphicsRecord(graphic));
        writeEvent(event);
    }

    void recordActorEvent(String type, Actor actor)
    {
        if (isCaptureHeld() || !config.recordCombatEvents()) { return; }
        Map<String, Object> event = base(type);
        event.put("actor", actorRecord(actor));
        writeEvent(event);
    }

    void recordNpcChanged(NpcChanged changed)
    {
        spatial.invalidatePaths();
        if (isCaptureHeld() || !config.recordNpcEvents()) { return; }
        Map<String, Object> event = base("npc_changed");
        event.put("oldId", changed.getOld() == null ? null : changed.getOld().getId());
        event.put("npc", npcRecord(changed.getNpc()));
        writeEvent(event);
    }

    void recordGroundItem(String type, Tile tile, TileItem item, int oldQuantity, int newQuantity)
    {
        if (isCaptureHeld()) { return; }
        Map<String, Object> event = base(type);
        event.put("id", item.getId());
        event.put("oldQuantity", oldQuantity);
        event.put("quantity", newQuantity);
        event.put("world", worldPointRecord(tile.getWorldLocation()));
        event.put("local", localPointRecord(tile.getLocalLocation()));
        event.put("templateWorld", templatePoint(tile.getLocalLocation(), tile.getPlane()));
        event.put("worldViewId", tile.getLocalLocation().getWorldView());
        writeEvent(event);
    }

    void recordStat(StatChanged changed)
    {
        if (isCaptureHeld()) { return; }
        Map<String, Object> event = base("stat_changed");
        event.put("skill", changed.getSkill().name());
        event.put("xp", changed.getXp());
        event.put("level", changed.getLevel());
        event.put("boostedLevel", changed.getBoostedLevel());
        writeEvent(event);
    }

    void recordVarbit(VarbitChanged changed)
    {
        if (isCaptureHeld() || client.getGameState() != GameState.LOGGED_IN) { return; }
        // Read actual prayer state on every relevant client update, including same-tick switches.
        recordPrayerTransitionIfChanged(activePrayers());
        String modifier = DroRecorderColosseumModifiers.name(changed.getVarbitId());
        if (modifier != null)
        {
            Map<String, Object> event = base("colosseum_modifier_changed");
            event.put("varbitId", changed.getVarbitId());
            event.put("modifier", modifier);
            event.put("value", changed.getValue());
            event.put("modifiers", DroRecorderColosseumModifiers.snapshot(client::getVarbitValue));
            writeEvent(event);
            return;
        }
        int varp = changed.getVarpId();
        boolean relevant = varp == VarPlayer.SPECIAL_ATTACK_PERCENT || varp == VarPlayer.SPECIAL_ATTACK_ENABLED
            || varp == VarPlayer.POISON || varp == VarPlayer.ATTACK_STYLE;
        for (Prayer prayer : Prayer.values()) { relevant |= prayer.getVarbit() == changed.getVarbitId(); }
        if (!relevant) { return; }
        Map<String, Object> event = base("activity_var_changed");
        event.put("varpId", varp);
        event.put("varbitId", changed.getVarbitId());
        event.put("value", changed.getLongValue());
        event.put("activity", activityContext());
        writeEvent(event);
    }

    void recordGameMessage(ChatMessage message)
    {
        if (isCaptureHeld()) { return; }
        if (message.getType() != ChatMessageType.GAMEMESSAGE && message.getType() != ChatMessageType.SPAM) { return; }
        Map<String, Object> event = base("game_message");
        event.put("messageType", message.getType().name());
        event.put("message", message.getMessage());
        writeEvent(event);
        int wave = DroRecorderColosseumModifiers.wave(net.runelite.client.util.Text.removeTags(message.getMessage()));
        if (wave < 0) { return; }
        Map<String, Integer> modifiers = DroRecorderColosseumModifiers.snapshot(client::getVarbitValue);
        if (!DroRecorderColosseumModifiers.any(modifiers)) { return; }
        Map<String, Object> snapshot = base("colosseum_modifiers");
        snapshot.put("wave", wave);
        snapshot.put("modifiers", modifiers);
        writeEvent(snapshot);
    }

    private Map<String, Object> resolveMenuTarget(MenuOptionClicked click)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        int clickedView = click.getMenuEntry().getWorldViewId();
        result.put("worldViewId", clickedView);
        String action = click.getMenuAction() == null ? "" : click.getMenuAction().name();
        if (action.startsWith("NPC_") || action.equals("WIDGET_TARGET_ON_NPC"))
        {
            Microbot.getRs2NpcCache().getStream().filter(n -> n.getIndex() == click.getId() && n.getWorldView().getId() == clickedView).findFirst()
                .ifPresent(n -> result.put("npc", npcRecord(n.getNpc())));
        }
        if (action.contains("OBJECT") || action.contains("GROUND_ITEM") || action.equals("WALK"))
        {
            Player player = client.getLocalPlayer();
            if (player != null && player.getWorldView() != null)
            {
                WorldView view = client.getWorldView(clickedView);
                if (view == null) { return result; }
                int x = click.getParam0(), y = click.getParam1(), plane = player.getWorldLocation().getPlane();
                if (x >= 0 && y >= 0 && x < view.getSizeX() && y < view.getSizeY())
                {
                    result.put("world", worldPointRecord(new WorldPoint(view.getBaseX() + x, view.getBaseY() + y, plane)));
                    result.put("objects", spatial.objectsAt(view, x, y, plane, click.getId()));
                    result.put("id", click.getId());
                    result.put("templateWorld", templatePoint(LocalPoint.fromScene(x, y, view), plane));
                    if (action.contains("GROUND_ITEM")) { result.put("itemName", client.getItemDefinition(click.getId()).getName()); }
                }
            }
        }
        Widget widget = click.getWidget();
        if (widget != null)
        {
            result.put("widgetId", widget.getId());
            result.put("widgetIndex", widget.getIndex());
            result.put("itemId", click.getItemId());
            result.put("slot", click.getParam0());
        }
        return result;
    }

    private void recordDialogueIfChanged()
    {
        List<Map<String, Object>> dialogue = new ArrayList<>();
        for (WidgetInfo info : new WidgetInfo[]{WidgetInfo.DIALOG_NPC_TEXT, WidgetInfo.DIALOG_PLAYER_TEXT,
            WidgetInfo.DIALOG_SPRITE_TEXT, WidgetInfo.DIALOG_DOUBLE_SPRITE_TEXT, WidgetInfo.DIALOG_OPTION_OPTIONS})
        {
            captureDialogue(client.getWidget(info), dialogue, 0);
        }
        String current = gson.toJson(dialogue);
        if (current.equals(previousDialogue)) { return; }
        previousDialogue = current;
        Map<String, Object> event = base("dialogue_changed");
        event.put("widgets", dialogue);
        writeEvent(event);
    }

    private void captureDialogue(Widget widget, List<Map<String, Object>> result, int depth)
    {
        if (widget == null || widget.isHidden() || depth > 3 || result.size() >= 64) { return; }
        if (widget.getText() != null && !widget.getText().isEmpty())
        {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", widget.getId());
            entry.put("index", widget.getIndex());
            entry.put("text", widget.getText());
            result.add(entry);
        }
        Widget[] children = widget.getChildren();
        if (children != null) { for (Widget child : children) { captureDialogue(child, result, depth + 1); } }
    }

    private void recordLossIfChanged()
    {
        long dropped = writer.getDroppedLines();
        if (dropped == previousDropped) { return; }
        Map<String, Object> event = base("recording_gap");
        event.put("droppedSincePrevious", dropped - previousDropped);
        event.put("droppedByType", writer.getDroppedByType());
        previousDropped = dropped;
        writeEvent(event);
    }

    private void writeRaw(Map<String, Object> record)
    {
        if (isCaptureHeld())
        {
            return;
        }
        writer.writeRaw(record);
        rawCount.incrementAndGet();
    }

    private void writeEvent(Map<String, Object> event)
    {
        if (isCaptureHeld())
        {
            return;
        }
        writeEventForce(event);
    }

    private void writeEventForce(Map<String, Object> event)
    {
        writer.writeEvent(event);
        rawCount.incrementAndGet();
        eventCount.incrementAndGet();
    }
}
