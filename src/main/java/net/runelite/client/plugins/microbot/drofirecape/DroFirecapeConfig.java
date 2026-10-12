/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;

@ConfigGroup(DroFirecapeConfig.GROUP)
@ConfigInformation("Automated Fight Caves, all 63 waves & 15 rotations. Create an Inventory Setup. Handles TzHaar travel, banking. Pre-determined positioning, protection prayers, potions, and thralls. Recovery requests = Pause after wave to heal up; the resume is automatic. Melee mode untested. Pure mode was tested in depth. It is very likely to fail unless purple sweets (150+), 1-tick prayers, rotation 5, and recovery, and prayer conservation is enabled in order of importance. View READ ME for example gear-setups.")
public interface DroFirecapeConfig extends Config {
    String GROUP="DroFirecape";
    @ConfigItem(keyName="pureMode",name="Pure mode",description="Low-Defence positioning with checked rock cover, contact separation and tiny-monster cleanup. Recovery remains in pure mode. Uses an isolated controller. Restart after enabling this option from regular mode.",position=4)
    default boolean pureMode(){return false;}
    @ConfigItem(keyName="nativeTickPrayers",name="1-tick prayers",description="Direct protection switches before predicted launches, with same-tick OFF/ON pairs when synchronized. No resets during movement, Jad or conflicting styles. Uncertain timing holds protection. Independent of Pure mode. Restart after enabling this option from regular mode. OFF retains the established visible prayer-button system.",position=5)
    default boolean nativeTickPrayers(){return false;}
    @ConfigItem(keyName="prayerConservation",name="Prayer conservation",description="Optional controller only (Pure or 1-tick prayers). Enter with prayers off and skip early empty-wave overheads. Use offensive prayers only against threatening or untrapped rangers and big melees, then Jad. Protection against incoming attacks stays enabled. OFF preserves the established demand policy.",position=7)
    default boolean prayerConservation(){return false;}
    default boolean demonstrationLures(){return true;}
    default boolean useThralls(){return true;}
    @ConfigItem(keyName="selectedInventorySetup",name="Inventory Setup",description="Authoritative equipment and inventory. Bank must contain the full setup.",position=1)
    default InventorySetup inventorySetup(){return null;}
    @ConfigItem(keyName="meleeCape",name="Melee cape (BETA)",description="OFF = ranged. ON = melee contact routes, melee-aware protection and automatic melee offensive prayers. Bring a melee Inventory Setup and normal combat boosts. Attack range is fixed to one tile; ranged peeks are disabled. Thralls are detected automatically in either combat mode. Restart the plugin after changing mode.",position=3)
    default boolean meleeCape(){return false;}
    @Range(min=0,max=15)
    @ConfigItem(keyName="entryRotation",name="Entry rotation (0 = any)",description="0 accepts any calibrated rotation. Set 2 for Rotation 2, 5 for Rotation 5, or another 1-15 value. Waits on this world; never hops to search for rotations. The observed run continues even if its rotation differs.",position=2)
    default int entryRotation(){return 0;}
    // Fixed internal defaults; removed options cannot retain stale saved overrides.
    default int weaponRange(){return 0;}
    @Range(min=5,max=70)
    @ConfigItem(keyName="restorePrayer",name="Restore prayer at",description="Sip a prayer potion or super restore before prayer is depleted.",position=12)
    default int restorePrayer(){return 25;}
    @Range(min=20,max=90)
    @ConfigItem(keyName="eatPercent",name="Heal at HP %",description="Healing starts at this HP percentage. Optional controller stops at a recovery buffer instead of repeatedly topping up to full. Low prayer alone does not start healing. Paused-wave recovery overbrew is separate.",position=6)
    default int eatPercent(){return 60;}
    @ConfigItem(keyName="usePurpleSweets",name="Use purple sweets",description="Heal to full during clear-wave gaps or while all remaining monsters are safely trapped. Bring sweets in your Inventory Setup. Emergency threshold healing takes priority; abort healing as soon as a threat approaches.",position=14)
    default boolean usePurpleSweets(){return false;}
    @ConfigItem(keyName="offensivePrayer",name="Auto best offensive prayer",description="After protection is confirmed, select the strongest usable prayer for this run: Rigour/Deadeye/Eagle Eye and lower ranged prayers, or Piety/Chivalry and the best available Strength + Attack combination. Checks unlocks and Defence requirements.",position=13)
    default boolean offensivePrayer(){return true;}
    default boolean rangingPotion(){return true;}
    default boolean blowpipeSpecial(){return true;}
    @ConfigItem(keyName="energyPause",name="Late-wave recovery pauses",description="OFF by default. From the selected wave, click Logout once during combat, finish the wave, recover HP/stats/prayer and run energy to 40%, then world-hop to resume. Retains the old energyPause setting; never requests a pause during Jad.",position=8)
    default boolean energyPause(){return false;}
    default int resumeEnergy(){return 40;}
    @Range(min=1,max=62)
    @ConfigItem(keyName="recoveryStartWave",name="Recovery starting wave",description="When recovery pauses are enabled, normal mode requests a pause each wave through 62; Pure requests one only at 70% HP or below, subject to its safety gates. Restart after changing recovery settings.",position=9)
    default int recoveryStartWave(){return 56;}
    @ConfigItem(keyName="recoveryOverbrew",name="Overbrew regardless of prayer",description="Optional controller, waves 53+: start one full overbrew batch per wave at about 95% HP, regardless of prayer. Sip until the full boosted HP target, then restore stats. Threshold healing remains enabled on every wave; low prayer alone does not start a healing batch. Also overbrew during late-wave recovery pauses.",position=10)
    default boolean recoveryOverbrew(){return false;}
    @Range(min=50,max=100)
    @ConfigItem(keyName="recoveryPrayerPercent",name="Prayer before resuming %",description="During a recovery pause, refill prayer to this percentage before world-hopping and continuing. Default 90%. Independent of the overbrew HP setting.",position=11)
    default int recoveryPrayerPercent(){return 90;}
    default boolean strictZeroExposure(){return false;}
    default boolean observeOnly(){return false;}
    @ConfigItem(keyName="hideOverlay",name="Hide overlay",description="Hide the compact Firecape card while the script and diagnostic logs continue running.",position=0)
    default boolean hideOverlay(){return false;}
    @ConfigItem(keyName="sceneOverlay",name="Legacy scene diagnostics",description="Compatibility key. Movement, target and spawn diagnostics now go to logs.",position=13,hidden=true)
    default boolean sceneOverlay(){return false;}
    @ConfigItem(keyName="recordRun",name="Record run",description="Write combat events and collision maps to .runelite/dro-firecape for run review. Enabled by default.",position=15)
    default boolean recordRun(){return true;}
    default boolean diagnostics(){return recordRun();}
    default boolean exitOnDamage(){return false;}
}
