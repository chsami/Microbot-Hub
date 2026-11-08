package net.runelite.client.plugins.microbot.fourtifysagility.util.action;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import net.runelite.api.Client;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AgilityAsyncActionsTest
{
    @BeforeEach
    public void inputBoundary()
    {
        Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[] {Client.class},
            (object, method, arguments) -> method.getReturnType() == boolean.class ? true
                : method.getReturnType() == int.class ? 0 : null);
        new MicrobotTestSupport(client, (point, entry) -> { });
    }

    @AfterEach
    public void restoreInput()
    {
        MicrobotTestSupport.restoreAll();
    }

    @Test
    public void blockingSdkOperationRunsOffDispatchThreadAndResetInterruptsIt() throws Exception
    {
        AgilityActions actions = new AgilityActions();
        Thread owner = Thread.currentThread();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        AtomicBoolean onOwner = new AtomicBoolean();
        AtomicInteger following = new AtomicInteger();
        actions.enqueueBlocking(() -> true, () ->
        {
            onOwner.set(Thread.currentThread() == owner);
            started.countDown();
            try
            {
                new CountDownLatch(1).await();
            }
            catch (InterruptedException exception)
            {
                interrupted.set(true);
                Thread.currentThread().interrupt();
            }
            finally
            {
                finished.countDown();
            }
        });
        try
        {
            actions.dispatch();
            assertTrue(started.await(2, TimeUnit.SECONDS));
            assertFalse(onOwner.get());
            actions.enqueue(following::incrementAndGet);
            actions.dispatch();
            assertEquals(0, following.get());
            actions.reset();
            assertTrue(finished.await(2, TimeUnit.SECONDS));
            assertTrue(interrupted.get());
            actions.dispatch();
            assertEquals(0, following.get());
        }
        finally
        {
            actions.reset();
        }
    }

    @Test
    public void queuedOperationRechecksEligibilityAndResetCancelsUndeliveredWork()
    {
        AgilityActions actions = new AgilityActions();
        AtomicBoolean valid = new AtomicBoolean(true);
        AtomicInteger calls = new AtomicInteger();
        actions.enqueueBlocking(valid::get, calls::incrementAndGet);
        valid.set(false);
        actions.dispatch();
        assertEquals(0, calls.get());
        actions.enqueueBlocking(() -> true, calls::incrementAndGet);
        actions.reset();
        actions.dispatch();
        assertEquals(0, calls.get());
    }
    @Test
    public void resetCannotOverlapAnSdkOperationThatHasNotReturned() throws Exception
    {
        AgilityActions actions = new AgilityActions();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch returned = new CountDownLatch(1);
        CountDownLatch replacement = new CountDownLatch(1);
        actions.enqueueBlocking(() -> true, () ->
        {
            started.countDown();
            while (release.getCount() != 0)
            {
                try
                {
                    release.await();
                }
                catch (InterruptedException ignored)
                {
                }
            }
            returned.countDown();
        });
        try
        {
            actions.dispatch();
            assertTrue(started.await(2, TimeUnit.SECONDS));
            actions.reset();
            actions.enqueueBlocking(() -> true, replacement::countDown);
            actions.dispatch();
            assertEquals(1, replacement.getCount());
            release.countDown();
            assertTrue(returned.await(2, TimeUnit.SECONDS));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (replacement.getCount() != 0 && System.nanoTime() < deadline)
            {
                actions.dispatch();
                Thread.yield();
            }
            assertTrue(replacement.await(2, TimeUnit.SECONDS));
        }
        finally
        {
            release.countDown();
            actions.reset();
        }
    }

}
