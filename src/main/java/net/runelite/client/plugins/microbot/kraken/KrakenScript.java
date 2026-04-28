package net.runelite.client.plugins.microbot.kraken;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.NPCComposition;
import net.runelite.client.plugins.grounditems.GroundItem;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.grounditem.LootingParameters;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2LootEngine;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.skills.slayer.Rs2Slayer;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

@Slf4j
public class KrakenScript extends Script {

    private static final int SMALL_WHIRLPOOL_NPC_ID = 5534;
    private static final int MAIN_WHIRLPOOL_NPC_ID = 496;
    // Attackable Kraken form. Used only to detect death (despawn = kill signal) — we never click it.
    private static final int KRAKEN_NPC_ID = 494;

    @Getter
    private volatile KrakenState state = KrakenState.IDLE;
    @Getter
    private volatile int killCount = 0;

    // Tracks NPC scene indexes of small whirlpools we've already clicked this kill,
    // so we can fire clicks in sequence without waiting for each to transform.
    private final Set<Integer> clickedSmallIndexes = new HashSet<>();

    public boolean run(KrakenConfig config) {
        state = KrakenState.IDLE;
        killCount = 0;
        clickedSmallIndexes.clear();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if (Microbot.pauseAllScripts.get()) return;

                switch (state) {
                    case IDLE:
                        handleIdle(config);
                        break;
                    case DISTURBING:
                        handleDisturb(config);
                        break;
                    case FIGHTING:
                        handleFighting(config);
                        break;
                    case LOOTING:
                        handleLooting(config);
                        break;
                    case STOPPED:
                        return;
                }
            } catch (Exception ex) {
                log.error("[Kraken] loop error: {}", ex.getMessage(), ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);

        return true;
    }

    private void handleIdle(KrakenConfig config) {
        Microbot.status = "Kraken: waiting for whirlpools";
        // Slayer-task gate: Kraken can only be killed while assigned. Re-checked every
        // cycle so we exit the moment the task finishes.
        if (!isOnKrakenTask()) {
            requestStop("Not on a Kraken slayer task.");
            return;
        }
        // Out-of-food gate. Only checked here (between kills) so we never abort mid-fight.
        if (config.stopWhenOutOfFood() && Rs2Inventory.getInventoryFood().isEmpty()) {
            requestStop("Out of food.");
            return;
        }
        if (anyWhirlpoolSpawned()) {
            // Small human reaction delay before the first click fires.
            sleep((int) Rs2Random.normalRange(150L, 400L, 0.0));
            state = KrakenState.DISTURBING;
        }
    }

    public void requestStop(String reason) {
        if (state == KrakenState.STOPPED) return;
        Microbot.log("[Kraken] Stopping: " + reason);
        state = KrakenState.STOPPED;
    }

    private static boolean isOnKrakenTask() {
        String task = Rs2Slayer.getSlayerTask();
        return task != null && task.toLowerCase().contains("kraken");
    }

    private void handleDisturb(KrakenConfig config) {
        // Fire all 5 clicks in sequence: 4 small whirlpools + main. The click alone is
        // enough — we don't need to wait for an attack to land. NPC index dedup
        // guarantees no double-clicks on the smalls.
        java.util.List<Rs2NpcModel> smalls = Microbot.getRs2NpcCache().query()
                .withId(SMALL_WHIRLPOOL_NPC_ID)
                .where(npc -> hasAction(npc, "Disturb"))
                .where(npc -> !clickedSmallIndexes.contains(npc.getIndex()))
                .toList();

        int smallsClicked = clickedSmallIndexes.size();
        for (Rs2NpcModel npc : smalls) {
            if (smallsClicked >= 4) break;
            Microbot.status = "Kraken: whirlpool " + (smallsClicked + 1) + "/5";
            if (npc.click("Disturb")) {
                clickedSmallIndexes.add(npc.getIndex());
                smallsClicked++;
            }
            sleep(clickDelayMs());
        }

        Microbot.status = "Kraken: whirlpool 5/5";
        Rs2NpcModel main = Microbot.getRs2NpcCache().query()
                .withId(MAIN_WHIRLPOOL_NPC_ID)
                .where(npc -> hasAction(npc, "Disturb"))
                .nearest();
        if (main != null) {
            main.click("Disturb");
            sleep(clickDelayMs());
        }

        state = KrakenState.FIGHTING;
    }

    // Smooth click cadence — mode ~180ms, bounded to [100, 350] with a long right tail.
    // Just enough variance to avoid metronomic timing without slowing the sequence down.
    private static int clickDelayMs() {
        return (int) Rs2Random.skewedRand(180L, 100L, 350L, 0.0);
    }

    private void handleFighting(KrakenConfig config) {
        Microbot.status = "Kraken: fighting";
        // Confirm the 5-click sequence actually spawned the boss. If a small was
        // missed, the main stays as a whirlpool and Kraken never emerges.
        boolean spawned = sleepUntil(() -> Microbot.getRs2NpcCache().query()
                .withId(KRAKEN_NPC_ID)
                .first() != null, 8_000);
        if (!spawned) {
            Microbot.log("[Kraken] Boss didn't spawn — retrying disturb sequence.");
            clickedSmallIndexes.clear();
            state = KrakenState.IDLE;
            return;
        }
        // Death signal: the attackable Kraken NPC despawns the moment it dies.
        // (isInCombat() lingers ~8s after the last hit — don't use that here.)
        sleepUntil(() -> Microbot.getRs2NpcCache().query()
                .withId(KRAKEN_NPC_ID)
                .first() == null, 120_000);
        // Short human-ish reaction delay before starting to loot.
        sleep((int) Rs2Random.normalRange(250L, 600L, 0.0));
        killCount++;
        state = KrakenState.LOOTING;
    }

    private void handleLooting(KrakenConfig config) {
        Microbot.status = "Kraken: looting";

        // Instanced, so no range / ownership filters needed. No max price, no delay.
        LootingParameters params = new LootingParameters(
                config.minPriceOfItemsToLoot(),
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                /* minItems */ 1,
                /* minInvSlots */ 0,
                /* delayedLooting */ false,
                /* antiLureProtection */ false
        );

        Rs2LootEngine.Builder builder = Rs2LootEngine.with(params)
                .withLootAction(Rs2GroundItem::coreLoot);

        // Only: hardcoded uniques + user's name list. Value threshold is opt-in.
        // No addCoins/addUntradables — on Leagues, drops are account-bound and report
        // as untradeable, which would sweep up every noted stack (monkfish, staves, runes).
        builder.addCustom("kraken-uniques", KrakenScript::isKrakenUnique, null);
        addCustomNames(builder, config.listOfItemsToLoot());
        if (config.toggleLootByValue()) {
            builder.addByValue();
        }

        builder.loot();
        sleep((int) Rs2Random.normalRange(150L, 400L, 0.0));

        clickedSmallIndexes.clear();
        if (Rs2Inventory.isFull()) {
            Microbot.log("[Kraken] Inventory full — stopping.");
            state = KrakenState.STOPPED;
        } else {
            // Back to IDLE — anyWhirlpoolSpawned() polling picks up the respawn quickly.
            state = KrakenState.IDLE;
        }
    }

    private static boolean hasAction(Rs2NpcModel npc, String action) {
        NPCComposition comp = npc.getNpc().getTransformedComposition();
        if (comp == null) return false;
        for (String a : comp.getActions()) {
            if (action.equalsIgnoreCase(a)) return true;
        }
        return false;
    }

    private static boolean anyWhirlpoolSpawned() {
        return Microbot.getRs2NpcCache().query()
                .withId(SMALL_WHIRLPOOL_NPC_ID)
                .where(npc -> hasAction(npc, "Disturb"))
                .first() != null
                || Microbot.getRs2NpcCache().query()
                .withId(MAIN_WHIRLPOOL_NPC_ID)
                .where(npc -> hasAction(npc, "Disturb"))
                .first() != null;
    }

    private static boolean isKrakenUnique(GroundItem gi) {
        String n = gi.getName() == null ? "" : gi.getName().trim().toLowerCase();
        return n.contains("kraken tentacle")
                || n.contains("trident of the seas")
                || n.contains("jar of dirt")
                || n.contains("pet kraken");
    }

    private static void addCustomNames(Rs2LootEngine.Builder builder, String csvNames) {
        if (csvNames == null) return;
        Set<String> needles = new HashSet<>();
        Arrays.stream(csvNames.split(","))
                .map(s -> s == null ? "" : s.trim().toLowerCase())
                .filter(s -> !s.isEmpty())
                .forEach(needles::add);
        if (needles.isEmpty()) return;

        Predicate<GroundItem> byNames = gi -> {
            String n = gi.getName() == null ? "" : gi.getName().trim().toLowerCase();
            for (String needle : needles) {
                if (n.contains(needle)) return true;
            }
            return false;
        };
        builder.addCustom("names", byNames, null);
    }

    @Override
    public void shutdown() {
        super.shutdown();
        state = KrakenState.STOPPED;
    }
}
