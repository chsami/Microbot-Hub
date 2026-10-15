package net.runelite.client.plugins.microbot.woodcutting;

import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.woodcutting.enums.WoodcuttingPrimaryAction;
import net.runelite.client.plugins.microbot.woodcutting.enums.WoodcuttingScriptState;
import net.runelite.client.plugins.microbot.woodcutting.enums.WoodcuttingSecondaryAction;
import net.runelite.client.plugins.microbot.woodcutting.enums.WoodcuttingWalkBack;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AutoWoodcuttingOverlayTest {

    @Test
    void renderSkipsAreaWhenReturnPointIsUnavailable() {
        Client client = mock(Client.class);
        when(client.findWorldViewFromWorldPoint(isNull())).thenThrow(new NullPointerException());

        AutoWoodcuttingConfig config = mock(AutoWoodcuttingConfig.class);
        when(config.distanceToStray()).thenReturn(10);
        when(config.walkBack()).thenReturn(WoodcuttingWalkBack.INITIAL_LOCATION);
        when(config.primaryAction()).thenReturn(WoodcuttingPrimaryAction.BANK);
        when(config.secondaryAction()).thenReturn(WoodcuttingSecondaryAction.NONE);

        AutoWoodcuttingPlugin plugin = mock(AutoWoodcuttingPlugin.class);
        AutoWoodcuttingScript script = mock(AutoWoodcuttingScript.class);
        script.woodcuttingScriptState = WoodcuttingScriptState.WOODCUTTING;
        plugin.autoWoodcuttingScript = script;

        Graphics2D graphics = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class, CALLS_REAL_METHODS);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class)) {
            microbot.when(Microbot::isLoggedIn).thenReturn(true);
            microbot.when(() -> Microbot.logStackTrace(anyString(), any(Exception.class))).thenAnswer(invocation -> null);
            player.when(Rs2Player::getWorldLocation).thenReturn(null);

            new AutoWoodcuttingOverlay(plugin, config, client).render(graphics);

            microbot.verify(() -> Microbot.logStackTrace(anyString(), any(Exception.class)), never());
            verify(client, never()).findWorldViewFromWorldPoint(any(WorldPoint.class));
        } finally {
            graphics.dispose();
        }
    }
}
