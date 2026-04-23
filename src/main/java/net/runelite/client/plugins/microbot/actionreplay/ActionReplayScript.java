package net.runelite.client.plugins.microbot.actionreplay;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.MenuAction;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.actionreplay.model.RecordedAction;
import net.runelite.client.plugins.microbot.actionreplay.model.Recording;
import net.runelite.client.plugins.microbot.actionreplay.model.TargetType;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.Rectangle;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class ActionReplayScript extends Script
{
	private static final int TARGET_LOOKUP_RADIUS = 20;

	private final AtomicBoolean abortFlag = new AtomicBoolean(false);
	private Recording recording;
	private Runnable onFinished;

	public boolean play(Recording recording, Runnable onFinished)
	{
		if (recording == null || recording.getActions() == null || recording.getActions().isEmpty())
		{
			log.warn("ActionReplay: recording is empty, nothing to play");
			return false;
		}
		this.recording = recording;
		this.onFinished = onFinished;
		this.abortFlag.set(false);

		log.info("ActionReplay: starting playback of '{}' ({} actions)", recording.getName(), recording.size());

		mainScheduledFuture = scheduledExecutorService.schedule(this::playbackLoop, 0, TimeUnit.MILLISECONDS);
		return true;
	}

	@Override
	public void shutdown()
	{
		abortFlag.set(true);
		super.shutdown();
	}

	private void playbackLoop()
	{
		try
		{
			while (!abortFlag.get())
			{
				runOnce();
			}
		}
		catch (RuntimeException e)
		{
			if (e.getCause() instanceof InterruptedException || abortFlag.get())
			{
				log.info("ActionReplay: playback stopped");
			}
			else
			{
				log.error("ActionReplay playback failed", e);
			}
		}
		finally
		{
			Runnable cb = onFinished;
			onFinished = null;
			if (cb != null)
			{
				cb.run();
			}
		}
	}

	private void runOnce()
	{
		for (int i = 0; i < recording.getActions().size(); i++)
		{
			if (abortFlag.get())
			{
				return;
			}
			RecordedAction action = recording.getActions().get(i);

			Integer ticks = action.getDelayTicksBefore();
			if (ticks != null && ticks > 0)
			{
				sleep(ticks * 600);
			}

			if (!Microbot.isLoggedIn())
			{
				log.warn("ActionReplay: not logged in, aborting playback");
				return;
			}

			if (action.getCondition() != null && !action.getCondition().check())
			{
				log.info("ActionReplay: skipping step #{} ({}) — condition '{}' false",
					i, action.describe(), action.getCondition().describe());
				continue;
			}

			if (!executeStep(action))
			{
				log.warn("ActionReplay: skipping step #{} ({})", i, action.describe());
			}
		}
	}

	private boolean executeStep(RecordedAction a)
	{
		TargetType type = a.getTargetType();
		if (type == null)
		{
			type = TargetType.UNKNOWN;
		}

		String option = a.getMenuOption();
		log.debug("ActionReplay: step {} {} (type={})", option, a.getTargetName(), type);

		switch (type)
		{
			case NPC:
				return replayNpc(a);
			case GAME_OBJECT:
				return replayGameObject(a);
			case GROUND_ITEM:
				return replayGroundItem(a);
			case WIDGET:
			case WALK:
			case PLAYER:
			case UNKNOWN:
			default:
				return replayRaw(a);
		}
	}

	private boolean replayNpc(RecordedAction a)
	{
		if (a.getTargetName() == null)
		{
			return false;
		}
Rs2NpcModel match = Microbot.getRs2NpcCache().query()
			.withName(a.getTargetName())
			.nearestOnClientThread(TARGET_LOOKUP_RADIUS);
		if (match == null)
		{
			return false;
		}
		return Rs2Npc.interact(match.getId(), a.getMenuOption());
	}

	private boolean replayGameObject(RecordedAction a)
	{
		if (a.getTargetName() == null)
		{
			log.warn("ActionReplay: no target name for game object step, skipping '{}'", a.describe());
			return false;
		}
Rs2TileObjectModel match = Microbot.getRs2TileObjectCache().query()
			.withName(a.getTargetName())
			.nearestOnClientThread(TARGET_LOOKUP_RADIUS);
		if (match == null)
		{
			log.warn("ActionReplay: no '{}' within {} tiles, skipping '{}'",
				a.getTargetName(), TARGET_LOOKUP_RADIUS, a.describe());
			return false;
		}
		return match.click(a.getMenuOption());
	}

	private boolean replayGroundItem(RecordedAction a)
	{
		if (a.getTargetName() == null)
		{
			return false;
		}
		return Rs2GroundItem.loot(a.getTargetName(), TARGET_LOOKUP_RADIUS);
	}

	private boolean replayRaw(RecordedAction a)
	{
		MenuAction ma = parseMenuAction(a.getMenuAction());
		if (ma == null)
		{
			log.warn("ActionReplay: unknown MenuAction '{}', cannot replay raw step", a.getMenuAction());
			return false;
		}

		int param1 = a.getParam1();

		if ((param1 >>> 16) > 0 && !Rs2Widget.isWidgetVisible(param1))
		{
			log.warn("ActionReplay: widget {} not visible, skipping '{}'", param1, a.describe());
			return false;
		}

		if (a.getItemId() > 0 && param1 > 0 && (param1 >>> 16) == InterfaceID.INVENTORY
			&& a.getMenuOption() != null && !a.getMenuOption().isEmpty())
		{
			String itemName = a.getTargetName();
			if (itemName == null || itemName.isEmpty())
			{
				log.warn("ActionReplay: no item name for inventory step, skipping '{}'", a.describe());
				return false;
			}
			if (!Rs2Inventory.hasItem(itemName, true))
			{
				log.warn("ActionReplay: item '{}' not in inventory, skipping '{}'", itemName, a.describe());
				return false;
			}
			return Rs2Inventory.interact(itemName, a.getMenuOption(), true);
		}

		NewMenuEntry entry = new NewMenuEntry(
			a.getMenuOption(),
			a.getTargetName() != null ? a.getTargetName() : "",
			a.getIdentifier(),
			ma,
			a.getParam0(),
			param1,
			false
		);
		entry.setItemId(a.getItemId());
		try
		{
			Rectangle rect = buildClickRect(a);
			Microbot.doInvoke(entry, rect);
			return true;
		}
		catch (Exception e)
		{
			log.warn("ActionReplay: doInvoke failed for step {}: {}", a.describe(), e.getMessage());
			return false;
		}
	}

	private Rectangle buildClickRect(RecordedAction a)
	{
		if (a.getCanvasX() > 0 && a.getCanvasY() > 0)
		{
			return new Rectangle(a.getCanvasX() - 2, a.getCanvasY() - 2, 4, 4);
		}
		return new Rectangle(1, 1);
	}

	private MenuAction parseMenuAction(String s)
	{
		if (s == null || s.isEmpty())
		{
			return null;
		}
		try
		{
			return MenuAction.valueOf(s);
		}
		catch (IllegalArgumentException ex)
		{
			return null;
		}
	}

	public boolean isPlaying()
	{
		return mainScheduledFuture != null && !mainScheduledFuture.isDone() && !abortFlag.get();
	}
}
