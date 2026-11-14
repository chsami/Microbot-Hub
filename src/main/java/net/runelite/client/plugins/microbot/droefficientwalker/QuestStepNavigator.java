package net.runelite.client.plugins.microbot.droefficientwalker;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.QuestState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.Microbot;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Coordinates Quest Helper navigation, item selection, and target interactions.
 */
final class QuestStepNavigator
{
    private final Object selectedQuest;
    private Object activeStep;
    private String instruction;
    private WorldPoint stepDestination;
    private Object requestIdentity;
    private List<WorldPoint> approachTiles = Collections.emptyList();
    private QuestDialogueController dialogueController;
    private QuestItemResolver selectedItem;
    private boolean waitingForStepAdvance;
    private boolean actionDispatched;
    private boolean targetSeen;

    QuestStepNavigator(Object selectedQuest, EfficientWalker walker)
    {
        this.selectedQuest = selectedQuest;
        this.requestIdentity = walker.getRequestIdentity();
    }

    boolean tickQuestStep(Client client, PluginManager pluginManager, EfficientWalker walker)
    {
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            return true;
        }

        // RuneLite can briefly report LOGGED_IN before the local player object is available
        // during login/world transitions.  The shipped coordinator dereferences the player later
        // when checking quest approach tiles, so wait for that object instead of throwing.
        if (client.getLocalPlayer() == null)
        {
            Microbot.status = "Waiting for local player before quest automation.";
            return true;
        }

        if (walker.getRequestIdentity() != this.requestIdentity)
        {
            clearSelectedItem(client);
            Microbot.status = "Quest automation stopped for another walk.";
            return false;
        }

        try
        {
            Method getState = this.selectedQuest.getClass().getMethod("getState", Client.class);
            if (getState.invoke(this.selectedQuest, client) == QuestState.FINISHED)
            {
                clearSelectedItem(client);
                walker.cancel();
                Microbot.status = "Quest complete. Automation stopped.";
                return false;
            }
        }
        catch (ReflectiveOperationException ex)
        {
            throw new IllegalStateException(
                "Cannot read Quest Helper's quest state. Update Quest Helper, then resume.");
        }

        if (QuestHelperBridge.getSelectedQuest(pluginManager) != this.selectedQuest)
        {
            throw new IllegalStateException(
                "Selected quest changed. Press Walk to quest step to start the selected quest.");
        }

        if (walker.hasBankSupplyPlan())
        {
            return true;
        }

        if (QuestDialogueController.isDialogueOpen(client))
        {
            if (!this.approachTiles.isEmpty())
            {
                walker.cancel();
                this.requestIdentity = walker.getRequestIdentity();
                this.approachTiles = Collections.emptyList();
            }

            if (this.selectedItem != null)
            {
                this.selectedItem.clearSelection(client);
                this.selectedItem = null;
            }

            if (this.dialogueController == null)
            {
                this.dialogueController = new QuestDialogueController(this.selectedQuest, client.getTickCount());
            }
            this.dialogueController.tickDialogue(client, pluginManager);
            Microbot.status = "Automating quest dialogue.";
            return true;
        }

        // Quest Helper's dialogue/cutscene guard used by the shipped build.
        if (client.getVarbitValue(542) != 0)
        {
            return true;
        }

        Object step = QuestHelperBridge.getActiveStep(this.selectedQuest);
        if (step == null)
        {
            clearSelectedItem(client);
            walker.cancel();
            this.requestIdentity = walker.getRequestIdentity();
            this.approachTiles = Collections.emptyList();
            Microbot.status = "Waiting for Quest Helper's next step. Cancel walk stops automation.";
            return true;
        }

        String instruction = QuestItemResolver.getInstructionText(step);
        WorldPoint definedPoint = QuestHelperBridge.getStepWorldPoint(step);

        if (step != this.activeStep || !Objects.equals(this.stepDestination, definedPoint) || !Objects.equals(this.instruction, instruction))
        {
            clearSelectedItem(client);
            walker.cancel();
            this.requestIdentity = walker.getRequestIdentity();
            this.approachTiles = Collections.emptyList();
            this.activeStep = step;
            this.stepDestination = definedPoint;
            this.instruction = instruction;
            this.waitingForStepAdvance = false;
            this.actionDispatched = false;
            this.targetSeen = false;
            this.dialogueController = null;
        }

        if (this.waitingForStepAdvance)
        {
            Microbot.status = "Waiting for the quest step to advance. Cancel walk stops automation.";
            return true;
        }

        // Steps with no destination that are not NPC/Object steps can describe inventory-only work.
        if (definedPoint == null && !isNpcOrObjectStep(step))
        {
            QuestItemResolver.InventoryItemAction combination = QuestItemResolver.resolveInventoryAction(client, step);
            if (combination.targetItem != null)
            {
                if (this.selectedItem == null)
                {
                    if (!walker.selectInventoryItem(combination.selectedItem))
                    {
                        throw new IllegalStateException(
                            "Open inventory and clear any selected item or spell, then resume.");
                    }
                    this.selectedItem = combination.selectedItem;
                    Microbot.status = "Selecting " + this.selectedItem.name + " for the quest combination.";
                    return true;
                }

                if (this.selectedItem.itemId != combination.selectedItem.itemId || this.selectedItem.inventorySlot != combination.selectedItem.inventorySlot)
                {
                    throw new IllegalStateException(
                        "The inventory combination changed before use. Check inventory, then resume.");
                }

                if (!this.selectedItem.isSelected(client))
                {
                    if (client.isWidgetSelected())
                    {
                        throw new IllegalStateException(
                            "Another item or spell was selected. Clear it, then resume.");
                    }
                    Microbot.status =
                        "Waiting for inventory item selection. Cancel walk stops automation.";
                    return true;
                }
            }

            if (!walker.performInventoryItemAction(combination))
            {
                throw new IllegalStateException(
                    "Cannot send the requested inventory action. Open inventory and follow Quest Helper manually, then resume.");
            }

            this.selectedItem = null;
            this.actionDispatched = true;
            this.waitingForStepAdvance = true;
            Microbot.status = combination.action + " " + combination.selectedItem.name
                + " requested. Waiting for quest progress.";
            return true;
        }

        QuestHelperBridge.QuestTarget target = QuestHelperBridge.resolveQuestTarget(pluginManager, client);

        if (!this.approachTiles.isEmpty() && walker.getStatus() == EfficientWalker.Status.BLOCKED)
        {
            throw new IllegalStateException(
                "Quest route is blocked. Resolve the walking error, then press Walk to quest step.");
        }

        boolean interactive = target.npcStep || target.objectStep;
        if (interactive && target.interactionLocation == null && this.targetSeen)
        {
            clearSelectedItem(client);
            walker.cancel();
            this.requestIdentity = walker.getRequestIdentity();
            this.approachTiles = Collections.emptyList();
            Microbot.status = "Waiting for the quest target to reappear.";
            return true;
        }

        this.targetSeen |= target.interactionLocation != null;

        final List<WorldPoint> approach;
        if (!interactive)
        {
            approach = Collections.singletonList(target.destination);
        }
        else if (target.interactionLocation == null)
        {
            approach = SceneInteractionTiles.tilesAround(target.destination, target.objectSize);
        }
        else if (target.objectStep)
        {
            approach = SceneInteractionTiles.findApproachTiles(client, target.object);
        }
        else
        {
            approach = SceneInteractionTiles.findApproachTiles(client, target.interactionLocation);
        }

        if (approach.isEmpty())
        {
            throw new IllegalStateException(
                "Cannot find a safe quest approach tile. Move closer, then resume.");
        }

        if ((!interactive || target.interactionLocation != null)
            && approach.contains(client.getLocalPlayer().getWorldLocation()))
        {
            if (!this.approachTiles.isEmpty())
            {
                walker.cancel();
                this.requestIdentity = walker.getRequestIdentity();
                this.approachTiles = Collections.emptyList();
            }

            if (!interactive)
            {
                this.waitingForStepAdvance = true;
                Microbot.status =
                    "Quest point reached. Waiting for the next step; follow any manual instructions.";
                return true;
            }

            QuestItemResolver itemUse = QuestItemResolver.resolveTargetItem(client, target);
            if (itemUse != null)
            {
                if (this.selectedItem == null)
                {
                    if (!walker.selectInventoryItem(itemUse))
                    {
                        throw new IllegalStateException(
                            "Open inventory and clear any selected item or spell, then resume quest automation.");
                    }
                    this.selectedItem = itemUse;
                    Microbot.status = "Selecting " + itemUse.name + " for the quest target.";
                    return true;
                }

                if (itemUse.itemId != this.selectedItem.itemId || itemUse.inventorySlot != this.selectedItem.inventorySlot)
                {
                    throw new IllegalStateException(
                        "The quest item changed before use. Check inventory, then resume.");
                }

                if (!this.selectedItem.isSelected(client))
                {
                    if (client.isWidgetSelected())
                    {
                        throw new IllegalStateException(
                            "Another item or spell was selected. Clear it, then resume.");
                    }
                    Microbot.status = "Waiting for quest item selection. Cancel walk stops automation.";
                    return true;
                }

                if (!walker.useSelectedItemOnQuestTarget(this.selectedItem, target))
                {
                    throw new IllegalStateException(
                        "Cannot use the quest item on this visible target. Interact manually, then resume.");
                }

                Microbot.status =
                    "Use " + itemUse.name + " requested. Waiting for quest progress.";
                this.selectedItem = null;
            }
            else if (target.objectStep)
            {
                String action = walker.interactQuestObject(target.object, target.questStep);
                if (action == null)
                {
                    throw new IllegalStateException(
                        "Cannot interact with the quest object. Make it visible, then resume.");
                }
                Microbot.status = action + " requested. Waiting for quest progress.";
            }
            else
            {
                String action = walker.interactQuestNpc(target.npc, target.questStep);
                if (action == null)
                {
                    throw new IllegalStateException(
                        "Cannot interact with the quest NPC. Make it visible, then resume.");
                }
                Microbot.status = action + " requested. Waiting for quest progress.";
            }

            this.actionDispatched = true;
            this.waitingForStepAdvance = true;
            return true;
        }

        if (this.selectedItem != null)
        {
            clearSelectedItem(client);
        }

        if (!approach.equals(this.approachTiles))
        {
            this.approachTiles = approach;
            if (interactive)
            {
                walker.followSuppliedRoute(approach);
            }
            else
            {
                walker.walkTo(target.destination);
            }
            this.requestIdentity = walker.getRequestIdentity();
        }

        return true;
    }

    /** True for Quest Helper NPC/Object step classes, including subclasses. */
    private static boolean isNpcOrObjectStep(Object step)
    {
        for (Class<?> type = step.getClass(); type != null; type = type.getSuperclass())
        {
            String simpleName = type.getSimpleName();
            if ("NpcStep".equals(simpleName) || "ObjectStep".equals(simpleName))
            {
                return true;
            }
        }
        return false;
    }

    boolean pause(Client client, EfficientWalker walker)
    {
        if (this.dialogueController != null)
        {
            this.dialogueController.markPaused(client.getTickCount());
        }
        clearSelectedItem(client);

        if (walker.getRequestIdentity() != this.requestIdentity)
        {
            return false;
        }

        if (!this.approachTiles.isEmpty())
        {
            walker.cancel();
            this.requestIdentity = walker.getRequestIdentity();
            this.approachTiles = Collections.emptyList();
        }
        return true;
    }

    boolean isCurrentStepFailure(String message, EfficientWalker walker)
    {
        if (!this.actionDispatched || walker.getRequestIdentity() != this.requestIdentity || TravelFailureMessages.explainActionFailure(message) == null)
        {
            return false;
        }

        Object activeStep = QuestHelperBridge.getActiveStep(this.selectedQuest);
        if (activeStep != this.activeStep || !Objects.equals(this.instruction, QuestItemResolver.getInstructionText(activeStep)))
        {
            return false;
        }

        String instruction = this.instruction == null ? "" : this.instruction.toLowerCase(Locale.ROOT);
        return !TravelFailureMessages.explainActionFailure(message).contains("unlock access")
            || (!instruction.startsWith("try to ") && !instruction.startsWith("attempt to "));
    }

    void onWidgetGroupLoaded(int tick)
    {
        if (this.dialogueController != null)
        {
            this.dialogueController.onWidgetGroupLoaded(tick);
        }
    }

    void clearSelectedItem(Client client)
    {
        if (this.selectedItem != null)
        {
            this.selectedItem.clearSelection(client);
            this.selectedItem = null;
        }
    }
}
