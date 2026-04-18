package net.runelite.client.plugins.microbot.HueycoatlPrayer;

import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;

import java.awt.*;

public class HueyPrayerOverlay extends Overlay
{
    public HueyPrayerOverlay()
    {
        setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        graphics.setColor(Color.WHITE);
        graphics.drawString("Huey Prayer Active", 10, 20);
        return null;
    }
}