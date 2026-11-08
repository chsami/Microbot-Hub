package net.runelite.client.plugins.microbot.fourtifysagility.util.navigation;

import net.runelite.client.plugins.microbot.fourtifysagility.util.action.AgilityActions;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BrimhavenMovementTest
{
    @Test
    public void cancelledArenaMoveCannotReachInputProjection() throws Exception
    {
        Fixture fixture = new Fixture();
        assertTrue(fixture.movement.walk(new WorldPoint(2794, 9568, 3), fixture.hint));
        fixture.movement.cancel();
        fixture.actions.dispatch();
    }

    @Test
    public void changedDispenserOrLogoutRejectsQueuedArenaMove() throws Exception
    {
        Fixture fixture = new Fixture();
        assertTrue(fixture.movement.walk(new WorldPoint(2794, 9568, 3), fixture.hint));
        fixture.hint = new WorldPoint(2761, 9546, 3);
        fixture.actions.dispatch();
        assertTrue(fixture.movement.walk(new WorldPoint(2794, 9568, 3), fixture.hint));
        fixture.state = GameState.LOGIN_SCREEN;
        fixture.actions.dispatch();
    }

    @Test
    public void rejectsTravelOutsideArenaPlaneAndNearbyScope() throws Exception
    {
        Fixture fixture = new Fixture();
        assertFalse(fixture.movement.walk(new WorldPoint(2794, 9568, 0), fixture.hint));
        assertFalse(fixture.movement.walk(new WorldPoint(2805, 9568, 3), fixture.hint));
        assertFalse(fixture.movement.walk(new WorldPoint(3200, 3200, 3), fixture.hint));
        assertFalse(fixture.movement.walk(null, fixture.hint));
    }

    private static final class Fixture
    {
        private GameState state = GameState.LOGGED_IN;
        private WorldPoint hint = new WorldPoint(2794, 9568, 3);
        private final AgilityActions actions = new AgilityActions();
        private final BrimhavenMovement movement;

        private Fixture() throws Exception
        {
            net.runelite.api.WorldView view = (net.runelite.api.WorldView) Proxy.newProxyInstance(
                net.runelite.api.WorldView.class.getClassLoader(), new Class<?>[] {net.runelite.api.WorldView.class},
                (object, method, args) -> null);
            Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[] {Player.class}, (object, method, args) ->
                    method.getName().equals("getWorldLocation") ? new WorldPoint(2783, 9568, 3)
                        : method.getName().equals("getWorldView") ? view : null);
            Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(),
                new Class<?>[] {Client.class}, (object, method, args) -> {
                    switch (method.getName())
                    {
                        case "getGameState":
                            return state;
                        case "getLocalPlayer":
                            return player;
                        case "getHintArrowPoint":
                            return hint;
                        case "getTopLevelWorldView":
                            return view;
                        default:
                            throw new AssertionError("Stale arena move reached " + method.getName());
                    }
                });
            Field field = AgilityActions.class.getDeclaredField("client");
            field.setAccessible(true);
            field.set(actions, client);
            movement = new BrimhavenMovement(client, actions);
        }
    }
}
