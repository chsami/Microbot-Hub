package net.runelite.client.plugins.microbot.droefficientwalker.engine;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.util.Text;

final class QuestHelperBridge {

   // Exact legacy outer key; the default-charset decoder remains available below.
   private static byte[] legacyXorKey = Base64.getDecoder().decode(
      "QxUSBXJKXU1WCkVJYE9LVVddZkIBSDMzBUsDNnleenw/dnBdQipPcTcSLGcaNFI1CDQ6cBk/N20NUnYGUnw8LnFJQ3APHicoUhUhQXggRWVaQDcxPA=="
   );

   static QuestHelperBridge.QuestTarget resolveQuestTarget(PluginManager pluginManager, Client client) {
      Object step = getActiveStep(getSelectedQuest(pluginManager));
      WorldPoint destination = getStepWorldPoint(step);
      boolean hasDefinedDestination = destination != null;
      WorldView worldView = client.getTopLevelWorldView();
      if (worldView != null && client.getLocalPlayer() != null && client.getLocalPlayer().getWorldView() == worldView) {
         if (!hasDefinedDestination) {
            destination = client.getLocalPlayer().getWorldLocation();
         }

         if (hasDefinedDestination && worldView.isInstance()) {
            Collection<WorldPoint> localInstances = WorldPoint.toLocalInstance(worldView, destination);
            if (localInstances.size() != 1) {
               throw new IllegalStateException(
                  "Quest destination is outside this instance or ambiguous. Leave the instance and retry."
               );
            }

            destination = localInstances.iterator().next();
         }

         Class<?> npcStepClass = null;
         Class<?> objectStepClass = null;
         Class<?> stepClass = step.getClass();

         while (stepClass != null) {
            if (!stepClass.getName().equals("com.questhelper.steps.NpcStep")
               && !stepClass.getName()
                  .equals(
                     "net.runelite.client.plugins.microbot.questhelper.steps.NpcStep"
                  )) {
               if (!stepClass.getName().equals("com.questhelper.steps.ObjectStep")
                  && !stepClass.getName()
                     .equals(
                        "net.runelite.client.plugins.microbot.questhelper.steps.ObjectStep"
                     )) {
                  stepClass = stepClass.getSuperclass();
                  continue;
               }

               objectStepClass = stepClass;
               break;
            }

            npcStepClass = stepClass;
            break;
         }

         WorldPoint nearestNpcLocation = null;
         NPC nearestNpc = null;
         if (npcStepClass != null) {
            try {
               Field npcsField = npcStepClass.getDeclaredField("npcs");
               npcsField.setAccessible(true);
               Method passesFocusChecks = npcStepClass.getDeclaredMethod("passesFocusChecks", NPC.class);
               passesFocusChecks.setAccessible(true);

               for (Object candidateNpc : (Collection<?>)npcsField.get(step)) {
                  NPC npc = (NPC)candidateNpc;
                  if (npc.getId() >= 0 && npc.getWorldView() == worldView && Boolean.TRUE.equals(passesFocusChecks.invoke(step, npc))) {
                     WorldPoint npcLocation = npc.getWorldLocation();
                     if (npcLocation != null && (nearestNpcLocation == null || npcLocation.distanceTo(destination) < nearestNpcLocation.distanceTo(destination))) {
                        nearestNpcLocation = npcLocation;
                        nearestNpc = npc;
                     }
                  }
               }
            } catch (ReflectiveOperationException | RuntimeException failure) {
               throw new IllegalStateException(
                  "Could not read Quest Helper's NPC. Update Quest Helper, then retry.",
                  failure
               );
            }
         }

         TileObject nearestObject = null;
         int objectSize = 1;
         if (objectStepClass != null) {
            try {
               Field objectIdField = objectStepClass.getDeclaredField("objectID");
               objectIdField.setAccessible(true);
               ObjectComposition objectDefinition = client.getObjectDefinition((Integer)objectIdField.get(step));
               if (objectDefinition != null) {
                  objectSize = Math.max(1, Math.max(objectDefinition.getSizeX(), objectDefinition.getSizeY()));
               }

               Field objectsField = objectStepClass.getDeclaredField("objects");
               objectsField.setAccessible(true);

               for (Object candidateObject : (Collection<?>)objectsField.get(step)) {
                  TileObject object = (TileObject)candidateObject;
                  if (object.getWorldView() == worldView
                     && object.getWorldLocation() != null
                     && (nearestObject == null || object.getWorldLocation().distanceTo(destination) < nearestObject.getWorldLocation().distanceTo(destination))) {
                     nearestObject = object;
                  }
               }
            } catch (ReflectiveOperationException | RuntimeException failure) {
               throw new IllegalStateException(
                  "Could not read Quest Helper's object. Update Quest Helper, then retry.",
                  failure
               );
            }
         }

         if (!hasDefinedDestination) {
            destination = nearestObject == null ? nearestNpcLocation : nearestObject.getWorldLocation();
            if (destination == null) {
               throw new IllegalStateException(
                  "The current quest step has no walking destination or visible target. Follow Quest Helper manually, then resume."
               );
            }
         }

         return new QuestHelperBridge.QuestTarget(step, destination, npcStepClass != null, objectStepClass != null, nearestNpcLocation, nearestNpc, nearestObject, objectSize);
      } else {
         throw new IllegalStateException(
            "Return to the main game world, then retry the quest walk."
         );
      }
   }

   static Object getSelectedQuest(PluginManager pluginManager) {
      List<Plugin> matchingPlugins = pluginManager.getPlugins().stream().filter(plugin ->
         plugin.getClass().getName().equals(resolvePluginClassName(
            "com.questhelper.QuestHelperPlugin",
            "i9snbUMQbT9lbnQGUhp4Im8GUEY0LAJ1NRkwMkhYSHkMNUgDdE56AgZDHGcpTmNGOioJMCF7AS84BkdTYhMPM0BIcRU8Xx9+ZEAUMElJdTZpEwZeDhomDT01fAZ4JzsVeTZ8TWQAbzRxcnA0BQZ+cgcQbhpPc0c9bxEZLUkxJEVSQgUFBUMHYEFPDUpeVWosMDFJQB8seS9DXy95GhkmeXkGFjxQP3FjAWdwCCNXQCxuNA==", 615213083, -1226703288))
         || plugin.getClass().getName().equals(resolvePluginClassName(
            "net.runelite.client.plugins.microbot.questhelper.QuestHelperPlugin",
            "jN0lHEI7bBpiTHI4VjN/AW9YVwQ2NgNqNCI3Vk5GTCkLKkhRc3N4cwdFHTguQ2VnPk4OcCFoBmk6AkZ+YwgIXEZKdXk7Wh9rYxYWGkh1dDxuXgBrChQhEj0eeyt6UDoYeDJ7MWJYayx2c3A2Al18fQY4bzdIfkEEayMeOUlqI3dQAwRTBAkARkcbCTNZZGoMN1ZLQR4ieBlEESlEHjAhf3k8EUNSHnB0AF13ESVIRBJpN25cdDhXLXsXZlZSEDY1BUExCztWSAxNLQ8bQSB2U3gEAVsYJiJjY0M/MgoRKDIDBjocQARmFQQ1QAR0DD9nFn5mFxZHTlFxJWJNBmELICUJNCd+JHosPBR9YHcvZAZqEnJ2eU8HYHxEACdqBUQgRwxqAhpUQHcmTFBkAiwBQQxZQUQIZl1rYy0yBUtFGDd9PEh6L1cfICVxcDoUBFI0dlUFbHtjI1xFEG0zZ3FxSldB", 177604184, 856306723))
      ).collect(Collectors.toList());
      if (matchingPlugins.isEmpty()) {
         throw new IllegalStateException(
            "Install Quest Helper, enable it, and select a quest."
         );
      }

      List<Plugin> activePlugins = matchingPlugins.stream().filter(pluginManager::isActive).collect(Collectors.toList());
      if (activePlugins.isEmpty()) {
         throw new IllegalStateException(
            "Enable Quest Helper and select a quest, then retry."
         );
      }

      if (activePlugins.size() != 1) {
         throw new IllegalStateException(
            "Enable only one Quest Helper instance, then retry."
         );
      }

      try {
         Object selectedQuest = invokeNoArgMethod(activePlugins.get(0), "getSelectedQuest");
         if (selectedQuest == null) {
            throw new IllegalStateException(
               "Select a quest in Quest Helper, then retry."
            );
         } else {
            return selectedQuest;
         }
      } catch (ReflectiveOperationException failure) {
         throw new IllegalStateException(
            "Could not read Quest Helper. Update it, then retry.",
            failure
         );
      }
   }

   static Object getActiveStep(Object quest) {
      try {
         Object currentStep = invokeNoArgMethod(quest, "getCurrentStep");
         return currentStep == null ? null : invokeNoArgMethod(currentStep, "getActiveStep");
      } catch (ReflectiveOperationException failure) {
         throw new IllegalStateException(
            "Could not read Quest Helper's current step. Update it, then retry.",
            failure
         );
      }
   }

   static int resolveInteractionActionIndex(Object step, String[] actions) {
      String instructionText;
      try {
         Object textLines = invokeNoArgMethod(step, "getText");
         if (!(textLines instanceof List) || ((List<?>)textLines).isEmpty() || !(((List<?>)textLines).get(0) instanceof String)) {
            throw new IllegalStateException(
               "Quest Helper has no interaction instruction. Interact manually."
            );
         }

         instructionText = Text.removeTags((String)((List<?>)textLines).get(0))
            .toLowerCase(Locale.ROOT)
            .replace('-', ' ')
            .replaceAll("\\s+", " ")
            .trim();
      } catch (ReflectiveOperationException failure) {
         throw new IllegalStateException(
            "Could not read the interaction instruction. Interact manually using Quest Helper.",
            failure
         );
      }

      if (instructionText.isEmpty()) {
         throw new IllegalStateException(
            "Quest Helper has no interaction instruction. Interact manually."
         );
      }

      if (instructionText.matches("(?s).*\\buse\\s+.+\\s+(on|with)\\s+.+")) {
         throw new IllegalStateException(
            "This step requires using an item on the quest target. Follow Quest Helper manually, then press Walk to quest step."
         );
      }

      int firstActionIndex = -1;
      int matchingActionIndex = -1;
      int longestMatchLength = 0;
      boolean ambiguousMatch = false;
      if (actions == null) {
         return -1;
      }

      for (int actionIndex = 0; actionIndex < Math.min(5, actions.length); actionIndex++) {
         if (actions[actionIndex] != null && !actions[actionIndex].trim().isEmpty() && !"Examine".equalsIgnoreCase(actions[actionIndex].trim())) {
            if (firstActionIndex < 0) {
               firstActionIndex = actionIndex;
            }

            String normalizedAction = actions[actionIndex]
               .toLowerCase(Locale.ROOT)
               .replace('-', ' ')
               .replaceAll("\\s+", " ")
               .trim();
            String quotedAction = Pattern.quote(normalizedAction);
            if (instructionText.matches("(?s).*\\b" + quotedAction + "\\b.*")) {
               if (normalizedAction.length() > longestMatchLength) {
                  matchingActionIndex = actionIndex;
                  longestMatchLength = normalizedAction.length();
                  ambiguousMatch = false;
               } else if (normalizedAction.length() == longestMatchLength) {
                  ambiguousMatch = true;
               }
            }
         }
      }

      if (ambiguousMatch) {
         throw new IllegalStateException(
            "More than one interaction action matches this step. Interact manually using Quest Helper."
         );
      } else {
         return matchingActionIndex < 0 ? firstActionIndex : matchingActionIndex;
      }
   }

   static WorldPoint getStepWorldPoint(Object step) {
      try {
         if (step != null) {
            Object definedPoint = invokeNoArgMethod(step, "getDefinedPoint");
            Object worldPoint = definedPoint == null ? null : invokeNoArgMethod(definedPoint, "getWorldPoint");
            if (worldPoint instanceof WorldPoint) {
               return (WorldPoint)worldPoint;
            }
         }
      } catch (NoSuchMethodException missingMethod) {
      } catch (ReflectiveOperationException | SecurityException failure) {
         throw new IllegalStateException(
            "Could not read Quest Helper. Update Quest Helper and restart the quest, then retry.",
            failure
         );
      }

      return null;
   }

   static Object invokeNoArgMethod(Object target, String methodName) throws ReflectiveOperationException {
      return target.getClass().getMethod(methodName).invoke(target);
   }

   /** Use readable names when decimal seed bytes match the legacy ASCII encoding. */
   private static String resolvePluginClassName(String literal, String encodedBytes, int outerSeed, int textSeed) {
      String decimalSeed = Integer.toString(outerSeed);
      if (Arrays.equals(decimalSeed.getBytes(), decimalSeed.getBytes(StandardCharsets.US_ASCII))) {
         return literal;
      }
      // Preserve the original fallback and its exceptions for unusual JVM default charsets.
      return decodeLegacyInner(decodeLegacyOuter(Base64.getDecoder().decode(encodedBytes), outerSeed), textSeed);
   }

   /** Historical in-place XOR layer; default charset and the two successive writes are deliberate. */
   public static String decodeLegacyOuter(byte[] payload, int seed) {
      byte[] numericKey = Integer.toString(seed).getBytes();
      for (int index = 0; index < payload.length; index++) {
         payload[index] = (byte) (payload[index] ^ numericKey[index % numericKey.length]);
         payload[index] = (byte) (payload[index] ^ legacyXorKey[index % legacyXorKey.length]);
      }
      return new String(payload, StandardCharsets.UTF_16);
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

   static final class QuestTarget {
      final Object questStep;
      final WorldPoint destination;
      final boolean npcStep;
      final WorldPoint nearestNpcLocation;
      final NPC npc;
      final boolean objectStep;
      final TileObject object;
      final WorldPoint interactionLocation;
      final int objectSize;

      QuestTarget(Object questStep, WorldPoint destination, boolean npcStep, boolean objectStep,
                  WorldPoint nearestNpcLocation, NPC npc, TileObject object, int objectSize) {
         this.questStep = questStep;
         this.destination = destination;
         this.npcStep = npcStep;
         this.nearestNpcLocation = nearestNpcLocation;
         this.npc = npc;
         this.objectStep = objectStep;
         this.object = object;
         this.objectSize = objectSize;
         this.interactionLocation = object == null ? nearestNpcLocation : object.getWorldLocation();
      }
   }
}
