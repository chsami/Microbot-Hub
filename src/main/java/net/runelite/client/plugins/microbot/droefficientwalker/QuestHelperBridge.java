package net.runelite.client.plugins.microbot.droefficientwalker;

import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.util.Text;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Quest Helper bridge.
 *
 * <p>The shipped class used reflection so Efficient Walker could interoperate with either the
 * upstream Quest Helper plugin or Dro's repackaged Quest Helper without linking either one at
 * compile time.</p>
 */
final class QuestHelperBridge
{
    private static final String QUEST_HELPER = "com.questhelper.QuestHelperPlugin";
    private static final String MICROBOT_QUEST_HELPER =
        "net.runelite.client.plugins.microbot.questhelper.QuestHelperPlugin";
    private static final String NPC_STEP = "com.questhelper.steps.NpcStep";
    private static final String MICROBOT_NPC_STEP =
        "net.runelite.client.plugins.microbot.questhelper.steps.NpcStep";
    private static final String OBJECT_STEP = "com.questhelper.steps.ObjectStep";
    private static final String MICROBOT_OBJECT_STEP =
        "net.runelite.client.plugins.microbot.questhelper.steps.ObjectStep";

    private QuestHelperBridge()
    {
    }

    static QuestTarget resolveQuestTarget(PluginManager pluginManager, Client client)
    {
        Object quest = getSelectedQuest(pluginManager);
        Object step = getActiveStep(quest);

        WorldPoint destination = getStepWorldPoint(step);
        boolean hadDefinedDestination = destination != null;

        WorldView worldView = client.getTopLevelWorldView();
        Player player = client.getLocalPlayer();
        if (worldView == null || player == null || player.getWorldView() != worldView)
        {
            throw new IllegalStateException("Return to the main game world, then retry the quest walk.");
        }

        if (!hadDefinedDestination)
        {
            destination = player.getWorldLocation();
        }

        if (hadDefinedDestination && worldView.isInstance())
        {
            Collection<WorldPoint> localPoints = WorldPoint.toLocalInstance(worldView, destination);
            if (localPoints.size() != 1)
            {
                throw new IllegalStateException(
                    "Quest destination is outside this instance or ambiguous. Leave the instance and retry.");
            }
            destination = localPoints.iterator().next();
        }

        Class<?> npcStepClass = null;
        Class<?> objectStepClass = null;
        for (Class<?> type = step.getClass(); type != null; type = type.getSuperclass())
        {
            String name = type.getName();
            if (NPC_STEP.equals(name) || MICROBOT_NPC_STEP.equals(name))
            {
                npcStepClass = type;
                break;
            }
            if (OBJECT_STEP.equals(name) || MICROBOT_OBJECT_STEP.equals(name))
            {
                objectStepClass = type;
                break;
            }
        }

        WorldPoint nearestNpcPoint = null;
        NPC nearestNpc = null;
        if (npcStepClass != null)
        {
            try
            {
                Field npcs = npcStepClass.getDeclaredField("npcs");
                npcs.setAccessible(true);
                Method passesFocusChecks = npcStepClass.getDeclaredMethod("passesFocusChecks", NPC.class);
                passesFocusChecks.setAccessible(true);

                @SuppressWarnings("unchecked")
                Collection<Object> highlighted = (Collection<Object>) npcs.get(step);
                List<Object> candidates = new java.util.ArrayList<>(highlighted);
                if (candidates.isEmpty())
                {
                    Field npcId = npcStepClass.getDeclaredField("npcID");
                    npcId.setAccessible(true);
                    NPCComposition expected = client.getNpcDefinition(npcId.getInt(step));
                    for (NPC npc : worldView.npcs())
                    {
                        if (matchesNpcFamily(npc, expected)) candidates.add(npc);
                    }
                }
                for (Object candidate : candidates)
                {
                    NPC npc = (NPC) candidate;
                    if (npc.getId() < 0 || npc.getWorldView() != worldView)
                    {
                        continue;
                    }
                    if (!Boolean.TRUE.equals(passesFocusChecks.invoke(step, npc)))
                    {
                        continue;
                    }

                    WorldPoint point = npc.getWorldLocation();
                    if (point != null && (nearestNpcPoint == null
                        || point.distanceTo(destination) < nearestNpcPoint.distanceTo(destination)))
                    {
                        nearestNpcPoint = point;
                        nearestNpc = npc;
                    }
                }
            }
            catch (ReflectiveOperationException | RuntimeException ex)
            {
                throw new IllegalStateException(
                    "Could not read Quest Helper's NPC. Update Quest Helper, then retry.", ex);
            }
        }

        TileObject nearestObject = null;
        int objectSize = 1;
        if (objectStepClass != null)
        {
            try
            {
                Field objectId = objectStepClass.getDeclaredField("objectID");
                objectId.setAccessible(true);
                ObjectComposition definition = client.getObjectDefinition(((Integer) objectId.get(step)).intValue());
                if (definition != null)
                {
                    objectSize = Math.max(1, Math.max(definition.getSizeX(), definition.getSizeY()));
                }

                Field objects = objectStepClass.getDeclaredField("objects");
                objects.setAccessible(true);
                @SuppressWarnings("unchecked")
                Collection<Object> candidates = (Collection<Object>) objects.get(step);
                for (Object candidate : candidates)
                {
                    TileObject object = (TileObject) candidate;
                    if (object.getWorldView() != worldView || object.getWorldLocation() == null)
                    {
                        continue;
                    }
                    if (nearestObject == null
                        || object.getWorldLocation().distanceTo(destination)
                            < nearestObject.getWorldLocation().distanceTo(destination))
                    {
                        nearestObject = object;
                    }
                }
            }
            catch (ReflectiveOperationException | RuntimeException ex)
            {
                throw new IllegalStateException(
                    "Could not read Quest Helper's object. Update Quest Helper, then retry.", ex);
            }
        }

        if (!hadDefinedDestination)
        {
            destination = nearestObject != null ? nearestObject.getWorldLocation() : nearestNpcPoint;
            if (destination == null)
            {
                throw new IllegalStateException(
                    "The current quest step has no walking destination or visible target. Follow Quest Helper manually, then resume.");
            }
        }

        return new QuestTarget(
            step,
            destination,
            npcStepClass != null,
            objectStepClass != null,
            nearestNpcPoint,
            nearestNpc,
            nearestObject,
            objectSize);
    }

    // Quest Helper's highlighted list can be empty while a transformed NPC is visible.
    static boolean matchesNpcFamily(NPC npc, NPCComposition expected)
    {
        if (npc == null || npc.isDead() || expected == null || expected.getName() == null
            || !expected.getName().equals(npc.getName()) || npc.getComposition() == null) return false;
        int[] variants = npc.getComposition().getConfigs();
        return variants != null && java.util.Arrays.stream(variants).anyMatch(id -> id == expected.getId());
    }

    static Object getSelectedQuest(PluginManager pluginManager)
    {
        List<Plugin> questHelpers = pluginManager.getPlugins().stream()
            .filter(QuestHelperBridge::isQuestHelperPlugin)
            .collect(Collectors.toList());
        if (questHelpers.isEmpty())
        {
            throw new IllegalStateException("Install Quest Helper, enable it, and select a quest.");
        }

        List<Plugin> enabled = questHelpers.stream()
            .filter(pluginManager::isActive)
            .collect(Collectors.toList());
        if (enabled.isEmpty())
        {
            throw new IllegalStateException("Enable Quest Helper and select a quest, then retry.");
        }
        if (enabled.size() != 1)
        {
            throw new IllegalStateException("Enable only one Quest Helper instance, then retry.");
        }

        try
        {
            Object selectedQuest = invokeNoArgMethod(enabled.get(0), "getSelectedQuest");
            if (selectedQuest == null)
            {
                throw new IllegalStateException("Select a quest in Quest Helper, then retry.");
            }
            return selectedQuest;
        }
        catch (ReflectiveOperationException ex)
        {
            throw new IllegalStateException("Could not read Quest Helper. Update it, then retry.", ex);
        }
    }

    static Object getActiveStep(Object selectedQuest)
    {
        try
        {
            Object currentStep = invokeNoArgMethod(selectedQuest, "getCurrentStep");
            return currentStep == null ? null : invokeNoArgMethod(currentStep, "getActiveStep");
        }
        catch (ReflectiveOperationException ex)
        {
            throw new IllegalStateException(
                "Could not read Quest Helper's current step. Update it, then retry.", ex);
        }
    }

    static int resolveInteractionActionIndex(Object step, String[] actions)
    {
        final String instruction;
        try
        {
            Object text = invokeNoArgMethod(step, "getText");
            if (!(text instanceof List)
                || ((List<?>) text).isEmpty()
                || !(((List<?>) text).get(0) instanceof String))
            {
                throw new IllegalStateException("Quest Helper has no interaction instruction. Interact manually.");
            }

            instruction = Text.removeTags((String) ((List<?>) text).get(0))
                .toLowerCase(Locale.ROOT)
                .replace('-', ' ')
                .replaceAll("\\s+", " ")
                .trim();
        }
        catch (ReflectiveOperationException ex)
        {
            throw new IllegalStateException(
                "Could not read the interaction instruction. Interact manually using Quest Helper.", ex);
        }

        if (instruction.isEmpty())
        {
            throw new IllegalStateException("Quest Helper has no interaction instruction. Interact manually.");
        }
        if (instruction.matches("(?s).*\\buse\\s+.+\\s+(on|with)\\s+.+"))
        {
            throw new IllegalStateException(
                "This step requires using an item on the quest target. Follow Quest Helper manually, then press Walk to quest step.");
        }

        int firstAction = -1;
        int bestAction = -1;
        int bestLength = 0;
        boolean ambiguous = false;
        if (actions == null)
        {
            return -1;
        }

        for (int index = 0; index < Math.min(5, actions.length); index++)
        {
            String action = actions[index];
            if (action == null || action.trim().isEmpty() || "Examine".equalsIgnoreCase(action.trim()))
            {
                continue;
            }
            if (firstAction < 0)
            {
                firstAction = index;
            }

            String normalized = action.toLowerCase(Locale.ROOT)
                .replace('-', ' ')
                .replaceAll("\\s+", " ")
                .trim();
            if (!instruction.matches("(?s).*\\b" + Pattern.quote(normalized) + "\\b.*"))
            {
                continue;
            }

            if (normalized.length() > bestLength)
            {
                bestAction = index;
                bestLength = normalized.length();
                ambiguous = false;
            }
            else if (normalized.length() == bestLength)
            {
                ambiguous = true;
            }
        }

        if (ambiguous)
        {
            throw new IllegalStateException(
                "More than one interaction action matches this step. Interact manually using Quest Helper.");
        }
        return bestAction < 0 ? firstAction : bestAction;
    }

    static WorldPoint getStepWorldPoint(Object step)
    {
        if (step == null)
        {
            return null;
        }

        try
        {
            Object definedPoint = invokeNoArgMethod(step, "getDefinedPoint");
            Object worldPoint = definedPoint == null ? null : invokeNoArgMethod(definedPoint, "getWorldPoint");
            return worldPoint instanceof WorldPoint ? (WorldPoint) worldPoint : null;
        }
        catch (NoSuchMethodException ex)
        {
            return null;
        }
        catch (ReflectiveOperationException | SecurityException ex)
        {
            throw new IllegalStateException(
                "Could not read Quest Helper. Update Quest Helper and restart the quest, then retry.", ex);
        }
    }

    static Object invokeNoArgMethod(Object target, String method) throws ReflectiveOperationException
    {
        return target.getClass().getMethod(method).invoke(target);
    }

    private static boolean isQuestHelperPlugin(Plugin plugin)
    {
        String name = plugin.getClass().getName();
        return QUEST_HELPER.equals(name) || MICROBOT_QUEST_HELPER.equals(name);
    }

    static final class QuestTarget
    {
        final Object questStep;
        final WorldPoint destination;
        final boolean npcStep;
        final WorldPoint nearestNpcLocation;
        final NPC npc;
        final boolean objectStep;
        final TileObject object;
        final WorldPoint interactionLocation;
        final int objectSize;

        QuestTarget(Object step, WorldPoint destination, boolean npcStep, boolean objectStep,
            WorldPoint nearestNpcPoint, NPC nearestNpc, TileObject nearestObject, int objectSize)
        {
            this.questStep = step;
            this.destination = destination;
            this.npcStep = npcStep;
            this.nearestNpcLocation = nearestNpcPoint;
            this.npc = nearestNpc;
            this.objectStep = objectStep;
            this.object = nearestObject;
            this.objectSize = objectSize;
            this.interactionLocation = nearestObject == null ? nearestNpcPoint : nearestObject.getWorldLocation();
        }
    }
}
