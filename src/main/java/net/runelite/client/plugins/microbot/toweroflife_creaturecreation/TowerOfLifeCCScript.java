package net.runelite.client.plugins.microbot.toweroflife_creaturecreation;

import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.grounditem.LootingParameters;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.misc.Rs2Food;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class TowerOfLifeCCScript extends Script {
    enum State {
        MOVING_TO_BANK,
        BANKING,
        MOVING_TO_ALTAR,
        CREATURE_CREATION_PROCESS
    }
    static State currentState = State.BANKING;

    BankLocation ardougneBankLocation = BankLocation.ARDOUGNE_SOUTH;
    int altarObjectId = 21893;
    boolean depositedLoot = false;

    WorldPoint basementLadderLocation = new WorldPoint(2649, 3212, 0);
    boolean arrivedInTower = false;
    boolean inBasement = false;

    private long lastKillTime = 0;

    LootingParameters spidineLootParams = new LootingParameters(
            10,
            1,
            1,
            1,
            false,
            true,
            "red spider"
    );
    LootingParameters unicowLootParams = new LootingParameters(
            10,
            1,
            1,
            1,
            false,
            true,
            "unicorn horn"
    );
    Rs2NpcModel summonedCreature = null;

    public boolean run(TowerOfLifeCCConfig config) {
        Microbot.enableAutoRunOn = false;

        InitialiseAntiban();
        depositedLoot = false;
        arrivedInTower = false;
        summonedCreature = null;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if (Microbot.pauseAllScripts.get()) return;

                inBasement = Rs2Player.getWorldLocation().getRegionID() == 12100;

                HandleStateMachine(config);

            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 300, TimeUnit.MILLISECONDS);
        return true;
    }

    void InitialiseAntiban()
    {
        Rs2Antiban.resetAntibanSettings();
        Rs2AntibanSettings.antibanEnabled = true;
        Rs2AntibanSettings.usePlayStyle = true;
        Rs2AntibanSettings.randomIntervals = false;
        Rs2AntibanSettings.simulateFatigue = true;
        Rs2AntibanSettings.simulateAttentionSpan = true;
        Rs2AntibanSettings.behavioralVariability = true;
        Rs2AntibanSettings.nonLinearIntervals = true;
        Rs2AntibanSettings.dynamicIntensity = false;
        Rs2AntibanSettings.dynamicActivity = false;
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.simulateMistakes = true;
        Rs2AntibanSettings.takeMicroBreaks = false;
        Rs2AntibanSettings.contextualVariability = true;
        Rs2AntibanSettings.devDebug = false;

        Rs2Antiban.setActivity(Activity.GENERAL_COMBAT);
        Rs2Antiban.setActivityIntensity(ActivityIntensity.HIGH);
    }


    @Override
    public void shutdown()
    {
        super.shutdown();
        summonedCreature = null;
        depositedLoot = false;
        arrivedInTower = false;
    }

    void HandleStateMachine(TowerOfLifeCCConfig _config)
    {
        switch (currentState)
        {
            case MOVING_TO_BANK:
                if (Rs2Walker.walkTo(ardougneBankLocation.getWorldPoint()))
                {
                    currentState = State.BANKING;
                }
                break;

            case BANKING:
                if (!Rs2Bank.isOpen())
                {
                    Rs2Bank.openBank();
                }
                else
                {
                    if (!depositedLoot)
                    {
                        if (Rs2Inventory.hasItem(ItemID.RED_SPIDERS_EGGS))
                        {
                            Rs2Bank.depositAll(ItemID.RED_SPIDERS_EGGS);
                            Rs2Inventory.waitForInventoryChanges(3000);
                        }
                        if (Rs2Inventory.hasItem(ItemID.UNICORN_HORN))
                        {
                            Rs2Bank.depositAll(ItemID.UNICORN_HORN);
                            Rs2Inventory.waitForInventoryChanges(3000);
                        }
                        depositedLoot = true;
                        break;
                    }

                    if (_config.EatFoodAtBank())
                    {
                        if (!Rs2Player.isFullHealth())
                        {
                            HandleFoodAtBank(_config.PreferLowestHealingFood());
                            break;
                        }
                    }

                    switch (_config.SelectedCreature())
                    {
                        case UNICOW:
                            if (!Rs2Bank.hasItem(ItemID.COW_HIDE))
                            {
                                Microbot.showMessage("No cowhides found in bank! Shutting down.");
                                Microbot.stopPlugin(TowerOfLifeCCPlugin.class);
                                break;
                            }

                            if (Microbot.getVarbitValue(VarbitID.ARDOUGNE_DIARY_MEDIUM_COMPLETE) == 1)
                            {
                                int numItemsToWithdraw = (int)Math.floor(Rs2Inventory.emptySlotCount() * 0.5);
                                Rs2Bank.withdrawX(ItemID.COW_HIDE, numItemsToWithdraw);
                                Rs2Inventory.waitForInventoryChanges(3000);
                                Rs2Bank.withdrawX(ItemID.UNICORN_HORN, numItemsToWithdraw);
                                Rs2Inventory.waitForInventoryChanges(3000);
                            }
                            else
                            {
                                Rs2Bank.withdrawX(ItemID.COW_HIDE, 7);
                                Rs2Inventory.waitForInventoryChanges(3000);
                                Rs2Bank.withdrawOne(ItemID.UNICORN_HORN);
                                Rs2Inventory.waitForInventoryChanges(3000);
                            }
                            break;

                        case SPIDINE:
                            if (!Rs2Bank.hasItem(ItemID.RAW_SARDINE))
                            {
                                Microbot.showMessage("No raw sardines found in bank! Shutting down.");
                                Microbot.stopPlugin(TowerOfLifeCCPlugin.class);
                                break;
                            }

                            if (Microbot.getVarbitValue(VarbitID.ARDOUGNE_DIARY_MEDIUM_COMPLETE) == 1)
                            {
                                int numItemsToWithdraw = (int)Math.floor(Rs2Inventory.emptySlotCount() * 0.5);
                                Rs2Bank.withdrawX(ItemID.RAW_SARDINE, numItemsToWithdraw);
                                Rs2Inventory.waitForInventoryChanges(3000);
                                Rs2Bank.withdrawX(ItemID.RED_SPIDERS_EGGS, numItemsToWithdraw);
                            }
                            else
                            {
                                Rs2Bank.withdrawX(ItemID.RAW_SARDINE, 4);
                                Rs2Inventory.waitForInventoryChanges(3000);
                                Rs2Bank.withdrawOne(ItemID.RED_SPIDERS_EGGS);
                                Rs2Inventory.waitForInventoryChanges(3000);
                            }
                            break;
                    }
                    Rs2Bank.closeBank();
                    currentState = State.MOVING_TO_ALTAR;
                    depositedLoot = false;
                    break;
                }
                break;

            case MOVING_TO_ALTAR:
                if (!arrivedInTower)
                {
                    if (!Rs2Walker.isInArea(new WorldPoint(2652, 3212, 0), new WorldPoint(2646, 3217, 0)))
                    {
                        Microbot.log("Walking to basement ladder.");
                        Rs2Walker.walkTo(basementLadderLocation);
                    }
                    else
                    {
                        arrivedInTower = true;
                        Microbot.log("Arrived in tower!");
                    }
                }
                else
                {
                    if (!inBasement && !Rs2Player.isMoving())
                    {
                        TileObject trapdoor = Rs2GameObject.getTileObject(new WorldPoint(2648, 3212, 0));
                        if (trapdoor != null)
                        {
                            Microbot.log("Trapdoor id: " + trapdoor.getId());
                            Rs2GameObject.interact(trapdoor, "Climb-down");
                        }
                        else
                        {
                            Microbot.log("Trapdoor null");
                        }
                    }
                    else if (inBasement)
                    {
                        WorldPoint altarLoc = _config.SelectedCreature().getAltarLocation();
                        WorldPoint altarSE = new WorldPoint(altarLoc.getX() + 5, altarLoc.getY() - 5, altarLoc.getPlane());
                        WorldPoint altarNW = new WorldPoint(altarLoc.getX() - 5, altarLoc.getY() + 5, altarLoc.getPlane());
                        if (!Rs2Walker.isInArea(altarSE, altarNW))
                        {
                            Rs2Walker.walkTo(_config.SelectedCreature().getAltarLocation());
                        }
                        else
                        {
                            Microbot.log("Arrived in basement! Switching state");
                            currentState = State.CREATURE_CREATION_PROCESS;
                            arrivedInTower = false;
                            break;
                        }
                    }
                }
                break;

            case CREATURE_CREATION_PROCESS:
                if (Rs2Dialogue.hasContinue())
                {
                    Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                    sleepGaussian(800, 300);
                    break;
                }

                if (Rs2Inventory.isFull())
                {
                    currentState = State.MOVING_TO_BANK;
                    break;
                }

                switch (_config.SelectedCreature())
                {
                    case UNICOW:
                        HandleCreature(unicowLootParams, ItemID.COW_HIDE, ItemID.UNICORN_HORN);
                        break;

                    case SPIDINE:
                        HandleCreature(spidineLootParams, ItemID.RAW_SARDINE, ItemID.RED_SPIDERS_EGGS);
                        break;
                }
                break;

        }
    }

    void HandleFoodAtBank(boolean preferLowestHealing)
    {
        // Eat any food in inventory first
        List<Rs2ItemModel> foodInInventory = Rs2Inventory.getInventoryFood();
        if (!foodInInventory.isEmpty())
        {
            Rs2Inventory.interact(foodInInventory.stream().findAny().get().getId(), "eat");
            return;
        }

        if (preferLowestHealing)
        {
            for (Rs2Food food : Arrays.stream(Rs2Food.values()).sorted(Comparator.comparingInt(Rs2Food::getHeal)).collect(Collectors.toList()))
            {
                if (Rs2Inventory.hasItem(food.getId()))
                {
                    Rs2Inventory.interact(food.getId(), "eat");
                    Rs2Inventory.waitForInventoryChanges(2000);
                    break;
                }

                if (Rs2Bank.hasItem(food.getId()))
                {
                    Rs2Bank.withdrawOne(food.getId());
                    Rs2Inventory.waitForInventoryChanges(2000);
                    Rs2Inventory.interact(food.getId(), "eat");
                    Rs2Inventory.waitForInventoryChanges(2000);
                    sleepGaussian(1000, 400);
                    break;
                }
            }
            return;
        }
        for (Rs2Food food : Arrays.stream(Rs2Food.values()).sorted(Comparator.comparingInt(Rs2Food::getHeal).reversed()).collect(Collectors.toList()))
        {
            if (Rs2Bank.hasItem(food.getId()))
            {
                Rs2Bank.withdrawOne(food.getId());
                Rs2Inventory.waitForInventoryChanges(2000);
                Rs2Inventory.interact(food.getId(), "eat");
                Rs2Inventory.waitForInventoryChanges(2000);
                sleepGaussian(1000, 400);
                break;
            }
        }
    }

    void HandleCreature(LootingParameters params, int disposableItem, int secondaryItem)
    {
        if (Rs2GroundItem.lootItemsBasedOnNames(params))
        {
            return;
        }

        if (summonedCreature == null)
        {
            // NPC death cooldown - give loot a chance to spawn
            if (System.currentTimeMillis() - lastKillTime < 2000) {
                //Microbot.log("Waiting a couple seconds since kill time before acting");
                return;
            }

            // We need at least 1 raw sardine/cow hide to create a creature
            if (Rs2Inventory.hasItem(disposableItem))
            {
                Rs2Inventory.useItemOnObject(disposableItem, altarObjectId);
                Rs2Inventory.waitForInventoryChanges(3000);
                Rs2Inventory.useItemOnObject(secondaryItem, altarObjectId);
                Rs2Inventory.waitForInventoryChanges(3000);
                Rs2GameObject.interact(altarObjectId, "Activate");
                sleepUntil(() -> { summonedCreature = Rs2Npc.getNpcs()
                        .filter(npc -> npc != null
                                && (npc.getInteracting() == null || npc.getInteracting() == Microbot.getClient().getLocalPlayer()))
                        .findFirst().orElse(null);
                    return summonedCreature != null;
                }, 5000);
                Microbot.log("Summoned creature");
            }
            else
            {
                // If there's loot we missed that we want to grab before we leave
                if (Rs2GroundItem.lootItemsBasedOnNames(params))
                {
                    return;
                }

                currentState = State.MOVING_TO_BANK;
            }
        }
        else if (!Rs2Combat.inCombat())
        {
            if (summonedCreature != null && !summonedCreature.isDead())
            {
                Rs2Npc.attack(summonedCreature);
            }
            else
            {
                lastKillTime = System.currentTimeMillis();
                summonedCreature = null;
            }
        }
    }
}
