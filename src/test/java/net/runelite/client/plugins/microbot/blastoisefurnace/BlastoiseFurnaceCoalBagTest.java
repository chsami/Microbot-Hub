package net.runelite.client.plugins.microbot.blastoisefurnace;

import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectQueryable;
import net.runelite.client.plugins.microbot.blastoisefurnace.enums.Bars;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static net.runelite.api.gameval.ObjectID.BLAST_FURNACE_CONVEYER_BELT_CLICKABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlastoiseFurnaceCoalBagTest {

    private static final int WALK_POLLS = 2 * BlastoiseFurnaceScript.CONVEYOR_IDLE_TIMEOUT_MS / BlastoiseFurnaceScript.CONVEYOR_POLL_MS;

    private MockedStatic<Microbot> microbot;
    private MockedStatic<Rs2Inventory> inventory;
    private MockedStatic<Rs2Bank> bank;
    private MockedStatic<Rs2Player> player;
    private MockedStatic<Rs2Dialogue> dialogue;
    private MockedStatic<Rs2Widget> widget;
    private MockedStatic<Global> global;
    private Rs2TileObjectQueryable query;
    private final AtomicInteger ore = new AtomicInteger();
    private final AtomicInteger walkPolls = new AtomicInteger();
    private final AtomicBoolean belted = new AtomicBoolean();
    private final AtomicInteger beltAcceptsLoads = new AtomicInteger(Integer.MAX_VALUE);
    private final AtomicBoolean bagEmpties = new AtomicBoolean(true);

    @BeforeEach
    void setUp() {
        microbot = mockStatic(Microbot.class);
        inventory = mockStatic(Rs2Inventory.class);
        bank = mockStatic(Rs2Bank.class);
        player = mockStatic(Rs2Player.class);
        dialogue = mockStatic(Rs2Dialogue.class);
        widget = mockStatic(Rs2Widget.class);
        global = mockStatic(Global.class);

        Rs2TileObjectCache cache = mock(Rs2TileObjectCache.class);
        query = mock(Rs2TileObjectQueryable.class, RETURNS_SELF);
        when(cache.query()).thenReturn(query);
        microbot.when(Microbot::getRs2TileObjectCache).thenReturn(cache);

        when(query.interact(BLAST_FURNACE_CONVEYER_BELT_CLICKABLE, "Put-ore-on")).thenAnswer(invocation -> {
            walkPolls.set(WALK_POLLS);
            belted.set(false);
            return true;
        });
        player.when(Rs2Player::isMoving).thenAnswer(invocation -> {
            if (walkPolls.get() > 0) {
                walkPolls.decrementAndGet();
                return true;
            }
            if (!belted.getAndSet(true) && beltAcceptsLoads.getAndDecrement() > 0) {
                ore.set(0);
            }
            return false;
        });
        inventory.when(() -> Rs2Inventory.count("ore")).thenAnswer(invocation -> ore.get());
        inventory.when(() -> Rs2Inventory.interact(BlastoiseFurnaceScript.COAL_BAGS, "Empty")).thenAnswer(invocation -> {
            if (bagEmpties.get()) {
                ore.set(27);
            }
            return true;
        });
        inventory.when(() -> Rs2Inventory.waitForInventoryChanges(anyInt())).thenAnswer(invocation -> bagEmpties.get());
        global.when(() -> Global.sleepUntilTrue(any(BooleanSupplier.class), any(BooleanSupplier.class), anyInt(), anyInt()))
                .thenAnswer(invocation -> {
                    BooleanSupplier awaited = invocation.getArgument(0);
                    BooleanSupplier reset = invocation.getArgument(1);
                    int poll = invocation.getArgument(2);
                    int timeout = invocation.getArgument(3);
                    int elapsed = 0;
                    while (elapsed < timeout) {
                        if (reset.getAsBoolean()) {
                            elapsed = 0;
                        }
                        if (awaited.getAsBoolean()) {
                            return true;
                        }
                        elapsed += poll;
                    }
                    return false;
                });
    }

    @AfterEach
    void tearDown() {
        global.close();
        widget.close();
        dialogue.close();
        player.close();
        bank.close();
        inventory.close();
        microbot.close();
    }

    @Test
    void withdrawsTheOpenCoalBagWhenOnlyItIsInTheBank() {
        bank.when(() -> Rs2Bank.hasItem(ItemID.COAL_BAG_OPEN)).thenReturn(true);

        assertTrue(script().ensureCoalBag());

        bank.verify(() -> Rs2Bank.withdrawItem(ItemID.COAL_BAG_OPEN), times(1));
        bank.verify(() -> Rs2Bank.withdrawItem(ItemID.COAL_BAG), never());
        microbot.verify(() -> Microbot.stopPlugin(nullable(Plugin.class)), never());
    }

    @Test
    void acceptsTheOpenCoalBagAlreadyInTheInventory() {
        inventory.when(() -> Rs2Inventory.contains(ItemID.COAL_BAG, ItemID.COAL_BAG_OPEN)).thenReturn(true);

        assertTrue(script().ensureCoalBag());

        bank.verify(() -> Rs2Bank.withdrawItem(anyInt()), never());
        microbot.verify(() -> Microbot.stopPlugin(nullable(Plugin.class)), never());
    }

    @Test
    void stillWithdrawsTheClosedCoalBag() {
        bank.when(() -> Rs2Bank.hasItem(ItemID.COAL_BAG)).thenReturn(true);

        assertTrue(script().ensureCoalBag());

        bank.verify(() -> Rs2Bank.withdrawItem(ItemID.COAL_BAG), times(1));
    }

    @Test
    void stopsWhenNoCoalBagIsInTheInventoryOrBank() {
        assertFalse(script().ensureCoalBag());

        microbot.verify(() -> Microbot.showMessage(anyString()), times(1));
        microbot.verify(() -> Microbot.stopPlugin(nullable(Plugin.class)), times(1));
        bank.verify(() -> Rs2Bank.withdrawItem(anyInt()), never());
    }

    @Test
    void keepsBothCoalBagVariantsWhenDepositing() {
        assertTrue(Arrays.asList(BlastoiseFurnaceScript.KEEP_ITEMS).contains(ItemID.COAL_BAG));
        assertTrue(Arrays.asList(BlastoiseFurnaceScript.KEEP_ITEMS).contains(ItemID.COAL_BAG_OPEN));
    }

    @Test
    void waitsForAWalkLongerThanTheIdleTimeoutBeforeEmptyingTheCoalBag() {
        ore.set(27);

        assertTrue(coalBagScript().depositOre());

        verify(query, times(2)).interact(BLAST_FURNACE_CONVEYER_BELT_CLICKABLE, "Put-ore-on");
        inventory.verify(() -> Rs2Inventory.interact(BlastoiseFurnaceScript.COAL_BAGS, "Empty"), times(1));
        assertEquals(0, ore.get());
    }

    @Test
    void reportsNoDepositWhenTheOreStaysInTheInventory() {
        ore.set(27);
        beltAcceptsLoads.set(0);

        assertFalse(coalBagScript().putOreOnConveyorBelt());
    }

    @Test
    void keepsTheCoalBagFullWhenTheOreDoesNotGoOnTheBelt() {
        ore.set(27);
        beltAcceptsLoads.set(0);

        assertFalse(coalBagScript().depositOre());

        verify(query, times(1)).interact(BLAST_FURNACE_CONVEYER_BELT_CLICKABLE, "Put-ore-on");
        inventory.verify(() -> Rs2Inventory.interact(eq(BlastoiseFurnaceScript.COAL_BAGS), anyString()), never());
        assertEquals(27, ore.get());
    }

    @Test
    void doesNotClickTheBeltAgainWhenTheCoalBagDoesNotEmpty() {
        ore.set(27);
        bagEmpties.set(false);

        assertTrue(coalBagScript().depositOre());

        verify(query, times(1)).interact(BLAST_FURNACE_CONVEYER_BELT_CLICKABLE, "Put-ore-on");
        inventory.verify(() -> Rs2Inventory.interact(BlastoiseFurnaceScript.COAL_BAGS, "Empty"), times(1));
        assertEquals(0, ore.get());
    }

    @Test
    void reportsNoDepositWhenTheCoalFromTheBagStaysInTheInventory() {
        ore.set(27);
        beltAcceptsLoads.set(1);

        assertFalse(coalBagScript().depositOre());

        verify(query, times(2)).interact(BLAST_FURNACE_CONVEYER_BELT_CLICKABLE, "Put-ore-on");
        assertEquals(27, ore.get());
    }

    @Test
    void skipsTheCoalBagForBarsThatDoNotUseIt() {
        ore.set(27);

        assertTrue(script(Bars.GOLD_BAR).depositOre());

        verify(query, times(1)).interact(BLAST_FURNACE_CONVEYER_BELT_CLICKABLE, "Put-ore-on");
        inventory.verify(() -> Rs2Inventory.interact(eq(BlastoiseFurnaceScript.COAL_BAGS), anyString()), never());
    }

    @Test
    void reportsNoDepositForBarsWithoutACoalBagWhenTheOreStaysInTheInventory() {
        ore.set(27);
        beltAcceptsLoads.set(0);

        assertFalse(script(Bars.GOLD_BAR).depositOre());

        inventory.verify(() -> Rs2Inventory.interact(eq(BlastoiseFurnaceScript.COAL_BAGS), anyString()), never());
        assertEquals(27, ore.get());
    }

    private static BlastoiseFurnaceScript script() {
        return new BlastoiseFurnaceScript(null, null);
    }

    private static BlastoiseFurnaceScript coalBagScript() {
        return script(Bars.MITHRIL_BAR);
    }

    private static BlastoiseFurnaceScript script(Bars bar) {
        BlastoiseFurnaceConfig config = mock(BlastoiseFurnaceConfig.class);
        when(config.getBars()).thenReturn(bar);
        return new BlastoiseFurnaceScript(null, config);
    }
}
