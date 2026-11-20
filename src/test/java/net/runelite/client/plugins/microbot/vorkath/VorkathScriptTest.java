package net.runelite.client.plugins.microbot.vorkath;

import net.runelite.api.NPC;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VorkathScriptTest {

    @Test
    void castTargetIsTheLegacyNpcModelThatRs2MagicCastOnClicks() {
        NPC npc = mock(NPC.class);
        Rs2NpcModel spawn = mock(Rs2NpcModel.class);
        when(spawn.getNpc()).thenReturn(npc);

        Object target = VorkathScript.castTarget(spawn);

        assertNotNull(target);
        assertTrue(target instanceof net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel);
        assertSame(npc, ((net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel) target).getRuneliteNpc());
    }

    @Test
    void castTargetIsNullWithoutAnNpc() {
        assertNull(VorkathScript.castTarget(null));
        assertNull(VorkathScript.castTarget(mock(Rs2NpcModel.class)));
    }
}
