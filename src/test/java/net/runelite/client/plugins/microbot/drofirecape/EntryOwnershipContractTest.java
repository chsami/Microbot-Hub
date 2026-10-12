/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.drofirecape;
import java.lang.reflect.Field;
import net.runelite.client.plugins.microbot.drofirecape.core.SupplyAck;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class EntryOwnershipContractTest {
    static class Config implements DroFirecapeConfig {int rotation;@Override public int entryRotation(){return rotation;}}
    static class Actions extends FcActions {
        final Object source=new Object();int second=22,entries,idles;
        FcPredictorGate.Sample sample(int s){return new FcPredictorGate.Sample(source,630,2,6,100,s,System.currentTimeMillis(),false,true,false,"fake predictor");}
        @Override FcPredictorGate.Sample predictor(boolean inside){return sample(second);}
        @Override boolean zoomConfirmed(){return true;}
        @Override boolean entryProtectionReady(){return true;}
        @Override boolean entryProtectionReady(boolean conservation){return true;}
        @Override boolean enterPredictedRotation(FcPredictorGate gate,int rotation){entries++;return true;}
        @Override boolean enterPredictedRotation(FcPredictorGate gate,int rotation,boolean conservation){entries++;return true;}
        @Override void idlePrayersOff(){idles++;}
    }
    static Object get(Object o,String field)throws Exception {
        Field f=DroFirecapeScript.class.getDeclaredField(field);f.setAccessible(true);return f.get(o);
    }
    static class Regular extends DroFirecapeScript { @Override protected void clearSaved(){} }
    static class Fixture {
        final DroFirecapeScript script;final Actions actions=new Actions();final Config config=new Config();final SupplyAck ack;final FcPredictorGate gate;
        Fixture(boolean pure,boolean calibrated)throws Exception {
            script=pure?new OptionalFirecapeScript():new Regular();FirecapeTestClient.set(script,"actions",actions);FirecapeTestClient.set(script,"config",config);
            FirecapeTestClient.set(script,"state",DroFirecapeScript.State.ROTATION_WAIT);
            java.lang.reflect.Constructor<?> ctor=net.runelite.client.config.ConfigManager.class.getDeclaredConstructors()[0];ctor.setAccessible(true);
            Object[] args=new Object[ctor.getParameterCount()];for(int i=0;i<args.length;i++)if(ctor.getParameterTypes()[i]==java.util.concurrent.ScheduledExecutorService.class)args[i]=FirecapeTestClient.proxy(java.util.concurrent.ScheduledExecutorService.class,(m,a)->null);
            FirecapeTestClient.set(script,"configManager",ctor.newInstance(args));
            ack=(SupplyAck)get(script,"supplyAck");gate=(FcPredictorGate)get(script,"entryGate");
            if(calibrated){gate.entryReady(actions.sample(20),System.currentTimeMillis(),0);gate.entryReady(actions.sample(21),System.currentTimeMillis(),0);}
        }
        void step(int tick,int item)throws Exception {script.rotationWait(FirecapeTestClient.frame(tick,item));}
    }
    @Test void startupObservedRestoreClearsDebtAndAllowsDefaultAnyEntry()throws Exception {
        for(boolean pure:new boolean[]{false,true}) {
            Fixture f=new Fixture(pure,true);f.script.supplyRequested(FirecapeTestClient.frame(100,3024),new FcFrame.ItemSlot(0,3024,1,"Super restore(4)",java.util.List.of("Drink")));FirecapeTestClient.set(f.script,"brewDebt",3);
            f.step(101,3026);assertFalse(f.ack.pending());assertEquals(0,get(f.script,"brewDebt"));assertEquals(1,f.actions.entries);assertEquals(0,f.actions.idles);
            assertEquals(DroFirecapeScript.State.ENTERING,f.script.state());
        }
    }
    @Test void unconsumedStartupDoseTimesOutBeforeEntryWithoutPretendingConsumption()throws Exception {
        for(boolean pure:new boolean[]{false,true}) {
            Fixture f=new Fixture(pure,true);if(pure)f.ack.sent(3024,4,100,SupplyAck.Kind.RESTORE,System.currentTimeMillis());else f.ack.sent(3024,1,100,SupplyAck.Kind.RESTORE);f.step(101,3024);assertTrue(f.ack.pending());assertEquals(0,f.actions.entries);
            if(pure)f.ack.sent(3024,4,100,SupplyAck.Kind.RESTORE,System.currentTimeMillis()-3001);f.actions.second=23;f.step(105,3024);assertFalse(f.ack.pending());assertEquals(1,f.actions.entries);
        }
    }
    @Test void chosenRotationAndUncalibratedPredictorCannotBeBypassedByConsumption()throws Exception {
        for(boolean pure:new boolean[]{false,true}) {
            Fixture selected=new Fixture(pure,true);selected.config.rotation=5;selected.step(101,3024);assertEquals(0,selected.actions.entries);
            selected.config.rotation=0;selected.actions.second=23;selected.step(102,3024);assertEquals(1,selected.actions.entries);
            Fixture uncalibrated=new Fixture(pure,false);uncalibrated.script.supplyRequested(FirecapeTestClient.frame(100,3024),new FcFrame.ItemSlot(0,3024,1,"Super restore(4)",java.util.List.of("Drink")));uncalibrated.step(101,3026);
            assertFalse(uncalibrated.ack.pending());assertEquals(0,uncalibrated.actions.entries);
        }
    }
}
