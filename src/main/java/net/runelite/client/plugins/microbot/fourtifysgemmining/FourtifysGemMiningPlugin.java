package net.runelite.client.plugins.microbot.fourtifysgemmining;

import net.runelite.client.plugins.microbot.PluginConstants;

import com.google.inject.Provides;
import java.time.Instant;
import java.util.Random;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.MenuAction;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.fourtifysgemmining.config.GemMiningConfig;
import net.runelite.client.plugins.microbot.fourtifysgemmining.mining.GemMine;
import net.runelite.client.plugins.microbot.fourtifysgemmining.overlay.GemMiningOverlay;
import net.runelite.client.plugins.microbot.fourtifysgemmining.tasks.BankGemsTask;
import net.runelite.client.plugins.microbot.fourtifysgemmining.tasks.GemBagTask;
import net.runelite.client.plugins.microbot.fourtifysgemmining.tasks.MineGemsTask;
import net.runelite.client.plugins.microbot.fourtifysgemmining.tasks.GemTripTask;
import net.runelite.client.plugins.microbot.fourtifysgemmining.util.action.GemMiningActions;
import net.runelite.client.plugins.microbot.fourtifysgemmining.util.action.ActionQueue;
import net.runelite.client.plugins.microbot.fourtifysgemmining.util.inventory.InventoryGainTracker;
import net.runelite.client.plugins.microbot.fourtifysgemmining.util.lifecycle.SessionLifecycle;
import net.runelite.client.plugins.microbot.fourtifysgemmining.util.navigation.Walking;
import net.runelite.client.plugins.microbot.fourtifysgemmining.util.timing.TickDelay;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(name = "<html><font color=#f5dd42>Fourtifys Gem Mining", configName = "fourtifyGemMining",
                    description = "Mine Shilo Village gems with automatic banking", tags = {"fourtifys", "mining", "gems"}, enabledByDefault = PluginConstants.DEFAULT_ENABLED,
                    authors = {"Fourtifys"}, version = FourtifysGemMiningPlugin.VERSION, minClientVersion = "2.6.43", isExternal = PluginConstants.IS_EXTERNAL,
                    iconUrl = "https://chsami.github.io/Microbot-Hub/FourtifysGemMiningPlugin/assets/icon.png")
public class FourtifysGemMiningPlugin extends Plugin
{
    public static final String VERSION = "1.0.1";
    /** Ticks to let the player's own gem bag action land before the plugin checks the bag again. */
    public static final int PLAYER_BAG_WAIT_TICKS = 3;
    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private GemMiningConfig config;
    @Inject private GemMiningActions actions;
    @Inject private OverlayManager overlays;
    @Inject private GemMiningOverlay overlay;
    @Inject private Walking walking;
    private final Random random = new Random();
    private final SessionLifecycle lifecycle = new SessionLifecycle();
    private Instant startTime;
    private String status = "Stopped";
    private String statusBeforePause;
    private GemMine activeMine;
    private GemMine tickMine;
    private WorldPoint previous;
    private int tick;
    private int tickGameCycle;
    private long observedRequests;
    private final TickDelay delay = new TickDelay();
    private final InventoryGainTracker gains = new InventoryGainTracker(InventoryGainTracker.Baseline.PREVIOUS_SNAPSHOT);
    private boolean counting;
    private boolean bankPreviouslyOpen;
    private boolean bagTransferPending;
    private boolean bagTransferBarrierSent;
    private boolean bagTransferWasOpen;
    private boolean bagTransferPaused;
    private boolean dispatchingAction;
    private int bagTransferDeadline;
    private MineGemsTask mining;
    private GemBagTask gemBag;
    private BankGemsTask banking;
    private GemTripTask trip;

    @Provides
    GemMiningConfig provideConfig(ConfigManager manager)
    {
        return manager.getConfig(GemMiningConfig.class);
    }

    @Provides
    Walking provideWalking(Client targetClient, ClientThread targetThread)
    {
        return new Walking(targetClient, targetThread, () -> lifecycle.isEnabled() && lifecycle.isStarted()
            && !SessionLifecycle.scriptsPaused()
            && trip != null && trip.canTravel() && tickMine != null && config.mine() == tickMine
            && client.getItemContainer(93) != null && actions.prerequisite(tickMine) == null);
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
        mining = new MineGemsTask(this, client, config, actions);
        gemBag = new GemBagTask(this, actions);
        banking = new BankGemsTask(this, client, config, actions, gemBag);
        trip = new GemTripTask(this, client, config, actions, banking, walking);
        tick = 0;
        gains.reset();
        lifecycle.setStarted(true);
        startTime = Instant.now();
        status = "Starting";
        overlays.add(overlay);
    }

    private void stop()
    {
        lifecycle.setStarted(false);
        resetActivity();
        startTime = null;
        statusBeforePause = null;
        overlays.remove(overlay);
        status = "Stopped";
    }

    private void resetActivity()
    {
        resetActivity(false);
    }

    private void resetActivity(boolean preserveBagTransfer)
    {
        walking.cancel();
        if (trip != null)
        {
            trip.reset();
        }
        if (mining != null)
        {
            mining.reset();
        }
        if (banking != null)
        {
            banking.reset();
        }
        if (gemBag != null)
        {
            gemBag.reset();
        }
        actions.reset();
        previous = null;
        activeMine = null;
        tickMine = null;
        gains.suspend();
        counting = false;
        bankPreviouslyOpen = false;
        if (!preserveBagTransfer)
        {
            bagTransferPending = false;
            bagTransferBarrierSent = false;
            bagTransferPaused = false;
        }
        else if (bagTransferPending)
        {
            bagTransferDeadline = tick + 8;
        }
        delay.set(0);
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() != GameState.LOGGED_IN)
        {
            if (event.getGameState() == GameState.LOADING && trip != null && trip.isInProgress())
            {
                walking.cancel();
                banking.suspend();
                gemBag.suspend();
                mining.reset();
                actions.reset();
                previous = null;
                tickMine = null;
                gains.suspend();
                counting = false;
                bankPreviouslyOpen = false;
                if (bagTransferPending)
                {
                    bagTransferDeadline = tick + 8;
                }
            }
            else
            {
                resetActivity(event.getGameState() == GameState.LOADING);
            }
            status = event.getGameState() == GameState.LOADING ? "Waiting for scene" : "Waiting for login";
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!GemMiningConfig.GROUP.equals(event.getGroup()) || !"mine".equals(event.getKey()))
        {
            return;
        }
        walking.cancel();
        int generation = lifecycle.generation();
        clientThread.invokeLater(() ->
        {
            if (lifecycle.isCurrent(generation))
            {
                resetActivity(true);
            }
        });
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        if (!lifecycle.isEnabled() || !lifecycle.isStarted() || client.getGameState() != GameState.LOGGED_IN
            || gemBag == null || mining == null)
        {
            return;
        }
        Player player = client.getLocalPlayer();
        boolean countMining = player != null && !player.isDead() && player.getWorldView() == client.getTopLevelWorldView()
            && config.mine() == tickMine && mining.isPending() && mining.isWithinRange() && !actions.bankingOpen();
        gemBag.observe(event, countMining && actions.gemBagOpen(), countMining && bagTransferPending,
            mining.getRequestSequence());
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        if (!lifecycle.isEnabled() || !lifecycle.isStarted() || actions.consumeBagRequest(event) || dispatchingAction || event.getParam1() != (149 << 16)
            || (event.getMenuAction() != MenuAction.CC_OP && event.getMenuAction() != MenuAction.CC_OP_LOW_PRIORITY))
        {
            return;
        }
        String option = event.getMenuOption();
        boolean transfer = "Fill".equalsIgnoreCase(option) || "Empty".equalsIgnoreCase(option);
        if (!transfer && !"Check".equalsIgnoreCase(option) && !"Open".equalsIgnoreCase(option)
            && !"Close".equalsIgnoreCase(option))
        {
            return;
        }
        int slot = event.getParam0();
        int itemId = event.getItemId();
        Item[] items = actions.items();
        if ((itemId != 12020 && itemId != 24481) || slot < 0 || slot >= items.length
            || items[slot] == null || items[slot].getId() != itemId)
        {
            return;
        }
        if (bagTransferPending)
        {
            bagTransferBarrierSent = false;
            bagTransferDeadline = tick + 8;
            delay.set(PLAYER_BAG_WAIT_TICKS);
            actions.reset();
            return;
        }
        if (banking != null)
        {
            banking.manualBagAction(transfer);
        }
        if (!transfer)
        {
            return;
        }
        bagTransferPending = true;
        bagTransferBarrierSent = false;
        bagTransferPaused = false;
        bagTransferDeadline = tick + 8;
        actions.reset();
        gains.suspend();
    }

    @Subscribe
    public void onClientTick(ClientTick event)
    {
        if (!lifecycle.canDispatch(client))
        {
            return;
        }
        if (SessionLifecycle.scriptsPaused())
        {
            actions.reset();
            return;
        }
        Player player = client.getLocalPlayer();
        if (player == null || player.isDead() || player.getWorldView() != client.getTopLevelWorldView()
            || config.mine() != tickMine)
        {
            walking.cancel();
            actions.reset();
            return;
        }
        if (walking.isActive() || walking.isStopping() || walking.getCancellationFailure() != null)
        {
            actions.reset();
            return;
        }
        if (ActionQueue.withinWindow(client.getGameCycle(), tickGameCycle))
        {
            dispatchingAction = true;
            try
            {
                actions.dispatch();
            }
            finally
            {
                dispatchingAction = false;
            }
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
        String paused = SessionLifecycle.pauseReason();
        if (paused != null)
        {
            walking.cancel();
            actions.reset();
            if (statusBeforePause == null)
            {
                statusBeforePause = status;
            }
            status = paused;
            return;
        }
        if (statusBeforePause != null)
        {
            // The short pause leaves the status alone while the player is busy, so the pause text would stay.
            status = statusBeforePause;
            statusBeforePause = null;
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
        if (client.getItemContainer(93) == null)
        {
            walking.cancel();
            if (bagTransferPending)
            {
                bagTransferDeadline = tick + 8;
            }
            status = "Waiting for inventory";
            return;
        }
        GemMine selected = config.mine();
        if (tickMine != null && selected != tickMine)
        {
            resetActivity(true);
        }
        tickMine = selected;
        int gems = actions.gemCount();
        boolean bankOpen = actions.bankingOpen();
        boolean transferringBag = bagTransferPending;
        gains.observe(gems, !transferringBag && counting && !bankOpen && !bankPreviouslyOpen
            && mining.isWithinRange());
        bankPreviouslyOpen = bankOpen;
        counting = false;
        WorldPoint point = player.getWorldLocation();
        boolean moved = previous != null && !previous.equals(point);
        previous = point;
        if (player.getWorldView() != client.getTopLevelWorldView())
        {
            walking.cancel();
            status = "Step off the boat and return to Shilo Village to continue";
            return;
        }
        if (player.isDead())
        {
            resetActivity();
            status = "Player is dead";
            return;
        }
        if (walking.getCancellationFailure() != null)
        {
            actions.reset();
            status = walking.getCancellationFailure() + ". Disable and enable to retry";
            return;
        }
        if (walking.isStopping())
        {
            status = "Waiting for travel to stop";
            return;
        }
        if (transferringBag)
        {
            gemBag.suspend();
            banking.suspend();
            if (bagTransferPaused || !actions.hasGemBag() || tick >= bagTransferDeadline)
            {
                bagTransferPaused = true;
                actions.reset();
                status = "You used the gem bag and its contents could not be confirmed. "
                    + "Keep the bag in your inventory, then disable and enable to retry";
            }
            else if (bagTransferBarrierSent && actions.gemBagOpen() != bagTransferWasOpen)
            {
                bagTransferPending = false;
                bagTransferBarrierSent = false;
                mining.reset();
                status = "Gem bag used. Resuming";
            }
            else
            {
                if (!bagTransferBarrierSent)
                {
                    if (waitForDelay(player))
                    {
                        bagTransferDeadline = tick + 8;
                        return;
                    }
                    String option = actions.gemBagOpen() ? "Close" : "Open";
                    actions.inventoryBagAction(option, () -> {
                        bagTransferWasOpen = actions.gemBagOpen();
                        bagTransferBarrierSent = true;
                        bagTransferDeadline = tick + 8;
                    });
                }
                status = "Waiting for gem bag transfer";
            }
            return;
        }
        if (moved)
        {
            mining.observeMovement(tick);
            banking.observeMovement(tick);
        }
        mining.observeAnimation(player, tick);
        if (mining.observeCompletion())
        {
            delayNextAction();
        }
        if (waitForDelay(player))
        {
            counting = mining.isPending() && mining.isWithinRange() && !bankOpen;
            return;
        }
        if (trip.isValid())
        {
            gemBag.suspend();
            mining.reset();
            activeMine = null;
            trip.setMoving(moved);
            trip.execute();
            return;
        }
        if (moved)
        {
            counting = mining.isPending() && mining.isWithinRange() && !bankOpen;
            status = mining.isPending() ? "Approaching gem rock"
                    : banking.isPending() ? "Approaching bank" : "Waiting for movement to finish";
            return;
        }
        if (banking.isValid())
        {
            mining.reset();
            status = banking.getStatus();
            banking.execute();
        }
        else
        {
            String missing = actions.prerequisite(selected);
            if (missing != null)
            {
                status = missing;
                return;
            }
            if (!selected.contains(point) && actions.nearestRock(selected) == null)
            {
                activeMine = null;
                mining.reset();
                status = "Walk closer to " + selected.getPlaceName();
                return;
            }
            activeMine = selected;
            if (!mining.isPending() && gemBag.isValid())
            {
                gemBag.execute();
                return;
            }
            if (mining.isValid())
            {
                status = mining.getStatus();
                mining.execute();
                counting = mining.isPending();
            }
        }
    }

    private void observeRequests()
    {
        long requests = actions.getRequestsDispatched();
        if (requests != observedRequests)
        {
            observedRequests = requests;
            delayNextAction();
            Player player = client.getLocalPlayer();
            counting = mining.isPending() && player != null && mining.isWithinRange() && !actions.bankingOpen();
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

    public int getGemsMined()
    {
        long bagged = gemBag == null ? 0 : gemBag.getBaggedGems();
        return (int) Math.min(Integer.MAX_VALUE, gains.getCollected() + bagged);
    }

    public String getActiveMineName()
    {
        return activeMine == null ? config.mine().getDisplayName() : activeMine.getDisplayName();
    }
}
