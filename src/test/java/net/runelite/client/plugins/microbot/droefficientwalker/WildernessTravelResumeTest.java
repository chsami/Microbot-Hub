package net.runelite.client.plugins.microbot.droefficientwalker;

import java.lang.reflect.Field;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static net.runelite.client.plugins.microbot.droefficientwalker.WalkerRequestCancellationTest.assertCancelled;
import static org.mockito.Mockito.*;

public class WildernessTravelResumeTest {
    private final WorldPoint target = new WorldPoint(3237, 3622, 0);

    @Test public void observedLogoutRequiresReadyLoginAndClearThreat() {
        WildernessTravelResume resume = new WildernessTravelResume();
        resume.pause(target, 1000);
        resume.observe(GameState.LOGIN_SCREEN);
        resume.observe(GameState.LOADING);
        assertFalse(resume.canResume(false, false, 2000));
        assertFalse(resume.canResume(true, true, 2000));
        assertTrue(resume.canResume(true, false, 2000));
        assertEquals(target, resume.take());
        assertNull(resume.take());
        assertFalse(resume.canResume(true, false, 20000));
    }

    @Test public void rejectedLogoutDoesNotResumeUntilThreatClearsAndGraceExpires() {
        WildernessTravelResume resume = new WildernessTravelResume();
        resume.pause(target, 1000);
        resume.pause(new WorldPoint(3100,3500,0), 7000);
        assertEquals(target, resume.target());
        assertFalse(resume.canResume(true, false, 8999));
        assertFalse(resume.canResume(true, true, 9000));
        assertTrue(resume.canResume(true, false, 9000));
    }

    @Test public void cancellationAndNewDestinationDiscardOldIntent() {
        WildernessTravelResume resume = new WildernessTravelResume();
        resume.pause(target, 1000);
        assertTrue(resume.acceptRequest(target));
        assertFalse(resume.acceptRequest(new WorldPoint(3100,3500,0)));
        assertNull(resume.target());
        resume.pause(target, 1000);
        resume.observe(GameState.LOGIN_SCREEN);
        resume.reset();
        assertFalse(resume.canResume(true, false, 20000));
    }

    @Test public void facadeKeepsDestinationAndExposesPausedOwnership() throws Exception {
        EfficientWalker walker = mock(EfficientWalker.class, CALLS_REAL_METHODS);
        initializeOwnership(walker);
        set(walker,"spellbookSupport",mock(WalkerSpellbookSupport.class));
        WildernessTravelResume resume = new WildernessTravelResume();
        Field field = WalkerTestReflection.declaredField(EfficientWalker.class, "wildernessTravelResume");
        field.setAccessible(true); field.set(walker, resume);
        set(walker,"motherlodeEntranceRockfalls",mock(MotherlodeEntranceRockfalls.class));
        set(walker,"apeAtollGreegree",new ApeAtollGreegree());
        Class<?> coordinatorType = Class.forName(EfficientWalker.class.getName() + "$BankTeleportBootstrap");
        var coordinator = coordinatorType.getDeclaredConstructor(EfficientWalker.class);
        coordinator.setAccessible(true);
        set(walker,"bankTeleportBootstrap",coordinator.newInstance(walker));
        resume.pause(target, 1000);
        assertTrue(walker.isWildernessTravelPaused());
        assertEquals(target, walker.getDestination());
        assertEquals(EfficientWalker.Status.WALKING, walker.getStatus());
        assertEquals("PAUSED_SAFETY_LOGOUT", walker.getNavigationPhase());
    }

    @Test public void explicitCancelDiscardsIntentBeforeQueuedClientCancellation() throws Exception {
        EfficientWalker walker = mock(EfficientWalker.class, CALLS_REAL_METHODS);
        initializeOwnership(walker);
        set(walker,"spellbookSupport",mock(WalkerSpellbookSupport.class));
        WildernessTravelResume resume = new WildernessTravelResume();
        WalkerRequestCancellation requests = new WalkerRequestCancellation();
        set(walker,"wildernessTravelResume",resume);
        set(walker,"requestCancellation",requests);
        set(walker,"client",mock(Client.class));
        long pending = requests.capture();
        resume.pause(target,1000);
        try (var dro = mockStatic(Microbot.class)) {
            dro.when(Microbot::getClientThread).thenReturn(mock(ClientThread.class));
            walker.cancel();
            assertNull(resume.target());
            assertCancelled(() -> requests.check(pending));
        }
    }

    @Test public void facadeRebuildsOnceAfterReadyLoginAndClearThreat() throws Exception {
        EfficientWalker walker = mock(EfficientWalker.class, CALLS_REAL_METHODS);
        initializeOwnership(walker);
        set(walker,"spellbookSupport",mock(WalkerSpellbookSupport.class));
        Client client = mock(Client.class);
        WildernessTravelResume resume = new WildernessTravelResume();
        set(walker,"wildernessTravelResume",resume); set(walker,"client",client);
        doReturn(true).when(walker).walkTo(target);
        resume.pause(target,1000); resume.observe(GameState.LOGIN_SCREEN);
        when(client.getGameState()).thenReturn(GameState.LOADING);
        walker.resumeWildernessTravel(false,2000);
        verify(walker,never()).walkTo(target);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getLocalPlayer()).thenReturn(mock(Player.class));
        walker.resumeWildernessTravel(true,2000);
        verify(walker,never()).walkTo(target);
        walker.resumeWildernessTravel(false,2000);
        walker.resumeWildernessTravel(false,2600);
        verify(walker,times(1)).walkTo(target);
        assertNull(resume.target());
    }

    @Test public void explicitCancelDuringSafetySnapshotCannotResurrectJourney() throws Exception {
        EfficientWalker walker = mock(EfficientWalker.class, CALLS_REAL_METHODS);
        initializeOwnership(walker);
        set(walker,"spellbookSupport",mock(WalkerSpellbookSupport.class));
        WildernessTravelResume resume = new WildernessTravelResume();
        set(walker,"wildernessTravelResume",resume);
        set(walker,"requestCancellation",new WalkerRequestCancellation());
        set(walker,"client",mock(Client.class));
        doReturn(EfficientWalker.Status.WALKING).when(walker).getStatus();
        doAnswer(invocation -> { walker.cancel(); return target; }).when(walker).getLogicalDestination();
        try (var dro = mockStatic(Microbot.class)) {
            dro.when(Microbot::getClientThread).thenReturn(mock(ClientThread.class));
            walker.pauseForWildernessLogout(1000);
            assertNull(resume.target());
            assertFalse(resume.canResume(true,false,20000));
        }
    }

    private static void initializeOwnership(EfficientWalker walker) throws Exception {
        set(walker,"requestOwnership",new WalkerRequestOwnership());
        set(walker,"routeSubmission",TestRouteSubmissions.immediate());
        Client client = mock(Client.class);
        when(client.isClientThread()).thenReturn(true);
        set(walker,"client",client);
        set(walker,"engine",new StatusEngine());
        set(walker,"engineClass",StatusEngine.class);
    }

    public static final class StatusEngine {
        public EfficientWalker.Status getStatus() { return EfficientWalker.Status.WALKING; }
    }

    private static void set(Object object, String name, Object value) throws Exception {
        Field field = WalkerTestReflection.declaredField(object.getClass(), name); field.setAccessible(true); field.set(object,value);
    }
}
