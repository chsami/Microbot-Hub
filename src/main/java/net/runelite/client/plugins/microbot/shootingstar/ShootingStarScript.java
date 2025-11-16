package net.runelite.client.plugins.microbot.shootingstar;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.inventorysetups.MInventorySetupsPlugin;
import net.runelite.client.plugins.microbot.shootingstar.ShootingStarConfig;
import net.runelite.client.plugins.microbot.shootingstar.ShootingStarPlugin;
import net.runelite.client.plugins.microbot.shootingstar.enums.Pickaxe;
import net.runelite.client.plugins.microbot.shootingstar.enums.ShootingStarState;
import net.runelite.client.plugins.microbot.shootingstar.model.Star;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Gembag;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.walker.WalkerState;

@Slf4j
public class ShootingStarScript extends Script
{
	public static ShootingStarState state;
	private final ShootingStarPlugin plugin;
	private final ShootingStarConfig config;
	private Rs2InventorySetup rs2InventorySetup;
	private Pickaxe pickaxe;
	private Star currentStar;
	private boolean hasEquipment = false;
	private boolean hasInventory = false;

    public Instant startTime;

    /**
     * Get the total runtime of the script
     *
     * @return the total runtime of the script
     */
    public Duration getRunTime() {
        if (startTime == null) return Duration.ofSeconds(0);
        return Duration.between(startTime, Instant.now());
    }

	@Inject
	public ShootingStarScript(ShootingStarPlugin plugin, ShootingStarConfig config)
	{
		this.plugin = plugin;
		this.config = config;
	}

	public boolean run()
	{
        startTime = Instant.now();
		Microbot.enableAutoRunOn = true;
		initialPlayerLocation = null;
		hasEquipment = false;
		hasInventory = false;
		Rs2Antiban.resetAntibanSettings();
		applyAntiBanSettings();
		Rs2Antiban.setActivity(Activity.GENERAL_MINING);
		mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
			try
			{
				if (!super.run())
				{
					return;
				}
				if (!Microbot.isLoggedIn())
				{
					return;
				}
				long startTime = System.currentTimeMillis();

				if (initialPlayerLocation == null)
				{
					initialPlayerLocation = Rs2Player.getWorldLocation();
				}

				if (hasStateChanged())
				{
					state = updateStarState();
				}

				if (state == null)
				{
					Microbot.showMessage("Unable to evaluate state");
					Microbot.stopPlugin(plugin);
					return;
				}

				if (Rs2Player.isMoving() || Rs2Antiban.getCategory().isBusy())
				{
					return;
				}
				if (Rs2AntibanSettings.actionCooldownActive)
				{
					return;
				}

				if (Rs2Gembag.isUnknown())
				{
					Rs2Gembag.checkGemBag();
					return;
				}

				switch (state)
				{
					case WAITING_FOR_STAR:
						if (config.useNearestHighTierStar())
						{
							if (!hasSelectedStar())
							{
								if (plugin.getStarList().isEmpty()) {
									log.debug("No stars found, waiting for api data");
									return;
								}

								currentStar = plugin.getClosestHighestTierStar();

								if (currentStar == null)
								{
									Microbot.showMessage("Unable to find a star within your tier range. Consider disabling useNearestHighTierStar until higher mining level.");
									Microbot.stopPlugin(plugin);
									return;
								}
							}
							else
							{
								currentStar = plugin.getSelectedStar();
							}
						}
						else
						{
							currentStar = plugin.getSelectedStar();

							if (currentStar == null)
							{
								log.warn("Please select a star inside of the panel to start the script.");
								sleepUntil(this::hasSelectedStar);
								return;
							}
						}

						log.info("Found star @ {}", currentStar.getShootingStarLocation().getLocationName());
						plugin.updateSelectedStar(currentStar);

						state = ShootingStarState.WALKING;
						break;
					case WALKING:
						toggleLockState(true);

						if (Rs2Player.getWorld() != currentStar.getWorld())
						{
							Microbot.hopToWorld(currentStar.getWorld());
							sleepUntil(() -> Microbot.getClient().getGameState() == GameState.LOGGED_IN);
							return;
						}

						boolean isNearShootingStar = Rs2Player.getWorldLocation().distanceTo(currentStar.getShootingStarLocation().getWorldPoint()) < 6;

						if (!isNearShootingStar)
						{
							WalkerState walkerState = Rs2Walker.walkWithState(currentStar.getShootingStarLocation().getWorldPoint(), 6);
							if (walkerState == WalkerState.UNREACHABLE)
							{
								log.debug("Walker State is {}, removing star from list", walkerState);
								plugin.removeStar(plugin.getSelectedStar());
								plugin.updatePanelList(true);
								state = ShootingStarState.WAITING_FOR_STAR;
							}
							return;
						}

						initialPlayerLocation = Rs2Player.getWorldLocation();

						state = ShootingStarState.MINING;
						break;
					case MINING:
						if (!currentStar.hasRequirements())
						{
							Microbot.status = "Waiting for star to degrade";
							log.warn("Unable to mine current star level, waiting..");
							return;
						}

						if (Rs2Inventory.isFull())
						{
							state = ShootingStarState.BANKING;
							return;
						}

						if (Rs2Equipment.isWearing("Dragon pickaxe"))
						{
							Rs2Combat.setSpecState(true, 1000);
						}

						TileObject starObject = Rs2GameObject.getGameObject(currentStar.getObjectId());

						if (starObject != null)
						{
							Rs2GameObject.interact(starObject, "mine");
							sleepUntil(Rs2Player::isAnimating);
							Rs2Antiban.actionCooldown();
							Rs2Antiban.moveMouseOffScreen();
						}

						break;
					case BANKING:
						BankLocation nearestBank = Rs2Bank.getNearestBank();
						boolean isNearBank = Rs2Bank.walkToBank(nearestBank);
						if (!isNearBank || !Rs2Bank.isNearBank(nearestBank, 6))
						{
							return;
						}

						toggleLockState(false);

						boolean isBankOpen = Rs2Bank.openBank();
						if (!isBankOpen || !Rs2Bank.isOpen())
						{
							return;
						}

						if (Rs2Inventory.hasItem("uncut"))
						{
							Rs2Bank.depositAll(x -> x.getName().toLowerCase().contains("uncut"));
						}

						if (isUsingInventorySetup())
						{
							if (!hasEquipment)
							{
								hasEquipment = rs2InventorySetup.loadEquipment();
								Rs2Random.waitEx(1200, 300);
							}
							if (!hasInventory && rs2InventorySetup.doesEquipmentMatch())
							{
								hasInventory = rs2InventorySetup.loadInventory();
								Rs2Random.waitEx(1200, 300);
							}

							if (!hasEquipment || !hasInventory)
							{
								return;
							}
						}
						else
						{
							if (pickaxe == null)
							{
								pickaxe = getBestPickaxe(Rs2Bank.bankItems());
								if (pickaxe != null)
								{
									Rs2Bank.withdrawItem(pickaxe.getItemName());
									Rs2Random.waitEx(1200, 300);
								}
								else
								{
									Microbot.showMessage("Unable to find pickaxe, please purchase a pickaxe");
									Microbot.stopPlugin(plugin);
									return;
								}
							}
						}

						if (Rs2Gembag.isAnyGemSlotFull())
						{
							Rs2Bank.emptyGemBag();
						}

						boolean bankClosed = Rs2Bank.closeBank();
						if (!bankClosed || Rs2Bank.isOpen())
						{
							return;
						}

						if (hasSelectedStar())
						{
							if (!currentStar.equals(plugin.getSelectedStar()))
							{
								return;
							}

							state = ShootingStarState.WALKING;
							return;
						}

						state = ShootingStarState.WAITING_FOR_STAR;
						break;
				}

				long endTime = System.currentTimeMillis();
				long totalTime = endTime - startTime;
				System.out.println("Total time for loop " + totalTime);

			}
			catch (Exception ex)
			{
				log.error("Error in ShootingStarScript main loop: {} - ", ex.getMessage(), ex);
			}
		}, 0, 600, TimeUnit.MILLISECONDS);
		return true;
	}

	@Override
	public void shutdown()
	{
		super.shutdown();
		state = null;
		currentStar = null;
		pickaxe = null;
		Rs2Antiban.resetAntibanSettings();
	}

	private boolean isUsingInventorySetup()
	{
		boolean isInventorySetupPluginEnabled = Microbot.isPluginEnabled(MInventorySetupsPlugin.class);
		if (!isInventorySetupPluginEnabled)
		{
			return false;
		}

		return config.useInventorySetup();
	}

	private boolean hasSelectedStar()
	{
		return plugin.getSelectedStar() != null;
	}

	public boolean shouldBank()
	{
		boolean isInventoryFull = Rs2Inventory.isFull();
		boolean shouldBreak = (shouldBreak() && config.useBreakAtBank());
		boolean isAnyGemBagFull = Rs2Gembag.hasGemBag() && Rs2Gembag.isAnyGemSlotFull();
		if (isUsingInventorySetup())
		{
			hasEquipment = rs2InventorySetup.doesEquipmentMatch();
			hasInventory = rs2InventorySetup.doesInventoryMatch();
			System.out.printf("hasEquipment: %s%nhasInventory: %s%nIs Inventory Full: %s%nshouldBreak: %s%nIsAnyGemBagFull: %s%n", hasEquipment, hasInventory, isInventoryFull, shouldBreak, isAnyGemBagFull);

			return (!hasEquipment || !hasInventory) || isInventoryFull || shouldBreak || isAnyGemBagFull;
		}
		return pickaxe == null || isInventoryFull || shouldBreak || isAnyGemBagFull;
	}

	public ShootingStarState getState()
	{
		if (shouldBank())
		{
			return ShootingStarState.BANKING;
		}

		if (hasSelectedStar())
		{
			return ShootingStarState.WALKING;
		}

		return ShootingStarState.WAITING_FOR_STAR;
	}

	private ShootingStarState updateStarState()
	{
		if (state == null)
		{
			if (isUsingInventorySetup())
			{
				rs2InventorySetup = new Rs2InventorySetup(config.inventorySetup(), mainScheduledFuture);
				if (!rs2InventorySetup.hasSpellBook())
				{
					Microbot.showMessage("Your spellbook is not matching the inventory setup.");
					Microbot.stopPlugin(plugin);
					return null;
				}
			}
			else
			{
				if (Rs2Inventory.hasItem("pickaxe") || Rs2Equipment.isWearing("pickaxe"))
				{
					pickaxe = getBestPickaxe(Rs2Equipment.items());
					if (pickaxe == null)
					{
						pickaxe = getBestPickaxe(Rs2Inventory.items().collect(Collectors.toList()));
					}
				}
			}
			return getState();
		}

		Star selectedStar = plugin.getSelectedStar();

		if (selectedStar == null)
		{
			if (shouldBank())
			{
				return ShootingStarState.BANKING;
			}

			return ShootingStarState.WAITING_FOR_STAR;
		}

		if (!currentStar.equals(selectedStar))
		{
			currentStar = selectedStar;
			if (state == ShootingStarState.MINING)
			{
				WorldPoint randomNearestWalkableTile = Rs2Tile.getNearestWalkableTile(Rs2Player.getWorldLocation());
				Rs2Walker.walkFastCanvas(randomNearestWalkableTile);
			}
			if (state == ShootingStarState.WALKING)
			{
				Rs2Walker.setTarget(null);
				Rs2Player.waitForWalking();
			}
			return ShootingStarState.WALKING;
		}

		if (state == ShootingStarState.MINING)
		{
			GameObject starObject = Rs2GameObject.getGameObject("crashed star", initialPlayerLocation, 10);

			if (currentStar == null || starObject == null)
			{

				if (plugin.getSelectedStar().getTier() == 1)
				{
					plugin.setTotalStarsMined(plugin.getTotalStarsMined() + 1);
				}
				plugin.removeStar(plugin.getSelectedStar());
				plugin.updatePanelList(true);

				if (shouldBank())
				{
					return ShootingStarState.BANKING;
				}

				return ShootingStarState.WAITING_FOR_STAR;
			}

			int _newTier = currentStar.getTierBasedOnObjectId(starObject.getId());
			currentStar.setTier(_newTier);
			plugin.updatePanelList(false);
			currentStar = selectedStar;
		}
		return ShootingStarState.MINING;
	}

	private boolean hasStateChanged()
	{
		// If no state (on plugin start)
		if (state == null)
		{
			return true;
		}
		// If waiting for star or if you are returning to bank & no selected star, no state change (mainly for manual mode, but also to allow waiting for star to run)
		if (state == ShootingStarState.WAITING_FOR_STAR || (state == ShootingStarState.BANKING && plugin.getSelectedStar() == null))
		{
			return false;
		}
		// If you are walking or mining a star & the star becomes null
		if (plugin.getSelectedStar() == null)
		{
			return true;
		}
		// If the instance of the current star in the script does not equal the selected star in the plugin (only based on world & location)
		if (!currentStar.equals(plugin.getSelectedStar()))
		{
			return true;
		}
		// If the state is mining state, scan the crashed star game object & check if the game object id has updated.
		if (state == ShootingStarState.MINING)
		{
			GameObject starObject = Rs2GameObject.getGameObject("crashed star", initialPlayerLocation, 10);
			return hasStarGameObjectChanged(starObject);
		}
		return false;
	}

	private boolean hasStarGameObjectChanged(GameObject starObject)
	{
		// If the GameObject does not exist anymore
		if (starObject == null)
		{
			return true;
		}

		// If the GameObject has updated to a new tier
		return currentStar.getObjectId() != starObject.getId();
	}

	private Pickaxe getBestPickaxe(List<Rs2ItemModel> items)
	{
		Pickaxe bestPickaxe = null;

		for (Pickaxe pickaxe : Pickaxe.values())
		{
			if (items.stream().noneMatch(i -> i.getName().toLowerCase().contains(pickaxe.getItemName())))
			{
				continue;
			}
			if (pickaxe.hasRequirements())
			{
				if (bestPickaxe == null || pickaxe.getMiningLevel() > bestPickaxe.getMiningLevel())
				{
					bestPickaxe = pickaxe;
				}
			}
		}
		return bestPickaxe;
	}

	private boolean shouldBreak()
	{
		if (!plugin.isBreakHandlerEnabled())
		{
			return false;
		}

		return BreakHandlerScript.breakIn <= 1;
	}

	public void toggleLockState(boolean lock)
	{
		if (!plugin.isBreakHandlerEnabled())
		{
			return;
		}

		if (plugin.isBreakHandlerEnabled() && config.useBreakAtBank())
		{
			if (lock && !BreakHandlerScript.isLockState())
			{
				BreakHandlerScript.setLockState(true);
			}
			else if (!lock && BreakHandlerScript.isLockState())
			{
				BreakHandlerScript.setLockState(false);
			}
		}
	}

	private void applyAntiBanSettings()
	{
		Rs2AntibanSettings.antibanEnabled = true;
		Rs2AntibanSettings.usePlayStyle = true;
		Rs2AntibanSettings.simulateFatigue = true;
		Rs2AntibanSettings.simulateAttentionSpan = true;
		Rs2AntibanSettings.behavioralVariability = true;
		Rs2AntibanSettings.nonLinearIntervals = true;
		Rs2AntibanSettings.naturalMouse = true;
		Rs2AntibanSettings.simulateMistakes = true;
		Rs2AntibanSettings.moveMouseOffScreen = true;
		Rs2AntibanSettings.contextualVariability = true;
		Rs2AntibanSettings.devDebug = false;
		Rs2AntibanSettings.playSchedule = true;
		Rs2AntibanSettings.actionCooldownChance = 0.35;
	}
}
