package net.runelite.client.plugins.microbot.droslayer.walker;

import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

import java.util.Locale;

/**
 * Formats recognized travel failures and recovery messages.
 */
final class TravelFailureMessages
{
    static String explainActionFailure(String input)
    {
        if (input == null)
        {
            return null;
        }

        String cleaned = Text.removeTags(input.replace("<br>", " "))
            .replace('\u00A0', ' ')
            .trim();

        String lower = cleaned.toLowerCase(Locale.ROOT).replace('\u2019', '\'');

        if (lower.matches("(?:the|this) (?:door|gate) is (?:securely )?locked[.!]?"))
        {
            return cleaned + " Obtain the required key or unlock access, then retry.";
        }

        if (lower.equals("you can't reach that.")
            || lower.equals("i can't reach that!")
            || lower.equals("you cannot reach that.")
            || lower.equals("you can't get there from here."))
        {
            return cleaned + " Move to an accessible side of the target, then retry.";
        }

        if (lower.equals("nothing interesting happens.")
            || lower.equals("the key doesn't fit."))
        {
            return cleaned + " Check the required item and target in Quest Helper, then retry.";
        }

        if (lower.matches("you (?:don't|do not) have enough (?:space|room) in your inventory[.!]?")
            || lower.matches("you need (?:more )?space in your inventory(?: to .+)?[.!]?"))
        {
            return cleaned + " Bank unused items to free inventory space, then retry.";
        }

        if (lower.matches("you need (?:an? )?(?:.+ level of [0-9]+|level [0-9]+ .+|to (?:complete|finish|start) .+|.+ to (?:use|enter|open|board|cross|climb) .+)[.!]?"))
        {
            return cleaned + " Meet the stated requirement, then retry.";
        }

        return null;
    }

    static String findVisibleActionFailureText(Client client)
    {
        int[][] widgets = {
            {229, 1},
            {229, 3},
            {231, 6},
            {217, 6},
            {193, 2},
            {11, 2}
        };

        for (int[] widgetId : widgets)
        {
            Widget widget = client.getWidget(widgetId[0], widgetId[1]);
            if (widget != null && !widget.isHidden() && explainActionFailure(widget.getText()) != null)
            {
                return widget.getText();
            }
        }

        return null;
    }

}
