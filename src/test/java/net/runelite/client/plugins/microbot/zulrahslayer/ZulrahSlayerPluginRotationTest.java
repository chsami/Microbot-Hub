package net.runelite.client.plugins.microbot.zulrahslayer;

import net.runelite.api.NPC;
import net.runelite.api.events.AnimationChanged;
import net.runelite.client.plugins.microbot.zulrahslayer.constants.ZulrahType;
import net.runelite.client.plugins.microbot.zulrahslayer.rotationutils.RotationType;
import net.runelite.client.plugins.microbot.zulrahslayer.rotationutils.ZulrahPhase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ZulrahSlayerPluginRotationTest {

    private ZulrahSlayerPlugin plugin;
    private ZulrahScript script;
    private Method onAnimationChanged;

    @BeforeEach
    void setUp() throws Exception {
        plugin = new ZulrahSlayerPlugin();
        script = mock(ZulrahScript.class);
        Field field = ZulrahSlayerPlugin.class.getDeclaredField("zulrahScript");
        field.setAccessible(true);
        field.set(plugin, script);
        onAnimationChanged = ZulrahSlayerPlugin.class.getDeclaredMethod("onAnimationChanged", AnimationChanged.class);
        onAnimationChanged.setAccessible(true);
    }

    private void animate(ZulrahType form, int animation) throws Exception {
        NPC npc = mock(NPC.class);
        when(npc.getName()).thenReturn("Zulrah");
        when(npc.getId()).thenReturn(form.getNpcId());
        when(npc.getAnimation()).thenReturn(animation);
        AnimationChanged event = new AnimationChanged();
        event.setActor(npc);
        onAnimationChanged.invoke(plugin, event);
    }

    private void playFullRotation(RotationType rotation) throws Exception {
        List<ZulrahPhase> phases = rotation.getZulrahPhases();
        animate(phases.get(0).getZulrahNpc().getType(), ZulrahSlayerPlugin.START_ANIMATION);
        for (int i = 1; i < phases.size(); i++) {
            ZulrahType previous = phases.get(i - 1).getZulrahNpc().getType();
            animate(previous, ZulrahSlayerPlugin.GOING_UNDER_WATER);
            animate(phases.get(i).getZulrahNpc().getType(), ZulrahSlayerPlugin.RESURFACE_ANIMATION);
        }
        animate(phases.get(phases.size() - 1).getZulrahNpc().getType(), ZulrahSlayerPlugin.GOING_UNDER_WATER);
    }

    private ZulrahPhase lastPushedPhase() {
        ArgumentCaptor<ZulrahPhase> captor = ArgumentCaptor.forClass(ZulrahPhase.class);
        verify(script, atLeastOnce()).setZulrahPhase(captor.capture());
        List<ZulrahPhase> pushed = captor.getAllValues();
        return pushed.get(pushed.size() - 1);
    }

    @Test
    void fullRotationReachesItsLastPhase() throws Exception {
        playFullRotation(RotationType.ROT_B);

        List<ZulrahPhase> phases = RotationType.ROT_B.getZulrahPhases();
        assertSame(phases.get(phases.size() - 1), lastPushedPhase());
    }

    @Test
    void nextCycleAfterRotationEndResyncsFromItsSecondPhase() throws Exception {
        playFullRotation(RotationType.ROT_B);

        animate(ZulrahType.MAGIC, ZulrahSlayerPlugin.RESURFACE_ANIMATION);

        assertSame(RotationType.ROT_D.getZulrahPhases().get(1), lastPushedPhase());
        assertEquals(1, plugin.getStage());
    }

    @Test
    void nextCycleMeleeOpenerDrivesAMeleePhaseInsteadOfNone() throws Exception {
        playFullRotation(RotationType.ROT_A);

        animate(ZulrahType.MELEE, ZulrahSlayerPlugin.RESURFACE_ANIMATION);

        ZulrahPhase pushed = lastPushedPhase();
        assertEquals(ZulrahType.MELEE, pushed.getZulrahNpc().getType());
        assertSame(RotationType.ROT_A.getZulrahPhases().get(1), pushed);
    }

    @Test
    void spawnAnimationAtTheStartOfANewCycleAlsoResyncsFromTheSecondPhase() throws Exception {
        playFullRotation(RotationType.ROT_C);

        animate(ZulrahType.MAGIC, ZulrahSlayerPlugin.START_ANIMATION);

        assertSame(RotationType.ROT_D.getZulrahPhases().get(1), lastPushedPhase());
    }
}
