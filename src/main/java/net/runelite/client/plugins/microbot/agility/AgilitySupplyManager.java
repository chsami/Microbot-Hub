package net.runelite.client.plugins.microbot.agility;

import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.agility.courses.AgilityCourseHandler;
import net.runelite.client.plugins.microbot.agility.enums.AgilityEquipmentOption;
import net.runelite.client.plugins.microbot.agility.enums.AgilityFoodOption;
import net.runelite.client.plugins.microbot.agility.enums.AgilityPotionOption;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.misc.Rs2Food;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.Collections;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

public class AgilitySupplyManager
{
	private static final long BANK_RETRY_COOLDOWN_MS = 30_000;
	private static final long FOOD_BANK_UNAVAILABLE_RETRY_MS = 300_000;
	private static final int SUMMER_PIE_ID = ItemID.SUMMER_PIE;
	private static final String GRACEFUL_HOOD = "Graceful hood";
	private static final String GRACEFUL_CAPE = "Graceful cape";
	private static final String GRACEFUL_TOP = "Graceful top";
	private static final String GRACEFUL_LEGS = "Graceful legs";
	private static final String GRACEFUL_GLOVES = "Graceful gloves";
	private static final String GRACEFUL_BOOTS = "Graceful boots";
	private static final String AGILITY_CAPE = "Agility cape";
	private static final String[] GRACEFUL_WITH_CAPE = {
		GRACEFUL_HOOD,
		GRACEFUL_CAPE,
		GRACEFUL_TOP,
		GRACEFUL_LEGS,
		GRACEFUL_GLOVES,
		GRACEFUL_BOOTS
	};
	private static final String[] GRACEFUL_WITH_AGILITY_CAPE = {
		GRACEFUL_HOOD,
		AGILITY_CAPE,
		GRACEFUL_TOP,
		GRACEFUL_LEGS,
		GRACEFUL_GLOVES,
		GRACEFUL_BOOTS
	};
	private static final int[] STAMINA_POTION_IDS = {
		ItemID._4DOSESTAMINA,
		ItemID._3DOSESTAMINA,
		ItemID._2DOSESTAMINA,
		ItemID._1DOSESTAMINA
	};

	private final MicroAgilityPlugin plugin;
	private final MicroAgilityConfig config;
	private final BooleanSupplier shuttingDown;
	private final Runnable shutdown;

	private long lastBankFailureAt = 0;
	private long lastFoodBankUnavailableAt = 0;
	private boolean summerPieBankUnavailable = false;

	public AgilitySupplyManager(MicroAgilityPlugin plugin, MicroAgilityConfig config, BooleanSupplier shuttingDown, Runnable shutdown)
	{
		this.plugin = plugin;
		this.config = config;
		this.shuttingDown = shuttingDown;
		this.shutdown = shutdown;
	}

	public void reset()
	{
		lastBankFailureAt = 0;
		lastFoodBankUnavailableAt = 0;
		summerPieBankUnavailable = false;
	}

	public InventorySnapshot createInventorySnapshot()
	{
		return new InventorySnapshot(plugin.getInventoryFood(), plugin.getSummerPies(), getStaminaPotionCount());
	}

	public boolean handleSummerPies(AgilityCourseHandler courseHandler, WorldPoint playerWorldLocation, int currentObstacleIndex, InventorySnapshot inventorySnapshot)
	{
		if (!courseHandler.canBeBoosted())
		{
			return false;
		}
		if (!isAtCourseStart(courseHandler, playerWorldLocation, currentObstacleIndex))
		{
			return false;
		}
		if (Rs2Player.getBoostedSkillLevel(Skill.AGILITY) >= courseHandler.getRequiredLevel())
		{
			return false;
		}

		List<Rs2ItemModel> summerPies = inventorySnapshot.getSummerPies();
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

	public boolean handlePreLevelCheck(AgilityCourseHandler courseHandler, WorldPoint playerWorldLocation, int currentObstacleIndex,
									   boolean hasRequiredLevel, InventorySnapshot inventorySnapshot)
	{
		boolean requiresBoost = courseHandler.canBeBoosted() && !plugin.hasRealRequiredLevel(courseHandler);
		if (!requiresBoost)
		{
			return false;
		}
		if (!isAtCourseStart(courseHandler, playerWorldLocation, currentObstacleIndex))
		{
			if (!hasRequiredLevel)
			{
				stopAndLogout("You do not have the required agility level for this course.");
				return true;
			}
			return false;
		}
		if (hasRequiredLevel && inventorySnapshot.getSummerPies().isEmpty() && config.summerPieWithdrawAmount() <= 0)
		{
			stopAndLogout("You need summer pies in inventory before starting this boosted course.");
			return true;
		}
		if (!needsSummerPieBanking(courseHandler, inventorySnapshot))
		{
			if (!hasRequiredLevel)
			{
				stopAndLogout("You do not have enough summer pies configured to start this course.");
				return true;
			}
			return false;
		}

		summerPieBankUnavailable = false;
		if (!handleBanking(courseHandler, playerWorldLocation, currentObstacleIndex, inventorySnapshot, true))
		{
			if (!hasRequiredLevel)
			{
				stopAndLogout("You do not have enough summer pies configured to start this course.");
				return true;
			}
			return false;
		}
		if (summerPieBankUnavailable && plugin.getSummerPies().size() < config.summerPieWithdrawAmount())
		{
			stopAndLogout("No summer pies were available for the selected boosted course.");
			return true;
		}
		return true;
	}

	public boolean handleFoodOrHealthSafety(InventorySnapshot inventorySnapshot)
	{
		return handleFood(inventorySnapshot) || handleHealthSafety(inventorySnapshot);
	}

	public boolean handleBeforeObstacle(AgilityCourseHandler courseHandler, WorldPoint playerWorldLocation, int currentObstacleIndex,
										InventorySnapshot inventorySnapshot)
	{
		if (wearConfiguredEquipmentFromInventory())
		{
			return true;
		}
		return handleBanking(courseHandler, playerWorldLocation, currentObstacleIndex, inventorySnapshot)
			|| handleHealthSafety(inventorySnapshot);
	}

	private boolean handleFood(InventorySnapshot inventorySnapshot)
	{
		if (config.hitpoints() <= 0)
		{
			return false;
		}

		if (Rs2Player.getHealthPercentage() > config.hitpoints())
		{
			return false;
		}

		List<Rs2ItemModel> foodItems = inventorySnapshot.getFoodItems();
		if (foodItems.isEmpty())
		{
			if (needsFoodBanking(inventorySnapshot) || config.hpSafetyWait())
			{
				return false;
			}
			Microbot.showMessage("Hitpoints are below the configured threshold and no food was found. Stopping agility.");
			shutdown.run();
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

	private boolean handleHealthSafety(InventorySnapshot inventorySnapshot)
	{
		if (!shouldWaitForHealthInsteadOfBanking(inventorySnapshot))
		{
			return false;
		}

		int resumeAt = Math.max(config.hpSafetyResumeAt(), config.hpSafetyWaitBelow());
		Microbot.status = "Waiting for hitpoints";
		Global.sleepUntil(() -> shuttingDown.getAsBoolean()
			|| !plugin.getInventoryFood().isEmpty()
			|| Rs2Player.getHealthPercentage() >= resumeAt, 10_000);
		return true;
	}

	private boolean handleBanking(AgilityCourseHandler courseHandler, WorldPoint playerWorldLocation, int currentObstacleIndex,
								  InventorySnapshot inventorySnapshot)
	{
		return handleBanking(courseHandler, playerWorldLocation, currentObstacleIndex, inventorySnapshot, false);
	}

	private boolean handleBanking(AgilityCourseHandler courseHandler, WorldPoint playerWorldLocation, int currentObstacleIndex,
								  InventorySnapshot inventorySnapshot, boolean blockUntilBankable)
	{
		if (!shouldBankForSupplies(courseHandler, inventorySnapshot) && !needsEquipmentBanking())
		{
			return false;
		}
		if (shouldWaitForHealthInsteadOfBanking(inventorySnapshot))
		{
			return false;
		}
		if (Rs2Player.isMoving() || Rs2Player.isAnimating())
		{
			return true;
		}
		if (!canAttemptBanking(courseHandler, playerWorldLocation, currentObstacleIndex))
		{
			Microbot.status = "Finishing course before banking";
			return blockUntilBankable || shouldPauseForUnsafeSupplyBanking(inventorySnapshot);
		}

		long now = System.currentTimeMillis();
		if (lastBankFailureAt > 0 && now - lastBankFailureAt < BANK_RETRY_COOLDOWN_MS)
		{
			Microbot.status = "Waiting before retrying bank";
			return true;
		}

		Microbot.status = "Banking agility supplies";
		boolean bankOpen = Rs2Bank.walkToBankAndUseBank();
		if (!bankOpen)
		{
			Global.sleepUntil(() -> Rs2Bank.isOpen() || Rs2Player.isMoving(), 1200);
			bankOpen = Rs2Bank.isOpen();
			if (!bankOpen && Rs2Player.isMoving())
			{
				lastBankFailureAt = 0;
				Microbot.status = "Walking to bank";
				return true;
			}
		}
		if (!bankOpen)
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
		InventorySnapshot inventorySnapshot = createInventorySnapshot();
		if (needsFoodBanking(inventorySnapshot))
		{
			success &= withdrawConfiguredFood();
		}
		if (needsPotionBanking(inventorySnapshot))
		{
			success &= withdrawConfiguredPotion();
		}
		if (needsSummerPieBanking(courseHandler, inventorySnapshot))
		{
			success &= withdrawSummerPies();
		}
		if (needsEquipmentBanking())
		{
			success &= withdrawAndWearConfiguredEquipment();
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
			markFoodBankUnavailable();
			Microbot.showMessage("No " + food.getName() + " found in the bank.");
			return false;
		}

		if (!Rs2Bank.withdrawX(food.getId(), amountToWithdraw))
		{
			return false;
		}
		boolean receivedFood = Global.sleepUntil(() -> plugin.getInventoryFood().size() >= currentFood + amountToWithdraw, 2400);
		if (receivedFood)
		{
			clearFoodBankUnavailable();
		}
		return receivedFood;
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
			if (!Global.sleepUntil(() -> plugin.getInventoryFood().size() >= expectedAfterWithdraw, 2400))
			{
				return false;
			}
			expectedFood = expectedAfterWithdraw;
			remaining = currentFood + amountToWithdraw - plugin.getInventoryFood().size();
		}

		if (remaining > 0)
		{
			markFoodBankUnavailable();
			Microbot.showMessage("Not enough configured food was found in the bank.");
			return false;
		}
		clearFoodBankUnavailable();
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
		return Global.sleepUntil(() -> getStaminaPotionCount() >= currentPotions + amountToWithdraw, 2400);
	}

	private boolean withdrawSummerPies()
	{
		int targetAmount = config.summerPieWithdrawAmount();
		int currentPies = plugin.getSummerPies().size();
		int missingPies = targetAmount - currentPies;
		if (missingPies <= 0)
		{
			summerPieBankUnavailable = false;
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
			summerPieBankUnavailable = true;
			Microbot.showMessage("No summer pies found in the bank.");
			return false;
		}
		if (!Rs2Bank.withdrawX(SUMMER_PIE_ID, amountToWithdraw))
		{
			summerPieBankUnavailable = true;
			return false;
		}
		boolean receivedPies = Global.sleepUntil(() -> plugin.getSummerPies().size() >= currentPies + amountToWithdraw, 2400);
		summerPieBankUnavailable = !receivedPies;
		return receivedPies;
	}

	private boolean shouldBankForSupplies(AgilityCourseHandler courseHandler, InventorySnapshot inventorySnapshot)
	{
		return needsFoodBanking(inventorySnapshot) || needsPotionBanking(inventorySnapshot) || needsSummerPieBanking(courseHandler, inventorySnapshot);
	}

	private boolean withdrawAndWearConfiguredEquipment()
	{
		boolean success = true;
		for (String itemName : getConfiguredEquipmentNames())
		{
			success &= ensureEquipmentPieceEquipped(itemName);
		}
		return success && !needsEquipmentBanking();
	}

	private boolean ensureEquipmentPieceEquipped(String itemName)
	{
		if (Rs2Equipment.isWearing(itemName))
		{
			return true;
		}
		if (Rs2Inventory.hasItem(itemName))
		{
			return Rs2Bank.wearItem(itemName);
		}
		if (!Rs2Bank.hasBankItem(itemName))
		{
			Microbot.showMessage(itemName + " was not found in the bank.");
			return false;
		}
		return Rs2Bank.withdrawXAndEquip(itemName, 1);
	}

	private boolean wearConfiguredEquipmentFromInventory()
	{
		if (config.bankEquipment().isNone())
		{
			return false;
		}
		for (String itemName : getConfiguredEquipmentNames())
		{
			if (!Rs2Equipment.isWearing(itemName) && Rs2Inventory.hasItem(itemName))
			{
				return Rs2Inventory.wear(itemName);
			}
		}
		return false;
	}

	private boolean needsEquipmentBanking()
	{
		if (config.bankEquipment().isNone())
		{
			return false;
		}
		for (String itemName : getConfiguredEquipmentNames())
		{
			if (!Rs2Equipment.isWearing(itemName) && !Rs2Inventory.hasItem(itemName))
			{
				return true;
			}
		}
		return false;
	}

	private String[] getConfiguredEquipmentNames()
	{
		return config.bankEquipment().useAgilityCape() ? GRACEFUL_WITH_AGILITY_CAPE : GRACEFUL_WITH_CAPE;
	}

	private boolean needsFoodBanking(InventorySnapshot inventorySnapshot)
	{
		return isFoodBankingConfigured()
			&& inventorySnapshot.getFoodCount() <= config.bankFoodAt()
			&& inventorySnapshot.getFoodCount() < config.foodWithdrawAmount();
	}

	private boolean isFoodBankingConfigured()
	{
		AgilityFoodOption food = config.bankFood();
		return !food.isNone()
			&& config.foodWithdrawAmount() > 0
			&& config.bankFoodAt() > 0;
	}

	private boolean needsPotionBanking(InventorySnapshot inventorySnapshot)
	{
		AgilityPotionOption potion = config.bankPotion();
		return !potion.isNone()
			&& config.potionWithdrawAmount() > 0
			&& inventorySnapshot.getStaminaPotionCount() < config.potionWithdrawAmount();
	}

	private boolean needsSummerPieBanking(AgilityCourseHandler courseHandler, InventorySnapshot inventorySnapshot)
	{
		return courseHandler != null
			&& courseHandler.canBeBoosted()
			&& config.summerPieWithdrawAmount() > 0
			&& inventorySnapshot.getSummerPieCount() < config.summerPieWithdrawAmount();
	}

	private int getStaminaPotionCount()
	{
		return Rs2Inventory.count(item -> item != null && Arrays.stream(STAMINA_POTION_IDS).anyMatch(id -> id == item.getId()));
	}

	private boolean shouldWaitForHealthInsteadOfBanking(InventorySnapshot inventorySnapshot)
	{
		return config.hpSafetyWait()
			&& inventorySnapshot.getFoodItems().isEmpty()
			&& Rs2Player.getHealthPercentage() < config.hpSafetyWaitBelow()
			&& (!isFoodBankingConfigured() || isFoodBankUnavailable());
	}

	private boolean shouldPauseForUnsafeSupplyBanking(InventorySnapshot inventorySnapshot)
	{
		return config.hpSafetyWait()
			&& isFoodBankingConfigured()
			&& inventorySnapshot.getFoodItems().isEmpty()
			&& Rs2Player.getHealthPercentage() < config.hpSafetyWaitBelow();
	}

	private boolean canAttemptBanking(AgilityCourseHandler courseHandler, WorldPoint playerWorldLocation, int currentObstacleIndex)
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
			&& currentObstacleIndex == 0
			&& playerWorldLocation.distanceTo(courseHandler.getStartPoint()) <= 12;
	}

	private boolean isAtCourseStart(AgilityCourseHandler courseHandler, WorldPoint playerWorldLocation, int currentObstacleIndex)
	{
		return courseHandler != null
			&& playerWorldLocation != null
			&& courseHandler.getStartPoint() != null
			&& courseHandler.getClientPlane() == 0
			&& currentObstacleIndex == 0
			&& playerWorldLocation.distanceTo(courseHandler.getStartPoint()) <= 12;
	}

	private int getBankItemQuantity(int itemId)
	{
		return Rs2Bank.bankItems().stream()
			.filter(item -> item != null && item.getId() == itemId)
			.mapToInt(Rs2ItemModel::getQuantity)
			.sum();
	}

	private void markFoodBankUnavailable()
	{
		lastFoodBankUnavailableAt = System.currentTimeMillis();
	}

	private void clearFoodBankUnavailable()
	{
		lastFoodBankUnavailableAt = 0;
	}

	private boolean isFoodBankUnavailable()
	{
		return lastFoodBankUnavailableAt > 0
			&& System.currentTimeMillis() - lastFoodBankUnavailableAt < FOOD_BANK_UNAVAILABLE_RETRY_MS;
	}

	private void stopAndLogout(String message)
	{
		Microbot.showMessage(message);
		Microbot.status = "Stopping agility";
		if (Microbot.isLoggedIn())
		{
			Rs2Player.logout();
		}
		shutdown.run();
	}

	static final class InventorySnapshot
	{
		private final List<Rs2ItemModel> foodItems;
		private final List<Rs2ItemModel> summerPies;
		private final int staminaPotionCount;

		private InventorySnapshot(List<Rs2ItemModel> foodItems, List<Rs2ItemModel> summerPies, int staminaPotionCount)
		{
			this.foodItems = Collections.unmodifiableList(foodItems);
			this.summerPies = Collections.unmodifiableList(summerPies);
			this.staminaPotionCount = staminaPotionCount;
		}

		List<Rs2ItemModel> getFoodItems()
		{
			return foodItems;
		}

		List<Rs2ItemModel> getSummerPies()
		{
			return summerPies;
		}

		int getFoodCount()
		{
			return foodItems.size();
		}

		int getSummerPieCount()
		{
			return summerPies.size();
		}

		int getStaminaPotionCount()
		{
			return staminaPotionCount;
		}
	}
}
