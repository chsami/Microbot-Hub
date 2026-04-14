package net.runelite.client.plugins.microbot.leftclickcast;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;

@ConfigGroup("leftclickcast")
public interface LeftClickCastConfig extends Config
{
	@ConfigItem(
		keyName = "enabled",
		name = "Enabled",
		description = "Replace the left-click Attack option on NPCs with Cast Spell",
		position = 0
	)
	default boolean enabled()
	{
		return true;
	}

	// Retained so existing stored config is not invalidated. Read once at startUp for migration into slot1Spell.
	@ConfigItem(
		keyName = "spell",
		name = "Spell",
		description = "Legacy single-spell setting — migrated into Slot 1 on startup.",
		position = 1
	)
	default PertTargetSpell spell()
	{
		return PertTargetSpell.FIRE_STRIKE;
	}

	@ConfigItem(
		keyName = "requireMagicWeapon",
		name = "Require magic weapon",
		description = "When enabled, the Cast entry is only inserted while a staff, bladed staff, powered staff, or powered wand is equipped. Disable to cast regardless of equipped weapon.",
		position = 2
	)
	default boolean requireMagicWeapon()
	{
		return true;
	}

	@ConfigSection(
		name = "Spell Slots",
		description = "Up to five spells that can be bound to hotkeys for mid-fight swapping.",
		position = 10
	)
	String spellSlotsSection = "spellSlots";

	@ConfigSection(
		name = "Hotkeys",
		description = "Hotkey bindings that switch the active spell slot.",
		position = 11
	)
	String hotkeysSection = "hotkeys";

	@ConfigItem(
		keyName = "slot1Spell",
		name = "Slot 1 Spell",
		description = "Spell for slot 1 (the startup-active slot).",
		section = spellSlotsSection,
		position = 0
	)
	default PertTargetSpell slot1Spell()
	{
		return PertTargetSpell.FIRE_STRIKE;
	}

	@ConfigItem(
		keyName = "slot2Spell",
		name = "Slot 2 Spell",
		description = "Spell for slot 2.",
		section = spellSlotsSection,
		position = 1
	)
	default PertTargetSpell slot2Spell()
	{
		return PertTargetSpell.FIRE_STRIKE;
	}

	@ConfigItem(
		keyName = "slot3Spell",
		name = "Slot 3 Spell",
		description = "Spell for slot 3.",
		section = spellSlotsSection,
		position = 2
	)
	default PertTargetSpell slot3Spell()
	{
		return PertTargetSpell.FIRE_STRIKE;
	}

	@ConfigItem(
		keyName = "slot4Spell",
		name = "Slot 4 Spell",
		description = "Spell for slot 4.",
		section = spellSlotsSection,
		position = 3
	)
	default PertTargetSpell slot4Spell()
	{
		return PertTargetSpell.FIRE_STRIKE;
	}

	@ConfigItem(
		keyName = "slot5Spell",
		name = "Slot 5 Spell",
		description = "Spell for slot 5.",
		section = spellSlotsSection,
		position = 4
	)
	default PertTargetSpell slot5Spell()
	{
		return PertTargetSpell.FIRE_STRIKE;
	}

	@ConfigItem(
		keyName = "slot1Hotkey",
		name = "Slot 1 Hotkey",
		description = "Hotkey that activates slot 1.",
		section = hotkeysSection,
		position = 0
	)
	default Keybind slot1Hotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
		keyName = "slot2Hotkey",
		name = "Slot 2 Hotkey",
		description = "Hotkey that activates slot 2.",
		section = hotkeysSection,
		position = 1
	)
	default Keybind slot2Hotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
		keyName = "slot3Hotkey",
		name = "Slot 3 Hotkey",
		description = "Hotkey that activates slot 3.",
		section = hotkeysSection,
		position = 2
	)
	default Keybind slot3Hotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
		keyName = "slot4Hotkey",
		name = "Slot 4 Hotkey",
		description = "Hotkey that activates slot 4.",
		section = hotkeysSection,
		position = 3
	)
	default Keybind slot4Hotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
		keyName = "slot5Hotkey",
		name = "Slot 5 Hotkey",
		description = "Hotkey that activates slot 5.",
		section = hotkeysSection,
		position = 4
	)
	default Keybind slot5Hotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
		keyName = "activeSlotChatMessage",
		name = "Chat feedback on slot change",
		description = "Post a game chat message when a hotkey switches the active slot.",
		section = hotkeysSection,
		position = 5
	)
	default boolean activeSlotChatMessage()
	{
		return true;
	}
}
