package net.runelite.client.plugins.microbot.actionreplay;

import net.runelite.client.plugins.microbot.actionreplay.model.Condition;
import net.runelite.client.plugins.microbot.actionreplay.model.ConditionComparator;
import net.runelite.client.plugins.microbot.actionreplay.model.ConditionType;
import net.runelite.client.plugins.microbot.actionreplay.model.RecordedAction;
import net.runelite.client.plugins.microbot.actionreplay.model.Recording;
import net.runelite.client.plugins.microbot.actionreplay.model.StatKind;
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
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
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
	private static final String NO_SCRIPTS_PLACEHOLDER = "(no scripts)";

	private ActionReplayPlugin plugin;

	private final JLabel countLabel = new JLabel(" ");
	private final JButton recordButton = new JButton("● Record script");
	private final JButton appendButton = new JButton("⊕");
	private final JButton stopPlaybackButton = new JButton("■ Stop script");

	private final JComboBox<String> scriptSelector = new JComboBox<>();
	private final DefaultListModel<String> actionsModel = new DefaultListModel<>();
	private final JList<String> actionsList = new JList<>(actionsModel);

	private final JButton upButton = new JButton("↑");
	private final JButton downButton = new JButton("↓");
	private final JButton deleteStepButton = new JButton("✕");
	private final JButton editStepButton = new JButton("✎");
	private final JButton playButton = new JButton("▶ Run script");
	private final JButton renameButton = new JButton("✎ Rename");
	private final JButton deleteScriptButton = new JButton("🗑 Delete");

	private Recording viewedRecording;
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
		playButton.addActionListener(e -> onPlay());

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

		initSmallButton(appendButton, "Append more actions to the selected script");
		appendButton.addActionListener(e -> onAppendClicked());
		editRow.add(appendButton);

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
			boolean appending = plugin.isAppendingToExisting();

			if (rec)
			{
				countLabel.setText("Captured: " + plugin.getCurrentRecordingSize() + " actions");
				countLabel.setForeground(MUTED);
			}
			else
			{
				countLabel.setText(" ");
			}

			stopPlaybackButton.setEnabled(play);

			refreshSelector();
			reloadActionList();
			updateButtonEnabled(rec, play, appending);

			revalidate();
			repaint();
		});
	}

	private void updateButtonEnabled(boolean rec, boolean play, boolean appending)
	{
		Recording selected = getSelectedRecording();
		boolean hasActions = selected != null && selected.size() > 0;
		boolean canEditSteps = !rec && !play && hasActions;

		upButton.setEnabled(canEditSteps);
		downButton.setEnabled(canEditSteps);
		deleteStepButton.setEnabled(canEditSteps);
		editStepButton.setEnabled(canEditSteps);

		playButton.setEnabled(!rec && !play && hasActions);
		renameButton.setEnabled(!rec && !play && viewedRecording != null);
		deleteScriptButton.setEnabled(!rec && !play && viewedRecording != null);

		scriptSelector.setEnabled(!rec && !savedRecordings.isEmpty());

		if (rec && !appending)
		{
			recordButton.setText("■ Stop recording");
			recordButton.setEnabled(true);
			appendButton.setText("⊕");
			appendButton.setEnabled(false);
		}
		else if (rec)
		{
			recordButton.setText("● Record script");
			recordButton.setEnabled(false);
			appendButton.setText("■");
			appendButton.setEnabled(true);
		}
		else
		{
			recordButton.setText("● Record script");
			recordButton.setEnabled(!play);
			appendButton.setText("⊕");
			appendButton.setEnabled(!play && viewedRecording != null);
		}
	}

	private void refreshSelector()
	{
		suppressSelectorEvents = true;
		try
		{
			savedRecordings = plugin.listRecordings();
			scriptSelector.removeAllItems();

			if (savedRecordings.isEmpty())
			{
				scriptSelector.addItem(NO_SCRIPTS_PLACEHOLDER);
				scriptSelector.setSelectedIndex(0);
				viewedRecording = null;
				return;
			}

			for (Recording r : savedRecordings)
			{
				scriptSelector.addItem(r.getName());
			}

			String targetName = viewedRecording != null ? viewedRecording.getName() : null;
			int selectedIdx = 0;
			if (targetName != null)
			{
				for (int i = 0; i < savedRecordings.size(); i++)
				{
					if (targetName.equals(savedRecordings.get(i).getName()))
					{
						selectedIdx = i;
						break;
					}
				}
			}
			scriptSelector.setSelectedIndex(selectedIdx);
			viewedRecording = savedRecordings.get(selectedIdx);
		}
		finally
		{
			suppressSelectorEvents = false;
		}
	}

	private void onSelectorChanged()
	{
		int idx = scriptSelector.getSelectedIndex();
		if (idx >= 0 && idx < savedRecordings.size())
		{
			viewedRecording = savedRecordings.get(idx);
		}
		else
		{
			viewedRecording = null;
		}
		reloadActionList();
		updateButtonEnabled(plugin.isRecording(), plugin.isPlaying(), plugin.isAppendingToExisting());
	}

	private Recording getSelectedRecording()
	{
		if (plugin.isRecording())
		{
			return plugin.getCurrentRecording();
		}
		return viewedRecording;
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
			if (plugin.isRecording())
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
				viewedRecording = saved;
			}
			refresh();
		}
		else
		{
			actionsModel.clear();
			viewedRecording = null;
			plugin.startRecording(null);
			refresh();
		}
	}

	private void onAppendClicked()
	{
		if (plugin.isRecording())
		{
			plugin.stopRecording(true);
			refresh();
		}
		else
		{
			if (viewedRecording == null)
			{
				return;
			}
			plugin.startRecording(viewedRecording);
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

		String[] typeOptions = {"None", "HP", "Prayer", "NPC nearby", "Object nearby", "Inventory"};
		JComboBox<String> typeCombo = new JComboBox<>(typeOptions);
		JComboBox<String> statCmpCombo = new JComboBox<>(new String[]{"below", "above"});
		JSpinner statSpinner = new JSpinner(new SpinnerNumberModel(20, 0, 9999, 1));
		JTextField npcNameField = new JTextField(15);
		JComboBox<String> npcPresentCombo = new JComboBox<>(new String[]{"present", "absent"});
		JTextField objNameField = new JTextField(15);
		JComboBox<String> objPresentCombo = new JComboBox<>(new String[]{"present", "absent"});
		JTextField invNameField = new JTextField(15);
		JSpinner invCountSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 9999, 1));
		JComboBox<String> invPresentCombo = new JComboBox<>(new String[]{"present", "absent"});

		Condition existing = a.getCondition();
		if (existing != null && existing.getType() != null)
		{
			switch (existing.getType())
			{
				case STAT:
					typeCombo.setSelectedItem(existing.getStat() == StatKind.PRAYER ? "Prayer" : "HP");
					statCmpCombo.setSelectedItem(existing.getComparator() == ConditionComparator.ABOVE ? "above" : "below");
					if (existing.getThreshold() != null) statSpinner.setValue(existing.getThreshold());
					break;
				case NPC_NEARBY:
					typeCombo.setSelectedItem("NPC nearby");
					if (existing.getName() != null) npcNameField.setText(existing.getName());
					npcPresentCombo.setSelectedItem(Boolean.FALSE.equals(existing.getPresent()) ? "absent" : "present");
					break;
				case OBJECT_NEARBY:
					typeCombo.setSelectedItem("Object nearby");
					if (existing.getName() != null) objNameField.setText(existing.getName());
					objPresentCombo.setSelectedItem(Boolean.FALSE.equals(existing.getPresent()) ? "absent" : "present");
					break;
				case INVENTORY:
					typeCombo.setSelectedItem("Inventory");
					if (existing.getName() != null) invNameField.setText(existing.getName());
					if (existing.getMinCount() != null && existing.getMinCount() > 0) invCountSpinner.setValue(existing.getMinCount());
					invPresentCombo.setSelectedItem(Boolean.FALSE.equals(existing.getPresent()) ? "absent" : "present");
					break;
			}
		}

		JPanel statPanel = hBox(statCmpCombo, statSpinner);
		JPanel npcPanel = hBox(new JLabel("name:"), npcNameField, npcPresentCombo);
		JPanel objPanel = hBox(new JLabel("name:"), objNameField, objPresentCombo);
		JPanel invPanel = hBox(new JLabel("item:"), invNameField, new JLabel("min:"), invCountSpinner, invPresentCombo);

		CardLayout cardLayout = new CardLayout();
		JPanel cards = new JPanel(cardLayout);
		cards.add(new JPanel(), "None");
		cards.add(statPanel, "Stat");
		cards.add(npcPanel, "NPC nearby");
		cards.add(objPanel, "Object nearby");
		cards.add(invPanel, "Inventory");

		Runnable showCard = () ->
		{
			String sel = (String) typeCombo.getSelectedItem();
			String card;
			if ("HP".equals(sel) || "Prayer".equals(sel)) card = "Stat";
			else if (sel == null || "None".equals(sel)) card = "None";
			else card = sel;
			cardLayout.show(cards, card);
		};
		typeCombo.addActionListener(e -> showCard.run());
		showCard.run();

		JPanel form = new JPanel(new GridBagLayout());
		GridBagConstraints gc = new GridBagConstraints();
		gc.insets = new Insets(4, 4, 4, 4);
		gc.anchor = GridBagConstraints.WEST;

		gc.gridx = 0;
		JTextField verbField = new JTextField(a.getMenuOption() == null ? "" : a.getMenuOption(), 12);
		JTextField targetField = new JTextField(a.getTargetName() == null ? "" : a.getTargetName(), 15);

		gc.gridy = 0;
		form.add(new JLabel("Action:"), gc);
		gc.gridx = 1;
		form.add(verbField, gc);

		gc.gridx = 0;
		gc.gridy = 1;
		form.add(new JLabel("Target:"), gc);
		gc.gridx = 1;
		form.add(targetField, gc);

		gc.gridx = 0;
		gc.gridy = 2;
		form.add(new JLabel("Delay before (ticks):"), gc);
		gc.gridx = 1;
		form.add(ticksSpinner, gc);

		gc.gridx = 0;
		gc.gridy = 3;
		form.add(new JLabel("Condition:"), gc);
		gc.gridx = 1;
		form.add(typeCombo, gc);

		gc.gridx = 0;
		gc.gridy = 4;
		gc.gridwidth = 2;
		form.add(cards, gc);

		Object[] options = {"OK", "Delete", "Cancel"};
		int choice = JOptionPane.showOptionDialog(this, form, "Edit step #" + (idx + 1),
			JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
		if (choice == 1)
		{
			r.getActions().remove(idx);
			reloadActionList();
			persistIfSaved(r);
			return;
		}
		if (choice != 0)
		{
			return;
		}

		int newTicks = (Integer) ticksSpinner.getValue();
		a.setDelayTicksBefore(newTicks);
		String newVerb = verbField.getText().trim();
		a.setMenuOption(newVerb.isEmpty() ? null : newVerb);
		String newTarget = targetField.getText().trim();
		a.setTargetName(newTarget.isEmpty() ? null : newTarget);
		a.setCondition(buildCondition(typeCombo, statCmpCombo, statSpinner, npcNameField, npcPresentCombo,
			objNameField, objPresentCombo, invNameField, invCountSpinner, invPresentCombo));
		reloadActionList();
		actionsList.setSelectedIndex(idx);
		persistIfSaved(r);
	}

	private static JPanel hBox(Component... components)
	{
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
		for (int i = 0; i < components.length; i++)
		{
			if (i > 0) p.add(Box.createHorizontalStrut(4));
			p.add(components[i]);
		}
		return p;
	}

	private static Condition buildCondition(JComboBox<String> typeCombo, JComboBox<String> statCmpCombo, JSpinner statSpinner,
		JTextField npcNameField, JComboBox<String> npcPresentCombo,
		JTextField objNameField, JComboBox<String> objPresentCombo,
		JTextField invNameField, JSpinner invCountSpinner, JComboBox<String> invPresentCombo)
	{
		String sel = (String) typeCombo.getSelectedItem();
		if (sel == null || "None".equals(sel))
		{
			return null;
		}
		Condition c = new Condition();
		switch (sel)
		{
			case "HP":
			case "Prayer":
				c.setType(ConditionType.STAT);
				c.setStat("Prayer".equals(sel) ? StatKind.PRAYER : StatKind.HEALTH);
				c.setComparator("above".equals(statCmpCombo.getSelectedItem()) ? ConditionComparator.ABOVE : ConditionComparator.BELOW);
				c.setThreshold((Integer) statSpinner.getValue());
				return c;
			case "NPC nearby":
			{
				String nm = npcNameField.getText().trim();
				if (nm.isEmpty()) return null;
				c.setType(ConditionType.NPC_NEARBY);
				c.setName(nm);
				c.setPresent(!"absent".equals(npcPresentCombo.getSelectedItem()));
				return c;
			}
			case "Object nearby":
			{
				String nm = objNameField.getText().trim();
				if (nm.isEmpty()) return null;
				c.setType(ConditionType.OBJECT_NEARBY);
				c.setName(nm);
				c.setPresent(!"absent".equals(objPresentCombo.getSelectedItem()));
				return c;
			}
			case "Inventory":
			{
				String nm = invNameField.getText().trim();
				if (nm.isEmpty()) return null;
				c.setType(ConditionType.INVENTORY);
				c.setName(nm);
				c.setMinCount((Integer) invCountSpinner.getValue());
				c.setPresent(!"absent".equals(invPresentCombo.getSelectedItem()));
				return c;
			}
			default:
				return null;
		}
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

	private void onPlay()
	{
		Recording r = getSelectedRecording();
		if (r == null || r.size() == 0)
		{
			return;
		}
		plugin.play(r);
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
		String prefix = "";
		if (a.getCondition() != null)
		{
			String desc = a.getCondition().describe();
			if (!desc.isEmpty())
			{
				prefix = "[" + desc + "]  ";
			}
		}
		return prefix + a.describe() + "  (" + delay + ")";
	}
}
