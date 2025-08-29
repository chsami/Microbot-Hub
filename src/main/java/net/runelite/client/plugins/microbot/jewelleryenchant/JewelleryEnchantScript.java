package net.runelite.client.plugins.microbot.jewelleryenchant;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.jewelleryenchant.util.ElementalStaff;
import net.runelite.client.plugins.microbot.jewelleryenchant.util.Jewellery;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Runes;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class JewelleryEnchantScript extends Script {

    public boolean run(JewelleryEnchantConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !super.run()) return;

                Jewellery jewellery = config.jewellery();

                if (!jewellery.hasRequiredLevel()) {
                    Microbot.showMessage("Magic level too low.");
                    shutdown();
                    return;
                }

                boolean hasRunes = hasRequiredRunes(jewellery);
                boolean hasJewellery = Rs2Inventory.hasItem(jewellery.getUnenchantedId());

                if (!hasRunes || !hasJewellery) {
                    bankAndRestock(jewellery);
                    return;
                }

                if (Rs2Bank.isOpen()) {
                    Rs2Bank.closeBank();
                    sleepUntil(() -> !Rs2Bank.isOpen());
                    return;
                }

                Rs2Magic.cast(jewellery);
                sleep(200, 300);
                Rs2Inventory.interact(jewellery.getName(), "Use");

            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 2000, TimeUnit.MILLISECONDS);
        return true;
    }

    private void bankAndRestock(Jewellery jewellery) {
        if (!Rs2Bank.isOpen()) {
            Rs2Bank.openBank();
            return;
        }

        Rs2Bank.depositAllExcept(item -> item.getId() == Runes.COSMIC.getItemId());
        sleep(300, 500);

        handleStaffEquipping(jewellery);
        ensureCorrectRunes(jewellery);
        handleJewelleryWithdrawal(jewellery);
    }

    private void handleStaffEquipping(Jewellery jewellery) {
        Runes elementalRune = jewellery.getElementalRune();
        if (isWearingElementalStaffFor(elementalRune)) {
            return;
        }

        int staffToWithdraw = -1;
        for (ElementalStaff staff : ElementalStaff.values()) {
            if (staff.providesRune(elementalRune) && Rs2Bank.hasItem(staff.getItemId())) {
                staffToWithdraw = staff.getItemId();
                break;
            }
        }

        if (staffToWithdraw != -1) {
            Rs2Bank.withdrawAndEquip(staffToWithdraw);
            sleepUntil(() -> isWearingElementalStaffFor(elementalRune), 3000);

            for (ElementalStaff staff : ElementalStaff.values()) {
                if (Rs2Inventory.hasItem(staff.getItemId())) {
                    // Check if the staff in inventory is the one we just equipped. If so, don't deposit it.
                    if (Rs2Equipment.isWearing(staff.getItemId())) continue;

                    Rs2Bank.depositAll(staff.getItemId());
                    sleep(200, 300);
                }
            }
        }
    }

    private boolean isWearingElementalStaffFor(Runes rune) {
        for (ElementalStaff staff : ElementalStaff.values()) {
            if (Rs2Equipment.isWearing(staff.getItemId()) && staff.providesRune(rune)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasRequiredRunes(Jewellery jewellery) {
        if (!Rs2Inventory.hasItem(Runes.COSMIC.getItemId())) {
            return false;
        }
        for (Map.Entry<Runes, Integer> entry : jewellery.getRequiredRunes().entrySet()) {
            Runes rune = entry.getKey();
            int amount = entry.getValue();
            if (rune == Runes.COSMIC) continue;
            if (!isWearingElementalStaffFor(rune) && !Rs2Inventory.hasItemAmount(rune.getItemId(), amount)) {
                return false;
            }
        }
        return true;
    }

    private void ensureCorrectRunes(Jewellery jewellery) {
        if (!Rs2Inventory.hasItem(Runes.COSMIC.getItemId())) {
            if (Rs2Bank.hasItem(Runes.COSMIC.getItemId())) {
                Rs2Bank.withdrawAll(Runes.COSMIC.getItemId());
                sleepUntil(() -> Rs2Inventory.hasItem(Runes.COSMIC.getItemId()));
            } else {
                Microbot.showMessage("Out of Cosmic runes!");
                shutdown();
                return;
            }
        }
        for (Map.Entry<Runes, Integer> entry : jewellery.getRequiredRunes().entrySet()) {
            Runes rune = entry.getKey();
            int amount = entry.getValue();
            if (rune == Runes.COSMIC) continue;
            boolean needsRuneInInventory = !isWearingElementalStaffFor(rune);
            if (needsRuneInInventory && !Rs2Inventory.hasItemAmount(rune.getItemId(), amount)) {
                if (Rs2Bank.hasBankItem(rune.getItemId(), amount)) {
                    Rs2Bank.withdrawX(rune.getItemId(), amount);
                    sleep(200, 400);
                } else {
                    Microbot.showMessage("Out of " + rune.name() + " runes!");
                    shutdown();
                    return;
                }
            }
        }
    }

    private void handleJewelleryWithdrawal(Jewellery jewellery) {
        if (!Rs2Bank.hasItem(jewellery.getUnenchantedId())) {
            Microbot.showMessage("You have no " + jewellery.getName() + " left in your bank.");
            shutdown();
            return;
        }
        Rs2Bank.withdrawAll(jewellery.getUnenchantedId());
        sleepUntil(() -> Rs2Inventory.hasItem(jewellery.getUnenchantedId()));
    }
}