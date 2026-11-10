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

    static String describeTravelFailure(String reason, WorldPoint destination, String objectLabel)
    {
        if (reason == null)
        {
            return "Could not continue the route to " + String.valueOf(destination)
                + ". Retry from your current location or choose another destination.";
        }

        if (reason.startsWith("preflight-equipment-"))
        {
            String equipment = reason.substring("preflight-equipment-".length()).replace('_', ' ');
            return "Could not equip " + equipment
                + ". Make sure it is available and you meet its requirements, then retry.";
        }

        switch (reason)
        {
            case "quest-approach-unavailable":
                return "No supported route to a safe quest approach tile. Move closer to the quest marker, then retry.";
            case "preflight-auto-retaliate":
                return "Could not turn off Auto Retaliate. Turn it off in Combat Options, then retry.";
            case "preflight-run":
                return "Could not turn on Run. Turn it on, then retry.";
            case "nearest-bank-unavailable":
                return "Could not find a reachable bank. Move closer to a bank and retry.";
            case "no-clickable-walk-target":
                return "Could not select the next walking tile. Make sure the minimap is visible, then retry.";
            case "player-state-unavailable":
                return "Could not read your location. Wait until you are fully logged in, then retry.";
            case "collision-unavailable":
                return "Could not read the area's map. Wait for the area to finish loading, then retry.";
            case "fairy-ring-access-lost":
                return "Fairy ring access or the required staff is no longer available. Equip a dramen or lunar staff, or check your unlocks, then retry.";
            case "fairy-ring-selection-failed":
                return "Could not confirm the fairy ring destination. Close the fairy ring interface, then retry.";
            case "quetzal-access-lost":
                return "Renu or a required landing site is no longer available. Check your quetzal unlocks, then retry.";
            case "quetzal-selection-failed":
                return "Could not select the quetzal destination. Close the quetzal map and check the landing site is unlocked, then retry.";
            case "quetzal-flight-failed":
                return "Could not confirm your quetzal landing. Wait until the flight has finished, then retry the walk.";
            case "stronghold-answer-unavailable":
            case "stronghold-answer-click-failed":
                return "Could not complete the Stronghold of Security door dialogue. Answer it manually, then retry.";
            case "planning-interrupted":
            case "planning-worker-error":
                return "Route calculation failed. Retry the walk. If it fails again, report the destination and debug log.";
            case "plan-unavailable":
            case "route-unavailable-after-planning":
                return "Could not prepare a route to " + String.valueOf(destination)
                    + ". Retry or choose another destination.";
            case "transition-landing-route-unavailable":
            case "teleport-landing-route-unavailable":
                return "Could not continue the route after travelling. Retry from your current location.";
            case "transition-unexpected-plane":
                return "You arrived on an unexpected floor. Check your location and retry the walk.";
            case "route-world-view-mismatch":
                return "The area changed and the route is no longer valid. Wait for loading to finish, then retry.";
            case "transition-object-unavailable-at-approach":
            case "queued-transition-object-unavailable":
                return "Could not find " + (objectLabel != null ? objectLabel : "the next route object")
                    + " along the route. Check the area and retry.";
            case "local-path-exhausted-before-arrival":
                return "The route ended before reaching " + String.valueOf(destination)
                    + ". Retry or choose a nearby tile.";
            case "rejected-teleport-route-unavailable":
                return "Could not use the planned teleport or find another route. Check the teleport's requirements, then retry.";
            case "rejected-transport-route-unavailable":
                return "Could not use the planned transport or find another route. Check its requirements, then retry.";
            default:
                return "Could not continue the route to " + String.valueOf(destination)
                    + ". Retry from your current location or choose another destination.";
        }
    }
}
