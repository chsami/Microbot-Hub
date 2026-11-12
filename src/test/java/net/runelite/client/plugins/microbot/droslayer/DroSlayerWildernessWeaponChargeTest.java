package net.runelite.client.plugins.microbot.droslayer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class DroSlayerWildernessWeaponChargeTest
{
    @Test public void idsAndTargetBoundsIncludeAllFourFamilies()
    {
        for (int id : new int[]{22542, 22545, 22547, 22550, 27652, 27655, 27657, 27660})
            assertTrue(DroSlayerWildernessWeaponCharge.isRevenantWeapon(id));
        assertFalse(DroSlayerWildernessWeaponCharge.isRevenantWeapon(4151));
        assertFalse(DroSlayerWildernessWeaponCharge.isRevenantWeapon(22552));
        assertTrue(DroSlayerWildernessWeaponCharge.isRevenantWeapon("Craw’s bow (u)"));
        assertFalse(DroSlayerWildernessWeaponCharge.isRevenantWeapon("Magic shortbow"));
        assertEquals(22545, DroSlayerWildernessWeaponCharge.chargedWeaponId(22542));
        assertEquals(22545, DroSlayerWildernessWeaponCharge.chargedWeaponId(22545));
        assertEquals(22550, DroSlayerWildernessWeaponCharge.chargedWeaponId(22547));
        assertEquals(27655, DroSlayerWildernessWeaponCharge.chargedWeaponId(27652));
        assertEquals(27660, DroSlayerWildernessWeaponCharge.chargedWeaponId(27657));
        assertEquals(4151, DroSlayerWildernessWeaponCharge.chargedWeaponId(4151));
        assertEquals(2000, DroSlayerWildernessWeaponCharge.targetCharges(150, 2000));
        assertEquals(200, DroSlayerWildernessWeaponCharge.targetCharges(-1, 1));
        assertEquals(5000, DroSlayerWildernessWeaponCharge.targetCharges(Integer.MAX_VALUE, Integer.MAX_VALUE));
    }

    @Test public void unchargedBowWithdrawsActivationPlusUsableReserveAndVerifiesCheck()
    {
        Fixture f = new Fixture(); f.e.inventoryWeapon = 22547; f.e.bankEther = 10000;
        assertEquals(DroSlayerWildernessWeaponCharge.Result.READY, f.finish(22550));
        assertEquals(3000, f.e.totalWithdrawnEther);
        assertEquals(2000, f.c.getObservedUsableCharges());
        assertEquals(22550, f.e.inventoryWeapon);
        assertEquals(0, f.e.worn);
        assertTrue(f.e.bank);
        assertEquals(1, f.e.combineClicks);
        assertEquals(1, f.e.checkClicks);
    }

    @Test public void originallyWornChainmaceIsToppedUpAndReequippedOnlyAfterCheck()
    {
        Fixture f = new Fixture(); f.e.worn = 22545; f.e.usable = 500; f.e.bankEther = 10000;
        assertEquals(DroSlayerWildernessWeaponCharge.Result.READY, f.finish(0));
        assertEquals(1500, f.e.totalWithdrawnEther);
        assertEquals(22545, f.e.worn);
        assertEquals(2, f.e.checkClicks);
        assertTrue(f.e.actions.indexOf("check:2000") < f.e.actions.indexOf("wear"));
        assertTrue(f.e.bank);
    }

    @Test public void setupChargedIdCanAcquireOnlyItsMatchingUnchargedBankTwin()
    {
        Fixture f = new Fixture(); f.e.storedWeapon = 27657; f.e.bankEther = 6000;
        assertEquals(DroSlayerWildernessWeaponCharge.Result.READY, f.finish(27660));
        assertTrue(f.e.actions.contains("withdraw-weapon:27657"));
        assertEquals(27660, f.e.inventoryWeapon);
        assertEquals(3000, f.e.totalWithdrawnEther);
        assertEquals(0, f.e.worn); // Equipment setup remains the caller's responsibility for an unworn weapon.
    }

    @Test public void ordinarySetupDoesNotWithdrawAnUnrelatedRevenantWeapon()
    {
        Fixture f = new Fixture(); f.e.storedWeapon = 22547; f.e.bankEther = 10000; f.e.worn = 4151;
        assertEquals(DroSlayerWildernessWeaponCharge.Result.READY, f.c.ensureAtBank(100, 4151, 2000));
        assertTrue(f.e.actions.isEmpty());
        assertEquals(DroSlayerWildernessWeaponCharge.Result.READY, f.c.ensureAtBank(100, 0, 2000));
        assertTrue(f.e.actions.isEmpty());
    }

    @Test public void currentHighChargeWeaponIsCheckedWithoutAddingEther()
    {
        Fixture f = new Fixture(); f.e.worn = 27655; f.e.usable = 2500;
        assertEquals(DroSlayerWildernessWeaponCharge.Result.READY, f.finish(0));
        assertEquals(0, f.e.totalWithdrawnEther);
        assertEquals(0, f.e.combineClicks);
        assertEquals(27655, f.e.worn);
    }

    @Test public void smallStockUsesMinimumUsableReserveAndInsufficientActivationBlocks()
    {
        Fixture f = new Fixture(); f.e.inventoryWeapon = 22542; f.e.bankEther = 1200;
        assertEquals(DroSlayerWildernessWeaponCharge.Result.READY, f.finish(22545));
        assertEquals(1200, f.e.totalWithdrawnEther);
        assertEquals(200, f.c.getObservedUsableCharges());
        Fixture insufficient = new Fixture(); insufficient.e.inventoryWeapon = 22542; insufficient.e.bankEther = 1000;
        assertEquals(DroSlayerWildernessWeaponCharge.Result.BLOCKED, insufficient.finish(22545));
        assertEquals(0, insufficient.e.combineClicks);
        assertTrue(insufficient.c.getFailureReason().contains("Insufficient revenant ether"));
    }

    @Test public void surplusInventoryEtherIsDepositedBeforeCombining()
    {
        Fixture f = new Fixture(); f.e.inventoryWeapon = 22547; f.e.ether = 10000;
        assertEquals(DroSlayerWildernessWeaponCharge.Result.READY, f.finish(22550));
        assertEquals(7000, f.e.totalDepositedEther);
        assertEquals(3000, f.e.lastCombinedEther);
        assertEquals(2000, f.c.getObservedUsableCharges());
    }

    @Test public void observedAmountPromptCompletesWithoutRepeatedCombineClicks()
    {
        Fixture f = new Fixture(); f.e.inventoryWeapon = 27652; f.e.bankEther = 5000; f.e.needsAmount = true;
        assertEquals(DroSlayerWildernessWeaponCharge.Result.READY, f.finish(27655));
        assertEquals(1, f.e.combineClicks);
        assertEquals(3000, f.e.submittedAmount);
        assertEquals(2000, f.c.getObservedUsableCharges());
    }

    @Test public void falseChargeDispatchDoesNotRetryCombinationOrClaimSuccess()
    {
        Fixture f = new Fixture(); f.e.inventoryWeapon = 22547; f.e.bankEther = 10000; f.e.applyCharge = false;
        assertEquals(DroSlayerWildernessWeaponCharge.Result.BLOCKED, f.finish(22550));
        assertEquals(1, f.e.combineClicks);
        assertEquals(0, f.e.checkClicks);
        assertTrue(f.c.getFailureReason().contains("WAIT_CHARGE"));
    }

    @Test public void unrecognizedCheckOrActivationRequirementCannotConfirmCharges()
    {
        Fixture f = new Fixture(); f.e.inventoryWeapon = 22545; f.e.usable = 1000;
        f.e.checkMessage = "You need 1,000 revenant ether to activate your Viggora's chainmace.";
        assertEquals(DroSlayerWildernessWeaponCharge.Result.BLOCKED, f.finish(22545));
        assertEquals(3, f.e.checkClicks);
        assertEquals(0, f.e.totalWithdrawnEther);
        assertEquals(-1, f.c.getObservedUsableCharges());
    }

    @Test public void storedEtherMessageSeparatesActivationFromUsableCharges()
    {
        Fixture f = new Fixture(); f.e.inventoryWeapon = 22545; f.e.usable = 2000;
        f.e.checkMessage = "Your Viggora's chainmace contains <col=ff0000>3,000</col> revenant ether.";
        assertEquals(DroSlayerWildernessWeaponCharge.Result.READY, f.finish(22545));
        assertEquals(2000, f.c.getObservedUsableCharges());
    }

    @Test public void passiveLowChargeAndDepletionMessagesRequestBankWithoutFieldInput()
    {
        Fixture f = new Fixture(); f.e.worn = 22550; f.e.usable = 2500;
        assertEquals(DroSlayerWildernessWeaponCharge.Result.READY, f.finish(0));
        f.e.bank = false; int actions = f.e.actions.size();
        assertFalse(f.c.needsRecharge());
        f.c.onChatMessage("Your Ursine chainmace has 0 charges remaining.");
        assertFalse(f.c.needsRecharge());
        f.c.onChatMessage("Your Craw's bow has 75 charges remaining.");
        assertTrue(f.c.needsRecharge());
        f.c.onChatMessage("Your Craw's bow has run out of charges. Recharge it to continue using it.");
        assertTrue(f.c.needsRecharge());
        assertEquals(0, f.c.getObservedUsableCharges());
        assertEquals(actions, f.e.actions.size());
        f.c.onChatMessage("Your weapon has run out of charges. You need to recharge it to continue using it.");
        assertTrue(f.c.needsRecharge());
        assertEquals(actions, f.e.actions.size());
    }

    @Test public void pausedAndFieldCallsDoNotClickAndCombatInterruptHolds()
    {
        Fixture f = new Fixture(); f.e.worn = 22545; f.e.bank = false;
        assertEquals(DroSlayerWildernessWeaponCharge.Result.WORKING, f.c.ensureAtBank(100, 0, 2000));
        assertTrue(f.e.actions.isEmpty());
        f.e.bank = true; f.e.paused = true;
        assertEquals(DroSlayerWildernessWeaponCharge.Result.WORKING, f.c.ensureAtBank(100, 0, 2000));
        assertTrue(f.e.actions.isEmpty());
        f.e.paused = false;
        f.step(0);
        f.e.sameBank = false;
        int actions = f.e.actions.size();
        assertEquals(DroSlayerWildernessWeaponCharge.Result.BLOCKED, f.step(0));
        assertEquals(actions, f.e.actions.size());
    }

    @Test public void restartDeathResetInvalidatesChargeObservation()
    {
        Fixture f = new Fixture(); f.e.worn = 22545; f.e.usable = 2500;
        assertEquals(DroSlayerWildernessWeaponCharge.Result.READY, f.finish(0));
        f.e.bank = false;
        assertFalse(f.c.needsRecharge());
        f.c.reset();
        assertTrue(f.c.needsRecharge());
    }

    @Test public void nextBankVisitChecksAgainAndBusyVisitCannotBeReset()
    {
        Fixture f = new Fixture(); f.e.worn = 22545; f.e.usable = 2500;
        assertEquals(DroSlayerWildernessWeaponCharge.Result.READY, f.finish(0));
        int firstChecks = f.e.checkClicks;
        f.c.beginBankVisit();
        f.step(0);
        assertTrue(f.c.isBusy());
        f.c.beginBankVisit();
        assertTrue(f.c.isBusy());
        assertEquals(DroSlayerWildernessWeaponCharge.Result.READY, f.finish(0));
        assertEquals(firstChecks + 1, f.e.checkClicks);
    }

    @Test public void spareOfSameFamilyCannotStandInForTheActuallyWornWeapon()
    {
        Fixture f = new Fixture(); f.e.worn = 22545; f.e.inventoryWeapon = 22542; f.e.bankEther = 6000;
        assertEquals(DroSlayerWildernessWeaponCharge.Result.BLOCKED, f.step(0));
        assertTrue(f.e.actions.isEmpty());
        assertTrue(f.c.getFailureReason().contains("Multiple copies"));
        assertEquals(0, f.e.combineClicks);
    }

    @Test public void recentCombatWaitsForItsFlagToClearWithoutBlockingOrClicking()
    {
        Fixture f = new Fixture(); f.e.worn = 22545; f.e.usable = 2500; f.e.combat = true;
        for (int i = 0; i < 20; i++) assertEquals(DroSlayerWildernessWeaponCharge.Result.WORKING, f.step(0));
        assertTrue(f.e.actions.isEmpty());
        assertFalse(f.c.isBlocked());
        f.e.combat = false;
        assertEquals(DroSlayerWildernessWeaponCharge.Result.READY, f.finish(0));
    }

    private static final class Fixture
    {
        final AtomicLong now = new AtomicLong();
        final Fake e = new Fake();
        final DroSlayerWildernessWeaponCharge c = new DroSlayerWildernessWeaponCharge(e, now::get);
        Fixture() { e.controller = c; }
        DroSlayerWildernessWeaponCharge.Result step(int wanted)
        {
            DroSlayerWildernessWeaponCharge.Result result = c.ensureAtBank(100, wanted, 2000);
            now.addAndGet(1500);
            return result;
        }
        DroSlayerWildernessWeaponCharge.Result finish(int wanted)
        {
            for (int i = 0; i < 100; i++)
            {
                DroSlayerWildernessWeaponCharge.Result result = step(wanted);
                if (result != DroSlayerWildernessWeaponCharge.Result.WORKING) return result;
            }
            throw new AssertionError("Controller did not finish: " + e.actions);
        }
    }
    private static final class Fake implements DroSlayerWildernessWeaponCharge.Environment
    {
        DroSlayerWildernessWeaponCharge controller;
        boolean bank = true, paused, sameBank = true, applyCharge = true, needsAmount, prompt, combat;
        int worn, inventoryWeapon, storedWeapon, ether, bankEther, usable;
        int combineClicks, checkClicks, totalWithdrawnEther, totalDepositedEther, lastCombinedEther, submittedAmount;
        String checkMessage;
        final List<String> actions = new ArrayList<>();
        public boolean available() { return true; }
        public boolean paused() { return paused; }
        public boolean bankOpen() { return bank; }
        public boolean sameBank(WorldPoint origin) { return sameBank; }
        public boolean inCombat() { return combat; }
        public WorldPoint location() { return new WorldPoint(3162, 3488, 0); }
        public int equippedWeapon() { return worn; }
        public int inventoryWeapon(DroSlayerWildernessWeaponCharge.Family f) { return f.matches(inventoryWeapon) ? inventoryWeapon : 0; }
        public int bankWeapon(DroSlayerWildernessWeaponCharge.Family f) { return f.matches(storedWeapon) ? storedWeapon : 0; }
        public int inventoryEther() { return ether; }
        public int bankEther() { return bankEther; }
        public int emptySlots() { return 2; }
        public boolean depositFood() { actions.add("food"); return true; }
        public boolean openBank() { actions.add("open"); bank = true; return true; }
        public boolean closeBank() { actions.add("close"); bank = false; return true; }
        public boolean unequip(int id) { actions.add("remove"); inventoryWeapon = worn; worn = 0; return true; }
        public boolean withdrawWeapon(int id) { actions.add("withdraw-weapon:" + id); inventoryWeapon = storedWeapon; storedWeapon = 0; return true; }
        public boolean check(int id)
        {
            checkClicks++; actions.add("check:" + usable);
            DroSlayerWildernessWeaponCharge.Family f = null;
            for (DroSlayerWildernessWeaponCharge.Family candidate : DroSlayerWildernessWeaponCharge.Family.values())
                if (candidate.matches(id)) f = candidate;
            controller.onChatMessage(checkMessage == null ? "Your " + f.name + " has " + usable + " charges remaining." : checkMessage);
            return true;
        }
        public boolean withdrawEther(int amount) { actions.add("ether:" + amount); ether += amount; bankEther -= amount; totalWithdrawnEther += amount; return true; }
        public boolean depositEther(int amount) { actions.add("deposit-ether:" + amount); ether -= amount; bankEther += amount; totalDepositedEther += amount; return true; }
        public boolean combine(int id)
        {
            actions.add("combine"); combineClicks++;
            if (needsAmount) prompt = true; else if (applyCharge) addEther(ether);
            return true;
        }
        private void addEther(int amount)
        {
            for (DroSlayerWildernessWeaponCharge.Family f : DroSlayerWildernessWeaponCharge.Family.values())
                if (f.matches(inventoryWeapon))
                {
                    usable += amount - (inventoryWeapon == f.uncharged ? 1000 : 0);
                    inventoryWeapon = f.charged; ether -= amount; lastCombinedEther = amount; return;
                }
        }
        public boolean amountPrompt() { return prompt; }
        public boolean submitAmount(int amount) { actions.add("amount:" + amount); submittedAmount = amount; prompt = false; addEther(amount); return true; }
        public boolean chargeConfirmation() { return false; }
        public boolean confirmCharge() { actions.add("confirm"); return true; }
        public boolean wear(int id) { actions.add("wear"); worn = inventoryWeapon; inventoryWeapon = 0; return true; }
    }
}
