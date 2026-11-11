package net.runelite.client.plugins.microbot.pitfallhunter;

import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class PitfallHunterScriptStopTest {

    private PitfallHunterScript script;

    @BeforeEach
    void setUp() {
        script = new PitfallHunterScript();
    }

    @AfterEach
    void tearDown() throws Exception {
        ((ScheduledExecutorService) read(script, Script.class, "scheduledExecutorService")).shutdownNow();
    }

    @Test
    void stoppingFromAnotherThreadKeepsTheSelectionTheInFlightTickIsUsing() throws Exception {
        PitfallHunterScript.PitfallDefinition pit = PitfallHunterScript.PITFALL_ORDER.get(0);
        Rs2NpcModel npc = mock(Rs2NpcModel.class);
        write(script, PitfallHunterScript.class, "selectedPit", pit);
        write(script, PitfallHunterScript.class, "selectedNpc", npc);

        Thread stopper = new Thread(script::shutdown);
        stopper.start();
        stopper.join();

        assertSame(pit, read(script, PitfallHunterScript.class, "selectedPit"));
        assertSame(npc, read(script, PitfallHunterScript.class, "selectedNpc"));
        assertEquals(pit.getName(), script.getSelectedPitDisplay());
    }

    private static Object read(Object owner, Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static void write(Object owner, Class<?> type, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(owner, value);
    }
}
