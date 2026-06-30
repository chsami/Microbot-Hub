package net.runelite.client.plugins.microbot.agility;

import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.agentserver.handler.ScriptHeartbeatRegistry;
import net.runelite.client.plugins.microbot.agility.courses.AgilityCourseHandler;
import net.runelite.client.plugins.microbot.agility.courses.BrimhavenSpikeCourse;
import net.runelite.client.plugins.microbot.agility.courses.GnomeStrongholdCourse;
import net.runelite.client.plugins.microbot.agility.courses.PrifddinasCourse;
import net.runelite.client.plugins.microbot.agility.courses.WerewolfCourse;
import net.runelite.client.plugins.microbot.agility.enums.AgilityCourse;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import javax.inject.Inject;
import java.awt.EventQueue;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class AgilityScript extends Script
{

	final MicroAgilityPlugin plugin;
	final MicroAgilityConfig config;

	WorldPoint startPoint = null;
	int lastAgilityXp = 0;
	long lastTimeoutWarning = 0;  // For throttled timeout warnings
	private AgilityCourse activeCourse = null;
	private AgilityCourseHandler activeHandler = null;
	private static final int MARK_OF_GRACE_SEARCH_DISTANCE = 30;
	private static final int MARK_OF_GRACE_PICKUP_TIMEOUT = 5000;
	private WorldPoint pendingMarkOfGraceLocation = null;
	private int pendingMarkOfGraceCount = 0;
	private long pendingMarkOfGraceStartedAt = 0;
	private final AgilitySupplyManager supplyManager;
	private volatile boolean shuttingDown = false;

	@Inject
	public AgilityScript(MicroAgilityPlugin plugin, MicroAgilityConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		this.supplyManager = new AgilitySupplyManager(plugin, config, this::isShuttingDown, this::shutdown);
	}

	@Override
	public void shutdown()
	{
		shuttingDown = true;
		ScriptHeartbeatRegistry.remove(this.getClass().getName());
		if (activeHandler != null)
		{
			activeHandler.reset();
		}
		activeCourse = null;
		activeHandler = null;
		startPoint = null;
		initialPlayerLocation = null;
		supplyManager.reset();
		clearPendingMarkOfGrace();

		if (mainScheduledFuture != null && !mainScheduledFuture.isDone())
		{
			mainScheduledFuture.cancel(true);
		}
		if (scheduledFuture != null && !scheduledFuture.isDone())
		{
			scheduledFuture.cancel(true);
		}

		clearWalkingRouteForShutdown();
		if (Microbot.getClientThread().scheduledFuture != null)
		{
			Microbot.getClientThread().scheduledFuture.cancel(true);
		}
		Microbot.pauseAllScripts.set(false);
		Rs2Walker.disableTeleports = false;
		Microbot.getSpecialAttackConfigs().reset();
	}

	private void clearWalkingRouteForShutdown()
	{
		if (!EventQueue.isDispatchThread())
		{
			Rs2Walker.clearWalkingRoute("agility:shutdown");
			return;
		}

		Thread cleanupThread = new Thread(() -> Rs2Walker.clearWalkingRoute("agility:shutdown"), "AgilityScript-shutdown-cleanup");
		cleanupThread.setDaemon(true);
		cleanupThread.start();
	}

	public boolean run()
	{
		shuttingDown = false;
		Microbot.enableAutoRunOn = true;
		Rs2Antiban.resetAntibanSettings();
		Rs2Antiban.antibanSetupTemplates.applyAgilitySetup();
		AgilityCourseHandler initialHandler = getActiveHandler();
		startPoint = initialHandler.getStartPoint();
		lastAgilityXp = Microbot.getClient().getSkillExperience(Skill.AGILITY);
		mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
			try
			{
				if (!Microbot.isLoggedIn())
				{
					return;
				}
				if (!super.run())
				{
					return;
				}
				AgilityCourseHandler courseHandler = getActiveHandler();
				final WorldPoint playerWorldLocation = Microbot.getClientThread().invoke(() -> Microbot.getClient().getLocalPlayer().getWorldLocation());

				if (startPoint == null)
				{
					Microbot.log("Early return: Start point is null");
					Microbot.showMessage("Agility course: " + config.agilityCourse().getTooltip() + " is not supported.");
					sleep(10000);
					return;
				}

				if (supplyManager.handleSummerPies(courseHandler))
				{
					Microbot.log("Early return: Handling summer pies");
					return;
				}

				if (supplyManager.handleFoodOrHealthSafety())
				{
					Microbot.log("Early return: Handling agility safety");
					return;
				}

				boolean hasRequiredLevel = plugin.hasRequiredLevel();
				if (supplyManager.handlePreLevelCheck(courseHandler, playerWorldLocation, hasRequiredLevel))
				{
					Microbot.log("Early return: Banking agility supplies");
					return;
				}

				if (supplyManager.handleBeforeObstacle(courseHandler, playerWorldLocation))
				{
					Microbot.log("Early return: Handling agility supplies");
					return;
				}

				if (!plugin.hasRequiredLevel())
				{
					Microbot.log("Early return: Required level not met");
					Microbot.showMessage("You do not have the required level for this course.");
					shutdown();
					return;
				}

				// Check coin requirement for BrimhavenSpike course (only before payment)
				if (courseHandler instanceof BrimhavenSpikeCourse)
				{
					BrimhavenSpikeCourse course = (BrimhavenSpikeCourse) courseHandler;
					if (!course.hasPaid() && !course.hasRequiredCoins())
					{
						Microbot.log("Early return: Not enough coins for BrimhavenSpike course");
						Microbot.showMessage("You need 200 coins to enter the Brimhaven Spike course!");
						shutdown();
						return;
					}
				}
				if (Rs2AntibanSettings.actionCooldownActive)
				{
					Microbot.log("Early return: Action cooldown active");
					return;
				}
				final int currentAgilityXp = Microbot.getClient().getSkillExperience(Skill.AGILITY);

				if (lootMarksOfGrace(courseHandler))
				{
					Microbot.log("Early return: Looting marks of grace");
					return;
				}

				if (handleCourseSpecificActions(courseHandler, playerWorldLocation))
				{
					return;
				}
				final int agilityExp = Microbot.getClient().getSkillExperience(Skill.AGILITY);

				TileObject gameObject = courseHandler.getCurrentObstacle();

				if (gameObject == null)
				{
					Microbot.log("No agility obstacle found. Report this as a bug if this keeps happening.");
					return;
				}

				if (!Rs2Camera.isTileOnScreen(gameObject))
				{
					Rs2Walker.walkMiniMap(gameObject.getWorldLocation());
				}

				// Check if we should click (handles animation/XP logic)
				if (!courseHandler.shouldClickObstacle(currentAgilityXp, lastAgilityXp))
				{
					return; // Not ready to click yet
				}
				
				// Update XP if we got it while animating
				if (currentAgilityXp > lastAgilityXp)
				{
					lastAgilityXp = currentAgilityXp;
				}

				// Handle alchemy if enabled
				if (shouldPerformAlch())
				{
					Optional<String> alchItem = getAlchItem();
					if (alchItem.isPresent())
					{
						// Check if we should skip inefficient alchs
						if (config.skipInefficient())
						{
							// Only alch if obstacle is far enough for efficient alching
							if (gameObject.getWorldLocation().distanceTo(playerWorldLocation) >= 5)
							{
								if (config.efficientAlching())
								{
									if (performEfficientAlch(gameObject, alchItem.get(), agilityExp))
									{
										return;
									}
								}
								else
								{
									// Still do normal alch if far enough but efficient alching is disabled
									performNormalAlch(alchItem.get());
								}
							}
							// Skip alching if obstacle is too close
						}
						else
						{
							// Normal behavior when skipInefficient is disabled
							if (config.efficientAlching())
							{
								if (performEfficientAlch(gameObject, alchItem.get(), agilityExp))
								{
									return;
								}
							}
							// Fall back to normal alching
							performNormalAlch(alchItem.get());
						}
					}
				}
				
				// Normal obstacle interaction
				if (interactWithObstacle(gameObject)) {
					// Wait for completion - this now returns quickly on XP drop
					boolean completed = courseHandler.waitForCompletion(agilityExp,
						Microbot.getClientThread().invoke(() -> Microbot.getClient().getLocalPlayer().getWorldLocation()).getPlane());
					
					if (!completed) {
						// Timeout occurred - log warning (throttled to once per 30 seconds)
						long now = System.currentTimeMillis();
						if (now - lastTimeoutWarning > 30000) {
							Microbot.log("Obstacle completion timed out - retrying on next iteration");
							lastTimeoutWarning = now;
						}
						return;  // Bail early to avoid acting on stale state
					}
					
					// XP tracking is already updated before clicking (line 137)
					// Don't update here to avoid losing early action state
					
					// If we're still animating after XP, don't add delays - proceed immediately
					if (!Rs2Player.isAnimating() && !Rs2Player.isMoving()) {
						// Only add delays if we're not animating
						Rs2Antiban.actionCooldown();
						Rs2Antiban.takeMicroBreakByChance();
					}
				}
			}
			catch (Exception ex)
			{
				if (isExpectedShutdownInterrupt(ex))
				{
					return;
				}
				Microbot.log("An error occurred: " + ex.getMessage(), ex);
			}
		}, 0, 100, TimeUnit.MILLISECONDS);
		return true;
	}

	public boolean isShuttingDown()
	{
		return shuttingDown;
	}

	private boolean isExpectedShutdownInterrupt(Exception ex)
	{
		if (!shuttingDown)
		{
			return false;
		}

		if (Thread.currentThread().isInterrupted())
		{
			return true;
		}

		Throwable current = ex;
		while (current != null)
		{
			if (current instanceof InterruptedException)
			{
				return true;
			}
			if (current.getMessage() != null && current.getMessage().contains("Interrupted waiting for client thread"))
			{
				return true;
			}
			current = current.getCause();
		}

		return false;
	}

	private Optional<String> getAlchItem()
	{
		String itemsInput = config.itemsToAlch().trim();
		if (itemsInput.isEmpty())
		{
			// Microbot.log("No items specified for alching or none available.");
			return Optional.empty();
		}

		List<String> itemsToAlch = Arrays.stream(itemsInput.split(","))
			.map(String::trim)
			.map(String::toLowerCase)
			.filter(s -> !s.isEmpty())
			.collect(Collectors.toList());

		if (itemsToAlch.isEmpty())
		{
			// Microbot.log("No valid items specified for alching.");
			return Optional.empty();
		}

		for (String itemName : itemsToAlch)
		{
			if (Rs2Inventory.hasItem(itemName))
			{
				return Optional.of(itemName);
			}
		}

		return Optional.empty();
	}

	private AgilityCourseHandler getActiveHandler()
	{
		AgilityCourse selectedCourse = config.agilityCourse();
		if (activeHandler == null || activeCourse != selectedCourse)
		{
			if (activeHandler != null)
			{
				activeHandler.reset();
			}

			activeCourse = selectedCourse;
			activeHandler = selectedCourse.getHandler();
			activeHandler.reset();
			startPoint = activeHandler.getStartPoint();
			lastAgilityXp = Microbot.getClient().getSkillExperience(Skill.AGILITY);
			supplyManager.reset();
		}
		return activeHandler;
	}

	private boolean lootMarksOfGrace(AgilityCourseHandler courseHandler)
	{
		if (shuttingDown)
		{
			clearPendingMarkOfGrace();
			return false;
		}

		if (pendingMarkOfGraceLocation != null)
		{
			if (markPickupResolved() || System.currentTimeMillis() - pendingMarkOfGraceStartedAt > MARK_OF_GRACE_PICKUP_TIMEOUT)
			{
				clearPendingMarkOfGrace();
			}
			else if (Rs2Player.isMoving() || Rs2Player.isAnimating())
			{
				return true;
			}
			else
			{
				clearPendingMarkOfGrace();
			}
		}

		if (Rs2Inventory.isFull() && !Rs2Inventory.contains(ItemID.GRACE))
		{
			return false;
		}

		WorldPoint playerLocation = courseHandler.getPlayerWorldLocation();
		if (playerLocation == null)
		{
			return false;
		}

		Rs2TileItemModel markOfGrace = Microbot.getRs2TileItemCache().query()
			.fromWorldView()
			.withId(ItemID.GRACE)
			.where(Rs2TileItemModel::isLootAble)
			.where(item -> item.getWorldLocation() != null && item.getWorldLocation().getPlane() == playerLocation.getPlane())
			.where(item -> item.getWorldLocation().distanceTo(playerLocation) <= MARK_OF_GRACE_SEARCH_DISTANCE)
			.where(item -> Rs2Walker.canReach(item.getWorldLocation()))
			.toList()
			.stream()
			.min(Comparator.comparingInt(item -> item.getWorldLocation().distanceTo(playerLocation)))
			.orElse(null);

		if (markOfGrace == null)
		{
			return false;
		}

		if (Rs2Player.isMoving() || Rs2Player.isAnimating())
		{
			return true;
		}

		WorldPoint markLocation = markOfGrace.getWorldLocation();
		var markLocalLocation = markOfGrace.getLocalLocation();
		if (markLocation == null || markLocalLocation == null)
		{
			return false;
		}

		if (!Rs2Camera.isTileOnScreen(markLocalLocation))
		{
			Rs2Camera.turnTo(markLocalLocation);
			sleep(300, 600);
			return true;
		}

		int markCount = Rs2Inventory.itemQuantity(ItemID.GRACE);
		if (!markOfGrace.pickup())
		{
			return false;
		}
		pendingMarkOfGraceLocation = markLocation;
		pendingMarkOfGraceCount = markCount;
		pendingMarkOfGraceStartedAt = System.currentTimeMillis();

		sleepUntil(() -> shuttingDown || markPickupResolved() || Rs2Player.isMoving(), 1800);
		if (markPickupResolved() || shuttingDown)
		{
			clearPendingMarkOfGrace();
		}
		return true;
	}

	private boolean markPickupResolved()
	{
		return pendingMarkOfGraceLocation != null
			&& (Rs2Inventory.itemQuantity(ItemID.GRACE) > pendingMarkOfGraceCount
			|| !hasLootableMarkAt(pendingMarkOfGraceLocation));
	}

	private void clearPendingMarkOfGrace()
	{
		pendingMarkOfGraceLocation = null;
		pendingMarkOfGraceCount = 0;
		pendingMarkOfGraceStartedAt = 0;
	}

	private boolean hasLootableMarkAt(WorldPoint markLocation)
	{
		return Microbot.getRs2TileItemCache().query()
			.fromWorldView()
			.withId(ItemID.GRACE)
			.where(Rs2TileItemModel::isLootAble)
			.where(item -> markLocation.equals(item.getWorldLocation()))
			.first() != null;
	}

	private boolean shouldPerformAlch()
	{
		if (!config.alchemy())
		{
			return false;
		}
		
		// Check if we should skip alching based on configured chance
		if (Math.random() * 100 < config.alchSkipChance())
		{
			return false;
		}
		
		return true;
	}

	private boolean performEfficientAlch(TileObject gameObject, String alchItem, int agilityExp)
	{
		WorldPoint playerLocation = Microbot.getClientThread().invoke(() -> Microbot.getClient().getLocalPlayer().getWorldLocation());

		if (gameObject.getWorldLocation().distanceTo(playerLocation) >= 5)
		{
			// Efficient alching: click, alch, click
			if (interactWithObstacle(gameObject))
			{
				sleep(100, 200);
				Rs2Magic.alch(alchItem, 50, 75);
				interactWithObstacle(gameObject);
				boolean completed = getActiveHandler().waitForCompletion(agilityExp,
					Microbot.getClientThread().invoke(() -> Microbot.getClient().getLocalPlayer().getWorldLocation()).getPlane());

				if (!completed) {
					// Timeout during efficient alching - log warning
					long now = System.currentTimeMillis();
					if (now - lastTimeoutWarning > 30000) {
						Microbot.log("Obstacle completion timed out during efficient alching");
						lastTimeoutWarning = now;
					}
					return false;  // Return false to indicate alch sequence failed
				}
				
				Rs2Antiban.actionCooldown();
				Rs2Antiban.takeMicroBreakByChance();
				lastAgilityXp = Microbot.getClient().getSkillExperience(Skill.AGILITY);
				return true;
			}
		}
		return false;
	}

	private void performNormalAlch(String alchItem)
	{
		// Simple alch - waitForCompletion handles all timing
		Rs2Magic.alch(alchItem, 50, 75);
	}

	private boolean handleCourseSpecificActions(AgilityCourseHandler courseHandler, WorldPoint playerWorldLocation)
	{
		if (courseHandler instanceof PrifddinasCourse)
		{
			PrifddinasCourse course = (PrifddinasCourse) courseHandler;
			return course.handlePortal() || course.handleWalkToStart(playerWorldLocation);
		}
		else if (courseHandler instanceof WerewolfCourse)
		{
			WerewolfCourse course = (WerewolfCourse) courseHandler;
			return course.handleFirstSteppingStone(playerWorldLocation)
				|| course.handleStickPickup(playerWorldLocation)
				|| course.handleSlide()
				|| course.handleStickReturn(playerWorldLocation);
		}
		else if (courseHandler instanceof BrimhavenSpikeCourse)
		{
			BrimhavenSpikeCourse course = (BrimhavenSpikeCourse) courseHandler;
			boolean result = course.handleWalkToStart(playerWorldLocation);
			return result;
		}
		else if (!(courseHandler instanceof GnomeStrongholdCourse))
		{
			return courseHandler.handleWalkToStart(playerWorldLocation);
		}
		return false;
	}

	private boolean interactWithObstacle(TileObject gameObject)
	{
		return Rs2GameObject.interact(gameObject);
	}
}
