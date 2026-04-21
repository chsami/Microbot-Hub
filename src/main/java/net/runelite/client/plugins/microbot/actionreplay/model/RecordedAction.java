package net.runelite.client.plugins.microbot.actionreplay.model;

import lombok.Data;

@Data
public class RecordedAction
{
	private Integer delayTicksBefore;
	private String menuOption;
	private String menuAction;
	private TargetType targetType;
	private int identifier;
	private int param0;
	private int param1;
	private int itemId;
	private String targetName;
	private int canvasX;
	private int canvasY;
	private Condition condition;

	public String describe()
	{
		if (targetName == null || targetName.isEmpty())
		{
			return menuOption;
		}
		return menuOption + " → " + targetName;
	}
}
