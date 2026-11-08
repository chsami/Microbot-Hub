package net.runelite.client.plugins.microbot.fourtifysgemmining;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.MenuAction;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.plugins.microbot.fourtifysgemmining.mining.GemMine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Drives the real plugin against simulated inventory, chat, and bank replies. */
public class GemBagIntegrationTest
{
    private static final int PICKAXE = 1265;
    private static final int CHISEL = 1755;
    private static final int CLOSED_BAG = 12020;
    private static final int OPEN_BAG = 24481;
    private static final int OPAL = 1625;
    private static final int SAPPHIRE = 1623;
    private static final int RUBY = 1619;

    @Test
    public void absentAndAlreadyOpenBagsDoNotPreventMining() throws Exception
    {
        Harness absent = new Harness(GemMine.SURFACE, new Item(PICKAXE, 1));
        absent.start();
        absent.until(() -> absent.count("Mine") > 0);
        assertEquals(0, absent.count("Open"));
        assertEquals(0, absent.count("Fill"));

        Harness open = new Harness(GemMine.SURFACE, new Item(PICKAXE, 1), new Item(OPEN_BAG, 1));
        open.start();
        open.until(() -> open.count("Mine") > 0);
        assertEquals(0, open.count("Open"));
        assertTrue(open.has(OPEN_BAG));
    }

    @Test
    public void closedBagOpensAndFillsExistingGemsWithoutCountingTransfers() throws Exception
    {
        Harness h = new Harness(GemMine.SURFACE, new Item(PICKAXE, 1), new Item(CLOSED_BAG, 1),
            new Item(SAPPHIRE, 1), new Item(RUBY, 1), new Item(OPAL, 1), new Item(CHISEL, 1));
        h.contents[4] = 3;
        h.start();
        h.until(() -> h.count("Mine") > 0);

        assertEquals(1, h.count("Open"));
        assertEquals(1, h.count("Fill"));
        assertTrue(h.first("Open") < h.first("Mine"));
        assertTrue(h.first("Fill") < h.first("Mine"));
        assertEquals(1, h.contents[0]);
        assertEquals(1, h.contents[2]);
        assertEquals(3, h.contents[4]);
        assertTrue(h.has(OPEN_BAG));
        assertTrue(h.has(OPAL));
        assertTrue(h.has(CHISEL));
        assertEquals(0, h.fixture.plugin.getGemsMined());
    }

    @Test
    public void baggedAndLooseMiningAreEachCountedOnce() throws Exception
    {
        Harness h = new Harness(GemMine.SURFACE, new Item(PICKAXE, 1), new Item(OPEN_BAG, 1));
        h.start();
        h.until(() -> h.count("Mine") == 1);

        h.contents[0]++;
        h.fixture.chat(ChatMessageType.GAMEMESSAGE, "You put it straight into your open gem bag.");
        h.fixture.chat(ChatMessageType.SPAM, "You just mined a Sapphire!");
        h.step();
        assertEquals(1, h.fixture.plugin.getGemsMined());

        h.fixture.world.objects.clear();
        h.step();
        int previousMines = h.count("Mine");
        h.fixture.world.rock(2825, 3000);
        h.until(() -> h.count("Mine") > previousMines);
        h.appendInventory(new Item(OPAL, 1));
        h.fixture.chat(ChatMessageType.SPAM, "You just mined an Opal!");
        h.step();
        assertEquals(2, h.fixture.plugin.getGemsMined());
    }

    @Test
    public void duplicateOrLateBagMessagesDoNotCreateExtraGains() throws Exception
    {
        Harness h = new Harness(GemMine.SURFACE, new Item(PICKAXE, 1), new Item(OPEN_BAG, 1));
        h.start();
        h.until(() -> h.count("Mine") == 1);
        h.contents[0]++;
        h.fixture.chat(ChatMessageType.GAMEMESSAGE, "You put it straight into your open gem bag.");
        h.fixture.chat(ChatMessageType.GAMEMESSAGE, "You put it straight into your open gem bag.");
        h.fixture.chat(ChatMessageType.SPAM, "You just mined a Sapphire!");
        h.step();
        assertEquals(1, h.fixture.plugin.getGemsMined());

        h.fixture.disable();
        h.fixture.chat(ChatMessageType.GAMEMESSAGE, "You put it straight into your open gem bag.");
        h.fixture.chat(ChatMessageType.SPAM, "You just mined a Sapphire!");
        h.step();
        assertEquals(1, h.fixture.plugin.getGemsMined());
    }

    @Test
    public void manualBagTransfersDuringMiningAreNotCountedAsMined() throws Exception
    {
        Harness empty = new Harness(GemMine.SURFACE, new Item(PICKAXE, 1), new Item(OPEN_BAG, 1));
        empty.contents[0] = 2;
        empty.start();
        empty.until(() -> empty.count("Mine") == 1);
        int emptyMines = empty.count("Mine");
        empty.fixture.manualBagAction("Empty");
        empty.contents[0] = 0;
        empty.appendInventory(new Item(SAPPHIRE, 1));
        empty.appendInventory(new Item(SAPPHIRE, 1));
        empty.until(() -> empty.count("Mine") > emptyMines);
        assertEquals(0, empty.fixture.plugin.getGemsMined());

        Harness fill = new Harness(GemMine.SURFACE, new Item(PICKAXE, 1), new Item(OPEN_BAG, 1));
        fill.start();
        fill.until(() -> fill.count("Mine") == 1);
        int fillMines = fill.count("Mine");
        fill.appendInventory(new Item(SAPPHIRE, 1));
        fill.appendInventory(new Item(SAPPHIRE, 1));
        fill.fixture.manualBagAction("Fill");
        fill.removeInventory(SAPPHIRE);
        fill.fixture.world.inventoryWidgets();
        fill.contents[0] = 2;
        fill.fixture.chat(ChatMessageType.GAMEMESSAGE, "You add the gems to your bag.");
        fill.until(() -> fill.count("Mine") > fillMines);
        fill.fixture.manualBagAction("Check");
        fill.fixture.chat(ChatMessageType.GAMEMESSAGE, "Sapphires: 2 / Emeralds: 0 / Rubies: 0 / Diamonds: 0 / Dragonstones: 0");
        fill.step();
        assertEquals(0, fill.fixture.plugin.getGemsMined());

        fill.fixture.world.objects.clear();
        fill.step();
        int previousMines = fill.count("Mine");
        fill.fixture.world.rock(2825, 3000);
        fill.until(() -> fill.count("Mine") > previousMines);
        fill.appendInventory(new Item(OPAL, 1));
        fill.fixture.chat(ChatMessageType.SPAM, "You just mined an Opal!");
        fill.step();
        assertEquals(1, fill.fixture.plugin.getGemsMined());
    }

    @Test
    public void delayedManualEmptyWaitsForObservedBagStateChange() throws Exception
    {
        Harness h = new Harness(GemMine.SURFACE, new Item(PICKAXE, 1), new Item(OPEN_BAG, 1));
        h.contents[0] = 2;
        h.start();
        h.until(() -> h.count("Mine") == 1);
        h.acceptOpen = false;
        h.acceptBagClose = false;
        h.fixture.manualBagAction("Empty");
        int previousMines = h.count("Mine");
        int previousChanges = h.countBagStateChanges();
        h.until(() -> h.countBagStateChanges() > previousChanges);
        assertEquals(previousMines, h.count("Mine"));
        assertEquals(0, h.fixture.plugin.getGemsMined());

        h.contents[0] = 0;
        h.appendInventory(new Item(SAPPHIRE, 1));
        h.appendInventory(new Item(SAPPHIRE, 1));
        h.step();
        assertEquals(0, h.fixture.plugin.getGemsMined());
        assertEquals(previousMines, h.count("Mine"));
        h.completeLastBagStateChange();
        h.acceptOpen = true;
        h.acceptBagClose = true;
        h.until(() -> h.count("Mine") > previousMines);
        assertEquals(0, h.fixture.plugin.getGemsMined());
    }

    @Test
    public void manualTransferWaitsForConfiguredActionDelayBeforeBarrier() throws Exception
    {
        Harness h = new Harness(GemMine.SURFACE, new Item(PICKAXE, 1), new Item(OPEN_BAG, 1));
        PluginTestFixture.MutableConfig config = (PluginTestFixture.MutableConfig) h.fixture.config;
        config.minDelayTicks = 10;
        config.maxDelayTicks = 10;
        h.start();
        h.until(() -> h.count("Mine") == 1);
        int previousMines = h.count("Mine");
        int previousChanges = h.countBagStateChanges();
        h.fixture.manualBagAction("Empty");

        for (int tick = 0; tick < 10; tick++)
        {
            h.step();
            assertEquals(previousChanges, h.countBagStateChanges(), "The transfer barrier must respect the configured action delay");
            assertEquals(previousMines, h.count("Mine"));
        }
        h.until(() -> h.countBagStateChanges() > previousChanges);
        h.until(() -> h.count("Mine") > previousMines);
        assertEquals(0, h.fixture.plugin.getGemsMined());
    }

    @Test
    public void manualTransferSurvivesLoadingBeforeOrAfterBarrierDispatch() throws Exception
    {
        for (boolean barrierAlreadySent : new boolean[] {false, true})
        {
            Harness h = new Harness(GemMine.SURFACE, new Item(PICKAXE, 1), new Item(OPEN_BAG, 1));
            h.contents[0] = 2;
            h.start();
            h.until(() -> h.count("Mine") == 1);
            h.acceptOpen = false;
            h.acceptBagClose = false;
            int previousMines = h.count("Mine");
            int previousChanges = h.countBagStateChanges();
            h.fixture.manualBagAction("Empty");
            if (barrierAlreadySent)
            {
                h.until(() -> h.countBagStateChanges() > previousChanges);
            }
            int changesAtLoading = h.countBagStateChanges();
            h.fixture.world.state = GameState.LOADING;
            GameStateChanged loading = new GameStateChanged();
            loading.setGameState(GameState.LOADING);
            h.fixture.plugin.onGameStateChanged(loading);
            h.contents[0] = 0;
            h.appendInventory(new Item(SAPPHIRE, 1));
            h.appendInventory(new Item(SAPPHIRE, 1));
            for (int tick = 0; tick < 12; tick++)
            {
                h.step();
            }
            assertEquals(changesAtLoading, h.countBagStateChanges());
            assertEquals(previousMines, h.count("Mine"));
            assertEquals(0, h.fixture.plugin.getGemsMined());

            h.fixture.world.state = GameState.LOGGED_IN;
            GameStateChanged loggedIn = new GameStateChanged();
            loggedIn.setGameState(GameState.LOGGED_IN);
            h.fixture.plugin.onGameStateChanged(loggedIn);
            h.step();
            assertEquals(previousMines, h.count("Mine"));
            assertEquals(0, h.fixture.plugin.getGemsMined());
            h.until(() -> h.countBagStateChanges() > previousChanges);
            assertEquals(previousChanges + 1, h.countBagStateChanges());

            h.completeLastBagStateChange();
            h.acceptOpen = true;
            h.acceptBagClose = true;
            h.until(() -> h.count("Mine") > previousMines);
            assertEquals(0, h.fixture.plugin.getGemsMined());
        }
    }

    @Test
    public void manualNoOpFillWaitsForObservedBagStateChangeThenResumes() throws Exception
    {
        String[] replies = {
            "You have nothing suitable for storing in the bag.",
            "You have no uncut gems to put in the bag.",
            "Your bag is too full to hold all your gems.",
            "Your gem bag cannot hold any more of those gems."
        };
        for (String reply : replies)
        {
            boolean full = reply.contains("full") || reply.contains("cannot hold");
            Harness h = full
                ? new Harness(GemMine.SURFACE, new Item(PICKAXE, 1), new Item(OPEN_BAG, 1), new Item(SAPPHIRE, 1))
                : new Harness(GemMine.SURFACE, new Item(PICKAXE, 1), new Item(OPEN_BAG, 1));
            if (full)
            {
                h.contents[0] = 60;
            }
            h.start();
            h.until(() -> h.count("Mine") > 0);
            h.acceptOpen = false;
            h.acceptBagClose = false;
            h.fixture.manualBagAction("Fill");
            int previousMines = h.count("Mine");
            int previousChanges = h.countBagStateChanges();
            h.fixture.chat(ChatMessageType.PUBLICCHAT, reply);
            h.fixture.chat(ChatMessageType.GAMEMESSAGE, "The gem bag is empty.");
            h.fixture.chat(ChatMessageType.GAMEMESSAGE, "An unrelated message.");
            h.fixture.chat(ChatMessageType.GAMEMESSAGE, reply);
            h.until(() -> h.countBagStateChanges() > previousChanges);
            h.step();
            assertEquals(previousMines, h.count("Mine"));
            assertEquals(0, h.fixture.plugin.getGemsMined());
            h.completeLastBagStateChange();
            h.acceptOpen = true;
            h.acceptBagClose = true;
            h.until(() -> h.count("Mine") > previousMines);
            assertEquals(0, h.fixture.plugin.getGemsMined());
            assertTrue(h.has(OPEN_BAG));
            assertTrue(h.has(PICKAXE));
            if (full)
            {
                assertTrue(h.has(SAPPHIRE));
                assertEquals(60, h.contents[0]);
            }
        }
    }

    @Test
    public void manualEmptyBagWaitsForObservedBagStateChangeThenResumes() throws Exception
    {
        Harness h = new Harness(GemMine.SURFACE, new Item(PICKAXE, 1), new Item(OPEN_BAG, 1));
        h.start();
        h.until(() -> h.count("Mine") > 0);
        h.acceptOpen = false;
        h.acceptBagClose = false;
        h.fixture.manualBagAction("Empty");
        int previousMines = h.count("Mine");
        int previousChanges = h.countBagStateChanges();
        h.fixture.chat(ChatMessageType.PUBLICCHAT, "The gem bag is empty.");
        h.fixture.chat(ChatMessageType.GAMEMESSAGE, "You have nothing suitable for storing in the bag.");
        h.fixture.chat(ChatMessageType.GAMEMESSAGE, "An unrelated message.");
        h.fixture.chat(ChatMessageType.GAMEMESSAGE, "The gem bag is empty.");
        h.until(() -> h.countBagStateChanges() > previousChanges);
        h.step();
        assertEquals(previousMines, h.count("Mine"));
        assertEquals(0, h.fixture.plugin.getGemsMined());
        h.completeLastBagStateChange();
        h.acceptOpen = true;
        h.acceptBagClose = true;
        h.until(() -> h.count("Mine") > previousMines);
        assertEquals(0, h.fixture.plugin.getGemsMined());
        assertTrue(h.has(OPEN_BAG));
        assertTrue(h.has(PICKAXE));
    }

    @Test
    public void baggableMiningGainDoesNotCompleteDelayedManualEmpty() throws Exception
    {
        Harness h = new Harness(GemMine.SURFACE, new Item(PICKAXE, 1), new Item(OPEN_BAG, 1));
        h.contents[0] = 2;
        h.start();
        h.until(() -> h.count("Mine") == 1);
        h.fixture.manualBagAction("Close");
        h.fixture.world.setBagOpen(false);
        h.acceptOpen = false;
        h.acceptBagClose = false;

        int previousMines = h.count("Mine");
        int previousChanges = h.countBagStateChanges();
        h.fixture.manualBagAction("Empty");
        h.appendInventory(new Item(RUBY, 1));
        h.fixture.chat(ChatMessageType.SPAM, "You just mined a Ruby!");
        h.until(() -> h.countBagStateChanges() > previousChanges);
        assertEquals(previousMines, h.count("Mine"));
        assertEquals(1, h.fixture.plugin.getGemsMined());

        h.contents[0] = 0;
        h.appendInventory(new Item(SAPPHIRE, 1));
        h.appendInventory(new Item(SAPPHIRE, 1));
        h.step();
        assertEquals(previousMines, h.count("Mine"));
        assertEquals(1, h.fixture.plugin.getGemsMined());
        h.completeLastBagStateChange();
        h.acceptOpen = true;
        h.acceptBagClose = true;
        h.until(() -> h.count("Mine") > previousMines);
        assertEquals(1, h.fixture.plugin.getGemsMined());
    }

    @Test
    public void manualBagActionDuringTransferBarrierWaitsAndConfirmsAgain() throws Exception
    {
        Harness h = new Harness(GemMine.SURFACE, new Item(PICKAXE, 1), new Item(OPEN_BAG, 1));
        h.contents[0] = 2;
        h.start();
        h.until(() -> h.count("Mine") == 1);
        h.acceptOpen = false;
        h.acceptBagClose = false;
        int previousMines = h.count("Mine");
        int previousChanges = h.countBagStateChanges();
        h.fixture.manualBagAction("Empty");
        h.until(() -> h.countBagStateChanges() > previousChanges);

        h.fixture.manualBagAction("Check");
        h.step();
        h.contents[0] = 0;
        h.appendInventory(new Item(SAPPHIRE, 1));
        h.appendInventory(new Item(SAPPHIRE, 1));
        h.completeLastBagStateChange();
        h.fixture.chat(ChatMessageType.GAMEMESSAGE, "The gem bag is empty.");
        for (int tick = 1; tick < FourtifysGemMiningPlugin.PLAYER_BAG_WAIT_TICKS; tick++)
        {
            h.step();
        }
        assertEquals(previousChanges + 1, h.countBagStateChanges(), "Wait for the player's own action to land first");

        h.acceptOpen = true;
        h.acceptBagClose = true;
        h.until(() -> h.count("Mine") > previousMines);

        assertEquals(previousChanges + 2, h.countBagStateChanges(), "Confirm the bag again with a fresh action");
        assertEquals(0, h.fixture.plugin.getGemsMined(), "Gems the player emptied are not mined gems");
        assertFalse(h.fixture.plugin.getStatus().contains("retry"), h.fixture.plugin.getStatus());
    }

    @Test
    public void unansweredManualTransferBarrierPausesWithoutRetryAndIgnoresLateToggle() throws Exception
    {
        Harness h = new Harness(GemMine.SURFACE, new Item(PICKAXE, 1), new Item(OPEN_BAG, 1));
        h.start();
        h.until(() -> h.count("Mine") == 1);
        h.acceptOpen = false;
        h.acceptBagClose = false;
        int previousMines = h.count("Mine");
        int previousChanges = h.countBagStateChanges();
        h.fixture.manualBagAction("Fill");
        h.until(() -> h.countBagStateChanges() > previousChanges);
        for (int tick = 0; tick < 12; tick++)
        {
            h.step();
        }
        h.completeLastBagStateChange();
        for (int tick = 0; tick < 12; tick++)
        {
            h.step();
        }

        assertEquals(previousMines, h.count("Mine"));
        assertEquals(previousChanges + 1, h.countBagStateChanges());
        assertEquals(0, h.fixture.plugin.getGemsMined());
        String status = h.fixture.plugin.getStatus().toLowerCase();
        assertTrue(status.startsWith("you used the gem bag") && status.contains("retry"), "Say the player's bag use caused the stop, got: " + status);
    }

    @Test
    public void confirmedLooseMiningDuringAnUnresolvedTransferCountsOnce() throws Exception
    {
        Harness h = new Harness(GemMine.SURFACE, new Item(PICKAXE, 1), new Item(OPEN_BAG, 1));
        h.contents[0] = 2;
        h.start();
        h.until(() -> h.count("Mine") == 1);
        h.acceptOpen = false;
        h.acceptBagClose = false;
        h.fixture.manualBagAction("Empty");
        int previousMines = h.count("Mine");
        int previousChanges = h.countBagStateChanges();
        h.appendInventory(new Item(OPAL, 1));
        h.fixture.chat(ChatMessageType.SPAM, "You just mined an Opal!");
        h.until(() -> h.countBagStateChanges() > previousChanges);
        assertEquals(1, h.fixture.plugin.getGemsMined());
        h.step();
        assertEquals(previousMines, h.count("Mine"));
        assertEquals(1, h.fixture.plugin.getGemsMined());

        h.contents[0] = 0;
        h.appendInventory(new Item(SAPPHIRE, 1));
        h.appendInventory(new Item(SAPPHIRE, 1));
        h.step();
        assertEquals(1, h.fixture.plugin.getGemsMined());
        assertEquals(previousMines, h.count("Mine"));
        h.completeLastBagStateChange();
        h.acceptOpen = true;
        h.acceptBagClose = true;
        h.until(() -> h.count("Mine") > previousMines);
        assertEquals(1, h.fixture.plugin.getGemsMined());
    }

    @Test
    public void bagConfirmationAndMiningSuccessOnTheSameTickCountOnce() throws Exception
    {
        for (boolean confirmationFirst : new boolean[] {true, false})
        {
            Harness h = new Harness(GemMine.SURFACE, new Item(PICKAXE, 1), new Item(OPEN_BAG, 1));
            h.contents[0] = 2;
            h.start();
            h.until(() -> h.count("Mine") == 1);
            h.acceptOpen = false;
            h.acceptBagClose = false;
            h.fixture.manualBagAction("Empty");
            int previousMines = h.count("Mine");
            int previousChanges = h.countBagStateChanges();

            if (confirmationFirst)
            {
                h.fixture.chat(ChatMessageType.GAMEMESSAGE, "You put it straight into your open gem bag.");
                h.fixture.chat(ChatMessageType.SPAM, "You just mined a Sapphire!");
            }
            else
            {
                h.fixture.chat(ChatMessageType.SPAM, "You just mined a Sapphire!");
                h.fixture.chat(ChatMessageType.GAMEMESSAGE, "You put it straight into your open gem bag.");
            }
            h.until(() -> h.countBagStateChanges() > previousChanges);
            assertEquals(1, h.fixture.plugin.getGemsMined());

            h.contents[0] = 0;
            h.appendInventory(new Item(SAPPHIRE, 1));
            h.appendInventory(new Item(SAPPHIRE, 1));
            h.step();
            assertEquals(1, h.fixture.plugin.getGemsMined());
            assertEquals(previousMines, h.count("Mine"));
            h.completeLastBagStateChange();
            h.acceptOpen = true;
            h.acceptBagClose = true;
            h.until(() -> h.count("Mine") > previousMines);
            assertEquals(1, h.fixture.plugin.getGemsMined());
        }
    }

    @Test
    public void lostOpenActionDoesNotStartMiningOrRetryForever() throws Exception
    {
        Harness h = new Harness(GemMine.SURFACE, new Item(PICKAXE, 1), new Item(CLOSED_BAG, 1));
        h.acceptOpen = false;
        h.start();
        h.until(() -> h.count("Open") > 0);
        for (int tick = 0; tick < 120; tick++)
        {
            h.step();
        }
        assertTrue(h.count("Open") <= 3);
        assertEquals(0, h.count("Mine"));
        assertTrue(h.has(CLOSED_BAG));
        String status = h.fixture.plugin.getStatus().toLowerCase();
        assertTrue(status.contains("did not") || status.contains("fail") || status.contains("unable") || status.contains("could not"), "Expected a clear bag failure, got: " + status);
    }

    @Test
    public void fullPerGemSlotDoesNotRepeatedlyFillOrDiscardLooseGems() throws Exception
    {
        Harness h = new Harness(GemMine.SURFACE, new Item(PICKAXE, 1), new Item(OPEN_BAG, 1),
            new Item(SAPPHIRE, 1));
        h.contents[0] = 60;
        h.start();
        h.until(() -> h.count("Mine") > 0);
        assertTrue(h.has(SAPPHIRE));
        assertTrue(h.count("Fill") <= 1);
        assertEquals(60, h.contents[0]);
        assertEquals(0, h.fixture.plugin.getGemsMined());
    }

    @Test
    public void bankAndDepositChestEmptyOnlyTheBagAndVerifyItBeforeResuming() throws Exception
    {
        for (GemMine mine : GemMine.values())
        {
            Harness h = fullTrip(mine);
            h.contents[0] = 4;
            h.contents[4] = 2;
            h.start();
            h.until(() -> h.checkedEmptyAfterBank);

            assertTrue(h.emptyAfterLooseDeposit);
            assertEquals(1, h.bankEmptyActions);
            assertEquals(0, Arrays.stream(h.contents).sum());
            assertEquals(4, h.banked[0]);
            assertEquals(2, h.banked[4]);
            assertTrue(h.has(OPEN_BAG) || h.has(CLOSED_BAG));
            assertTrue(h.has(PICKAXE));
            assertTrue(h.has(CHISEL));
            assertFalse(h.has(OPAL));
            assertEquals(0, h.fixture.plugin.getGemsMined());
            assertEquals(0, h.count("Empty containers"));
            assertEquals(0, h.count("Deposit inventory"));

            if (mine == GemMine.SURFACE)
            {
                h.fixture.world.position = new WorldPoint(2824, 3000, 0);
            }
            else
            {
                h.fixture.world.position = new WorldPoint(2838, 9385, 0);
                h.fixture.world.rock(2839, 9385);
            }
            h.until(() -> h.count("Mine") > 0);
            assertTrue(h.has(OPEN_BAG));
        }
    }

    @Test
    public void retainedBagContentsAfterBankingCauseBoundedRetryAndPause() throws Exception
    {
        for (GemMine mine : GemMine.values())
        {
            Harness h = fullTrip(mine);
            h.contents[0] = 2;
            h.acceptBagDeposit = false;
            h.start();
            h.until(() -> h.bankEmptyActions >= 1);
            for (int tick = 0; tick < 160; tick++)
            {
                h.step();
            }
            assertTrue(h.bankEmptyActions <= 3);
            assertEquals(2, h.contents[0]);
            assertTrue(h.has(OPEN_BAG) || h.has(CLOSED_BAG));
            assertTrue(h.has(PICKAXE));
            assertTrue(h.has(CHISEL));
            assertEquals(0, h.count("Mine"));
            String status = h.fixture.plugin.getStatus().toLowerCase();
            assertTrue(status.contains("fail") || status.contains("unable") || status.contains("could not"), "Expected a clear bank failure, got: " + status);
        }
    }

    @Test
    public void partialBankTransferDoesNotDiscardOrForgetTheResidualGemType() throws Exception
    {
        Harness h = fullTrip(GemMine.UNDERGROUND);
        h.contents[0] = 2;
        h.contents[1] = 1;
        h.acceptsBagType[1] = false;
        h.start();
        h.until(() -> h.bankEmptyActions >= 2);
        for (int tick = 0; tick < 120; tick++)
        {
            h.step();
        }
        assertEquals(2, h.banked[0]);
        assertEquals(0, h.contents[0]);
        assertEquals(1, h.contents[1]);
        assertEquals(0, h.banked[1]);
        assertTrue(h.bankEmptyActions <= 4);
        assertTrue(h.has(OPEN_BAG) || h.has(CLOSED_BAG));
        assertFalse(h.checkedEmptyAfterBank);
        assertEquals(0, h.count("Mine"));
    }

    @Test
    public void staleEmptyReplyCannotVerifyALaterUnansweredCheck() throws Exception
    {
        Harness h = fullTrip(GemMine.SURFACE);
        h.contents[0] = 2;
        h.acceptBagDeposit = false;
        h.answerCheck = false;
        h.start();
        h.until(() -> h.bankEmptyActions == 1);
        h.fixture.chat(ChatMessageType.GAMEMESSAGE, "The gem bag is empty.");
        for (int tick = 0; tick < 120; tick++)
        {
            h.step();
        }
        assertTrue(h.count("Check") > 0);
        assertTrue(h.count("Check") <= 3);
        assertEquals(0, h.count("Mine"));
        assertEquals(2, h.contents[0]);
        assertTrue(h.has(OPEN_BAG) || h.has(CLOSED_BAG));
        assertFalse(h.checkedEmptyAfterBank);
    }

    @Test
    public void delayedManualCheckReplyBeforeBarrierCannotVerifyPostBankCheck() throws Exception
    {
        Harness h = fullTrip(GemMine.SURFACE);
        h.contents[0] = 2;
        h.acceptBagDeposit = false;
        h.answerCheck = false;
        h.acceptOpen = false;
        h.acceptBagClose = false;
        h.start();
        h.until(() -> h.bankEmptyActions == 1 && h.count("Close") > 0);

        h.fixture.manualBagAction("Check");
        h.step();
        h.fixture.chat(ChatMessageType.GAMEMESSAGE, "The gem bag is empty.");
        h.step();
        assertEquals(0, h.count("Check"));
        assertNoDepartureWithContents(h);

        h.until(() -> h.countBagStateChanges() > 0);
        h.completeLastBagStateChange();
        h.acceptOpen = true;
        h.acceptBagClose = true;
        h.until(() -> h.count("Check") > 0);
        assertNoDepartureWithContents(h);

        h.fixture.chat(ChatMessageType.GAMEMESSAGE, h.checkMessage());
        for (int tick = 0; tick < 4; tick++)
        {
            h.step();
        }
        assertNoDepartureWithContents(h);
    }

    @Test
    public void manualCheckAfterDispatchRequiresAnotherObservedBarrier() throws Exception
    {
        Harness h = fullTrip(GemMine.SURFACE);
        h.contents[0] = 2;
        h.acceptBagDeposit = false;
        h.answerCheck = false;
        h.start();
        h.until(() -> h.count("Check") > 0);
        int checksBeforeManual = h.count("Check");
        int changesBeforeManual = h.countBagStateChanges();
        h.acceptOpen = false;
        h.acceptBagClose = false;

        h.fixture.manualBagAction("Check");
        h.fixture.chat(ChatMessageType.GAMEMESSAGE, "The gem bag is empty.");
        h.step();
        assertNoDepartureWithContents(h);
        h.fixture.chat(ChatMessageType.GAMEMESSAGE, h.checkMessage());
        h.step();
        assertNoDepartureWithContents(h);
        h.until(() -> h.countBagStateChanges() > changesBeforeManual);
        assertEquals(checksBeforeManual, h.count("Check"));

        h.completeLastBagStateChange();
        h.acceptOpen = true;
        h.acceptBagClose = true;
        h.until(() -> h.count("Check") > checksBeforeManual);
        assertNoDepartureWithContents(h);
        h.fixture.chat(ChatMessageType.GAMEMESSAGE, h.checkMessage());
        for (int tick = 0; tick < 4; tick++)
        {
            h.step();
        }
        assertNoDepartureWithContents(h);
    }

    @Test
    public void manualCheckDuringPendingBarrierWaitsAndChecksTheBagAgain() throws Exception
    {
        Harness h = fullTrip(GemMine.SURFACE);
        h.contents[0] = 2;
        h.acceptBagDeposit = false;
        h.answerCheck = false;
        h.acceptOpen = false;
        h.acceptBagClose = false;
        h.start();
        h.until(() -> h.bankEmptyActions == 1 && h.countBagStateChanges() == 1);
        assertTrue(h.has(OPEN_BAG));
        assertEquals(0, h.count("Check"));

        h.fixture.manualBagAction("Check");
        h.completeLastBagStateChange();
        h.fixture.chat(ChatMessageType.GAMEMESSAGE, "The gem bag is empty.");
        for (int tick = 0; tick < FourtifysGemMiningPlugin.PLAYER_BAG_WAIT_TICKS; tick++)
        {
            h.step();
        }
        assertEquals(1, h.countBagStateChanges(), "Wait for the player's own action to land first");
        assertEquals(0, h.count("Check"), "The player's own Check reply must not verify the bag");
        assertNoDepartureWithContents(h);

        h.acceptOpen = true;
        h.acceptBagClose = true;
        h.answerCheck = true;
        h.until(() -> h.count("Check") == 1);

        assertEquals(2, h.countBagStateChanges(), "Confirm the bag again with a fresh action");
        assertNoDepartureWithContents(h);
        assertFalse(h.fixture.plugin.getStatus().contains("retry"), h.fixture.plugin.getStatus());
    }

    @Test
    public void disablingDuringBagBarrierIgnoresLateCheckReply() throws Exception
    {
        Harness h = fullTrip(GemMine.SURFACE);
        h.contents[0] = 2;
        h.acceptBagDeposit = false;
        h.answerCheck = false;
        h.acceptOpen = false;
        h.acceptBagClose = false;
        h.start();
        h.until(() -> h.bankEmptyActions == 1 && h.countBagStateChanges() > 0);
        h.fixture.disable();
        int requests = h.fixture.world.requests.size();
        h.fixture.chat(ChatMessageType.GAMEMESSAGE, "The gem bag is empty.");
        h.step();

        assertEquals(requests, h.fixture.world.requests.size());
        assertNoDepartureWithContents(h);
    }

    private static void assertNoDepartureWithContents(Harness h)
    {
        assertEquals(2, h.contents[0]);
        assertTrue(h.has(OPEN_BAG) || h.has(CLOSED_BAG));
        assertTrue(h.has(PICKAXE));
        assertEquals(0, h.count("Mine"));
    }

    @Test
    public void replacedBagCannotDispatchAnOldQueuedAction() throws Exception
    {
        Harness h = new Harness(GemMine.SURFACE, new Item(PICKAXE, 1), new Item(CLOSED_BAG, 1));
        h.start();
        h.fixture.decisionOnly();
        h.fixture.world.inventory[1] = new Item(CHISEL, 1);
        h.fixture.world.inventoryWidgets();
        h.fixture.clientTick();
        assertTrue(h.fixture.world.requests.isEmpty());
        h.until(() -> h.count("Mine") > 0);
        assertFalse(h.has(CLOSED_BAG));
        assertTrue(h.has(CHISEL));
    }

    private static Harness fullTrip(GemMine mine) throws Exception
    {
        Item[] items = new Item[28];
        Arrays.fill(items, new Item(OPAL, 1));
        items[0] = new Item(PICKAXE, 1);
        items[1] = new Item(OPEN_BAG, 1);
        items[2] = new Item(CHISEL, 1);
        Harness h = new Harness(mine, items);
        if (mine == GemMine.SURFACE)
        {
            h.fixture.world.position = new WorldPoint(2840, 2951, 0);
            h.fixture.world.banker(7, new WorldPoint(2852, 2951, 0));
        }
        else
        {
            h.fixture.world.position = new WorldPoint(2840, 9385, 0);
            h.fixture.world.object(10530, "Bank deposit chest", new String[] {"Deposit"}, new WorldPoint(2841, 9385, 0));
        }
        return h;
    }

    private static final class Harness
    {
        private static final int[] BAGGABLE = {1623, 1621, 1619, 1617, 1631};
        private static final String[] NAMES = {"sapphire", "emerald", "ruby", "diamond", "dragonstone"};
        private final PluginTestFixture fixture;
        private final GemMine mine;
        private final int[] contents = new int[5];
        private final int[] banked = new int[5];
        private final boolean[] acceptsBagType = {true, true, true, true, true};
        private boolean acceptBagDeposit = true;
        private boolean acceptOpen = true;
        private boolean acceptBagClose = true;
        private boolean answerCheck = true;
        private boolean emptiedAtBank;
        private boolean checkedEmptyAfterBank;
        private boolean emptyAfterLooseDeposit;
        private int bankEmptyActions;
        private int handled;

        private Harness(GemMine mine, Item... inventory) throws Exception
        {
            PluginTestFixture.MutableConfig config = new PluginTestFixture.MutableConfig();
            config.mine = mine;
            fixture = new PluginTestFixture(config);
            this.mine = mine;
            fixture.world.inventory = inventory;
            fixture.world.inventoryWidgets();
        }

        private void start()
        {
            fixture.enableAndDrain();
        }

        private void until(BooleanSupplier done)
        {
            for (int tick = 0; tick < 180 && !done.getAsBoolean(); tick++)
            {
                step();
            }
            assertTrue(done.getAsBoolean(), "Timed out; status=" + fixture.plugin.getStatus() + ", actions=" + options());
        }

        private void step()
        {
            fixture.tick();
            while (handled < fixture.world.requests.size())
            {
                reply(fixture.world.requests.get(handled++));
            }
        }

        private void reply(Object[] request)
        {
            String action = (String) request[5];
            int widget = (Integer) request[1];
            int item = (Integer) request[4];
            if ("Open".equals(action) && item == CLOSED_BAG)
            {
                if (acceptOpen)
                {
                    fixture.world.setBagOpen(true);
                }
            }
            else if ("Close".equals(action) && item == OPEN_BAG)
            {
                if (acceptBagClose)
                {
                    fixture.world.setBagOpen(false);
                }
            }
            else if ("Fill".equals(action) && (item == CLOSED_BAG || item == OPEN_BAG))
            {
                fill();
            }
            else if ("Check".equals(action) && (item == CLOSED_BAG || item == OPEN_BAG))
            {
                if (answerCheck && emptiedAtBank && Arrays.stream(contents).sum() == 0)
                {
                    checkedEmptyAfterBank = true;
                }
                if (answerCheck)
                {
                    fixture.chat(ChatMessageType.GAMEMESSAGE, checkMessage());
                }
            }
            else if ("Bank".equals(action) || "Deposit".equals(action) && item == -1)
            {
                showBank();
            }
            else if ("Deposit-All".equals(action))
            {
                assertTrue(isShiloGem(item), "Only the seven Shilo gems may be deposited");
                removeInventory(item);
                showBank();
            }
            else if ("Empty".equals(action) && (widget == (15 << 16 | 3) || widget == (192 << 16 | 24)))
            {
                assertTrue(item == CLOSED_BAG || item == OPEN_BAG);
                assertEquals(MenuAction.CC_OP, request[2]);
                assertEquals(9, request[3]);
                bankEmptyActions++;
                emptyAfterLooseDeposit = !has(OPAL);
                if (acceptBagDeposit)
                {
                    for (int i = 0; i < contents.length; i++)
                    {
                        if (acceptsBagType[i])
                        {
                            banked[i] += contents[i];
                            contents[i] = 0;
                        }
                    }
                }
                emptiedAtBank = true;
            }
            else if ("Close".equals(action) && item == -1)
            {
                fixture.world.closeBanking();
            }
        }

        private void showBank()
        {
            if (mine.isUnderground())
            {
                fixture.world.depositBoxWithGems();
            }
            else
            {
                fixture.world.bankWithGems();
            }
        }

        private String checkMessage()
        {
            if (Arrays.stream(contents).sum() == 0)
            {
                return "The gem bag is empty.";
            }
            List<String> values = new ArrayList<>();
            for (int i = 0; i < contents.length; i++)
            {
                values.add(NAMES[i] + ": " + contents[i]);
            }
            return String.join(" / ", values);
        }

        private void fill()
        {
            List<Item> kept = new ArrayList<>();
            boolean changed = false;
            for (Item item : fixture.world.inventory)
            {
                int index = indexOf(BAGGABLE, item.getId());
                if (index >= 0 && contents[index] < 60)
                {
                    contents[index]++;
                    changed = true;
                }
                else
                {
                    kept.add(item);
                }
            }
            fixture.world.inventory = kept.toArray(new Item[0]);
            fixture.world.inventoryWidgets();
            fixture.chat(ChatMessageType.GAMEMESSAGE, changed ? "You add the gems to your bag."
                : "Your gem bag cannot hold any more of those gems.");
        }

        private void removeInventory(int itemId)
        {
            fixture.world.inventory = Arrays.stream(fixture.world.inventory).filter(item -> item.getId() != itemId).toArray(Item[]::new);
        }

        private void appendInventory(Item item)
        {
            Item[] old = fixture.world.inventory;
            fixture.world.inventory = Arrays.copyOf(old, old.length + 1);
            fixture.world.inventory[old.length] = item;
            fixture.world.inventoryWidgets();
        }

        private boolean has(int itemId)
        {
            return Arrays.stream(fixture.world.inventory).anyMatch(item -> item.getId() == itemId);
        }

        private int count(String option)
        {
            return (int) fixture.world.requests.stream().filter(request -> option.equals(request[5])).count();
        }

        private int countBagStateChanges()
        {
            return (int) fixture.world.requests.stream().filter(request -> {
                int item = (Integer) request[4];
                String option = (String) request[5];
                return item == CLOSED_BAG && "Open".equals(option) || item == OPEN_BAG && "Close".equals(option);
            }).count();
        }

        private void completeLastBagStateChange()
        {
            for (int i = fixture.world.requests.size() - 1; i >= 0; i--)
            {
                Object[] request = fixture.world.requests.get(i);
                int item = (Integer) request[4];
                String option = (String) request[5];
                if (item == CLOSED_BAG && "Open".equals(option) || item == OPEN_BAG && "Close".equals(option))
                {
                    fixture.world.setBagOpen("Open".equals(option));
                    return;
                }
            }
            throw new AssertionError("No gem bag state change to complete");
        }

        private int first(String option)
        {
            for (int i = 0; i < fixture.world.requests.size(); i++)
            {
                if (option.equals(fixture.world.requests.get(i)[5]))
                {
                    return i;
                }
            }
            return Integer.MAX_VALUE;
        }

        private List<String> options()
        {
            List<String> result = new ArrayList<>();
            for (Object[] request : fixture.world.requests)
            {
                result.add((String) request[5]);
            }
            return result;
        }

        private static int indexOf(int[] ids, int itemId)
        {
            for (int i = 0; i < ids.length; i++)
            {
                if (ids[i] == itemId)
                {
                    return i;
                }
            }
            return -1;
        }

        private static boolean isShiloGem(int itemId)
        {
            return itemId == 1625 || itemId == 1627 || itemId == 1629 || itemId == 1623
                || itemId == 1621 || itemId == 1619 || itemId == 1617;
        }
    }
}
