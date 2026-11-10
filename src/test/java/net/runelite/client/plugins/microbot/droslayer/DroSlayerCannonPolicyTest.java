package net.runelite.client.plugins.microbot.droslayer;

import org.junit.jupiter.api.Test;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.droslayer.profile.BaseProfileDro;
import java.util.function.BooleanSupplier;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

public class DroSlayerCannonPolicyTest {
    @Test public void wildernessCompletionRequestsNextAssignmentBeforeRestoration() throws Exception {
        var script = new DroSlayerScript();
        var config = mock(DroSlayerConfig.class);
        when(config.slayerMaster()).thenReturn(DroSlayerMaster.KRYSTILIA);
        when(config.getNewTask()).thenReturn(true);
        field(script, "config", config);
        field(script, "initialSetupDone", true);
        var completion = DroSlayerScript.class.getDeclaredMethod("finishTaskAndChooseNextStop");
        completion.setAccessible(true);
        try (var runtime = mockStatic(DroSlayerRuntime.class)) { completion.invoke(script); }
        assertEquals(DroSlayerState.GETTING_TASK, DroSlayerPlugin.getState());
        var pending = DroSlayerScript.class.getDeclaredField("wildernessMasterBeforeRestore"); pending.setAccessible(true);
        assertEquals(true, pending.get(script));
        var surface = DroSlayerScript.class.getDeclaredMethod("handlePostTaskEscapeToSurface", net.runelite.api.coords.WorldPoint.class);
        surface.setAccessible(true);
        assertEquals(false, surface.invoke(script, new net.runelite.api.coords.WorldPoint(3338, 10134, 0)));
        // The task/master route belongs to Walker, not a pre-task Ferox detour.
    }
    @Test public void fewerThanTenRemainingSkipsAmmoOnlyBankingForBothMasters() throws Exception {
        for (DroSlayerMaster master : new DroSlayerMaster[]{DroSlayerMaster.KRYSTILIA, DroSlayerMaster.DURADEL}) {
            DroSlayerScript script = new DroSlayerScript();
            var config = mock(DroSlayerConfig.class);
            when(config.slayerMaster()).thenReturn(master);
            when(config.bankForCannonballs()).thenReturn(true);
            when(config.enableAutoBanking()).thenReturn(true);
            when(config.cannonballThreshold()).thenReturn(50);
            field(script, "config", config); field(script, "isUsingCannon", true);
            var restock = DroSlayerScript.class.getDeclaredMethod("shouldRestockCannonballs"); restock.setAccessible(true);
            try (var inventory = mockStatic(Rs2Inventory.class);
                 var slayer = mockStatic(net.runelite.client.plugins.microbot.util.skills.slayer.Rs2Slayer.class)) {
                for (int remaining : new int[]{0, 2, 9}) {
                    slayer.when(net.runelite.client.plugins.microbot.util.skills.slayer.Rs2Slayer::getSlayerTaskSize).thenReturn(remaining);
                    assertEquals(false, restock.invoke(script));
                }
                slayer.when(net.runelite.client.plugins.microbot.util.skills.slayer.Rs2Slayer::getSlayerTaskSize).thenReturn(10);
                assertEquals(true, restock.invoke(script));
            }
        }
    }
    private static Object supply(DroSlayerScript script, String name) throws Exception {
        var method = DroSlayerScript.class.getDeclaredMethod("ensureCannonItemAtBank", String.class, int.class);
        method.setAccessible(true); return method.invoke(script, name, 1);
    }
    private static void field(DroSlayerScript script, String name, Object value) throws Exception {
        var field = DroSlayerScript.class.getDeclaredField(name); field.setAccessible(true); field.set(script, value);
    }
    private static int failures(DroSlayerScript script) throws Exception {
        var field = DroSlayerScript.class.getDeclaredField("cannonWithdrawalFailures");
        field.setAccessible(true); return field.getInt(script);
    }
    @Test public void rejectedCannonWithdrawalRetriesWithCooldownAndThenObservesSuccess() throws Exception {
        DroSlayerScript script = new DroSlayerScript();
        field(script, "isUsingCannon", true);
        var carried = new java.util.concurrent.atomic.AtomicInteger();
        try (var bank = mockStatic(Rs2Bank.class); var inventory = mockStatic(Rs2Inventory.class);
             var runtime = mockStatic(DroSlayerRuntime.class)) {
            bank.when(Rs2Bank::isOpen).thenReturn(true);
            bank.when(() -> Rs2Bank.count("Cannon base", false)).thenReturn(1);
            inventory.when(Rs2Inventory::emptySlotCount).thenReturn(2);
            inventory.when(() -> Rs2Inventory.itemQuantity("Cannon base", false)).thenAnswer(call -> carried.get());
            runtime.when(() -> DroSlayerRuntime.performAction(any(BaseProfileDro.ActionPhase.class), eq(false), any(BooleanSupplier.class)))
                    .thenAnswer(call -> ((BooleanSupplier) call.getArgument(2)).getAsBoolean());
            bank.when(() -> Rs2Bank.withdrawX("Cannon base", 1, false)).thenReturn(false);
            assertEquals(false, supply(script, "Cannon base"));
            assertEquals(1, failures(script));
            assertEquals(false, supply(script, "Cannon base"));
            bank.verify(() -> Rs2Bank.withdrawX("Cannon base", 1, false), times(1));
            field(script, "cannonWithdrawalRetryAt", 0L);
            bank.when(() -> Rs2Bank.withdrawX("Cannon base", 1, false)).thenAnswer(call -> { carried.set(1); return true; });
            assertEquals(true, supply(script, "Cannon base"));
            assertEquals(0, failures(script));
        }
    }
    @Test public void profileHoldDoesNotConsumeWithdrawalFailures() throws Exception {
        DroSlayerScript script = new DroSlayerScript();
        try (var bank = mockStatic(Rs2Bank.class); var inventory = mockStatic(Rs2Inventory.class);
             var runtime = mockStatic(DroSlayerRuntime.class)) {
            bank.when(Rs2Bank::isOpen).thenReturn(true);
            bank.when(() -> Rs2Bank.count("Cannon base", false)).thenReturn(1);
            inventory.when(Rs2Inventory::emptySlotCount).thenReturn(2);
            assertEquals(false, supply(script, "Cannon base"));
            assertEquals(0, failures(script));
            bank.verify(() -> Rs2Bank.withdrawX("Cannon base", 1, false), never());
        }
    }
    @Test public void threeFailuresDisableOnlyAfterSpacedAttemptsAndEarlierPartsDoNotResetBudget() throws Exception {
        DroSlayerScript script = new DroSlayerScript();
        field(script, "isUsingCannon", true);
        DroSlayerConfig config = mock(DroSlayerConfig.class);
        when(config.slayerMaster()).thenReturn(DroSlayerMaster.KRYSTILIA);
        field(script, "config", config);
        try (var bank = mockStatic(Rs2Bank.class); var inventory = mockStatic(Rs2Inventory.class);
             var runtime = mockStatic(DroSlayerRuntime.class); var reader = mockStatic(DroSlayerWildernessTaskReader.class)) {
            reader.when(DroSlayerWildernessTaskReader::getTaskName).thenReturn("Bandits");
            bank.when(Rs2Bank::isOpen).thenReturn(true);
            bank.when(() -> Rs2Bank.count("Cannon furnace", false)).thenReturn(1);
            inventory.when(Rs2Inventory::emptySlotCount).thenReturn(2);
            inventory.when(() -> Rs2Inventory.itemQuantity("Cannon base", false)).thenReturn(1);
            runtime.when(() -> DroSlayerRuntime.performAction(any(BaseProfileDro.ActionPhase.class), eq(false), any(BooleanSupplier.class)))
                    .thenAnswer(call -> ((BooleanSupplier) call.getArgument(2)).getAsBoolean());
            for (int attempt = 1; attempt <= 3; attempt++) {
                field(script, "cannonWithdrawalRetryAt", 0L);
                assertEquals(true, supply(script, "Cannon base"));
                assertEquals(attempt == 3, supply(script, "Cannon furnace"));
                if (attempt < 3) assertEquals(attempt, failures(script));
            }
            var policyField = DroSlayerScript.class.getDeclaredField("cannonPolicy"); policyField.setAccessible(true);
            assertTrue(((DroSlayerCannonPolicy) policyField.get(script)).isDisabled());
            bank.verify(() -> Rs2Bank.withdrawX("Cannon furnace", 1, false), times(3));
        }
    }
    @Test public void restartClearsSameTaskDisableAndPreviousBankVisit() {
        var policy = new DroSlayerCannonPolicy();
        policy.selectTask("Bandits"); policy.observeBank(true, 350); policy.disable();
        policy.reset(); policy.selectTask("Bandits");
        assertFalse(policy.isDisabled());
        policy.disable(); policy.observeBank(true, 350);
        assertFalse(policy.isDisabled());
    }
    @Test public void assignmentCleanupClearsCannonPolicyBeforeTheNextTaskIsSelected() throws Exception {
        DroSlayerScript script = new DroSlayerScript();
        var field = DroSlayerScript.class.getDeclaredField("cannonPolicy"); field.setAccessible(true);
        var policy = (DroSlayerCannonPolicy) field.get(script);
        policy.selectTask("Ice warriors"); policy.observeBank(true, 350);
        policy.observeRestock(0, 50); policy.disable();
        var cleanup = DroSlayerScript.class.getDeclaredMethod("resetTravelData"); cleanup.setAccessible(true);
        try (var runtime = mockStatic(DroSlayerRuntime.class)) {
            cleanup.invoke(script);
        }
        assertFalse(policy.isDisabled());
        assertTrue(policy.needsRestock(true, true, true, 0, true, 50));
        policy.disable(); policy.observeBank(true, 350);
        assertFalse(policy.isDisabled()); // Completion also cleared the old bank-visit latch.
        policy.selectTask("Bandits");
        assertTrue(policy.needsRestock(true, true, true, 0, true, 50));
    }
    @Test public void replenishmentTopsUp55AtBankInsteadOfAcceptingThe50Threshold() {
        assertTrue(DroSlayerCannonPolicy.needsBankTopUp(true, 55, true, 50));
        assertFalse(DroSlayerCannonPolicy.needsBankTopUp(true, 350, true, 50));
        assertTrue(DroSlayerCannonPolicy.needsBankTopUp(true, 55, false, 50));
        assertFalse(DroSlayerCannonPolicy.needsBankTopUp(true, 1000, false, 50));
        // The opt-out still has its original bank top-up behavior.
        assertFalse(DroSlayerCannonPolicy.needsBankTopUp(false, 55, true, 50));
        assertTrue(DroSlayerCannonPolicy.needsBankTopUp(false, 49, true, 50));
    }

    @Test public void loadedAmmunitionDoesNotSendAnAssembledCannonStraightBackToBank() throws Exception {
        DroSlayerScript script = new DroSlayerScript();
        DroSlayerConfig config = org.mockito.Mockito.mock(DroSlayerConfig.class);
        org.mockito.Mockito.when(config.slayerMaster()).thenReturn(DroSlayerMaster.KRYSTILIA);
        org.mockito.Mockito.when(config.bankForCannonballs()).thenReturn(true);
        org.mockito.Mockito.when(config.enableAutoBanking()).thenReturn(true);
        org.mockito.Mockito.when(config.cannonballThreshold()).thenReturn(50);
        for (String name : new String[]{"config", "isUsingCannon", "cannonPlaced"}) {
            java.lang.reflect.Field field = DroSlayerScript.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(script, name.equals("config") ? config : true);
        }
        java.lang.reflect.Method restock = DroSlayerScript.class.getDeclaredMethod("shouldRestockCannonballs");
        restock.setAccessible(true);
        try (var inventory = org.mockito.Mockito.mockStatic(net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory.class);
             var dro = org.mockito.Mockito.mockStatic(net.runelite.client.plugins.microbot.Microbot.class);
             var slayer = mockStatic(net.runelite.client.plugins.microbot.util.skills.slayer.Rs2Slayer.class)) {
            slayer.when(net.runelite.client.plugins.microbot.util.skills.slayer.Rs2Slayer::getSlayerTaskSize).thenReturn(20);
            inventory.when(() -> net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory.itemQuantity("Steel cannonball", true))
                    .thenReturn(25);
            dro.when(() -> net.runelite.client.plugins.microbot.Microbot.getVarbitPlayerValue(net.runelite.api.VarPlayer.CANNON_AMMO))
                    .thenReturn(30);
            assertEquals(false, restock.invoke(script)); // 55 total, merely transferred into the cannon.
            dro.when(() -> net.runelite.client.plugins.microbot.Microbot.getVarbitPlayerValue(net.runelite.api.VarPlayer.CANNON_AMMO))
                    .thenReturn(24);
            assertEquals(true, restock.invoke(script)); // 49 total after actual firing.
            java.lang.reflect.Field placed = DroSlayerScript.class.getDeclaredField("cannonPlaced");
            placed.setAccessible(true); placed.set(script, false);
            assertEquals(true, restock.invoke(script)); // Ignore stale loaded ammo when no cannon is placed.
        }
    }
    @Test public void wildernessAmmoStaysAt350RegardlessOfBankThreshold() {
        assertEquals(350, DroSlayerCannonPolicy.ammunitionTarget(true, 100));
        assertEquals(350, DroSlayerCannonPolicy.ammunitionTarget(true, 2000));
        assertEquals(1000, DroSlayerCannonPolicy.ammunitionTarget(false, 100));
        assertEquals(2000, DroSlayerCannonPolicy.ammunitionTarget(false, 2000));
    }
    @Test public void failedSameTaskDoesNotReenableOnDetectionOrRepeatedBankTicks() {
        DroSlayerCannonPolicy policy = new DroSlayerCannonPolicy();
        policy.selectTask("Black demons");
        policy.observeBank(true, 1000);
        policy.disable();
        policy.selectTask("BLACK DEMONS");
        policy.observeBank(true, 1000);
        assertTrue(policy.isDisabled());
        policy.leaveBank();
        policy.observeBank(true, 30);
        assertFalse(policy.isDisabled());
    }

    @Test public void stockRequiresAllPartsAndActualAmmunitionButNoArbitraryMinimum() {
        DroSlayerCannonPolicy policy = new DroSlayerCannonPolicy();
        policy.selectTask("Suqahs");
        policy.disable();
        policy.observeBank(false, 1000);
        assertTrue(policy.isDisabled());
        policy.leaveBank();
        policy.observeBank(true, 0);
        assertTrue(policy.isDisabled());
        policy.leaveBank();
        policy.observeBank(true, 1);
        assertFalse(policy.isDisabled());
        assertFalse(DroSlayerCannonPolicy.canDeploy(false, 1000));
        assertFalse(DroSlayerCannonPolicy.canDeploy(true, 0));
        assertTrue(DroSlayerCannonPolicy.canDeploy(true, 1));
    }

    @Test public void aNewTaskMayTryCannonAgain() {
        DroSlayerCannonPolicy policy = new DroSlayerCannonPolicy();
        policy.selectTask("Black demons");
        policy.disable();
        policy.selectTask("Suqahs");
        assertFalse(policy.isDisabled());
    }

    @Test public void missingOrStoppedCannonNeverSuppressesOrdinaryAttacks() {
        assertFalse(DroSlayerCannonPolicy.suppressManualAttacks(true, false, false));
        assertFalse(DroSlayerCannonPolicy.suppressManualAttacks(true, true, false));
        assertFalse(DroSlayerCannonPolicy.suppressManualAttacks(false, true, true));
        assertTrue(DroSlayerCannonPolicy.suppressManualAttacks(true, true, true));
    }

    @Test public void optionalSupplyFilterKeepsCombatGearAndOtherRequirements() {
        assertTrue(DroSlayerCannonPolicy.isSupply("Cannon base (or)"));
        assertTrue(DroSlayerCannonPolicy.isSupply("Steel cannonball"));
        assertTrue(DroSlayerCannonPolicy.isSupply("Granite cannonball"));
        assertFalse(DroSlayerCannonPolicy.isSupply("Anti-dragon shield"));
        assertFalse(DroSlayerCannonPolicy.isSupply("Lockpick"));
        assertFalse(DroSlayerCannonPolicy.isSupply("Blighted super restore(4)"));
    }
    @Test public void resupplyIsOptInForBothMastersAndRequiresAutoBanking() {
        DroSlayerCannonPolicy policy = new DroSlayerCannonPolicy();
        for (boolean wild : new boolean[]{false, true}) {
            assertTrue(policy.needsRestock(true, true, true, 99, wild, 100));
            assertFalse(policy.needsRestock(false, true, true, 0, wild, 100));
            assertFalse(policy.needsRestock(true, false, true, 0, wild, 100));
            assertFalse(policy.needsRestock(true, true, false, 0, wild, 100));
            assertFalse(policy.needsRestock(true, true, true, 100, wild, 100));
        }
        assertEquals(100, DroSlayerCannonPolicy.restockThreshold(true, 2000));
        assertFalse(policy.needsRestock(true, true, true, 320 + 28, true, 350));
        assertFalse(policy.needsRestock(true, true, true, 320 + 28, true, 2000));
        assertTrue(policy.needsRestock(true, true, true, 99, true, 350));
        assertFalse(policy.needsRestock(true, true, true, 350, true, 2000));
    }
    @Test public void insufficientStockSuppressesBankLoopsWithoutDisablingRemainingAmmo() {
        DroSlayerCannonPolicy policy = new DroSlayerCannonPolicy();
        policy.selectTask("Suqahs");
        policy.observeRestock(5, 100);
        policy.leaveBank();
        assertFalse(policy.needsRestock(true, true, true, 0, true, 100));
        assertFalse(policy.isDisabled());
        policy.observeRestock(350, 100);
        assertTrue(policy.needsRestock(true, true, true, 99, true, 100));
        policy.disable();
        assertFalse(policy.needsRestock(true, true, true, 0, true, 100));
    }
}
