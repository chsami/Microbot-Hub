package net.runelite.client.plugins.microbot.droefficientwalker.engine;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;

@ConfigGroup("efficientwalker")
public interface WalkerEngineConfig extends Config {
   String GROUP = "efficientwalker";
   @ConfigSection(name = "General", description = "General walking and interaction settings", position = -2)
   String generalSection = "generalSection";
   @ConfigSection(name = "Hotkeys", description = "Keyboard shortcuts for walking", position = -1)
   String hotkeysSection = "hotkeysSection";
   @ConfigSection(name = "Route modification", description = "Choose which optional shortcuts Efficient Walker may include", position = 0)
   String routeModificationSection = "routeModificationSection";
   @ConfigSection(name = "Debug", description = "Diagnostic options", position = 1, closedByDefault = true)
   String debugSection = "debugSection";

   @ConfigItem(
      keyName = "handleInteractions",
      name = "Automate quest steps",
      description = "Press Walk to quest step once to follow quest targets, handle supported interactions and dialogue; Cancel walk stops automation",
      position = 0,
      section = "generalSection"
   )
   default boolean handleInteractions() {
      return false;
   }

   @ConfigItem(
      keyName = "walkToNearestBankHotkey",
      name = "Walk to nearest bank",
      description = "Walk to the nearest reachable bank",
      position = 0,
      section = "hotkeysSection"
   )
   default Keybind walkToNearestBankHotkey() {
      return Keybind.NOT_SET;
   }

   @ConfigItem(
      keyName = "walkToQuestStepHotkey",
      name = "Walk to quest step",
      description = "Walk to the current Quest Helper step; requires Quest Helper and a selected quest",
      position = 1,
      section = "hotkeysSection"
   )
   default Keybind walkToQuestStepHotkey() {
      return Keybind.NOT_SET;
   }

   @ConfigItem(
      keyName = "cancelWalkHotkey",
      name = "Cancel walk",
      description = "Cancel the current walk and clear its route",
      position = 2,
      section = "hotkeysSection"
   )
   default Keybind cancelWalkHotkey() {
      return Keybind.NOT_SET;
   }

   @ConfigItem(
      keyName = "useTeleportations",
      name = "Use teleportations",
      description = "Use supported standard spells and tablets when they improve a route",
      position = 0,
      section = "routeModificationSection"
   )
   default boolean useTeleportations() {
      return true;
   }

   @ConfigItem(
      keyName = "useItemsFromBank",
      name = "Use items from bank",
      description = "Fetch teleport items or missing spell runes when the bank detour saves at least 15 seconds and 20% of the current route",
      position = 4,
      section = "routeModificationSection"
   )
   default boolean useItemsFromBank() {
      return false;
   }

   @ConfigItem(
      keyName = "useFairyRings",
      name = "Use fairy rings",
      description = "Include available fairy rings when calculating routes",
      position = 3,
      section = "routeModificationSection"
   )
   default boolean useFairyRings() {
      return true;
   }

   @ConfigItem(
      keyName = "allowAgilityShortcuts",
      name = "Allow agility shortcuts",
      description = "Include eligible agility shortcuts when calculating routes",
      position = 1,
      section = "routeModificationSection"
   )
   default boolean allowAgilityShortcuts() {
      return true;
   }

   @ConfigItem(
      keyName = "allowGrappleShortcuts",
      name = "Allow grapple shortcuts",
      description = "Include eligible crossbow and Mith grapple shortcuts when calculating routes",
      position = 2,
      section = "routeModificationSection"
   )
   default boolean allowGrappleShortcuts() {
      return false;
   }

   @ConfigItem(
      keyName = "enableDebugging",
      name = "Enable debugging",
      description = "Writes route planning, movement, obstacle, and transition diagnostics to the client log",
      position = 0,
      section = "debugSection"
   )
   default boolean enableDebugging() {
      return false;
   }
}
