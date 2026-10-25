package net.runelite.client.plugins.microbot.farmtreerun;

import net.runelite.api.ObjectComposition;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectQueryable;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
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

    private static void stubTreePatchLookup(MockedStatic<Microbot> microbot, ObjectComposition composition) {
        Rs2TileObjectModel treePatch = mock(Rs2TileObjectModel.class);
        when(treePatch.getId()).thenReturn(8392);
        Rs2TileObjectQueryable query = mock(Rs2TileObjectQueryable.class, RETURNS_SELF);
        when(query.nearest()).thenReturn(treePatch);
        Rs2TileObjectCache cache = mock(Rs2TileObjectCache.class);
        when(cache.query()).thenReturn(query);
        ClientThread clientThread = mock(ClientThread.class);
        when(clientThread.runOnClientThreadOptional(any())).thenReturn(Optional.ofNullable(composition));
        microbot.when(Microbot::getRs2TileObjectCache).thenReturn(cache);
        microbot.when(Microbot::getClientThread).thenReturn(clientThread);
    }

    @Test
    void nullTreeCompositionSkipsPatchInsteadOfThrowing() throws Exception {
        FarmTreeRunConfig config = mock(FarmTreeRunConfig.class);
        FarmTreeRunScript script = runningScript(config);
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
            microbot.when(Microbot::isLoggedIn).thenReturn(true);
            stubTreePatchLookup(microbot, null);

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
        ObjectComposition composition = mock(ObjectComposition.class);
        when(composition.getActions()).thenReturn(null);
        when(composition.getImpostorIds()).thenReturn(null);
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
            microbot.when(Microbot::isLoggedIn).thenReturn(true);
            stubTreePatchLookup(microbot, composition);

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
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Npc> npc = mockStatic(Rs2Npc.class)) {

            Class<?> paymentKind = Class.forName(FarmTreeRunScript.class.getName() + "$PaymentKind");
            Object protect = java.util.Arrays.stream(paymentKind.getEnumConstants()).filter(k -> k.toString().equals("PROTECT")).findFirst().orElseThrow();
            Method handlePayment = FarmTreeRunScript.class.getDeclaredMethod("handlePayment", FarmTreeRunConfig.class, FarmTreeRunScript.Patch.class, paymentKind);
            handlePayment.setAccessible(true);
            Object result = handlePayment.invoke(script, config, FarmTreeRunScript.Patch.GNOME_STRONGHOLD_TREE_PATCH, protect);

            assertEquals(Boolean.TRUE, result);
            microbot.verify(Microbot::getClientThread, never());
            npc.verify(() -> Rs2Npc.getNearestNpcWithAction(anyString()), never());
        }
    }
}
