package net.runelite.client.plugins.microbot.bonestobananas;

import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.bonestobananas.util.ElementalStaff;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spellbook;
import net.runelite.client.plugins.microbot.util.magic.Runes;
import net.runelite.client.plugins.microbot.util.magic.Spell;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

public class BonesToBananasScript extends Script {

    private static final int BONES_ID = ItemID.BONES;
    private static final int BANANA_ID = ItemID.BANANA;
    private static final int MAGIC_LEVEL_REQUIRED = 15;

    private boolean initialNaturalMouseState;

    private static final Spell BONES_TO_BANANAS_SPELL = new Spell() {
        @Override public MagicAction getMagicAction() { return MagicAction.BONES_TO_BANANAS; }
        @Override public HashMap<Runes, Integer> getRequiredRunes() {
            return new HashMap<>() {{
                put(Runes.NATURE, 1);
                put(Runes.EARTH, 2);
                put(Runes.WATER, 2);
            }};
        }
        @Override public Rs2Spellbook getSpellbook() { return Rs2Spellbook.MODERN; }
        @Override public int getRequiredLevel() { return MAGIC_LEVEL_REQUIRED; }
    };

    public boolean run(BonesToBananasConfig config) {
        initialNaturalMouseState = Rs2AntibanSettings.naturalMouse;
        Rs2AntibanSettings.naturalMouse = config.useNaturalMouse();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !super.run()) return;

                if (!BONES_TO_BANANAS_SPELL.hasRequiredLevel()) {
                    Microbot.showMessage("Magic level is too low.");
                    shutdown();
                    return;
                }

                if (Rs2Inventory.hasItem(BONES_ID) && hasRequiredRunes()) {
                    if (Rs2Bank.isOpen()) {
                        Rs2Bank.closeBank();
                        return;
                    }
                    Rs2Magic.cast(BONES_TO_BANANAS_SPELL);
                    sleep(600);
                } else {
                    bankAndRestock();
                }

            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    // FIX: Removed the incorrect @Override annotation from this method.
    public void shutdown() {
        Rs2AntibanSettings.naturalMouse = initialNaturalMouseState;
        super.shutdown();
    }

    private void bankAndRestock() {
        if (!Rs2Bank.isOpen()) {
            Rs2Bank.openBank();
            return;
        }
        Rs2Bank.depositAll(BANANA_ID);
        sleep(300, 500);
        handleStaffEquipping();
        ensureCorrectRunes();
        handleBoneWithdrawal();
    }

    private void handleStaffEquipping() {
        if (Rs2Equipment.isWearing(ItemID.MUD_BATTLESTAFF) || Rs2Equipment.isWearing(ItemID.MYSTIC_MUD_STAFF)) {
            return;
        }
        int staffToEquip = findBestStaffInBank();
        if (staffToEquip != -1) {
            final int finalStaffId = staffToEquip;
            Rs2Bank.withdrawAndEquip(finalStaffId);
            sleepUntil(() -> Rs2Equipment.isWearing(finalStaffId), 3000);
            for (ElementalStaff staff : ElementalStaff.values()) {
                if (Rs2Inventory.hasItem(staff.getItemId()) && !Rs2Equipment.isWearing(staff.getItemId())) {
                    Rs2Bank.depositAll(staff.getItemId());
                    sleep(200, 300);
                }
            }
        }
    }

    private int findBestStaffInBank() {
        if (Rs2Bank.hasItem(ItemID.MUD_BATTLESTAFF)) {
            return ItemID.MUD_BATTLESTAFF;
        }
        if (Rs2Bank.hasItem(ItemID.MYSTIC_MUD_STAFF)) {
            return ItemID.MYSTIC_MUD_STAFF;
        }
        return -1;
    }

    private boolean isWearingElementalStaffFor(Runes rune) {
        for (ElementalStaff staff : ElementalStaff.values()) {
            if (Rs2Equipment.isWearing(staff.getItemId()) && staff.providesRune(rune)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasRequiredRunes() {
        boolean hasNature = Rs2Inventory.hasItem(Runes.NATURE.getItemId());
        boolean hasWater = Rs2Inventory.hasItemAmount(Runes.WATER.getItemId(), 2) || isWearingElementalStaffFor(Runes.WATER);
        boolean hasEarth = Rs2Inventory.hasItemAmount(Runes.EARTH.getItemId(), 2) || isWearingElementalStaffFor(Runes.EARTH);
        return hasNature && hasWater && hasEarth;
    }

    private void ensureCorrectRunes() {
        if (!Rs2Inventory.hasItem(Runes.NATURE.getItemId())) {
            withdrawRuneIfAvailable(Runes.NATURE, 1);
        }
        if (!isWearingElementalStaffFor(Runes.WATER) && !Rs2Inventory.hasItemAmount(Runes.WATER.getItemId(), 2)) {
            withdrawRuneIfAvailable(Runes.WATER, 2);
        }
        if (!isWearingElementalStaffFor(Runes.EARTH) && !Rs2Inventory.hasItemAmount(Runes.EARTH.getItemId(), 2)) {
            withdrawRuneIfAvailable(Runes.EARTH, 2);
        }
    }

    private void withdrawRuneIfAvailable(Runes rune, int amount) {
        if (Rs2Bank.hasBankItem(rune.getItemId(), amount)) {
            Rs2Bank.withdrawAll(rune.getItemId());
            sleepUntil(() -> Rs2Inventory.hasItem(rune.getItemId()));
        } else {
            Microbot.showMessage("Out of " + rune.name() + " runes!");
            shutdown();
        }
    }

    private void handleBoneWithdrawal() {
        if (!Rs2Bank.hasItem(BONES_ID)) {
            Microbot.showMessage("You have no bones left in your bank.");
            shutdown();
            return;
        }
        Rs2Bank.withdrawAll(BONES_ID);
        sleepUntil(() -> Rs2Inventory.hasItem(BONES_ID));
    }
}