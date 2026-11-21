package net.runelite.client.plugins.microbot.drorecorder;

import java.nio.file.Path;
import java.util.Map;
import net.runelite.api.Actor;
import net.runelite.api.Hitsplat;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.input.MouseManager;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DroRecorderCaptureTest {
    @Test void sharedCaptureRegistersOnceCapturesDamageAndDiagnosticsAndDetaches(){
        var session=mock(DroRecorderSession.class);var bus=new EventBus();var mouse=mock(MouseManager.class);var before=mock(Runnable.class);
        var capture=new DroRecorderCapture(session,bus,mouse,before);capture.start();
        var damage=mock(HitsplatApplied.class);var actor=mock(Actor.class);var hit=mock(Hitsplat.class);
        when(damage.getActor()).thenReturn(actor);when(damage.getHitsplat()).thenReturn(hit);
        bus.post(damage);bus.post(new GameTick());var fields=Map.<String,Object>of("stage","CONFIRMED","id",42L);
        bus.post(new DroRecorderDiagnostic(fields));
        verify(session).recordHitsplat(actor,hit);verify(session).recordGameTick();verify(session).recordDiagnostic(fields);verify(before).run();
        capture.close();bus.post(damage);bus.post(new GameTick());bus.post(new DroRecorderDiagnostic(fields));
        verifyNoMoreInteractions(session);verify(mouse).registerMouseListener(any());verify(mouse).unregisterMouseListener(any());
        verify(session,never()).stop(); // The owner decides when to finalize its own session.
    }
    @Test void namedArenaAndFirecapeDiagnosticsKeepTheirType(){
        assertEquals("firecape_hit",DroRecorderSession.diagnosticType(Map.of("record","firecape_hit","attacker",5)));
        assertEquals("firecape_attack",DroRecorderSession.diagnosticType(Map.of("record","firecape_attack")));
        assertEquals("arena_safety_net",DroRecorderSession.diagnosticType(Map.of("record","arena_safety_net")));
        assertEquals("arena_input_command",DroRecorderSession.diagnosticType(Map.of("kind","arena_input")));
        assertEquals("arena_prayer_command",DroRecorderSession.diagnosticType(Map.of("op","prayer.set")));
        assertEquals("arena_prayer_command",DroRecorderSession.diagnosticType(Map.of("record","other_type")),"Unknown names cannot mint new record types");
    }
    @Test void automaticDirectoriesAreNamedByActivityAndCannotEscapeRoot(){
        var root=Path.of("/test/recordings");
        for(String activity:new String[]{"inferno","colosseum","firecape"}){
            var path=DroAutomaticRecording.directory(root,activity,"20261019-120000-000","abc12345");
            assertEquals(root,path.getParent());assertTrue(path.getFileName().toString().startsWith(activity+"-"));
        }
        assertThrows(IllegalArgumentException.class,()->DroAutomaticRecording.directory(root,"../escape","stamp","id"));
    }
}
