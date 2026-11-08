package net.runelite.client.plugins.microbot.fourtifysbarrows.util.widget;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.TileObject;
import net.runelite.api.ObjectComposition;
import net.runelite.api.widgets.Widget;

public final class WidgetQueries
{
    private WidgetQueries()
    {
    }

    public static boolean visible(Widget widget)
    {
        return widget != null && !widget.isHidden();
    }

    /** An equipment slot shows its item on a dynamic child, not on the slot widget itself. */
    public static boolean showsItem(Widget widget, int id)
    {
        if (widget == null)
        {
            return false;
        }
        if (widget.getItemId() == id)
        {
            return true;
        }
        Widget[] children = widget.getDynamicChildren();
        if (children != null)
        {
            for (Widget child : children)
            {
                if (child != null && child.getItemId() == id)
                {
                    return true;
                }
            }
        }
        return false;
    }

    public static int indexOf(String[] actions, String option)
    {
        if (actions != null)
        {
            for (int i = 0; i < actions.length; i++)
            {
                if (option.equalsIgnoreCase(actions[i]))
                {
                    return i;
                }
            }
        }
        return -1;
    }

    private static boolean containsWidget(Widget widget, Widget target, Set<Widget> visited)
    {
        if (!visible(widget) || !visited.add(widget))
        {
            return false;
        }
        if (widget == target)
        {
            return true;
        }
        for (Widget[] children : new Widget[][] {widget.getDynamicChildren(), widget.getStaticChildren(), widget.getNestedChildren()})
        {
            if (children != null)
            {
                for (Widget child : children)
                {
                    if (containsWidget(child, target, visited))
                    {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean live(Client client, Widget target)
    {
        Set<Widget> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Widget root = client.getWidget(target.getId() >>> 16, 0);
        if (containsWidget(root, target, visited))
        {
            return true;
        }
        for (Widget candidate : client.getWidgetRoots())
        {
            if (containsWidget(candidate, target, visited))
            {
                return true;
            }
        }
        return false;
    }

    public static int objectActionIndex(Client client, TileObject object, String option)
    {
        if (object == null)
        {
            return -1;
        }
        ObjectComposition definition = objectDefinition(client, object);
        return definition == null ? -1 : indexOf(definition.getActions(), option);
    }

    /** The object's definition, resolved to its current impostor when it has one. */
    public static ObjectComposition objectDefinition(Client client, TileObject object)
    {
        ObjectComposition definition = client.getObjectDefinition(object.getId());
        if (definition != null && definition.getImpostorIds() != null)
        {
            definition = definition.getImpostor();
        }
        return definition;
    }

}
