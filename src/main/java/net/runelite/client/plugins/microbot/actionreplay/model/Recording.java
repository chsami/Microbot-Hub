package net.runelite.client.plugins.microbot.actionreplay.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class Recording
{
	private String name;
	private long lastUsedAtEpochMs;
	private List<RecordedAction> actions = new ArrayList<>();

	public int size()
	{
		return actions == null ? 0 : actions.size();
	}
}
