package net.runelite.client.plugins.microbot.shootingstar;

import com.google.common.collect.Ordering;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.util.List;
import java.util.function.Function;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shootingstar.ShootingStarPlugin;
import net.runelite.client.plugins.microbot.shootingstar.model.ShootingStarTableHeader;
import net.runelite.client.plugins.microbot.shootingstar.model.ShootingStarTableRow;
import net.runelite.client.plugins.microbot.shootingstar.model.Star;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

@Slf4j
public class ShootingStarPanel extends PluginPanel
{
	public static final int WORLD_WIDTH = 35;
	public static final int LOCATION_WIDTH = 125;
	public static final int TIER_WIDTH = 25;
	public static final int TIME_WIDTH = 70;

	private static final String SELECT_OPTION = "Select star";
	private static final String UNSELECT_OPTION = "Unselect Star";
	private static final String BLACKLIST_OPTION = "Blacklist location";

	private final JPanel listContainer = new JPanel();
	private final ShootingStarPlugin plugin;

	private ShootingStarTableHeader worldHeader;
	private ShootingStarTableHeader locationHeader;
	private ShootingStarTableHeader tierHeader;
	private ShootingStarTableHeader timeLeftHeader;
	private Order orderIndex = Order.TIER;
	private boolean ascendingOrder = true;

	public ShootingStarPanel(ShootingStarPlugin plugin)
	{
		this.plugin = plugin;
		setBorder(null);

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

		JPanel header = buildHeader();
		add(header);

		listContainer.setLayout(new BoxLayout(listContainer, BoxLayout.Y_AXIS));
		add(listContainer);

		add(Box.createVerticalGlue());

		JPanel buttonPanel = createButtonPanel();
		add(buttonPanel);
	}

	private JPanel createButtonPanel()
	{
		JPanel buttonContainer = new JPanel();
		buttonContainer.setBorder(new EmptyBorder(5, 5, 5, 5));
		buttonContainer.setLayout(new BoxLayout(buttonContainer, BoxLayout.Y_AXIS));

		JPanel topButtonRow = new JPanel();
		topButtonRow.setLayout(new GridLayout(1, 2, 5, 0));

		JButton importButton = new JButton("Import");
		importButton.setFont(FontManager.getRunescapeBoldFont());
		importButton.setBackground(ColorScheme.BRAND_ORANGE);
		importButton.setForeground(Color.WHITE);
		importButton.setFocusPainted(false);
		importButton.setBorder(new EmptyBorder(4, 8, 4, 8));
		importButton.setToolTipText("Import blacklisted locations");
		importButton.addActionListener(e -> plugin.importBlacklistedLocations());

		JButton exportButton = new JButton("Export");
		exportButton.setFont(FontManager.getRunescapeBoldFont());
		exportButton.setBackground(ColorScheme.BRAND_ORANGE);
		exportButton.setForeground(Color.WHITE);
		exportButton.setFocusPainted(false);
		exportButton.setBorder(new EmptyBorder(4, 8, 4, 8));
		exportButton.setToolTipText("Export blacklisted locations");
		exportButton.addActionListener(e -> plugin.exportBlacklistedLocations());

		topButtonRow.add(importButton);
		topButtonRow.add(exportButton);

		JPanel bottomButtonRow = new JPanel();
		bottomButtonRow.setLayout(new BoxLayout(bottomButtonRow, BoxLayout.X_AXIS));
		bottomButtonRow.add(Box.createHorizontalGlue());

		JButton resetButton = new JButton("Reset");
		resetButton.setFont(FontManager.getRunescapeBoldFont());
		resetButton.setBackground(new Color(255, 55, 40));
		resetButton.setForeground(Color.WHITE);
		resetButton.setFocusPainted(false);
		resetButton.setBorder(new EmptyBorder(4, 8, 4, 8));
		resetButton.setPreferredSize(new Dimension(120, 28));
		resetButton.setToolTipText("Reset configured blacklisted locations");
		resetButton.addActionListener(e -> plugin.clearBlacklistedLocations());

		JButton supportButton = new JButton("Support");
		supportButton.setFont(FontManager.getRunescapeBoldFont());
		supportButton.setBackground(new Color(76, 175, 80)); // Light green color
		supportButton.setForeground(Color.WHITE);
		supportButton.setFocusPainted(false);
		supportButton.setBorder(new EmptyBorder(4, 8, 4, 8));
		supportButton.setPreferredSize(new Dimension(120, 28));
		supportButton.setToolTipText("Show support to the creator");
		supportButton.addActionListener(e -> {
			try {
				Desktop.getDesktop().browse(URI.create("https://g-mason0.github.io/"));
			} catch (Exception ex) {
				log.error("ShootingStarPanel: Failed to open support link", ex);
			}
		});

		bottomButtonRow.add(resetButton);
		bottomButtonRow.add(Box.createRigidArea(new Dimension(5, 0)));
		bottomButtonRow.add(supportButton);
		bottomButtonRow.add(Box.createHorizontalGlue());

		buttonContainer.add(topButtonRow);
		buttonContainer.add(Box.createRigidArea(new Dimension(0, 5)));
		buttonContainer.add(bottomButtonRow);

		return buttonContainer;
	}

	private JPanel buildHeader()
	{
		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
		header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20)); // Allow full width stretching

		worldHeader = new ShootingStarTableHeader("W");
		worldHeader.setPreferredSize(new Dimension(WORLD_WIDTH, 20));
		worldHeader.setMaximumSize(new Dimension(WORLD_WIDTH, 20));
		worldHeader.addMouseListener(createHeaderMouseOptions(Order.WORLD));

		tierHeader = new ShootingStarTableHeader("T");
		tierHeader.setPreferredSize(new Dimension(TIER_WIDTH, 20));
		tierHeader.setMaximumSize(new Dimension(TIER_WIDTH, 20));
		tierHeader.addMouseListener(createHeaderMouseOptions(Order.TIER));
		tierHeader.highlight(true, ascendingOrder);

		locationHeader = new ShootingStarTableHeader("Location");
		locationHeader.setPreferredSize(new Dimension(LOCATION_WIDTH, 20));
		locationHeader.setMaximumSize(new Dimension(LOCATION_WIDTH, 20));
		locationHeader.addMouseListener(createHeaderMouseOptions(Order.LOCATION));

		timeLeftHeader = new ShootingStarTableHeader("Time Left");
		timeLeftHeader.setPreferredSize(new Dimension(TIME_WIDTH, 20));
		timeLeftHeader.setMaximumSize(new Dimension(TIME_WIDTH, 20));
		timeLeftHeader.addMouseListener(createHeaderMouseOptions(Order.TIME_LEFT));

		header.add(worldHeader);
		header.add(tierHeader);
		header.add(locationHeader);
		header.add(timeLeftHeader);

		header.add(Box.createHorizontalGlue());

		return header;
	}

	private MouseAdapter createHeaderMouseOptions(Order order)
	{
		return new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (SwingUtilities.isRightMouseButton(e))
				{
					return;
				}
				ascendingOrder = orderIndex != order || !ascendingOrder;
				orderBy(order);
			}
		};
	}

	void updateList(List<Star> starData)
	{
		listContainer.removeAll();
		if (starData.isEmpty())
		{
			JLabel noStarsLabel = new JLabel("Please wait for data to be fetched");
			noStarsLabel.setFont(FontManager.getRunescapeSmallFont());
			noStarsLabel.setBorder(new EmptyBorder(5, 5, 5, 5));
			noStarsLabel.setHorizontalAlignment(JLabel.CENTER);
			noStarsLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
			listContainer.add(noStarsLabel);
		}
		else
		{
			starData.sort((r1, r2) ->
			{
				switch (orderIndex)
				{
					case WORLD:
						return getCompareValue(r1, r2, Star::getWorld);
					case LOCATION:
						return getCompareValue(r1, r2, Star::getShootingStarLocation);
					case TIER:
						return getCompareValue(r1, r2, Star::getTier);
					case TIME_LEFT:
						return getCompareValue(r1, r2, Star::getEndsAt);
					default:
						return 0;
				}
			});

			int i = 0;
			for (Star data : starData)
			{
				if (data.isHidden())
				{
					continue;
				}

				Color backgroundColor = i % 2 == 0 ? ColorScheme.DARK_GRAY_COLOR : ColorScheme.DARKER_GRAY_COLOR;
				ShootingStarTableRow r = new ShootingStarTableRow(data, plugin.isDisplayAsMinutes(), backgroundColor, Rs2Player.getWorld());
				r.setComponentPopupMenu(buildPopupMenu(r, data));
				listContainer.add(r);
				i++;
			}
		}

		listContainer.revalidate();
		listContainer.repaint();
	}

	void refreshList(List<Star> starData)
	{
		if (starData.isEmpty())
		{
			return;
		}

		for (Component component : listContainer.getComponents())
		{
			ShootingStarTableRow r = (ShootingStarTableRow) component;
			r.updateTime();
			r.updateSelectedBorder();
			r.updateLocationColor();
			r.updateTier();
			r.updateTierColor();
		}

		listContainer.repaint();
	}

	private JPopupMenu buildPopupMenu(ShootingStarTableRow row, Star star)
	{
		JPopupMenu popupMenu = new JPopupMenu();
		popupMenu.setBorder(new EmptyBorder(5, 5, 5, 5));

		JMenuItem hopEntryOption = new JMenuItem();
		hopEntryOption.setText("Hop to");
		hopEntryOption.setFont(FontManager.getRunescapeSmallFont());
		hopEntryOption.addActionListener(e -> Microbot.hopToWorld(star.getWorld()));
		popupMenu.add(hopEntryOption);

		JMenuItem selectedEntryOption = new JMenuItem();
		if (star.isSelected())
		{
			selectedEntryOption.setText(UNSELECT_OPTION);
		}
		else
		{
			selectedEntryOption.setText(SELECT_OPTION);
		}
		selectedEntryOption.setFont(FontManager.getRunescapeSmallFont());
		selectedEntryOption.addActionListener(e -> {
			if (star.isSelected())
			{
				selectedEntryOption.setText(UNSELECT_OPTION);
			}
			else
			{
				selectedEntryOption.setText(SELECT_OPTION);
			}
			if (plugin.getSelectedStar() != null)
			{
				plugin.getSelectedStar().setSelected(false);
			}
			plugin.updateSelectedStar(star);
			row.updateSelectedBorder();
			selectedEntryOption.repaint();
		});
		popupMenu.add(selectedEntryOption);

		JMenuItem blacklistEntryOption = new JMenuItem();
		blacklistEntryOption.setText(BLACKLIST_OPTION);
		blacklistEntryOption.setFont(FontManager.getRunescapeSmallFont());
		blacklistEntryOption.addActionListener(e -> {
			plugin.addLocationToBlacklist(star.getShootingStarLocation().getLocationName());
		});
		popupMenu.add(blacklistEntryOption);

		JMenuItem removeEntryOption = new JMenuItem();
		removeEntryOption.setText("Remove");
		removeEntryOption.setFont(FontManager.getRunescapeSmallFont());
		removeEntryOption.addActionListener(e -> {
			plugin.removeStar(star);
			plugin.updatePanelList(true);
		});
		popupMenu.add(removeEntryOption);

		return popupMenu;
	}

	private int getCompareValue(Star row1, Star row2, Function<Star, Comparable> compareFn)
	{
		Ordering<Comparable> ordering = Ordering.natural();
		if (!ascendingOrder)
		{
			ordering = ordering.reverse();
		}
		ordering = ordering.reverse();
		return ordering.compare(compareFn.apply(row1), compareFn.apply(row2));
	}

	private void orderBy(Order order)
	{
		// Reset all headers to not highlighted
		worldHeader.highlight(false, false);
		locationHeader.highlight(false, false);
		tierHeader.highlight(false, false);
		timeLeftHeader.highlight(false, false);

		switch (order)
		{
			case WORLD:
				worldHeader.highlight(true, ascendingOrder);
				break;
			case LOCATION:
				locationHeader.highlight(true, ascendingOrder);
				break;
			case TIER:
				tierHeader.highlight(true, ascendingOrder);
				break;
			case TIME_LEFT:
				timeLeftHeader.highlight(true, ascendingOrder);
				break;
		}
		orderIndex = order;
		updateList(plugin.getStarList());
	}

	private enum Order
	{
		WORLD,
		LOCATION,
		TIER,
		TIME_LEFT
	}
}
