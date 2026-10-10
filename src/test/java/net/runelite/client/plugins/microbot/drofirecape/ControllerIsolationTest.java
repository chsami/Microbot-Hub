package net.runelite.client.plugins.microbot.drofirecape;

import java.lang.reflect.Field;
import java.nio.file.*;
import java.security.MessageDigest;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ControllerIsolationTest {
    @Test public void mainScriptRemainsByteIdenticalToWorking0331() throws Exception {
        String relative="src/main/java/net/runelite/client/plugins/microbot/drofirecape/DroFirecapeScript.java";
        Path source=Paths.get(relative);
        if(!Files.exists(source))source=Paths.get("runelite-client").resolve(relative);
        org.junit.Assume.assumeTrue("Source-only baseline check",Files.exists(source));
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source));
        StringBuilder actual=new StringBuilder();
        for(byte b:digest)actual.append(String.format("%02x",b&255));
        assertEquals("bf65abef8824ebf042166638a0c8976c21273d8bed3d327812311e92ff6f7d60",actual.toString());
    }
    private void inject(FcControllers owner,String name,Object value) throws Exception {
        Field f=FcControllers.class.getDeclaredField(name);f.setAccessible(true);f.set(owner,value);
    }
    @Test public void onlySelectedControllerReceivesLifecycleAndEvents() throws Exception {
        for(boolean pure:new boolean[]{false,true})for(boolean beta:new boolean[]{false,true}) {
            DroFirecapeConfig config=mock(DroFirecapeConfig.class);
            when(config.pureMode()).thenReturn(pure);when(config.nativeTickPrayers()).thenReturn(beta);
            DroFirecapeScript regular=mock(DroFirecapeScript.class);
            net.runelite.client.plugins.microbot.drofirecape.optional.DroFirecapeScript optional=
                mock(net.runelite.client.plugins.microbot.drofirecape.optional.DroFirecapeScript.class);
            FcControllers owner=new FcControllers();inject(owner,"regular",regular);inject(owner,"optional",optional);
            owner.run(config);
            // Settings changes cannot silently start a second writer during a run.
            when(config.pureMode()).thenReturn(!pure);when(config.nativeTickPrayers()).thenReturn(!beta);
            owner.onGameTick();owner.onClientTick();owner.shutdown();
            if(pure||beta) {
                verify(optional).run(config);verify(optional).onGameTick();verify(optional).onClientTick();verify(optional).shutdown();
                verifyNoInteractions(regular);
            } else {
                verify(regular).run(config);verify(regular).onGameTick();verify(regular).onClientTick();verify(regular).shutdown();
                verifyNoInteractions(optional);
            }
        }
    }
}
