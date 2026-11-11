package net.runelite.client.plugins.microbot.droelf;

import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Locale;

public class DroElfOverlay extends OverlayPanel {
    private static final int WIDTH = 224;
    private static final int CRYSTAL_ICON_ID = 23959; // Enhanced crystal teleport seed

    // Same palette used by DroCrafterOverlay.
    private static final Color BACKGROUND = new Color(18, 12, 25, 242);
    private static final Color BORDER = new Color(169, 112, 214, 190);
    private static final Color PURPLE = new Color(205, 153, 245);
    private static final Color TEXT = new Color(247, 241, 250);
    private static final Color MUTED = new Color(187, 171, 198);
    private static final Color LIVE = new Color(238, 184, 76);

    private final DroElfPlugin plugin;
    private final ItemManager itemManager;
    private BufferedImage crystalIcon;

    @Inject
    DroElfOverlay(DroElfPlugin plugin, ItemManager itemManager) {
        super(plugin);
        this.plugin = plugin;
        this.itemManager = itemManager;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        panelComponent.getChildren().clear();
        panelComponent.setPreferredSize(new Dimension(WIDTH, 0));

        // Keep the reliable OverlayPanel rendering path, only change its color.
        setPreferredColor(BACKGROUND);

        panelComponent.getChildren().add(TitleComponent.builder()
                .text("DRO ELF THIEVER")
                .color(PURPLE)
                .build());

        String status = plugin.getScriptStatus();
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Activity")
                .leftColor(MUTED)
                .right(status == null || status.isBlank() ? "Starting" : status)
                .rightColor(plugin.hasXpBaseline() ? LIVE : MUTED)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Runtime")
                .leftColor(MUTED)
                .right(duration(plugin.getSessionElapsedMs()))
                .rightColor(TEXT)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Elf")
                .leftColor(MUTED)
                .right(plugin.getLockedElfDisplay())
                .rightColor(TEXT)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Bank")
                .leftColor(MUTED)
                .right(prettifyBank(plugin.getLockedBankDisplay()))
                .rightColor(TEXT)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("XP gained")
                .leftColor(MUTED)
                .right(number(plugin.getXpGained()))
                .rightColor(TEXT)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("XP / hour")
                .leftColor(MUTED)
                .right(number(plugin.getXpPerHour()))
                .rightColor(TEXT)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Profit")
                .leftColor(MUTED)
                .right(number(plugin.getTotalProfit()) + " gp")
                .rightColor(TEXT)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Profit / hour")
                .leftColor(MUTED)
                .right(number(plugin.getProfitPerHour()) + " gp")
                .rightColor(TEXT)
                .build());

        Dimension rendered = super.render(graphics);

        // Decorative only: border and crystal are drawn after OverlayPanel has
        // completed its normal, already-working layout/render cycle.
        if (rendered != null) {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setStroke(new BasicStroke(1.2f));
            graphics.setColor(BORDER);
            graphics.drawRoundRect(0, 0,
                    Math.max(0, rendered.width - 1),
                    Math.max(0, rendered.height - 1),
                    10, 10);
        }

        if (crystalIcon == null) {
            crystalIcon = itemManager.getImage(CRYSTAL_ICON_ID);
        }
        if (rendered != null && crystalIcon != null) {
            graphics.drawImage(crystalIcon, 7, 4, 28, 28, null);
        }

        return rendered;
    }

    private static String prettifyBank(String bank) {
        if (bank == null || bank.isBlank() || bank.equalsIgnoreCase("Not locked")) return "Not locked";
        if (bank.equalsIgnoreCase("PRIFDDINAS_NORTH")) return "Prif North";
        if (bank.equalsIgnoreCase("PRIFDDINAS_SOUTH")) return "Prif South";
        return bank.replace('_', ' ');
    }

    private static String number(long value) {
        return String.format(Locale.US, "%,d", Math.max(0L, value));
    }

    private static String duration(long milliseconds) {
        long seconds = Math.max(0L, milliseconds) / 1000L;
        return String.format(Locale.US, "%02d:%02d:%02d",
                seconds / 3600L, seconds % 3600L / 60L, seconds % 60L);
    }
}
