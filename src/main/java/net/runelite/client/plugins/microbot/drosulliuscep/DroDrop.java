/*
 * DroSulliuscep - Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Created by DRO (droplugins). Free and open source.
 * Reuse is permitted only under LICENSE.txt: every copy, fork or excerpt of this
 * file must keep this notice, the author credit and the license text. See CREDITS.txt.
 */
package net.runelite.client.plugins.microbot.drosulliuscep;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.inventory.InteractOrder;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.MouseMotionFactory;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.SpeedManager;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.DefaultSpeedManager;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Reusable Dro inventory-drop utility.
 *
 * CUSTOM
 * ------
 * The user's learned manual drop profile:
 *   - 8 recorded manual inventories
 *   - 209 recorded Drop actions
 *   - median click-to-click cadence ~400 ms
 *   - IQR ~380-422 ms
 *   - real traversal templates, direction changes and rare safe irregularities
 *
 * RANDOM
 * ------
 * Deliberately remains Dro's normal RANDOM interact order. CUSTOM and
 * RANDOM are separate so other Dro scripts can explicitly opt into the learned
 * profile without changing the semantics of RANDOM.
 *
 * This class is public so other Dro scripts can import and reuse it:
 *
 *   DroDrop.dropAll(predicate, DroDrop.Pattern.CUSTOM, profile);
 *
 * or without BaseProfileDro:
 *
 *   DroDrop.dropAll(predicate, DroDrop.Pattern.CUSTOM);
 */
public final class DroDrop
{
    public enum Pattern
    {
        CUSTOM("Custom (Dro learned)", null),
        STANDARD("Standard", InteractOrder.STANDARD),
        EFFICIENT_ROW("Efficient row", InteractOrder.EFFICIENT_ROW),
        COLUMN("Column", InteractOrder.COLUMN),
        EFFICIENT_COLUMN("Efficient column", InteractOrder.EFFICIENT_COLUMN),
        ZIGZAG("Zigzag", InteractOrder.ZIGZAG),
        RANDOM("Random", InteractOrder.RANDOM);

        private final String displayName;
        private final InteractOrder interactOrder;

        Pattern(String displayName, InteractOrder interactOrder)
        {
            this.displayName = displayName;
            this.interactOrder = interactOrder;
        }

        InteractOrder interactOrder()
        {
            return interactOrder;
        }

        @Override
        public String toString()
        {
            return displayName;
        }
    }

    // Manual cursor travel was typically ~140-165 ms inside the ~400 ms cadence.
    private static final long CUSTOM_MOUSE_BASE_MS = 60L;

    private static final int[][] CUSTOM_TEMPLATES = {
        {0, 1, 2, 3, 7, 11, 10, 9, 4, 8, 12, 13, 14, 15, 19, 18, 17, 16, 20, 21, 22, 23, 27, 26, 25, 24},
            {3, 2, 6, 7, 5, 4, 8, 9, 10, 11, 15, 14, 13, 12, 16, 17, 18, 19, 23, 22, 21, 20, 24, 25, 26, 27},
            {3, 2, 7, 6, 5, 4, 8, 12, 13, 9, 10, 14, 15, 11, 19, 18, 22, 23, 21, 17, 16, 20, 24, 25, 26, 27},
            {2, 3, 7, 6, 5, 4, 8, 9, 10, 11, 15, 14, 18, 17, 13, 12, 16, 20, 24, 25, 21, 22, 23, 27, 26, 19},
            {24, 25, 26, 27, 23, 22, 21, 20, 16, 17, 18, 19, 15, 14, 13, 12, 8, 9, 10, 11, 7, 6, 5, 4, 2, 3},
            {2, 7, 6, 5, 4, 8, 9, 10, 3, 11, 15, 14, 13, 12, 16, 17, 18, 19, 23, 22, 21, 20, 24, 25, 26, 27},
            {12, 13, 14, 15, 19, 11, 10, 9, 8, 4, 5, 6, 7, 3, 2, 16, 17, 18, 22, 21, 20, 24, 25, 26, 27, 23},
            {2, 3, 7, 6, 5, 4, 8, 9, 10, 11, 15, 14, 13, 12, 16, 17, 18, 19, 23, 22, 21, 20, 24, 25, 26, 27}
    };

    private static final int[] CUSTOM_ADJACENT_MS = {
        599, 518, 463, 438, 521, 421, 420, 443, 439, 419, 400, 420, 400, 400, 400, 422, 418, 381,
            400, 400, 380, 360, 439, 421, 480, 441, 400, 459, 420, 379, 422, 379, 400, 362, 399, 359,
            421, 400, 359, 399, 383, 383, 396, 400, 380, 425, 400, 396, 458, 423, 442, 380, 421, 397,
            401, 359, 399, 421, 360, 462, 539, 521, 398, 399, 421, 501, 400, 400, 400, 443, 399, 398,
            423, 481, 419, 422, 417, 464, 477, 422, 436, 421, 420, 380, 462, 400, 460, 378, 421, 441,
            437, 424, 419, 418, 403, 458, 379, 564, 422, 378, 379, 480, 380, 383, 399, 438, 400, 399,
            381, 399, 401, 381, 359, 441, 402, 357, 379, 442, 378, 361, 400, 381, 439, 399, 400, 358,
            382, 382, 357, 383, 542, 378, 322, 358, 400, 400, 381, 384, 355, 400, 362, 338, 400, 401,
            378, 383, 400, 337, 364, 357, 380, 463, 358, 340, 400, 403, 399, 400, 378, 422, 399, 382,
            402, 357, 342, 400, 398, 379, 360, 421, 479, 400, 419, 421, 400, 423, 377, 380, 380, 384,
            377, 359, 399, 421, 379, 381, 384, 376, 359, 382, 382, 378, 460, 422, 940, 821, 779, 718,
            696
    };

    private static final int[] CUSTOM_MEDIUM_JUMP_MS = {
        1437, 857, 501, 521, 542, 564, 779, 940
    };

    private static final int[] CUSTOM_LARGE_JUMP_MS = {
        439, 564, 696, 718
    };

    private DroDrop()
    {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void dropAll(
            Predicate<Rs2ItemModel> predicate,
            Pattern pattern)
    {
        dropAll(predicate, pattern, null);
    }

    public static void dropCustom(
            Predicate<Rs2ItemModel> predicate)
    {
        dropAll(predicate, Pattern.CUSTOM, null);
    }

    public static void dropCustom(
            Predicate<Rs2ItemModel> predicate,
            BaseProfileDro profile)
    {
        dropAll(predicate, Pattern.CUSTOM, profile);
    }

    public static void dropAll(
            Predicate<Rs2ItemModel> predicate,
            Pattern pattern,
            BaseProfileDro profile)
    {
        List<Rs2ItemModel> raw = Rs2Inventory.items(predicate)
                .collect(Collectors.toList());

        if (raw.isEmpty())
        {
            return;
        }

        dropItems(raw, pattern, profile);
    }

    static void dropItems(List<Rs2ItemModel> raw, Pattern pattern, BaseProfileDro profile)
    {
        Pattern resolved = pattern == null ? Pattern.CUSTOM : pattern;

        if (resolved == Pattern.CUSTOM)
        {
            dropCustomInternal(raw, profile);
            return;
        }

        InteractOrder interactOrder = resolved.interactOrder();
        List<Rs2ItemModel> ordered =
                Rs2Inventory.calculateInteractOrder(raw, interactOrder);

        dropFixedOrder(ordered, profile);
    }

    private static void dropCustomInternal(
            List<Rs2ItemModel> raw,
            BaseProfileDro profile)
    {
        List<Rs2ItemModel> items = customOrder(raw);

        if (items.isEmpty())
        {
            return;
        }

        SpeedManager savedManager = useCustomMouseSpeed();

        boolean injectSafeMistake =
                shouldInjectSafeMistake(items.size());
        int mistakeIndex = injectSafeMistake
                ? mistakeIndex(items.size())
                : -1;

        try
        {
            for (int i = 0; i < items.size(); i++)
            {
                Rs2ItemModel item = items.get(i);

                if (item == null)
                {
                    continue;
                }

                int slot = item.getSlot();
                long actionStartedAt = System.currentTimeMillis();

                performProfiledDrop(item, profile);

                if (i == mistakeIndex && profile != null)
                {
                    Rectangle slotBounds = Rs2Inventory.itemBounds(item);

                    if (slotBounds != null)
                    {
                        boolean empty = sleepUntil(
                                () -> Rs2Inventory.getItemInSlot(slot) == null,
                                900);

                        if (empty)
                        {
                            sleep((int) Math.round(mistakeDelayMs() * 0.48));
                            profile.clickHumanized(slotBounds);
                            sleep((int) Math.round(mistakeRecoveryMs() * 0.48));
                        }
                    }
                }

                if (i < items.size() - 1)
                {
                    Rs2ItemModel next = items.get(i + 1);

                    if (next != null)
                    {
                        int targetCadence = customCadenceMs(
                                slot,
                                next.getSlot());

                        long elapsed =
                                System.currentTimeMillis() - actionStartedAt;
                        long remaining = targetCadence - elapsed;

                        if (remaining > 0L)
                        {
                            sleep((int) remaining);
                        }
                    }
                }
            }

        }
        finally
        {
            restoreMouse(savedManager);
        }
    }

    /**
     * Fixed/RANDOM Dro orders remain available. They use normal inventory
     * interaction ordering and do NOT silently inherit the learned CUSTOM route.
     */
    private static void dropFixedOrder(
            List<Rs2ItemModel> items,
            BaseProfileDro profile)
    {
        if (items == null || items.isEmpty())
        {
            return;
        }

        for (Rs2ItemModel item : items)
        {
            if (item == null)
            {
                continue;
            }

            performProfiledDrop(item, profile);
            sleep(ThreadLocalRandom.current().nextInt(70, 141));
        }
    }

    /** Route every individual Drop interaction through BaseProfileDro. The
     * reduced local delay multiplier preserves the learned drop cadence while
     * still making the profile the owner of each real action. */
    private static boolean performProfiledDrop(Rs2ItemModel item, BaseProfileDro profile)
    {
        if (item == null)
        {
            return false;
        }
        if (profile == null)
        {
            return Rs2Inventory.interact(item, "Drop");
        }
        return profile.performAction(
                BaseProfileDro.ActionPhase.ACTIVE_WORK,
                true,
                () -> Rs2Inventory.interact(item, "Drop"),
                0.25);
    }

    private static List<Rs2ItemModel> customOrder(
            List<Rs2ItemModel> source)
    {
        if (source == null || source.isEmpty())
        {
            return new ArrayList<>();
        }

        int[] template = CUSTOM_TEMPLATES[
                ThreadLocalRandom.current().nextInt(
                        CUSTOM_TEMPLATES.length)];

        Map<Integer, Rs2ItemModel> bySlot = new HashMap<>();

        for (Rs2ItemModel item : source)
        {
            if (item != null)
            {
                bySlot.put(item.getSlot(), item);
            }
        }

        List<Rs2ItemModel> ordered =
                new ArrayList<>(source.size());

        for (int slot : template)
        {
            Rs2ItemModel item = bySlot.remove(slot);

            if (item != null)
            {
                ordered.add(item);
            }
        }

        // Future inventories can have a different pair of protected/non-drop
        // slots. Any slot not represented by the selected recording is appended
        // using nearest-neighbour continuation instead of breaking the route.
        while (!bySlot.isEmpty())
        {
            final int fromSlot = ordered.isEmpty()
                    ? bySlot.keySet().stream()
                        .min(Integer::compareTo)
                        .orElse(0)
                    : ordered.get(ordered.size() - 1).getSlot();

            Rs2ItemModel next = bySlot.values().stream()
                    .min(Comparator
                            .comparingInt(
                                    (Rs2ItemModel item) ->
                                            manhattan(
                                                    fromSlot,
                                                    item.getSlot()))
                            .thenComparingInt(
                                    Rs2ItemModel::getSlot))
                    .orElse(null);

            if (next == null)
            {
                break;
            }

            ordered.add(next);
            bySlot.remove(next.getSlot());
        }

        return ordered;
    }

    private static int customCadenceMs(
            int fromSlot,
            int toSlot)
    {
        int distance = manhattan(fromSlot, toSlot);

        int[] pool = distance <= 1
                ? CUSTOM_ADJACENT_MS
                : distance == 2
                    ? CUSTOM_MEDIUM_JUMP_MS
                    : CUSTOM_LARGE_JUMP_MS;

        int sampled = pool[
                ThreadLocalRandom.current().nextInt(pool.length)];

        // Preserve the measured distribution without replaying exact timestamps.
        sampled += ThreadLocalRandom.current().nextInt(-18, 19);

        return (int) Math.round(Math.max(300, Math.min(1_500, sampled)) * 0.48);
    }

    private static boolean shouldInjectSafeMistake(
            int itemCount)
    {
        return itemCount >= 10
                && ThreadLocalRandom.current().nextInt(100) < 34;
    }

    private static int mistakeIndex(int itemCount)
    {
        int upper = Math.max(4, itemCount - 2);

        return ThreadLocalRandom.current().nextInt(
                2,
                upper);
    }

    private static int mistakeDelayMs()
    {
        return ThreadLocalRandom.current().nextInt(
                285,
                421);
    }

    private static int mistakeRecoveryMs()
    {
        return ThreadLocalRandom.current().nextInt(
                300,
                641);
    }

    private static int manhattan(int a, int b)
    {
        int ar = a / 4;
        int ac = a % 4;
        int br = b / 4;
        int bc = b % 4;

        return Math.abs(ar - br) + Math.abs(ac - bc);
    }

    private static SpeedManager useCustomMouseSpeed()
    {
        if (Microbot.naturalMouse == null)
        {
            return null;
        }

        MouseMotionFactory factory =
                Microbot.naturalMouse.getFactory();
        SpeedManager original = factory.getSpeedManager();

        DefaultSpeedManager custom =
                new DefaultSpeedManager();
        custom.setMouseMovementBaseTimeMs(
                CUSTOM_MOUSE_BASE_MS);

        factory.setSpeedManager(custom);

        return original;
    }

    private static void restoreMouse(SpeedManager original)
    {
        if (Microbot.naturalMouse == null || original == null)
        {
            return;
        }

        Microbot.naturalMouse
                .getFactory()
                .setSpeedManager(original);
    }
}
