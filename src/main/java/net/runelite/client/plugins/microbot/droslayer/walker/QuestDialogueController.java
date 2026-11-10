package net.runelite.client.plugins.microbot.droslayer.walker;

import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.util.Text;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/** Quest Helper dialogue bridge. */
final class QuestDialogueController
{
    private static final int[][] CONTINUE_WIDGETS = {
        {231, 5}, {217, 5}, {193, 0}, {193, 3}, {11, 4}, {229, 0}, {229, 2}, {229, 4}
    };

    private final Object selectedQuest;
    private int lastDialogueTick;
    private int lastActionTick;
    private String lastActedSignature;
    private String pendingSignature;
    private int nextActionTick;
    private int pausedTick;

    QuestDialogueController(Object quest, int tick)
    {
        this.pausedTick = -1;
        this.selectedQuest = quest;
        this.lastDialogueTick = tick;
    }

    static boolean isDialogueOpen(Client client)
    {
        return getOptionsWidget(client) != null || getContinueWidget(client) != null;
    }

    void onWidgetGroupLoaded(int groupId)
    {
        if (groupId == 219 || groupId == 231 || groupId == 217 || groupId == 193
            || groupId == 11 || groupId == 229)
        {
            this.lastActedSignature = null;
            this.pendingSignature = null;
        }
    }

    void markPaused(int tick)
    {
        if (this.pausedTick < 0)
        {
            this.pausedTick = tick;
        }
    }

    boolean tickDialogue(Client client, PluginManager pluginManager)
    {
        if (this.pausedTick >= 0)
        {
            int delta = client.getTickCount() - this.pausedTick;
            this.lastDialogueTick += delta;
            this.lastActionTick += delta;
            this.nextActionTick += delta;
            this.pausedTick = -1;
        }

        if (QuestHelperBridge.getSelectedQuest(pluginManager) != this.selectedQuest)
        {
            throw new IllegalStateException(
                "Selected quest changed. Press Walk to quest step to resume.");
        }

        Widget dialogue = getOptionsWidget(client);
        Widget continueWidget = getContinueWidget(client);
        int tick = client.getTickCount();

        if (dialogue == null && continueWidget == null)
        {
            this.pendingSignature = null;
            return tick - this.lastDialogueTick < 10;
        }

        this.lastDialogueTick = tick;
        StringBuilder signature = new StringBuilder();
        int groupId = dialogue != null ? 219 : continueWidget.getId() >>> 16;
        for (int childId = 0; childId < 32; childId++)
        {
            Widget widget = client.getWidget(groupId, childId);
            if (widget != null && !widget.isHidden())
            {
                signature.append(childId).append(':').append(normalizeDialogueText(widget.getText())).append('|');
            }
        }

        if (dialogue != null && dialogue.getChildren() != null)
        {
            for (Widget child : dialogue.getChildren())
            {
                if (child != null)
                {
                    signature.append(normalizeDialogueText(child.getText())).append('|');
                }
            }
        }

        String state = groupId + ":" + signature;
        if (state.equals(this.lastActedSignature))
        {
            if (tick - this.lastActionTick >= 8)
            {
                throw new IllegalStateException(
                    "Dialogue did not advance. Continue it manually, then press Walk to quest step to resume.");
            }
            return true;
        }

        if (!state.equals(this.pendingSignature))
        {
            this.pendingSignature = state;
            this.nextActionTick = tick + ThreadLocalRandom.current().nextInt(1, 4);
        }

        if (tick < this.nextActionTick)
        {
            return true;
        }

        if (dialogue != null)
        {
            Widget option = resolveDialogueOption(client, QuestHelperBridge.getActiveStep(this.selectedQuest), dialogue);
            if (!Rs2Dialogue.keyPressForDialogueOption(option.getText(), true))
            {
                throw new IllegalStateException(
                    "Cannot read the dialogue option's key. Select the highlighted option manually, then resume.");
            }
        }
        else
        {
            Rs2Keyboard.keyPress(32);
        }

        this.lastActedSignature = state;
        this.lastActionTick = tick;
        return true;
    }

    @SuppressWarnings("unchecked")
    static Widget resolveDialogueOption(Client client, Object step, Widget parent)
    {
        try
        {
            Widget[] children = parent.getChildren();
            Object choicesObject = readInheritedField(step, "choices");
            List<?> choices = (List<?>) readInheritedField(choicesObject, "choices");
            String lastDialogSeen = (String) readInheritedField(step, "lastDialogSeen");
            Set<Widget> matches = new HashSet<>();

            for (Object rule : choices)
            {
                if (!"DialogChoiceStep".equals(rule.getClass().getSimpleName()))
                {
                    throw new IllegalStateException(
                        "This quest uses an unsupported dialogue rule. Select the answer manually, then resume.");
                }

                String expectedPreviousLine = (String) readInheritedField(rule, "expectedPreviousLine");
                if (expectedPreviousLine != null
                    && (lastDialogSeen == null || !lastDialogSeen.contains(expectedPreviousLine)))
                {
                    continue;
                }

                List<String> excludedStrings = (List<String>) readInheritedField(rule, "excludedStrings");
                if (excludedStrings != null)
                {
                    int excludedGroupId = ((Integer) readInheritedField(rule, "excludedGroupId")).intValue();
                    int excludedChildId = ((Integer) readInheritedField(rule, "excludedChildId")).intValue();
                    Widget exclusionWidget = client.getWidget(excludedGroupId, excludedChildId);
                    boolean excluded = false;
                    if (exclusionWidget != null && exclusionWidget.getChildren() != null)
                    {
                        Widget[] exclusionChildren = exclusionWidget.getChildren();
                        for (int child = 0; child < exclusionChildren.length; child++)
                        {
                            Widget current = exclusionChildren[child];
                            if (current == null)
                            {
                                continue;
                            }
                            for (String excludedString : excludedStrings)
                            {
                                if (normalizeDialogueText(current.getText()).contains(excludedString))
                                {
                                    excluded = true;
                                }
                            }
                        }
                    }
                    if (excluded)
                    {
                        continue;
                    }
                }

                int choiceById = ((Integer) readInheritedField(rule, "choiceById")).intValue();
                String choice = (String) readInheritedField(rule, "choice");
                Pattern pattern = (Pattern) readInheritedField(rule, "pattern");
                int varbitId = ((Integer) readInheritedField(rule, "varbitId")).intValue();

                if (choiceById < 0 && varbitId >= 0)
                {
                    Map<Integer, String> byValue =
                        (Map<Integer, String>) readInheritedField(rule, "varbitValueToAnswer");
                    choice = byValue == null ? null : byValue.get(client.getVarbitValue(varbitId));
                }

                if (children == null)
                {
                    continue;
                }

                // RuneLite dialogue option child 0 is the heading/container; answer options begin at 1.
                for (int childIndex = 1; childIndex < children.length; childIndex++)
                {
                    Widget child = children[childIndex];
                    if (child == null || child.isHidden())
                    {
                        continue;
                    }
                    if (choiceById >= 0 && choiceById != childIndex)
                    {
                        continue;
                    }

                    String text = normalizeDialogueText(child.getText());
                    boolean matchesRule =
                        (choice != null && choice.equals(text))
                            || (pattern != null && pattern.matcher(text).find())
                            || (choiceById >= 0 && choice == null && pattern == null);
                    if (matchesRule)
                    {
                        matches.add(child);
                    }
                }
            }

            if (matches.size() == 1)
            {
                return matches.iterator().next();
            }

            throw new IllegalStateException(
                "Quest Helper does not identify one dialogue answer. Select the answer manually, then press Walk to quest step to resume.");
        }
        catch (Exception ex)
        {
            throw new IllegalStateException(
                "Could not read Quest Helper's dialogue rules. Update Quest Helper or answer manually.", ex);
        }
    }

    static Object readInheritedField(Object object, String name) throws ReflectiveOperationException
    {
        if (object == null)
        {
            throw new NoSuchFieldException(name);
        }

        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass())
        {
            try
            {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(object);
            }
            catch (NoSuchFieldException ignored)
            {
                // Continue through the inheritance chain.
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static String normalizeDialogueText(String value)
    {
        if (value == null)
        {
            return "";
        }
        return Text.removeTags(value).replaceFirst("^(\\[\\d+\\]\\s*)+", "").trim();
    }

    private static Widget getOptionsWidget(Client client)
    {
        Widget widget = client.getWidget(219, 1);
        return widget != null && !widget.isHidden() ? widget : null;
    }

    private static Widget getContinueWidget(Client client)
    {
        for (int[] root : CONTINUE_WIDGETS)
        {
            Widget widget = client.getWidget(root[0], root[1]);
            if (widget != null && !widget.isHidden()
                && normalizeDialogueText(widget.getText()).equalsIgnoreCase("Click here to continue"))
            {
                return widget;
            }
        }
        return null;
    }
}
