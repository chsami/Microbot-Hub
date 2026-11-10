package net.runelite.client.plugins.microbot.drocolosseumprayers;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;

@ConfigGroup(DroColosseumConfig.GROUP)
@ConfigInformation(ColosseumRequirements.HTML)
public interface DroColosseumConfig extends ArenaPreparationConfig {
    String GROUP="drocolosseum";
    @ConfigItem(keyName="setup",name="Inventory Setups",description="Choose the saved gear and inventory to load locally before travel and entry. Uses your saved setup locally.",position=2)
    default InventorySetup setup() { return null; }
    @ConfigItem(keyName="killWith",name="Kill with",description="Style used to kill Colosseum waves. Applies on the next local run.",position=10)
    default ColosseumCombatStyle killWith(){return ColosseumCombatStyle.RANGED;}
    @ConfigItem(keyName="warbandFirst",name="Warband first",description="Style used on the first warband target. Applies on the next local run.",position=11)
    default ColosseumCombatStyle warbandFirst(){return ColosseumCombatStyle.MELEE;}
    @ConfigItem(keyName="bloodHealLast",name="Blood spells on the last enemy",description="With one enemy left in a wave (not Sol), switch to the mage setup and cast its Blood autocast (Barrage or Burst) until HP is 95% of max, then finish it with the usual style. Needs the Ancient spellbook, a Blood spell set to autocast and Magic at its level. Applies on the next local run.",position=12)
    default boolean bloodHealLast(){return false;}
    @Range(min=1,max=12)
    @ConfigItem(keyName="potionsFromWave",name="Potions from wave",description="Ranging and combat potions are only re-drunk from this wave on (a brew lowers both stats). 1 re-pots from the start. The pre-entry boost is unchanged; brews and restores are not affected. Applies on the next local run.",position=14)
    default int potionsFromWave(){return 1;}
    @ConfigItem(keyName="showTilePack",name="Show Colosseum tile pack",description="Draw the community Colosseum tile pack (78 Ground Markers tiles: pillar A/B off-tick spots, steps 1/2, spawn outlines) and each monster's true tile, coloured by the prayer it needs (green ranged, blue magic, red melee, orange Manticore, yellow Sol/Bees). Display only; the script does not use it.",position=13)
    default boolean showTilePack(){return false;}
    @ConfigItem(keyName="logoutAfterRun",name="Log out after death or completion",description="Log out when the Colosseum run ends (death or wave 12 complete) and keep auto-login held until you log in again.",position=9)
    default boolean logoutAfterRun(){return false;}
}
