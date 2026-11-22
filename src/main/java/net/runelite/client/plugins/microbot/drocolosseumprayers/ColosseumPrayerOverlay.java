package net.runelite.client.plugins.microbot.drocolosseumprayers;

import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

class ColosseumPrayerOverlay extends OverlayPanel
{
	private final DroColosseumPrayersPlugin plugin;
	private final DroColosseumPrayersConfig config;

	@Inject
	ColosseumPrayerOverlay(DroColosseumPrayersPlugin plugin, DroColosseumPrayersConfig config)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showOverlay() || !plugin.inside())
		{
			return null;
		}
		panelComponent.getChildren().add(TitleComponent.builder().text("Colosseum Prayers").build());
		panelComponent.getChildren().add(LineComponent.builder().left("Overhead").right(plugin.wantedLabel()).build());
		panelComponent.getChildren().add(LineComponent.builder().left("Answering").right(plugin.answeringLabel()).build());
		panelComponent.getChildren().add(LineComponent.builder().left("Next").right(plugin.upcomingLabel()).build());
		panelComponent.getChildren().add(LineComponent.builder().left("Status").right(plugin.status()).build());
		return super.render(graphics);
	}
}
