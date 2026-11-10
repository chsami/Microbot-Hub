package net.runelite.client.plugins.microbot.droslayer.walker;

import net.runelite.api.*;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves Quest Helper inventory requirements and item-use instructions.
 */
final class QuestItemResolver
{
    private static final Pattern ITEM_USE_ANY =
        Pattern.compile("\\buse\\s+.+?\\s+(on|with)\\s+", Pattern.CASE_INSENSITIVE);
    private static final Pattern ITEM_USE_TARGET = Pattern.compile(
        "^(?:right[- ]click\\s+)?use\\s+(.+?)\\s+on\\s+(.+?)(?:[.!]|$)",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern MULTI_ACTION =
        Pattern.compile("\\b(and|or|then|all)\\b|[,;]", Pattern.CASE_INSENSITIVE);
    private static final Pattern INVENTORY_PAIR = Pattern.compile(
        "^(?:use\\s+(.+?)\\s+on|combine\\s+(.+?)\\s+with)\\s+(.+?)\\.?$",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern INVENTORY_SINGLE =
        Pattern.compile("^([a-z]+(?:-[a-z]+)?)\\s+(.+?)\\.?$", Pattern.CASE_INSENSITIVE);

    final int itemId;
    final int inventorySlot;
    final String name;

    QuestItemResolver(int itemId, int slot, String name)
    {
        this.itemId = itemId;
        this.inventorySlot = slot;
        this.name = name;
    }

    static String getInstructionText(Object step)
    {
        try
        {
            Object value = QuestHelperBridge.invokeNoArgMethod(step, "getText");
            if (value instanceof List && !((List<?>) value).isEmpty())
            {
                return Text.removeTags(String.valueOf(((List<?>) value).get(0))).trim();
            }
            return "";
        }
        catch (ReflectiveOperationException ex)
        {
            throw interactionFailure("Cannot read the current instruction");
        }
    }

    static QuestItemResolver resolveTargetItem(Client client, QuestHelperBridge.QuestTarget step)
    {
        String instruction = getInstructionText(step.questStep);
        if (!ITEM_USE_ANY.matcher(instruction).find())
        {
            return null;
        }

        Matcher matcher = ITEM_USE_TARGET.matcher(instruction);
        if (!matcher.find())
        {
            throw interactionFailure("The item-use instruction includes another action or target");
        }

        String itemName = normalizeItemText(matcher.group(1));
        String targetName = normalizeItemText(matcher.group(2));
        if (MULTI_ACTION.matcher(matcher.group()).find())
        {
            throw interactionFailure("This instruction names multiple items or actions");
        }

        String highlightedTarget = step.npc == null ? null : step.npc.getName();
        if (step.object != null)
        {
            ObjectComposition definition = client.getObjectDefinition(step.object.getId());
            if (definition != null && definition.getImpostorIds() != null)
            {
                definition = definition.getImpostor();
            }
            highlightedTarget = definition == null ? null : definition.getName();
        }

        if (highlightedTarget == null || !containsWholePhrase(targetName, normalizeItemText(highlightedTarget)))
        {
            throw interactionFailure("The instruction's item-use target does not match the highlighted target");
        }

        return resolveInventoryItem(client, step.questStep, itemName, true);
    }

    static QuestItemResolver resolveInventoryItem(Client client, Object source, String requestedName, boolean strict)
    {
        String normalizedRequested = normalizeItemText(requestedName);
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        if (inventory == null)
        {
            throw interactionFailure("Cannot read inventory");
        }

        try
        {
            Collection<?> requirements = (Collection<?>) QuestHelperBridge.invokeNoArgMethod(source, "getRequirements");
            int iconItemId = ((Integer) QuestDialogueController.readInheritedField(source, "iconItemID")).intValue();
            Map<Integer, QuestItemResolver> candidates = new LinkedHashMap<>();
            boolean supported = false;

            for (Object requirement : requirements)
            {
                if (!"ItemRequirement".equals(requirement.getClass().getSimpleName()))
                {
                    continue;
                }
                if (!Boolean.TRUE.equals(QuestHelperBridge.invokeNoArgMethod(requirement, "isActualItem")))
                {
                    continue;
                }
                if (Boolean.TRUE.equals(QuestHelperBridge.invokeNoArgMethod(requirement, "mustBeEquipped")))
                {
                    continue;
                }

                Method shouldRender = requirement.getClass()
                    .getMethod("shouldRenderItemHighlights", Client.class);
                if (!Boolean.TRUE.equals(shouldRender.invoke(requirement, client)))
                {
                    continue;
                }

                @SuppressWarnings("unchecked")
                Collection<Object> rawIds = (Collection<Object>) QuestHelperBridge.invokeNoArgMethod(requirement, "getAllIds");
                String requirementName = String.valueOf(QuestHelperBridge.invokeNoArgMethod(requirement, "getName"));
                boolean nameMatches = containsWholePhrase(normalizedRequested, normalizeItemText(requirementName))
                    || containsWholePhrase(normalizeItemText(requirementName), normalizedRequested);

                for (Object rawId : rawIds)
                {
                    int id = ((Number) rawId).intValue();
                    if (id < 0)
                    {
                        continue;
                    }
                    ItemComposition definition = client.getItemDefinition(id);
                    if (definition != null)
                    {
                        nameMatches |= containsWholePhrase(normalizedRequested, normalizeItemText(definition.getName()));
                    }
                }

                Method shouldHighlight = requirement.getClass()
                    .getMethod("shouldHighlightInInventory", Client.class);
                boolean highlighted = Boolean.TRUE.equals(shouldHighlight.invoke(requirement, client));

                if (!nameMatches)
                {
                    continue;
                }
                if (strict && !highlighted && !containsId(rawIds, iconItemId))
                {
                    continue;
                }

                supported = true;
                int required = ((Number) QuestHelperBridge.invokeNoArgMethod(requirement, "getQuantity")).intValue();
                if (required <= 0)
                {
                    throw interactionFailure("The required item quantity is unclear");
                }

                int total = 0;
                for (Item item : inventory.getItems())
                {
                    if (item != null && containsId(rawIds, item.getId()))
                    {
                        total += item.getQuantity();
                    }
                }
                if (total < required)
                {
                    throw interactionFailure("Need " + (required - total) + " more " + requirementName + " in inventory");
                }

                Item[] items = inventory.getItems();
                for (int slot = 0; slot < items.length; slot++)
                {
                    Item item = items[slot];
                    if (item == null || item.getQuantity() <= 0 || !containsId(rawIds, item.getId()))
                    {
                        continue;
                    }

                    ItemComposition definition = client.getItemDefinition(item.getId());
                    if (definition == null || definition.getNote() != -1)
                    {
                        continue;
                    }

                    candidates.putIfAbsent(
                        item.getId(), new QuestItemResolver(item.getId(), slot, definition.getName()));
                }
            }

            if (candidates.size() == 1)
            {
                return candidates.values().iterator().next();
            }
            if (supported)
            {
                throw interactionFailure("Cannot choose one supported inventory item");
            }
            throw interactionFailure("Item metadata does not uniquely support the instruction");
        }
        catch (Exception ex)
        {
            throw interactionFailure("Cannot read compatible Quest Helper item metadata");
        }
    }

    static InventoryItemAction resolveInventoryAction(Client client, Object step)
    {
        String instruction = getInstructionText(step);
        if (MULTI_ACTION.matcher(instruction).find())
        {
            throw interactionFailure("This inventory instruction contains multiple items or actions");
        }

        Matcher pair = INVENTORY_PAIR.matcher(instruction);
        if (pair.matches())
        {
            String firstName = pair.group(1) != null ? pair.group(1) : pair.group(2);
            QuestItemResolver first = resolveInventoryItem(client, step, normalizeItemText(firstName), false);
            QuestItemResolver second = resolveInventoryItem(client, step, normalizeItemText(pair.group(3)), false);
            if (first.inventorySlot == second.inventorySlot)
            {
                throw interactionFailure("The two inventory items resolve to the same slot");
            }
            return new InventoryItemAction(first, second, "Use");
        }

        Matcher single = INVENTORY_SINGLE.matcher(instruction);
        if (!single.matches() || "Use".equalsIgnoreCase(single.group(1)))
        {
            throw interactionFailure("Unsupported inventory instruction");
        }

        QuestItemResolver item = resolveInventoryItem(client, step, normalizeItemText(single.group(2)), false);
        return new InventoryItemAction(item, null, single.group(1));
    }

    boolean isSelected(Client client)
    {
        Widget selected = client.getSelectedWidget();
        return client.isWidgetSelected()
            && selected != null
            && selected.getId() == 9764864
            && selected.getIndex() == this.inventorySlot
            && selected.getItemId() == this.itemId;
    }

    void clearSelection(Client client)
    {
        if (isSelected(client))
        {
            client.setWidgetSelected(false);
        }
    }

    private static boolean containsId(Collection<?> ids, int id)
    {
        for (Object value : ids)
        {
            if (((Number) value).intValue() == id)
            {
                return true;
            }
        }
        return false;
    }

    private static boolean containsWholePhrase(String haystack, String phrase)
    {
        return !phrase.isEmpty() && (" " + haystack + " ").contains(" " + phrase + " ");
    }

    private static String normalizeItemText(String value)
    {
        if (value == null)
        {
            return "";
        }
        return Text.removeTags(value)
            .toLowerCase(Locale.ROOT)
            .replaceAll("\\([^)]*\\)", " ")
            .replace('-', ' ')
            .replaceAll("\\b(?:a|an|the)\\b", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private static IllegalStateException interactionFailure(String message)
    {
        return new IllegalStateException(
            message + ". Follow Quest Helper manually, then press Walk to quest step to resume.");
    }

    static final class InventoryItemAction
    {
        final QuestItemResolver selectedItem;
        final QuestItemResolver targetItem;
        final String action;

        InventoryItemAction(QuestItemResolver first, QuestItemResolver second, String action)
        {
            this.selectedItem = first;
            this.targetItem = second;
            this.action = action;
        }
    }
}
