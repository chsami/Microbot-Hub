package net.runelite.client.plugins.microbot.drofirecape;

import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MinigameBankGuardTest {
    static class Actions extends FcActions {
        int closeRequests, readinessChecks;
        @Override boolean closeBank() { closeRequests++;return true; }
        @Override protected boolean uiReady(long gap) {readinessChecks++;return false;}
    }
    @Test void reopenedBankInvalidatesFinalTeleportTarget() {
        try(var bank=mockStatic(Rs2Bank.class)) {
            bank.when(Rs2Bank::isOpen).thenReturn(true);
            assertNull(FcActions.teleportBounds());
        }
    }
    @Test void successfulCloseRequestStillDefersGroupingUntilBankIsObservedClosed() {
        Actions actions=new Actions();
        try(var bank=mockStatic(Rs2Bank.class);var tabs=mockStatic(Rs2Tab.class)) {
            bank.when(Rs2Bank::isOpen).thenReturn(true);
            assertTrue(actions.minigameStep().contains("bank closure"));
            assertTrue(actions.minigameStep().contains("bank closure"));
            assertEquals(2,actions.closeRequests);assertEquals(0,actions.readinessChecks);
            tabs.verifyNoInteractions();
            bank.when(Rs2Bank::isOpen).thenReturn(false);
            assertEquals("Waiting for minigame interface",actions.minigameStep());
            assertEquals(2,actions.closeRequests);assertEquals(1,actions.readinessChecks);
        }
    }
}
