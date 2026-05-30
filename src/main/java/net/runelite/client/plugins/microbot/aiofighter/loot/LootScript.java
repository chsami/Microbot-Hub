package net.runelite.client.plugins.microbot.aiofighter.loot;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.grounditems.GroundItem;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.aiofighter.AIOFighterConfig;
import net.runelite.client.plugins.microbot.aiofighter.AIOFighterPlugin;
import net.runelite.client.plugins.microbot.aiofighter.enums.DefaultLooterStyle;
import net.runelite.client.plugins.microbot.aiofighter.enums.State;
import net.runelite.client.plugins.microbot.util.grounditem.LootingParameters;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2LootEngine;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

@Slf4j
public class LootScript extends Script {

    private static final int DEFAULT_MIN_STACK_EXCLUSIVE_ARROWS = 9; // allow 2+
    private static final int DEFAULT_MIN_STACK_EXCLUSIVE_RUNES  = 1; // allow 2+

    private int minFreeSlots = 0;

    public LootScript() {}

    public boolean run(AIOFighterConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                minFreeSlots = config.bank() ? config.minFreeSlots() : 0;

                if (!super.run()) return;
                if (!Microbot.isLoggedIn()) return;
                if (!config.toggleLootItems()) return;

                final State st = AIOFighterPlugin.getState();
                if (st == State.BANKING || st == State.WALKING) return;

                if (((Rs2Inventory.isFull() || Rs2Inventory.emptySlotCount() <= minFreeSlots) && !config.eatFoodForSpace())
                        || (Rs2Player.isInCombat() && !config.toggleForceLoot())) {
                    return;
                }

                LootingParameters params = new LootingParameters(
                        config.minPriceOfItemsToLoot(),
                        config.maxPriceOfItemsToLoot(),
                        config.attackRadius(),
                        /* minQuantity */ 1,
                        /* minInvSlots */ minFreeSlots,
                        config.toggleDelayedLooting(),
                        config.toggleOnlyLootMyItems()
                );
                params.setEatFoodForSpace(config.eatFoodForSpace());

                Rs2LootEngine.Builder builder = Rs2LootEngine.with(params)
                        .withLootAction(gi -> lootOrTelegrab(gi, config));

                // custom filter
                if (config.looterStyle() == DefaultLooterStyle.ITEM_LIST || config.looterStyle() == DefaultLooterStyle.MIXED) {
                    addCustomNames(builder, config.listOfItemsToLoot());
                }

                if (config.looterStyle() == DefaultLooterStyle.GE_PRICE_RANGE || config.looterStyle() == DefaultLooterStyle.MIXED) builder.addByValue();
                if (config.toggleBuryBones())       builder.addBones();
                if (config.toggleScatter())         builder.addAshes();
                if (config.toggleLootCoins())       builder.addCoins();
                if (config.toggleLootUntradables()) builder.addUntradables();
                if (config.toggleLootArrows())      builder.addArrows(DEFAULT_MIN_STACK_EXCLUSIVE_ARROWS);
                if (config.toggleLootRunes())       builder.addRunes(DEFAULT_MIN_STACK_EXCLUSIVE_RUNES);

                // Execute one combined, distance-sorted looting pass
                builder.loot();

            } catch (Exception ex) {
                Microbot.log("LootScript: " + ex.getMessage());
            }
        }, 0, 200, TimeUnit.MILLISECONDS);

        return true;
    }

    /**
     * Loots a ground item by walking to it (default), or by Telekinetic Grab when the item
     * is unreachable on foot and the "Telegrab unreachable loot" option is enabled.
     *
     * <p>Handles spots like the caged Lesser Demon where drops land behind bars: walking
     * there just yields "I can't reach that!". When telegrab is enabled and we can cast
     * (33 Magic + law/air runes) we Telekinetic Grab the item instead. While the option is
     * on, unreachable items are never walked to, which stops the "can't reach" spam.
     */
    private void lootOrTelegrab(GroundItem groundItem, AIOFighterConfig config) {
        if (config.toggleTelegrabUnreachableLoot()) {
            final WorldPoint wp = groundItem.getLocation();
            if (wp != null && !Rs2Tile.isTileReachable(wp)) {
                // Unreachable wanted item (e.g. behind the bars at a caged demon): Telekinetic
                // Grab it rather than walking, which would just spam "I can't reach that!".
                final int id = groundItem.getId();
                if (Rs2Magic.canCast(MagicAction.TELEKINETIC_GRAB) && Rs2Magic.cast(MagicAction.TELEKINETIC_GRAB)) {
                    sleep(300, 500); // let the spell arm before clicking the item
                    final WorldPoint me = Rs2Player.getWorldLocation();
                    final int range = (me == null ? 10 : me.distanceTo(wp) + 2);
                    Rs2GroundItem.interact(id, "Cast", range);
                    // Block until this pile is actually grabbed (gone), bounded. Casting once per
                    // pile stops the loot loop from re-casting mid-grab (the spellbook stutter)
                    // and keeps the cast/reachability calls off the client thread's hot path,
                    // which was saturating it and causing TimeoutExceptions in other scripts.
                    sleepUntil(() -> Rs2GroundItem.getGroundItems().get(wp, id) == null, 3000);
                }
                return; // never walk to unreachable loot while this option is on
            }
        }
        Rs2GroundItem.coreLoot(groundItem);
    }

    /**
     * Adds a custom "by names" intent sourced from the config's comma-separated list.
     * (We use a custom predicate so we don't depend on params.getNames()).
     */
    private void addCustomNames(Rs2LootEngine.Builder builder, String csvNames) {
        if (csvNames == null) return;
        final Set<String> needles = new HashSet<>();
        Arrays.stream(csvNames.split(","))
                .map(s -> s == null ? "" : s.trim().toLowerCase())
                .filter(s -> !s.isEmpty())
                .forEach(needles::add);

        if (needles.isEmpty()) return;

        Predicate<GroundItem> byNames = gi -> {
            final String n = gi.getName() == null ? "" : gi.getName().trim().toLowerCase();
            for (String needle : needles) {
                if (n.contains(needle)) return true;
            }
            return false;
        };

        builder.addCustom("names", byNames, /*ignoredLower*/ null);
    }


    @Override
    public void shutdown() {
        super.shutdown();
    }
}
