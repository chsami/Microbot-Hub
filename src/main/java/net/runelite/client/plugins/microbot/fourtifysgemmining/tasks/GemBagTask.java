package net.runelite.client.plugins.microbot.fourtifysgemmining.tasks;

import java.util.OptionalInt;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.plugins.microbot.fourtifysgemmining.FourtifysGemMiningPlugin;
import net.runelite.client.plugins.microbot.fourtifysgemmining.util.inventory.GemBagMessages;
import net.runelite.client.plugins.microbot.fourtifysgemmining.util.action.GemMiningActions;
import net.runelite.client.plugins.microbot.fourtifysgemmining.util.task.FourtifysTask;

/** Prepares an optional gem bag and records its observed messages. */
public final class GemBagTask implements FourtifysTask
{
    private enum Waiting
    {
        NONE, OPEN, FILL
    }

    private final FourtifysGemMiningPlugin plugin;
    private final GemMiningActions actions;
    private Waiting waiting = Waiting.NONE;
    private boolean prepared;
    private boolean paused;
    private int deadline;
    private int controlsDeadline;
    private int attempts;
    private int beforeFill;
    private long fillRevision;
    private long beforeFillRevision;
    private long checkRevision;
    private int checkedTotal;
    private long lastCountedRequest;
    private volatile int baggedGems;
    private String status = "Preparing gem bag";

    public GemBagTask(FourtifysGemMiningPlugin plugin, GemMiningActions actions)
    {
        this.plugin = plugin;
        this.actions = actions;
    }

    @Override
    public boolean isValid()
    {
        if (!actions.hasGemBag())
        {
            reset();
            return false;
        }
        return paused || waiting != Waiting.NONE || !actions.gemBagOpen() || !prepared;
    }

    @Override
    public String getStatus()
    {
        return status;
    }

    @Override
    public void execute()
    {
        status = prepare();
        plugin.setStatus(status);
    }

    public void observe(ChatMessage event, boolean countStored, boolean countTransferMining, long miningRequest)
    {
        OptionalInt checked = GemBagMessages.checkedTotal(event.getType(), event.getMessage());
        if (checked.isPresent())
        {
            checkedTotal = checked.getAsInt();
            checkRevision++;
        }
        if (GemBagMessages.fillAcknowledged(event.getType(), event.getMessage()))
        {
            fillRevision++;
        }
        boolean mined = (countStored && GemBagMessages.stored(event.getType(), event.getMessage()))
            || (countTransferMining && GemBagMessages.mined(event.getType(), event.getMessage()));
        if (mined && miningRequest > 0 && miningRequest != lastCountedRequest)
        {
            lastCountedRequest = miningRequest;
            if (baggedGems < Integer.MAX_VALUE)
            {
                baggedGems++;
            }
        }
    }

    public long getCheckRevision()
    {
        return checkRevision;
    }

    public int getCheckedTotal()
    {
        return checkedTotal;
    }

    public int getBaggedGems()
    {
        return baggedGems;
    }

    public void suspend()
    {
        if (waiting != Waiting.NONE)
        {
            deadline = plugin.getTick() + 8;
        }
        if (controlsDeadline != 0)
        {
            controlsDeadline = plugin.getTick() + 8;
        }
    }

    public void reset()
    {
        waiting = Waiting.NONE;
        prepared = false;
        paused = false;
        deadline = 0;
        controlsDeadline = 0;
        attempts = 0;
        status = "Preparing gem bag";
    }

    private String prepare()
    {
        if (paused)
        {
            return "Gem bag action failed. Disable and enable to retry";
        }
        int tick = plugin.getTick();
        if (waiting != Waiting.NONE)
        {
            boolean observed = waiting == Waiting.OPEN ? actions.gemBagOpen()
                : actions.baggableGemCount() < beforeFill || fillRevision > beforeFillRevision;
            if (observed)
            {
                if (waiting == Waiting.FILL)
                {
                    prepared = true;
                }
                waiting = Waiting.NONE;
                attempts = 0;
                controlsDeadline = 0;
            }
            else if (tick < deadline)
            {
                return "Waiting for gem bag action";
            }
            else
            {
                waiting = Waiting.NONE;
                if (attempts >= 3)
                {
                    paused = true;
                    return "Gem bag action failed. Disable and enable to retry";
                }
            }
        }
        if (!actions.gemBagOpen())
        {
            prepared = false;
            return request("Open", Waiting.OPEN, "Opening gem bag");
        }
        if (!prepared && actions.baggableGemCount() > 0)
        {
            return request("Fill", Waiting.FILL, "Filling gem bag");
        }
        prepared = true;
        return "Gem bag ready";
    }

    private String request(String option, Waiting action, String message)
    {
        if (controlsDeadline == 0)
        {
            controlsDeadline = plugin.getTick() + 8;
        }
        if (plugin.getTick() >= controlsDeadline)
        {
            paused = true;
            return "Gem bag controls unavailable. Disable and enable to retry";
        }
        if (!actions.inventoryBagAction(option, () -> await(action)))
        {
            return "Waiting for gem bag controls";
        }
        return message;
    }

    private void await(Waiting action)
    {
        waiting = action;
        beforeFill = actions.baggableGemCount();
        beforeFillRevision = fillRevision;
        deadline = plugin.getTick() + 8;
        controlsDeadline = 0;
        attempts++;
    }
}
