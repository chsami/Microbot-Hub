package net.runelite.client.plugins.microbot.cgprep;

final class LobbyDeposit
{
	static final String ENTRY_REFUSED = "you cannot keep any items";
	static final int MAX_TRIES = 3;

	private LobbyDeposit()
	{
	}

	static boolean isEntryRefusal(String message)
	{
		return message != null && message.toLowerCase().contains(ENTRY_REFUSED);
	}

	static boolean giveUp(int fails)
	{
		return fails >= MAX_TRIES;
	}

	static boolean isGameText(net.runelite.api.ChatMessageType type)
	{
		return type == net.runelite.api.ChatMessageType.GAMEMESSAGE
			|| type == net.runelite.api.ChatMessageType.SPAM
			|| type == net.runelite.api.ChatMessageType.ENGINE
			|| type == net.runelite.api.ChatMessageType.MESBOX
			|| type == net.runelite.api.ChatMessageType.DIALOG;
	}

	/**
	 * Use the deposit box now? With a reward waiting: only to make room for it (the 1.8.3 rule).
	 * Otherwise only after the game actually refused entry because of what's carried or worn
	 * (1.8.5, user: banking everything, gear included, before every single run looked scripted, and
	 * runs with loot in the bag were getting in fine).
	 */
	static boolean needed(boolean reward, int free, int bankBelowFreeSlots, int chestFails, int carried, boolean wearing, boolean refused)
	{
		if (reward)
		{
			return free < bankBelowFreeSlots || (chestFails >= 2 && free < 8);
		}
		return refused && (carried > 0 || wearing);
	}

	/** Worn items only go in the box after a refusal; making room for loot never touches your gear. */
	static boolean depositWorn(boolean refused, boolean wearing)
	{
		return refused && wearing;
	}
}
