package net.runelite.client.plugins.microbot.fourtifysbarrows.util.action;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Prayer;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.WallObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.fourtifysbarrows.config.BarrowsConfig;
import net.runelite.client.plugins.microbot.fourtifysbarrows.config.TravelMethod;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BarrowsInteractionTest
{
    @Test
    public void bankMainRemainsOpenWithoutItsInventoryAndClosesTheObservedWidget() throws Exception
    {
        Fixture fixture = new Fixture();
        int root = 12 << 16;
        int main = root | 1;
        int close = root | 2;
        Widget button = fixture.widget(close, 11, -1, new String[] {"Close"}, null);
        fixture.widgets.put(main, fixture.widget(main, -1, -1, null, null));
        fixture.widgets.put(root, fixture.widget(root, -1, -1, null, new Widget[] {button}));
        fixture.busy = true;
        assertTrue(fixture.actions.bankingOpen());
        assertFalse(fixture.actions.bankCloseObserved());
        AtomicInteger closed = new AtomicInteger();
        assertTrue(fixture.actions.closeBank(closed::incrementAndGet));
        fixture.actions.dispatch();
        assertEquals(1, closed.get());
        assertEquals(1, fixture.menu.size());
        assertEquals(close, fixture.menu.get(0)[1]);
        assertEquals(11, fixture.menu.get(0)[0]);
        assertEquals("Close", fixture.menu.get(0)[5]);
        fixture.widgets.remove(main);
        assertFalse(fixture.actions.bankingOpen());
        assertFalse(fixture.actions.bankCloseObserved());
        fixture.busy = false;
        assertTrue(fixture.actions.bankCloseObserved());
    }

    @Test
    public void cancelledBankCloseCannotDispatchTheCapturedWidget() throws Exception
    {
        Fixture fixture = new Fixture();
        int root = 12 << 16;
        Widget button = fixture.widget(root | 2, 11, -1, new String[] {"Close"}, null);
        fixture.widgets.put(root | 1, fixture.widget(root | 1, -1, -1, null, null));
        fixture.widgets.put(root, fixture.widget(root, -1, -1, null, new Widget[] {button}));
        AtomicInteger closed = new AtomicInteger();
        assertTrue(fixture.actions.closeBank(closed::incrementAndGet));
        fixture.actions.reset();
        fixture.actions.dispatch();
        assertEquals(0, closed.get());
        assertTrue(fixture.menu.isEmpty());
    }

    @Test
    public void hiddenBankBusyFlagRejectsTabletUntilTheServerAcknowledgesClose() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.inventoryItems = new Item[] {new Item(19629, 2)};
        int inventory = 149 << 16;
        Widget tablet = fixture.widget(inventory, 0, 19629, new String[] {"Break"}, null);
        fixture.widgets.put(inventory, fixture.widget(inventory, -1, -1, null, new Widget[] {tablet}));
        fixture.busy = true;
        assertTrue(fixture.actions.travelProblem(TravelMethod.BARROWS_TABLET).contains("Close the bank"));
        assertFalse(fixture.actions.requestTravel(TravelMethod.BARROWS_TABLET, null));
        fixture.actions.dispatch();
        assertTrue(fixture.menu.isEmpty());
        fixture.busy = false;
        assertTrue(fixture.actions.requestTravel(TravelMethod.BARROWS_TABLET, null));
        fixture.busy = true;
        fixture.actions.dispatch();
        assertTrue(fixture.menu.isEmpty());
        fixture.busy = false;
        assertTrue(fixture.actions.requestTravel(TravelMethod.BARROWS_TABLET, null));
        fixture.actions.dispatch();
        assertEquals(1, fixture.menu.size());
        assertEquals("Break", fixture.menu.get(0)[5]);
    }

    @Test
    public void feroxPoolUsesGuardedDrinkAndRejectsChangedStateBeforeDispatch() throws Exception
    {
        for (int change = 0; change < 6; change++)
        {
            Fixture fixture = new Fixture();
            fixture.playerPosition = new WorldPoint(3130, 3634, 0);
            fixture.energy = 9_000;
            GameObject pool = fixture.gameObject(39651, new WorldPoint(3128, 3633, 0));
            fixture.liveObjects = new GameObject[] {pool};
            fixture.actions.requestBank();
            AtomicInteger started = new AtomicInteger();
            assertTrue(fixture.actions.drinkFeroxPool(started::incrementAndGet));
            assertTrue(fixture.menu.isEmpty());
            if (change == 1)
            {
                fixture.actions.completeTrip();
            }
            else if (change == 2)
            {
                fixture.energy = 10_000;
            }
            else if (change == 3)
            {
                fixture.liveObjects = new GameObject[] {fixture.gameObject(39651, new WorldPoint(3128, 3633, 0))};
            }
            else if (change == 4)
            {
                fixture.loggedIn = false;
            }
            else if (change == 5)
            {
                int bank = (15 << 16) | 3;
                fixture.widgets.put(bank, fixture.widget(bank, -1, -1, null, null));
            }
            fixture.actions.dispatch();
            assertEquals(change == 0 ? 1 : 0, started.get());
            assertEquals(change == 0 ? 1 : 0, fixture.menu.size());
            if (change == 0)
            {
                assertEquals(39651, fixture.menu.get(0)[3]);
                assertEquals("Drink", fixture.menu.get(0)[5]);
            }
        }
    }

    @Test
    public void feroxBankChestUsesItsObservedUseAction() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.playerPosition = new WorldPoint(3130, 3634, 0);
        fixture.liveObjects = new GameObject[] {fixture.gameObject(26711, new WorldPoint(3130, 3632, 0))};
        assertTrue(fixture.actions.openBank(null));
        fixture.actions.dispatch();
        assertEquals(1, fixture.menu.size());
        assertEquals(26711, fixture.menu.get(0)[3]);
        assertEquals("Use", fixture.menu.get(0)[5]);
    }

    @Test
    public void travelLegsMustBeInInventoryEvenWhenEquipped() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.equipmentItems = new Item[] {new Item(13114, 1)};
        assertTrue(fixture.actions.travelProblem(TravelMethod.MORYTANIA_LEGS_3).contains("inventory"));
        fixture.inventoryItems = new Item[] {new Item(13114, 1)};
        assertEquals(null, fixture.actions.travelProblem(TravelMethod.MORYTANIA_LEGS_3));
    }

    @Test
    public void aQueuedTabletRequestCannotRunAfterTheTravelMethodChanges() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.inventoryItems = new Item[] {new Item(19629, 2)};
        int id = 149 << 16;
        Widget tablet = fixture.widget(id, 0, 19629, new String[] {"Break"}, null);
        fixture.widgets.put(id, fixture.widget(id, -1, -1, null, new Widget[] {tablet}));
        assertTrue(fixture.actions.requestTravel(TravelMethod.BARROWS_TABLET, null));
        fixture.selected = TravelMethod.MORTTON_SCROLL;
        fixture.actions.dispatch();
        assertTrue(fixture.menu.isEmpty());
    }

    @Test
    public void carriedRingTeleportsThroughItsFeroxSubOptionWithoutRubbing() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.inventoryItems = new Item[] {new Item(2558, 1)};
        fixture.subops = ringSubOptions();
        int id = 149 << 16;
        Widget ring = fixture.widget(id, 0, 2558, new String[] {"Wear", "Rub", "Drop", "Examine"}, null);
        fixture.widgets.put(id, fixture.widget(id, -1, -1, null, new Widget[] {ring}));
        AtomicInteger observed = new AtomicInteger();
        assertTrue(fixture.actions.requestEmergencyTravel(observed::incrementAndGet));
        assertTrue(fixture.menu.isEmpty());
        fixture.actions.dispatch();
        assertEquals(1, fixture.menu.size());
        assertEquals(MenuAction.CC_OP, fixture.menu.get(0)[2]);
        assertEquals(3 * 65536 + 2, fixture.menu.get(0)[3], "the third Rub sub-option; Rub is the widget's second action");
        assertEquals("Ferox Enclave", fixture.menu.get(0)[5]);
        assertEquals(1, observed.get());
    }

    @Test
    public void aSubOptionTheItemDoesNotListIsNeverSent() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.inventoryItems = new Item[] {new Item(2558, 1)};
        fixture.subops = new String[5][];
        int id = 149 << 16;
        Widget ring = fixture.widget(id, 0, 2558, new String[] {"Wear", "Rub", "Drop", "Examine"}, null);
        fixture.widgets.put(id, fixture.widget(id, -1, -1, null, new Widget[] {ring}));
        assertFalse(fixture.actions.requestEmergencyTravel(null));
        fixture.actions.dispatch();
        assertTrue(fixture.menu.isEmpty());
        assertEquals(0, fixture.actions.getRequestsDispatched());
    }

    @Test
    public void aRingWithoutTheRubActionIsNeverSent() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.inventoryItems = new Item[] {new Item(2558, 1)};
        fixture.subops = ringSubOptions();
        int id = 149 << 16;
        Widget ring = fixture.widget(id, 0, 2558, new String[] {"Wear", "Drop", "Examine"}, null);
        fixture.widgets.put(id, fixture.widget(id, -1, -1, null, new Widget[] {ring}));
        assertFalse(fixture.actions.requestEmergencyTravel(null));
        fixture.actions.dispatch();
        assertTrue(fixture.menu.isEmpty());
    }

    private static String[][] ringSubOptions()
    {
        String[][] subops = new String[5][];
        subops[3] = new String[] {"Emir's Arena", "Castle Wars", "Ferox Enclave", "Fortis Colosseum"};
        return subops;
    }

    @Test
    public void protectionPrayersQueueTheCorrectMicrobotProtectionRequests() throws Exception
    {
        Prayer[] prayers = {Prayer.PROTECT_FROM_MAGIC, Prayer.PROTECT_FROM_MISSILES, Prayer.PROTECT_FROM_MELEE};
        for (int index = 0; index < prayers.length; index++)
        {
            Fixture fixture = new Fixture();
            int id = (541 << 16) | (21 + index);
            fixture.widgets.put(id, fixture.widget(id, -1, -1, null, null));
            AtomicInteger observed = new AtomicInteger();
            assertTrue(fixture.actions.protect(prayers[index], true, observed::incrementAndGet));
            assertTrue(fixture.menu.isEmpty());
            assertEquals(0, observed.get());
            fixture.actions.dispatch();
            assertEquals(1, fixture.menu.size());
            assertEquals(id, fixture.menu.get(0)[1]);
            assertEquals(MenuAction.CC_OP, fixture.menu.get(0)[2]);
            assertEquals(1, fixture.menu.get(0)[3]);
            assertEquals(1, observed.get());
            assertEquals(1, fixture.actions.getRequestsDispatched());
        }
    }

    @Test
    public void hiddenPrayerTabIsOpenedByTheSameQueuedToggle() throws Exception
    {
        Fixture fixture = new Fixture();
        AtomicInteger observed = new AtomicInteger();
        assertTrue(fixture.actions.protect(Prayer.PROTECT_FROM_MELEE, true, observed::incrementAndGet));
        assertTrue(fixture.scripts.isEmpty());
        assertTrue(fixture.menu.isEmpty());
        fixture.actions.dispatch();
        assertEquals(1, fixture.scripts.size());
        assertEquals(915, fixture.scripts.get(0)[0]);
        assertEquals(InterfaceTab.PRAYER.getVarcIntIndex(), fixture.scripts.get(0)[1]);
        assertEquals(1, fixture.menu.size());
        assertEquals((541 << 16) | 23, fixture.menu.get(0)[1]);
        assertEquals(1, observed.get());
    }

    @Test
    public void changingPrayerStateBeforeDispatchRejectsAnUnneededToggle() throws Exception
    {
        Fixture fixture = new Fixture();
        int prayer = (541 << 16) | 23;
        fixture.widgets.put(prayer, fixture.widget(prayer, -1, -1, null, null));
        assertTrue(fixture.actions.protect(Prayer.PROTECT_FROM_MELEE, true, null));
        fixture.prayerActive = true;
        fixture.actions.dispatch();
        assertTrue(fixture.menu.isEmpty());
        assertEquals(0, fixture.actions.getRequestsDispatched());
    }

    @Test
    public void hiddenInventoryQueuesTabOnlyThenUsesTheObservedLiveItem() throws Exception
    {
        Fixture fixture = new Fixture();
        AtomicInteger consumed = new AtomicInteger();
        fixture.actions.inventory(385, "Eat", consumed::incrementAndGet);
        assertTrue(fixture.scripts.isEmpty());
        fixture.actions.dispatch();
        assertEquals(1, fixture.scripts.size());
        assertEquals(915, fixture.scripts.get(0)[0]);
        assertEquals(InterfaceTab.INVENTORY.getVarcIntIndex(), fixture.scripts.get(0)[1]);
        assertTrue(fixture.menu.isEmpty());
        assertEquals(0, consumed.get());
        int id = 149 << 16;
        Widget food = fixture.widget(id, 0, 385, new String[] {"Eat"}, null);
        fixture.widgets.put(id, fixture.widget(id, -1, -1, null, new Widget[] {food}));
        assertTrue(fixture.actions.inventory(385, "Eat", consumed::incrementAndGet));
        assertTrue(fixture.menu.isEmpty());
        fixture.actions.dispatch();
        assertEquals(1, fixture.menu.size());
        assertEquals(385, fixture.menu.get(0)[4]);
        assertEquals(1, consumed.get());
    }

    @Test
    public void wallAtTwentyTilesQueuesAndDispatchesWithoutAnAdjacencyRequirement() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.wallDistance = 20;
        AtomicInteger crossed = new AtomicInteger();
        assertTrue(fixture.actions.tileAction(fixture.wall, "Open", crossed::incrementAndGet));
        assertTrue(fixture.menu.isEmpty());
        fixture.actions.dispatch();
        assertEquals(1, fixture.menu.size());
        assertEquals(70, fixture.menu.get(0)[0]);
        assertEquals(50, fixture.menu.get(0)[1]);
        assertEquals(MenuAction.GAME_OBJECT_FIRST_OPTION, fixture.menu.get(0)[2]);
        assertEquals(20681, fixture.menu.get(0)[3]);
        assertEquals(1, crossed.get());
        fixture.wallDistance = 21;
        assertFalse(fixture.actions.tileAction(fixture.wall, "Open", null));
    }

    @Test
    public void staleWallIdentityRangeAndPlaneRejectDispatch() throws Exception
    {
        for (int mutation = 0; mutation < 3; mutation++)
        {
            Fixture fixture = new Fixture();
            AtomicInteger crossed = new AtomicInteger();
            assertTrue(fixture.actions.tileAction(fixture.wall, "Open", crossed::incrementAndGet));
            if (mutation == 0)
            {
                fixture.liveWall = fixture.wall();
            }
            else if (mutation == 1)
            {
                fixture.wallDistance = 21;
            }
            else
            {
                fixture.wallPlane = 1;
            }
            fixture.actions.dispatch();
            assertTrue(fixture.menu.isEmpty());
            assertEquals(0, crossed.get());
            assertEquals(0, fixture.actions.getRequestsDispatched());
        }
    }

    @Test
    public void transformedWallActionIsRecheckedAtDispatch() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.impostor = true;
        assertTrue(fixture.actions.tileAction(fixture.wall, "Open", null));
        fixture.wallOptions = new String[] {"Close"};
        fixture.actions.dispatch();
        assertTrue(fixture.menu.isEmpty());
        assertFalse(fixture.actions.tileAction(fixture.wall, "Open", null));
        assertEquals(0, fixture.actions.getRequestsDispatched());
    }

    @Test
    public void logoutRejectsQueuedTabAndWallInteractions() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.actions.inventory(385, "Eat", null);
        assertTrue(fixture.actions.tileAction(fixture.wall, "Open", null));
        fixture.loggedIn = false;
        fixture.actions.dispatch();
        fixture.actions.dispatch();
        assertTrue(fixture.scripts.isEmpty());
        assertTrue(fixture.menu.isEmpty());
    }

    @Test
    public void notedWithdrawalFailsClearlyBeforeSendingAnAction() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.noted = true;
        assertFalse(fixture.actions.withdrawOne(385, null));
        fixture.actions.dispatch();
        assertTrue(fixture.menu.isEmpty());
        assertFalse(fixture.actions.ready());
        assertTrue(fixture.actions.getStatus().contains("withdrawal mode to Item"));
    }

    @Test
    public void notedModeChangedAfterAdmissionRejectsTheWithdrawal() throws Exception
    {
        Fixture fixture = new Fixture();
        int id = InterfaceID.Bankmain.ITEMS;
        Widget item = fixture.widget(id, 0, 385, new String[] {"Withdraw-1"}, null);
        fixture.widgets.put(id, fixture.widget(id, -1, -1, null, new Widget[] {item}));
        fixture.widgets.put((15 << 16) | 3, fixture.widget((15 << 16) | 3, -1, -1, null, null));
        assertTrue(fixture.actions.withdrawOne(385, null));
        fixture.noted = true;
        fixture.actions.dispatch();
        assertTrue(fixture.menu.isEmpty());
        assertEquals(0, fixture.actions.getRequestsDispatched());
    }

    @Test
    public void bankPlaceholderWithZeroQuantityCannotSupplyPotions() throws Exception
    {
        Fixture fixture = new Fixture();
        int id = InterfaceID.Bankmain.ITEMS;
        Widget item = fixture.widget(id, 0, 2434, new String[] {"Withdraw-1"}, null);
        fixture.widgets.put(id, fixture.widget(id, -1, -1, null, new Widget[] {item}));
        assertTrue(fixture.actions.bankHas(2434));
        fixture.bankQuantity = 0;
        assertFalse(fixture.actions.bankHas(2434));
    }

    @Test
    public void bankDiscoveryAndWithdrawalUseTheItemGridBesideTheScrollbar() throws Exception
    {
        Fixture fixture = new Fixture();
        assertEquals((12 << 16) | 12, InterfaceID.Bankmain.ITEMS);
        assertEquals((12 << 16) | 13, InterfaceID.Bankmain.SCROLLBAR);
        int id = InterfaceID.Bankmain.ITEMS;
        Widget item = fixture.widget(id, 0, 385, new String[] {"Withdraw-1"}, null);
        fixture.widgets.put(id, fixture.widget(id, -1, -1, null, new Widget[] {item}));
        int scrollbar = InterfaceID.Bankmain.SCROLLBAR;
        Widget decoration = fixture.widget(scrollbar, 0, 2434, new String[] {"Withdraw-1"}, null);
        fixture.widgets.put(scrollbar, fixture.widget(scrollbar, -1, -1, null, new Widget[] {decoration}));
        fixture.widgets.put((15 << 16) | 3, fixture.widget((15 << 16) | 3, -1, -1, null, null));
        AtomicInteger withdrawn = new AtomicInteger();

        assertTrue(fixture.actions.bankHas(385));
        assertFalse(fixture.actions.bankHas(2434));
        assertTrue(fixture.actions.withdrawOne(385, withdrawn::incrementAndGet));
        assertTrue(fixture.menu.isEmpty());
        fixture.actions.dispatch();

        assertEquals(1, fixture.menu.size());
        assertEquals(id, fixture.menu.get(0)[1]);
        assertEquals(385, fixture.menu.get(0)[4]);
        assertEquals("Withdraw-1", fixture.menu.get(0)[5]);
        assertEquals(1, withdrawn.get());
    }

    @Test
    public void aBulkWithdrawalUsesWithdrawXWhenTheBankHoldsTheWholeAmount() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.bankQuantity = 5;
        int id = InterfaceID.Bankmain.ITEMS;
        Widget item = fixture.widget(id, 0, 385, new String[] {"Withdraw-1", null, null, null, "Withdraw-X"}, null);
        fixture.widgets.put(id, fixture.widget(id, -1, -1, null, new Widget[] {item}));
        fixture.widgets.put((15 << 16) | 3, fixture.widget((15 << 16) | 3, -1, -1, null, null));

        assertEquals(5, fixture.actions.bankCount(385));
        assertFalse(fixture.actions.withdraw(385, 6, null), "More than the bank holds");
        assertTrue(fixture.actions.withdraw(385, 3, null));
        fixture.actions.dispatch();

        assertEquals(1, fixture.menu.size());
        assertEquals("Withdraw-X", fixture.menu.get(0)[5]);
    }

    @Test
    public void scrollbarChildrenCannotReplaceAMissingBankItemGrid() throws Exception
    {
        Fixture fixture = new Fixture();
        int id = InterfaceID.Bankmain.SCROLLBAR;
        Widget decoration = fixture.widget(id, 0, 385, new String[] {"Withdraw-1"}, null);
        fixture.widgets.put(id, fixture.widget(id, -1, -1, null, new Widget[] {decoration}));
        fixture.widgets.put((15 << 16) | 3, fixture.widget((15 << 16) | 3, -1, -1, null, null));

        assertFalse(fixture.actions.bankHas(385));
        assertFalse(fixture.actions.withdrawOne(385, null));
        fixture.actions.dispatch();
        assertTrue(fixture.menu.isEmpty());
        assertEquals(0, fixture.actions.getRequestsDispatched());
    }

    @Test
    public void replacedBankItemGridRejectsAQueuedWithdrawal() throws Exception
    {
        Fixture fixture = new Fixture();
        int id = InterfaceID.Bankmain.ITEMS;
        Widget item = fixture.widget(id, 0, 385, new String[] {"Withdraw-1"}, null);
        fixture.widgets.put(id, fixture.widget(id, -1, -1, null, new Widget[] {item}));
        fixture.widgets.put((15 << 16) | 3, fixture.widget((15 << 16) | 3, -1, -1, null, null));
        assertTrue(fixture.actions.withdrawOne(385, null));
        fixture.widgets.put(id, fixture.widget(id, -1, -1, null, new Widget[] {item}));

        fixture.actions.dispatch();
        assertTrue(fixture.menu.isEmpty());
        assertEquals(0, fixture.actions.getRequestsDispatched());
    }

    @Test
    public void puzzleAndRewardButtonsRequestTheirSemanticDefaultActions() throws Exception
    {
        int[] ids = {(25 << 16) | 12};
        String[] options = {"Select"};
        for (int index = 0; index < ids.length; index++)
        {
            Fixture fixture = new Fixture();
            int id = ids[index];
            String option = options[index];
            fixture.widgets.put(id, fixture.widget(id, -1, -1, new String[] {option}, null));
            assertTrue(fixture.actions.widget(id, option, () -> true, null));
            assertTrue(fixture.menu.isEmpty());
            fixture.actions.dispatch();
            assertEquals(1, fixture.menu.size());
            assertEquals(id, fixture.menu.get(0)[1]);
            assertEquals(option, fixture.menu.get(0)[5]);
        }
    }

    @Test
    public void rewardsCloseClicksTheSteelborderDynamicCloseChild() throws Exception
    {
        Fixture fixture = new Fixture();
        int frameId = (155 << 16) | 1;
        Widget[] children = new Widget[12];
        for (int index = 0; index < 11; index++)
        {
            children[index] = fixture.widget(frameId, index, -1, null, null);
        }
        children[11] = fixture.widget(frameId, 11, -1, new String[] {"Close"}, null);
        fixture.widgets.put(155 << 16, fixture.widget(155 << 16, -1, -1, null, null));
        fixture.widgets.put(frameId, fixture.widget(frameId, -1, -1, null, children));
        assertTrue(fixture.actions.rewardsCloseVisible());
        assertTrue(fixture.actions.closeRewards(null));
        fixture.actions.dispatch();
        assertEquals(1, fixture.menu.size());
        assertEquals(frameId, fixture.menu.get(0)[1]);
        assertEquals("Close", fixture.menu.get(0)[5]);
    }

    @Test
    public void rewardsCloseIsUnavailableWithoutTheDynamicChild() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.widgets.put(155 << 16, fixture.widget(155 << 16, -1, -1, null, null));
        fixture.widgets.put((155 << 16) | 11, fixture.widget((155 << 16) | 11, -1, -1, new String[] {"Close"}, null));
        assertFalse(fixture.actions.rewardsCloseVisible());
        assertFalse(fixture.actions.closeRewards(null));
    }

    @Test
    public void changedPuzzleModelsAndReplacedWidgetsRejectQueuedClicks() throws Exception
    {
        for (int mutation = 0; mutation < 2; mutation++)
        {
            Fixture fixture = new Fixture();
            int id = (25 << 16) | 12;
            AtomicInteger model = new AtomicInteger(6716);
            fixture.widgets.put(id, fixture.widget(id, -1, -1, new String[] {"Select"}, null));
            assertTrue(fixture.actions.widget(id, "Select", () -> model.get() == 6716, null));
            if (mutation == 0)
            {
                model.set(6722);
            }
            else
            {
                fixture.widgets.put(id, fixture.widget(id, -1, -1, new String[] {"Select"}, null));
            }
            fixture.actions.dispatch();
            assertTrue(fixture.menu.isEmpty());
            assertEquals(0, fixture.actions.getRequestsDispatched());
        }
    }

    @Test
    public void dialogueChoiceUsesSemanticIndexAndRechecksCryptEligibility() throws Exception
    {
        Fixture fixture = new Fixture();
        int id = (219 << 16) | 1;
        fixture.widgets.put(id, fixture.widget(id, -1, -1, null, null));
        AtomicInteger eligible = new AtomicInteger(1);
        assertTrue(fixture.actions.dialogueOption(1, () -> eligible.get() == 1, null));
        fixture.actions.dispatch();
        assertEquals(1, fixture.menu.size());
        assertEquals(1, fixture.menu.get(0)[0]);
        assertEquals(id, fixture.menu.get(0)[1]);
        fixture.actions.dialogueOption(2, () -> eligible.get() == 1, null);
        eligible.set(0);
        fixture.actions.dispatch();
        assertEquals(1, fixture.menu.size());
    }

    @Test
    public void aCrossedDoorCountsAsLoadedOnlyAtItsOwnTile() throws Exception
    {
        Fixture fixture = new Fixture();
        WorldPoint tile = new WorldPoint(3204, 3200, 0);
        assertTrue(fixture.actions.doorLoaded(tile));
        assertFalse(fixture.actions.doorLoaded(tile.dx(9)), "another copy of the same door does not count");
        fixture.liveWall = null;
        assertFalse(fixture.actions.doorLoaded(tile));
    }

    private static final class Fixture
    {
        private final BarrowsActions actions = new BarrowsActions();
        private final Map<Integer, Widget> widgets = new HashMap<>();
        private final List<Object[]> menu = new ArrayList<>();
        private final List<Object[]> scripts = new ArrayList<>();
        private boolean prayerActive;
        private boolean noted;
        private boolean busy;
        private int bankQuantity = 1;
        private Item[] inventoryItems = {new Item(385, 1)};
        private String[][] subops;
        private String[] definitionActions = {null, "Wear", null, "Rub", null};
        private Item[] equipmentItems = {};
        private TravelMethod selected = TravelMethod.BARROWS_TABLET;
        private int energy = 10_000;
        private WorldPoint playerPosition = new WorldPoint(3200, 3200, 0);
        private GameObject[] liveObjects = new GameObject[0];
        private boolean loggedIn = true;
        private int wallDistance = 4;
        private int wallPlane;
        private boolean impostor;
        private String[] wallOptions = {"Open"};
        private WallObject liveWall;
        private final Tile tile = proxy(Tile.class, (name, args) -> {
            switch (name)
            {
                case "getWallObject": return liveWall;
                case "getGameObjects": return liveObjects;
                default: return null;
            }
        });
        private final Scene scene = proxy(Scene.class,
            (name, args) -> name.equals("getTiles") ? new Tile[][][] {{{tile}}} : null);
        private final WorldView view = proxy(WorldView.class, (name, args) -> {
            switch (name)
            {
                case "getScene": return scene;
                case "getPlane": return 0;
                default: return null;
            }
        });
        private final Player player = proxy(Player.class, (name, args) -> {
            switch (name)
            {
                case "getWorldView": return view;
                case "getWorldLocation": return playerPosition;
                case "getLocalLocation": return LocalPoint.fromScene(50, 50);
                case "isDead": return false;
                default: return null;
            }
        });
        private final ObjectComposition transformed = proxy(ObjectComposition.class,
            (name, args) -> name.equals("getActions") ? wallOptions : null);
        private final ObjectComposition definition = proxy(ObjectComposition.class, (name, args) -> {
            switch (name)
            {
                case "getImpostorIds": return impostor ? new int[] {20679, 20680} : null;
                case "getImpostor": return transformed;
                case "getActions": return wallOptions;
                default: return null;
            }
        });
        private final WallObject wall = wall();

        private Fixture() throws Exception
        {
            liveWall = wall;
            ItemContainer inventory = proxy(ItemContainer.class,
                (name, args) -> name.equals("getItems") ? inventoryItems : null);
            ItemContainer equipment = proxy(ItemContainer.class,
                (name, args) -> name.equals("getItems") ? equipmentItems : null);
            Client client = proxy(Client.class, (name, args) -> {
                switch (name)
                {
                    case "getGameState": return loggedIn ? GameState.LOGGED_IN : GameState.LOGIN_SCREEN;
                    case "getLocalPlayer": return player;
                    case "getTopLevelWorldView": return view;
                    case "getBoostedSkillLevel": return 99;
                    case "getRealSkillLevel": return 99;
                    case "getEnergy": return energy;
                    case "isPrayerActive": return prayerActive;
                    case "getVarbitValue": return (Integer) args[0] == 12393 && busy || (Integer) args[0] == 3958 && noted ? 1 : 0;
                    case "getVarpValue": return 61;
                    case "getItemContainer": return (Integer) args[0] == 94 ? equipment : inventory;
                    case "getItemDefinition":
                        return proxy(ItemComposition.class, (member, values) ->
                        member.equals("getSubops") ? subops : member.equals("getInventoryActions") ? definitionActions
                            : member.equals("isStackable") ? Boolean.FALSE : null);
                    case "getObjectDefinition":
                        if ((Integer) args[0] == 39651 || (Integer) args[0] == 26711)
                        {
                            String[] options = (Integer) args[0] == 39651 ? new String[] {"Drink"} : new String[] {"Use", null, "Collect"};
                            return proxy(ObjectComposition.class, (member, values) -> member.equals("getActions") ? options : null);
                        }
                        return definition;
                    case "getWidget":
                        int id = args.length == 1 ? (Integer) args[0] : ((Integer) args[0] << 16) | (Integer) args[1];
                        return widgets.get(id);
                    case "getWidgetRoots": return widgets.values().toArray(new Widget[0]);
                    case "menuAction":
                        menu.add(args.clone());
                        return null;
                    case "runScript":
                        scripts.add(((Object[]) args[0]).clone());
                        return null;
                    default: return null;
                }
            });
            inject(actions, "client", client);
            inject(actions, "config", new BarrowsConfig() {
                @Override public TravelMethod travelMethod() { return selected; }
            });
            MicrobotTestSupport.attach(actions, client);
        }

        private WallObject wall()
        {
            return proxy(WallObject.class, (name, args) -> {
                switch (name)
                {
                    case "getId": return 20681;
                    case "getWorldView": return view;
                    case "getPlane": return wallPlane;
                    case "getWorldLocation": return new WorldPoint(3200 + wallDistance, 3200, wallPlane);
                    case "getLocalLocation": return LocalPoint.fromScene(50 + wallDistance, 50);
                    default: return null;
                }
            });
        }

        private GameObject gameObject(int id, WorldPoint position)
        {
            return proxy(GameObject.class, (name, args) -> {
                switch (name)
                {
                    case "getId": return id;
                    case "getWorldView": return view;
                    case "getPlane": return position.getPlane();
                    case "getWorldLocation": return position;
                    case "getSceneMinLocation": return new net.runelite.api.Point(49, 49);
                    case "getSceneMaxLocation": return new net.runelite.api.Point(50, 50);
                    default: return null;
                }
            });
        }

        private Widget widget(int id, int index, int item, String[] options, Widget[] children)
        {
            return proxy(Widget.class, (name, args) -> {
                switch (name)
                {
                    case "getId": return id;
                    case "getIndex": return index;
                    case "getItemId": return item;
                    case "getActions": return options;
                    case "getItemQuantity": return bankQuantity;
                    case "getDynamicChildren": return children;
                    case "isHidden": return false;
                    default: return null;
                }
            });
        }
    }

    private static <T> T proxy(Class<T> type, BiFunction<String, Object[], Object> handler)
    {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (instance, method, args) -> {
            if (method.getName().equals("hashCode"))
            {
                return System.identityHashCode(instance);
            }
            if (method.getName().equals("equals"))
            {
                return instance == args[0];
            }
            return handler.apply(method.getName(), args);
        }));
    }

    private static void inject(Object target, String name, Object value) throws Exception
    {
        Field field = BarrowsActions.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
