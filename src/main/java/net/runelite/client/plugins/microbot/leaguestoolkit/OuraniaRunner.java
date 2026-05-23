package net.runelite.client.plugins.microbot.leaguestoolkit;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
public class OuraniaRunner {

    private static final WorldArea ALTAR_AREA = new WorldArea(new WorldPoint(3054, 5574, 0), 12, 12);
    private static final WorldPoint ENIOLA_AREA = new WorldPoint(3014, 5625, 0);
    private static final String BRIEFCASE_NAME = "Banker's briefcase";
    private static final int PURE_ESSENCE_ID = 7936; // Pure essence (BLANKRUNE_HIGH in gameval)
    private static final String PURE_ESSENCE_NAME = "Pure essence";

    private enum State {
        CRAFTING,
        TELEPORTING_TO_BANK,
        BANKING,
        WALKING_TO_ALTAR
    }

    @Getter
    private String status = "Idle";
    private State state = State.CRAFTING;
    private int bankFailCount = 0;
    private static final int MAX_BANK_FAILS = 3;

    public void reset() {
        status = "Idle";
        state = State.CRAFTING;
        bankFailCount = 0;
    }

    public boolean tick(LeaguesToolkitConfig config) {
        switch (state) {
            case CRAFTING:
                return handleCrafting();
            case TELEPORTING_TO_BANK:
                return handleTeleportToBank();
            case BANKING:
                return handleBanking(config);
            case WALKING_TO_ALTAR:
                return handleWalkingToAltar(config);
        }
        return true;
    }

    private boolean handleCrafting() {
        // Check if we have pure essence to craft
        if (!Rs2Inventory.hasItem(PURE_ESSENCE_ID)) {
            if (isNearAltar()) {
                status = "No essence — heading to bank";
                state = State.TELEPORTING_TO_BANK;
                return true;
            } else if (isNearEniola()) {
                status = "At bank, no essence";
                state = State.BANKING;
                return true;
            } else {
                status = "No essence — teleporting to bank";
                state = State.TELEPORTING_TO_BANK;
                return true;
            }
        }

        if (!isNearAltar()) {
            status = "Walking to altar";
            state = State.WALKING_TO_ALTAR;
            return true;
        }

        if (Rs2Player.isAnimating()) {
            status = "Crafting...";
            return true;
        }

        status = "Crafting runes at altar";
        Microbot.getRs2TileObjectCache().query()
                .withId(ObjectID.RC_ZMI_DUNGEON_CRACKED_CENTER_ALTAR)
                .interact("craft-rune");
        Rs2Inventory.waitForInventoryChanges(5000);
        return true;
    }

    private boolean handleTeleportToBank() {
        if (Rs2Player.isAnimating() || Rs2Player.isMoving()) {
            status = "In transit...";
            return true;
        }

        if (isNearEniola()) {
            status = "At Eniola";
            state = State.BANKING;
            return true;
        }

        // Use briefcase to teleport to bank
        if (Rs2Equipment.isWearing(BRIEFCASE_NAME)) {
            status = "Briefcase Last-destination to Eniola";
            Rs2Equipment.interact(BRIEFCASE_NAME, "Last-destination");
            sleep(2000, 3000);
            sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isMoving(), 10000);
            sleep(500, 1000);
            if (isNearEniola()) {
                state = State.BANKING;
            }
        } else {
            // No briefcase — walk to Eniola
            status = "No briefcase — walking to Eniola";
            log.warn("[Ourania] Briefcase not equipped, falling back to walk");
            Rs2Walker.walkTo(ENIOLA_AREA, 4);
            sleepUntil(() -> isNearEniola() || !Rs2Player.isMoving(), 30000);
            if (isNearEniola()) {
                state = State.BANKING;
            }
        }
        return true;
    }

    private boolean handleBanking(LeaguesToolkitConfig config) {
        if (!Rs2Bank.isOpen()) {
            Rs2NpcModel eniola = Microbot.getRs2NpcCache().query()
                    .withId(NpcID.RC_ZMI_BANKER).nearest();
            if (eniola == null) {
                status = "Can't find Eniola — walking closer";
                Rs2Walker.walkTo(ENIOLA_AREA, 4);
                sleepUntil(() -> !Rs2Player.isMoving(), 5000);
                return true;
            }
            status = "Opening bank at Eniola";
            eniola.click("bank");
            sleepUntil(Rs2Bank::isOpen, 5000);

            // Auto-pay exhaustion detection — if bank didn't open after clicking, runes may be depleted
            if (!Rs2Bank.isOpen()) {
                bankFailCount++;
                log.warn("[Ourania] Bank failed to open (attempt {}/{})", bankFailCount, MAX_BANK_FAILS);
                if (bankFailCount >= MAX_BANK_FAILS) {
                    status = "Eniola refused banking — auto-pay runes depleted?";
                    log.error("[Ourania] Stopping: Eniola refused banking {} times — check auto-pay runes", MAX_BANK_FAILS);
                    return false;
                }
                sleep(1000, 2000);
                return true;
            }
            bankFailCount = 0;
            return true;
        }

        // Deposit crafted runes only — keep air runes, noted pure essence, ledger, etc.
        status = "Depositing crafted runes";
        // Collect IDs to deposit first, then deposit (avoid modifying stream mid-iteration)
        java.util.List<Integer> toDeposit = Rs2Inventory.items()
                .filter(item -> item.getName().toLowerCase().contains("rune")
                        && !item.getName().toLowerCase().contains("air rune")
                        && !item.getName().toLowerCase().contains("pure essence"))
                .map(item -> item.getId())
                .distinct()
                .collect(java.util.stream.Collectors.toList());

        for (int id : toDeposit) {
            Rs2Bank.depositAll(id);
            Rs2Inventory.waitForInventoryChanges(1800);
        }

        // Eat food if HP low
        if (Rs2Player.getHealthPercentage() <= config.ouraniaEatAtPercent()) {
            status = "Eating food at bank";
            int maxEats = 10;
            while (--maxEats > 0 && Rs2Player.getHealthPercentage() < 100 && Rs2Bank.hasItem(config.ouraniaFoodName())) {
                Rs2Bank.withdrawOne(config.ouraniaFoodName());
                Rs2Inventory.waitForInventoryChanges(1800);
                Rs2Player.useFood();
                Rs2Inventory.waitForInventoryChanges(1800);
            }
        }

        // If no pure essence in bank, deposit our noted stack so it becomes available unnoted
        if (!Rs2Bank.hasBankItem(PURE_ESSENCE_NAME)) {
            if (Rs2Inventory.hasItem(PURE_ESSENCE_NAME)) {
                status = "Depositing noted pure essence to unnote";
                Rs2Bank.depositAll(PURE_ESSENCE_ID);
                Rs2Inventory.waitForInventoryChanges(1800);
            } else {
                status = "No pure essence anywhere — waiting for transmutation";
                Rs2Bank.closeBank();
                sleep(3000, 5000);
                return true;
            }
        }

        status = "Withdrawing pure essence";
        Rs2Bank.withdrawAll(PURE_ESSENCE_ID);
        Rs2Inventory.waitForInventoryChanges(1800);
        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 3000);

        state = State.WALKING_TO_ALTAR;
        return true;
    }

    private boolean handleWalkingToAltar(LeaguesToolkitConfig config) {
        if (isNearAltar()) {
            state = State.CRAFTING;
            return true;
        }

        if (Rs2Player.isMoving()) {
            status = "Walking to altar...";
            // Eat while walking if HP low
            if (Rs2Player.getHealthPercentage() <= config.ouraniaEatAtPercent()) {
                if (Rs2Inventory.hasItem(config.ouraniaFoodName())) {
                    Rs2Player.useFood();
                }
            }
            return true;
        }

        status = "Walking to altar";
        // Walk via the short path through the cave
        Rs2Walker.walkTo(new WorldPoint(3060, 5580, 0), 6);
        sleep(2000, 3000);
        return true;
    }

    private boolean isNearAltar() {
        WorldPoint loc = Rs2Player.getWorldLocation();
        return loc != null && ALTAR_AREA.contains(loc);
    }

    private boolean isNearEniola() {
        Rs2NpcModel eniola = Microbot.getRs2NpcCache().query()
                .withId(NpcID.RC_ZMI_BANKER).nearest();
        if (eniola == null) return false;
        WorldPoint loc = Rs2Player.getWorldLocation();
        return loc != null && loc.distanceTo2D(eniola.getWorldLocation()) < 12;
    }
}
