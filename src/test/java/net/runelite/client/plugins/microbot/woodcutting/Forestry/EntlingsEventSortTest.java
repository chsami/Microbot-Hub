package net.runelite.client.plugins.microbot.woodcutting.Forestry;

import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcCache;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcQueryable;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.woodcutting.AutoWoodcuttingPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntlingsEventSortTest {

    private static final WorldPoint PLAYER = new WorldPoint(3200, 3200, 0);

    private static Rs2NpcModel entlingAt(WorldPoint location) {
        Rs2NpcModel entling = mock(Rs2NpcModel.class);
        when(entling.getWorldLocation()).thenReturn(location);
        return entling;
    }

    @Test
    void sortsEntlingsNearestFirst() {
        Rs2NpcModel far = entlingAt(new WorldPoint(3205, 3200, 0));
        Rs2NpcModel near = entlingAt(new WorldPoint(3201, 3200, 0));
        Rs2NpcModel middle = entlingAt(new WorldPoint(3203, 3200, 0));

        List<Rs2NpcModel> sorted = EntlingsEvent.sortByDistance(new ArrayList<>(Arrays.asList(far, near, middle)), PLAYER);

        assertEquals(Arrays.asList(near, middle, far), sorted);
    }

    @Test
    void skipsEntlingsWithoutWorldLocation() {
        Rs2NpcModel missing = entlingAt(null);
        Rs2NpcModel near = entlingAt(new WorldPoint(3201, 3200, 0));
        Rs2NpcModel far = entlingAt(new WorldPoint(3204, 3200, 0));

        List<Rs2NpcModel> sorted = EntlingsEvent.sortByDistance(new ArrayList<>(Arrays.asList(far, missing, near)), PLAYER);

        assertEquals(Arrays.asList(near, far), sorted);
    }

    @Test
    void executeWaitsForPlayerLocationInsteadOfFailing() {
        Rs2NpcModel near = entlingAt(new WorldPoint(3201, 3200, 0));
        Rs2NpcModel far = entlingAt(new WorldPoint(3205, 3200, 0));
        when(near.getOverheadText()).thenReturn("Short on top!");
        when(far.getOverheadText()).thenReturn("Short back and sides!");
        List<Rs2NpcModel> present = Arrays.asList(far, near);

        Rs2NpcQueryable query = mock(Rs2NpcQueryable.class);
        when(query.where(any())).thenReturn(query);
        when(query.toList()).thenReturn(present, present, present, Collections.emptyList());
        Rs2NpcCache cache = mock(Rs2NpcCache.class);
        when(cache.query()).thenReturn(query);

        AutoWoodcuttingPlugin plugin = mock(AutoWoodcuttingPlugin.class);
        when(plugin.ensureInventorySpace(anyInt())).thenReturn(true);

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class);
             MockedStatic<Global> global = mockStatic(Global.class)) {
            microbot.when(() -> Microbot.isPluginEnabled(plugin)).thenReturn(true);
            microbot.when(Microbot::getClient).thenReturn(mock(Client.class));
            microbot.when(Microbot::isLoggedIn).thenReturn(true);
            microbot.when(Microbot::getRs2NpcCache).thenReturn(cache);
            player.when(Rs2Player::getWorldLocation).thenReturn(null, PLAYER);
            global.when(() -> Global.sleepUntil(any(BooleanSupplier.class), anyInt())).thenReturn(true);

            assertTrue(new EntlingsEvent(plugin).execute());

            global.verify(() -> Global.sleepUntil(any(BooleanSupplier.class), anyInt()), times(1));
            verify(near).click("Prune-top");
            verify(far).click("Prune-back");
            verify(plugin).incrementForestryEventCompleted();
        }
    }
}
