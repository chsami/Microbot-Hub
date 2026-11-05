package net.runelite.client.plugins.microbot.fourtifystormenteddemons;

import net.runelite.client.plugins.microbot.PluginConstants;

import com.google.inject.Provides;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.IntSupplier;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Projectile;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.config.TormentedDemonsConfig;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.overlay.TormentedDemonsOverlay;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.util.action.ActionQueue;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.util.inventory.InventoryGainTracker;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.util.lifecycle.SessionLifecycle;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.util.navigation.Walking;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.util.task.FourtifysTask;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.util.timing.TickDelay;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.tasks.BankTask;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.tasks.CombatTask;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.tasks.DodgeTask;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.tasks.EmergencyTask;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.tasks.GearTask;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.tasks.LootTask;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.tasks.PrayerTask;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.tasks.SuppliesTask;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.tasks.TravelTask;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.CombatStyle;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.DemonFight;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.GearChoice;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.LootRule;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.TempleData;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.TripPhase;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.TripRules;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.TripState;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.util.action.DemonActions;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(name = "<html><font color=#f5dd42>Fourtifys Tormented Demons", configName = "fourtifyTormentedDemons",
                    description = "Fight Tormented Demons with prayer switching, fire bomb steps and Ferox Enclave banking", tags = {"fourtifys", "tormented", "demons", "pvm"}, enabledByDefault = PluginConstants.DEFAULT_ENABLED,
                    authors = {"Fourtifys"}, version = FourtifysTormentedDemonsPlugin.VERSION, minClientVersion = "2.6.43", isExternal = PluginConstants.IS_EXTERNAL,
                    iconUrl = "https://chsami.github.io/Microbot-Hub/FourtifysTormentedDemonsPlugin/assets/icon.png")
public class FourtifysTormentedDemonsPlugin extends Plugin
{
    public static final String VERSION = "1.0.0";
    private static final int FIGHT_GAP_TICKS = 3;
    private static final int KILL_MEMORY_TICKS = 10;
    @Inject
    private Client client;
    @Inject
    private ClientThread clientThread;
    @Inject
    private TormentedDemonsConfig config;
    @Inject
    private DemonActions actions;
    @Inject
    private OverlayManager overlays;
    @Inject
    private TormentedDemonsOverlay overlay;
    @Inject
    private Walking walking;
    @Inject
    private Notifier notifier;
    private final Random random = new Random();
    private final SessionLifecycle lifecycle = new SessionLifecycle();
    private final TickDelay delay = new TickDelay();
    private final InventoryGainTracker uniques = new InventoryGainTracker(InventoryGainTracker.Baseline.PREVIOUS_SNAPSHOT);
    private final InventoryGainTracker loot = new InventoryGainTracker(InventoryGainTracker.Baseline.PREVIOUS_SNAPSHOT);
    private final Set<Projectile> seenProjectiles = Collections.newSetFromMap(new WeakHashMap<>());
    private final DemonFight fight = new DemonFight();
    private TripState trip = new TripState();
    private Instant startTime;
    private String status = "Stopped";
    private int tick;
    private int tickGameCycle;
    private long observedRequests;
    private long kills;
    private boolean stopReported;
    private NPC lastKilled;
    /** The tick each demon last fought the player: a dying demon stops interacting before its death shows. */
    private final Map<NPC, Integer> fought = new HashMap<>();
    private Set<Integer> kept = Set.of();
    private EmergencyTask emergency;
    private DodgeTask dodge;
    private PrayerTask prayer;
    private SuppliesTask supplies;
    private GearTask gear;
    private LootTask looting;
    private CombatTask combat;
    private BankTask banking;
    private TravelTask travel;

    @Provides
    TormentedDemonsConfig provideConfig(ConfigManager manager)
    {
        return manager.getConfig(TormentedDemonsConfig.class);
    }

    @Provides
    Walking provideWalking(Client targetClient, ClientThread targetThread)
    {
        return new Walking(targetClient, targetThread, () -> lifecycle.isEnabled() && lifecycle.isStarted()
            && actions.getFailure() == null && trip.getPhase() != null);
    }

    @Override
    protected void startUp()
    {
        lifecycle.scheduleStart(clientThread, this::start);
    }

    @Override
    protected void shutDown()
    {
        lifecycle.scheduleStop(clientThread, this::stop);
        walking.cancel();
    }

    private void start()
    {
        stop();
        if (!lifecycle.isEnabled())
        {
            return;
        }
        if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null)
        {
            status = "Waiting for login";
            return;
        }
        if (!actions.inventoryLoaded())
        {
            status = "Waiting for inventory";
            return;
        }
        actions.newSession();
        trip = new TripState();
        Map<CombatStyle, Set<Integer>> sets = new EnumMap<>(CombatStyle.class);
        sets.put(CombatStyle.MAGIC, ids("Magic equipment IDs", config.magicGear()));
        sets.put(CombatStyle.RANGED, ids("Ranged equipment IDs", config.rangedGear()));
        sets.put(CombatStyle.MELEE, ids("Melee equipment IDs", config.meleeGear()));
        Set<Integer> keep = ids("Retained inventory IDs", config.keepItems());
        Map<Integer, Integer> restock = Map.of();
        try
        {
            restock = DemonActions.restock(config.restockItems());
        }
        catch (IllegalArgumentException invalid)
        {
            actions.fail("Additional supplies/ammunition: " + invalid.getMessage());
        }
        if (sets.values().stream().filter(ids -> !ids.isEmpty()).count() < 2)
        {
            actions.fail("Fill in at least two of Magic, Ranged and Melee equipment IDs");
        }
        IntSupplier ticks = this::getTick;
        emergency = new EmergencyTask(actions, config, trip, ticks);
        dodge = new DodgeTask(actions, fight, trip, ticks, Rs2Tile::getReachableTilesFromTile,
            () -> config.room().getCenter());
        prayer = new PrayerTask(actions, config, fight, trip, ticks, FourtifysTormentedDemonsPlugin::unlocked);
        supplies = new SuppliesTask(actions, config, fight, trip, ticks);
        gear = new GearTask(actions, fight, trip, ticks, sets);
        looting = new LootTask(actions, config, fight, trip, ticks, new LootRule(config.lootAlways(), config.lootMinimum()));
        combat = new CombatTask(client, actions, config, fight, trip, ticks);
        banking = new BankTask(actions, config, trip, walking, ticks, sets, keep, restock);
        travel = new TravelTask(actions, config, trip, walking, ticks);
        kept = Set.copyOf(banking.keptItems());
        trip.setStyle(GearChoice.worn(sets, actions::wearing));
        if (actions.getFailure() == null)
        {
            WorldPoint position = actions.position();
            boolean onRoute = TempleData.atScrollArrival(position) || TempleData.onChasmLedge(position, 1)
                || TempleData.onChasmLedge(position, 2);
            TripPhase phase = TripRules.startPhase(TempleData.atFerox(position), TempleData.inTemple(position), onRoute, leaveReason());
            if (phase == null)
            {
                actions.fail("Walk to Ferox Enclave or into the Ancient Guthixian Temple, then restart the plugin");
            }
            else if (actions.duelingRing() <= 0 && phase != TripPhase.BANKING)
            {
                actions.fail("Carry or wear a Ring of dueling, then restart the plugin");
            }
            else
            {
                trip.setPhase(phase);
                if (phase == TripPhase.LEAVING)
                {
                    trip.leave(leaveReason());
                }
            }
        }
        fight.reset();
        seenProjectiles.clear();
        stopReported = false;
        kills = 0;
        lastKilled = null;
        fought.clear();
        tick = 0;
        uniques.reset();
        loot.reset();
        travel.reset();
        observedRequests = actions.getRequestsDispatched();
        lifecycle.setStarted(true);
        startTime = Instant.now();
        status = "Starting";
        overlays.add(overlay);
    }

    private void stop()
    {
        lifecycle.setStarted(false);
        walking.cancel();
        startTime = null;
        delay.set(0);
        actions.reset();
        overlays.remove(overlay);
        status = "Stopped";
    }

    /** The setting's item IDs, or none after stopping with the setting's name and the format to use. */
    private Set<Integer> ids(String setting, String text)
    {
        try
        {
            return DemonActions.ids(text);
        }
        catch (IllegalArgumentException invalid)
        {
            actions.fail(setting + ": " + invalid.getMessage());
            return Set.of();
        }
    }

    private static boolean unlocked(Rs2PrayerEnum prayer)
    {
        if (prayer == Rs2PrayerEnum.PIETY)
        {
            return Rs2Prayer.isPietyUnlocked();
        }
        if (prayer == Rs2PrayerEnum.RIGOUR)
        {
            return Rs2Prayer.isRigourUnlocked();
        }
        return prayer == Rs2PrayerEnum.AUGURY && Rs2Prayer.isAuguryUnlocked();
    }

    private String leaveReason()
    {
        return TripRules.leaveReason(supplies.needsFood(), actions.count(config.foodId()), supplies.needsPrayer(),
            actions.count(TempleData.PRAYER_RESTORES), actions.freeSlots());
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() != GameState.LOGGED_IN)
        {
            walking.cancel();
            actions.reset();
            fight.reset();
            status = event.getGameState() == GameState.LOADING ? "Waiting for scene" : "Waiting for login";
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!TormentedDemonsConfig.GROUP.equals(event.getGroup()))
        {
            return;
        }
        if ("showOverlay".equals(event.getKey()))
        {
            return;
        }
        if (event.getKey() != null && event.getKey().startsWith("tickDelay"))
        {
            delay.set(0);
            return;
        }
        // The restart's stop() cancels walking and queued actions on the client thread.
        lifecycle.scheduleStart(clientThread, this::start);
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        if (!lifecycle.isStarted() || event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM)
        {
            return;
        }
        if (TempleData.BIND_MESSAGE.equals(event.getMessage()))
        {
            // Messages arrive with the next game tick, before its GameTick event.
            fight.observeBind(tick + 1);
        }
        else if (TempleData.QUEST_MESSAGE.equals(event.getMessage()))
        {
            actions.fail("The Guthixian temple teleport needs While Guthix Sleeps; complete the quest, then restart the plugin");
        }
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged event)
    {
        if (!lifecycle.isStarted() || !(event.getActor() instanceof NPC))
        {
            return;
        }
        NPC npc = (NPC) event.getActor();
        Player player = client.getLocalPlayer();
        if (!TempleData.DEMON_IDS.contains(npc.getId()) || player == null
            || npc != trip.getTarget() && npc.getInteracting() != player && !fought.containsKey(npc))
        {
            return;
        }
        if (npc.getAnimation() == TempleData.DEATH_ANIMATION)
        {
            if (npc != lastKilled)
            {
                lastKilled = npc;
                kills++;
                trip.killed(tick);
            }
            return;
        }
        fight.observeAttack(CombatStyle.fromAttackAnimation(npc.getAnimation()));
    }

    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event)
    {
        if (lifecycle.isStarted() && event.getActor() != null && event.getActor() == trip.getTarget()
            && event.getHitsplat().isMine())
        {
            trip.hitLanded(tick);
        }
    }

    @Subscribe
    public void onProjectileMoved(ProjectileMoved event)
    {
        Projectile projectile = event.getProjectile();
        Player player = client.getLocalPlayer();
        if (!lifecycle.isStarted() || projectile == null || player == null || !seenProjectiles.add(projectile))
        {
            return;
        }
        if (projectile.getId() == TempleData.BOMB_PROJECTILE)
        {
            // Bombs aimed at this player land within one tile of them; others belong to other fights.
            WorldPoint target = projectile.getTargetPoint();
            if (target != null && target.distanceTo(player.getWorldLocation()) <= 1)
            {
                fight.observeBomb(target, tick + 1);
            }
        }
        else if (projectile.getTargetActor() == player)
        {
            if (projectile.getId() == TempleData.BIND_PROJECTILE)
            {
                fight.observeBind(tick + 1);
            }
            else
            {
                fight.observeAttack(CombatStyle.fromProjectile(projectile.getId()));
            }
        }
    }

    @Subscribe
    public void onClientTick(ClientTick event)
    {
        if (!lifecycle.canDispatch(client))
        {
            return;
        }
        if (ActionQueue.withinWindow(client.getGameCycle(), tickGameCycle))
        {
            actions.dispatch();
            observeRequests();
        }
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        actions.nextTick();
        tickGameCycle = client.getGameCycle();
        Player player = client.getLocalPlayer();
        if (!lifecycle.isEnabled() || client.getGameState() != GameState.LOGGED_IN || player == null)
        {
            walking.cancel();
            return;
        }
        // The Break Handler and the Microbot pause control set this flag for every script.
        if (Microbot.pauseAllScripts.get())
        {
            walking.cancel();
            status = "Paused";
            return;
        }
        if (!lifecycle.isStarted())
        {
            start();
        }
        if (!lifecycle.isEnabled() || !lifecycle.isStarted())
        {
            return;
        }
        tick++;
        observeRequests();
        if (player.isDead())
        {
            walking.cancel();
            actions.fail("Player died; disable and recover before restarting");
        }
        if (actions.getFailure() != null)
        {
            walking.cancel();
            status = actions.getFailure();
            reportStop();
            return;
        }
        if (!actions.inventoryLoaded())
        {
            status = "Waiting for inventory";
            return;
        }
        observe(player);
        if (emergency.isValid())
        {
            run(emergency);
            return;
        }
        for (FourtifysTask task : new FourtifysTask[] {dodge, prayer})
        {
            if (task.isValid())
            {
                run(task);
                return;
            }
        }
        if (waitForDelay(player))
        {
            return;
        }
        for (FourtifysTask task : new FourtifysTask[] {supplies, gear, looting, combat, banking, travel})
        {
            if (task.isValid())
            {
                run(task);
                return;
            }
        }
        status = "Waiting";
    }

    /** Tells the player once per stop, in the chat and as a notification, since the overlay may be off or cut short. */
    private void reportStop()
    {
        if (stopReported)
        {
            return;
        }
        stopReported = true;
        String message = "[Tormented Demons] Stopped: " + status;
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null);
        notifier.notify(message);
    }

    private void run(FourtifysTask task)
    {
        task.execute();
        status = actions.getFailure() != null ? actions.getFailure() : task.getStatus();
    }

    /** Reads this tick's fight, trip and statistics before any task runs. */
    private void observe(Player player)
    {
        if (player.hasSpotAnim(TempleData.BIND_SPOT_ANIMATION))
        {
            fight.observeBind(tick);
        }
        fight.expire(tick);
        NPC engaged = null;
        fought.values().removeIf(at -> tick - at > KILL_MEMORY_TICKS);
        for (NPC npc : actions.demons())
        {
            if (npc.getInteracting() == player || player.getInteracting() == npc)
            {
                fought.put(npc, tick);
                if (engaged == null)
                {
                    engaged = npc;
                }
            }
        }
        trip.engage(engaged, tick);
        if (engaged == null && trip.ticksSinceEngaged(tick) >= FIGHT_GAP_TICKS)
        {
            fight.endFight();
        }
        gear.observe();
        WorldPoint position = player.getWorldLocation();
        if (trip.getPhase() == TripPhase.FIGHTING)
        {
            if (config.room().contains(position))
            {
                walking.cancel();
            }
            String reason = leaveReason();
            if (reason != null)
            {
                trip.leave(reason);
            }
        }
        boolean counting = TempleData.inTemple(position) && !actions.bankOpen();
        uniques.observe(actions.count(TempleData.TORMENTED_SYNAPSE) + actions.count(TempleData.BURNING_CLAW), counting);
        loot.observe(lootValue(), counting);
    }

    private int lootValue()
    {
        long total = 0;
        for (Item item : actions.items())
        {
            if (item != null && item.getId() > 0 && !kept.contains(item.getId()))
            {
                total += actions.price(item.getId()) * Math.max(0, item.getQuantity());
            }
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    private void observeRequests()
    {
        long requests = actions.getRequestsDispatched();
        if (requests != observedRequests)
        {
            observedRequests = requests;
            delayNextAction();
        }
    }

    boolean waitForDelay(Player player)
    {
        return delay.waitForDelay(player, this::setStatus);
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public void setTickDelay(int ticks)
    {
        delay.set(ticks);
    }

    public void delayNextAction()
    {
        setTickDelay(rollTickDelay());
    }

    int rollTickDelay()
    {
        return TickDelay.roll(random, config);
    }

    public int getTick()
    {
        return tick;
    }

    public Instant getStartTime()
    {
        return startTime;
    }

    public String getStatus()
    {
        return status;
    }

    public long getKills()
    {
        return kills;
    }

    public long getKillsPerHour()
    {
        if (startTime == null)
        {
            return 0;
        }
        long seconds = Math.max(1, Duration.between(startTime, Instant.now()).getSeconds());
        return kills * 3600 / seconds;
    }

    public long getLootValue()
    {
        return loot.getCollected();
    }

    public int getUniques()
    {
        return uniques.getCollected();
    }

    public int getTrips()
    {
        return trip.getTrips();
    }
}
