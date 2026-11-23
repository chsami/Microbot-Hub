package net.runelite.client.plugins.microbot.glassmake;

import net.runelite.client.ui.overlay.OverlayManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class GabulhasGlassMakePluginTest {

    private GabulhasGlassMakeInfo.states previous;

    @BeforeEach
    void setUp() {
        previous = GabulhasGlassMakeInfo.botStatus;
    }

    @AfterEach
    void tearDown() {
        GabulhasGlassMakeInfo.botStatus = previous;
    }

    @Test
    void startUpSetsStatusBeforeOverlayAndScriptStart() throws Exception {
        List<GabulhasGlassMakeInfo.states> seen = new ArrayList<>();
        OverlayManager overlayManager = mock(OverlayManager.class);
        doAnswer(invocation -> {
            seen.add(GabulhasGlassMakeInfo.botStatus);
            return true;
        }).when(overlayManager).add(any());
        GabulhasGlassMakeScript script = mock(GabulhasGlassMakeScript.class);
        doAnswer(invocation -> {
            seen.add(GabulhasGlassMakeInfo.botStatus);
            return true;
        }).when(script).run(any());

        GabulhasGlassMakePlugin plugin = new GabulhasGlassMakePlugin();
        inject(plugin, "overlayManager", overlayManager);
        inject(plugin, "gabulhasGlassMakeOverlay", mock(GabulhasGlassMakeOverlay.class));
        inject(plugin, "gabulhasGlassMakeScript", script);
        inject(plugin, "config", mock(GabulhasGlassMakeConfig.class));
        GabulhasGlassMakeInfo.botStatus = null;

        plugin.startUp();

        assertEquals(List.of(GabulhasGlassMakeInfo.states.Starting, GabulhasGlassMakeInfo.states.Starting), seen);
    }

    private static void inject(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
