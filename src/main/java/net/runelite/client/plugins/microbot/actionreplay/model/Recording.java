package net.runelite.client.plugins.microbot.actionreplay.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class Recording
{
	public static final int CURRENT_VERSION = 1;

	private int version = CURRENT_VERSION;
	private String name;
	private long createdAtEpochMs;
	private long lastUsedAtEpochMs;
	private List<RecordedAction> actions = new ArrayList<>();

	public int size()
	{
		return actions == null ? 0 : actions.size();
	}
}
