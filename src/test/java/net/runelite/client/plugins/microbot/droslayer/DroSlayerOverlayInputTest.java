package net.runelite.client.plugins.microbot.droslayer;

import java.awt.Canvas;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import net.runelite.client.plugins.microbot.util.mouse.BotEventGuard;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class DroSlayerOverlayInputTest {
    @Test public void blockedButtonClickStillTogglesAndDoesNotReleaseGameInput() {
        DroSlayerOverlay overlay = overlay();
        Canvas canvas = new Canvas();
        overlay.enableCanvasInput(canvas);
        try {
            MouseEvent press = event(canvas, MouseEvent.MOUSE_PRESSED, 365, 110);
            press.consume();
            overlay.handleCanvasInput(press);
            assertTrue(press.isConsumed());
            assertTrue(render(overlay).height > 50);
            for (int id : new int[]{MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED}) {
                MouseEvent finish = event(canvas,id,365,110);
                overlay.handleCanvasInput(finish);
                assertTrue(finish.isConsumed());
            }
            MouseEvent outside = event(canvas,MouseEvent.MOUSE_PRESSED,150,170);
            outside.consume();
            overlay.handleCanvasInput(outside);
            assertTrue(outside.isConsumed());
            assertTrue(render(overlay).height > 50);
            overlay.handleCanvasInput(event(canvas,MouseEvent.MOUSE_PRESSED,365,110));
            assertEquals(50,render(overlay).height);
        } finally { overlay.disableCanvasInput(); }
    }

    @Test public void otherComponentsAndSyntheticClicksCannotToggleTheOverlay() {
        DroSlayerOverlay overlay = overlay();
        Canvas canvas = new Canvas();
        overlay.enableCanvasInput(canvas);
        try {
            overlay.handleCanvasInput(event(new Canvas(),MouseEvent.MOUSE_PRESSED,365,110));
            BotEventGuard.begin();
            try {
                overlay.handleCanvasInput(event(canvas,MouseEvent.MOUSE_PRESSED,365,110));
                overlay.getInputListener().mousePressed(event(canvas,MouseEvent.MOUSE_PRESSED,365,110));
            }
            finally { BotEventGuard.end(); }
            assertEquals(50,render(overlay).height);
            overlay.disableCanvasInput();
            overlay.handleCanvasInput(event(canvas,MouseEvent.MOUSE_PRESSED,365,110));
            assertEquals(50,render(overlay).height);
        } finally { overlay.disableCanvasInput(); }
    }

    @Test public void startsMinimizedAgainAfterRestart() {
        DroSlayerOverlay overlay = overlay();
        Canvas canvas = new Canvas();
        overlay.enableCanvasInput(canvas);
        try {
            assertEquals(50, render(overlay).height);
            overlay.handleCanvasInput(event(canvas, MouseEvent.MOUSE_PRESSED, 365, 110));
            assertTrue(render(overlay).height > 50);
            overlay.disableCanvasInput();
            overlay.enableCanvasInput(canvas);
            assertEquals(50, render(overlay).height);
        } finally { overlay.disableCanvasInput(); }
    }

    private static DroSlayerOverlay overlay() {
        DroSlayerPlugin plugin = mock(DroSlayerPlugin.class);
        when(plugin.getRuntimeDisplay()).thenReturn("00:00:00");
        when(plugin.getSelectedSlayerMaster()).thenReturn(DroSlayerMaster.DURADEL);
        DroSlayerOverlay overlay = new DroSlayerOverlay(plugin);
        overlay.getBounds().setBounds(100,100,280,240);
        return overlay;
    }
    private static MouseEvent event(Canvas canvas,int id,int x,int y) {
        return new MouseEvent(canvas,id,0,0,x,y,1,false,MouseEvent.BUTTON1);
    }
    private static Dimension render(DroSlayerOverlay overlay) {
        Graphics2D graphics = new BufferedImage(300,400,BufferedImage.TYPE_INT_ARGB).createGraphics();
        try { return overlay.render(graphics); }
        finally { graphics.dispose(); }
    }
}
