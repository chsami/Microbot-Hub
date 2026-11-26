package net.runelite.client.plugins.microbot.baggedplants;

import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcCache;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcQueryable;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BooleanSupplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BaggedPlantsPhialsUnnoteTest {

    private static final int UNNOTED_BAGGED_PLANT = 8431;
    private static final int NOTED_BAGGED_PLANT = 8432;
    private static final int EMPTY_WATERING_CAN = 5331;
    private static final int PHIALS_DIALOG = 14352385;
    private static final WorldPoint PHIALS = new WorldPoint(2950, 3213, 0);
    private static final WorldPoint HOUSE_PORTAL_EXIT = new WorldPoint(2953, 3222, 0);
    private static final WorldPoint NEXT_TO_PHIALS = new WorldPoint(2951, 3214, 0);

    private final ClientThread clientThread = mock(ClientThread.class);
    private final Rs2NpcModel phials = mock(Rs2NpcModel.class);

    private MockedStatic<Microbot> microbot;
    private MockedStatic<Rs2Inventory> inventory;
    private MockedStatic<Rs2Widget> widget;
    private MockedStatic<Rs2Player> player;
    private MockedStatic<Rs2Walker> walker;

    @BeforeEach
    void setUp() {
        microbot = mockStatic(Microbot.class);
        inventory = mockStatic(Rs2Inventory.class);
        widget = mockStatic(Rs2Widget.class);
        player = mockStatic(Rs2Player.class);
        walker = mockStatic(Rs2Walker.class);

        Rs2NpcCache cache = mock(Rs2NpcCache.class);
        Rs2NpcQueryable query = mock(Rs2NpcQueryable.class);
        when(cache.query()).thenReturn(query);
        when(query.withName(anyString())).thenReturn(query);
        when(query.nearestOnClientThread()).thenReturn(phials);
        when(phials.getWorldLocation()).thenReturn(PHIALS);

        microbot.when(Microbot::getClient).thenReturn(mock(Client.class));
        microbot.when(Microbot::getClientThread).thenReturn(clientThread);
        microbot.when(Microbot::getRs2NpcCache).thenReturn(cache);
    }

    @AfterEach
    void tearDown() {
        walker.close();
        player.close();
        widget.close();
        inventory.close();
        microbot.close();
    }

    @Test
    void walksToPhialsBeforeUsingNotesWhenStandingAtThePortal() throws Exception {
        needsUnnoting();
        player.when(Rs2Player::getWorldLocation).thenReturn(HOUSE_PORTAL_EXIT);

        refillSupplies();

        walker.verify(() -> Rs2Walker.walkTo(PHIALS, 2));
        inventory.verify(() -> Rs2Inventory.use(anyInt()), never());
        verify(phials, never()).click(anyString());
        verify(clientThread, never()).invoke(any(BooleanSupplier.class));
    }

    @Test
    void usesNotesOnPhialsFromTheScriptThreadInsteadOfQueueingClientThreadRetries() throws Exception {
        needsUnnoting();
        player.when(Rs2Player::getWorldLocation).thenReturn(NEXT_TO_PHIALS);
        inventory.when(Rs2Inventory::isItemSelected).thenReturn(true);
        widget.when(() -> Rs2Widget.getWidget(PHIALS_DIALOG)).thenReturn(null, mock(Widget.class));
        when(phials.click("Use")).thenReturn(true);

        refillSupplies();

        verify(phials).click("Use");
        verify(clientThread, never()).invoke(any(BooleanSupplier.class));
        walker.verify(() -> Rs2Walker.walkTo(any(WorldPoint.class), anyInt()), never());
    }

    @Test
    void clearsLeftoverSelectionBeforeRefillingTheWateringCan() throws Exception {
        inventory.when(() -> Rs2Inventory.hasItem(UNNOTED_BAGGED_PLANT)).thenReturn(true);
        inventory.when(() -> Rs2Inventory.hasItem(EMPTY_WATERING_CAN)).thenReturn(true);
        inventory.when(Rs2Inventory::isItemSelected).thenReturn(true, false);

        refillSupplies();

        inventory.verify(Rs2Inventory::deselect);
        inventory.verify(() -> Rs2Inventory.useItemOnObject(anyInt(), anyInt()), never());
    }

    private void needsUnnoting() {
        inventory.when(() -> Rs2Inventory.hasItem(UNNOTED_BAGGED_PLANT)).thenReturn(false);
        inventory.when(() -> Rs2Inventory.hasItem(NOTED_BAGGED_PLANT)).thenReturn(true);
    }

    private void refillSupplies() throws Exception {
        BaggedPlantsScript script = new BaggedPlantsScript();
        Field executor = Script.class.getDeclaredField("scheduledExecutorService");
        executor.setAccessible(true);
        ((ScheduledExecutorService) executor.get(script)).shutdownNow();
        Method refill = BaggedPlantsScript.class.getDeclaredMethod("refillSupplies");
        refill.setAccessible(true);
        refill.invoke(script);
    }
}
