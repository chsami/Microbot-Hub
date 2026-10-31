package net.runelite.client.plugins.microbot.cgprep.handover;

import java.util.ArrayList;
import java.util.List;
import net.runelite.api.ChatMessageType;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.cgprep.data.GearSlot;
import net.runelite.client.plugins.microbot.cgprep.data.PrepProfile;
import net.runelite.client.plugins.microbot.cgprep.plan.Ledger;

/**
 * Builds and delivers the "setup complete" message: time left first, then loadout, then anything
 * that came out worse than planned. Delivered as a chat line, an optional popup and (with "Notify on
 * win / death" on) a desktop notification, so it reaches the player even when tabbed out.
 */
public final class Handover
{
	private volatile String bannerText = "";
	private volatile List<String> lines = new ArrayList<>();
	private volatile boolean active;

	public void clear()
	{
		active = false;
		bannerText = "";
		lines = new ArrayList<>();
	}

	public boolean isActive()
	{
		return active;
	}

	public String banner()
	{
		return bannerText;
	}

	public List<String> lines()
	{
		return lines;
	}

	public void announce(Ledger l, PrepProfile p, int secondsLeft, boolean popup, boolean notify)
	{
		List<String> out = new ArrayList<>();
		String time = secondsLeft < 0 ? "unknown" : (secondsLeft / 60) + ":" + String.format("%02d", secondsLeft % 60);
		out.add("TIME LEFT: " + time + " (auto-teleport into the boss at 0:00)");
		out.add("Armour: helm T" + l.tier.get(GearSlot.HELM) + ", body T" + l.tier.get(GearSlot.BODY) + ", legs T" + l.tier.get(GearSlot.LEGS));
		out.add("Weapons: bow T" + l.tier.get(GearSlot.BOW) + ", staff T" + l.tier.get(GearSlot.STAFF)
			+ (l.tier.get(GearSlot.HALBERD) > 0 ? ", halberd T" + l.tier.get(GearSlot.HALBERD) : ""));
		out.add("Food: " + l.cookedFish + " paddlefish | Potions: " + l.potions + " (" + l.potionDoses + " doses)");
		out.add("Prayers are OFF (full points). Boss starts with RANGED: Protect from Missiles first.");

		for (GearSlot s : GearSlot.values())
		{
			int want = p.targetTier.getOrDefault(s, 0);
			int have = l.tier.getOrDefault(s, 0);
			if (have < want)
			{
				out.add("WARNING: " + s + " is T" + have + " (wanted T" + want + ")"
					+ (s == GearSlot.STAFF ? " - use the bow on the Magic overhead" : "")
					+ (s == GearSlot.BOW ? " - use the staff on the Ranged overhead" : ""));
			}
		}
		if (l.cookedFish < 8)
		{
			out.add("WARNING: low food (" + l.cookedFish + ")");
		}

		lines = out;
		bannerText = "PREP COMPLETE - " + time + " left - your turn";
		active = true;

		String html = String.join("<br>", out);
		String plain = String.join(" | ", out);
		Microbot.getClientThread().invoke(() ->
		{
			Microbot.getClient().addChatMessage(ChatMessageType.GAMEMESSAGE, "",
				"<col=ff9040>[Auto Gauntlet]</col> Setup complete. " + plain, null);
			if (popup)
			{
				Microbot.openPopUp("Auto Gauntlet: prep complete", "<col=ffffff>" + html + "</col>");
			}
		});
		// Prep Only: a tabbed-out player has to know it's their turn before the timer runs out
		// (review). Gated by "Notify on win / death".
		if (notify && Microbot.getNotifier() != null)
		{
			Microbot.getNotifier().notify("Auto Gauntlet: prep done, " + time + " left. Your turn: go through the boss barrier.");
		}
	}
}
