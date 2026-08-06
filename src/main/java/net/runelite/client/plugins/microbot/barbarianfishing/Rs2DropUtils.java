package net.runelite.client.plugins.microbot.barbarianfishing;

import net.runelite.client.plugins.microbot.Microbot;

import net.runelite.client.plugins.microbot.util.inventory.InteractOrder;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.MouseMotionFactory;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.SpeedManager;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.DefaultSpeedManager;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.util.Global.sleep;

/**
 * Humanized item dropping with fast natural mouse movement.
 * Shared across all Hub plugins — included in each JAR during build.
 */
public final class Rs2DropUtils {

    private static final long DROP_MOUSE_BASE_MS = 40;

    private Rs2DropUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Drop matching items with humanized timing and sped-up natural mouse.
     * Each drop session rolls a pace seed (150-250ms), then jitters per drop.
     */
    public static void dropAllHumanized(Predicate<Rs2ItemModel> predicate, InteractOrder order) {
        List<Rs2ItemModel> items = Rs2Inventory.calculateInteractOrder(
                Rs2Inventory.items(predicate).collect(Collectors.toList()), order);

        if (items.isEmpty()) return;

        int pace = Rs2Random.fancyNormalSample(150, 250);

        SpeedManager savedManager = speedUpMouse();
        try {
            for (int i = 0; i < items.size(); i++) {
                Rs2Inventory.interact(items.get(i), "Drop");

                if (i < items.size() - 1) {
                    int delay = Rs2Random.logNormalBounded(
                            (int) (pace * 0.7),
                            (int) (pace * 1.3));
                    sleep(delay);
                }
            }
        } finally {
            restoreMouse(savedManager);
        }
    }

    private static SpeedManager speedUpMouse() {
        if (Microbot.naturalMouse == null) return null;

        MouseMotionFactory factory = Microbot.naturalMouse.getFactory();
        SpeedManager original = factory.getSpeedManager();

        DefaultSpeedManager fast = new DefaultSpeedManager();
        fast.setMouseMovementBaseTimeMs(DROP_MOUSE_BASE_MS);
        factory.setSpeedManager(fast);

        return original;
    }

    private static void restoreMouse(SpeedManager original) {
        if (Microbot.naturalMouse == null || original == null) return;
        Microbot.naturalMouse.getFactory().setSpeedManager(original);
    }
}
