package net.runelite.client.plugins.microbot.leaguestoolkit;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
public class SnapeGrassTelegrabber {

    private static final WorldPoint SNAPE_GRASS_LOCATION = new WorldPoint(1736, 3170, 0);
    private static final int SNAPE_GRASS_ITEM_ID = 231;
    private static final int REQUIRED_MAGIC_LEVEL = 33;
    private static final int GRAB_RANGE = 15;
    private static final String BRIEFCASE_NAME = "Banker's briefcase";
    private static final int SNAPE_GRASS_NOTED_ID = 232;

    private boolean banking = false;

    @Getter
    private String status = "Idle";

    public void reset() {
        status = "Idle";
        banking = false;
    }

    public boolean tick(LeaguesToolkitConfig config) {
        // Handle banking if inventory was full
        if (banking) {
            return handleBanking(config);
        }
        // Check magic level
        if (!Rs2Player.getSkillRequirement(Skill.MAGIC, REQUIRED_MAGIC_LEVEL)) {
            status = "Need 33 Magic for Telekinetic Grab";
            return false;
        }

        // Check for runes — need law runes + air runes (or air staff)
        boolean hasLawRunes = Rs2Inventory.hasItem("Law rune");
        boolean hasAirSource = Rs2Inventory.hasItem("Air rune")
                || Rs2Equipment.isWearing("Staff of air")
                || Rs2Equipment.isWearing("Air battlestaff")
                || Rs2Equipment.isWearing("Mystic air staff");

        if (!hasLawRunes) {
            status = "No law runes in inventory";
            return false;
        }
        if (!hasAirSource) {
            status = "No air runes or air staff equipped";
            return false;
        }

        // Check if inventory is full — bank and come back
        if (Rs2Inventory.isFull()) {
            banking = true;
            return handleBanking(config);
        }

        // Walk to location if not nearby
        WorldPoint playerPos = Rs2Player.getWorldLocation();
        if (playerPos == null) return true;

        int distance = playerPos.distanceTo(SNAPE_GRASS_LOCATION);
        if (distance > 20) {
            status = "Walking to snape grass (" + distance + " tiles)";
            Rs2Walker.walkTo(SNAPE_GRASS_LOCATION, 4);
            sleep(3000, 5000);
            return true;
        }

        // Wait if still moving/animating
        if (Rs2Player.isMoving() || Rs2Player.isAnimating()) {
            status = "Moving...";
            return true;
        }

        // Check if snape grass is on the ground
        if (!Rs2GroundItem.exists(SNAPE_GRASS_ITEM_ID, GRAB_RANGE)) {
            status = "Waiting for snape grass to respawn...";
            return true;
        }

        // Cast Telekinetic Grab
        status = "Casting Telekinetic Grab on snape grass";
        if (!Rs2Magic.cast(MagicAction.TELEKINETIC_GRAB)) {
            status = "Failed to cast — check spellbook";
            return true;
        }
        sleep(300, 500);

        // Click the ground item
        Rs2GroundItem.interact(SNAPE_GRASS_ITEM_ID, "Cast", GRAB_RANGE);
        sleep(600, 900);

        // Wait for the grab animation to finish
        sleepUntil(() -> !Rs2Player.isAnimating(), 5000);
        sleep(300, 600);

        status = "Grabbed snape grass";
        return true;
    }

    private boolean handleBanking(LeaguesToolkitConfig config) {
        if (!Rs2Bank.isOpen()) {
            if (Rs2Equipment.isWearing(BRIEFCASE_NAME)) {
                status = "Teleporting to bank via briefcase";
                Rs2Equipment.interact(BRIEFCASE_NAME, "Last-destination");
                sleep(2000, 3000);
                sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isMoving(), 8000);
                sleep(500, 1000);
                status = "Opening bank";
                Rs2Bank.openBank();
                sleepUntil(Rs2Bank::isOpen, 5000);
            } else {
                status = "Walking to nearest bank";
                if (!Rs2Bank.walkToBankAndUseBank()) return true;
            }
            return true;
        }

        status = "Depositing snape grass";
        Rs2Bank.depositAll("Snape grass");
        sleep(300, 500);
        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 3000);

        banking = false;
        status = "Walking back to snape grass";
        return true;
    }
}
