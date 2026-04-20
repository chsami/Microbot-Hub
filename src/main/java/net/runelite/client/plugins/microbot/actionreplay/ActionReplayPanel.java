package net.runelite.client.plugins.microbot.actionreplay;

import net.runelite.client.plugins.microbot.actionreplay.model.RecordedAction;
import net.runelite.client.plugins.microbot.actionreplay.model.Recording;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

import javax.inject.Inject;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.ScrollPaneConstants;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ActionReplayPanel extends PluginPanel
{
	private static final Color MUTED = new Color(160, 160, 160);
	private static final String CURRENT_LABEL = "(Current recording)";

	private ActionReplayPlugin plugin;

	private final JLabel countLabel = new JLabel(" ");
	private final JButton recordButton = new JButton("● Record script");
	private final JButton stopPlaybackButton = new JButton("■ Stop script");

	private final JComboBox<String> scriptSelector = new JComboBox<>();
	private final DefaultListModel<String> actionsModel = new DefaultListModel<>();
	private final JList<String> actionsList = new JList<>(actionsModel);

	private final JButton upButton = new JButton("↑");
	private final JButton downButton = new JButton("↓");
	private final JButton deleteStepButton = new JButton("✕");
	private final JButton editStepButton = new JButton("✎");
	private final JButton duplicateStepButton = new JButton("⧉");
	private final JButton playButton = new JButton("▶ Run script");
	private final JButton renameButton = new JButton("✎ Rename");
	private final JButton deleteScriptButton = new JButton("🗑 Delete");

	private Recording viewedRecording;
	private Recording lastLiveRecording;
	private List<Recording> savedRecordings = new ArrayList<>();
	private boolean suppressSelectorEvents = false;

	@Inject
	public ActionReplayPanel()
	{
		super(false);
		setBorder(new EmptyBorder(10, 10, 10, 10));
		setLayout(new BorderLayout(0, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		add(buildHeader(), BorderLayout.NORTH);
		add(buildCenter(), BorderLayout.CENTER);
	}

	private JPanel buildHeader()
	{
		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		header.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel title = new JLabel("AIO AIO");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		title.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.add(title);
		header.add(Box.createVerticalStrut(6));

		countLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.add(countLabel);

		header.add(Box.createVerticalStrut(8));

		recordButton.setAlignmentX(Component.LEFT_ALIGNMENT);
		recordButton.setForeground(Color.WHITE);
		recordButton.addActionListener(e -> onRecordClicked());

		playButton.setAlignmentX(Component.LEFT_ALIGNMENT);
		playButton.setForeground(Color.WHITE);
		playButton.setToolTipText("Loop selected script until stopped");
		playButton.addActionListener(e -> onPlay(true));

		stopPlaybackButton.setAlignmentX(Component.LEFT_ALIGNMENT);
		stopPlaybackButton.setForeground(Color.WHITE);
		stopPlaybackButton.addActionListener(e -> plugin.stopPlayback());

		header.add(playButton);
		header.add(Box.createVerticalStrut(4));
		header.add(stopPlaybackButton);
		header.add(Box.createVerticalStrut(4));
		header.add(recordButton);

		return header;
	}

	private JPanel buildCenter()
	{
		JPanel center = new JPanel();
		center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
		center.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel selectorLabel = new JLabel("Script:");
		selectorLabel.setForeground(MUTED);
		selectorLabel.setFont(selectorLabel.getFont().deriveFont(11f));
		selectorLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		center.add(selectorLabel);
		center.add(Box.createVerticalStrut(2));

		scriptSelector.setAlignmentX(Component.LEFT_ALIGNMENT);
		scriptSelector.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		scriptSelector.addActionListener(e ->
		{
			if (!suppressSelectorEvents)
			{
				onSelectorChanged();
			}
		});
		center.add(scriptSelector);
		center.add(Box.createVerticalStrut(4));

		actionsList.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		actionsList.setForeground(Color.WHITE);
		actionsList.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (e.getClickCount() == 2)
				{
					int idx = actionsList.locationToIndex(e.getPoint());
					if (idx >= 0)
					{
						actionsList.setSelectedIndex(idx);
						onEditStep();
					}
				}
			}
		});
		JScrollPane scroll = new JScrollPane(actionsList);
		scroll.setPreferredSize(new Dimension(220, 200));
		scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
		scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
		scroll.setBorder(null);
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		center.add(scroll);
		center.add(Box.createVerticalStrut(6));

		JPanel editRow = new JPanel(new GridLayout(1, 5, 3, 0));
		editRow.setOpaque(false);
		editRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		editRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

		initSmallButton(upButton, "Move step up");
		upButton.addActionListener(e -> onMoveUp());
		editRow.add(upButton);

		initSmallButton(downButton, "Move step down");
		downButton.addActionListener(e -> onMoveDown());
		editRow.add(downButton);

		initSmallButton(editStepButton, "Edit step (double-click works too)");
		editStepButton.addActionListener(e -> onEditStep());
		editRow.add(editStepButton);

		initSmallButton(duplicateStepButton, "Duplicate selected step");
		duplicateStepButton.addActionListener(e -> onDuplicateStep());
		editRow.add(duplicateStepButton);

		initSmallButton(deleteStepButton, "Delete selected step(s)");
		deleteStepButton.addActionListener(e -> onDeleteStep());
		editRow.add(deleteStepButton);

		center.add(editRow);
		center.add(Box.createVerticalStrut(6));

		JPanel scriptRow = new JPanel(new GridLayout(1, 2, 4, 4));
		scriptRow.setOpaque(false);
		scriptRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		scriptRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

		renameButton.setToolTipText("Rename selected script");
		renameButton.addActionListener(e -> onRename());
		scriptRow.add(renameButton);

		deleteScriptButton.setToolTipText("Delete selected script");
		deleteScriptButton.addActionListener(e -> onDeleteScript());
		scriptRow.add(deleteScriptButton);

		center.add(scriptRow);

		return center;
	}

	private void initSmallButton(JButton b, String tooltip)
	{
		b.setToolTipText(tooltip);
		b.setMargin(new java.awt.Insets(2, 4, 2, 4));
		b.setFocusPainted(false);
	}

	public void setPlugin(ActionReplayPlugin plugin)
	{
		this.plugin = plugin;
	}

	public void refresh()
	{
		if (plugin == null)
		{
			return;
		}
		SwingUtilities.invokeLater(() ->
		{
			boolean rec = plugin.isRecording();
			boolean play = plugin.isPlaying();

			if (rec)
			{
				recordButton.setText("■ Stop recording");
				countLabel.setText("Captured: " + plugin.getCurrentRecordingSize() + " actions");
				countLabel.setForeground(MUTED);
			}
			else
			{
				recordButton.setText("● Record script");
				countLabel.setText(" ");
			}
			recordButton.setEnabled(!play);
			stopPlaybackButton.setEnabled(play);

			if (rec)
			{
				viewedRecording = null;
			}

			refreshSelector();
			reloadActionList();
			updateButtonEnabled(rec, play);

			revalidate();
			repaint();
		});
	}

	private void updateButtonEnabled(boolean rec, boolean play)
	{
		Recording selected = getSelectedRecording();
		boolean hasActions = selected != null && selected.size() > 0;
		boolean canEditSteps = !rec && !play && hasActions;

		upButton.setEnabled(canEditSteps);
		downButton.setEnabled(canEditSteps);
		deleteStepButton.setEnabled(canEditSteps);
		editStepButton.setEnabled(canEditSteps);
		duplicateStepButton.setEnabled(canEditSteps);

		playButton.setEnabled(!rec && !play && hasActions);
		renameButton.setEnabled(!rec && !play && viewedRecording != null);
		deleteScriptButton.setEnabled(!rec && !play && viewedRecording != null);

		scriptSelector.setEnabled(!rec);
	}

	private void refreshSelector()
	{
		suppressSelectorEvents = true;
		try
		{
			String prev = viewedRecording != null ? viewedRecording.getName() : CURRENT_LABEL;
			scriptSelector.removeAllItems();
			scriptSelector.addItem(CURRENT_LABEL);
			savedRecordings = plugin.listRecordings();
			for (Recording r : savedRecordings)
			{
				scriptSelector.addItem(r.getName());
			}
			boolean matched = false;
			for (int i = 0; i < scriptSelector.getItemCount(); i++)
			{
				if (prev.equals(scriptSelector.getItemAt(i)))
				{
					scriptSelector.setSelectedIndex(i);
					matched = true;
					break;
				}
			}
			if (!matched)
			{
				scriptSelector.setSelectedIndex(0);
				viewedRecording = null;
			}
		}
		finally
		{
			suppressSelectorEvents = false;
		}
	}

	private void onSelectorChanged()
	{
		int idx = scriptSelector.getSelectedIndex();
		if (idx <= 0)
		{
			viewedRecording = null;
		}
		else
		{
			int savedIdx = idx - 1;
			if (savedIdx >= 0 && savedIdx < savedRecordings.size())
			{
				viewedRecording = savedRecordings.get(savedIdx);
			}
		}
		reloadActionList();
		updateButtonEnabled(plugin.isRecording(), plugin.isPlaying());
	}

	private Recording getSelectedRecording()
	{
		if (viewedRecording != null)
		{
			return viewedRecording;
		}
		if (plugin.isRecording())
		{
			return plugin.getCurrentRecording();
		}
		return lastLiveRecording;
	}

	private void reloadActionList()
	{
		int prevSelected = actionsList.getSelectedIndex();
		Recording r = getSelectedRecording();
		actionsModel.clear();
		if (r == null || r.getActions() == null || r.getActions().isEmpty())
		{
			return;
		}
		for (int i = 0; i < r.getActions().size(); i++)
		{
			actionsModel.addElement(format(i, r.getActions().get(i)));
		}
		if (prevSelected >= 0 && prevSelected < actionsModel.size())
		{
			actionsList.setSelectedIndex(prevSelected);
		}
	}

	public void onActionRecorded(RecordedAction action)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (viewedRecording == null)
			{
				int idx = actionsModel.size();
				actionsModel.addElement(format(idx, action));
				actionsList.ensureIndexIsVisible(idx);
			}
			countLabel.setText("Captured: " + plugin.getCurrentRecordingSize() + " actions");
			countLabel.setForeground(MUTED);
		});
	}

	private void onRecordClicked()
	{
		if (plugin.isRecording())
		{
			Recording saved = plugin.stopRecording(true);
			if (saved != null)
			{
				lastLiveRecording = saved;
			}
			viewedRecording = null;
			refresh();
		}
		else
		{
			actionsModel.clear();
			viewedRecording = null;
			lastLiveRecording = null;
			plugin.startRecording();
			refresh();
		}
	}

	private void onMoveUp()
	{
		Recording r = getSelectedRecording();
		if (r == null)
		{
			return;
		}
		int idx = actionsList.getSelectedIndex();
		if (idx <= 0)
		{
			return;
		}
		List<RecordedAction> actions = r.getActions();
		RecordedAction moved = actions.remove(idx);
		actions.add(idx - 1, moved);
		reloadActionList();
		actionsList.setSelectedIndex(idx - 1);
		persistIfSaved(r);
	}

	private void onMoveDown()
	{
		Recording r = getSelectedRecording();
		if (r == null)
		{
			return;
		}
		int idx = actionsList.getSelectedIndex();
		if (idx < 0 || idx >= r.getActions().size() - 1)
		{
			return;
		}
		List<RecordedAction> actions = r.getActions();
		RecordedAction moved = actions.remove(idx);
		actions.add(idx + 1, moved);
		reloadActionList();
		actionsList.setSelectedIndex(idx + 1);
		persistIfSaved(r);
	}

	private void onDeleteStep()
	{
		Recording r = getSelectedRecording();
		if (r == null)
		{
			return;
		}
		int[] selected = actionsList.getSelectedIndices();
		if (selected.length == 0)
		{
			return;
		}
		for (int i = selected.length - 1; i >= 0; i--)
		{
			if (selected[i] >= 0 && selected[i] < r.getActions().size())
			{
				r.getActions().remove(selected[i]);
			}
		}
		reloadActionList();
		persistIfSaved(r);
	}

	private void onEditStep()
	{
		Recording r = getSelectedRecording();
		if (r == null)
		{
			return;
		}
		int idx = actionsList.getSelectedIndex();
		if (idx < 0 || idx >= r.getActions().size())
		{
			return;
		}
		RecordedAction a = r.getActions().get(idx);

		int currentTicks = a.getDelayTicksBefore() != null ? a.getDelayTicksBefore() : 0;
		SpinnerNumberModel spinnerModel = new SpinnerNumberModel(currentTicks, 0, 1000, 1);
		JSpinner ticksSpinner = new JSpinner(spinnerModel);

		JPanel form = new JPanel(new GridBagLayout());
		GridBagConstraints gc = new GridBagConstraints();
		gc.insets = new Insets(4, 4, 4, 4);
		gc.anchor = GridBagConstraints.WEST;

		gc.gridx = 0;
		gc.gridy = 0;
		form.add(new JLabel("Action:"), gc);
		gc.gridx = 1;
		form.add(new JLabel(a.describe()), gc);

		gc.gridx = 0;
		gc.gridy = 1;
		form.add(new JLabel("Delay before (ticks):"), gc);
		gc.gridx = 1;
		form.add(ticksSpinner, gc);

		int choice = JOptionPane.showConfirmDialog(this, form, "Edit step #" + (idx + 1),
			JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		if (choice != JOptionPane.OK_OPTION)
		{
			return;
		}

		int newTicks = (Integer) ticksSpinner.getValue();
		a.setDelayTicksBefore(newTicks);
		a.setDelayMsBefore(newTicks * 600L);
		reloadActionList();
		actionsList.setSelectedIndex(idx);
		persistIfSaved(r);
	}

	private void onDuplicateStep()
	{
		Recording r = getSelectedRecording();
		if (r == null)
		{
			return;
		}
		int idx = actionsList.getSelectedIndex();
		if (idx < 0 || idx >= r.getActions().size())
		{
			return;
		}
		RecordedAction copy = cloneAction(r.getActions().get(idx));
		r.getActions().add(idx + 1, copy);
		reloadActionList();
		actionsList.setSelectedIndex(idx + 1);
		persistIfSaved(r);
	}

	private static RecordedAction cloneAction(RecordedAction src)
	{
		RecordedAction dst = new RecordedAction();
		dst.setDelayMsBefore(src.getDelayMsBefore());
		dst.setDelayTicksBefore(src.getDelayTicksBefore());
		dst.setMenuOption(src.getMenuOption());
		dst.setMenuTarget(src.getMenuTarget());
		dst.setMenuAction(src.getMenuAction());
		dst.setTargetType(src.getTargetType());
		dst.setIdentifier(src.getIdentifier());
		dst.setParam0(src.getParam0());
		dst.setParam1(src.getParam1());
		dst.setItemId(src.getItemId());
		dst.setTargetName(src.getTargetName());
		dst.setTargetId(src.getTargetId());
		dst.setCanvasX(src.getCanvasX());
		dst.setCanvasY(src.getCanvasY());
		return dst;
	}

	private void persistIfSaved(Recording r)
	{
		if (r == null || viewedRecording == null)
		{
			return;
		}
		try
		{
			plugin.save(r);
		}
		catch (IOException ex)
		{
			JOptionPane.showMessageDialog(this,
				"Save failed: " + ex.getMessage(),
				"AIO AIO", JOptionPane.ERROR_MESSAGE);
		}
	}

	private void onPlay(boolean loop)
	{
		Recording r = getSelectedRecording();
		if (r == null || r.size() == 0)
		{
			return;
		}
		plugin.play(r, loop);
	}

	private void onRename()
	{
		if (viewedRecording == null)
		{
			return;
		}
		String name = JOptionPane.showInputDialog(this, "New name:", viewedRecording.getName());
		if (name == null || name.trim().isEmpty())
		{
			return;
		}
		try
		{
			plugin.rename(viewedRecording, name.trim());
		}
		catch (IOException | IllegalArgumentException ex)
		{
			JOptionPane.showMessageDialog(this,
				"Rename failed: " + ex.getMessage(),
				"AIO AIO", JOptionPane.ERROR_MESSAGE);
		}
		refresh();
	}

	private void onDeleteScript()
	{
		if (viewedRecording == null)
		{
			return;
		}
		int choice = JOptionPane.showConfirmDialog(this,
			"Delete recording '" + viewedRecording.getName() + "'?",
			"AIO AIO", JOptionPane.OK_CANCEL_OPTION);
		if (choice != JOptionPane.OK_OPTION)
		{
			return;
		}
		try
		{
			plugin.delete(viewedRecording);
		}
		catch (IOException ex)
		{
			JOptionPane.showMessageDialog(this,
				"Delete failed: " + ex.getMessage(),
				"AIO AIO", JOptionPane.ERROR_MESSAGE);
		}
		viewedRecording = null;
		refresh();
	}

	private static String format(int idx, RecordedAction a)
	{
		Integer ticks = a.getDelayTicksBefore();
		String delay = ticks == null ? "—" : ticks + "t";
		return String.format("%03d  %s  (%s)", idx + 1, a.describe(), delay);
	}
}
