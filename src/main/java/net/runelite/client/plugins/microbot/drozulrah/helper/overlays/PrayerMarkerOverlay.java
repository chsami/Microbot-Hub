package net.runelite.client.plugins.microbot.drozulrah.helper.overlays;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Prayer;
import net.runelite.api.VarClientInt;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.drozulrah.helper.ZulrahConfig;
import net.runelite.client.plugins.microbot.drozulrah.helper.ZulrahRotationHelper;
import net.runelite.client.plugins.microbot.drozulrah.helper.util.OverlayUtils;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

public class PrayerMarkerOverlay extends Overlay
{
	private final Client client;
	private final ZulrahRotationHelper plugin;
	private final ZulrahConfig config = ZulrahRotationHelper.DEFAULTS;

	@Inject
	private PrayerMarkerOverlay(Client client, ZulrahRotationHelper plugin)
	{
		this.client = client;
		this.plugin = plugin;
		this.setPosition(OverlayPosition.DYNAMIC);
		this.setLayer(OverlayLayer.ABOVE_WIDGETS);
		this.setPriority(OverlayPriority.HIGH);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!plugin.showOverlay()) return null;
		if (config.prayerMarker() && plugin.getZulrahNpc() != null && !plugin.getZulrahNpc().isDead())
		{
			plugin.getZulrahData().forEach((data) -> {
				data.getCurrentPhasePrayer().ifPresent((prayer) -> {
					if (client.getVarcIntValue(VarClientInt.INVENTORY_TAB) == 5)
					{
						Widget widget = client.getWidget(prayer == Prayer.PROTECT_FROM_MAGIC
                            ? net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum.PROTECT_MAGIC.getIndex()
                            : net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum.PROTECT_RANGE.getIndex());
						Color color = !client.isPrayerActive(prayer) ? Color.RED : Color.GREEN;
						OverlayUtils.renderWidgetPolygon(graphics, widget, color, true, false);
					}

				});
			});
			return null;
		}
		else
		{
			return null;
		}
	}

	private int prayerToChildId(Prayer prayer)
	{
		switch (prayer)
		{
			case PROTECT_FROM_MELEE:
				return 19;
			case PROTECT_FROM_MISSILES:
				return 18;
			case PROTECT_FROM_MAGIC:
				return 17;
			default:
				return -1;
		}
	}
}
