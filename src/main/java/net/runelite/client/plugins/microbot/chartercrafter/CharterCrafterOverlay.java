package net.runelite.client.plugins.microbot.chartercrafter;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class CharterCrafterOverlay extends OverlayPanel {
    private final CharterCrafterPlugin plugin;

    @Inject
    public CharterCrafterOverlay(CharterCrafterPlugin plugin) {
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        setPriority(OverlayPriority.HIGH);
        panelComponent.setPreferredSize(new Dimension(250, 0));
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        panelComponent.getChildren().clear();
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Charter Crafter")
                .color(Color.CYAN)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Status:")
                .right(condense(plugin.getStatus(), 27))
                .build());

        long elapsedMs = Math.max(1L, System.currentTimeMillis() - plugin.getStartTimeMillis());

        int currentMagicXp = Microbot.getClient() != null ? Microbot.getClient().getSkillExperience(Skill.MAGIC) : 0;
        int currentCraftingXp = Microbot.getClient() != null ? Microbot.getClient().getSkillExperience(Skill.CRAFTING) : 0;
        int magicGained = Math.max(0, currentMagicXp - plugin.getStartMagicXp());
        int craftingGained = Math.max(0, currentCraftingXp - plugin.getStartCraftingXp());

        int magicPerHour = (int) ((magicGained * 3600000L) / elapsedMs);
        int craftingPerHour = (int) ((craftingGained * 3600000L) / elapsedMs);

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Amount crafted:")
                .right(Integer.toString(plugin.getMoltenGlassCrafted()))
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Magic XP/hr:")
                .right(formatCompact(magicPerHour))
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Crafting XP/hr:")
                .right(formatCompact(craftingPerHour))
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Magic XP gained:")
                .right(formatCompact(magicGained))
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Crafting XP gained:")
                .right(formatCompact(craftingGained))
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Runtime:")
                .right(formatDuration(elapsedMs))
                .build());

        return super.render(graphics);
    }

    private static String formatDuration(long ms) {
        long seconds = ms / 1000L;
        long hours = seconds / 3600L;
        seconds %= 3600L;
        long minutes = seconds / 60L;
        seconds %= 60L;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    private static String formatCompact(int value) {
        long v = value;
        if (v < 1000) return Long.toString(v);
        if (v < 1_000_000) {
            double k = v / 1000.0;
            return String.format("%.1fK", k);
        }
        if (v < 1_000_000_000) {
            double m = v / 1_000_000.0;
            return String.format("%.1fM", m);
        }
        double b = v / 1_000_000_000.0;
        return String.format("%.1fB", b);
    }

    private static String condense(String s, int maxLen) {
        if (s == null) return "";
        String t = s.trim();
        if (t.length() <= maxLen) return t;
        int cut = Math.max(0, maxLen - 3);
        return t.substring(0, cut) + "...";
    }
}
