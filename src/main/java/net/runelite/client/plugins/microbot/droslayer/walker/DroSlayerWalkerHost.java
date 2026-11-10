package net.runelite.client.plugins.microbot.droslayer.walker;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.microbot.droslayer.support.DroLoginReadiness;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.droslayer.support.WildernessPlayerPresenceLogoutCheck;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Dro Slayer's own walker host.
 *
 * <p>The walking engine in this package is Dro Slayer's copy of Efficient Walker 1.12.69. This host
 * does the routing work the Efficient Walker plugin did for Dro Slayer: it builds the engine, forwards
 * game events to it, and runs the Wilderness threat watcher and combat escape. It draws nothing.
 * It reads the saved {@code efficientwalker} settings so routing preferences stay the same, and never
 * writes them. It has no hotkeys, right-click walk entries, Quest Helper automation or Sailing UI.</p>
 */
@Singleton
public final class DroSlayerWalkerHost
{
    private static final int CUTSCENE_VARBIT = 542;

    private final Client client;
    private final ConfigManager configManager;
    private final EventBus eventBus;

    private final WildernessPvpActivity wildernessPlayerAttack = new WildernessPvpActivity();
    private final WildernessThreatOwnership wildernessThreatOwnership = new WildernessThreatOwnership();
    private final WalkerLocalTravel localTravel = new WalkerLocalTravel();

    private volatile EfficientWalker walker;
    private EfficientWalkerConfig config;
    private TirannwnObstacleSupport tirannwnObstacleSupport;
    private WildernessCombatEscape wildernessCombatEscape;
    private long nextWildernessThreatCheckAt;
    private long nextWildernessLogoutRetryAt;
    /** Set while this host asks other activities whether to suppress its own presence logout. */
    private boolean postingOwnPresenceCheck;

    @Inject
    DroSlayerWalkerHost(Client client, ConfigManager configManager, EventBus eventBus)
    {
        this.client = client;
        this.configManager = configManager;
        this.eventBus = eventBus;
    }

    public synchronized void start()
    {
        if (walker != null) return;
        EfficientWalkerConfig saved = configManager.getConfig(EfficientWalkerConfig.class);
        config = localTravel.effectiveConfig(wildernessThreatOwnership.effectiveConfig(saved));
        MaxPohSupport.configure(client, config, configManager);
        EfficientWalker created = new EfficientWalker(client, config);
        WalkerMouseControl.configure(config);
        tirannwnObstacleSupport = new TirannwnObstacleSupport(client, created);
        wildernessCombatEscape = new WildernessCombatEscape(client, created::cancel);
        nextWildernessThreatCheckAt = 0L;
        nextWildernessLogoutRetryAt = 0L;
        walker = created;
        eventBus.register(this);
    }

    public synchronized void stop()
    {
        EfficientWalker stopping = walker;
        if (stopping == null) return;
        eventBus.unregister(this);
        walker = null;
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
        stopping.cancel("PLUGIN_SHUTDOWN");
        stopping.shutdown();
    }

    /** Null while Dro Slayer is stopped. */
    public EfficientWalker getWalker()
    {
        return walker;
    }

    /** Scoped routing plus a combat-only threat response. Travel retains normal threat logout. */
    public WildernessCombatContext acquireWildernessContext(String owner)
    {
        return wildernessThreatOwnership.acquire(owner);
    }

    public String getWildernessCombatEscapeState()
    {
        WildernessCombatEscape escape = wildernessCombatEscape;
        return escape == null ? "IDLE" : escape.getState().name();
    }

    /** Confirmed PvP action within the last 10 game ticks, also available during travel/recovery. */
    public boolean isWildernessPlayerAttackActive()
    {
        if (walker == null) return false;
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
        WildernessCombatEscape escape = wildernessCombatEscape;
        if (escape != null) escape.reset();
    }

    /**
     * Dro Slayer's watcher answers threats while it owns the Wilderness; a separately running
     * Efficient Walker plugin must not also log out for the same nearby player.
     */
    @Subscribe
    public void onWildernessPlayerPresenceLogoutCheck(WildernessPlayerPresenceLogoutCheck check)
    {
        if (!postingOwnPresenceCheck && walker != null && wildernessThreatOwnership.isOwned())
            check.suppressNearbyPlayerLogout();
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        EfficientWalker current = walker;
        if (current != null && WalkerRouteSettings.affectsRoute(event.getGroup(), event.getKey()))
            current.routeSettingsChanged();
        if (EfficientWalkerConfig.GROUP.equals(event.getGroup()) && ("useMicrobotMouse".equals(event.getKey())
            || "walkerMouseActivity".equals(event.getKey()) || "walkerMouseParkSide".equals(event.getKey())))
            WalkerMouseControl.configure(config);
    }

    @Subscribe
    public void onClientTick(ClientTick event)
    {
        EfficientWalker current = walker;
        if (current == null || DroLoginReadiness.shouldWaitForLogin()) return;
        long now = System.currentTimeMillis();
        if (now < nextWildernessThreatCheckAt) return;
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

        // Once PvP has started, stop movement without repeatedly issuing a logout combat rejects.
        if (threat.isPvpCombat()
            && (!wildernessThreatOwnership.allowsQuietPvpResume() || playerAttackActive))
        {
            current.cancel("PVP_COMBAT");
            Microbot.status = "PvP combat - Dro Slayer walker stopped";
            return;
        }
        boolean presenceSuppressed = wildernessThreatOwnership.suppressesNearbyPlayerLogout();
        if (threat.isAttackCapablePlayerNearby() && !presenceSuppressed && Microbot.getEventBus() != null)
        {
            // Ask the live activities before issuing any logout.
            WildernessPlayerPresenceLogoutCheck check = new WildernessPlayerPresenceLogoutCheck();
            postingOwnPresenceCheck = true;
            try { Microbot.getEventBus().post(check); }
            finally { postingOwnPresenceCheck = false; }
            presenceSuppressed = check.isSuppressed();
        }
        if (!threat.isAttackCapablePlayerNearby() || presenceSuppressed)
        {
            nextWildernessLogoutRetryAt = 0L;
            return;
        }

        // NPC combat makes logout unavailable. Keep travel/recovery moving until combat clears.
        if (!WildernessThreatPolicy.mayAttemptTravelLogout(Rs2Player.isInCombat())) return;
        if (now < nextWildernessLogoutRetryAt) return;

        current.pauseForWildernessLogout(now);
        Microbot.status = "Wilderness threat detected - logging out";
        Rs2Player.logout();
        nextWildernessLogoutRetryAt = now + 500L + (long) (Math.random() * 301L);
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        EfficientWalker current = walker;
        if (current == null || DroLoginReadiness.shouldWaitForLogin()) return;
        // The local player can be briefly missing during login/world transitions.
        if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null) return;

        MaxPohSupport.observe();
        if (client.getVarbitValue(CUTSCENE_VARBIT) != 0)
        {
            if (current.hasActiveExecutionRequest())
                Microbot.status = "Waiting for the cutscene to finish.";
            return;
        }

        // A failed travel leg must still let the requested spellbook service report BLOCKED.
        current.tickSpellbookPreparation();
        if (current.isWildernessTravelPaused())
        {
            WildernessThreatPolicy.Snapshot threat = WildernessThreatPolicy.evaluate(client,
                config.wildernessThreatRadius(), config.wildernessUpperCombatBuffer());
            current.resumeWildernessTravel(threat.isAttackCapablePlayerNearby() || threat.isPvpCombat(),
                System.currentTimeMillis());
            if (current.isWildernessTravelPaused()) return;
        }
        if (tirannwnObstacleSupport.tick()) return;
        if (current.getStatus() == EfficientWalker.Status.BLOCKED && !current.hasPendingRouteSettings()) return;
        if (handleBlockingMessage(current, TravelFailureMessages.findVisibleActionFailureText(client))) return;
        current.tick();
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event)
    {
        EfficientWalker current = walker;
        if (current == null) return;
        if (event.getContainerId() == InventoryID.EQUIPMENT.getId()) current.travelEquipmentChanged();
        if (event.getContainerId() == InventoryID.INVENTORY.getId()) current.onInventoryChanged(event.getItemContainer());
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        EfficientWalker current = walker;
        if (current == null) return;
        GameState state = event.getGameState();
        current.observeWildernessLogoutState(state);
        if (state == GameState.LOGIN_SCREEN || state == GameState.LOGIN_SCREEN_AUTHENTICATOR) WalkerMouseControl.resetSession();
        if (state != GameState.LOGGED_IN) nextWildernessLogoutRetryAt = 0L;
        if (state != GameState.LOGGED_IN && state != GameState.LOADING)
        {
            current.resetNetworkTransportSession(state == GameState.LOGIN_SCREEN
                || state == GameState.LOGIN_SCREEN_AUTHENTICATOR);
            current.refreshTeleportInventory();
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        EfficientWalker current = walker;
        if (current == null) return;
        if (event.getType() == ChatMessageType.DIALOG
            && QuetzalNetwork.isWhistleRechargeMessage(Text.removeTags(event.getMessage())))
            current.refreshTeleportInventory();

        if (event.getType() == ChatMessageType.GAMEMESSAGE
            || event.getType() == ChatMessageType.SPAM
            || event.getType() == ChatMessageType.DIALOG)
        {
            String message = Text.removeTags(event.getMessage());
            current.onTeleportChargeMessage(message);
            handleBlockingMessage(current, message);
        }
    }

    private boolean handleBlockingMessage(EfficientWalker current, String message)
    {
        if (client.getVarbitValue(CUTSCENE_VARBIT) != 0 || message == null) return false;
        return current.handleActionFailureMessage(message);
    }
}
