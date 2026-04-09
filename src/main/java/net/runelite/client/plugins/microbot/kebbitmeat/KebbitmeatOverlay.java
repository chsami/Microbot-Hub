package net.runelite.client.plugins.microbot.kebbitmeat;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;
import java.time.Duration;
import java.time.Instant;

public class KebbitmeatOverlay extends OverlayPanel {

	private final KebbitmeatPlugin plugin;
	private final KebbitmeatScript script;

	@Inject
	KebbitmeatOverlay(KebbitmeatPlugin plugin, KebbitmeatScript script) {
		super(plugin);
		this.plugin = plugin;
		this.script = script;
		setPosition(OverlayPosition.TOP_LEFT);
		setSnappable(true);
		setNaughty();
	}

	@Override
	public Dimension render(Graphics2D graphics) {
		try {
			panelComponent.getChildren().clear();
			panelComponent.setPreferredSize(new Dimension(200, 300));
			panelComponent.getChildren().add(TitleComponent.builder()
				.text("[H] Kebbit Meat")
				.color(Color.RED)
				.build());

			panelComponent.getChildren().add(LineComponent.builder().build());

			panelComponent.getChildren().add(LineComponent.builder()
				.left("State:")
				.right(script.getCurrentState().name())
				.rightColor(stateColor(script.getCurrentState()))
				.build());

			panelComponent.getChildren().add(LineComponent.builder()
				.left("Kebbits:")
				.right(String.valueOf(script.getKebbitsCollected()))
				.rightColor(Color.GREEN)
				.build());

			if (plugin.getStartTime() != null) {
				panelComponent.getChildren().add(LineComponent.builder()
					.left("Runtime:")
					.right(formatDuration(Duration.between(plugin.getStartTime(), Instant.now())))
					.rightColor(Color.GREEN)
					.build());
			}

		} catch (Exception e) {
			// Suppress overlay exceptions to avoid flickering
		}
		return super.render(graphics);
	}

	private Color stateColor(KebbitmeatScript.State state) {
		switch (state) {
			case TRAPPING: return Color.GREEN;
			case CUTTING_LOGS: return Color.ORANGE;
			case BANKING: return Color.YELLOW;
			case WALKING_TO_BANK:
			case WALKING_TO_AREA: return Color.CYAN;
			default: return Color.WHITE;
		}
	}

	private String formatDuration(Duration duration) {
		long hours = duration.toHours();
		long minutes = duration.toMinutesPart();
		long seconds = duration.toSecondsPart();
		return String.format("%02d:%02d:%02d", hours, minutes, seconds);
	}
}
