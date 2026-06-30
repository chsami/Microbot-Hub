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
import net.runelite.client.plugins.microbot.agility.enums.AgilityFoodOption;
import net.runelite.client.plugins.microbot.agility.enums.AgilityPotionOption;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.misc.Rs2Food;
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
	private static final long BANK_RETRY_COOLDOWN_MS = 30_000;
	private static final int SUMMER_PIE_ID = ItemID.SUMMER_PIE;
	private static final int[] STAMINA_POTION_IDS = {
		ItemID._4DOSESTAMINA,
		ItemID._3DOSESTAMINA,
		ItemID._2DOSESTAMINA,
		ItemID._1DOSESTAMINA
	};
	private WorldPoint pendingMarkOfGraceLocation = null;
	private int pendingMarkOfGraceCount = 0;
	private long pendingMarkOfGraceStartedAt = 0;
	private long lastBankFailureAt = 0;
	private boolean lastFoodBankUnavailable = false;
	private volatile boolean shuttingDown = false;

	@Inject
	public AgilityScript(MicroAgilityPlugin plugin, MicroAgilityConfig config)
	{
		this.plugin = plugin;
		this.config = config;
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
		lastBankFailureAt = 0;
		lastFoodBankUnavailable = false;
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

				if (handleSummerPies(courseHandler))
				{
					Microbot.log("Early return: Handling summer pies");
					return;
				}

				if (!plugin.hasRequiredLevel() && handleBanking(courseHandler, playerWorldLocation))
				{
					Microbot.log("Early return: Banking agility supplies");
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

				if (handleFood())
				{
					Microbot.log("Early return: Handling food");
					return;
				}
				if (handleBanking(courseHandler, playerWorldLocation))
				{
					Microbot.log("Early return: Banking agility supplies");
					return;
				}
				if (handleHealthSafety())
				{
					Microbot.log("Early return: Waiting for safe hitpoints");
					return;
				}
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
			lastBankFailureAt = 0;
			lastFoodBankUnavailable = false;
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

	private boolean handleFood()
	{
		if (config.hitpoints() <= 0)
		{
			return false;
		}

		if (Rs2Player.getHealthPercentage() > config.hitpoints())
		{
			return false;
		}

		List<Rs2ItemModel> foodItems = plugin.getInventoryFood();
		if (foodItems.isEmpty())
		{
			if (needsFoodBanking() || config.hpSafetyWait())
			{
				return false;
			}
			Microbot.showMessage("Hitpoints are below the configured threshold and no food was found. Stopping agility.");
			shutdown();
			return true;
		}
		Rs2ItemModel foodItem = foodItems.get(0);

		Rs2Inventory.interact(foodItem, foodItem.getName().toLowerCase().contains("jug of wine") ? "drink" : "eat");
		Rs2Inventory.waitForInventoryChanges(1800);

		if (Rs2Inventory.contains(ItemID.JUG_EMPTY))
		{
			Rs2Inventory.dropAll(ItemID.JUG_EMPTY);
		}
		return true;
	}

	private boolean handleHealthSafety()
	{
		if (!shouldWaitForHealthInsteadOfBanking())
		{
			return false;
		}

		int resumeAt = Math.max(config.hpSafetyResumeAt(), config.hpSafetyWaitBelow());
		Microbot.status = "Waiting for hitpoints";
		sleepUntil(() -> shuttingDown
			|| !plugin.getInventoryFood().isEmpty()
			|| Rs2Player.getHealthPercentage() >= resumeAt, 10_000);
		return true;
	}

	private boolean handleBanking(AgilityCourseHandler courseHandler, WorldPoint playerWorldLocation)
	{
		if (!shouldBankForSupplies(courseHandler))
		{
			return false;
		}
		if (shouldWaitForHealthInsteadOfBanking())
		{
			return false;
		}
		if (Rs2Player.isMoving() || Rs2Player.isAnimating())
		{
			return true;
		}
		if (!canAttemptBanking(courseHandler, playerWorldLocation))
		{
			Microbot.status = "Finishing course before banking";
			return false;
		}

		long now = System.currentTimeMillis();
		if (lastBankFailureAt > 0 && now - lastBankFailureAt < BANK_RETRY_COOLDOWN_MS)
		{
			Microbot.status = "Waiting before retrying bank";
			return true;
		}

		Microbot.status = "Banking agility supplies";
		lastFoodBankUnavailable = false;
		if (!Rs2Bank.walkToBankAndUseBank())
		{
			lastBankFailureAt = now;
			Microbot.log("Unable to reach or open bank for agility supplies. Retrying after cooldown.");
			return true;
		}

		boolean success = withdrawConfiguredSupplies(courseHandler);
		Rs2Bank.closeBank();
		lastBankFailureAt = success ? 0 : System.currentTimeMillis();
		return true;
	}

	private boolean withdrawConfiguredSupplies(AgilityCourseHandler courseHandler)
	{
		boolean success = true;
		if (needsFoodBanking())
		{
			success &= withdrawConfiguredFood();
		}
		if (needsPotionBanking())
		{
			success &= withdrawConfiguredPotion();
		}
		if (needsSummerPieBanking(courseHandler))
		{
			success &= withdrawSummerPies();
		}
		return success;
	}

	private boolean withdrawConfiguredFood()
	{
		int targetAmount = config.foodWithdrawAmount();
		int currentFood = plugin.getInventoryFood().size();
		int missingFood = targetAmount - currentFood;
		if (missingFood <= 0)
		{
			return true;
		}
		if (Rs2Inventory.emptySlotCount() <= 0)
		{
			Microbot.showMessage("Inventory is full. Unable to withdraw agility food.");
			return false;
		}

		int amountToWithdraw = Math.min(missingFood, Rs2Inventory.emptySlotCount());

		AgilityFoodOption option = config.bankFood();
		if (option.isAuto())
		{
			return withdrawAutoFood(amountToWithdraw, currentFood);
		}

		Rs2Food food = option.getFood();
		if (food == null)
		{
			return false;
		}
		if (!Rs2Bank.hasBankItem(food.getId(), amountToWithdraw))
		{
			lastFoodBankUnavailable = true;
			Microbot.showMessage("No " + food.getName() + " found in the bank.");
			return false;
		}

		if (!Rs2Bank.withdrawX(food.getId(), amountToWithdraw))
		{
			return false;
		}
		return sleepUntil(() -> plugin.getInventoryFood().size() >= currentFood + amountToWithdraw, 2400);
	}

	private boolean withdrawAutoFood(int amountToWithdraw, int currentFood)
	{
		int remaining = amountToWithdraw;
		int expectedFood = currentFood;

		List<AgilityFoodOption> foodOptions = Arrays.stream(AgilityFoodOption.values())
			.filter(foodOption -> !foodOption.isNone() && !foodOption.isAuto())
			.filter(foodOption -> foodOption.getFood() != null)
			.sorted(Comparator.comparingInt((AgilityFoodOption foodOption) -> foodOption.getFood().getHeal()).reversed())
			.collect(Collectors.toList());

		for (AgilityFoodOption option : foodOptions)
		{
			if (remaining <= 0 || Rs2Inventory.emptySlotCount() <= 0)
			{
				break;
			}

			Rs2Food food = option.getFood();
			int available = getBankItemQuantity(food.getId());
			if (available <= 0)
			{
				continue;
			}

			int withdrawAmount = Math.min(Math.min(remaining, available), Rs2Inventory.emptySlotCount());
			if (!Rs2Bank.withdrawX(food.getId(), withdrawAmount))
			{
				return false;
			}

			int expectedAfterWithdraw = expectedFood + withdrawAmount;
			if (!sleepUntil(() -> plugin.getInventoryFood().size() >= expectedAfterWithdraw, 2400))
			{
				return false;
			}
			expectedFood = expectedAfterWithdraw;
			remaining = currentFood + amountToWithdraw - plugin.getInventoryFood().size();
		}

		if (remaining > 0)
		{
			lastFoodBankUnavailable = true;
			Microbot.showMessage("Not enough configured food was found in the bank.");
			return false;
		}
		return true;
	}

	private boolean withdrawConfiguredPotion()
	{
		AgilityPotionOption option = config.bankPotion();
		int targetAmount = config.potionWithdrawAmount();
		int currentPotions = getStaminaPotionCount();
		int missingPotions = targetAmount - currentPotions;
		if (missingPotions <= 0)
		{
			return true;
		}
		if (Rs2Inventory.emptySlotCount() <= 0)
		{
			Microbot.showMessage("Inventory is full. Unable to withdraw agility potions.");
			return false;
		}

		int amountToWithdraw = Math.min(missingPotions, Rs2Inventory.emptySlotCount());
		if (option.isNone() || option.getItemId() <= 0)
		{
			return false;
		}
		if (!Rs2Bank.hasBankItem(option.getItemId(), amountToWithdraw))
		{
			Microbot.showMessage("No " + option + " found in the bank.");
			return false;
		}

		if (!Rs2Bank.withdrawX(option.getItemId(), amountToWithdraw))
		{
			return false;
		}
		return sleepUntil(() -> getStaminaPotionCount() >= currentPotions + amountToWithdraw, 2400);
	}

	private boolean withdrawSummerPies()
	{
		int targetAmount = config.summerPieWithdrawAmount();
		int currentPies = plugin.getSummerPies().size();
		int missingPies = targetAmount - currentPies;
		if (missingPies <= 0)
		{
			return true;
		}
		if (Rs2Inventory.emptySlotCount() <= 0)
		{
			Microbot.showMessage("Inventory is full. Unable to withdraw summer pies.");
			return false;
		}

		int amountToWithdraw = Math.min(missingPies, Rs2Inventory.emptySlotCount());
		if (!Rs2Bank.hasBankItem(SUMMER_PIE_ID, amountToWithdraw))
		{
			Microbot.showMessage("No summer pies found in the bank.");
			return false;
		}
		if (!Rs2Bank.withdrawX(SUMMER_PIE_ID, amountToWithdraw))
		{
			return false;
		}
		return sleepUntil(() -> plugin.getSummerPies().size() >= currentPies + amountToWithdraw, 2400);
	}

	private boolean shouldBankForSupplies(AgilityCourseHandler courseHandler)
	{
		return needsFoodBanking() || needsPotionBanking() || needsSummerPieBanking(courseHandler);
	}

	private boolean needsFoodBanking()
	{
		AgilityFoodOption food = config.bankFood();
		return isFoodBankingConfigured()
			&& plugin.getInventoryFood().size() <= config.bankFoodAt()
			&& plugin.getInventoryFood().size() < config.foodWithdrawAmount();
	}

	private boolean isFoodBankingConfigured()
	{
		AgilityFoodOption food = config.bankFood();
		return !food.isNone()
			&& config.foodWithdrawAmount() > 0
			&& config.bankFoodAt() > 0;
	}

	private boolean needsPotionBanking()
	{
		AgilityPotionOption potion = config.bankPotion();
		return !potion.isNone()
			&& config.potionWithdrawAmount() > 0
			&& getStaminaPotionCount() < config.potionWithdrawAmount();
	}

	private boolean needsSummerPieBanking(AgilityCourseHandler courseHandler)
	{
		return courseHandler != null
			&& courseHandler.canBeBoosted()
			&& config.summerPieWithdrawAmount() > 0
			&& plugin.getSummerPies().size() < config.summerPieWithdrawAmount();
	}

	private int getStaminaPotionCount()
	{
		return Rs2Inventory.count(item -> item != null && Arrays.stream(STAMINA_POTION_IDS).anyMatch(id -> id == item.getId()));
	}

	private boolean shouldWaitForHealthInsteadOfBanking()
	{
		return config.hpSafetyWait()
			&& plugin.getInventoryFood().isEmpty()
			&& Rs2Player.getHealthPercentage() < config.hpSafetyWaitBelow()
			&& (!isFoodBankingConfigured() || lastFoodBankUnavailable);
	}

	private boolean canAttemptBanking(AgilityCourseHandler courseHandler, WorldPoint playerWorldLocation)
	{
		if (!config.bankOnlyAtCourseStart())
		{
			return true;
		}
		if (courseHandler == null || playerWorldLocation == null || courseHandler.getStartPoint() == null)
		{
			return false;
		}
		return courseHandler.getClientPlane() == 0
			&& courseHandler.getCurrentObstacleIndex() == 0
			&& playerWorldLocation.distanceTo(courseHandler.getStartPoint()) <= 12;
	}

	private int getBankItemQuantity(int itemId)
	{
		return Rs2Bank.bankItems().stream()
			.filter(item -> item != null && item.getId() == itemId)
			.mapToInt(Rs2ItemModel::getQuantity)
			.sum();
	}

	private boolean handleSummerPies(AgilityCourseHandler courseHandler)
	{
		if (!courseHandler.canBeBoosted())
		{
			return false;
		}
		if (courseHandler.getCurrentObstacleIndex() > 0)
		{
			return false;
		}
		if (Rs2Player.getBoostedSkillLevel(Skill.AGILITY) >= courseHandler.getRequiredLevel())
		{
			return false;
		}

		List<Rs2ItemModel> summerPies = plugin.getSummerPies();
		if (summerPies.isEmpty())
		{
			return false;
		}
		Rs2ItemModel summerPie = summerPies.get(0);

		Rs2Inventory.interact(summerPie, "eat");
		Rs2Inventory.waitForInventoryChanges(1800);
		if (Rs2Inventory.contains(ItemID.PIEDISH))
		{
			Rs2Inventory.dropAll(ItemID.PIEDISH);
		}
		return true;
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
