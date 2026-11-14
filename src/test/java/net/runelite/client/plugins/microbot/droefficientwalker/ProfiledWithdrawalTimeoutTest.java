package net.runelite.client.plugins.microbot.droefficientwalker;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.FutureTask;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static net.runelite.client.plugins.microbot.droefficientwalker.ApeAtollGreegreeIntegrationTest.*;

class ProfiledWithdrawalTimeoutTest {
    @Test void profiledActionGetsItsOwnBudgetThenWaitsForInventoryReceipt()throws Exception{
        try(var f=new HousePortalRecoveryTest.Fixture();var bank=mockStatic(Rs2Bank.class)){
            f.inside.set(false);bank.when(Rs2Bank::isOpen).thenReturn(true);state(f.coordinator,"WITHDRAWING");set(f.coordinator,"pendingTarget",new WorldPoint(3165,3487,0));
            set(f.coordinator,"activeWithdrawal",Map.entry(2552,1));set(f.coordinator,"activeWithdrawalStartedTick",1);
            FutureTask<Boolean> pending=new FutureTask<>(()->false);set(f.coordinator,"bankAction",pending);
            f.clock.set(12);f.tick();assertEquals("WITHDRAWING",f.state());assertFalse(pending.isCancelled(),"Profiled action is not cancelled by the shorter receipt timeout");
            f.clock.set(15);pending.run();f.tick();assertEquals(15,get(f.coordinator,"activeWithdrawalCompletedTick"));
            f.clock.set(24);f.tick();assertEquals("WITHDRAWING",f.state());
            f.clock.set(25);f.tick();assertNotEquals("WITHDRAWING",f.state(),"An unacknowledged withdrawal still has a bounded failure");
        }
    }
    @Test void mouseAndPauseHoldsPreservePreparationAndReceiptBudgets()throws Exception{
        try(var f=new HousePortalRecoveryTest.Fixture();var bank=mockStatic(Rs2Bank.class)){
            f.inside.set(false);bank.when(Rs2Bank::isOpen).thenReturn(true);state(f.coordinator,"WITHDRAWING");set(f.coordinator,"activeWithdrawal",Map.entry(2552,1));set(f.coordinator,"activeWithdrawalStartedTick",1);set(f.coordinator,"activeWithdrawalCompletedTick",3);
            Method hold=WalkerTestReflection.declaredMethod(f.coordinator.getClass(), "holdPreparationBudget",boolean.class);hold.setAccessible(true);
            f.clock.set(4);hold.invoke(f.coordinator,true);f.clock.set(1004);hold.invoke(f.coordinator,false);f.tick();
            assertEquals(1003,get(f.coordinator,"activeWithdrawalCompletedTick"));assertEquals("WITHDRAWING",f.state());
        }
    }
}
