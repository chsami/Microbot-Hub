package net.runelite.client.plugins.microbot.fourtifysbarrows.util.action;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.fourtifysbarrows.crypt.Brother;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class BarrowsActionsTest
{
    @Test
    public void dispatchesOneRequestAtATimeInFirstInFirstOutOrder()
    {
        BarrowsActions actions = new BarrowsActions();
        List<Integer> observed = new ArrayList<>();
        actions.enqueue(() -> observed.add(1));
        actions.enqueue(() -> observed.add(2));
        assertTrue(observed.isEmpty());
        actions.dispatch();
        assertEquals(List.of(1), observed);
        actions.dispatch();
        assertEquals(List.of(1, 2), observed);
        assertEquals(0, actions.getRequestsDispatched());
    }

    @Test
    public void limitsRequestsToFiftyPerSnapshot()
    {
        BarrowsActions actions = new BarrowsActions();
        List<Integer> observed = new ArrayList<>();
        for (int i = 0; i < 60; i++)
        {
            int value = i;
            actions.enqueue(() -> observed.add(value));
        }
        for (int i = 0; i < 60; i++)
        {
            actions.dispatch();
        }
        assertEquals(50, observed.size());
        assertEquals(Integer.valueOf(49), observed.get(49));
    }

    @Test
    public void nextTickAndResetExpireOldRequestsWithoutExecutingThem()
    {
        BarrowsActions actions = new BarrowsActions();
        List<Integer> observed = new ArrayList<>();
        actions.enqueue(() -> observed.add(1));
        actions.nextTick();
        actions.dispatch();
        assertTrue(observed.isEmpty());
        actions.enqueue(() -> observed.add(2));
        actions.dispatch();
        actions.enqueue(() -> observed.add(3));
        actions.reset();
        actions.dispatch();
        assertEquals(List.of(2), observed);
        assertEquals(0, actions.getRequestsDispatched());
    }
    private static final WorldPoint TOMB = new WorldPoint(3554, 9713, 3);
    private static final WorldPoint SURFACE = new WorldPoint(3560, 3310, 0);
    private static final WorldPoint FEROX = new WorldPoint(3151, 3636, 0);

    @Test
    public void fatalFailureExpiresQueuedWorkAndKeepsTheFailureReason() throws Exception
    {
        BarrowsActions actions = new BarrowsActions();
        new Scene(actions, FEROX);
        List<Integer> observed = new ArrayList<>();
        actions.enqueue(() -> observed.add(1));
        actions.fail("Missing supplies");
        actions.dispatch();
        assertTrue(observed.isEmpty());
        assertEquals("Missing supplies", actions.getStatus());
    }

    @Test
    public void aStopInACryptWaitsForTheTeleportOutAndKeepsTheFirstReason() throws Exception
    {
        BarrowsActions actions = new BarrowsActions();
        Scene scene = new Scene(actions, TOMB);
        assertNotNull(Brother.inTomb(TOMB));
        actions.fail("Missing equipment item 1");
        assertTrue(actions.ready(), "The safety tasks still need interactions");
        assertEquals("Missing equipment item 1", actions.pendingStop());
        actions.fail("Combat stalled");
        assertEquals("Missing equipment item 1", actions.pendingStop());
        scene.location = FEROX;
        actions.fail("Emergency teleport completed");
        assertFalse(actions.ready());
        assertNull(actions.pendingStop());
        assertEquals("Missing equipment item 1", actions.getStatus());
    }

    @Test
    public void aStopOnTheSurfaceWaitsOnlyWhileAnNpcAttacks() throws Exception
    {
        BarrowsActions actions = new BarrowsActions();
        Scene scene = new Scene(actions, SURFACE);
        actions.fail("Out of food");
        assertFalse(actions.ready());
        actions.newSession();
        scene.attacker = true;
        actions.fail("Out of food");
        assertTrue(actions.ready());
        assertEquals("Out of food", actions.pendingStop());
    }

    @Test
    public void aStopAwayFromBarrowsOrAfterDeathDoesNotWait() throws Exception
    {
        BarrowsActions actions = new BarrowsActions();
        Scene scene = new Scene(actions, FEROX);
        scene.attacker = true;
        actions.fail("Out of food");
        assertFalse(actions.ready(), "The ring teleport only helps at Barrows");
        actions.newSession();
        scene.location = TOMB;
        scene.dead = true;
        actions.fail("Player died");
        assertNull(actions.pendingStop());
        assertEquals("Player died", actions.getStatus());
    }

    @Test
    public void aFailedTeleportOutStopsWithTheNextStep() throws Exception
    {
        BarrowsActions actions = new BarrowsActions();
        new Scene(actions, TOMB);
        actions.fail("Combat stalled");
        actions.failEscape();
        assertFalse(actions.ready());
        assertNull(actions.pendingStop());
        assertEquals("Combat stalled; the teleport out failed, so leave Barrows now", actions.getStatus());
    }

    @Test
    public void aFailedGameActionStopsWithoutTheClientErrorText() throws Exception
    {
        BarrowsActions actions = new BarrowsActions();
        new Scene(actions, FEROX);
        java.lang.reflect.Field field = BarrowsActions.class.getDeclaredField("worker");
        field.setAccessible(true);
        field.set(actions, new InteractionWorker((net.runelite.client.callback.ClientThread) null)
        {
            @Override
            public RuntimeException getFailure()
            {
                return new NullPointerException("Cannot invoke \"getId()\" because \"w\" is null");
            }
        });
        actions.nextTick();
        assertEquals("A game action did not go through; enable the plugin again to retry", actions.getStatus());
    }

    @Test
    public void aNewSessionClearsAPendingStop() throws Exception
    {
        BarrowsActions actions = new BarrowsActions();
        new Scene(actions, TOMB);
        actions.fail("Combat stalled");
        actions.newSession();
        assertNull(actions.pendingStop());
        assertTrue(actions.ready());
    }

    @Test
    public void aNewSessionClearsAnOldBankTripAndPendingWork()
    {
        BarrowsActions actions = new BarrowsActions();
        List<Integer> observed = new ArrayList<>();
        actions.requestBank();
        assertTrue(actions.tripPending());
        actions.enqueue(() -> observed.add(1));
        actions.newSession();
        actions.dispatch();
        assertFalse(actions.tripPending());
        assertTrue(observed.isEmpty());
    }

    private static final class Scene
    {
        WorldPoint location;
        boolean attacker;
        boolean dead;

        Scene(BarrowsActions actions, WorldPoint location) throws Exception
        {
            this.location = location;
            Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
                (proxy, method, args) -> {
                    switch (method.getName())
                    {
                        case "isDead": return dead;
                        case "getWorldLocation": return this.location;
                        default: return null;
                    }
                });
            NPC npc = (NPC) Proxy.newProxyInstance(NPC.class.getClassLoader(), new Class<?>[] {NPC.class},
                (proxy, method, args) -> {
                    switch (method.getName())
                    {
                        case "getInteracting": return attacker ? player : null;
                        case "isDead": return false;
                        default: return null;
                    }
                });
            Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[] {Client.class},
                (proxy, method, args) -> {
                    switch (method.getName())
                    {
                        case "getGameState": return GameState.LOGGED_IN;
                        case "getLocalPlayer": return player;
                        case "getNpcs": return List.of(npc);
                        case "getTopLevelWorldView": return null;
                        default: return null;
                    }
                });
            MicrobotTestSupport.attach(actions, client);
        }
    }
}
