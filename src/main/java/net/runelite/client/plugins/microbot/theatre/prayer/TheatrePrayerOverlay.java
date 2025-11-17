package net.runelite.client.plugins.microbot.theatre.prayer;

import lombok.AccessLevel;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.Prayer;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.theatre.TheatreConfig;
import net.runelite.client.ui.overlay.*;

import javax.inject.Inject;
import java.awt.*;
import java.util.Queue;

public abstract class TheatrePrayerOverlay extends Overlay
{
	private static final int TICK_PIXEL_SIZE = 60;
	private static final int BOX_WIDTH = 10;
	private static final int BOX_HEIGHT = 5;
	private static final int PRAYER_WIDGET_GROUP_ID = 541;

	@Getter(AccessLevel.PROTECTED)
	private final TheatreConfig config;
	private final Client client;

	@Inject
	protected TheatrePrayerOverlay(final Client client, final TheatreConfig config)
	{
		this.client = client;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(OverlayPriority.HIGHEST);
	}

	protected abstract Queue<TheatreUpcomingAttack> getAttackQueue();

	protected abstract long getLastTick();

	protected abstract boolean isEnabled();

	@Override
	public Dimension render(Graphics2D graphics)
	{
		final Widget meleePrayerWidget = getPrayerWidget(Prayer.PROTECT_FROM_MELEE);
		final Widget rangePrayerWidget = getPrayerWidget(Prayer.PROTECT_FROM_MISSILES);
		final Widget magicPrayerWidget = getPrayerWidget(Prayer.PROTECT_FROM_MAGIC);


		var prayerWidgetHidden = meleePrayerWidget == null
			|| rangePrayerWidget == null
			|| magicPrayerWidget == null
			|| meleePrayerWidget.isHidden()
			|| rangePrayerWidget.isHidden()
			|| magicPrayerWidget.isHidden();

		if ((config.prayerHelper() && isEnabled()) && (!prayerWidgetHidden || config.alwaysShowPrayerHelper()))
		{
			renderPrayerIconOverlay(graphics);

			if (config.descendingBoxes())
			{
				renderDescendingBoxes(graphics);
			}
		}

		return null;
	}

	private void renderDescendingBoxes(Graphics2D graphics)
	{
		var tickPriorityMap = TheatrePrayerUtil.getTickPriorityMap(getAttackQueue());

		getAttackQueue().forEach(attack -> {
			int tick = attack.getTicksUntil();
			final Color color = tick == 1 ? config.prayerColorDanger() : config.prayerColor();
			final Widget prayerWidget = getPrayerWidget(attack.getPrayer());

			if (prayerWidget == null)
			{
				return;
			}

			int baseX = (int) prayerWidget.getBounds().getX();
			baseX += prayerWidget.getBounds().getWidth() / 2;
			baseX -= BOX_WIDTH / 2;

			int baseY = (int) prayerWidget.getBounds().getY() - tick * TICK_PIXEL_SIZE - BOX_HEIGHT;
			baseY += TICK_PIXEL_SIZE - ((getLastTick() + 600 - System.currentTimeMillis()) / 600.0 * TICK_PIXEL_SIZE);

			final Rectangle boxRectangle = new Rectangle(BOX_WIDTH, BOX_HEIGHT);
			boxRectangle.translate(baseX, baseY);

			if (attack.getPrayer().equals(tickPriorityMap.get(attack.getTicksUntil()).getPrayer()))
			{
				OverlayUtil.renderPolygon(graphics, boxRectangle, color, color, new BasicStroke(2));
			}
			else if (config.indicateNonPriorityDescendingBoxes())
			{
				OverlayUtil.renderPolygon(graphics, boxRectangle, color, new Color(0, 0, 0, 0), new BasicStroke(2));
			}
		});
	}

	private void renderPrayerIconOverlay(Graphics2D graphics)
	{
		var attack = getAttackQueue().peek();
		if (attack == null)
		{
			return;
		}

		if (!client.isPrayerActive(attack.getPrayer()))
		{
			final Widget prayerWidget = getPrayerWidget(attack.getPrayer());
			if (prayerWidget == null)
			{
				return;
			}

			final Rectangle prayerRectangle = new Rectangle((int) prayerWidget.getBounds().getWidth(), (int) prayerWidget.getBounds().getHeight());
			prayerRectangle.translate((int) prayerWidget.getBounds().getX(), (int) prayerWidget.getBounds().getY());

			OverlayUtil.renderPolygon(graphics, prayerRectangle, config.prayerColorDanger());
		}
	}

	private Widget getPrayerWidget(Prayer prayer)
	{
		int childId = getPrayerChildId(prayer);
		if (childId < 0)
		{
			return null;
		}

		Widget widget = client.getWidget(PRAYER_WIDGET_GROUP_ID, childId);
		if (widget == null || widget.isHidden())
		{
			return null;
		}

		return widget;
	}

	private int getPrayerChildId(Prayer prayer)
	{
		switch (prayer)
		{
			case PROTECT_FROM_MISSILES:
				return 18;
			case PROTECT_FROM_MAGIC:
				return 17;
			case PROTECT_FROM_MELEE:
				return 19;
			default:
				return -1;
		}
	}
}
