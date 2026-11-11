package net.runelite.client.plugins.microbot.drofirecape;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StartupBankAndTeleportDialogueTest {
    private static ClientThread clientThread() throws Exception {
        ClientThread thread=mock(ClientThread.class);
        when(thread.runOnClientThreadOptional(any())).thenAnswer(call ->
            Optional.ofNullable(((Callable<?>)call.getArgument(0)).call()));
        return thread;
    }
    private static final class Actions extends FcActions {
        @Override protected boolean uiReady(long gap){return !observe;}
    }
    @Test void firstTimeNoticeThenThirdOptionAreObservedAndSentOnce() throws Exception {
        Actions actions=new Actions();ClientThread thread=clientThread();
        Widget yes=mock(Widget.class),no=mock(Widget.class),never=mock(Widget.class);
        when(yes.getText()).thenReturn("Yes.");when(no.getText()).thenReturn("No.");
        when(never.getText()).thenReturn("No, and don't ask again.");
        try(var micro=mockStatic(Microbot.class);var dialogue=mockStatic(Rs2Dialogue.class)) {
            micro.when(Microbot::getClientThread).thenReturn(thread);
            dialogue.when(Rs2Dialogue::hasContinue).thenReturn(true);
            dialogue.when(Rs2Dialogue::getDialogueText).thenReturn("There may be more players participating in the Fight Pits on World 362.");
            assertTrue(actions.minigameDialogueStep().contains("suggested-world"));
            assertNotNull(actions.minigameDialogueStep());
            dialogue.verify(Rs2Dialogue::clickContinue,times(1));
            dialogue.when(Rs2Dialogue::hasContinue).thenReturn(false);
            dialogue.when(Rs2Dialogue::hasSelectAnOption).thenReturn(true);
            dialogue.when(Rs2Dialogue::getDialogueOptions).thenReturn(List.of(yes,no,never));
            dialogue.when(()->Rs2Dialogue.clickOption("No, and don't ask again.",true)).thenReturn(true);
            assertTrue(actions.minigameDialogueStep().contains("No, and don't ask again"));
            assertNotNull(actions.minigameDialogueStep());
            dialogue.verify(()->Rs2Dialogue.clickOption("No, and don't ask again.",true),times(1));
            dialogue.when(Rs2Dialogue::hasSelectAnOption).thenReturn(false);
            assertNull(actions.minigameDialogueStep());
        }
    }
    @Test void rejectedOptionCanRetryButNeverChoosesAPlainNoOrYes() throws Exception {
        Actions actions=new Actions();ClientThread thread=clientThread();Widget never=mock(Widget.class);
        when(never.getText()).thenReturn("No, don't ask again.");
        try(var micro=mockStatic(Microbot.class);var dialogue=mockStatic(Rs2Dialogue.class)) {
            micro.when(Microbot::getClientThread).thenReturn(thread);
            dialogue.when(Rs2Dialogue::hasSelectAnOption).thenReturn(true);
            dialogue.when(Rs2Dialogue::getDialogueOptions).thenReturn(List.of(never));
            dialogue.when(()->Rs2Dialogue.clickOption("No, don't ask again.",true)).thenReturn(false,true);
            actions.minigameDialogueStep();actions.minigameDialogueStep();actions.minigameDialogueStep();
            dialogue.verify(()->Rs2Dialogue.clickOption("No, don't ask again.",true),times(2));
            dialogue.verify(()->Rs2Dialogue.clickOption("No"),never());
            dialogue.verify(()->Rs2Dialogue.keyPressForDialogueOption(3),never());
        }
    }
    @Test void unrelatedDialogueAndObserveOnlyNeverAdvance() throws Exception {
        Actions actions=new Actions();ClientThread thread=clientThread();
        try(var micro=mockStatic(Microbot.class);var dialogue=mockStatic(Rs2Dialogue.class)) {
            micro.when(Microbot::getClientThread).thenReturn(thread);
            dialogue.when(Rs2Dialogue::hasContinue).thenReturn(true);
            dialogue.when(Rs2Dialogue::getDialogueText).thenReturn("Would you like to leave the cave?");
            assertNull(actions.minigameDialogueStep());dialogue.verify(Rs2Dialogue::clickContinue,never());
            dialogue.when(Rs2Dialogue::getDialogueText).thenReturn("There may be more players participating in the Fight Pits on World 362.");
            actions.observeOnly(true);assertNotNull(actions.minigameDialogueStep());
            dialogue.verify(Rs2Dialogue::clickContinue,never());
        }
    }
    @Test void firstTimePromptRunsBeforeLandingTimeoutAndStartsAFreshLandingWindow() throws Exception {
        DroFirecapeScript script=mock(DroFirecapeScript.class,CALLS_REAL_METHODS);
        FcActions actions=mock(FcActions.class);
        var field=DroFirecapeScript.class.getDeclaredField("actions");field.setAccessible(true);field.set(script,actions);
        script.config=mock(DroFirecapeConfig.class);script.state=DroFirecapeScript.State.TELEPORT;
        script.stateAt=System.currentTimeMillis();script.teleportAt=System.currentTimeMillis()-30_000;
        when(actions.minigameDialogueStep()).thenReturn("Continuing Fight Pit suggested-world notice");
        script.outside(mock(FcFrame.class));
        assertEquals("Continuing Fight Pit suggested-world notice",script.status);
        assertTrue(System.currentTimeMillis()-script.teleportAt<1000);
        verify(script,never()).fail(anyString());
        when(actions.minigameDialogueStep()).thenReturn(null);
        script.outside(mock(FcFrame.class));
        assertEquals("Waiting for minigame teleport landing",script.status);
        verify(actions,never()).minigameStep();verify(script,never()).fail(anyString());
    }
    @Test void aStuckPromptCannotExtendTheWholeTravelBudgetIndefinitely() throws Exception {
        DroFirecapeScript script=mock(DroFirecapeScript.class,CALLS_REAL_METHODS);
        FcActions actions=mock(FcActions.class);
        var field=DroFirecapeScript.class.getDeclaredField("actions");field.setAccessible(true);field.set(script,actions);
        script.config=mock(DroFirecapeConfig.class);script.state=DroFirecapeScript.State.TELEPORT;
        script.stateAt=System.currentTimeMillis()-91_000;script.teleportAt=System.currentTimeMillis();
        doNothing().when(script).fail(anyString());
        script.outside(mock(FcFrame.class));
        verify(script).fail("Minigame teleport unavailable from this location/interface");
        verifyNoInteractions(actions);
    }
}
