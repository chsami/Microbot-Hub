package net.runelite.client.plugins.microbot.fourtifysagility;

import com.google.inject.Provides;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameState;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.fourtifysagility.config.AgilityConfig;
import net.runelite.client.plugins.microbot.fourtifysagility.course.AgilityCourse;
import net.runelite.client.plugins.microbot.fourtifysagility.course.CourseHandoff;
import net.runelite.client.plugins.microbot.fourtifysagility.course.PyramidBlocks;
import net.runelite.client.plugins.microbot.fourtifysagility.overlay.AgilityOverlay;
import net.runelite.client.plugins.microbot.fourtifysagility.tasks.CompleteLapTask;
import net.runelite.client.plugins.microbot.fourtifysagility.tasks.MovingTask;
import net.runelite.client.plugins.microbot.fourtifysagility.tasks.TurnInPyramidTopTask;
import net.runelite.client.plugins.microbot.fourtifysagility.util.action.AgilityActions;
import net.runelite.client.plugins.microbot.fourtifysagility.util.tracking.AgilityLapTracker;
import net.runelite.client.plugins.microbot.fourtifysagility.util.action.Spellbook;
import net.runelite.client.plugins.microbot.fourtifysagility.util.action.ActionQueue;
import net.runelite.client.plugins.microbot.fourtifysagility.util.lifecycle.SessionLifecycle;
import net.runelite.client.plugins.microbot.fourtifysagility.util.inventory.InventoryGainTracker;
import net.runelite.client.plugins.microbot.fourtifysagility.util.timing.TickDelay;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(name = "<html><font color=#f5dd42>Fourtifys Agility", configName = "fourtifyAgility",
                    description = "Agility course training", tags = {"fourtifys", "agility"}, enabledByDefault = false,
                    authors = {"Fourtifys"}, version = FourtifysAgilityPlugin.VERSION, minClientVersion = "2.6.43", isExternal = true,
                    iconUrl = "https://chsami.github.io/Microbot-Hub/FourtifysAgilityPlugin/assets/icon.png")
public class FourtifysAgilityPlugin extends Plugin
{
    public static final String VERSION = "1.0.0";
    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private ConfigManager configManager;
    @Inject private AgilityConfig config;
    @Inject private AgilityActions actions;
    @Inject private Spellbook spells;
    @Inject private OverlayManager overlays;
    @Inject private AgilityOverlay overlay;
    private final Random random = new Random();
    private final Map<NPC, Integer> pyramidBlockTimers = new HashMap<>();
    private final InventoryGainTracker marks = new InventoryGainTracker();
    private final AgilityLapTracker lapTracker = new AgilityLapTracker();
    private final InventoryGainTracker pyramidTops = new InventoryGainTracker(InventoryGainTracker.Baseline.PREVIOUS_SNAPSHOT);
    private final InventoryGainTracker wildernessTickets = new InventoryGainTracker(InventoryGainTracker.Baseline.PREVIOUS_SNAPSHOT);
    private final InventoryGainTracker arenaTickets = new InventoryGainTracker(InventoryGainTracker.Baseline.PREVIOUS_SNAPSHOT);
    private final InventoryGainTracker arenaVouchers = new InventoryGainTracker(InventoryGainTracker.Baseline.PREVIOUS_SNAPSHOT);
    private AgilityCourse lastRewardCourse;
    private boolean bankRecentlyOpen;
    private final TickDelay delay = new TickDelay();
    private String status = "Stopped";
    private Instant startTime;
    private final SessionLifecycle lifecycle = new SessionLifecycle();
    private final CourseHandoff handoff = new CourseHandoff();
    private LocalPoint lastLocalLocation;
    private int tickGameCycle;
    private MovingTask moving;
    private CompleteLapTask laps;
    private TurnInPyramidTopTask tops;

    @Provides
    AgilityConfig provideConfig(ConfigManager manager)
    {
        return manager.getConfig(AgilityConfig.class);
    }

    @Override
    protected void startUp()
    {
        marks.reset();
        lapTracker.reset();
        pyramidTops.reset();
        wildernessTickets.reset();
        arenaTickets.reset();
        arenaVouchers.reset();
        lastRewardCourse = null;
        bankRecentlyOpen = false;
        lifecycle.scheduleStart(clientThread, this::start);
    }

    @Override
    protected void shutDown()
    {
        lifecycle.scheduleStop(clientThread, this::stop);
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
        moving = new MovingTask(this, client, config, actions, spells);
        laps = new CompleteLapTask(this, client, config, actions, spells);
        tops = new TurnInPyramidTopTask(this, client, config, actions, spells);
        lifecycle.setStarted(true);
        observeMarks(client.getLocalPlayer());
        startTime = Instant.now();
        status = "Starting";
        for (NPC npc : client.getTopLevelWorldView().npcs())
        {
            if (PyramidBlocks.PYRAMID_BLOCK_IDS.contains(npc.getId()))
            {
                pyramidBlockTimers.put(npc, -1);
            }
        }
        overlays.add(overlay);
    }

    private void stop()
    {
        lifecycle.setStarted(false);
        if (laps != null)
        {
            laps.resetSeersTeleport();
        }
        handoff.reset();
        startTime = null;
        delay.set(0);
        lastLocalLocation = null;
        pyramidBlockTimers.clear();
        actions.reset();
        overlays.remove(overlay);
        status = "Stopped";
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (AgilityConfig.GROUP.equals(event.getGroup())
                && ("mode".equals(event.getKey()) || "seersTeleport".equals(event.getKey())
                    || "brimhavenLeave".equals(event.getKey())
                    || "brimhavenVoucherReward".equals(event.getKey())))
        {
            int generation = lifecycle.generation();
            clientThread.invokeLater(() ->
            {
                if (!lifecycle.isCurrent(generation))
                {
                    return;
                }
                actions.reset();
                if (laps != null)
                {
                    laps.resetSeersTeleport();
                }
            });
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() != GameState.LOGGED_IN)
        {
            if (laps != null)
            {
                laps.cancelBrimhavenMovement();
            }
            marks.suspend();
            lapTracker.suspend();
            suspendRewards();
            bankRecentlyOpen = false;
            if (event.getGameState() != GameState.LOADING)
            {
                handoff.reset();
                if (laps != null)
                {
                    laps.resetSeersTeleport();
                }
            }
            actions.reset();
            lastLocalLocation = null;
            pyramidBlockTimers.clear();
        }
    }

    @Subscribe
    public void onScriptPostFired(ScriptPostFired event)
    {
        if (lifecycle.isEnabled() && lifecycle.isStarted() && client.getGameState() == GameState.LOGGED_IN)
        {
            lapTracker.script(event.getScriptId(), activeCourse());
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        if (lifecycle.isEnabled() && lifecycle.isStarted() && laps != null
            && (event.getType() == ChatMessageType.GAMEMESSAGE || event.getType() == ChatMessageType.SPAM))
        {
            laps.seersTeleportMessage(event.getMessage());
        }
    }

    @Subscribe
    public void onClientTick(ClientTick event)
    {
        if (!lifecycle.canDispatch(client))
        {
            return;
        }
        Player player = client.getLocalPlayer();
        if (client.getGameCycle() == tickGameCycle + 29 && player != null)
        {
            lastLocalLocation = player.getLocalLocation();
        }
        if (ActionQueue.withinWindow(client.getGameCycle(), tickGameCycle))
        {
            actions.dispatch();
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
        observeMarks(player);
        bankRecentlyOpen = actions.visible(786432);
        if (actions.visible(13959168))
        {
            return;
        }
        updatePyramidBlocks();
        // Auto progress is temporarily disabled.
        // updateSelectedCourse();
        handoff.update(player.getWorldLocation(), config.area());
        lapTracker.observeCourse(activeCourse());
        laps.observeBrimhavenHint();
        if (laps.waitingForSeersTeleport(player))
        {
            return;
        }
        if (waitForDelay(player))
        {
            return;
        }
        if (selectCourse(player) && !(tops.isValid()
                && (actions.findNearest(5786) != null || actions.visible(14221312)
                    || actions.visible(15138816) || actions.visible(14352384)
                    || actions.visible(15007744))))
        {
            return;
        }
        if (moving.isValid())
        {
            moving.execute();
        }
        else if (!handoff.isFinishing() && tops.isValid())
        {
            status = tops.getStatus();
            tops.execute();
        }
        else
        {
            status = handoff.isFinishing()
                    ? "Finishing " + activeCourse().getDisplayName() + " before " + config.area().getDisplayName()
                    : laps.getStatus();
            laps.execute();
        }
    }

    boolean waitForDelay(Player player)
    {
        return delay.waitForDelay(player, this::setStatus);
    }

    private boolean selectCourse(Player player)
    {
        if (handoff.isFinishing())
        {
            return false;
        }
        if (CourseHandoff.isOnCourse(config.area(), player.getWorldLocation())
            || CourseHandoff.startObstacle(config.area(), player.getWorldLocation(),
                obstacle -> actions.obstacleInRange(obstacle.type, obstacle.id)) != null)
        {
            return false;
        }
        status = "Walk closer to first obstacle";
        return true;
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

    public void forEachReadyPyramidBlock(int npcId, Runnable action)
    {
        for (Map.Entry<NPC, Integer> block : pyramidBlockTimers.entrySet())
        {
            if (block.getKey().getId() == npcId && block.getValue() > 0)
            {
                action.run();
            }
        }
    }

    public Instant getStartTime()
    {
        return startTime;
    }

    public int getMarksOfGrace()
    {
        return marks.getCollected();
    }

    public int getPyramidTops()
    {
        return pyramidTops.getCollected();
    }

    public int getWildernessTickets()
    {
        return wildernessTickets.getCollected();
    }

    public int getArenaTickets()
    {
        return arenaTickets.getCollected();
    }

    public int getArenaVouchers()
    {
        return arenaVouchers.getCollected();
    }

    public int getCompletedLaps()
    {
        return lapTracker.getCompleted(activeCourse());
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event)
    {
        if (event.getContainerId() == 93 && lifecycle.isEnabled() && lifecycle.isStarted())
        {
            observeMarks(event.getItemContainer().count(11849), client.getLocalPlayer());
            observeRewards(event.getItemContainer(), client.getLocalPlayer());
        }
    }

    private void observeMarks(Player player)
    {
        ItemContainer inventory = client.getItemContainer(93);
        if (inventory == null)
        {
            marks.suspend();
            suspendRewards();
            return;
        }
        observeMarks(inventory.count(11849), player);
        observeRewards(inventory, player);
    }

    private void suspendRewards()
    {
        pyramidTops.suspend();
        wildernessTickets.suspend();
        arenaTickets.suspend();
        arenaVouchers.suspend();
    }

    private void observeRewards(ItemContainer inventory, Player player)
    {
        if (client.getGameState() != GameState.LOGGED_IN || player == null)
        {
            suspendRewards();
            return;
        }
        AgilityCourse course = activeCourse();
        if (course != lastRewardCourse)
        {
            suspendRewards();
            lastRewardCourse = course;
        }
        boolean eligible = CourseHandoff.isOnCourse(course, player.getWorldLocation())
            && !bankRecentlyOpen && !actions.visible(786432);
        pyramidTops.observe(inventory.count(6970), eligible && course == AgilityCourse.AGILITY_PYRAMID);
        wildernessTickets.observe(inventory.count(29460), eligible && course == AgilityCourse.WILDERNESS);
        arenaTickets.observe(inventory.count(29480), eligible && course == AgilityCourse.BRIMHAVEN);
        arenaVouchers.observe(inventory.count(29482), eligible && course == AgilityCourse.BRIMHAVEN);
    }

    private void observeMarks(int quantity, Player player)
    {
        if (client.getGameState() != GameState.LOGGED_IN || player == null)
        {
            marks.suspend();
            return;
        }
        boolean eligible = CourseHandoff.isOnCourse(activeCourse(), player.getWorldLocation())
                            && !bankRecentlyOpen && !actions.visible(786432);
        marks.observe(quantity, eligible);
    }

    public String getStatus()
    {
        return status;
    }

    public AgilityCourse getActiveCourse()
    {
        return activeCourse();
    }

    AgilityCourse activeCourse()
    {
        return handoff.activeCourse(config.area());
    }

    /* Auto progress is temporarily disabled.
    private void updateSelectedCourse()
    {
        if (config.autoProgress())
        {
            AgilityCourse course = CourseSelection.forLevel(client.getBoostedSkillLevel(Skill.AGILITY), client.getVarpValue(302));
            if (config.area() != course)
            {
                configManager.setConfiguration(AgilityConfig.GROUP, "mode", course);
            }
        }
    }

    */

    public boolean hasPlayerMoved()
    {
        Player player = client.getLocalPlayer();
        return player != null && lastLocalLocation != null && player.getLocalLocation() != null
                && !player.getLocalLocation().equals(lastLocalLocation);
    }

    int rollTickDelay()
    {
        return TickDelay.roll(random, config);
    }

    private void updatePyramidBlocks()
    {
        for (NPC npc : pyramidBlockTimers.keySet())
        {
            if (npc == null || npc.getId() == -1)
            {
                break;
            }
            WorldPoint point = npc.getWorldLocation();
            if (point.getPlane() == 1)
            {
                if (point.getX() != PyramidBlocks.PYRAMID_BLOCK_START_COORDINATES.get(npc.getId()))
                {
                    pyramidBlockTimers.put(npc, -1);
                    break;
                }
                if (pyramidBlockTimers.get(npc) == -1)
                {
                    pyramidBlockTimers.put(npc, 8);
                    break;
                }
            }
            if (point.getPlane() == 3)
            {
                if (point.getY() == 2847)
                {
                    pyramidBlockTimers.put(npc, -1);
                    break;
                }
                if (pyramidBlockTimers.get(npc) == -1 && point.getY() == PyramidBlocks.PYRAMID_BLOCK_START_COORDINATES.get(npc.getId()))
                {
                    pyramidBlockTimers.put(npc, 6);
                    break;
                }
            }
            if (pyramidBlockTimers.get(npc) > 0)
            {
                pyramidBlockTimers.put(npc, pyramidBlockTimers.get(npc) - 1);
            }
        }
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned event)
    {
        NPC npc = event.getNpc();
        if (lifecycle.isEnabled() && lifecycle.isStarted() && PyramidBlocks.PYRAMID_BLOCK_IDS.contains(npc.getId()))
        {
            pyramidBlockTimers.put(npc, -1);
        }
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event)
    {
        pyramidBlockTimers.remove(event.getNpc());
    }
}
