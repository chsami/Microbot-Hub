package net.runelite.client.plugins.microbot.droefficientwalker.engine;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.ObjectComposition;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

/** Resolves the inventory items and actions identified by a Quest Helper instruction. */
final class QuestItemResolver {
   final int itemId;
   final int inventorySlot;
   final String name;

   QuestItemResolver(int itemId, int inventorySlot, String name) {
      this.itemId = itemId;
      this.inventorySlot = inventorySlot;
      this.name = name;
   }

   static String getInstructionText(Object step) {
      try {
         Object instructionLines = QuestHelperBridge.invokeNoArgMethod(step, "getText");
         if (instructionLines instanceof List<?> && !((List<?>)instructionLines).isEmpty()) {
            return Text.removeTags(String.valueOf(((List<?>)instructionLines).get(0))).trim();
         }
      } catch (ReflectiveOperationException failure) {
         throw interactionFailure("Cannot read the current instruction");
      }

      return "";
   }

   static QuestItemResolver resolveTargetItem(Client client, QuestHelperBridge.QuestTarget target) {
      String instruction = getInstructionText(target.questStep);
      if (!Pattern.compile("\\buse\\s+.+?\\s+(on|with)\\s+", 2).matcher(instruction).find()) {
         return null;
      }

      Matcher itemUse = Pattern.compile(
            "^(?:right[- ]click\\s+)?use\\s+(.+?)\\s+on\\s+(.+?)(?:[.!]|$)",
            2
         )
         .matcher(instruction);
      if (!itemUse.find()) {
         throw interactionFailure(
            "The item-use instruction includes another action or target"
         );
      }

      String itemText = normalizeItemText(itemUse.group(1));
      String targetText = normalizeItemText(itemUse.group(2));
      if (Pattern.compile("\\b(and|or|then|all)\\b|[,;]", 2).matcher(itemUse.group()).find()) {
         throw interactionFailure(
            "This instruction names multiple items or actions"
         );
      }

      String targetName = target.npc == null ? null : target.npc.getName();
      if (target.object != null) {
         ObjectComposition definition = client.getObjectDefinition(target.object.getId());
         if (definition != null && definition.getImpostorIds() != null) {
            definition = definition.getImpostor();
         }

         targetName = definition == null ? null : definition.getName();
      }

      if (targetName != null && containsWholePhrase(targetText, normalizeItemText(targetName))) {
         return resolveInventoryItem(client, target.questStep, itemText, true);
      } else {
         throw interactionFailure(
            "The instruction's item-use target does not match the highlighted target"
         );
      }
   }

   static QuestItemResolver resolveInventoryItem(Client client, Object step, String itemText, boolean requireHighlighted) {
      String normalizedItemText = normalizeItemText(itemText);
      ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
      if (inventory == null) {
         throw interactionFailure(ItemText.INVENTORY_UNAVAILABLE.value());
      }

      try {
         String requirementsMethod = ItemText.REQUIREMENTS_METHOD.value();
         Collection<?> requirements = (Collection<?>) QuestHelperBridge.invokeNoArgMethod(step, requirementsMethod);
         String iconItemField = ItemText.ICON_ITEM_FIELD.value();
         int iconItemId = (Integer) QuestDialogueController.readInheritedField(step, iconItemField);
         LinkedHashMap<Integer, QuestItemResolver> candidates = new LinkedHashMap<>();
         boolean matchedRequirement = false;

         for (Object requirement : requirements) {
            String requirementType = requirement.getClass().getSimpleName();
            String itemRequirementType = ItemText.SUPPORTED_REQUIREMENT_CLASS.value();
            if (!requirementType.equals(itemRequirementType)) {
               continue;
            }
            String actualItemMethod = ItemText.ACTUAL_ITEM_METHOD.value();
            if (!Boolean.TRUE.equals(QuestHelperBridge.invokeNoArgMethod(requirement, actualItemMethod))) {
               continue;
            }
            String equippedMethod = ItemText.EQUIPPED_ITEM_METHOD.value();
            if (Boolean.TRUE.equals(QuestHelperBridge.invokeNoArgMethod(requirement, equippedMethod))) {
               continue;
            }

            Class<?> renderOwner = requirement.getClass();
            String renderMethod = ItemText.RENDER_HIGHLIGHTS_METHOD.value();
            if (!Boolean.TRUE.equals(renderOwner.getMethod(renderMethod, Client.class).invoke(requirement, client))) {
               continue;
            }

            String idsMethod = ItemText.ITEM_IDS_METHOD.value();
            Collection<?> itemIds = (Collection<?>) QuestHelperBridge.invokeNoArgMethod(requirement, idsMethod);
            String nameMethod = ItemText.ITEM_NAME_METHOD.value();
            String requirementName = String.valueOf(QuestHelperBridge.invokeNoArgMethod(requirement, nameMethod));
            boolean nameMatches = containsWholePhrase(normalizedItemText, normalizeItemText(requirementName))
               || containsWholePhrase(normalizeItemText(requirementName), normalizedItemText);
            for (Object itemIdValue : itemIds) {
               int itemId = ((Number) itemIdValue).intValue();
               if (itemId >= 0) {
                  ItemComposition definition = client.getItemDefinition(itemId);
                  if (definition != null) {
                     // Keep every definition lookup and comparison after an earlier name matched.
                     nameMatches |= containsWholePhrase(normalizedItemText, normalizeItemText(definition.getName()));
                  }
               }
            }

            Class<?> highlightOwner = requirement.getClass();
            String highlightMethod = ItemText.INVENTORY_HIGHLIGHT_METHOD.value();
            boolean highlighted = Boolean.TRUE.equals(highlightOwner.getMethod(highlightMethod, Client.class).invoke(requirement, client));
            if (!nameMatches || (requireHighlighted && !highlighted && !itemIds.contains(iconItemId))) {
               continue;
            }

            matchedRequirement = true;
            String quantityMethod = ItemText.QUANTITY_METHOD.value();
            int requiredQuantity = ((Number) QuestHelperBridge.invokeNoArgMethod(requirement, quantityMethod)).intValue();
            if (requiredQuantity < 1) {
               throw interactionFailure(ItemText.UNCLEAR_QUANTITY.value());
            }
            int availableQuantity = 0;
            for (Item item : inventory.getItems()) {
               if (item != null && itemIds.contains(item.getId())) {
                  availableQuantity += item.getQuantity();
               }
            }
            if (availableQuantity < requiredQuantity) {
               int missingQuantity = requiredQuantity - availableQuantity;
               StringBuilder message = new StringBuilder();
               message.append(ItemText.MISSING_QUANTITY_PREFIX.value());
               message.append(missingQuantity);
               message.append(ItemText.MISSING_QUANTITY_JOINER.value());
               message.append(requirementName);
               message.append(ItemText.MISSING_QUANTITY_SUFFIX.value());
               throw interactionFailure(message.toString());
            }

            // The original reads the current array length for each slot iteration.
            for (int slot = 0; slot < inventory.getItems().length; slot++) {
               Item item = inventory.getItem(slot);
               if (item != null && itemIds.contains(item.getId()) && item.getQuantity() > 0) {
                  ItemComposition definition = client.getItemDefinition(item.getId());
                  if (definition != null && definition.getNote() == -1) {
                     candidates.putIfAbsent(item.getId(), new QuestItemResolver(item.getId(), slot, definition.getName()));
                  }
               }
            }
         }

         if (candidates.size() != 1) {
            String message = matchedRequirement
               ? ItemText.AMBIGUOUS_INVENTORY_ITEM.value()
               : ItemText.UNSUPPORTED_ITEM_METADATA.value();
            throw interactionFailure(message);
         }
         return candidates.values().iterator().next();
      } catch (Exception ignored) {
         // Preserve the original policy: every metadata failure becomes the same diagnostic, without its cause.
      }
      throw interactionFailure(ItemText.METADATA_READ_FAILURE.value());
   }

   static QuestItemResolver.InventoryItemAction resolveInventoryAction(Client client, Object step) {
      String instruction = getInstructionText(step);
      if (Pattern.compile("\\b(and|or|then|all)\\b|[,;]", 2).matcher(instruction).find()) {
         throw interactionFailure(
            "This inventory instruction contains multiple items or actions"
         );
      }

      Matcher itemCombination = Pattern.compile(
            "^(?:use\\s+(.+?)\\s+on|combine\\s+(.+?)\\s+with)\\s+(.+?)\\.?$",
            2
         )
         .matcher(instruction);
      if (itemCombination.matches()) {
         QuestItemResolver selectedItem = resolveInventoryItem(client, step, itemCombination.group(1) == null ? itemCombination.group(2) : itemCombination.group(1), false);
         QuestItemResolver targetItem = resolveInventoryItem(client, step, itemCombination.group(3), false);
         if (selectedItem.inventorySlot == targetItem.inventorySlot) {
            throw interactionFailure(
               "The two inventory items resolve to the same slot"
            );
         } else {
            return new QuestItemResolver.InventoryItemAction(selectedItem, targetItem, "Use");
         }
      } else {
         Matcher singleAction = Pattern.compile("^([a-z]+(?:-[a-z]+)?)\\s+(.+?)\\.?$", 2)
            .matcher(instruction);
         if (singleAction.matches() && !singleAction.group(1).equalsIgnoreCase("Use")) {
            QuestItemResolver item = resolveInventoryItem(client, step, singleAction.group(2), false);
            return new QuestItemResolver.InventoryItemAction(item, null, singleAction.group(1));
         } else {
            throw interactionFailure("Unsupported inventory instruction");
         }
      }
   }

   boolean isSelected(Client client) {
      Widget selectedWidget = client.getSelectedWidget();
      return client.isWidgetSelected() && selectedWidget != null && selectedWidget.getId() == 9764864 && selectedWidget.getIndex() == this.inventorySlot && selectedWidget.getItemId() == this.itemId;
   }

   void clearSelection(Client client) {
      if (this.isSelected(client)) {
         client.setWidgetSelected(false);
      }
   }

   private static boolean containsWholePhrase(String text, String phrase) {
      return !phrase.isEmpty() && (" " + text + " ").contains(" " + phrase + " ");
   }

   private static String normalizeItemText(String text) {
      if (text == null) {
         return ItemText.NULL_ITEM_TEXT.value();
      }
      String normalized = Text.removeTags(text).toLowerCase(Locale.ROOT);
      String parenthesesPattern = ItemText.PARENTHESES_PATTERN.value();
      String parenthesesReplacement = ItemText.PARENTHESES_REPLACEMENT.value();
      normalized = normalized.replaceAll(parenthesesPattern, parenthesesReplacement).replace('-', ' ');
      String articlesPattern = ItemText.ARTICLES_PATTERN.value();
      String articlesReplacement = ItemText.ARTICLES_REPLACEMENT.value();
      normalized = normalized.replaceAll(articlesPattern, articlesReplacement);
      String whitespacePattern = ItemText.WHITESPACE_PATTERN.value();
      String whitespaceReplacement = ItemText.WHITESPACE_REPLACEMENT.value();
      return normalized.replaceAll(whitespacePattern, whitespaceReplacement).trim();
   }

   private static IllegalStateException interactionFailure(String reason) {
      StringBuilder message = new StringBuilder();
      message.append(reason);
      message.append(ItemText.MANUAL_RESUME_SUFFIX.value());
      return new IllegalStateException(message.toString());
   }

   /** Public decoder entry point. */
   public static String lugruzusqx(byte[] encoded, byte[] key, int seed) {
      return decodeLegacyOuter(encoded, key, seed);
   }

   /** Mutates the input in its original two-step order, including partial mutation on an invalid key. */
   private static String decodeLegacyOuter(byte[] encoded, byte[] key, int seed) {
      byte[] seedDigits = Integer.toString(seed).getBytes();
      for (int index = 0; index < encoded.length; index++) {
         encoded[index] ^= seedDigits[index % seedDigits.length];
         encoded[index] ^= key[index % key.length];
      }
      return new String(encoded, StandardCharsets.UTF_16);
   }

   /** The legacy inner layer is Base64 followed by a 32-bit LCG XOR stream over UTF-16 code units. */
   private static String decodeLegacyInner(String encoded, int seed) {
      byte[] bytes = Base64.getDecoder().decode(encoded);
      char[] characters = new char[bytes.length >>> 1];
      for (int index = 0; index < characters.length; index++) {
         seed = seed * 1664525 + 1013904223;
         int encodedCharacter = (bytes[index * 2] & 255) << 8 | bytes[index * 2 + 1] & 255;
         characters[index] = (char) (encodedCharacter ^ seed >>> 16);
      }
      return new String(characters).intern();
   }

   /** Inverse of the inner decoder, used only to preserve legacy charset behavior and public decoder data. */
   private static String encodeLegacyInner(String text, int seed) {
      byte[] encoded = new byte[text.length() * 2];
      for (int index = 0; index < text.length(); index++) {
         seed = seed * 1664525 + 1013904223;
         int encodedCharacter = text.charAt(index) ^ seed >>> 16;
         encoded[index * 2] = (byte) (encodedCharacter >>> 8);
         encoded[index * 2 + 1] = (byte) encodedCharacter;
      }
      return Base64.getEncoder().encodeToString(encoded);
   }

   /** Readable literals; seeds retain the original behavior with non-ASCII default charset digits. */
   private enum ItemText {
      INVENTORY_UNAVAILABLE("Cannot read inventory", 1802127226, -398775787),
      REQUIREMENTS_METHOD("getRequirements", 1122403701, 1842405502),
      ICON_ITEM_FIELD("iconItemID", 1122403701, -471023933),
      SUPPORTED_REQUIREMENT_CLASS("ItemRequirement", 805764529, 2046326100),
      ACTUAL_ITEM_METHOD("isActualItem", 2017687760, -7076935),
      EQUIPPED_ITEM_METHOD("mustBeEquipped", 1755065072, 1973542402),
      RENDER_HIGHLIGHTS_METHOD("shouldRenderItemHighlights", 435191809, -876768105),
      ITEM_IDS_METHOD("getAllIds", 1196752055, 1095344888),
      ITEM_NAME_METHOD("getName", 1196752055, -949651123),
      INVENTORY_HIGHLIGHT_METHOD("shouldHighlightInInventory", 1182995241, 1567700950),
      QUANTITY_METHOD("getQuantity", 423234586, -754007493),
      UNCLEAR_QUANTITY("The required item quantity is unclear", 480230262, 689608844),
      MISSING_QUANTITY_PREFIX("Need ", 1905088758, -1355393263),
      MISSING_QUANTITY_JOINER(" more ", 1905088758, 616721786),
      MISSING_QUANTITY_SUFFIX(" in inventory", 1905088758, -1159840817),
      AMBIGUOUS_INVENTORY_ITEM("Cannot choose one supported inventory item", 1909083110, 820770384),
      UNSUPPORTED_ITEM_METADATA("Item metadata does not uniquely support the instruction", 980144589, -1232630619),
      METADATA_READ_FAILURE("Cannot read compatible Quest Helper item metadata", 1555112718, 210983694),
      NULL_ITEM_TEXT("", 903161730, -107064258),
      PARENTHESES_PATTERN("\\([^)]*\\)", 169191294, 2088083029),
      PARENTHESES_REPLACEMENT(" ", 169191294, -230047512),
      ARTICLES_PATTERN("\\b(the|a|an|your|some)\\b", 169191294, 1750928255),
      ARTICLES_REPLACEMENT(" ", 169191294, -298746478),
      WHITESPACE_PATTERN("\\s+", 169191294, 1678024745),
      WHITESPACE_REPLACEMENT(" ", 169191294, -635901252),
      MANUAL_RESUME_SUFFIX(". Follow Quest Helper manually, then press Walk to quest step to resume.", 453487196, -318145682);

      private final String text;
      private final int outerSeed;
      private final int innerSeed;

      ItemText(String text, int outerSeed, int innerSeed) {
         this.text = text;
         this.outerSeed = outerSeed;
         this.innerSeed = innerSeed;
      }

      private String value() {
         byte[] seedDigits = Integer.toString(outerSeed).getBytes();
         byte[] asciiDigits = Integer.toString(outerSeed).getBytes(StandardCharsets.US_ASCII);
         if (Arrays.equals(seedDigits, asciiDigits)) {
            return text.intern();
         }
         // The original fixed XOR key cancels when reconstructing the encoded text.
         // UTF_16 preserves its original BOM behavior, including the empty payload.
         byte[] encoded = encodeLegacyInner(text, innerSeed).getBytes(StandardCharsets.UTF_16);
         for (int index = 0; index < encoded.length; index++) {
            encoded[index] ^= asciiDigits[index % asciiDigits.length];
            encoded[index] ^= seedDigits[index % seedDigits.length];
         }
         return decodeLegacyInner(new String(encoded, StandardCharsets.UTF_16), innerSeed);
      }
   }

   static final class InventoryItemAction {
      final QuestItemResolver selectedItem;
      final QuestItemResolver targetItem;
      final String action;

      InventoryItemAction(QuestItemResolver selectedItem, QuestItemResolver targetItem, String action) {
         this.selectedItem = selectedItem;
         this.targetItem = targetItem;
         this.action = action;
      }
   }
}
