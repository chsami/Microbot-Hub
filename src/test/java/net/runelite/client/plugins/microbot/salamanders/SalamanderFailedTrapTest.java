package net.runelite.client.plugins.microbot.salamanders;

import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.hunter.HunterTrap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SalamanderFailedTrapTest {

    private static final int FACING_NORTH = 1024;
    private static final WorldPoint TRAP = new WorldPoint(3551, 3450, 0);
    private static final WorldPoint OTHER = new WorldPoint(3555, 3454, 0);

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
