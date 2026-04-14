package net.runelite.client.plugins.microbot.leftclickcast;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Provides;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Varbits;
import net.runelite.api.events.MenuEntryAdded;
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
@Slf4j
public class LeftClickCastPlugin extends Plugin
{
	static final String version = "1.0.0";

	// Magic WeaponType ordinals against varbit 357 (EQUIPPED_WEAPON_TYPE).
	// Sourced from the RuneLite core WeaponType enum: STAFF, BLADED_STAFF, POWERED_STAFF, POWERED_WAND.
	private static final Set<Integer> MAGIC_WEAPON_TYPES = ImmutableSet.of(22, 23, 26, 27);

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
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (!config.enabled())
		{
			return;
		}

		PertTargetSpell spell = config.spell();
		if (spell == null)
		{
			return;
		}

		if (!"Attack".equals(event.getOption()))
		{
			return;
		}

		MenuEntry entry = event.getMenuEntry();
		NPC npc = entry.getNpc();
		if (npc == null)
		{
			return;
		}

		if (config.requireMagicWeapon() && !isMagicWeaponEquipped())
		{
			return;
		}

		client.getMenu().createMenuEntry(-1)
			.setOption("Cast")
			.setTarget("<col=00ff00>" + spell.getDisplayName() + "</col> " + event.getTarget())
			.setType(net.runelite.api.MenuAction.RUNELITE)
			// Rs2Magic.castOn uses sleepUntil, which is a no-op on the client thread — dispatch off-thread.
			.onClick(e -> CompletableFuture.runAsync(
				() -> Rs2Magic.castOn(spell.getMagicAction(), new Rs2NpcModel(npc))));
	}

	private boolean isMagicWeaponEquipped()
	{
		return MAGIC_WEAPON_TYPES.contains(client.getVarbitValue(Varbits.EQUIPPED_WEAPON_TYPE));
	}
}
