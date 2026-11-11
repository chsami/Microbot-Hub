package net.runelite.client.plugins.microbot.droefficientwalker;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.runelite.api.*;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class SailingMinimapControlsTest {
    @Test public void activeWorldMapVoyageHasExactlyOneCancelAndNoPauseOrNewDestination() throws Exception {
        Client client = mock(Client.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getMouseCanvasPosition()).thenReturn(new Point(110, 110));
        when(client.getMenuEntries()).thenReturn(new MenuEntry[0]);
        Widget map = mock(Widget.class);
        when(map.getBounds()).thenReturn(new Rectangle(100, 100, 80, 80));
        when(client.getWidget(MicrobotSailingFacade.WORLD_MAP_ROOT)).thenReturn(map);
        List<String> options = new ArrayList<>();
        when(client.createMenuEntry(-1)).thenAnswer(invocation -> {
            MenuEntry entry = mock(MenuEntry.class, RETURNS_SELF);
            when(entry.setOption(anyString())).thenAnswer(call -> { options.add(call.getArgument(0)); return entry; });
            return entry;
        });
        EfficientSailingController navigation = mock(EfficientSailingController.class);
        when(navigation.isBusy()).thenReturn(true);
        EfficientSailingModule module = new EfficientSailingModule(client, mock(EfficientWalkerConfig.class),
                null, null, null, null, null, null);
        ControllerRecoveryIntegrationTest.set(module, "navigator", navigation);
        module.onMenuOpened(mock(MenuOpened.class));
        assertEquals(Arrays.asList("Cancel sailing"), options);
        MenuOptionClicked event = mock(MenuOptionClicked.class);
        when(event.getMenuOption()).thenReturn("Cancel sailing");
        when(event.getMenuTarget()).thenReturn("<col=00ff00>Efficient Sailing</col>");
        module.onMenuOptionClicked(event);
        verify(event).consume(); verify(navigation).cancel();
        verify(navigation, never()).togglePause();
        verify(navigation, never()).sailTo(any());
        verify(client, never()).getWorldMap();
        MenuEntry existing = mock(MenuEntry.class);
        when(existing.getOption()).thenReturn("Cancel sailing");
        when(existing.getTarget()).thenReturn("<col=00ff00>Efficient Sailing</col>");
        when(client.getMenuEntries()).thenReturn(new MenuEntry[]{existing});
        options.clear(); module.onMenuOpened(mock(MenuOpened.class));
        assertTrue(options.isEmpty(), "An existing cancel must not be duplicated");
    }

    @Test public void sceneAndEveryMinimapLayoutHaveNoSailingControls() throws Exception {
        for (int id : new int[]{-1, ComponentID.FIXED_VIEWPORT_MINIMAP_DRAW_AREA,
                ComponentID.RESIZABLE_VIEWPORT_MINIMAP_DRAW_AREA, ComponentID.RESIZABLE_VIEWPORT_BOTTOM_LINE_MINIMAP_DRAW_AREA}) {
            Client client = mock(Client.class);
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
            when(client.getMouseCanvasPosition()).thenReturn(new Point(110, 110));
            if (id != -1) {
                Widget minimap = mock(Widget.class);
                when(minimap.getBounds()).thenReturn(new Rectangle(100, 100, 80, 80));
                when(client.getWidget(id)).thenReturn(minimap);
            }
            EfficientSailingController navigation = mock(EfficientSailingController.class);
            when(navigation.isBusy()).thenReturn(true);
            EfficientSailingModule module = new EfficientSailingModule(client, mock(EfficientWalkerConfig.class),
                    null, null, null, null, null, null);
            ControllerRecoveryIntegrationTest.set(module, "navigator", navigation);
            module.onMenuOpened(mock(MenuOpened.class));
            verify(client, never()).createMenuEntry(anyInt());
        }
    }
}
