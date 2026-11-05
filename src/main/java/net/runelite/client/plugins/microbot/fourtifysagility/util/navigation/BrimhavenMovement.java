package net.runelite.client.plugins.microbot.fourtifysagility.util.navigation;

import net.runelite.client.plugins.microbot.fourtifysagility.util.action.AgilityActions;

import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.fourtifysagility.course.BrimhavenRoute;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

/** Limits native minimap movement to the selected Brimhaven arena crossing. */
public final class BrimhavenMovement
{
    private final Client client;
    private final AgilityActions actions;
    private final AtomicInteger generation = new AtomicInteger();

    public BrimhavenMovement(Client client, AgilityActions actions)
    {
        this.client = client;
        this.actions = actions;
    }

    public void cancel()
    {
        generation.incrementAndGet();
    }

    public boolean walk(WorldPoint target, WorldPoint expectedDispenser)
    {
        if (!valid(target, expectedDispenser))
        {
            return false;
        }
        int version = generation.get();
        actions.enqueueBlocking(() -> version == generation.get() && valid(target, expectedDispenser),
            () -> Rs2Walker.walkMiniMap(target));
        return true;
    }

    private boolean valid(WorldPoint target, WorldPoint expectedDispenser)
    {
        Player player = client.getLocalPlayer();
        return client.getGameState() == GameState.LOGGED_IN && player != null && target != null
            && client.getTopLevelWorldView() != null && player.getWorldView() == client.getTopLevelWorldView()
            && BrimhavenRoute.platformAt(player.getWorldLocation()) != null
            && BrimhavenRoute.platformAt(target) != null
            && target.getPlane() == player.getWorldLocation().getPlane()
            && target.distanceTo(player.getWorldLocation()) <= 16
            && (expectedDispenser == null
                || expectedDispenser.equals(BrimhavenRoute.platformAt(client.getHintArrowPoint())));
    }
}
