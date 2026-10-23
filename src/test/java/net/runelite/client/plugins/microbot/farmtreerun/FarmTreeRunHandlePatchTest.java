package net.runelite.client.plugins.microbot.farmtreerun;

import net.runelite.api.GameObject;
import net.runelite.api.ObjectComposition;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class FarmTreeRunHandlePatchTest {

    private static FarmTreeRunScript runningScript(FarmTreeRunConfig config) throws Exception {
        FarmTreeRunScript script = new FarmTreeRunScript(mock(FarmTreeRunPlugin.class), config);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        when(future.isDone()).thenReturn(false);
        Field field = Script.class.getDeclaredField("mainScheduledFuture");
        field.setAccessible(true);
        field.set(script, future);
        return script;
    }

    @Test
    void nullTreeCompositionSkipsPatchInsteadOfThrowing() throws Exception {
        FarmTreeRunConfig config = mock(FarmTreeRunConfig.class);
        FarmTreeRunScript script = runningScript(config);
        GameObject treePatch = mock(GameObject.class);
        when(treePatch.getId()).thenReturn(8392);
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2GameObject> gameObject = mockStatic(Rs2GameObject.class)) {
            microbot.when(Microbot::isLoggedIn).thenReturn(true);
            gameObject.when(() -> Rs2GameObject.findObjectByImposter(anyInt(), anyString(), eq(false))).thenReturn(treePatch);
            gameObject.when(() -> Rs2GameObject.findObjectComposition(anyInt())).thenReturn(null);

            Method handlePatch = FarmTreeRunScript.class.getDeclaredMethod("handlePatch", FarmTreeRunConfig.class, FarmTreeRunScript.Patch.class);
            handlePatch.setAccessible(true);
            Object result = handlePatch.invoke(script, config, FarmTreeRunScript.Patch.GNOME_STRONGHOLD_TREE_PATCH);

            assertEquals(Boolean.FALSE, result);
        }
    }

    @Test
    void nullTreeActionsSkipsPatchInsteadOfThrowing() throws Exception {
        FarmTreeRunConfig config = mock(FarmTreeRunConfig.class);
        FarmTreeRunScript script = runningScript(config);
        GameObject treePatch = mock(GameObject.class);
        when(treePatch.getId()).thenReturn(8392);
        ObjectComposition composition = mock(ObjectComposition.class);
        when(composition.getActions()).thenReturn(null);
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2GameObject> gameObject = mockStatic(Rs2GameObject.class)) {
            microbot.when(Microbot::isLoggedIn).thenReturn(true);
            gameObject.when(() -> Rs2GameObject.findObjectByImposter(anyInt(), anyString(), eq(false))).thenReturn(treePatch);
            gameObject.when(() -> Rs2GameObject.findObjectComposition(anyInt())).thenReturn(composition);

            Method handlePatch = FarmTreeRunScript.class.getDeclaredMethod("handlePatch", FarmTreeRunConfig.class, FarmTreeRunScript.Patch.class);
            handlePatch.setAccessible(true);
            Object result = handlePatch.invoke(script, config, FarmTreeRunScript.Patch.GNOME_STRONGHOLD_TREE_PATCH);

            assertEquals(Boolean.FALSE, result);
        }
    }

    @Test
    void disabledProtectionSkipsPaymentWithoutReadingComposition() throws Exception {
        FarmTreeRunConfig config = mock(FarmTreeRunConfig.class);
        when(config.protectTrees()).thenReturn(false);
        FarmTreeRunScript script = runningScript(config);
        try (MockedStatic<Rs2GameObject> gameObject = mockStatic(Rs2GameObject.class);
             MockedStatic<Rs2Npc> npc = mockStatic(Rs2Npc.class)) {
            gameObject.when(() -> Rs2GameObject.getObjectComposition(anyInt())).thenReturn(null);

            Class<?> paymentKind = Class.forName(FarmTreeRunScript.class.getName() + "$PaymentKind");
            Object protect = java.util.Arrays.stream(paymentKind.getEnumConstants()).filter(k -> k.toString().equals("PROTECT")).findFirst().orElseThrow();
            Method handlePayment = FarmTreeRunScript.class.getDeclaredMethod("handlePayment", FarmTreeRunConfig.class, FarmTreeRunScript.Patch.class, paymentKind);
            handlePayment.setAccessible(true);
            Object result = handlePayment.invoke(script, config, FarmTreeRunScript.Patch.GNOME_STRONGHOLD_TREE_PATCH, protect);

            assertEquals(Boolean.TRUE, result);
            gameObject.verify(() -> Rs2GameObject.getObjectComposition(anyInt()), never());
            npc.verify(() -> Rs2Npc.getNearestNpcWithAction(anyString()), never());
        }
    }
}
