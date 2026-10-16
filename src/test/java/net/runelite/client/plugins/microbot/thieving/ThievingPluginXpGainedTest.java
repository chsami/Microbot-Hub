package net.runelite.client.plugins.microbot.thieving;

import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.Rs2PlayerModel;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class ThievingPluginXpGainedTest {

    @Test
    void xpGainedDoesNotThrowWhenLocalPlayerIsMissing() {
        Client client = mock(Client.class);
        when(client.getSkillExperience(Skill.THIEVING)).thenReturn(1000);
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class)) {
            microbot.when(Microbot::getClient).thenReturn(client);
            player.when(Rs2Player::getLocalPlayer).thenReturn(null);

            ThievingPlugin plugin = new ThievingPlugin();
            assertDoesNotThrow(plugin::xpGained);
        }
    }

    @Test
    void xpGainedKeepsStartXpAcrossMissingLocalPlayer() {
        Client client = mock(Client.class);
        Rs2PlayerModel localPlayer = mock(Rs2PlayerModel.class);
        when(localPlayer.getName()).thenReturn("Thief");
        when(client.getSkillExperience(Skill.THIEVING)).thenReturn(1000, 1000, 1200, 1500);
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class)) {
            microbot.when(Microbot::getClient).thenReturn(client);
            player.when(Rs2Player::getLocalPlayer).thenReturn(localPlayer, null, localPlayer);

            ThievingPlugin plugin = new ThievingPlugin();
            assertEquals(0, plugin.xpGained());
            assertEquals(200, plugin.xpGained());
            assertEquals(500, plugin.xpGained());
        }
    }
}
