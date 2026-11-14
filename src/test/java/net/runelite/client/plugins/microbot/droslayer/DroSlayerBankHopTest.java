package net.runelite.client.plugins.microbot.droslayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcCache;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcQueryable;
import net.runelite.client.plugins.microbot.droslayer.profile.BaseProfileDro;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DroSlayerBankHopTest {
    @ParameterizedTest
    @ValueSource(strings = {"hopToNewWorld", "handleTempleDeparture"})
    void openBankClosesBeforeHopRetriesOrRouteChanges(String name) throws Exception {
        var script = new DroSlayerScript();
        Field departure = script.getClass().getDeclaredField("templeDeparture"); departure.setAccessible(true);
        for (Object value : departure.getType().getEnumConstants()) if (value.toString().equals("HOP")) departure.set(script, value);
        var cache = mock(Rs2NpcCache.class); var query = mock(Rs2NpcQueryable.class, RETURNS_SELF);
        when(cache.query()).thenReturn(query);
        Method method = script.getClass().getDeclaredMethod(name); method.setAccessible(true);
        try (var dro = mockStatic(Microbot.class); var bank = mockStatic(Rs2Bank.class);
             var player = mockStatic(Rs2Player.class); var runtime = mockStatic(DroSlayerRuntime.class)) {
            dro.when(Microbot::getRs2NpcCache).thenReturn(cache);
            var thread = mock(net.runelite.client.callback.ClientThread.class);
            when(thread.runOnClientThreadOptional(any())).thenAnswer(call ->
                    java.util.Optional.ofNullable(((java.util.concurrent.Callable<?>) call.getArgument(0)).call()));
            dro.when(Microbot::getClientThread).thenReturn(thread);
            bank.when(Rs2Bank::isOpen).thenReturn(true); bank.when(Rs2Bank::closeBank).thenReturn(true);
            player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(3200, 3576, 0));
            runtime.when(() -> DroSlayerRuntime.performAction(eq(BaseProfileDro.ActionPhase.SETUP), eq(false), any(BooleanSupplier.class)))
                    .thenAnswer(call -> ((BooleanSupplier) call.getArgument(2)).getAsBoolean());
            method.invoke(script); method.invoke(script);
            bank.verify(Rs2Bank::closeBank, times(2));
            dro.verify(() -> Microbot.hopToWorld(anyInt()), never());
            runtime.verify(DroSlayerRuntime::cancelWalk, never());
            for (String counter : new String[]{"taskHopAttemptAt", "templeHopAttemptAt", "templeHopAttempts"}) {
                Field field = script.getClass().getDeclaredField(counter); field.setAccessible(true);
                assertEquals(0L, ((Number) field.get(script)).longValue(), counter);
            }
        }
    }
}
