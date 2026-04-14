package net.runelite.client.plugins.microbot.leftclickcast;

import com.google.inject.Provides;
import java.util.concurrent.CompletableFuture;
import javax.inject.Inject;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Menu;
import net.runelite.api.NPC;
import net.runelite.api.ParamID;
import net.runelite.api.Player;
import net.runelite.api.StructComposition;
import net.runelite.api.events.PostMenuSort;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Keybind;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.util.HotkeyListener;

@PluginDescriptor(
	name = PluginConstants.PERT + "Left-Click Cast",
	description = "Replaces left-click Attack on NPCs with a preconfigured Cast Spell action.",
	tags = {"magic", "combat", "spell", "left-click", "cast", "pvm", "pvp"},
	authors = {"Pert"},
	version = LeftClickCastPlugin.version,
	minClientVersion = "2.0.13",
	enabledByDefault = PluginConstants.DEFAULT_ENABLED,
	isExternal = PluginConstants.IS_EXTERNAL
)
public class LeftClickCastPlugin extends Plugin
{
	static final String version = "1.1.0";

	private static final int SLOT_COUNT = 5;

	@Inject
	private Client client;

	@Inject
	private LeftClickCastConfig config;

	@Inject
	private KeyManager keyManager;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private ConfigManager configManager;

	private volatile int activeSlot = 0;

	private final HotkeyListener[] hotkeyListeners = new HotkeyListener[SLOT_COUNT];

	@Provides
	LeftClickCastConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(LeftClickCastConfig.class);
	}

	@Override
	protected void startUp()
	{
		activeSlot = 0;
		for (int i = 0; i < SLOT_COUNT; i++)
		{
			final int slotIndex = i;
			HotkeyListener listener = new HotkeyListener(() -> slotHotkeyFor(slotIndex))
			{
				@Override
				public void hotkeyPressed()
				{
					onSlotHotkey(slotIndex);
				}
			};
			hotkeyListeners[i] = listener;
			keyManager.registerKeyListener(listener);
		}
		migrateLegacySpellKey();
	}

	@Override
	protected void shutDown()
	{
		for (int i = 0; i < hotkeyListeners.length; i++)
		{
			HotkeyListener listener = hotkeyListeners[i];
			if (listener != null)
			{
				keyManager.unregisterKeyListener(listener);
				hotkeyListeners[i] = null;
			}
		}
	}

	@Subscribe
	public void onPostMenuSort(PostMenuSort event)
	{
		// Don't mutate while the right-click menu is open — entries are frozen at open-time.
		if (client.isMenuOpen())
		{
			return;
		}
		if (!config.enabled())
		{
			return;
		}
		PertTargetSpell spell = slotSpellFor(activeSlot);
		if (spell == null)
		{
			return;
		}
		if (config.requireMagicWeapon() && !isMagicWeaponEquipped())
		{
			return;
		}

		Menu menu = client.getMenu();
		MenuEntry[] entries = menu.getMenuEntries();

		// Find the top-most NPC or Player Attack entry (the game's already-sorted left-click candidate).
		int attackIdx = -1;
		Actor targetActor = null;
		for (int i = entries.length - 1; i >= 0; i--)
		{
			MenuEntry e = entries[i];
			if (!"Attack".equals(e.getOption()))
			{
				continue;
			}
			if (e.getNpc() != null)
			{
				attackIdx = i;
				targetActor = e.getNpc();
				break;
			}
			if (e.getPlayer() != null)
			{
				attackIdx = i;
				targetActor = e.getPlayer();
				break;
			}
		}
		if (attackIdx < 0)
		{
			return;
		}

		MenuEntry attack = entries[attackIdx];
		// Rs2Magic.castOn requires Rs2NpcModel for NPCs but accepts raw Player (Rs2PlayerModel implements Player).
		final Actor dispatchTarget = targetActor instanceof NPC
			? new Rs2NpcModel((NPC) targetActor)
			: (Player) targetActor;
		final PertTargetSpell dispatchSpell = spell;
		attack.setOption("Cast " + dispatchSpell.getDisplayName());
		attack.setType(MenuAction.RUNELITE);
		// Rs2Magic.castOn uses sleepUntil, which is a no-op on the client thread — dispatch off-thread.
		attack.onClick(e -> CompletableFuture.runAsync(
			() -> Rs2Magic.castOn(dispatchSpell.getMagicAction(), dispatchTarget)));

		// Move to the tail of the array — that slot is the left-click action in RuneLite's menu model.
		if (attackIdx != entries.length - 1)
		{
			entries[attackIdx] = entries[entries.length - 1];
			entries[entries.length - 1] = attack;
			menu.setMenuEntries(entries);
		}
	}

	private Keybind slotHotkeyFor(int index)
	{
		switch (index)
		{
			case 0:
				return config.slot1Hotkey();
			case 1:
				return config.slot2Hotkey();
			case 2:
				return config.slot3Hotkey();
			case 3:
				return config.slot4Hotkey();
			case 4:
				return config.slot5Hotkey();
			default:
				return Keybind.NOT_SET;
		}
	}

	private PertTargetSpell slotSpellFor(int index)
	{
		switch (index)
		{
			case 0:
				return config.slot1Spell();
			case 1:
				return config.slot2Spell();
			case 2:
				return config.slot3Spell();
			case 3:
				return config.slot4Spell();
			case 4:
				return config.slot5Spell();
			default:
				return config.slot1Spell();
		}
	}

	private void onSlotHotkey(int index)
	{
		activeSlot = index;
		if (config.activeSlotChatMessage())
		{
			PertTargetSpell spell = slotSpellFor(index);
			String display = spell != null ? spell.getDisplayName() : "(no spell)";
			chatMessageManager.queue(QueuedMessage.builder()
				.type(ChatMessageType.GAMEMESSAGE)
				.value("Left-Click Cast: now casting " + display)
				.build());
		}
	}

	// Best-effort: if the user had previously set the legacy `spell` key to a non-default value and
	// slot1Spell is still at its default, copy the legacy value into slot1Spell so existing configs keep working.
	private void migrateLegacySpellKey()
	{
		try
		{
			PertTargetSpell legacy = configManager.getConfiguration(
				"leftclickcast", "spell", PertTargetSpell.class);
			if (legacy == null || legacy == PertTargetSpell.FIRE_STRIKE)
			{
				return;
			}
			if (config.slot1Spell() != PertTargetSpell.FIRE_STRIKE)
			{
				return;
			}
			configManager.setConfiguration("leftclickcast", "slot1Spell", legacy);
		}
		catch (Exception ignored)
		{
			// Migration is best-effort; ignore any deserialization or storage errors.
		}
	}

	// A weapon counts as "magic" when its style struct exposes Casting or Defensive Casting.
	// Mirrors the core AttackStylesPlugin logic (EnumID.WEAPON_STYLES + ParamID.ATTACK_STYLE_NAME).
	private boolean isMagicWeaponEquipped()
	{
		int weaponType = client.getVarbitValue(VarbitID.COMBAT_WEAPON_CATEGORY);
		EnumComposition weaponStyles = client.getEnum(EnumID.WEAPON_STYLES);
		if (weaponStyles == null)
		{
			return false;
		}
		int styleEnumId = weaponStyles.getIntValue(weaponType);
		if (styleEnumId == -1)
		{
			return false;
		}
		int[] styleStructs = client.getEnum(styleEnumId).getIntVals();
		for (int structId : styleStructs)
		{
			StructComposition sc = client.getStructComposition(structId);
			if (sc == null)
			{
				continue;
			}
			String name = sc.getStringValue(ParamID.ATTACK_STYLE_NAME);
			if ("Casting".equalsIgnoreCase(name) || "Defensive Casting".equalsIgnoreCase(name))
			{
				return true;
			}
		}
		return false;
	}
}
