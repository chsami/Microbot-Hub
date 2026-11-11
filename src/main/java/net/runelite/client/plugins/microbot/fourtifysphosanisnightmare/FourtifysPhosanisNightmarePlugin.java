package net.runelite.client.plugins.microbot.fourtifysphosanisnightmare;

import net.runelite.client.plugins.microbot.PluginConstants;

import com.google.inject.Provides;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.IntSupplier;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.NPC;
import net.runelite.api.Actor;
import net.runelite.api.Projectile;
import net.runelite.api.Player;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.magic.Spell;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.config.PhosanisNightmareConfig;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.AttackStyle;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.DreamData;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.FlowerLayout;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.Hazards;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.LastStand;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.Loadout;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.LootRule;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.NightmareFight;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.Stage;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.TripPhase;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.TripRules;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.TripState;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.overlay.PhosanisNightmareOverlay;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.tasks.BankTask;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.tasks.CombatTask;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.tasks.DodgeTask;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.tasks.GearTask;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.tasks.LootTask;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.tasks.NextKillTask;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.tasks.PrayerTask;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.tasks.SuppliesTask;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.tasks.TravelTask;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.action.ActionQueue;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.action.MouseSpeed;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.action.NightmareActions;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.inventory.InventoryGainTracker;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.lifecycle.SessionLifecycle;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.navigation.Walking;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.scene.ReachableTiles;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.task.FourtifysTask;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.timing.TickDelay;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

@PluginDescriptor(name = "<html><font color=#f5dd42>Fourtifys Phosani's Nightmare", configName = "fourtifyPhosanisNightmare",
                    description = "Fight Phosani's Nightmare with prayer switching, hazard dodging and Ferox Enclave banking", tags = {"fourtifys", "phosani", "nightmare", "pvm"}, enabledByDefault = PluginConstants.DEFAULT_ENABLED,
                    authors = {"Fourtifys"}, version = FourtifysPhosanisNightmarePlugin.VERSION, minClientVersion = "2.6.43", isExternal = PluginConstants.IS_EXTERNAL,
                    iconUrl = "https://chsami.github.io/Microbot-Hub/FourtifysPhosanisNightmarePlugin/assets/icon.png")
public class FourtifysPhosanisNightmarePlugin extends Plugin
{
    public static final String VERSION = "1.0.0";
    /** Tiles around the player searched for a safe step: far enough to cross the arena floor to where a surge lands. */
    private static final int STEP_REACH = 16;
    /** The run setting shows a toggle a tick later; resending sooner would switch it back. */
    private static final int RUN_RESEND_TICKS = 2;
    /** A clear line of sight between two areas on the current scene. */
    private BiPredicate<WorldArea, WorldArea> sight = (from, to) -> from.hasLineOfSightTo(this.client.getTopLevelWorldView(), to);
    /** The spellbook, Magic level and runes, the rune pouch's included, allow the spell. */
    private Predicate<Spell> castable = Rs2Magic::canCast;
    @Inject
    private Client client;
    @Inject
    private ClientThread clientThread;
    @Inject
    private PhosanisNightmareConfig config;
    @Inject
    private NightmareActions actions;
    @Inject
    private OverlayManager overlays;
    @Inject
    private PhosanisNightmareOverlay overlay;
    @Inject
    private Walking walking;
    @Inject
    private Notifier notifier;
    private final Random random = new Random();
    private final SessionLifecycle lifecycle = new SessionLifecycle();
    private final TickDelay delay = new TickDelay();
    private final InventoryGainTracker loot = new InventoryGainTracker(InventoryGainTracker.Baseline.PREVIOUS_SNAPSHOT);
    private final NightmareFight fight = new NightmareFight();
    private final Hazards hazards = new Hazards();
    private final LastStand lastStand = new LastStand();
    private final MouseSpeed mouse = new MouseSpeed(Microbot::getNaturalMouse);
    private WorldPoint bossTile;
    private int bossAnimation = -1;
    /** The tick she started to leave, while her reappearance is still to come, or -1. */
    private int leftAt = -1;
    private TripState trip = new TripState();
    private Instant startTime;
    private String status = "Stopped";
    private int tick;
    private int runRequestedAt = Integer.MIN_VALUE / 2;
    private Projectile parasiteShot;
    private int parasiteAnimation = -1;
    private int tickGameCycle;
    private long observedRequests;
    private long kills;
    private boolean stopReported;
    /** This session holds Break Handler breaks. */
    private boolean breaksHeld;
    private Set<Integer> kept = Set.of();
    /** The item settings, shared with the tasks and refilled in place when a setting changes. */
    private final Set<Integer> melee = new LinkedHashSet<>();
    private final Set<Integer> magic = new LinkedHashSet<>();
    private final Map<Loadout, Integer> weapons = new EnumMap<>(Loadout.class);
    private final Set<Integer> keep = new LinkedHashSet<>();
    private final Set<Integer> equipment = new LinkedHashSet<>();
    /** The current stop came from the settings. */
    private boolean settingsStop;
    private DodgeTask dodge;
    private PrayerTask prayer;
    private SuppliesTask supplies;
    private GearTask gear;
    private LootTask looting;
    private CombatTask combat;
    private NextKillTask nextKill;
    private BankTask banking;
    private TravelTask travel;

    @Provides
    PhosanisNightmareConfig provideConfig(ConfigManager manager)
    {
        return manager.getConfig(PhosanisNightmareConfig.class);
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
        readItemSettings();
        IntSupplier ticks = this::getTick;
        Function<WorldPoint, Collection<WorldPoint>> reachable = from -> ReachableTiles.around(client.getTopLevelWorldView(), from, STEP_REACH);
        dodge = new DodgeTask(actions, hazards, ticks, reachable);
        prayer = new PrayerTask(actions, config, fight, trip, ticks, FourtifysPhosanisNightmarePlugin::unlocked);
        supplies = new SuppliesTask(actions, config, fight, trip, ticks);
        gear = new GearTask(actions, trip, ticks, melee, magic, weapons);
        looting = new LootTask(actions, fight, trip, ticks, new LootRule(config.lootAlways(), config.lootMinimum()));
        combat = new CombatTask(actions, config, fight, hazards, trip, gear, ticks, reachable, sight, castable, weapons, this::tell);
        nextKill = new NextKillTask(actions, config, fight, trip, supplies, ticks, () -> looting.next() != null);
        banking = new BankTask(actions, config, trip, walking, ticks, supplies, equipment, keep, this::tell);
        travel = new TravelTask(actions, trip, walking, ticks);
        kept = Set.copyOf(banking.keptItems());
        if (actions.getFailure() == null)
        {
            WorldPoint position = actions.position();
            boolean inDream = actions.inInstance() && actions.boss() != null;
            TripPhase phase = TripRules.startPhase(DreamData.atFerox(position),
                DreamData.inSanctuary(position) && !actions.inInstance(), inDream);
            if (phase == null)
            {
                actions.fail("Walk to Ferox Enclave, the Sisterhood Sanctuary or into her dream, then restart the plugin");
            }
            else if (actions.duelingRing() <= 0 && phase != TripPhase.BANKING)
            {
                actions.fail("Carry or wear a Ring of dueling, then restart the plugin");
            }
            else
            {
                trip.setPhase(phase);
            }
        }
        fight.reset();
        hazards.reset();
        lastStand.reset();
        stopReported = false;
        kills = 0;
        tick = 0;
        // Ticks count from 0 again, so a run request from the last session would hold this one back.
        runRequestedAt = Integer.MIN_VALUE / 2;
        loot.reset();
        travel.reset();
        banking.reset();
        nextKill.reset();
        observedRequests = actions.getRequestsDispatched();
        lifecycle.setStarted(true);
        startTime = Instant.now();
        status = "Starting";
        overlays.add(overlay);
    }

    private void stop()
    {
        lifecycle.setStarted(false);
        releaseBreaks();
        walking.cancel();
        startTime = null;
        delay.set(0);
        actions.reset();
        mouse.fast(false);
        overlays.remove(overlay);
        status = "Stopped";
    }

    /**
     * Reads the item settings into the sets the tasks share, so a change applies without a new session. Remembers
     * whether these settings caused a stop, which a later change can then mend.
     */
    private void readItemSettings()
    {
        boolean failedBefore = actions.getFailure() != null;
        Set<Integer> readMelee = ids("Melee gear IDs", config.meleeGear());
        Set<Integer> readMagic = ids("Magic gear IDs", config.magicGear());
        Map<Loadout, Integer> readWeapons = new EnumMap<>(Loadout.class);
        readWeapons.put(Loadout.HUSK, weapon("Husk weapon ID", config.huskWeapon()));
        readWeapons.put(Loadout.PARASITE, weapon("Parasite weapon ID", config.parasiteWeapon()));
        readWeapons.put(Loadout.SLEEPWALKER, weapon("Sleepwalker weapon ID", config.sleepwalkerWeapon()));
        readWeapons.put(Loadout.SPEC, weapon("Spec weapon ID", config.specWeapon()));
        Set<Integer> readKeep = ids("Keep items", config.keepItems());
        if (readMelee.isEmpty() || readMagic.isEmpty())
        {
            actions.fail("Fill in Melee gear IDs and Magic gear IDs in the Equipment section");
        }
        melee.clear();
        melee.addAll(readMelee);
        magic.clear();
        magic.addAll(readMagic);
        weapons.clear();
        weapons.putAll(readWeapons);
        keep.clear();
        keep.addAll(readKeep);
        equipment.clear();
        equipment.addAll(melee);
        equipment.addAll(magic);
        weapons.values().stream().filter(id -> id > 0).forEach(equipment::add);
        settingsStop = !failedBefore && actions.getFailure() != null;
    }

    /** The setting's item IDs, or none after stopping with the setting's name and the format to use. */
    private Set<Integer> ids(String setting, String text)
    {
        try
        {
            return NightmareActions.ids(text);
        }
        catch (IllegalArgumentException invalid)
        {
            actions.fail(setting + ": " + invalid.getMessage());
            return Set.of();
        }
    }

    /** One item ID, or -1 when the setting is blank. */
    private int weapon(String setting, String text)
    {
        Set<Integer> ids = ids(setting, text);
        if (ids.size() > 1)
        {
            actions.fail(setting + ": enter one item ID");
        }
        return ids.isEmpty() ? -1 : ids.iterator().next();
    }

    private static boolean unlocked(Rs2PrayerEnum prayer)
    {
        if (prayer == Rs2PrayerEnum.PIETY)
        {
            return Rs2Prayer.isPietyUnlocked();
        }
        return prayer == Rs2PrayerEnum.AUGURY && Rs2Prayer.isAuguryUnlocked();
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() != GameState.LOGGED_IN)
        {
            walking.cancel();
            actions.reset();
            status = event.getGameState() == GameState.LOADING ? "Waiting for scene" : "Waiting for login";
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!PhosanisNightmareConfig.GROUP.equals(event.getGroup()))
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
        // Tasks read the other settings when they use them, so a change keeps the session, its fight and its counts.
        clientThread.invokeLater(() ->
        {
            if (!lifecycle.isStarted())
            {
                return;
            }
            if (actions.getFailure() != null)
            {
                // Only a stop the settings caused can be mended here; other stops wait for the plugin to be turned on again.
                if (settingsStop)
                {
                    lifecycle.scheduleStart(clientThread, this::start);
                }
                return;
            }
            readItemSettings();
            looting.setRule(new LootRule(config.lootAlways(), config.lootMinimum()));
            kept = Set.copyOf(banking.keptItems());
            // Items that join or leave the kept list are not loot, so the loot count starts from what is carried now.
            loot.suspend();
        });
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        if (!lifecycle.isStarted() || event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM)
        {
            return;
        }
        // Her messages come coloured, as in "<col=a53fff>The Nightmare has cursed you...</col>".
        String message = Text.removeTags(event.getMessage()).replaceAll("@mes_\\w+@", "");
        if (DreamData.CURSED.equals(message))
        {
            fight.setCursed(true);
        }
        else if (DreamData.CURSE_ENDED.equals(message))
        {
            fight.setCursed(false);
        }
        else if (DreamData.DROWSY.equals(message))
        {
            fight.setDrowsy(true);
        }
        else if (DreamData.DROWSY_ENDED.equals(message))
        {
            fight.setDrowsy(false);
        }
        else if (DreamData.WEAKENED.equals(message))
        {
            fight.setWeakened(true);
        }
        else if (message != null && message.startsWith(DreamData.KILL_COUNT))
        {
            kills++;
        }
        else if (message != null && DreamData.noCharges(message, magicNames()))
        {
            String weapon = DreamData.unchargedItem(message);
            String stop = weapon + " has no charges; charge it, then restart the plugin";
            if (trip.getPhase() == TripPhase.FIGHTING)
            {
                trip.leaveAndStop(weapon + " has no charges", stop);
            }
            else if (trip.getPhase() != TripPhase.LEAVING)
            {
                actions.fail(stop);
            }
        }
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged event)
    {
        if (lifecycle.isStarted() && event.getActor() == client.getLocalPlayer())
        {
            // Animations arrive with the next game tick, before its GameTick event.
            combat.playerAnimated(tick + 1);
            return;
        }
        if (!lifecycle.isStarted() || !(event.getActor() instanceof NPC) || !actions.inInstance())
        {
            return;
        }
        NPC npc = (NPC) event.getActor();
        if (npc.getId() == DreamData.WEAK_PARASITE || npc.getId() == DreamData.STRONG_PARASITE)
        {
            if (npc.getAnimation() == DreamData.PARASITE_RISE)
            {
                fight.parasiteRose();
            }
            else if (npc.getAnimation() != -1)
            {
                parasiteAnimation = npc.getAnimation();
            }
            return;
        }
        // Animations arrive with the next game tick, before its GameTick event.
        if (npc.getId() == DreamData.RANGED_HUSK)
        {
            fight.observeMissileHusk(npc.getAnimation(), tick + 1);
            return;
        }
        if (Stage.fromNpcId(npc.getId()) == null)
        {
            return;
        }
        AttackStyle style = AttackStyle.fromAnimation(npc.getAnimation());
        if (style != null)
        {
            fight.observeAttack(style, tick + 1);
        }
        fight.observeSpecial(npc.getAnimation());

    }

    /**
     * A risen parasite heals her shield, drains a totem or attacks the player, the last whenever it cannot help her.
     * Its projectile's target tells which. The projectile only moves after a delay, by when the animation it was
     * launched with may have ended, so the attack's style comes from the parasite's last animation.
     */
    @Subscribe
    public void onProjectileMoved(ProjectileMoved event)
    {
        Projectile projectile = event.getProjectile();
        Actor source = projectile.getSourceActor();
        if (!lifecycle.isStarted() || projectile == parasiteShot || !(source instanceof NPC)
            || projectile.getTargetActor() != client.getLocalPlayer())
        {
            return;
        }
        int id = ((NPC) source).getId();
        if (id == DreamData.WEAK_PARASITE || id == DreamData.STRONG_PARASITE)
        {
            parasiteShot = projectile;
            fight.observeParasiteAttack(parasiteAnimation);
        }
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        actions.clicked(event.getItemId());
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
        holdBreaks();
        if (ActionQueue.dead(client, player))
        {
            walking.cancel();
            actions.fail("Player died; collect your items from Sister Senga before restarting");
        }
        if (actions.getFailure() != null)
        {
            walking.cancel();
            mouse.fast(false);
            status = actions.getFailure();
            reportStop();
            return;
        }
        if (!actions.inventoryLoaded())
        {
            status = "Waiting for inventory";
            return;
        }
        observe();
        // The fight is tick-sensitive: a prayer switch shares the tick with the next action, and no random delay waits.
        boolean fighting = actions.inInstance() && fight.isFighting();
        mouse.fast(fighting);
        boolean prayed = fighting && prayer.isValid();
        if (prayed)
        {
            run(prayer);
        }
        if (fighting && !fight.isDrowsy() && !actions.running() && tick - runRequestedAt >= RUN_RESEND_TICKS)
        {
            // Every step off her hazards is timed for running, and her spores' drowsiness switches it off.
            int now = tick;
            actions.run(() -> runRequestedAt = now);
        }
        for (FourtifysTask task : fighting ? new FourtifysTask[] {dodge} : new FourtifysTask[] {dodge, prayer})
        {
            if (task.isValid())
            {
                run(task);
                return;
            }
        }
        if (!fighting && waitForDelay(player))
        {
            return;
        }
        for (FourtifysTask task : new FourtifysTask[] {supplies, gear, looting, combat, nextKill, banking, travel})
        {
            if (task.isValid())
            {
                run(task);
                return;
            }
        }
        if (!prayed)
        {
            status = "Waiting";
        }
    }

    /** Tells the player once per stop, in the chat and as a notification, since the overlay may be off or cut short. */
    private void reportStop()
    {
        if (stopReported)
        {
            return;
        }
        stopReported = true;
        tell("Stopped: " + status);
    }

    /** Tells the player in the chat and as a notification. */
    private void tell(String message)
    {
        String text = "[Phosani's Nightmare] " + message;
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", text, null);
        notifier.notify(text);
    }

    private void run(FourtifysTask task)
    {
        task.execute();
        status = actions.getFailure() != null ? actions.getFailure() : task.getStatus();
    }

    /**
     * A Break Handler break waits from the dream until the player is back at Ferox Enclave: a pause in her dream
     * would leave her hitting the player.
     */
    private void holdBreaks()
    {
        boolean hold = actions.getFailure() == null
            && (trip.getPhase() == TripPhase.FIGHTING || trip.getPhase() == TripPhase.LEAVING);
        if (hold != breaksHeld)
        {
            BreakHandlerScript.setLockState(hold);
            breaksHeld = hold;
        }
    }

    private void releaseBreaks()
    {
        if (breaksHeld)
        {
            BreakHandlerScript.setLockState(false);
            breaksHeld = false;
        }
    }

    /** Reads this tick's fight, hazards and statistics before any task runs. */
    private void observe()
    {
        if (actions.inInstance())
        {
            NPC boss = actions.boss();
            fight.observeBoss(boss == null ? null : Stage.fromNpcId(boss.getId()));
            hazards.expire(tick);
            hazards.observeBoss(boss == null ? null : boss.getWorldArea());
            hazards.observeStage(fight.getStage());
            hazards.observePlayer(actions.position());
            observeJump(boss);
            observeLastStand(boss);
            observeEmptySupplies();
            // A hit already on its way can kill a sleepwalker as she absorbs it, and it still counts for her blast.
            for (NPC walker : actions.npcs(Set.of(DreamData.SLEEPWALKER), true))
            {
                fight.observeSleepwalker(walker.getIndex(), walker.getAnimation() == DreamData.SLEEPWALKER_ABSORBED, walker.isDead());
            }
            for (Map.Entry<WorldPoint, Integer> rift : actions.graphicAges(DreamData.RIFT_GRAPHIC).entrySet())
            {
                hazards.observeRift(rift.getKey(), tick, rift.getValue());
            }
            observeObjects();
        }
        else
        {
            fight.reset();
            hazards.reset();
            lastStand.reset();
            bossTile = null;
            bossAnimation = -1;
            leftAt = -1;
        }
        if (actions.varbit(DreamData.PARASITE_VARBIT) == 0)
        {
            fight.setWeakened(false);
        }
        combat.observe();
        gear.observe();
        nextKill.observe();
        loot.observe(lootValue(), actions.inInstance() && !actions.bankOpen());
    }

    /** The names of the magic set's items, as far as they can be read. */
    private Set<String> magicNames()
    {
        Set<String> names = new HashSet<>();
        for (int id : gear.wanted(Loadout.MAGIC))
        {
            String name = actions.itemName(id);
            if (name != null)
            {
                names.add(name);
            }
        }
        return names;
    }

    /** With no food left in her final phase, the trip ends unless the player can be expected to outlast her. */
    private void observeLastStand(NPC boss)
    {
        if (boss == null || fight.getStage() != Stage.DESPERATION)
        {
            lastStand.reset();
            return;
        }
        lastStand.observe(actions.hitpoints(), boss.getHealthRatio(), boss.getHealthScale());
        if (trip.getPhase() == TripPhase.FIGHTING && supplies.food() == 0 && !lastStand.outlasts(actions.hitpoints()))
        {
            trip.leave("Out of food in her final phase");
        }
    }

    /**
     * Before her final phase, with no food left the trip ends once the Hitpoints are at the eating point, since hits no
     * prayer stops would go unhealed, or once no Prayer points and no restores are left either.
     */
    private void observeEmptySupplies()
    {
        if (trip.getPhase() != TripPhase.FIGHTING || !fight.isFighting() || fight.getStage() == Stage.DESPERATION
            || supplies.food() > 0)
        {
            return;
        }
        if (actions.hitpoints() <= config.eatAt())
        {
            trip.leave("Out of food");
        }
        else if (supplies.restoreDoses() == 0 && actions.prayerPoints() == 0)
        {
            trip.leave("Out of food and Prayer");
        }
    }

    /**
     * She leaves and reappears: at the start of a surge lane, or back at the centre for her flowers. Seen leaving,
     * her next tile at the start of a lane is where she reappears however near. Her own tile counts once her return animation shows, or
     * once she has had time to move while it is a lane start; she can be seen back later than that, so until then
     * the tile she left from is no lane. Unseen, a jump of more than a step is the reappearance. The surge's own charge is a jump too, made with its charge
     * animation, after which the lane is clear.
     */
    private void observeJump(NPC boss)
    {
        WorldPoint now = boss == null ? null : boss.getWorldLocation();
        if (now == null)
        {
            // She is gone between leaving and reappearing; where she left from stays the comparison.
            return;
        }
        int animation = boss.getAnimation();
        // A step she finishes as she starts to leave, such as before her blast, is no lane start.
        boolean laneStart = hazards.atLaneStart(boss.getWorldArea());
        if (leftAt >= 0 && (!now.equals(bossTile) && laneStart || animation == DreamData.SURGE_RETURN
            || tick - leftAt >= DreamData.REAPPEAR_TICKS && laneStart))
        {
            leftAt = -1;
            if (animation == DreamData.SURGE_CHARGE)
            {
                hazards.surgeLanded();
            }
            else
            {
                hazards.observeArrival(boss.getWorldArea(), boss.getOrientation(), tick);
            }
        }
        else if (leftAt >= 0 && tick - leftAt >= DreamData.REAPPEAR_WAIT_TICKS)
        {
            leftAt = -1;
        }
        else if (bossTile != null && now.distanceTo(bossTile) > 2)
        {
            if (boss.getAnimation() == DreamData.SURGE_CHARGE)
            {
                hazards.surgeLanded();
            }
            else
            {
                hazards.observeArrival(boss.getWorldArea(), boss.getOrientation(), tick);
            }
        }
        else if (animation == DreamData.SURGE_LEAVE && bossAnimation != DreamData.SURGE_LEAVE)
        {
            leftAt = tick;
        }
        bossTile = now;
        bossAnimation = animation;
    }

    /** Spores and flowers, read from one scan of the scene. */
    private void observeObjects()
    {
        Set<Integer> ids = new HashSet<>(DreamData.SPORES_LOCS);
        ids.addAll(DreamData.SAFE_FLOWERS);
        ids.addAll(DreamData.UNSAFE_FLOWERS);
        Set<WorldPoint> spores = new HashSet<>();
        Set<WorldPoint> safe = new HashSet<>();
        Set<WorldPoint> unsafe = new HashSet<>();
        for (TileObject object : actions.objects(ids))
        {
            int id = object.getId();
            (DreamData.SPORES_LOCS.contains(id) ? spores : DreamData.SAFE_FLOWERS.contains(id) ? safe : unsafe)
                .add(object.getWorldLocation());
        }
        hazards.setSpores(spores);
        hazards.setFlowers(FlowerLayout.read(safe, unsafe));
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

    /** The shield phase, 1 to 3, or 4 in her final phase; 0 outside a fight. */
    public int getPhase()
    {
        return fight.isFighting() ? fight.getPhase() : 0;
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

    public int getTrips()
    {
        return trip.getTrips();
    }
}
