package net.runelite.client.plugins.microbot.fourtifysgemmining.tasks;

import net.runelite.client.plugins.microbot.fourtifysgemmining.util.task.FourtifysTask;

import net.runelite.api.Client;
import net.runelite.client.plugins.microbot.fourtifysgemmining.FourtifysGemMiningPlugin;
import net.runelite.client.plugins.microbot.fourtifysgemmining.config.GemMiningConfig;
import net.runelite.client.plugins.microbot.fourtifysgemmining.mining.GemMine;
import net.runelite.client.plugins.microbot.fourtifysgemmining.util.action.GemMiningActions;

public final class BankGemsTask implements FourtifysTask
{
    private enum Waiting
    {
        NONE, OPEN, DEPOSIT, CLOSE, BAG_BARRIER, CHECK_BAG
    }

    private enum BagPhase
    {
        NONE, EMPTY_SENT, BARRIER, CHECK, VERIFIED
    }

    private final FourtifysGemMiningPlugin plugin;
    private final Client client;
    private final GemMiningConfig config;
    private final GemMiningActions actions;
    private final GemBagTask gemBag;
    private boolean needed;
    private boolean paused;
    private int deadline;
    private int attempts;
    private int before;
    private Waiting waiting = Waiting.NONE;
    private BagPhase bagPhase = BagPhase.NONE;
    private boolean bagExpected;
    private int bagAttempts;
    private int previousBagTotal = -1;
    private int bagControlsDeadline;
    private long beforeCheckRevision;
    private boolean bagWasOpen;
    private String status = "Banking gems";

    public BankGemsTask(FourtifysGemMiningPlugin plugin, Client client, GemMiningConfig config,
                        GemMiningActions actions, GemBagTask gemBag)
    {
        this.plugin = plugin;
        this.client = client;
        this.config = config;
        this.actions = actions;
        this.gemBag = gemBag;
    }

    public boolean isValid()
    {
        needed |= actions.freeSlots() == 0 || actions.bankingOpen()
            || (bagExpected && bagPhase != BagPhase.VERIFIED);
        return client.getLocalPlayer() != null && needed;
    }

    public String getStatus()
    {
        return status;
    }

    public boolean isPending()
    {
        return waiting != Waiting.NONE && !paused;
    }

    public boolean isPaused()
    {
        return paused;
    }

    public boolean requiresBankAccess()
    {
        return actions.gemCount() > 0 || ((bagExpected || actions.hasGemBag()) && bagPhase == BagPhase.NONE);
    }

    public void observeMovement(int tick)
    {
        if (isPending())
        {
            deadline = tick + 8;
        }
    }

    public void manualBagAction(boolean changesContents)
    {
        if (!needed || !bagExpected)
        {
            return;
        }
        if (changesContents)
        {
            bagPhase = BagPhase.NONE;
            bagAttempts = 0;
            previousBagTotal = -1;
        }
        else if (waiting == Waiting.BAG_BARRIER || bagPhase == BagPhase.CHECK)
        {
            // The player's action can answer the plugin's own, so confirm the bag state again.
            bagPhase = BagPhase.BARRIER;
        }
        else
        {
            return;
        }
        waiting = Waiting.NONE;
        attempts = 0;
        bagControlsDeadline = 0;
        actions.reset();
        plugin.setTickDelay(FourtifysGemMiningPlugin.PLAYER_BAG_WAIT_TICKS);
    }

    public void execute()
    {
        status = bank();
        plugin.setStatus(status);
    }

    public void suspend()
    {
        if (isPending())
        {
            deadline = plugin.getTick() + 8;
        }
        if (bagControlsDeadline != 0)
        {
            bagControlsDeadline = plugin.getTick() + 8;
        }
    }

    public void reset()
    {
        needed = false;
        paused = false;
        attempts = 0;
        waiting = Waiting.NONE;
        bagPhase = BagPhase.NONE;
        bagExpected = false;
        bagAttempts = 0;
        previousBagTotal = -1;
        bagControlsDeadline = 0;
        beforeCheckRevision = 0;
        status = "Banking gems";
    }

    private String bank()
    {
        if (paused)
        {
            return status;
        }
        int gems = actions.gemCount();
        int tick = plugin.getTick();
        GemMine mine = config.mine();
        if (actions.bankPinOpen())
        {
            // The PIN is part of opening the bank, so its wait is not a failed open.
            deadline = tick + 8;
            actions.enterBankPin();
            return "Entering the bank PIN. If Microbot has none saved, type it yourself";
        }
        bagExpected |= actions.hasGemBag();
        if (bagExpected && !actions.hasGemBag())
        {
            paused = true;
            return "Gem bag is no longer in your inventory. Withdraw it, then disable and enable to retry";
        }
        if (waiting != Waiting.NONE)
        {
            boolean observed = waiting == Waiting.OPEN ? actions.bankingOpen()
                : waiting == Waiting.CLOSE ? !actions.bankingOpen()
                : waiting == Waiting.BAG_BARRIER ? actions.gemBagOpen() != bagWasOpen
                : waiting == Waiting.CHECK_BAG ? gemBag.getCheckRevision() > beforeCheckRevision : gems < before;
            if (observed)
            {
                if (waiting == Waiting.BAG_BARRIER)
                {
                    bagPhase = BagPhase.CHECK;
                }
                if (waiting == Waiting.CHECK_BAG && !acceptBagCheck())
                {
                    return "The gem bag could not be emptied. Make space in your bank, then disable and enable to retry";
                }
                waiting = Waiting.NONE;
                attempts = 0;
            }
            else if (tick < deadline)
            {
                return "Waiting for bank action";
            }
            else
            {
                boolean checkingBag = waiting == Waiting.CHECK_BAG;
                boolean confirmingBag = waiting == Waiting.BAG_BARRIER;
                waiting = Waiting.NONE;
                if (confirmingBag || attempts >= 3)
                {
                    paused = true;
                    return checkingBag ? "The gem bag did not answer Check. Disable and enable to retry"
                        : confirmingBag ? "The gem bag did not open or close. Disable and enable to retry"
                        : "The bank did not respond after three tries. Walk next to the bank, then disable and enable to retry";
                }
            }
        }
        if (bagExpected && bagPhase == BagPhase.BARRIER && !actions.bankingOpen())
        {
            return bagBarrier();
        }
        if (bagExpected && bagPhase == BagPhase.CHECK && !actions.bankingOpen())
        {
            return checkBag();
        }
        if (gems == 0)
        {
            if (actions.bankingOpen())
            {
                if (bagExpected && bagPhase != BagPhase.VERIFIED)
                {
                    return emptyBag();
                }
                actions.closeBank(() -> await(Waiting.CLOSE));
                return "Closing bank";
            }
            if (bagExpected && bagPhase != BagPhase.VERIFIED)
            {
                if (actions.openBank(mine, () -> await(Waiting.OPEN)))
                {
                    return "Opening bank";
                }
                return walkCloserToBank(mine);
            }
            if (actions.freeSlots() == 0)
            {
                return "Inventory full of other items. Please make room";
            }
            reset();
            return "Walk closer to " + mine.getPlaceName();
        }
        if (!actions.bankingOpen())
        {
            if (actions.openBank(mine, () -> await(Waiting.OPEN)))
            {
                return "Opening bank";
            }
            return walkCloserToBank(mine);
        }
        if (!actions.depositGem(() -> await(Waiting.DEPOSIT)))
        {
            return "Waiting for the bank to show your inventory";
        }
        return "Depositing uncut gems";
    }

    private static String walkCloserToBank(GemMine mine)
    {
        return mine.isUnderground() ? "Walk closer to the bank deposit chest in the underground mine"
            : "Walk closer to a banker in Shilo Village bank";
    }

    private boolean acceptBagCheck()
    {
        int remaining = gemBag.getCheckedTotal();
        if (remaining == 0)
        {
            bagPhase = BagPhase.VERIFIED;
            bagAttempts = 0;
            return true;
        }
        if (previousBagTotal >= 0 && remaining < previousBagTotal)
        {
            bagAttempts = 0;
        }
        previousBagTotal = remaining;
        if (bagAttempts >= 3)
        {
            paused = true;
            return false;
        }
        bagPhase = BagPhase.NONE;
        return true;
    }

    private String emptyBag()
    {
        if (bagPhase == BagPhase.EMPTY_SENT || bagPhase == BagPhase.BARRIER || bagPhase == BagPhase.CHECK)
        {
            if (controlsExpired())
            {
                return "The bank has no close button. Close the bank, then disable and enable to retry";
            }
            if (!actions.closeBank(() -> {
                bagPhase = BagPhase.BARRIER;
                bagControlsDeadline = 0;
                await(Waiting.CLOSE);
            }))
            {
                return "Waiting for bank close action";
            }
            return "Closing bank to check gem bag";
        }
        if (controlsExpired())
        {
            return "The gem bag shows no Empty option in the bank. Empty it yourself, then disable and enable to retry";
        }
        if (!actions.emptyGemBag(() -> {
            bagPhase = BagPhase.EMPTY_SENT;
            bagAttempts++;
            bagControlsDeadline = 0;
        }))
        {
            return "Waiting for gem bag Empty action";
        }
        return "Emptying gem bag";
    }

    /** Observe an item change the game sent before accepting a later bag Check reply. */
    private String bagBarrier()
    {
        if (controlsExpired())
        {
            return "The gem bag shows no Open or Close option. Keep it in your inventory, then disable and enable to retry";
        }
        String option = actions.gemBagOpen() ? "Close" : "Open";
        if (!actions.inventoryBagAction(option, () -> {
            bagWasOpen = actions.gemBagOpen();
            bagControlsDeadline = 0;
            await(Waiting.BAG_BARRIER);
        }))
        {
            return "Waiting for gem bag controls";
        }
        return "Confirming gem bag state";
    }

    private String checkBag()
    {
        if (controlsExpired())
        {
            return "The gem bag shows no Check option. Keep it in your inventory, then disable and enable to retry";
        }
        if (!actions.inventoryBagAction("Check", () -> {
            beforeCheckRevision = gemBag.getCheckRevision();
            bagControlsDeadline = 0;
            await(Waiting.CHECK_BAG);
        }))
        {
            return "Waiting for gem bag Check action";
        }
        return "Checking gem bag";
    }

    private boolean controlsExpired()
    {
        if (bagControlsDeadline == 0)
        {
            bagControlsDeadline = plugin.getTick() + 8;
        }
        if (plugin.getTick() >= bagControlsDeadline)
        {
            paused = true;
            return true;
        }
        return false;
    }

    private void await(Waiting action)
    {
        waiting = action;
        before = actions.gemCount();
        deadline = plugin.getTick() + 8;
        attempts++;
    }
}
