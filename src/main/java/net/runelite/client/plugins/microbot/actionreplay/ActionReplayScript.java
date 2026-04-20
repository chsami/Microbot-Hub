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
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.Rectangle;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class ActionReplayScript extends Script
{
	private static final int PLAYBACK_SPEED_PERCENT = 100;
	private static final int MIN_STEP_DELAY_MS = 600;
	private static final boolean SKIP_MISSING_TARGETS = true;
	private static final int TARGET_LOOKUP_RADIUS = 20;

	private final AtomicBoolean abortFlag = new AtomicBoolean(false);
	private Recording recording;
	private boolean loop;
	private Runnable onFinished;
	private int currentIndex;
	private boolean priorNaturalMouse;

	public boolean play(Recording recording, ActionReplayConfig config, boolean loop, Runnable onFinished)
	{
		if (recording == null || recording.getActions() == null || recording.getActions().isEmpty())
		{
			log.warn("ActionReplay: recording is empty, nothing to play");
			return false;
		}
		this.recording = recording;
		this.loop = loop;
		this.onFinished = onFinished;
		this.abortFlag.set(false);

		priorNaturalMouse = Rs2AntibanSettings.naturalMouse;
		Rs2AntibanSettings.naturalMouse = true;
		log.info("ActionReplay: enabling naturalMouse for playback (was {})", priorNaturalMouse);

		mainScheduledFuture = scheduledExecutorService.schedule(this::playbackLoop, 0, TimeUnit.MILLISECONDS);
		return true;
	}

	public int getCurrentIndex()
	{
		return currentIndex;
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
			do
			{
				runOnce();
			}
			while (loop && !abortFlag.get());
		}
		catch (Exception e)
		{
			log.error("ActionReplay playback failed", e);
		}
		finally
		{
			Rs2AntibanSettings.naturalMouse = priorNaturalMouse;
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
			currentIndex = i;
			RecordedAction action = recording.getActions().get(i);

			Integer ticks = action.getDelayTicksBefore();
			long delayMs;
			if (ticks != null)
			{
				delayMs = ticks * 600L;
			}
			else
			{
				delayMs = Math.max(MIN_STEP_DELAY_MS, action.getDelayMsBefore());
			}
			long scaled = (delayMs * 100L) / PLAYBACK_SPEED_PERCENT;
			if (scaled > 0)
			{
				sleep((int) scaled);
			}

			if (!Microbot.isLoggedIn())
			{
				log.warn("ActionReplay: not logged in, aborting playback");
				return;
			}

			boolean ok = executeStep(action);
			if (!ok)
			{
				if (SKIP_MISSING_TARGETS)
				{
					log.warn("ActionReplay: skipping step #{} ({})", i, action.describe());
				}
				else
				{
					log.warn("ActionReplay: aborting playback at step #{} ({})", i, action.describe());
					return;
				}
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
		log.debug("ActionReplay: step {} {} (id={}, type={})", option, a.getTargetName(), a.getTargetId(), type);

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
		Rs2NpcModel match = null;
		if (a.getTargetId() != null)
		{
			match = Microbot.getRs2NpcCache().query()
				.withId(a.getTargetId())
				.nearest(TARGET_LOOKUP_RADIUS);
		}
		if (match == null && a.getTargetName() != null)
		{
			match = Microbot.getRs2NpcCache().query()
				.withName(a.getTargetName())
				.nearest(TARGET_LOOKUP_RADIUS);
		}
		if (match == null)
		{
			return false;
		}
		return Rs2Npc.interact(match.getId(), a.getMenuOption());
	}

	private boolean replayGameObject(RecordedAction a)
	{
		Rs2TileObjectModel match = null;
		if (a.getTargetId() != null)
		{
			match = Microbot.getRs2TileObjectCache().query()
				.withId(a.getTargetId())
				.nearest(TARGET_LOOKUP_RADIUS);
		}
		if (match == null && a.getTargetName() != null)
		{
			match = Microbot.getRs2TileObjectCache().query()
				.withName(a.getTargetName())
				.nearest(TARGET_LOOKUP_RADIUS);
		}
		if (match == null)
		{
			return Rs2GameObject.interact(a.getIdentifier(), a.getMenuOption());
		}
		return match.click(a.getMenuOption());
	}

	private boolean replayGroundItem(RecordedAction a)
	{
		if (a.getItemId() != 0)
		{
			Rs2TileItemModel match = Microbot.getRs2TileItemCache().query()
				.withId(a.getItemId())
				.nearest(TARGET_LOOKUP_RADIUS);
			if (match == null)
			{
				return false;
			}
			return Rs2GroundItem.loot(a.getItemId(), TARGET_LOOKUP_RADIUS);
		}
		if (a.getTargetName() != null)
		{
			return Rs2GroundItem.loot(a.getTargetName(), TARGET_LOOKUP_RADIUS);
		}
		return false;
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

		if (param1 > 0 && !Rs2Widget.isWidgetVisible(param1))
		{
			log.warn("ActionReplay: widget {} not visible, skipping '{}'", param1, a.describe());
			return false;
		}

		// Inventory item actions → delegate to Rs2Inventory.interact. It resolves the
		// correct inventory widget (normal/bank/deposit/GE/shop), finds the item's
		// current slot + bounds, and invokes with the correct params. Replaying a
		// stored NewMenuEntry directly is fragile: stale canvas coords mean the
		// physical click may land on an empty slot, at which point no MenuEntryAdded
		// fires and MicrobotPlugin's targetMenu injection silently no-ops.
		if (a.getItemId() > 0 && param1 > 0 && (param1 >>> 16) == InterfaceID.INVENTORY
			&& a.getMenuOption() != null && !a.getMenuOption().isEmpty())
		{
			if (!Rs2Inventory.hasItem(a.getItemId()))
			{
				log.warn("ActionReplay: item {} not in inventory, skipping '{}'", a.getItemId(), a.describe());
				return false;
			}
			return Rs2Inventory.interact(a.getItemId(), a.getMenuOption());
		}

		String target = a.getMenuTarget() != null ? a.getMenuTarget() : "";
		NewMenuEntry entry = new NewMenuEntry(
			a.getMenuOption(),
			target,
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
