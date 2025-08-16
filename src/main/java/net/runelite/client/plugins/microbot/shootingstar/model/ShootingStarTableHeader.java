package net.runelite.client.plugins.microbot.shootingstar.model;

import java.awt.Color;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import net.runelite.client.plugins.microbot.shootingstar.ShootingStarPlugin;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.ImageUtil;

public class ShootingStarTableHeader extends JPanel
{

	private static final ImageIcon ARROW_UP;
	private static final ImageIcon HIGHLIGHT_ARROW_UP;
	private static final ImageIcon HIGHLIGHT_ARROW_DOWN;

	static
	{
		final BufferedImage arrowDown = ImageUtil.loadImageResource(ShootingStarPlugin.class, "arrow.png");
		final BufferedImage arrowUp = ImageUtil.rotateImage(arrowDown, Math.PI);
		ARROW_UP = new ImageIcon(arrowUp);

		final BufferedImage highlightArrowDown = ImageUtil.fillImage(arrowDown, ColorScheme.BRAND_ORANGE);
		final BufferedImage highlightArrowUp = ImageUtil.fillImage(arrowUp, ColorScheme.BRAND_ORANGE);
		HIGHLIGHT_ARROW_DOWN = new ImageIcon(highlightArrowDown);
		HIGHLIGHT_ARROW_UP = new ImageIcon(highlightArrowUp);
	}

	private final JLabel textLabel = new JLabel();
	private final JLabel arrowLabel = new JLabel();
	private boolean ordering = false;

	public ShootingStarTableHeader(String title)
	{
		setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
		setBorder(new CompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 0, 1, ColorScheme.LIGHT_GRAY_COLOR),
			new EmptyBorder(0, 2, 0, 2)
		));
		setBackground(ColorScheme.SCROLL_TRACK_COLOR);
		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				if (!ordering)
				{
					textLabel.setForeground(ColorScheme.BRAND_ORANGE);
				}
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				if (!ordering)
				{
					textLabel.setForeground(Color.WHITE);
				}
			}
		});

		textLabel.setFont(FontManager.getRunescapeSmallFont());
		textLabel.setText(title);
		textLabel.setForeground(Color.WHITE);
		add(textLabel);
		add(Box.createHorizontalStrut(1));
		add(arrowLabel);
	}

	public void highlight(boolean highlight, boolean ascending)
	{
		ordering = highlight;
		arrowLabel.setIcon(highlight
			? ascending ? HIGHLIGHT_ARROW_UP : HIGHLIGHT_ARROW_DOWN
			: ascending ? ARROW_UP : null);
		textLabel.setForeground(highlight ? ColorScheme.BRAND_ORANGE : Color.WHITE);
	}
}
