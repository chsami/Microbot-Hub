package net.runelite.client.plugins.microbot.beggingbot;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class BeggingBotOverlay extends OverlayPanel
{
    private final BeggingBotPlugin plugin;
    private final BeggingBotConfig config;

    @Inject
    BeggingBotOverlay(BeggingBotPlugin plugin, BeggingBotConfig config)
    {
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        BeggingBotScript script = plugin.script();
        if (script == null) return null;
        long now = System.currentTimeMillis();
        long elapsed = Math.max(1, now - script.startedAt());
        int coins = script.coinsGained();
        long perHour = coins * 3_600_000L / elapsed;
        long hopIn = Math.max(0, script.lastTradeAt() + config.noTradeHopMinutes() * 60_000L - now);
        long chatIn = Math.max(0, script.nextChatAt() - now);
        String status = Microbot.status == null || Microbot.status.isEmpty() ? "Starting" : Microbot.status;

        panelComponent.getChildren().add(TitleComponent.builder()
            .text("Begging Bot v" + BeggingBotPlugin.version).color(new Color(255, 200, 60)).build());
        line("Status", status, Color.WHITE);
        line("Time run", clock(elapsed), Color.WHITE);
        line("Gold received", format(coins) + " gp", new Color(120, 230, 120));
        line("Gold / hr", format(perHour) + " gp", new Color(120, 230, 120));
        line("Trades", script.tradesCompleted() + " done / " + script.tradesOpened() + " opened", Color.WHITE);
        line("World", String.valueOf(Microbot.getClient().getWorld()), Color.WHITE);
        line("Players nearby", String.valueOf(script.nearbyCount()), Color.WHITE);
        line("Hops", String.valueOf(script.hops()), Color.WHITE);
        line("Hop if no trade", clock(hopIn), hopIn < 60_000 ? new Color(255, 140, 80) : Color.WHITE);
        line("Messages sent", script.messagesSent() + " (next " + (chatIn / 1000) + "s)", Color.WHITE);
        return super.render(graphics);
    }

    private void line(String left, String right, Color rightColor)
    {
        panelComponent.getChildren().add(LineComponent.builder().left(left).right(right).rightColor(rightColor).build());
    }

    private static String clock(long millis)
    {
        long s = millis / 1000;
        return String.format("%02d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
    }

    private static String format(long value)
    {
        if (value >= 10_000_000) return String.format("%.1fM", value / 1_000_000.0);
        if (value >= 10_000) return String.format("%.1fK", value / 1_000.0);
        return String.valueOf(value);
    }
}
