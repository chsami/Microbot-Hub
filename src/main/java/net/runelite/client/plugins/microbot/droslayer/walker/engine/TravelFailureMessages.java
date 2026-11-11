package net.runelite.client.plugins.microbot.droslayer.walker.engine;

import java.util.Locale;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

final class TravelFailureMessages {
   static String explainActionFailure(String message) {
      if (message == null) {
         return null;
      } else {
         String plainText = Text.removeTags(message.replace("<br>", " ")).replace(' ', ' ').trim();
         String normalizedText = plainText.toLowerCase(Locale.ROOT).replace('’', '\'');
         if (normalizedText.matches(
            "(?:the|this) (?:door|gate) is (?:securely )?locked[.!]?"
         )) {
            return plainText
               + " Obtain the required key or unlock access, then retry.";
         } else if (normalizedText.equals("you can't reach that.")
            || normalizedText.equals("i can't reach that!")
            || normalizedText.equals("you cannot reach that.")
            || normalizedText.equals("you can't get there from here.")) {
            return plainText
               + " Move to an accessible side of the target, then retry.";
         } else if (normalizedText.equals("nothing interesting happens.")
            || normalizedText.equals("the key doesn't fit.")) {
            return plainText
               + " Check the required item and target in Quest Helper, then retry.";
         } else if (normalizedText.matches(
               "you (?:don't|do not) have enough (?:space|room) in your inventory[.!]?"
            )
            || normalizedText.matches(
               "you need (?:more )?space in your inventory(?: to .+)?[.!]?"
            )) {
            return plainText
               + " Bank unused items to free inventory space, then retry.";
         } else if (normalizedText.matches(
            "you need (?:an? )?(?:.+ level of [0-9]+|level [0-9]+ .+|to (?:complete|finish|start) .+|.+ to (?:use|enter|open|board|cross|climb) .+)[.!]?"
         )) {
            return plainText
               + " Meet the stated requirement, then retry.";
         } else {
            return null;
         }
      }
   }

   static String describeTravelFailure(String reason, WorldPoint destination, String routeObjectName) {
      if (reason.startsWith("preflight-equipment-")) {
         String equipmentName = reason.substring(20).replace('_', ' ');
         return "Could not equip "
            + equipmentName
            + ". Make sure it is available and you meet its requirements, then retry.";
      }

      switch (reason) {
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
            return "Could not prepare a route to "
               + String.valueOf(destination)
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
            String missingObject = routeObjectName == null ? "the next route object" : routeObjectName;
            return "Could not find "
               + missingObject
               + " along the route. Check the area and retry.";
         case "local-path-exhausted-before-arrival":
            return "The route ended before reaching "
               + String.valueOf(destination)
               + ". Retry or choose a nearby tile.";
         case "rejected-teleport-route-unavailable":
            return "Could not use the planned teleport or find another route. Check the teleport's requirements, then retry.";
         case "rejected-transport-route-unavailable":
            return "Could not use the planned transport or find another route. Check its requirements, then retry.";
         default:
            return "Could not continue the route to "
               + String.valueOf(destination)
               + ". Retry from your current location or choose another destination.";
      }
   }
}
