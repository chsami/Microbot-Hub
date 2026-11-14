package net.runelite.client.plugins.microbot.droslayer;

import net.runelite.client.plugins.microbot.droslayer.profile.BaseProfileDro;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.skills.slayer.Rs2Slayer;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class DroSlayerSkipDialogueTest {
    @Test public void listedAssignmentSkipsInsteadOfAccepting() throws Exception { verifyChoice(true,true,270,true); }
    @Test public void deferredMouseActionDoesNotFallThroughToAccept() throws Exception { verifyChoice(true,false,270,true); }
    @Test public void ordinaryAssignmentIsAccepted() throws Exception { verifyChoice(false,true,270,false); }
    @Test public void pointReserveIsRespected() throws Exception { verifyChoice(true,true,90,false); }

    private void verifyChoice(boolean listed,boolean clickAllowed,int points,boolean expectSkip) throws Exception {
        DroSlayerScript script = new DroSlayerScript();
        DroSlayerConfig config = mock(DroSlayerConfig.class);
        when(config.enableAutoSkip()).thenReturn(true);
        when(config.skipTaskList()).thenReturn(listed ? "Black Knights" : "Ice warriors");
        when(config.minPointsToSkip()).thenReturn(100);
        Field field = DroSlayerScript.class.getDeclaredField("config"); field.setAccessible(true); field.set(script,config);
        Field pointsField = DroSlayerPlugin.class.getDeclaredField("slayerPoints"); pointsField.setAccessible(true);
        int previousPoints = pointsField.getInt(null);
        DroSlayerState previousState = DroSlayerPlugin.getState();
        pointsField.setInt(null,points);
        DroSlayerPlugin.setState(DroSlayerState.GETTING_TASK);
        try (MockedStatic<Rs2Dialogue> dialogue = mockStatic(Rs2Dialogue.class);
             MockedStatic<Rs2Slayer> slayer = mockStatic(Rs2Slayer.class);
             MockedStatic<DroSlayerRuntime> runtime = mockStatic(DroSlayerRuntime.class)) {
            slayer.when(Rs2Slayer::getSlayerTask).thenReturn("Black Knights");
            dialogue.when(() -> Rs2Dialogue.hasDialogueOption("skip for now")).thenReturn(true);
            dialogue.when(() -> Rs2Dialogue.hasDialogueOption("Okay")).thenReturn(true);
            dialogue.when(() -> Rs2Dialogue.clickOption("skip for now",false)).thenReturn(true);
            runtime.when(() -> DroSlayerRuntime.performAction(eq(BaseProfileDro.ActionPhase.SETUP),eq(false),any(BooleanSupplier.class)))
                    .thenAnswer(call -> clickAllowed && ((BooleanSupplier) call.getArgument(2)).getAsBoolean());
            Method method = DroSlayerScript.class.getDeclaredMethod("handleSlayerDialogue"); method.setAccessible(true); method.invoke(script);
            if (expectSkip) {
                dialogue.verify(() -> Rs2Dialogue.hasDialogueOption("Okay"),never());
                assertEquals(clickAllowed ? DroSlayerState.SKIPPING_TASK : DroSlayerState.GETTING_TASK,DroSlayerPlugin.getState());
                dialogue.verify(() -> Rs2Dialogue.clickOption("skip for now",false),times(clickAllowed ? 1 : 0));
            } else {
                dialogue.verify(() -> Rs2Dialogue.hasDialogueOption("Okay"));
                dialogue.verify(() -> Rs2Dialogue.clickOption("skip for now",false),never());
            }
        } finally {
            pointsField.setInt(null,previousPoints);
            DroSlayerPlugin.setState(previousState);
        }
    }
}
