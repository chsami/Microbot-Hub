package net.runelite.client.plugins.microbot.droefficientwalker.engine;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.util.Text;

/** Tracks dialogue changes and selects the single answer supported by the current Quest Helper step. */
final class QuestDialogueController {
   private static final int[][] CONTINUE_WIDGETS = {
      {231, 5}, {217, 5}, {193, 0}, {193, 3}, {11, 4}, {229, 0}, {229, 2}, {229, 4}
   };
   private final Object selectedQuest;
   private int lastDialogueTick;
   private int lastActionTick;
   private String lastActedSignature;
   private String pendingSignature;
   private int nextActionTick;
   private int pausedTick = -1;
   private static String legacyStringTable = buildLegacyStringTable();

   QuestDialogueController(Object selectedQuest, int currentTick) {
      this.selectedQuest = selectedQuest;
      this.lastDialogueTick = currentTick;
   }

   static boolean isDialogueOpen(Client client) {
      return getOptionsWidget(client) != null || getContinueWidget(client) != null;
   }

   void onWidgetGroupLoaded(int groupId) {
      if (groupId == 219 || groupId == 231 || groupId == 217 || groupId == 193 || groupId == 11 || groupId == 229) {
         this.lastActedSignature = null;
         this.pendingSignature = null;
      }
   }

   void markPaused(int currentTick) {
      if (this.pausedTick < 0) {
         this.pausedTick = currentTick;
      }
   }

   boolean tickDialogue(Client client, PluginManager pluginManager) {
      if (this.pausedTick >= 0) {
         int pausedDuration = client.getTickCount() - this.pausedTick;
         this.lastDialogueTick += pausedDuration;
         this.lastActionTick += pausedDuration;
         this.nextActionTick += pausedDuration;
         this.pausedTick = -1;
      }

      if (QuestHelperBridge.getSelectedQuest(pluginManager) != this.selectedQuest) {
         throw new IllegalStateException(
            "Selected quest changed. Press Walk to quest step to resume."
         );
      }

      Widget optionsWidget = getOptionsWidget(client);
      Widget continueWidget = getContinueWidget(client);
      int currentTick = client.getTickCount();
      if (optionsWidget == null && continueWidget == null) {
         this.pendingSignature = null;
         return currentTick - this.lastDialogueTick < 10;
      }

      this.lastDialogueTick = currentTick;
      StringBuilder signatureText = new StringBuilder();
      int widgetGroup = optionsWidget == null ? continueWidget.getId() >>> 16 : 219;

      for (int childIndex = 0; childIndex < 32; childIndex++) {
         Widget widget = client.getWidget(widgetGroup, childIndex);
         if (widget != null && !widget.isHidden()) {
            signatureText.append(childIndex).append(':').append(normalizeDialogueText(widget.getText())).append('|');
         }
      }

      if (optionsWidget != null && optionsWidget.getChildren() != null) {
         for (Widget child : optionsWidget.getChildren()) {
            if (child != null) {
               signatureText.append(normalizeDialogueText(child.getText())).append('|');
            }
         }
      }

      String visibleText = String.valueOf(signatureText);
      String signature = widgetGroup + ":" + visibleText;
      if (signature.equals(this.lastActedSignature)) {
         if (currentTick - this.lastActionTick >= 8) {
            throw new IllegalStateException(
               "Dialogue did not advance. Continue it manually, then press Walk to quest step to resume."
            );
         } else {
            return true;
         }
      } else {
         if (!signature.equals(this.pendingSignature)) {
            this.pendingSignature = signature;
            this.nextActionTick = currentTick + ThreadLocalRandom.current().nextInt(1, 4);
         }

         if (currentTick < this.nextActionTick) {
            return true;
         }

         if (optionsWidget != null) {
            Widget selectedOption = resolveDialogueOption(client, QuestHelperBridge.getActiveStep(this.selectedQuest), optionsWidget);
            if (!Rs2Dialogue.keyPressForDialogueOption(selectedOption.getText(), true)) {
               throw new IllegalStateException(
                  "Cannot read the dialogue option's key. Select the highlighted option manually, then resume."
               );
            }
         } else {
            Rs2Keyboard.keyPress(32);
         }

         this.lastActedSignature = signature;
         this.lastActionTick = currentTick;
         return true;
      }
   }

   static Widget resolveDialogueOption(Client client, Object step, Widget optionsWidget) {
      try {
         Widget[] options = optionsWidget.getChildren();
         String choicesField = DialogueText.STEP_CHOICES_FIELD.value();
         Object choicesContainer = readInheritedField(step, choicesField);
         String rulesField = DialogueText.CONTAINER_CHOICES_FIELD.value();
         List<?> rules = (List<?>) readInheritedField(choicesContainer, rulesField);
         String previousDialogField = DialogueText.LAST_DIALOG_FIELD.value();
         String previousDialog = (String) readInheritedField(step, previousDialogField);
         HashSet<Widget> matchingOptions = new HashSet<>();

         for (Object rule : rules) {
            String ruleType = rule.getClass().getSimpleName();
            String supportedType = DialogueText.SUPPORTED_RULE_CLASS.value();
            if (!ruleType.equals(supportedType)) {
               throw new IllegalStateException(DialogueText.UNSUPPORTED_RULE.value());
            }

            String expectedPreviousField = DialogueText.EXPECTED_PREVIOUS_LINE_FIELD.value();
            String expectedPrevious = (String) readInheritedField(rule, expectedPreviousField);
            if (expectedPrevious != null && (previousDialog == null || !previousDialog.contains(expectedPrevious))) {
               continue;
            }

            String exclusionsField = DialogueText.EXCLUDED_STRINGS_FIELD.value();
            List<?> excludedStrings = (List<?>) readInheritedField(rule, exclusionsField);
            String excludedGroupField = DialogueText.EXCLUDED_GROUP_FIELD.value();
            int excludedGroup = (Integer) readInheritedField(rule, excludedGroupField);
            String excludedChildField = DialogueText.EXCLUDED_CHILD_FIELD.value();
            int excludedChild = (Integer) readInheritedField(rule, excludedChildField);
            Widget excludedWidget = client.getWidget(excludedGroup, excludedChild);
            if (excludedStrings != null && excludedWidget != null && excludedWidget.getChildren() != null) {
               boolean excluded = false;
               for (Widget child : excludedWidget.getChildren()) {
                  if (child != null) {
                     for (Object excludedValue : excludedStrings) {
                        String text = normalizeDialogueText(child.getText());
                        // Every exclusion is evaluated, even after a previous exclusion matched.
                        excluded |= text.contains((String) excludedValue);
                     }
                  }
               }
               if (excluded) {
                  continue;
               }
            }

            String choiceIdField = DialogueText.CHOICE_ID_FIELD.value();
            int choiceId = (Integer) readInheritedField(rule, choiceIdField);
            String choiceField = DialogueText.CHOICE_TEXT_FIELD.value();
            String choice = (String) readInheritedField(rule, choiceField);
            String patternField = DialogueText.CHOICE_PATTERN_FIELD.value();
            Pattern pattern = (Pattern) readInheritedField(rule, patternField);
            String varbitField = DialogueText.VARBIT_FIELD.value();
            int varbit = (Integer) readInheritedField(rule, varbitField);
            if (choiceId < 0 && varbit >= 0) {
               String answersField = DialogueText.VARBIT_ANSWERS_FIELD.value();
               Map<?, ?> answers = (Map<?, ?>) readInheritedField(rule, answersField);
               choice = answers == null ? null : (String) answers.get(client.getVarbitValue(varbit));
            }

            if (options == null) {
               continue;
            }
            for (int index = 1; index < options.length; index++) {
               Widget option = options[index];
               if (option == null || option.isHidden() || (choiceId >= 0 && choiceId != index)) {
                  continue;
               }
               String text = normalizeDialogueText(option.getText());
               if ((choice != null && choice.equals(text))
                  || (pattern != null && pattern.matcher(text).find())
                  || (choiceId >= 0 && choice == null && pattern == null)) {
                  matchingOptions.add(option);
               }
            }
         }

         if (matchingOptions.size() == 1) {
            return matchingOptions.iterator().next();
         }
         throw new IllegalStateException(DialogueText.AMBIGUOUS_ANSWER.value());
      } catch (Exception failure) {
         String message = DialogueText.RULE_READ_FAILURE.value();
         throw new IllegalStateException(message, failure);
      }
   }

   static Object readInheritedField(Object owner, String fieldName) throws ReflectiveOperationException {
      if (owner == null) {
         throw new NoSuchFieldException(fieldName);
      }

      for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
         try {
            Field field = type.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(owner);
         } catch (NoSuchFieldException missingField) {
         }
      }

      throw new NoSuchFieldException(fieldName);
   }

   private static String normalizeDialogueText(String text) {
      if (text == null) {
         return DialogueText.NULL_DIALOGUE_TEXT.value();
      }
      String withoutTags = Text.removeTags(text);
      String prefixPattern = DialogueText.NUMBER_PREFIX_PATTERN.value();
      String replacement = DialogueText.NUMBER_PREFIX_REPLACEMENT.value();
      return withoutTags.replaceFirst(prefixPattern, replacement).trim();
   }

   private static Widget getOptionsWidget(Client client) {
      Widget optionsWidget = client.getWidget(219, 1);
      return optionsWidget != null && !optionsWidget.isHidden() ? optionsWidget : null;
   }

   private static Widget getContinueWidget(Client client) {
      for (int[] location : CONTINUE_WIDGETS) {
         Widget continueWidget = client.getWidget(location[0], location[1]);
         if (continueWidget != null
            && !continueWidget.isHidden()
            && normalizeDialogueText(continueWidget.getText()).equalsIgnoreCase("Click here to continue")) {
            return continueWidget;
         }
      }

      return null;
   }

   /** Public decoder entry point. */
   public static String pjfiexiauo(byte[] descriptor, int seed) {
      return decodeLegacyOuter(descriptor, seed);
   }

   /** The descriptor contains a big-endian character count followed by a table offset. */
   private static String decodeLegacyOuter(byte[] descriptor, int seed) {
      byte[] seedDigits = Integer.toString(seed).getBytes();
      return decodeLegacySlice(descriptor, seedDigits);
   }

   private static String decodeLegacySlice(byte[] descriptor, byte[] seedDigits) {
      int length = readDescriptorInt(descriptor, 0);
      int offset = readDescriptorInt(descriptor, 4);
      byte[] encoded = legacyStringTable.substring(offset, offset + length).getBytes(StandardCharsets.UTF_16BE);
      for (int index = 0; index < encoded.length; index++) {
         encoded[index] ^= seedDigits[index % seedDigits.length];
      }
      return new String(encoded, StandardCharsets.UTF_16BE);
   }

   private static int readDescriptorInt(byte[] descriptor, int offset) {
      return (descriptor[offset] & 255) << 24 | (descriptor[offset + 1] & 255) << 16
         | (descriptor[offset + 2] & 255) << 8 | descriptor[offset + 3] & 255;
   }

   /** Rebuild the exact original table from readable literals, including its public slice offsets. */
   private static String buildLegacyStringTable() {
      StringBuilder table = new StringBuilder();
      for (DialogueText entry : DialogueText.values()) {
         byte[] encoded = encodeLegacyInner(entry.text, entry.innerSeed).getBytes(StandardCharsets.UTF_16BE);
         byte[] asciiDigits = Integer.toString(entry.outerSeed).getBytes(StandardCharsets.US_ASCII);
         for (int index = 0; index < encoded.length; index++) {
            encoded[index] ^= asciiDigits[index % asciiDigits.length];
         }
         table.append(new String(encoded, StandardCharsets.UTF_16BE));
      }
      return table.toString();
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
   private enum DialogueText {
      UNSUPPORTED_RULE("This quest uses an unsupported dialogue rule. Select the answer manually, then resume.", 650328116, 819959976, 0),
      EXPECTED_PREVIOUS_LINE_FIELD("expectedPreviousLine", 2037013802, -1159052013, 232),
      SUPPORTED_RULE_CLASS("DialogChoiceStep", 1017201139, -1229722043, 288),
      RULE_READ_FAILURE("Could not read Quest Helper's dialogue rules. Update Quest Helper or answer manually.", 1588004499, -6263487, 332),
      EXCLUDED_STRINGS_FIELD("excludedStrings", 1320475059, 83234694, 560),
      EXCLUDED_GROUP_FIELD("excludedGroupId", 1320475059, -1912456727, 600),
      EXCLUDED_CHILD_FIELD("excludedChildId", 1320475059, 137290332, 640),
      AMBIGUOUS_ANSWER("Quest Helper does not identify one dialogue answer. Select the answer manually, then press Walk to quest step to resume.", 1982956651, 1972740318, 680),
      VARBIT_ANSWERS_FIELD("varbitValueToAnswer", 827023288, -343280021, 1000),
      CHOICE_ID_FIELD("choiceById", 1573169726, -1841648441, 1052),
      CHOICE_TEXT_FIELD("choice", 1573169726, 474306858, 1080),
      CHOICE_PATTERN_FIELD("pattern", 1573169726, -430898275, 1096),
      VARBIT_FIELD("varbitId", 1573169726, 1618856960, 1116),
      STEP_CHOICES_FIELD("choices", 1622845803, 661696772, 1140),
      CONTAINER_CHOICES_FIELD("choices", 1622845803, -1566868625, 1160),
      LAST_DIALOG_FIELD("lastDialogSeen", 1622845803, 749151698, 1180),
      NULL_DIALOGUE_TEXT("", 1328438688, -107064262, 1220),
      NUMBER_PREFIX_PATTERN("^(\\[\\d+\\]\\s*)+", 1661572461, 2088083025, 1220),
      NUMBER_PREFIX_REPLACEMENT("", 1661572461, -230047508, 1260);

      private final String text;
      private final int outerSeed;
      private final int innerSeed;
      private final int offset;

      DialogueText(String text, int outerSeed, int innerSeed, int offset) {
         this.text = text;
         this.outerSeed = outerSeed;
         this.innerSeed = innerSeed;
         this.offset = offset;
      }

      private String value() {
         byte[] seedDigits = Integer.toString(outerSeed).getBytes();
         byte[] asciiDigits = Integer.toString(outerSeed).getBytes(StandardCharsets.US_ASCII);
         if (Arrays.equals(seedDigits, asciiDigits)) {
            return text.intern();
         }
         return decodeLegacyInner(decodeLegacySlice(descriptor(), seedDigits), innerSeed);
      }

      private byte[] descriptor() {
         int length = ((text.length() * 2 + 2) / 3) * 4;
         return new byte[]{
            (byte) (length >>> 24), (byte) (length >>> 16), (byte) (length >>> 8), (byte) length,
            (byte) (offset >>> 24), (byte) (offset >>> 16), (byte) (offset >>> 8), (byte) offset
         };
      }
   }
}
