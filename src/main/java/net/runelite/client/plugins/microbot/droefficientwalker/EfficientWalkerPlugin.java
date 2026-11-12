package net.runelite.client.plugins.microbot.droefficientwalker;


import com.google.inject.Provides;
import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.widgets.Widget;
import net.runelite.api.worldmap.WorldMap;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Keybind;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.worldmap.WorldMapOverlay;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Efficient Walker RuneLite/Microbot plugin shell.
 *
 * <p>The plugin lifecycle, hotkeys, overlays, event forwarding, route preview/test menu entries,
 * Quest Helper bridge, cutscene handling and world-map coordinate conversion are readable source.
 * The walking engine itself is exposed by {@link EfficientWalker}.</p>
 */
@PluginDescriptor(
    minClientVersion = "2.6.55",
    name = PluginConstants.DRO + "Efficient Walker",
    description = "Efficient Microbot webwalker",
    tags = {"walking", "pathfinding", "microbot"},
    enabledByDefault = PluginConstants.DEFAULT_ENABLED,
    version = "1.12.69",
    iconUrl = "https://chsami.github.io/Microbot-Hub/EfficientWalkerPlugin/assets/icon.png",
    cardUrl = "https://chsami.github.io/Microbot-Hub/EfficientWalkerPlugin/assets/card.png",
    isExternal = PluginConstants.IS_EXTERNAL
)
public class EfficientWalkerPlugin extends Plugin
{
    private static final String DRY_RUN_WALK = "Dry-run walk";
    private static final String TEST_WALK = "Test walk";
    private static final String CANCEL_WALK = "Cancel walk";
    private static final String MENU_TARGET = "<col=00ff00>Efficient Walker</col>";
    private static final int WORLD_MAP_ROOT = 38993927;
    private static final int CUTSCENE_VARBIT = 542;

    @Inject
    private Client client;

    @Inject
    private EfficientWalker walker;

    @Inject
    private TirannwnObstacleSupport tirannwnObstacleSupport;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private SceneRouteOverlay overlay;

    @Inject
    private WorldMapRouteOverlay mapOverlay;

    @Inject
    private EfficientWalkerStatusOverlay statusOverlay;

    @Inject
    private WorldMapOverlay worldMapOverlay;

    @Inject
    private EfficientWalkerConfig config;

    @Inject
    private KeyManager keyManager;

    @Inject
    private PluginManager pluginManager;

    @Inject
    private ConfigManager configManager;

    @Inject
    private EventBus eventBus;

    private EfficientSailingModule sailing;
    private WorldPoint pendingWorldMapWalk;
    /** Package-private: {@link QuestWalkerAccess} reads it to tell whether quest automation owns the engine. */
    QuestStepNavigator questAutomation;
    private long nextWildernessThreatCheckAt;
    private long nextWildernessLogoutRetryAt;
    private WildernessCombatEscape wildernessCombatEscape;
    private final WildernessPvpActivity wildernessPlayerAttack = new WildernessPvpActivity();
    private final WildernessThreatOwnership wildernessThreatOwnership = new WildernessThreatOwnership();
    private final WalkerLocalTravel localTravel = new WalkerLocalTravel();
    private final AtomicReference<HotkeyAction> pendingHotkey = new AtomicReference<>();

    private final HotkeyListener bankHotkey = hotkey(this::bankKeybind, HotkeyAction.BANK);
    private final HotkeyListener questHotkey = hotkey(this::questKeybind, HotkeyAction.QUEST);
    private final HotkeyListener cancelHotkey = hotkey(this::cancelKeybind, HotkeyAction.CANCEL);
    private final HotkeyListener destinationHotkey = hotkey(() -> config.walkToNamedDestinationHotkey(), HotkeyAction.DESTINATION);

    private HotkeyListener hotkey(Supplier<Keybind> supplier, HotkeyAction action)
    {
        return new HotkeyListener(supplier)
        {
            @Override
            public void hotkeyPressed()
            {
                pendingHotkey.set(action);
            }
        };
    }

    @Subscribe
    public void onConfigChanged(net.runelite.client.events.ConfigChanged event)
    {
        if (walker != null && WalkerRouteSettings.affectsRoute(event.getGroup(), event.getKey()))
            walker.routeSettingsChanged();
        if (EfficientWalkerConfig.GROUP.equals(event.getGroup()) && "walkToNamedDestination".equals(event.getKey()))
            pendingHotkey.set(HotkeyAction.DESTINATION);
        if (EfficientWalkerConfig.GROUP.equals(event.getGroup()) && ("useMicrobotMouse".equals(event.getKey()) || "walkerMouseActivity".equals(event.getKey()) || "walkerMouseParkSide".equals(event.getKey())))
            WalkerMouseControl.configure(config);
    }

    /** Explicit save refresh: the owner defers replanning until its current transport is safe. */
    public void refreshRouteSettings()
    {
        if (walker != null) walker.routeSettingsChanged();
    }

    @Provides
    @Singleton
    EfficientWalkerConfig provideConfig(ConfigManager configManager)
    {
        EfficientWalkerConfig config = configManager.getConfig(EfficientWalkerConfig.class);
        // Import before PluginManager writes this new section's defaults.
        EfficientSailingModule.migrateSettings(configManager, config);
        return localTravel.effectiveConfig(wildernessThreatOwnership.effectiveConfig(config));
    }

    @Provides
    @Singleton
    EfficientWalker provideWalker(Client client, EfficientWalkerConfig config, ConfigManager manager)
    {
        MaxPohSupport.configure(client, config, manager);
        return new EfficientWalker(client, config);
    }

    public EfficientWalker getWalker()
    {
        return walker;
    }

    /** Scoped routing plus a combat-only threat response. Travel retains normal threat logout. */
    public WildernessCombatContext acquireWildernessContext(String owner)
    {
        return wildernessThreatOwnership.acquire(owner);
    }

    /** Release after the owned local route stops; closing the client cannot persist this override. */
    public Runnable acquireLocalTravelSettings()
    {
        return localTravel.acquire();
    }

    public String getWildernessCombatEscapeState()
    {
        return wildernessCombatEscape == null ? "IDLE" : wildernessCombatEscape.getState().name();
    }

    /** Confirmed PvP action within the last 10 game ticks, also available during travel/recovery. */
    public boolean isWildernessPlayerAttackActive()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            if (!wildernessThreatOwnership.isOwned()) { wildernessPlayerAttack.reset(); return false; }
            WildernessThreatPolicy.Snapshot threat = WildernessThreatPolicy.evaluate(client,
                config.wildernessThreatRadius(), config.wildernessUpperCombatBuffer());
            return wildernessPlayerAttack.observe(threat.getWildernessLevel() > 0,
                threat.hasAttackAction(), client.getTickCount());
        }).orElse(false);
    }

    public void acknowledgeWildernessCombatEscape()
    {
        if (wildernessCombatEscape != null) wildernessCombatEscape.reset();
    }

    /** Uses Walker's configured alert radius/buffer without exposing its configuration to scripts. */
    public WildernessThreatPolicy.Snapshot wildernessThreatSnapshot()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
            WildernessThreatPolicy.evaluate(client, config.wildernessThreatRadius(),
                config.wildernessUpperCombatBuffer())).orElseGet(WildernessThreatPolicy.Snapshot::empty);
    }

    /** Null when Sailing is disabled; acceptance does not mean observed arrival. */
    public SailingNavigationService getNavigationService()
    {
        return sailing == null ? null : sailing.getNavigationService();
    }

    public EfficientSailingController getNavigator()
    {
        return sailing == null ? null : sailing.getNavigator();
    }

    @Override
    protected void startUp()
    {
        // Guice reuses the Walker singleton, while shutDown clears its mouse binding.
        WalkerMouseControl.configure(config);
        wildernessCombatEscape = new WildernessCombatEscape(client, walker::cancel);
        pendingHotkey.set(null);
        nextWildernessThreatCheckAt = 0L;
        nextWildernessLogoutRetryAt = 0L;
        keyManager.registerKeyListener(bankHotkey);
        keyManager.registerKeyListener(questHotkey);
        keyManager.registerKeyListener(cancelHotkey);
        keyManager.registerKeyListener(destinationHotkey);
        overlayManager.add(overlay);
        overlayManager.add(mapOverlay);
        statusOverlay.reset();
        overlayManager.add(statusOverlay);
        sailing = new EfficientSailingModule(client, config, pluginManager, configManager,
            keyManager, overlayManager, worldMapOverlay, walker);
        sailing.start();
        eventBus.register(sailing);
    }

    @Override
    protected void shutDown()
    {
        if (sailing != null)
        {
            eventBus.unregister(sailing);
            sailing.close();
            sailing = null;
        }
        if (wildernessCombatEscape != null)
        {
            wildernessCombatEscape.close();
            wildernessCombatEscape = null;
        }
        wildernessThreatOwnership.clear();
        localTravel.clear();
        wildernessPlayerAttack.reset();
        nextWildernessThreatCheckAt = 0L;
        nextWildernessLogoutRetryAt = 0L;
        clearQuestAutomation();
        keyManager.unregisterKeyListener(bankHotkey);
        keyManager.unregisterKeyListener(questHotkey);
        keyManager.unregisterKeyListener(cancelHotkey);
        keyManager.unregisterKeyListener(destinationHotkey);
        bankHotkey.focusLost();
        questHotkey.focusLost();
        cancelHotkey.focusLost();
        destinationHotkey.focusLost();
        pendingHotkey.set(null);
        overlayManager.remove(overlay);
        overlayManager.remove(mapOverlay);
        overlayManager.remove(statusOverlay);
        statusOverlay.reset();
        pendingWorldMapWalk = null;
        walker.cancel("PLUGIN_SHUTDOWN");
        walker.shutdown();
    }

    /**
     * DroKBD-style Wilderness threat watcher. Client ticks are substantially faster than game
     * ticks, so throttle this to one evaluation every 100 ms without blocking RuneLite's client
     * thread. This preserves DroKBD's fast reaction while avoiding its background-thread sleep.
     */
    @Subscribe
    public void onClientTick(ClientTick event)
    {
        if (net.runelite.client.plugins.microbot.droefficientwalker.compat.DroLoginReadiness.shouldWaitForLogin()) return;
        long now = System.currentTimeMillis();
        if (now < nextWildernessThreatCheckAt)
        {
            return;
        }
        nextWildernessThreatCheckAt = now + 100L;

        boolean playerAttackActive = false;
        if (wildernessThreatOwnership.isOwned())
        {
            WildernessThreatPolicy.Snapshot threat = WildernessThreatPolicy.evaluate(client,
                config.wildernessThreatRadius(), config.wildernessUpperCombatBuffer());
            playerAttackActive = wildernessPlayerAttack.observe(threat.getWildernessLevel() > 0,
                threat.hasAttackAction(), client.getTickCount());
        }
        else wildernessPlayerAttack.reset();

        if (wildernessThreatOwnership.isCombatActive())
        {
            if (wildernessCombatEscape != null)
            {
                WildernessThreatPolicy.Snapshot threat = WildernessThreatPolicy.evaluate(client,
                    config.wildernessThreatRadius(), config.wildernessUpperCombatBuffer());
                wildernessCombatEscape.tick(threat, now, wildernessThreatOwnership.usesSouthEscape(),
                    wildernessThreatOwnership.prefersLogoutWhenOutOfCombat());
            }
            return;
        }
        if (wildernessCombatEscape != null
            && wildernessCombatEscape.getState() != WildernessCombatEscape.State.IDLE)
            wildernessCombatEscape.reset();

        if (!config.wildernessThreatLogout()
            || client.getGameState() != GameState.LOGGED_IN
            || client.getLocalPlayer() == null)
        {
            nextWildernessLogoutRetryAt = 0L;
            return;
        }

        WildernessThreatPolicy.Snapshot threat = WildernessThreatPolicy.evaluate(client,
            config.wildernessThreatRadius(), config.wildernessUpperCombatBuffer());
        if (threat.getWildernessLevel() <= 0)
        {
            nextWildernessLogoutRetryAt = 0L;
            return;
        }

        // Preserve Walker's response once PvP has already started: stop movement without
        // repeatedly issuing a logout that combat will reject.
        if (threat.isPvpCombat()
            && (!wildernessThreatOwnership.allowsQuietPvpResume() || playerAttackActive))
        {
            walker.cancel("PVP_COMBAT");
            Microbot.status = "PvP combat - Efficient Walker stopped";
            return;
        }
        boolean presenceSuppressed = wildernessThreatOwnership.suppressesNearbyPlayerLogout();
        if (threat.isAttackCapablePlayerNearby() && !presenceSuppressed && Microbot.getEventBus() != null)
        {
            // The activity may start inside its course before the routing adapter has
            // acquired its lease. Query the live activity before issuing any logout.
            var check = new net.runelite.client.plugins.microbot.droefficientwalker.compat.WildernessPlayerPresenceLogoutCheck();
            Microbot.getEventBus().post(check);
            presenceSuppressed = check.isSuppressed();
        }
        if (!threat.isAttackCapablePlayerNearby() || presenceSuppressed)
        {
            nextWildernessLogoutRetryAt = 0L;
            return;
        }

        // NPC combat makes logout unavailable. Keep travel/recovery moving until combat clears.
        if (!WildernessThreatPolicy.mayAttemptTravelLogout(Rs2Player.isInCombat())) return;

        if (now < nextWildernessLogoutRetryAt)
        {
            return;
        }

        walker.pauseForWildernessLogout(now);
        Microbot.status = "Wilderness threat detected - logging out";
        Rs2Player.logout();
        // DroKBD used a blocking 500-800 ms sleep. Keep the same retry spacing without
        // sleeping on RuneLite's client thread.
        nextWildernessLogoutRetryAt = now + 500L + (long) (Math.random() * 301L);
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (net.runelite.client.plugins.microbot.droefficientwalker.compat.DroLoginReadiness.shouldWaitForLogin()) return;
        statusOverlay.refresh();
        // During login/world transitions RuneLite can briefly report a game tick before the
        // local player object has been installed.  Do not forward that transient tick into the
        // preserved walker/quest engine, which expects a non-null local player.
        if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null)
        {
            return;
        }

        MaxPohSupport.observe();
        if (client.getVarbitValue(CUTSCENE_VARBIT) != 0)
        {
            if (pendingHotkey.get() == HotkeyAction.CANCEL)
            {
                processHotkey();
                return;
            }

            if (questAutomation != null && !questAutomation.pause(client, walker))
            {
                questAutomation = null;
            }

            if (questAutomation != null || pendingHotkey.get() != null || walker.hasActiveExecutionRequest())
            {
                Microbot.status = "Waiting for the cutscene to finish. Cancel walk stops automation.";
            }

            if (questAutomation != null && QuestDialogueController.isDialogueOpen(client))
            {
                tickQuestAutomation();
            }
            return;
        }

        processHotkey();
        // A failed travel leg must still let the requested spellbook service report BLOCKED.
        walker.tickSpellbookPreparation();
        if (pendingWorldMapWalk != null)
        {
            Widget worldMap = client.getWidget(WORLD_MAP_ROOT);
            if (worldMap != null && !worldMap.isHidden())
            {
                closeWorldMap();
                return;
            }
            WorldPoint target = pendingWorldMapWalk;
            pendingWorldMapWalk = null;
            walker.walkTo(target);
        }
        if (walker.isWildernessTravelPaused()) {
            WildernessThreatPolicy.Snapshot threat = WildernessThreatPolicy.evaluate(client,
                config.wildernessThreatRadius(), config.wildernessUpperCombatBuffer());
            walker.resumeWildernessTravel(threat.isAttackCapablePlayerNearby() || threat.isPvpCombat(),
                System.currentTimeMillis());
            if (walker.isWildernessTravelPaused()) return;
        }
        if (tirannwnObstacleSupport.tick())
        {
            return;
        }
        if (walker.getStatus() == EfficientWalker.Status.BLOCKED && !walker.hasPendingRouteSettings())
        {
            clearQuestAutomation();
            pendingWorldMapWalk = null;
            return;
        }

        if (handleBlockingMessage(TravelFailureMessages.findVisibleActionFailureText(client)))
        {
            return;
        }

        tickQuestAutomation();
        walker.tick();

        if (walker.getStatus() == EfficientWalker.Status.BLOCKED)
        {
            clearQuestAutomation();
            pendingWorldMapWalk = null;
            return;
        }

    }

    private void processHotkey()
    {
        HotkeyAction action = pendingHotkey.getAndSet(null);
        if (action == null)
        {
            return;
        }

        WalkerDestination selected = action == HotkeyAction.DESTINATION ? config.namedDestination() : null;
        if (action == HotkeyAction.DESTINATION && (selected == null || selected.getWorldPoint() == null))
        {
            Microbot.status = "Choose a named destination in Efficient Walker settings first.";
            return;
        }

        if (sailing != null) sailing.onWalkerUserCommand();

        try
        {
            switch (action)
            {
                case CANCEL:
                    cancelEverything();
                    Microbot.status = "Walk cancelled.";
                    return;
                case BANK:
                    clearQuestAutomation();
                    pendingWorldMapWalk = null;
                    if (!walker.walkToNearestBank())
                    {
                        reportError("No reachable bank route was found.");
                    }
                    return;
                case QUEST:
                    startQuestStep();
                    return;
                case DESTINATION:
                    clearQuestAutomation();
                    pendingWorldMapWalk = null;
                    if (!walker.walkTo(selected.getWorldPoint()))
                        reportError("No reachable route to " + selected + " was found.");
                    return;
                default:
                    return;
            }
        }
        catch (IllegalStateException ex)
        {
            reportError(ex.getMessage());
        }
    }

    private void startQuestStep()
    {
        clearQuestAutomation();
        pendingWorldMapWalk = null;

        Object selectedQuest = QuestHelperBridge.getSelectedQuest(pluginManager);
        if (config.handleInteractions())
        {
            questAutomation = new QuestStepNavigator(selectedQuest, walker);
            Microbot.status = "Quest automation started.";
            tickQuestAutomation();
            return;
        }

        QuestHelperBridge.QuestTarget target = QuestHelperBridge.resolveQuestTarget(pluginManager, client);
        if (target.npcStep || target.objectStep)
        {
            List<WorldPoint> approach;
            if (target.interactionLocation == null)
            {
                approach = SceneInteractionTiles.tilesAround(target.destination, target.objectSize);
            }
            else if (target.objectStep)
            {
                approach = SceneInteractionTiles.findApproachTiles(client, target.object);
            }
            else
            {
                approach = SceneInteractionTiles.findApproachTiles(client, target.interactionLocation);
            }

            if (approach.isEmpty() || !walker.followSuppliedRoute(approach))
            {
                throw new IllegalStateException("Cannot find a supported route beside the current quest target.");
            }
        }
        else if (!walker.walkTo(target.destination))
        {
            throw new IllegalStateException("Cannot find a supported route to the current quest step.");
        }
    }

    private void tickQuestAutomation()
    {
        if (questAutomation == null)
        {
            return;
        }

        if (!config.handleInteractions())
        {
            clearQuestAutomation();
            Microbot.status = "Quest automation stopped because Automate quest steps is disabled.";
            return;
        }

        try
        {
            if (!questAutomation.tickQuestStep(client, pluginManager, walker))
            {
                questAutomation = null;
            }
        }
        catch (IllegalStateException ex)
        {
            reportError(ex.getMessage());
        }
    }

    private void clearQuestAutomation()
    {
        if (questAutomation != null)
        {
            questAutomation.clearSelectedItem(client);
        }
        questAutomation = null;
    }

    private void reportError(String message)
    {
        cancelEverything();
        Microbot.status = "Efficient Walker: " + (message == null ? "Unknown walking error." : message);
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event)
    {
        if (questAutomation != null)
        {
            questAutomation.onWidgetGroupLoaded(event.getGroupId());
        }
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event)
    {
        if (event.getContainerId() == InventoryID.EQUIPMENT.getId()) walker.travelEquipmentChanged();
        if (event.getContainerId() == InventoryID.INVENTORY.getId())
        {
            walker.onInventoryChanged(event.getItemContainer());
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() != GameState.LOGGED_IN && event.getGameState() != GameState.LOADING)
        {
            statusOverlay.reset();
        }
        GameState state = event.getGameState();
        walker.observeWildernessLogoutState(state);
        if (state == GameState.LOGIN_SCREEN || state == GameState.LOGIN_SCREEN_AUTHENTICATOR) WalkerMouseControl.resetSession();
        if (state != GameState.LOGGED_IN)
        {
            pendingHotkey.set(null);
            nextWildernessLogoutRetryAt = 0L;
        }
        if (state != GameState.LOGGED_IN && state != GameState.LOADING)
        {
            clearQuestAutomation();
            walker.resetNetworkTransportSession(state == GameState.LOGIN_SCREEN
                || state == GameState.LOGIN_SCREEN_AUTHENTICATOR);
            walker.refreshTeleportInventory();
        }
    }

    private boolean handleBlockingMessage(String message)
    {
        if (client.getVarbitValue(CUTSCENE_VARBIT) != 0 || message == null)
        {
            return false;
        }

        boolean questStopped = questAutomation != null && questAutomation.isCurrentStepFailure(message, walker);
        if (!questStopped && !walker.handleActionFailureMessage(message))
        {
            return false;
        }

        clearQuestAutomation();
        pendingWorldMapWalk = null;
        if (questStopped)
        {
            walker.rejectCurrentAction(TravelFailureMessages.explainActionFailure(message));
        }
        return true;
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        if (event.getType() == ChatMessageType.DIALOG
            && QuetzalNetwork.isWhistleRechargeMessage(Text.removeTags(event.getMessage())))
        {
            walker.refreshTeleportInventory();
        }

        if (event.getType() == ChatMessageType.GAMEMESSAGE
            || event.getType() == ChatMessageType.SPAM
            || event.getType() == ChatMessageType.DIALOG)
        {
            String message = Text.removeTags(event.getMessage());
            walker.onTeleportChargeMessage(message);
            handleBlockingMessage(message);
        }
    }

    @Subscribe
    public void onMenuOpened(MenuOpened event)
    {
        // Entries are added only after a right-click has actually opened the context menu. Injecting them
        // while the menu is built makes a RUNELITE entry eligible to become the left-click action, so a
        // scripted "Walk here" press could start a Test walk route instead (Inferno, 2026-11-01).
        Point mouse = client.getMouseCanvasPosition();
        Widget worldMap = client.getWidget(WORLD_MAP_ROOT);
        if (worldMap != null && !worldMap.isHidden() && mouse != null
            && worldMap.getBounds().contains(mouse.getX(), mouse.getY()))
        {
            // This keeps ordinary world-map left clicks and click-drag panning completely native.
            WorldPoint destination = worldMapPoint(mouse);
            if (destination == null)
            {
                Microbot.status = "Efficient Walker: Cannot resolve this world-map destination.";
                return;
            }
            addWalkMenuEntries(destination, true, -1);
            return;
        }

        MenuEntry[] entries = client.getMenuEntries();
        for (int i = 0; i < entries.length; i++)
        {
            MenuEntry source = entries[i];
            if (source.getType() != MenuAction.WALK)
            {
                continue;
            }
            WorldView worldView = client.getWorldView(source.getWorldViewId());
            Tile tile = worldView == null ? null : worldView.getSelectedSceneTile();
            // Directly above "Walk here", where menu construction used to place them.
            addWalkMenuEntries(tile == null ? null : tile.getWorldLocation(), false, i + 1);
            return;
        }
    }

    /** Adds Dry run, Test walk and (while routing) Cancel walk, bottom to top, starting at {@code index} (-1: top). */
    private void addWalkMenuEntries(WorldPoint point, boolean worldMap, int index)
    {
        if (point == null || alreadyHasWalkerMenuEntry())
        {
            return;
        }

        int position = index;
        position = addWalkerEntry(position, DRY_RUN_WALK, worldMap, () -> dryRun(point));
        position = addWalkerEntry(position, TEST_WALK, worldMap, () -> testWalk(point, worldMap));
        if (walker.hasActiveExecutionRequest())
        {
            addWalkerEntry(position, CANCEL_WALK, worldMap, this::cancelEverything);
        }
    }

    /** Returns the index directly above the new entry. */
    private int addWalkerEntry(int index, String option, boolean worldMap, Runnable action)
    {
        client.createMenuEntry(index)
            .setOption(option)
            .setTarget(MENU_TARGET)
            .setType(MenuAction.RUNELITE)
            .setDeprioritized(worldMap)
            .onClick(entry -> action.run());
        return index < 0 ? index : index + 1;
    }

    private void cancelEverything()
    {
        if (sailing != null) sailing.onWalkerUserCommand();
        clearQuestAutomation();
        pendingHotkey.set(null);
        pendingWorldMapWalk = null;
        walker.cancel("MANUAL_STOP");
    }

    private void testWalk(WorldPoint target, boolean worldMap)
    {
        if (sailing != null) sailing.onWalkerUserCommand();
        clearQuestAutomation();
        if (!worldMap)
        {
            pendingWorldMapWalk = null;
            walker.walkTo(target);
            return;
        }

        walker.cancel("ROUTE_REPLACEMENT");
        pendingWorldMapWalk = target;
    }

    private void dryRun(WorldPoint target)
    {
        if (sailing != null) sailing.onWalkerUserCommand();
        pendingWorldMapWalk = null;
        clearQuestAutomation();
        walker.previewRouteTo(target);
    }

    private void closeWorldMap()
    {
        Widget worldMap = client.getWidget(WORLD_MAP_ROOT);
        if (worldMap == null)
        {
            return;
        }

        Rectangle bounds = worldMap.getBounds();
        Point click = new Point(
            Math.max(0, bounds.x + Math.max(1, bounds.width - 12)),
            Math.max(0, bounds.y + 12));
        CanvasClickDispatcher.clickCanvas(Microbot.getClient(), click);
    }

    static String describePlanningFailure(EfficientWalker.PlanningFailure failure, WorldPoint start, WorldPoint target)
    {
        if (failure == EfficientWalker.PlanningFailure.TARGET_NOT_STANDABLE)
        {
            return "The selected target tile is not standable.";
        }
        if (failure == EfficientWalker.PlanningFailure.SEARCH_LIMIT)
        {
            return "Route search reached its safety limit before finding a path.";
        }
        if (target == null)
        {
            return "No supported route was found.";
        }
        return String.format("No supported route was found to %d, %d, %d.",
            target.getX(), target.getY(), target.getPlane());
    }

    private boolean alreadyHasWalkerMenuEntry()
    {
        return Arrays.stream(client.getMenuEntries())
            .map(MenuEntry::getOption)
            .map(Text::removeTags)
            .anyMatch(EfficientWalkerPlugin::isWalkerMenuOption);
    }

    private WorldPoint worldMapPoint(Point mouse)
    {
        WorldMap map = client.getWorldMap();
        if (map == null || map.getWorldMapData() == null)
        {
            return null;
        }

        Point mapPosition = map.getWorldMapPosition();
        if (mapPosition == null) return null;
        WorldPoint center = new WorldPoint(mapPosition.getX(), mapPosition.getY(), 0);
        Point centerGraphics = worldMapOverlay.mapWorldPointToGraphicsPoint(center);
        float zoom = map.getWorldMapZoom();
        if (centerGraphics == null || !Float.isFinite(zoom) || zoom <= 0.0f)
        {
            return null;
        }

        int dx = (int) ((mouse.getX() - centerGraphics.getX()) / zoom);
        int dy = (int) (-(mouse.getY() - centerGraphics.getY()) / zoom);
        return WalkerWorldMapDestination.resolve(client, center.dx(dx).dy(dy));
    }

    private static boolean isWalkerMenuOption(String option)
    {
        return DRY_RUN_WALK.equals(option) || TEST_WALK.equals(option) || CANCEL_WALK.equals(option);
    }

    private Keybind cancelKeybind()
    {
        return config.cancelWalkHotkey();
    }

    private Keybind questKeybind()
    {
        return config.walkToQuestStepHotkey();
    }

    private Keybind bankKeybind()
    {
        return config.walkToNearestBankHotkey();
    }

    private enum HotkeyAction
    {
        BANK,
        QUEST,
        DESTINATION,
        CANCEL
    }
}
