package net.runelite.client.plugins.microbot.leftclickcast;

import com.google.inject.Provides;
import java.util.concurrent.CompletableFuture;
import javax.inject.Inject;
import net.runelite.api.Actor;
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
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;

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
	static final String version = "1.0.0";

	@Inject
	private Client client;

	@Inject
	private LeftClickCastConfig config;

	@Provides
	LeftClickCastConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(LeftClickCastConfig.class);
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
		PertTargetSpell spell = config.spell();
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
		attack.setOption("Cast " + spell.getDisplayName());
		attack.setType(MenuAction.RUNELITE);
		// Rs2Magic.castOn uses sleepUntil, which is a no-op on the client thread — dispatch off-thread.
		attack.onClick(e -> CompletableFuture.runAsync(
			() -> Rs2Magic.castOn(spell.getMagicAction(), dispatchTarget)));

		// Move to the tail of the array — that slot is the left-click action in RuneLite's menu model.
		if (attackIdx != entries.length - 1)
		{
			entries[attackIdx] = entries[entries.length - 1];
			entries[entries.length - 1] = attack;
			menu.setMenuEntries(entries);
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
