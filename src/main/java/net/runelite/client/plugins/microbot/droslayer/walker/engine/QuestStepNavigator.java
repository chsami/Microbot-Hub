package net.runelite.client.plugins.microbot.droslayer.walker.engine;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.QuestState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.Microbot;

final class QuestStepNavigator {
   private final Object selectedQuest;
   private Object activeStep;
   private String instruction;
   private WorldPoint stepDestination;
   private Object requestIdentity;
   private List<WorldPoint> approachTiles;
   private QuestDialogueController dialogueController;
   private QuestItemResolver selectedItem;
   private boolean waitingForStepAdvance;
   private boolean actionDispatched;
   private boolean targetSeen;

   // Exact legacy UTF-16 code-unit table, retained for non-ASCII-compatible default charsets.
   private static String legacyStringTable = ByteBuffer.wrap(Base64.getDecoder().decode(
      "OHs3WTEHNx8xADB6N2U1WjBhOB83WDFzNwIxdzBAN3M1WjB0OF83CjMcNl8yWzReN1ozUDN4MV44fTNKNmAyADRtN1szbzNYMXc4ZDNWNksyQzR/N3ozXTNnMXc4WDMO"
   )).asCharBuffer().toString();

   QuestStepNavigator(Object selectedQuest, WalkerEngine walker) {
      this.approachTiles = Collections.emptyList();
      this.selectedQuest = selectedQuest;
      this.requestIdentity = walker.getRequestIdentity();
   }

   boolean tickQuestStep(Client client, PluginManager pluginManager, WalkerEngine walker) {
      if (client.getGameState() != GameState.LOGGED_IN) {
         return true;
      }

      if (walker.getRequestIdentity() != this.requestIdentity) {
         this.clearSelectedItem(client);
         Microbot.status = "Quest automation stopped for another walk.";
         return false;
      }

      try {
         if (this.selectedQuest.getClass().getMethod("getState", Client.class).invoke(this.selectedQuest, client) == QuestState.FINISHED) {
            this.clearSelectedItem(client);
            walker.cancel();
            Microbot.status = "Quest complete. Automation stopped.";
            return false;
         }
      } catch (ReflectiveOperationException failure) {
         throw new IllegalStateException(
            "Cannot read Quest Helper's quest state. Update Quest Helper, then resume."
         );
      }

      if (QuestHelperBridge.getSelectedQuest(pluginManager) != this.selectedQuest) {
         throw new IllegalStateException(
            "Selected quest changed. Press Walk to quest step to start the selected quest."
         );
      }

      if (walker.hasBankSupplyPlan()) {
         return true;
      }

      if (QuestDialogueController.isDialogueOpen(client)) {
         if (!this.approachTiles.isEmpty()) {
            walker.cancel();
            this.requestIdentity = walker.getRequestIdentity();
            this.approachTiles = Collections.emptyList();
         }

         if (this.selectedItem != null) {
            this.selectedItem.clearSelection(client);
            this.selectedItem = null;
         }

         if (this.dialogueController == null) {
            this.dialogueController = new QuestDialogueController(this.selectedQuest, client.getTickCount());
         }

         this.dialogueController.tickDialogue(client, pluginManager);
         Microbot.status = "Automating quest dialogue.";
         return true;
      } else {
         if (client.getVarbitValue(542) != 0) {
            return true;
         }

         Object currentStep = QuestHelperBridge.getActiveStep(this.selectedQuest);
         if (currentStep == null) {
            this.clearSelectedItem(client);
            walker.cancel();
            this.requestIdentity = walker.getRequestIdentity();
            this.approachTiles = Collections.emptyList();
            Microbot.status = "Waiting for Quest Helper's next step. Cancel walk stops automation.";
            return true;
         }

         String currentInstruction = QuestItemResolver.getInstructionText(currentStep);
         WorldPoint currentDestination = QuestHelperBridge.getStepWorldPoint(currentStep);
         if (currentStep != this.activeStep || !Objects.equals(this.stepDestination, currentDestination) || !Objects.equals(this.instruction, currentInstruction)) {
            this.clearSelectedItem(client);
            walker.cancel();
            this.requestIdentity = walker.getRequestIdentity();
            this.approachTiles = Collections.emptyList();
            this.activeStep = currentStep;
            this.stepDestination = currentDestination;
            this.instruction = currentInstruction;
            this.waitingForStepAdvance = false;
            this.actionDispatched = false;
            this.targetSeen = false;
            this.dialogueController = null;
         }

         if (this.waitingForStepAdvance) {
            Microbot.status = "Waiting for the quest step to advance. Cancel walk stops automation.";
            return true;
         }

         if (currentDestination == null && !isNpcOrObjectStep(currentStep)) {
            QuestItemResolver.InventoryItemAction inventoryAction = QuestItemResolver.resolveInventoryAction(client, currentStep);
            if (inventoryAction.targetItem != null) {
               if (this.selectedItem == null) {
                  if (!walker.selectInventoryItem(inventoryAction.selectedItem)) {
                     throw new IllegalStateException(
                        "Open inventory and clear any selected item or spell, then resume."
                     );
                  }

                  this.selectedItem = inventoryAction.selectedItem;
                  String combinationItemName = this.selectedItem.name;
                  Microbot.status = "Selecting "
                     + combinationItemName
                     + " for the quest combination.";
                  return true;
               }

               if (this.selectedItem.itemId != inventoryAction.selectedItem.itemId || this.selectedItem.inventorySlot != inventoryAction.selectedItem.inventorySlot) {
                  throw new IllegalStateException(
                     "The inventory combination changed before use. Check inventory, then resume."
                  );
               }

               if (!this.selectedItem.isSelected(client)) {
                  if (client.isWidgetSelected()) {
                     throw new IllegalStateException(
                        "Another item or spell was selected. Clear it, then resume."
                     );
                  }

                  Microbot.status = "Waiting for inventory item selection. Cancel walk stops automation.";
                  return true;
               }
            }

            if (!walker.performInventoryItemAction(inventoryAction)) {
               throw new IllegalStateException(
                  "Cannot send the requested inventory action. Open inventory and follow Quest Helper manually, then resume."
               );
            }

            this.selectedItem = null;
            this.actionDispatched = true;
            this.waitingForStepAdvance = true;
            String inventoryItemName = inventoryAction.selectedItem.name;
            String inventoryActionName = inventoryAction.action;
            Microbot.status = inventoryActionName
               + " "
               + inventoryItemName
               + " requested. Waiting for quest progress.";
            return true;
         } else {
            QuestHelperBridge.QuestTarget target = QuestHelperBridge.resolveQuestTarget(pluginManager, client);
            if (!this.approachTiles.isEmpty() && walker.getStatus() == WalkerEngine.Status.BLOCKED) {
               throw new IllegalStateException(
                  "Quest route is blocked. Resolve the walking error, then press Walk to quest step."
               );
            }

            boolean interactiveTarget = target.npcStep || target.objectStep;
            if (interactiveTarget && target.interactionLocation == null && this.targetSeen) {
               this.clearSelectedItem(client);
               walker.cancel();
               this.requestIdentity = walker.getRequestIdentity();
               this.approachTiles = Collections.emptyList();
               Microbot.status = "Waiting for the quest target to reappear.";
               return true;
            }

            this.targetSeen = this.targetSeen | target.interactionLocation != null;
            List<WorldPoint> targetApproachTiles = !interactiveTarget
               ? Collections.singletonList(target.destination)
               : (target.interactionLocation == null ? SceneInteractionTiles.tilesAround(target.destination, target.objectSize) : (target.objectStep ? SceneInteractionTiles.findApproachTiles(client, target.object) : SceneInteractionTiles.findApproachTiles(client, target.interactionLocation)));
            if (targetApproachTiles.isEmpty()) {
               throw new IllegalStateException(
                  "Cannot find a safe quest approach tile. Move closer, then resume."
               );
            }

            if ((!interactiveTarget || target.interactionLocation != null) && targetApproachTiles.contains(client.getLocalPlayer().getWorldLocation())) {
               if (!this.approachTiles.isEmpty()) {
                  walker.cancel();
                  this.requestIdentity = walker.getRequestIdentity();
                  this.approachTiles = Collections.emptyList();
               }

               if (!interactiveTarget) {
                  this.waitingForStepAdvance = true;
                  Microbot.status = "Quest point reached. Waiting for the next step; follow any manual instructions.";
                  return true;
               }

               QuestItemResolver requiredItem = QuestItemResolver.resolveTargetItem(client, target);
               if (requiredItem != null) {
                  if (this.selectedItem == null) {
                     if (!walker.selectInventoryItem(requiredItem)) {
                        throw new IllegalStateException(
                           "Open inventory and clear any selected item or spell, then resume quest automation."
                        );
                     }

                     this.selectedItem = requiredItem;
                     String targetItemName = requiredItem.name;
                     Microbot.status = "Selecting "
                        + targetItemName
                        + " for the quest target.";
                     return true;
                  }

                  if (requiredItem.itemId != this.selectedItem.itemId || requiredItem.inventorySlot != this.selectedItem.inventorySlot) {
                     throw new IllegalStateException(
                        "The quest item changed before use. Check inventory, then resume."
                     );
                  }

                  if (!this.selectedItem.isSelected(client)) {
                     if (client.isWidgetSelected()) {
                        throw new IllegalStateException(
                           "Another item or spell was selected. Clear it, then resume."
                        );
                     }

                     Microbot.status = "Waiting for quest item selection. Cancel walk stops automation.";
                     return true;
                  }

                  if (!walker.useSelectedItemOnQuestTarget(this.selectedItem, target)) {
                     throw new IllegalStateException(
                        "Cannot use the quest item on this visible target. Interact manually, then resume."
                     );
                  }

                  String usedItemName = requiredItem.name;
                  Microbot.status = "Use "
                     + usedItemName
                     + " requested. Waiting for quest progress.";
                  this.selectedItem = null;
               } else if (target.objectStep) {
                  String objectAction = walker.interactQuestObject(target.object, target.questStep);
                  if (objectAction == null) {
                     throw new IllegalStateException(
                        "Cannot interact with the quest object. Make it visible, then resume."
                     );
                  }

                  Microbot.status = objectAction
                     + " requested. Waiting for quest progress.";
               } else {
                  String npcAction = walker.interactQuestNpc(target.npc, target.questStep);
                  if (npcAction == null) {
                     throw new IllegalStateException(
                        "Cannot interact with the quest NPC. Make it visible, then resume."
                     );
                  }

                  Microbot.status = npcAction
                     + " requested. Waiting for quest progress.";
               }

               this.actionDispatched = true;
               this.waitingForStepAdvance = true;
               return true;
            } else {
               if (this.selectedItem != null) {
                  this.clearSelectedItem(client);
               }

               if (!targetApproachTiles.equals(this.approachTiles)) {
                  this.approachTiles = targetApproachTiles;
                  if (interactiveTarget) {
                     walker.followSuppliedRoute(targetApproachTiles);
                  } else {
                     walker.walkTo(target.destination);
                  }

                  this.requestIdentity = walker.getRequestIdentity();
               }

               return true;
            }
         }
      }
   }

   private static boolean isNpcOrObjectStep(Object step) {
      for (Class<?> stepClass = step.getClass(); stepClass != null; stepClass = stepClass.getSuperclass()) {
         if (stepClass.getSimpleName().equals(resolveStepName("NpcStep", 20, 0, 807715701, -1012526419))) {
            return true;
         }
         if (stepClass.getSimpleName().equals(resolveStepName("ObjectStep", 28, 20, 336321487, 1179506886))) {
            return true;
         }
      }
      return false;
   }

   boolean pause(Client client, WalkerEngine walker) {
      if (this.dialogueController != null) {
         this.dialogueController.markPaused(client.getTickCount());
      }

      this.clearSelectedItem(client);
      if (walker.getRequestIdentity() != this.requestIdentity) {
         return false;
      }

      if (!this.approachTiles.isEmpty()) {
         walker.cancel();
         this.requestIdentity = walker.getRequestIdentity();
         this.approachTiles = Collections.emptyList();
      }

      return true;
   }

   boolean isCurrentStepFailure(String message, WalkerEngine walker) {
      if (this.actionDispatched && walker.getRequestIdentity() == this.requestIdentity && TravelFailureMessages.explainActionFailure(message) != null) {
         Object currentStep = QuestHelperBridge.getActiveStep(this.selectedQuest);
         if (currentStep == this.activeStep && Objects.equals(this.instruction, QuestItemResolver.getInstructionText(currentStep))) {
            String normalizedInstruction = this.instruction == null ? "" : this.instruction.toLowerCase(Locale.ROOT);
            return !TravelFailureMessages.explainActionFailure(message).contains("unlock access")
               || !normalizedInstruction.startsWith("try to ") && !normalizedInstruction.startsWith("attempt to ");
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   void onWidgetGroupLoaded(int groupId) {
      if (this.dialogueController != null) {
         this.dialogueController.onWidgetGroupLoaded(groupId);
      }
   }

   void clearSelectedItem(Client client) {
      if (this.selectedItem != null) {
         this.selectedItem.clearSelection(client);
         this.selectedItem = null;
      }
   }

   private static String resolveStepName(String literal, int length, int offset, int outerSeed, int textSeed) {
      String decimalSeed = Integer.toString(outerSeed);
      byte[] numericKey = decimalSeed.getBytes();
      if (Arrays.equals(numericKey, decimalSeed.getBytes(StandardCharsets.US_ASCII))) {
         return literal;
      }
      // Decode lazily at the original comparison site so unusual-charset failures keep their timing.
      return decodeLegacyInner(decodeLegacySlice(length, offset, numericKey), textSeed);
   }

   /** Original descriptor layout: big-endian character count, then big-endian table offset. */
   public static String decodeLegacyOuter(byte[] descriptor, int seed) {
      byte[] numericKey = Integer.toString(seed).getBytes();
      int length = (descriptor[0] & 255) << 24 | (descriptor[1] & 255) << 16
         | (descriptor[2] & 255) << 8 | descriptor[3] & 255;
      int offset = (descriptor[4] & 255) << 24 | (descriptor[5] & 255) << 16
         | (descriptor[6] & 255) << 8 | descriptor[7] & 255;
      return decodeLegacySlice(length, offset, numericKey);
   }

   private static String decodeLegacySlice(int length, int offset, byte[] numericKey) {
      byte[] payload = legacyStringTable.substring(offset, offset + length).getBytes(StandardCharsets.UTF_16BE);
      for (int index = 0; index < payload.length; index++) {
         payload[index] = (byte) (payload[index] ^ numericKey[index % numericKey.length]);
      }
      return new String(payload, StandardCharsets.UTF_16BE);
   }

   /** Original Base64/LCG layer, including signed-int overflow, odd-byte truncation and interning. */
   private static String decodeLegacyInner(String encoded, int seed) {
      byte[] payload = Base64.getDecoder().decode(encoded);
      char[] decoded = new char[payload.length >>> 1];
      for (int index = 0; index < decoded.length; index++) {
         seed = seed * 1664525 + 1013904223;
         decoded[index] = (char) ((((payload[index << 1] & 255) << 8)
            | (payload[(index << 1) + 1] & 255)) ^ (seed >>> 16));
      }
      return new String(decoded).intern();
   }
}
