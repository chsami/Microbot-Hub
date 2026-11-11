package net.runelite.client.plugins.microbot.woodcutting.Forestry;

import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcCache;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcQueryable;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectQueryable;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.woodcutting.AutoWoodcuttingPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ForestryEventPlayerLocationTest {

    private static final WorldPoint PLAYER = new WorldPoint(3200, 3200, 0);
    private static final WorldPoint TARGET = new WorldPoint(3204, 3200, 0);

    private final AutoWoodcuttingPlugin plugin = mock(AutoWoodcuttingPlugin.class);
    private final Rs2NpcQueryable npcQuery = mock(Rs2NpcQueryable.class);
    private final Rs2TileObjectQueryable objectQuery = mock(Rs2TileObjectQueryable.class);

    private MockedStatic<Microbot> microbot;
    private MockedStatic<Rs2Player> player;
    private MockedStatic<Rs2Walker> walker;
    private MockedStatic<Rs2Inventory> inventory;
    private MockedStatic<Global> global;

    @BeforeEach
    void setUp() {
        Rs2NpcCache npcCache = mock(Rs2NpcCache.class);
        when(npcCache.query()).thenReturn(npcQuery);
        when(npcQuery.withId(anyInt())).thenReturn(npcQuery);
        Rs2TileObjectCache objectCache = mock(Rs2TileObjectCache.class);
        when(objectCache.query()).thenReturn(objectQuery);
        when(objectQuery.withId(anyInt())).thenReturn(objectQuery);
        when(objectQuery.where(any())).thenReturn(objectQuery);
        when(plugin.ensureInventorySpace(anyInt())).thenReturn(true);

        microbot = mockStatic(Microbot.class);
        player = mockStatic(Rs2Player.class);
        walker = mockStatic(Rs2Walker.class);
        inventory = mockStatic(Rs2Inventory.class);
        global = mockStatic(Global.class);
        microbot.when(() -> Microbot.isPluginEnabled(plugin)).thenReturn(true);
        microbot.when(Microbot::getClient).thenReturn(mock(Client.class));
        microbot.when(Microbot::isLoggedIn).thenReturn(true);
        microbot.when(Microbot::getRs2NpcCache).thenReturn(npcCache);
        microbot.when(Microbot::getRs2TileObjectCache).thenReturn(objectCache);
        player.when(Rs2Player::getWorldLocation).thenReturn(null, PLAYER);
        global.when(() -> Global.sleepUntil(any(BooleanSupplier.class), anyInt())).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        global.close();
        inventory.close();
        walker.close();
        player.close();
        microbot.close();
    }

    @Test
    void eggEventWaitsForPlayerLocationThenCollectsFromTheNest() {
        Rs2NpcModel forester = mock(Rs2NpcModel.class);
        when(npcQuery.nearest()).thenReturn(forester, forester, forester, null);
        when(npcQuery.toList()).thenReturn(Collections.singletonList(mock(Rs2NpcModel.class)));
        Rs2TileObjectModel nest = mock(Rs2TileObjectModel.class);
        when(nest.getWorldLocation()).thenReturn(TARGET);
        when(nest.click()).thenReturn(true);
        when(objectQuery.toList()).thenReturn(Collections.singletonList(nest));

        assertTrue(new EggEvent(plugin).execute());

        global.verify(() -> Global.sleepUntil(any(BooleanSupplier.class), eq(1200)), times(1));
        verify(nest).click();
        verify(plugin).incrementForestryEventCompleted();
    }

    @Test
    void leprechaunEventWaitsForPlayerLocationThenWalksToTheRainbow() {
        when(npcQuery.nearest()).thenReturn(mock(Rs2NpcModel.class), mock(Rs2NpcModel.class), null);
        Rs2TileObjectModel rainbow = mock(Rs2TileObjectModel.class);
        when(rainbow.getWorldLocation()).thenReturn(TARGET);
        when(objectQuery.nearest()).thenReturn(rainbow);

        assertTrue(new LeprechaunEvent(plugin).execute());

        global.verify(() -> Global.sleepUntil(any(BooleanSupplier.class), eq(1200)), times(1));
        walker.verify(() -> Rs2Walker.walkFastCanvas(TARGET), times(1));
        verify(plugin).incrementForestryEventCompleted();
    }

    @Test
    void ritualEventWaitsForPlayerLocationThenWalksToTheCircle() throws Exception {
        when(npcQuery.nearest()).thenReturn(mock(Rs2NpcModel.class), mock(Rs2NpcModel.class), null);
        net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel target = circle(10);
        when(target.getWorldLocation()).thenReturn(TARGET);
        setRitualCircles(Arrays.asList(circle(0), circle(0), circle(5), circle(5), target));

        assertTrue(new RitualEvent(plugin).execute());

        global.verify(() -> Global.sleepUntil(any(BooleanSupplier.class), eq(1200)), times(1));
        walker.verify(() -> Rs2Walker.walkFastCanvas(TARGET), times(1));
        verify(plugin).incrementForestryEventCompleted();
    }

    @Test
    void saplingValidateSkipsWhilePlayerLocationIsMissing() {
        Rs2TileObjectModel sapling = sapling();

        StrugglingSaplingEvent event = new StrugglingSaplingEvent(plugin);

        assertFalse(event.validate());
        verifyNoInteractions(sapling);
        assertTrue(event.validate());
    }

    @Test
    void saplingExecuteDefersWhilePlayerLocationIsMissing() {
        Rs2TileObjectModel sapling = sapling();
        player.when(Rs2Player::getWorldLocation).thenReturn(null);

        assertFalse(new StrugglingSaplingEvent(plugin).execute());

        verifyNoInteractions(sapling);
        verify(plugin, never()).incrementForestryEventCompleted();
    }

    @Test
    void saplingExecuteWaitsForPlayerLocationBeforeChoosingAnIngredient() throws Exception {
        sapling();
        Rs2TileObjectModel ingredient = mock(Rs2TileObjectModel.class);
        when(ingredient.getId()).thenReturn(1);
        when(ingredient.hasAction("Collect")).thenReturn(true);
        when(ingredient.getWorldLocation()).thenReturn(TARGET);
        when(objectQuery.toList()).thenReturn(Collections.singletonList(ingredient));
        Field field = AutoWoodcuttingPlugin.class.getDeclaredField("saplingOrder");
        field.setAccessible(true);
        field.set(plugin, new GameObject[3]);
        player.when(Rs2Player::getWorldLocation).thenReturn(PLAYER, PLAYER, null, PLAYER);

        assertTrue(new StrugglingSaplingEvent(plugin).execute());

        global.verify(() -> Global.sleepUntil(any(BooleanSupplier.class), eq(1200)), times(1));
        verify(ingredient, times(1)).click("Collect");
        verify(plugin).incrementForestryEventCompleted();
    }

    private Rs2TileObjectModel sapling() {
        Rs2TileObjectModel sapling = mock(Rs2TileObjectModel.class);
        when(sapling.hasAction("Add-mulch")).thenReturn(true);
        when(sapling.getWorldLocation()).thenReturn(TARGET);
        when(objectQuery.withName("Struggling sapling")).thenReturn(objectQuery);
        when(objectQuery.toListOnClientThread()).thenReturn(Collections.singletonList(sapling));
        return sapling;
    }

    private static net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel circle(int offset) {
        net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel circle = mock(net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel.class);
        when(circle.getId()).thenReturn(NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_A_1 + offset);
        return circle;
    }

    private void setRitualCircles(List<net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel> circles) throws Exception {
        Field field = AutoWoodcuttingPlugin.class.getDeclaredField("ritualCircles");
        field.setAccessible(true);
        field.set(plugin, circles);
    }
}
