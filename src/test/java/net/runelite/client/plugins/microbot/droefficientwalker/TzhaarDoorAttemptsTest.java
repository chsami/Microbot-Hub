package net.runelite.client.plugins.microbot.droefficientwalker;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TzhaarDoorAttemptsTest {
    @Test void normalDoorFailureHasTwoAttemptsTotal(){
        var attempts=new AtomicInteger();
        assertFalse(TzhaarDoorAttempts.pass(()->true,()->false,()->{attempts.incrementAndGet();return true;},()->false));
        assertEquals(2,attempts.get());
    }
    @Test void observedPassageAndHumanHoldPreventMoreClicks(){
        var attempts=new AtomicInteger();
        assertTrue(TzhaarDoorAttempts.pass(()->true,()->false,()->{attempts.incrementAndGet();return true;},()->true));assertEquals(1,attempts.get());
        assertFalse(TzhaarDoorAttempts.pass(()->false,()->false,()->{fail("Held input clicked");return true;},()->false));
        assertTrue(TzhaarDoorAttempts.pass(()->true,()->true,()->{fail("Already crossed");return true;},()->false));
    }
    @Test void actionStillRunningAfterTheLandingWaitCannotReceiveAnotherClick(){
        var attempts=new AtomicInteger();var busy=new AtomicBoolean();var crossed=new AtomicBoolean();
        assertTrue(TzhaarDoorAttempts.pass(()->true,()->crossed.get()&&!busy.get(),
            ()->{attempts.incrementAndGet();return true;},
            ()->{busy.set(true);return false;},busy::get,
            ()->{assertEquals(1,attempts.get());crossed.set(true);busy.set(false);return true;}));
        assertEquals(1,attempts.get());
    }
    @Test void unfinishedActionTimeoutStopsWithoutASecondPass(){
        var attempts=new AtomicInteger();var busy=new AtomicBoolean();
        assertFalse(TzhaarDoorAttempts.pass(()->true,()->false,()->{attempts.incrementAndGet();return true;},
            ()->{busy.set(true);return false;},busy::get,()->false));
        assertEquals(1,attempts.get());
    }
}
