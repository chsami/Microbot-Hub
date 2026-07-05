package net.runelite.client.plugins.microbot.agility;

import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.ItemComposition;
import net.runelite.api.MenuAction;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.agentserver.handler.ScriptHeartbeatRegistry;
import net.runelite.client.plugins.microbot.agility.courses.AgilityCourseHandler;
import net.runelite.client.plugins.microbot.agility.enums.AgilityCourse;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.reflection.Rs2Reflection;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import javax.inject.Inject;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.EventQueue;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
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
	private static final long MAIN_LOOP_DELAY_MS = 250;
	private static final long MARK_OF_GRACE_SCAN_INTERVAL_MS = 750;
	private static final int MARK_OF_GRACE_SEARCH_DISTANCE = 30;
	private static final int MARK_OF_GRACE_PICKUP_TIMEOUT = 5000;
	private volatile int currentObstacleIndex = -1;
	private WorldPoint pendingMarkOfGraceLocation = null;
	private int pendingMarkOfGraceCount = 0;
	private long pendingMarkOfGraceStartedAt = 0;
	private long lastMarkOfGraceScanAt = 0;
	private WorldPoint alchDecisionObstacleLocation = null;
	private int alchDecisionObstacleId = -1;
	private boolean alchDecisionShouldAlch = false;
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
		currentObstacleIndex = -1;
		supplyManager.reset();
		clearPendingMarkOfGrace();
		clearAlchDecision();

		if (mainScheduledFuture != null && !mainScheduledFuture.isDone())
		{
			mainScheduledFuture.cancel(true);
		}
		if (scheduledFuture != null && !scheduledFuture.isDone())
		{
			scheduledFuture.cancel(true);
		}

		clearWalkingRouteForShutdown();
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

				boolean hasRequiredLevel = plugin.hasRequiredLevel(courseHandler);
				currentObstacleIndex = courseHandler.getCurrentObstacleIndex();
				AgilitySupplyManager.InventorySnapshot inventorySnapshot = supplyManager.createInventorySnapshot();
				if (supplyManager.handleSummerPies(courseHandler, playerWorldLocation, currentObstacleIndex, inventorySnapshot))
				{
					Microbot.log("Early return: Handling summer pies");
					return;
				}

				if (supplyManager.handleFoodOrHealthSafety(inventorySnapshot))
				{
					Microbot.log("Early return: Handling agility safety");
					return;
				}

				if (supplyManager.handlePreLevelCheck(courseHandler, playerWorldLocation, currentObstacleIndex, hasRequiredLevel, inventorySnapshot))
				{
					Microbot.log("Early return: Banking agility supplies");
					return;
				}

				if (supplyManager.handleBeforeObstacle(courseHandler, playerWorldLocation, currentObstacleIndex, inventorySnapshot))
				{
					Microbot.log("Early return: Handling agility supplies");
					return;
				}

				if (!hasRequiredLevel)
				{
					if (supplyManager.shouldWalkToCourseStartForSummerPie(courseHandler, playerWorldLocation, currentObstacleIndex)
						&& courseHandler.handleCourseActions(playerWorldLocation))
					{
						return;
					}
					Microbot.log("Early return: Required level not met");
					plugin.notifyUser("Your Agility level is too low for " + config.agilityCourse().getTooltip() + ". Select another course, or enable summer pies if a +5 boost is enough.");
					shutdown();
					return;
				}

				if (!courseHandler.hasRequiredCourseItems())
				{
					Microbot.log("Early return: Missing required course items");
					Microbot.showMessage(courseHandler.getMissingRequiredCourseItemsMessage());
					shutdown();
					return;
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

				if (courseHandler.handleCourseActions(playerWorldLocation))
				{
					return;
				}
				final int agilityExp = currentAgilityXp;

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
				if (shouldPerformAlch(gameObject))
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
									if (performNormalAlch(alchItem.get()))
									{
										return;
									}
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
							if (performNormalAlch(alchItem.get()))
							{
								return;
							}
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
					clearAlchDecision();
					
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
		}, 0, MAIN_LOOP_DELAY_MS, TimeUnit.MILLISECONDS);
		return true;
	}

	public boolean isShuttingDown()
	{
		return shuttingDown;
	}

	public int getCurrentObstacleIndex()
	{
		return currentObstacleIndex;
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
			currentObstacleIndex = -1;
			supplyManager.reset();
			clearAlchDecision();
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

		long now = System.currentTimeMillis();
		if (now - lastMarkOfGraceScanAt < MARK_OF_GRACE_SCAN_INTERVAL_MS)
		{
			return false;
		}
		lastMarkOfGraceScanAt = now;

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
		if (!pickupMarkOfGrace(markOfGrace))
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
		lastMarkOfGraceScanAt = 0;
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

	private boolean pickupMarkOfGrace(Rs2TileItemModel markOfGrace)
	{
		try
		{
			ItemComposition item = Microbot.getClientThread()
				.runOnClientThreadOptional(() -> Microbot.getClient().getItemDefinition(markOfGrace.getId()))
				.orElse(null);
			if (item == null)
			{
				return false;
			}

			LocalPoint localPoint = markOfGrace.getLocalLocation();
			if (localPoint == null)
			{
				return false;
			}

			MenuAction menuAction = getGroundItemMenuAction(item, "Take");
			if (menuAction == null)
			{
				return false;
			}

			if (!Rs2Camera.isTileOnScreen(localPoint))
			{
				Rs2Camera.turnTo(localPoint);
			}

			Polygon canvasTile = Perspective.getCanvasTilePoly(Microbot.getClient(), localPoint);
			Rectangle clickBounds = canvasTile == null
				? new Rectangle(1, 1, Microbot.getClient().getCanvasWidth(), Microbot.getClient().getCanvasHeight())
				: canvasTile.getBounds();

			Microbot.doInvoke(new NewMenuEntry()
					.param0(localPoint.getSceneX())
					.param1(localPoint.getSceneY())
					.opcode(menuAction.getId())
					.identifier(markOfGrace.getId())
					.itemId(-1)
					.option("Take")
					.target("<col=ff9040>" + item.getName())
					.worldViewId(localPoint.getWorldView()),
				clickBounds);
			return true;
		}
		catch (Exception ex)
		{
			Microbot.log("Failed to pick up Mark of grace: " + ex.getMessage());
			return false;
		}
	}

	private MenuAction getGroundItemMenuAction(ItemComposition item, String action)
	{
		String[] groundActions = Rs2Reflection.getGroundItemActions(item);
		for (int i = 0; i < groundActions.length; i++)
		{
			String groundAction = groundActions[i];
			if (groundAction != null && groundAction.equalsIgnoreCase(action))
			{
				return groundItemMenuAction(i);
			}
		}
		return null;
	}

	private MenuAction groundItemMenuAction(int index)
	{
		switch (index)
		{
			case 0:
				return MenuAction.GROUND_ITEM_FIRST_OPTION;
			case 1:
				return MenuAction.GROUND_ITEM_SECOND_OPTION;
			case 2:
				return MenuAction.GROUND_ITEM_THIRD_OPTION;
			case 3:
				return MenuAction.GROUND_ITEM_FOURTH_OPTION;
			case 4:
				return MenuAction.GROUND_ITEM_FIFTH_OPTION;
			default:
				return null;
		}
	}

	private boolean shouldPerformAlch(TileObject gameObject)
	{
		if (!config.alchemy())
		{
			clearAlchDecision();
			return false;
		}

		WorldPoint obstacleLocation = gameObject.getWorldLocation();
		if (gameObject.getId() == alchDecisionObstacleId && obstacleLocation.equals(alchDecisionObstacleLocation))
		{
			return alchDecisionShouldAlch;
		}

		alchDecisionObstacleId = gameObject.getId();
		alchDecisionObstacleLocation = obstacleLocation;
		alchDecisionShouldAlch = ThreadLocalRandom.current().nextInt(100) >= config.alchSkipChance();
		return alchDecisionShouldAlch;
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
				clearAlchDecision();
				return true;
			}
		}
		return false;
	}

	private boolean performNormalAlch(String alchItem)
	{
		int initialCount = Rs2Inventory.itemQuantity(alchItem);
		Rs2Magic.alch(alchItem, 50, 75);
		sleepUntil(() -> shuttingDown || Rs2Inventory.itemQuantity(alchItem) < initialCount, 1200);
		alchDecisionShouldAlch = false;
		return true;
	}

	private void clearAlchDecision()
	{
		alchDecisionObstacleLocation = null;
		alchDecisionObstacleId = -1;
		alchDecisionShouldAlch = false;
	}

	private boolean interactWithObstacle(TileObject gameObject)
	{
		return Rs2GameObject.interact(gameObject);
	}
}
