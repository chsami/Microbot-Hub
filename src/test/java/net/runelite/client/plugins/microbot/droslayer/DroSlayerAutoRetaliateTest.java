package net.runelite.client.plugins.microbot.droslayer;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DroSlayerAutoRetaliateTest {
    @Test void generalCombatAndAssemblyRequestsDoNotToggleEachOtherBackOn() throws Exception {
        var script=new DroSlayerScript();
        var field=DroSlayerScript.class.getDeclaredField("isUsingCannon");field.setAccessible(true);field.setBoolean(script,true);
        var method=DroSlayerScript.class.getDeclaredMethod("setAutoRetaliateHumanized",boolean.class);method.setAccessible(true);
        var client=mock(Client.class);var thread=mock(ClientThread.class);
        AtomicInteger setting=new AtomicInteger(0),stage=new AtomicInteger();
        when(thread.runOnClientThreadOptional(any())).thenAnswer(call->Optional.ofNullable(((Callable<?>)call.getArgument(0)).call()));
        when(client.getVarpValue(VarPlayerID.OPTION_NODEF)).thenAnswer(call->setting.get());
        try(var micro=mockStatic(Microbot.class);var runtime=mockStatic(DroSlayerRuntime.class);var combat=mockStatic(Rs2Combat.class)) {
            micro.when(Microbot::getClient).thenReturn(client);micro.when(Microbot::getClientThread).thenReturn(thread);
            micro.when(()->Microbot.getVarbitPlayerValue(VarPlayerID.DROPCANNON)).thenAnswer(call->stage.get());
            runtime.when(()->DroSlayerRuntime.performAction(any(),eq(false),any(BooleanSupplier.class)))
                .thenAnswer(call->((BooleanSupplier)call.getArgument(2)).getAsBoolean());
            combat.when(()->Rs2Combat.setAutoRetaliate(anyBoolean())).thenAnswer(call->{setting.set((boolean)call.getArgument(0)?0:1);return true;});
            for(int s:new int[]{0,0,1,2,3}) {
                stage.set(s);
                assertEquals(true,method.invoke(script,true),"Ordinary combat requests obey the assembly owner");
                assertEquals(true,method.invoke(script,false));
                assertEquals(1,setting.get());
            }
            combat.verify(()->Rs2Combat.setAutoRetaliate(false),times(1));
            combat.verify(()->Rs2Combat.setAutoRetaliate(true),never());
            stage.set(4);
            assertEquals(true,method.invoke(script,true));assertEquals(true,method.invoke(script,true));
            assertEquals(0,setting.get());combat.verify(()->Rs2Combat.setAutoRetaliate(true),times(1));
        }
    }
    @Test void pickupAndExplicitOffRemainOffAndNonCannonCombatIsUnchanged() {
        assertFalse(DroSlayerScript.autoRetaliateTarget(true,true,4,true));
        assertFalse(DroSlayerScript.autoRetaliateTarget(false,true,4,false));
        assertTrue(DroSlayerScript.autoRetaliateTarget(true,true,4,false));
        assertTrue(DroSlayerScript.autoRetaliateTarget(true,false,0,false));
    }
    @Test void anUnavailableSettingSnapshotCannotAuthorizeAToggle() throws Exception {
        var method=DroSlayerScript.class.getDeclaredMethod("setAutoRetaliateHumanized",boolean.class);method.setAccessible(true);
        var thread=mock(ClientThread.class);when(thread.runOnClientThreadOptional(any())).thenReturn(Optional.empty());
        try(var micro=mockStatic(Microbot.class);var runtime=mockStatic(DroSlayerRuntime.class);var combat=mockStatic(Rs2Combat.class)) {
            micro.when(Microbot::getClientThread).thenReturn(thread);
            assertEquals(false,method.invoke(new DroSlayerScript(),false));
            runtime.verifyNoInteractions();combat.verifyNoInteractions();
        }
    }
}
