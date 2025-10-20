package net.runelite.client.plugins.microbot.thievingstalls.model;

import net.runelite.api.GameObject;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Gembag;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.security.Login;

import javax.inject.Inject;

public class FortisGemStallThievingSpot implements IStallThievingSpot {

    private static final WorldPoint SAFESPOT = new WorldPoint(1671, 3101, 0);
    private static final int STALL_ID = 51935;

    @Inject
    public FortisGemStallThievingSpot() {}

    @Override
    public void thieve() {
        boolean atSafespot = SAFESPOT.equals(Rs2Player.getWorldLocation());
        if (!atSafespot) {
            Rs2Walker.walkTo(SAFESPOT);
            Global.sleepUntil(() -> SAFESPOT.equals(Rs2Player.getWorldLocation()), 3000);
            if (!SAFESPOT.equals(Rs2Player.getWorldLocation())) {
                return;
            }
        }

        if (Rs2Player.hopIfPlayerDetected(1, 0, 2)) {
            return;
        }

        final GameObject stall = Rs2GameObject.getGameObject(STALL_ID, SAFESPOT, 2);
        if (stall == null) {
            boolean started = Microbot.hopToWorld(Login.getRandomWorld(Rs2Player.isMember()));
            if (started) {
                Global.sleepUntil(Microbot::isLoggedIn, 15000);
            }
            return;
        }

        if (!Rs2GameObject.hasAction(Rs2GameObject.convertToObjectComposition(stall), "Steal-from")) {
            boolean started = Microbot.hopToWorld(Login.getRandomWorld(Rs2Player.isMember()));
            if (started) {
                Global.sleepUntil(Microbot::isLoggedIn, 15000);
            }
            return;
        }

        Rs2GameObject.interact(stall, "Steal-from");
        Rs2Player.waitForXpDrop(Skill.THIEVING);

        if (Rs2Gembag.hasGemBag()) {
            if (Rs2Gembag.isUnknown()) {
                Rs2Gembag.checkGemBag();
            }
            if (!isGemBagCompletelyFull()) {
                if (Rs2Inventory.hasItem(ItemID.GEM_BAG)) {
                    Rs2Inventory.interact(ItemID.GEM_BAG, "Open");
                    Global.sleepUntil(Rs2Gembag::isGemBagOpen, 2000);
                }
                if (Rs2Gembag.isGemBagOpen() && hasAnyUncutGemInInventory()) {
                    Rs2Inventory.interact(ItemID.GEM_BAG_OPEN, "Fill");
                }
            }
        }
    }

    @Override
    public void bank() {
        Rs2Bank.walkToBankAndUseBank();
        if (!Rs2Bank.isOpen()) return;

        Rs2Bank.depositAll(ItemID.UNCUT_SAPPHIRE);
        Rs2Bank.depositAll(ItemID.UNCUT_EMERALD);
        Rs2Bank.depositAll(ItemID.UNCUT_RUBY);
        Rs2Bank.depositAll(ItemID.UNCUT_DIAMOND);

        Rs2Bank.emptyGemBag();

        Rs2Bank.depositAllExcept(ItemID.GEM_BAG, ItemID.GEM_BAG_OPEN);
        Rs2Bank.closeBank();
    }

    @Override
    public Integer[] getItemIdsToDrop() {
        return new Integer[0];
    }

    private boolean hasAnyUncutGemInInventory() {
        return Rs2Inventory.items(this::isUncutGem).findAny().isPresent();
    }

    private boolean isUncutGem(Rs2ItemModel item) {
        int id = item.getId();
        return id == ItemID.UNCUT_SAPPHIRE ||
                id == ItemID.UNCUT_EMERALD ||
                id == ItemID.UNCUT_RUBY ||
                id == ItemID.UNCUT_DIAMOND;
    }

    private boolean isGemBagCompletelyFull() {
        if (Rs2Gembag.isUnknown()) return false;
        int sapphire = Rs2Gembag.getGemBagContents().stream().filter(i -> i.getId() == ItemID.UNCUT_SAPPHIRE).findFirst().map(Rs2ItemModel::getQuantity).orElse(0);
        int emerald = Rs2Gembag.getGemBagContents().stream().filter(i -> i.getId() == ItemID.UNCUT_EMERALD).findFirst().map(Rs2ItemModel::getQuantity).orElse(0);
        int ruby = Rs2Gembag.getGemBagContents().stream().filter(i -> i.getId() == ItemID.UNCUT_RUBY).findFirst().map(Rs2ItemModel::getQuantity).orElse(0);
        int diamond = Rs2Gembag.getGemBagContents().stream().filter(i -> i.getId() == ItemID.UNCUT_DIAMOND).findFirst().map(Rs2ItemModel::getQuantity).orElse(0);
        return sapphire >= 60 && emerald >= 60 && ruby >= 60 && diamond >= 60;
    }
}
