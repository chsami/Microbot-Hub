package net.runelite.client.plugins.microbot.fourtifysbarrows;

import net.runelite.client.plugins.microbot.PluginConstants;

import com.google.inject.Provides;
import java.awt.Color;
import java.time.Instant;
import java.util.Random;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.ChatMessageType;
import net.runelite.api.NPC;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.fourtifysbarrows.config.BarrowsConfig;
import net.runelite.client.plugins.microbot.fourtifysbarrows.overlay.BarrowsOverlay;
import net.runelite.client.plugins.microbot.fourtifysbarrows.tasks.CryptTask;
import net.runelite.client.plugins.microbot.fourtifysbarrows.tasks.CombatTask;
import net.runelite.client.plugins.microbot.fourtifysbarrows.tasks.SuppliesTask;
import net.runelite.client.plugins.microbot.fourtifysbarrows.tasks.BankTask;
import net.runelite.client.plugins.microbot.fourtifysbarrows.tasks.EmergencyTask;
import net.runelite.client.plugins.microbot.fourtifysbarrows.tasks.TravelTask;
import net.runelite.client.plugins.microbot.fourtifysbarrows.tasks.AutoRetaliateTask;
import net.runelite.client.events.ConfigChanged;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.fourtifysbarrows.util.task.FourtifysTask;
import net.runelite.client.plugins.microbot.fourtifysbarrows.util.action.BarrowsActions;
import net.runelite.client.plugins.microbot.fourtifysbarrows.util.action.ActionQueue;
import net.runelite.client.plugins.microbot.fourtifysbarrows.util.lifecycle.SessionLifecycle;
import net.runelite.client.plugins.microbot.fourtifysbarrows.util.timing.TickDelay;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ColorUtil;

@PluginDescriptor(name = "<html><font color=#f5dd42>Fourtifys Barrows", configName = "fourtifyBarrows",
                    description = "Kills the Barrows brothers, loots the chest and restocks at Ferox Enclave", tags = {"fourtifys", "barrows"}, enabledByDefault = PluginConstants.DEFAULT_ENABLED,
                    authors = {"Fourtifys"}, version = FourtifysBarrowsPlugin.VERSION, minClientVersion = "2.6.43", isExternal = PluginConstants.IS_EXTERNAL,
                    iconUrl = "https://chsami.github.io/Microbot-Hub/FourtifysBarrowsPlugin/assets/icon.png")
public class FourtifysBarrowsPlugin extends Plugin
{
    public static final String VERSION = "1.0.0";
    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private BarrowsConfig config;
    @Inject private BarrowsActions actions;
    @Inject private OverlayManager overlays;
    @Inject private BarrowsOverlay overlay;
    private final Random random = new Random();
    private final TickDelay delay = new TickDelay();
    private String status = "Stopped";
    private Instant startTime;
    private final SessionLifecycle lifecycle = new SessionLifecycle();
    private int tickGameCycle;
    private long sessionRequestBaseline;
    private long lastActionRequests;
    private CryptTask crypt;
    private CombatTask combat;
    private SuppliesTask supplies;
    private BankTask bank;
    private EmergencyTask emergency;
    private TravelTask travel;
    private AutoRetaliateTask autoRetaliate;

    @Provides
    BarrowsConfig provideConfig(ConfigManager manager)
    {
        return manager.getConfig(BarrowsConfig.class);
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
        actions.newSession();
        String settingsProblem = settingsProblem();
        if (settingsProblem != null)
        {
            actions.fail(settingsProblem);
        }
        crypt = new CryptTask(actions);
        combat = new CombatTask(actions);
        supplies = new SuppliesTask(actions);
        bank = new BankTask(actions);
        emergency = new EmergencyTask(actions);
        travel = new TravelTask(actions);
        autoRetaliate = new AutoRetaliateTask(actions);
        if (actions.ready())
        {
            String bankTravelProblem = actions.bankTravelStartProblem();
            String gearProblem = actions.missingGear();
            if (bankTravelProblem != null)
            {
                actions.fail(bankTravelProblem);
            }
            else if (gearProblem != null)
            {
                actions.fail(gearProblem);
            }
            else if (!actions.hasBankTravelRing())
            {
                actions.requestBank();
            }
        }
        boolean atBarrows = actions.atBarrows();
        if (actions.ready() && (!actions.ensureSpade()
            || !atBarrows && actions.hp() > 0 && actions.emergencyHealth()
            || config.foodCount() > 0 && actions.foodCount() == 0
            || config.potionCount() > 0 && actions.potionCount() == 0
            || !atBarrows && (actions.foodCount() < config.foodCount() || actions.potionCount() < config.potionCount()
                || actions.restockItems().entrySet().stream().anyMatch(item -> actions.count(item.getKey()) < item.getValue()))))
        {
            actions.requestBank();
        }
        if (actions.ready() && !atBarrows && !actions.bankPending() && actions.player().getInteracting() == null)
        {
            String attackProblem = actions.npcAttackProblem();
            if (attackProblem != null)
            {
                actions.fail(attackProblem);
            }
        }
        sessionRequestBaseline = actions.getRequestsDispatched();
        lastActionRequests = sessionRequestBaseline;
        lifecycle.setStarted(true);
        startTime = Instant.now();
        status = "Starting";
        overlays.add(overlay);
    }

    /** Names the first item list setting that does not parse, with its value and the expected form. */
    private String settingsProblem()
    {
        String[][] lists = {{"Magic equipment IDs", config.magicGear()}, {"Ahrim equipment IDs", config.rangedGear()},
            {"Tunnel equipment IDs", config.meleeGear()}, {"Retained inventory IDs", config.keepItems()}};
        for (String[] list : lists)
        {
            try
            {
                BarrowsActions.gearIds(list[1]);
            }
            catch (IllegalArgumentException invalid)
            {
                return list[0] + " is \"" + list[1] + "\"; use positive item IDs separated by commas, such as 4151,11840";
            }
        }
        try
        {
            actions.restockItems();
        }
        catch (IllegalArgumentException invalid)
        {
            return "Additional supplies/ammunition is \"" + config.restockItems()
                + "\"; use itemID:quantity pairs separated by commas, such as 560:100,9075:200";
        }
        return null;
    }

    private void stop()
    {
        lifecycle.setStarted(false);
        startTime = null;
        delay.set(0);
        actions.reset();
        overlays.remove(overlay);
        status = actions.failure() == null ? "Stopped" : actions.failure();
    }

    /** Ends the session, tells the player why in chat and switches the plugin off. */
    private void stopFor(String reason)
    {
        status = reason;
        lifecycle.scheduleStop(clientThread, this::stop);
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", ColorUtil.wrapWithColorTag("Fourtifys Barrows stopped: " + reason, Color.RED), null);
        Microbot.stopPlugin(this);
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() != GameState.LOGGED_IN)
        {
            actions.reset();
            if (bank != null)
            {
                bank.suspend();
            }
            if (travel != null)
            {
                travel.suspend();
            }
            if (autoRetaliate != null)
            {
                autoRetaliate.suspend();
            }
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (BarrowsConfig.GROUP.equals(event.getGroup()))
        {
            if (event.getKey() != null && event.getKey().startsWith("tickDelay"))
            {
                delay.set(0);
                return;
            }
            // Tasks read settings when they use them, so a change keeps the runtime, chest count and crypt progress.
            clientThread.invokeLater(() ->
            {
                String problem = lifecycle.isStarted() ? settingsProblem() : null;
                if (problem != null)
                {
                    actions.fail(problem);
                }
            });
        }
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged event)
    {
        if (event.getActor() instanceof NPC)
        {
            actions.npcAnimated((NPC) event.getActor());
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        if (event.getType() == ChatMessageType.GAMEMESSAGE && "I'm already under attack.".equals(event.getMessage()))
        {
            actions.attackRejected();
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
            if (actions.getRequestsDispatched() != lastActionRequests)
            {
                lastActionRequests = actions.getRequestsDispatched();
                delayNextAction();
            }
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
        autoRetaliate.observe();
        crypt.observe();
        if (player.isDead())
        {
            actions.fail("Player died; recover before enabling the plugin again");
        }
        String failure = actions.failure();
        if (failure != null)
        {
            stopFor(failure);
            return;
        }
        if (!actions.ready())
        {
            status = actions.getStatus();
            return;
        }
        String stopping = actions.pendingStop();
        if (stopping != null)
        {
            if (emergency.isValid())
            {
                emergency.execute();
                status = actions.pendingStop() != null ? "Teleporting out before stopping: " + stopping : actions.getStatus();
            }
            else
            {
                actions.failEscape();
                status = actions.getStatus();
            }
            return;
        }
        String prerequisite = actions.prerequisite();
        if (prerequisite != null)
        {
            actions.fail(prerequisite);
            status = actions.getStatus();
            return;
        }
        if (client.getRealSkillLevel(Skill.PRAYER) < 43)
        {
            actions.fail("Protection prayers need level 43 Prayer; train Prayer to 43, then enable the plugin again");
            status = actions.getStatus();
            return;
        }
        if (emergency.isValid())
        {
            emergency.execute();
            status = actions.getStatus();
            return;
        }
        if (waitForDelay(player))
        {
            return;
        }
        for (FourtifysTask task : new FourtifysTask[] {supplies, autoRetaliate, combat, bank, travel, crypt})
        {
            if (task.isValid())
            {
                task.execute();
                status = actions.getStatus();
                return;
            }
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

    public Instant getStartTime()
    {
        return startTime;
    }

    public String getStatus()
    {
        return status;
    }

    public long getChests()
    {
        return crypt == null ? 0 : crypt.getChests();
    }

    public long getRequestsDispatched()
    {
        return actions.getRequestsDispatched() - sessionRequestBaseline;
    }

    public String getWalkingDiagnostic()
    {
        return actions.getWalkingDiagnostic();
    }
}
