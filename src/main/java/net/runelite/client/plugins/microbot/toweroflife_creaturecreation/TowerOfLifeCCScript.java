package net.runelite.client.plugins.microbot.toweroflife_creaturecreation;

import net.runelite.api.NPC;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
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
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.awt.event.KeyEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

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

    boolean looting = false;
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
    List<Rs2NpcModel> targetList = new CopyOnWriteArrayList<>();

    public boolean run(TowerOfLifeCCConfig config) {
        Microbot.enableAutoRunOn = false;

        InitialiseAntiban();
        depositedLoot = false;
        arrivedInTower = false;
        targetList.clear();

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
        targetList.clear();
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

                    switch (_config.SelectedCreature())
                    {
                        case UNICOW:
                            if (!Rs2Bank.hasItem(ItemID.COW_HIDE))
                            {
                                Microbot.showMessage("No cowhides found in bank! Shutting down.");
                                this.shutdown();
                                break;
                            }

                            //if (_config.ArdougneMediumDiaryDone())
                            if (Microbot.getVarbitValue(VarbitID.ARDOUGNE_DIARY_MEDIUM_COMPLETE) == 1)
                            {
                                int numItemsToWithdraw = Rs2Inventory.emptySlotCount() - 1;
                                Rs2Bank.withdrawX(ItemID.COW_HIDE, numItemsToWithdraw);
                                Rs2Inventory.waitForInventoryChanges(3000);
                            }
                            else
                            {
                                Rs2Bank.withdrawX(ItemID.COW_HIDE, 7);
                                Rs2Inventory.waitForInventoryChanges(3000);
                            }
                            Rs2Bank.withdrawOne(ItemID.UNICORN_HORN);
                            Rs2Inventory.waitForInventoryChanges(3000);

                            Rs2Bank.closeBank();
                            currentState = State.MOVING_TO_ALTAR;
                            depositedLoot = false;
                            break;

                        case SPIDINE:
                            if (!Rs2Bank.hasItem(ItemID.RAW_SARDINE))
                            {
                                Microbot.showMessage("No raw sardines found in bank! Shutting down.");
                                this.shutdown();
                                break;
                            }

                            //if (_config.ArdougneMediumDiaryDone())
                            if (Microbot.getVarbitValue(VarbitID.ARDOUGNE_DIARY_MEDIUM_COMPLETE) == 1)
                            {
                                int numItemsToWithdraw = Rs2Inventory.emptySlotCount() - 1;
                                Rs2Bank.withdrawX(ItemID.RAW_SARDINE, numItemsToWithdraw);
                                Rs2Inventory.waitForInventoryChanges(3000);
                            }
                            else
                            {
                                Rs2Bank.withdrawX(ItemID.RAW_SARDINE, 4);
                                Rs2Inventory.waitForInventoryChanges(3000);
                            }
                            Rs2Bank.withdrawOne(ItemID.RED_SPIDERS_EGGS);
                            Rs2Inventory.waitForInventoryChanges(3000);

                            Rs2Bank.closeBank();
                            currentState = State.MOVING_TO_ALTAR;
                            depositedLoot = false;
                            break;
                    }
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
                        looting = Rs2GroundItem.lootItemsBasedOnNames(unicowLootParams);

                        if (targetList.isEmpty())
                        {
                            // We need at least 1 cow hide to create a creature
                            if (Rs2Inventory.hasItem(ItemID.COW_HIDE))
                            {
                                Rs2Inventory.useItemOnObject(ItemID.UNICORN_HORN, altarObjectId);
                                Rs2Inventory.waitForInventoryChanges(3000);
                                Rs2Inventory.useItemOnObject(ItemID.COW_HIDE, altarObjectId);
                                Rs2Inventory.waitForInventoryChanges(3000);

                                Rs2GameObject.interact(altarObjectId, "Activate");
                                sleepUntil(() -> !targetList.isEmpty(), 5000);
                                break;
                            }
                            else if (!looting)
                            {
                                currentState = State.MOVING_TO_BANK;
                                break;
                            }
                        }
                        else if (!Rs2Combat.inCombat())
                        {
                            Rs2NpcModel target = targetList.stream().findAny().orElse(null);
                            assert target != null;

                            if (!target.isDead())
                            {
                                Rs2Npc.attack(target);
                            }
                        }

                        break;

                    case SPIDINE:
                        looting = Rs2GroundItem.lootItemsBasedOnNames(spidineLootParams);

                        if (targetList.isEmpty())
                        {
                            // We need at least 1 raw sardine to create a creature
                            if (Rs2Inventory.hasItem(ItemID.RAW_SARDINE))
                            {
                                Rs2Inventory.useItemOnObject(ItemID.RAW_SARDINE, altarObjectId);
                                Rs2Inventory.waitForInventoryChanges(3000);
                                Rs2Inventory.useItemOnObject(ItemID.RED_SPIDERS_EGGS, altarObjectId);
                                Rs2Inventory.waitForInventoryChanges(3000);

                                Rs2GameObject.interact(altarObjectId, "Activate");
                                sleepUntil(() -> !targetList.isEmpty(), 5000);
                                break;
                            }
                            else if (!looting)
                            {
                                currentState = State.MOVING_TO_BANK;
                                break;
                            }
                        }
                        else if (!Rs2Combat.inCombat())
                        {
                            Rs2NpcModel target = targetList.stream().findAny().orElse(null);
                            assert target != null;

                            if (!target.isDead())
                            {
                                Rs2Npc.attack(target);
                            }
                        }

                        break;
                }

                break;
        }
    }

    public void TryAddNpcToTargets(NPC _npc, TowerOfLifeCCConfig _config)
    {
        if (_npc == null) return;

        switch (_config.SelectedCreature())
        {
            case UNICOW:
                if (_npc.getId() == NpcID.TOL_UNICOW
                        && (_npc.getInteracting() == null || _npc.getInteracting() == Microbot.getClient().getLocalPlayer()))
                {
                    targetList.add(new Rs2NpcModel(_npc));
                    Microbot.log("Added " + _npc.getName() + " to targets.");
                }
                break;

            case SPIDINE:
                if (_npc.getId() == NpcID.TOL_SPIDINE
                        && (_npc.getInteracting() == null || _npc.getInteracting() == Microbot.getClient().getLocalPlayer()))
                {
                    targetList.add(new Rs2NpcModel(_npc));
                    Microbot.log("Added " + _npc.getName() + " to targets.");
                }
                break;
        }
    }

    public void RemoveNpcFromTargets(NPC _npc)
    {
        if (targetList.removeIf(model -> model.getRuneliteNpc() == _npc))
        {
            Microbot.log("Removed npc from targets: " + _npc.getName());
        }
    }
}
