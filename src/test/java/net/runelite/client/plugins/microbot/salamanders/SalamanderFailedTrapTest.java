package net.runelite.client.plugins.microbot.salamanders;

import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.hunter.HunterTrap;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalamanderFailedTrapTest {

    private static final int FACING_NORTH = 1024;
    private static final WorldPoint TRAP = new WorldPoint(3551, 3450, 0);
    private static final WorldPoint OTHER = new WorldPoint(3555, 3454, 0);
    private static final WorldPoint NEXT_TO_TRAP = new WorldPoint(3551, 3451, 0);

    private SalamanderPlugin plugin;

    @BeforeEach
    void setUp() throws Exception {
        plugin = new SalamanderPlugin();
        Client client = mock(Client.class);
        when(client.getLocalPlayer()).thenReturn(mock(Player.class));
        Field field = SalamanderPlugin.class.getDeclaredField("client");
        field.setAccessible(true);
        field.set(plugin, client);
    }

    @Test
    void failedObjectOfEveryTypeMarksTrackedTrapEmpty() {
        int[] failedIds = {
                ObjectID.HUNTING_SAPLING_FAILED_SWAMP,
                ObjectID.HUNTING_SAPLING_FAILED_ORANGE,
                ObjectID.HUNTING_SAPLING_FAILED_RED,
                ObjectID.HUNTING_SAPLING_FAILED_BLACK,
                ObjectID.HUNTING_SAPLING_FAILED_MOUNTAIN,
                ObjectID.HUNTING_SAPLING_NET_RESET_SWAMP,
                ObjectID.HUNTING_SAPLING_NET_RESET_ORANGE,
                ObjectID.HUNTING_SAPLING_NET_RESET_RED,
                ObjectID.HUNTING_SAPLING_NET_RESET_BLACK,
                ObjectID.HUNTING_SAPLING_NET_RESET_MOUNTAIN,
                ObjectID.HUNTING_SAPLING_UP_RESET_SWAMP,
                ObjectID.HUNTING_SAPLING_UP_RESET_ORANGE,
                ObjectID.HUNTING_SAPLING_UP_RESET_RED,
                ObjectID.HUNTING_SAPLING_UP_RESET_BLACK,
                ObjectID.HUNTING_SAPLING_UP_RESET_MOUNTAIN
        };
        for (int id : failedIds) {
            plugin.getTraps().clear();
            HunterTrap trap = track(TRAP);
            trap.setState(HunterTrap.State.TRANSITION);

            spawn(id, TRAP);

            assertEquals(HunterTrap.State.EMPTY, trap.getState(), "object " + id);
        }
    }

    @Test
    void failedObjectOnUntrackedTileIsIgnored() {
        HunterTrap trap = track(TRAP);

        spawn(ObjectID.HUNTING_SAPLING_NET_RESET_SWAMP, OTHER);

        assertEquals(HunterTrap.State.OPEN, trap.getState());
        assertEquals(1, plugin.getTraps().size());
    }

    @Test
    void resettingOwnFailedTrapTracksItAsOpenAgain() {
        HunterTrap trap = track(TRAP);
        spawn(ObjectID.HUNTING_SAPLING_NET_RESET_SWAMP, TRAP);

        spawn(ObjectID.HUNTING_SAPLING_NET_SET_SWAMP, TRAP);

        HunterTrap tracked = plugin.getTraps().get(TRAP);
        assertNotSame(trap, tracked);
        assertEquals(HunterTrap.State.OPEN, tracked.getState());
        assertEquals(1, plugin.getTraps().size());
    }

    @Test
    void setTrapOfAnotherPlayerIsNotTracked() {
        HunterTrap trap = track(TRAP);

        spawn(ObjectID.HUNTING_SAPLING_NET_SET_SWAMP, TRAP);
        spawn(ObjectID.HUNTING_SAPLING_NET_SET_SWAMP, OTHER);

        assertSame(trap, plugin.getTraps().get(TRAP));
        assertEquals(1, plugin.getTraps().size());
    }

    @Test
    void scriptResetsFullAndFailedTrapsOnly() {
        assertTrue(SalamanderScript.needsReset(HunterTrap.State.FULL));
        assertTrue(SalamanderScript.needsReset(HunterTrap.State.EMPTY));
        assertFalse(SalamanderScript.needsReset(HunterTrap.State.OPEN));
        assertFalse(SalamanderScript.needsReset(HunterTrap.State.TRANSITION));
    }

    @Test
    void resetObjectOnNeighbouringTileMarksTransitioningTrapEmpty() {
        HunterTrap trap = track(TRAP);
        trap.setState(HunterTrap.State.TRANSITION);

        spawn(ObjectID.HUNTING_SAPLING_UP_RESET_SWAMP, NEXT_TO_TRAP);

        assertEquals(HunterTrap.State.EMPTY, trap.getState());
    }

    @Test
    void resetObjectOnNeighbouringTileLeavesOpenTrapAlone() {
        HunterTrap trap = track(TRAP);

        spawn(ObjectID.HUNTING_SAPLING_NET_RESET_SWAMP, NEXT_TO_TRAP);

        assertEquals(HunterTrap.State.OPEN, trap.getState());
    }

    @Test
    void fullTrapsAreHandledBeforeOlderFailedTraps() throws Exception {
        HunterTrap failed = track(TRAP);
        failed.setState(HunterTrap.State.EMPTY);
        placedSecondsAgo(failed, 120);
        HunterTrap full = track(OTHER);
        full.setState(HunterTrap.State.FULL);

        List<HunterTrap> order = SalamanderScript.resetOrder(plugin.getTraps()).stream()
                .map(Map.Entry::getValue).collect(Collectors.toList());

        assertEquals(List.of(full, failed), order);
    }

    @Test
    void failedTrapWithoutResetObjectDoesNotBlockFullTrap() throws Exception {
        HunterTrap failed = track(TRAP);
        failed.setState(HunterTrap.State.EMPTY);
        placedSecondsAgo(failed, 120);
        track(OTHER).setState(HunterTrap.State.FULL);
        Rs2TileObjectModel fullObject = mock(Rs2TileObjectModel.class);
        SalamanderScript.SalamandersCaught = 0;

        boolean handled = new SalamanderScript().handleTraps(plugin,
                entry -> entry.getKey().equals(OTHER) ? fullObject : null);

        assertTrue(handled);
        verify(fullObject).click("Reset");
        assertEquals(1, SalamanderScript.SalamandersCaught);
    }

    @Test
    void resettingFailedTrapIsNotCountedAsCatch() {
        track(TRAP).setState(HunterTrap.State.EMPTY);
        Rs2TileObjectModel resetObject = mock(Rs2TileObjectModel.class);
        SalamanderScript.SalamandersCaught = 0;

        boolean handled = new SalamanderScript().handleTraps(plugin, entry -> resetObject);

        assertTrue(handled);
        verify(resetObject).click("Reset");
        assertEquals(0, SalamanderScript.SalamandersCaught);
        assertEquals(1, plugin.getActiveTrapCount());
    }

    @Test
    void recentlyFailedTrapWithoutResetObjectStaysActive() {
        track(TRAP).setState(HunterTrap.State.EMPTY);

        boolean handled = new SalamanderScript().handleTraps(plugin, entry -> null);

        assertFalse(handled);
        assertEquals(1, plugin.getActiveTrapCount());
    }

    @Test
    void failedTrapWithoutResetObjectStopsCountingAfterThirtySeconds() throws Exception {
        HunterTrap failed = track(TRAP);
        failed.setState(HunterTrap.State.EMPTY);
        placedSecondsAgo(failed, 31);
        track(OTHER);
        SalamanderScript script = new SalamanderScript();

        boolean handled = script.handleTraps(plugin, entry -> null);

        assertFalse(handled);
        assertEquals(1, plugin.getActiveTrapCount());
        assertFalse(plugin.abandonTrap(TRAP));
        assertEquals(2, plugin.getTraps().size());
    }

    @Test
    void abandonedTrapCountsAgainOnceItIsSetAgain() throws Exception {
        HunterTrap failed = track(TRAP);
        failed.setState(HunterTrap.State.EMPTY);
        placedSecondsAgo(failed, 31);
        new SalamanderScript().handleTraps(plugin, entry -> null);
        assertEquals(0, plugin.getActiveTrapCount());

        spawn(ObjectID.HUNTING_SAPLING_NET_SET_SWAMP, TRAP);

        assertEquals(1, plugin.getActiveTrapCount());
    }

    @Test
    void restocksWhenOnlyAbandonedTrapsAreLeftAndNoNetTrapsInInventory() throws Exception {
        HunterTrap failed = track(TRAP);
        failed.setState(HunterTrap.State.EMPTY);
        placedSecondsAgo(failed, 31);
        assertFalse(SalamanderScript.needsRestock(0, plugin.getActiveTrapCount()));

        new SalamanderScript().handleTraps(plugin, entry -> null);

        assertFalse(plugin.getTraps().isEmpty());
        assertTrue(SalamanderScript.needsRestock(0, plugin.getActiveTrapCount()));
        assertFalse(SalamanderScript.needsRestock(1, plugin.getActiveTrapCount()));
    }

    @Test
    void fullTrapWithoutObjectIsNeverAbandoned() throws Exception {
        HunterTrap full = track(TRAP);
        full.setState(HunterTrap.State.FULL);
        placedSecondsAgo(full, 120);

        boolean handled = new SalamanderScript().handleTraps(plugin, entry -> null);

        assertFalse(handled);
        assertEquals(1, plugin.getActiveTrapCount());
    }

    private void placedSecondsAgo(HunterTrap trap, int seconds) throws Exception {
        Field field = HunterTrap.class.getDeclaredField("placedOn");
        field.setAccessible(true);
        field.set(trap, Instant.now().minusSeconds(seconds));
    }

    private HunterTrap track(WorldPoint location) {
        HunterTrap trap = new HunterTrap(object(ObjectID.HUNTING_SAPLING_NET_SET_SWAMP, location));
        plugin.getTraps().put(location, trap);
        return trap;
    }

    private void spawn(int id, WorldPoint location) {
        GameObjectSpawned event = new GameObjectSpawned();
        event.setGameObject(object(id, location));
        plugin.onGameObjectSpawned(event);
    }

    private GameObject object(int id, WorldPoint location) {
        GameObject gameObject = mock(GameObject.class);
        when(gameObject.getId()).thenReturn(id);
        when(gameObject.getWorldLocation()).thenReturn(location);
        when(gameObject.getOrientation()).thenReturn(FACING_NORTH);
        return gameObject;
    }
}
