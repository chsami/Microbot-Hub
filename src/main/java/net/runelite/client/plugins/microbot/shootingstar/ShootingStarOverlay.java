package net.runelite.client.plugins.microbot.shootingstar;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shootingstar.ShootingStarConfig;
import net.runelite.client.plugins.microbot.shootingstar.ShootingStarPlugin;
import net.runelite.client.plugins.microbot.shootingstar.ShootingStarScript;
import net.runelite.client.plugins.microbot.util.misc.TimeUtils;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentOrientation;
import net.runelite.client.ui.overlay.components.ImageComponent;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.SplitComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.ImageUtil;

@Slf4j
public class ShootingStarOverlay extends OverlayPanel
{

	private final ShootingStarPlugin plugin;
	private final ShootingStarConfig config;
	private final ImageComponent image;
	private final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss");

	@Inject
	ShootingStarOverlay(ShootingStarPlugin plugin, ShootingStarConfig config)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		image = new ImageComponent(getCrashedStarImage());
		setPosition(OverlayPosition.TOP_LEFT);
		setNaughty();
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		try
		{
			panelComponent.setPreferredSize(new Dimension(200, 300));
			panelComponent.setBackgroundColor(new Color(0, 0, 0, 150));

			final LineComponent titleLine = LineComponent.builder()
				.left(" ShootingStar Miner V" + ShootingStarPlugin.version)
				.leftColor(ColorUtil.fromHex("0077B6"))
				.build();

			final SplitComponent splitTitleComponent = SplitComponent.builder()
				.first(image)
				.second(titleLine)
				.orientation(ComponentOrientation.HORIZONTAL)
				.gap(new Point(2, 0))
				.build();

			panelComponent.getChildren().add(splitTitleComponent);

			panelComponent.getChildren().add(LineComponent.builder().build());

			panelComponent.getChildren().add(LineComponent.builder()
				.left("Status:")
				.right(Microbot.status)
				.build());

			if (!config.isHideDevOverlay())
			{
				if (ShootingStarScript.state != null)
				{
					panelComponent.getChildren().add(LineComponent.builder()
						.left("Current State:")
						.right(ShootingStarScript.state.name())
						.build());
				}

				panelComponent.getChildren().add(LineComponent.builder()
					.left("Run time:")
					.right(TimeUtils.getFormattedDuration(plugin.getScriptRuntime()))
					.build());

				panelComponent.getChildren().add(LineComponent.builder()
					.left("Total Stars Mined:")
					.right(Integer.toString(plugin.getTotalStarsMined()))
					.build());

				if (plugin.getSelectedStar() != null)
				{
					panelComponent.getChildren().add(LineComponent.builder().build());

					panelComponent.getChildren().add(TitleComponent.builder()
						.text("Current Star Information")
						.color(ColorUtil.fromHex("0077B6"))
						.build());

					panelComponent.getChildren().add(LineComponent.builder().build());

					panelComponent.getChildren().add(LineComponent.builder()
						.left("Location:")
						.right(plugin.getSelectedStar().getShootingStarLocation().getLocationName())
						.build());

					panelComponent.getChildren().add(LineComponent.builder()
						.left("Is in Wilderness:")
						.right(String.valueOf(plugin.getSelectedStar().isInWilderness()))
						.build());

					panelComponent.getChildren().add(LineComponent.builder()
						.left("World:")
						.right(Integer.toString(plugin.getSelectedStar().getWorld()))
						.build());

					String worldType = plugin.getSelectedStar().isMemberWorld() ? "Member" : "F2P";

					panelComponent.getChildren().add(LineComponent.builder()
						.left("World Type:")
						.right(worldType)
						.build());

					panelComponent.getChildren().add(LineComponent.builder()
						.left("Tier:")
						.right(Integer.toString(plugin.getSelectedStar().getTier()))
						.build());

					Instant endInstant = Instant.ofEpochMilli(plugin.getSelectedStar().getEndsAt());
					String estEndTime = plugin.isDisplayAsMinutes() ? TimeUtils.getFormattedDurationBetween(Instant.now(), endInstant) :
						LocalDateTime.ofInstant(endInstant, ZoneId.systemDefault()).format(dtf);

					panelComponent.getChildren().add(LineComponent.builder()
						.left("Est. End Time:")
						.right(estEndTime)
						.build());


					panelComponent.getChildren().add(LineComponent.builder()
						.left("Has Requirements:")
						.right(String.valueOf(plugin.getSelectedStar().hasRequirements()))
						.build());

					if (!plugin.getSelectedStar().hasRequirements())
					{
						panelComponent.getChildren().add(LineComponent.builder().build());

						panelComponent.getChildren().add(LineComponent.builder()
							.left("Has Location Requirements:")
							.right(String.valueOf(plugin.getSelectedStar().hasLocationRequirements()))
							.build());

						panelComponent.getChildren().add(LineComponent.builder()
							.left("Has Mining Level:")
							.right(String.valueOf(plugin.getSelectedStar().hasMiningLevel()))
							.build());
					}
				}
			}

		}
		catch (Exception ex)
		{
			log.error("Error rendering ShootingStarOverlay: {} - ", ex.getMessage(), ex);
		}
		return super.render(graphics);
	}

	private BufferedImage getCrashedStarImage()
	{
		try
		{
			var img = ImageUtil.loadImageResource(ShootingStarPlugin.class, "crashed_star_7.png");
			return ImageUtil.resizeImage(img, 24, 32, true);
		}
		catch (Exception e)
		{
			log.error("Error loading Star Image: {}", e.getMessage(), e);
			return null;
		}
	}
}
