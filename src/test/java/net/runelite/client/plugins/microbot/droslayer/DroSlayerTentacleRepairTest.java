package net.runelite.client.plugins.microbot.droslayer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.ItemID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class DroSlayerTentacleRepairTest {
    @Test public void bankingLeavesRepairConfirmationToItsOwnerInsteadOfGenericDialogueDismissal() throws Exception {
        Fake env = new Fake();
        env.open = false;
        env.inventory.add(ItemID.KRAKEN_TENTACLE);
        env.inventory.add(ItemID.ABYSSAL_WHIP);
        var repair = new DroSlayerTentacleRepair(env, () -> env.now);
        repair.begin();
        tick(repair, env); // Use tentacle on whip, awaiting the recorded Yes/No prompt.
        env.confirmation = true;
        env.now += 1200L;
        var script = new DroSlayerScript();
        var field = DroSlayerScript.class.getDeclaredField("tentacleRepair");
        field.setAccessible(true);
        field.set(script, repair);
        var banking = DroSlayerScript.class.getDeclaredMethod("handleBankingState");
        banking.setAccessible(true);
        try (var dialogue = mockStatic(net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue.class)) {
            dialogue.when(net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue::isInDialogue).thenReturn(true);
            banking.invoke(script);
            assertEquals(List.of("combine", "confirm"), env.actions);
            assertTrue(repair.isActive()); // Still wait for the finished weapon, not merely the click.
            dialogue.verify(net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue::isInDialogue, never());
        }
    }

    @Test public void rebuildUnequipsWithdrawsAndHandlesBothPromptsBeforeSuccess() {
        Fake env = new Fake();
        env.worn = true;
        DroSlayerTentacleRepair repair = new DroSlayerTentacleRepair(env, () -> env.now);
        repair.begin();
        tick(repair, env); // Close bank before removing equipped tentacle.
        tick(repair, env);
        tick(repair, env); // Reopen bank for whip.
        tick(repair, env);
        tick(repair, env); // Close bank for combination.
        tick(repair, env);
        assertEquals(List.of("close", "unequip", "open", "withdraw:4151", "close", "combine"), env.actions);
        assertTrue(repair.isActive());
        tick(repair, env); // No observed dialogue/result yet: don't combine again.
        assertEquals(6, env.actions.size());
        env.warning = true;
        tick(repair, env);
        env.confirmation = true;
        tick(repair, env);
        assertEquals("continue", env.actions.get(6));
        assertEquals("confirm", env.actions.get(7));
        assertTrue(repair.isActive()); // Clicking Yes is not proof of success.
        env.inventory.clear();
        env.inventory.add(ItemID.ABYSSAL_TENTACLE);
        assertEquals(DroSlayerTentacleRepair.Result.READY, tick(repair, env));
        assertFalse(repair.isActive());
    }

    @Test public void makesRoomBeforeUnequippingAndWithdrawing() {
        Fake env = new Fake();
        env.worn = true;
        env.slots = 0;
        DroSlayerTentacleRepair repair = new DroSlayerTentacleRepair(env, () -> env.now);
        repair.begin();
        tick(repair, env);
        assertEquals(List.of("space"), env.actions);
        tick(repair, env);
        tick(repair, env);
        tick(repair, env);
        tick(repair, env);
        assertEquals("space", env.actions.get(env.actions.size() - 1));
        assertFalse(env.inventory.contains(ItemID.ABYSSAL_WHIP));
        tick(repair, env);
        assertTrue(env.inventory.contains(ItemID.ABYSSAL_WHIP));
    }

    @Test public void missingWhipBlocksWithoutCombining() {
        Fake env = new Fake();
        env.inventory.add(ItemID.KRAKEN_TENTACLE);
        env.stock.remove(ItemID.ABYSSAL_WHIP);
        DroSlayerTentacleRepair repair = new DroSlayerTentacleRepair(env, () -> env.now);
        repair.begin();
        assertEquals(DroSlayerTentacleRepair.Result.BLOCKED, tick(repair, env));
        assertTrue(repair.failureReason().contains("No abyssal whip"));
        assertTrue(env.actions.isEmpty());
    }

    @Test public void timeoutDoesNotReportAnUnobservedRepairAsSuccess() {
        Fake env = new Fake();
        env.inventory.add(ItemID.KRAKEN_TENTACLE);
        env.inventory.add(ItemID.ABYSSAL_WHIP);
        DroSlayerTentacleRepair repair = new DroSlayerTentacleRepair(env, () -> env.now);
        repair.begin();
        tick(repair, env);
        tick(repair, env);
        env.now += 90_000L;
        assertEquals(DroSlayerTentacleRepair.Result.BLOCKED, repair.step());
        assertTrue(repair.failureReason().contains("timed out"));
        assertEquals(List.of("close", "combine"), env.actions);
    }

    @Test public void pauseAndRetrySpacingIssueNoExtraActions() {
        Fake env = new Fake();
        env.inventory.add(ItemID.KRAKEN_TENTACLE);
        DroSlayerTentacleRepair repair = new DroSlayerTentacleRepair(env, () -> env.now);
        repair.begin();
        env.available = false;
        repair.step();
        assertTrue(env.actions.isEmpty());
        env.available = true;
        repair.step();
        repair.step();
        assertEquals(List.of("withdraw:4151"), env.actions);
        repair.reset();
        assertFalse(repair.isActive());
    }

    private static DroSlayerTentacleRepair.Result tick(DroSlayerTentacleRepair repair, Fake env) {
        env.now += 1200L;
        return repair.step();
    }
    private static class Fake implements DroSlayerTentacleRepair.Environment {
        long now;
        boolean open = true, worn, warning, confirmation, available = true;
        int slots = 28;
        Set<Integer> inventory = new HashSet<>();
        Set<Integer> stock = new HashSet<>(Set.of(ItemID.KRAKEN_TENTACLE, ItemID.ABYSSAL_WHIP));
        List<String> actions = new ArrayList<>();
        public boolean available() { return available; }
        public boolean bankOpen() { return open; }
        public boolean equipped(int id) { return id == ItemID.KRAKEN_TENTACLE && worn; }
        public boolean carried(int id) { return inventory.contains(id); }
        public int bankCount(int id) { return stock.contains(id) ? 1 : 0; }
        public int freeSlots() { return slots; }
        public boolean openBank() { actions.add("open"); open = true; return true; }
        public boolean closeBank() { actions.add("close"); open = false; return true; }
        public boolean makeSpace() { actions.add("space"); slots++; return true; }
        public boolean unequip() { actions.add("unequip"); worn = false; inventory.add(ItemID.KRAKEN_TENTACLE); slots--; return true; }
        public boolean withdraw(int id) { actions.add("withdraw:" + id); inventory.add(id); slots--; return true; }
        public boolean combine() { actions.add("combine"); return true; }
        public boolean warning() { return warning; }
        public boolean confirmation() { return confirmation; }
        public boolean continueWarning() { actions.add("continue"); warning = false; return true; }
        public boolean confirm() { actions.add("confirm"); confirmation = false; return true; }
    }
}
