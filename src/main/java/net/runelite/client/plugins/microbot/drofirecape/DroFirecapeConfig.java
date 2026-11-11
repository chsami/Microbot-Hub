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
@ConfigInformation("Automated Fight Caves, all 63 waves & 15 rotations, with the pure controller: checked rock cover, contact separation and tiny-monster cleanup. Create an Inventory Setup; ranged and magic (staves, wands, powered staves; Ice/Water autocast is set automatically when none is selected) setups are detected from it. The cave is entered with prayers off. Protection is lazy-flicked: on for the hit tick only, and 1-tick flicked whenever a guard must stay up. The best offensive prayer for the wielded weapon is flicked on the tick before each of your shots at a big melee, ranger, mager (only when no other monster is attacking), Jad or healer, and off after it; never at bats or blobs. Carried Ancient magic is used automatically when the spellbook and runes allow Ice or Blood Burst/Blitz/Barrage: Ice freezes bats first, then big melees, big blobs and split-blob pairs coming at you in the open, and Blood only heals (from below 90% HP to 95%, on a clump or from a safespot). Magers, rangers and Jad are never spell targets; magic gear is labelled the Colosseum 1.0.82 way (weapon rank, magic bonus per slot, void mage helm, Blue Moon set) and only comes off into a free inventory slot. Manual movement: you walk the cave and the script plays the rest. Prayers only: the script only prays. Handles TzHaar travel, banking, potions and thralls. Recovery requests pause after a wave to heal up; the resume is automatic. Purple sweets (150+), rotation 5 and recovery are recommended. View READ ME for example gear setups.")
public interface DroFirecapeConfig extends Config {
    String GROUP="DroFirecape";
    @ConfigItem(keyName="fightInput",name="Fight input",description="Natural mouse and direct invokes use the same fight logic. Direct invokes send every cave action (prayers, attacks, supplies, specials, spells, logout) straight to the client; Natural mouse carries the same actions on the mouse. Walking always uses the Natural mouse.",position=4)
    default FcFightInput fightInput(){return FcFightInput.NATURAL_MOUSE;}
    @ConfigItem(keyName="prayersOnly",name="Prayers only",description="Only flick protection and offensive prayers inside the cave; you play everything else (entering, movement, attacks, gear, potions, inventory). Prayers are invoked directly without opening the prayer book or moving the mouse. Nothing is done outside the cave. Applies on the next start.",position=5)
    default boolean prayersOnly(){return false;}
    @ConfigItem(keyName="manualMovement",name="Manual movement",description="The script plays everything in the cave except walking: prayers, attacks, Ancient magic switches, potions, sweets and the blowpipe special stay automatic, and you move the player yourself (no lures, rock cover, spacing or escape steps, and the camera is left alone). Direct invokes are used. Attacks wait while you walk and go only to a monster in range and sight of your tile, so they never move you. Banking, travel and entry stay automatic. Prayers only takes precedence. Applies on the next start.",position=5)
    default boolean manualMovement(){return false;}
    default boolean demonstrationLures(){return true;}
    default boolean useThralls(){return true;}
    @ConfigItem(keyName="selectedInventorySetup",name="Inventory Setup",description="Authoritative equipment and inventory. Bank must contain the full setup.",position=1)
    default InventorySetup inventorySetup(){return null;}
    @ConfigItem(keyName="startAtEntrance",name="Start at entrance",description="Skip banking, the Inventory Setup and the ranging pre-dose: walk to the Fight Cave entrance with your current gear and inventory, wait for the selected rotation and enter. Teleports to TzHaar first when outside it. Weapon and prayer-potion checks still apply. Get another cape still banks for each later run.",position=2)
    default boolean startAtEntrance(){return false;}
    @Range(min=0,max=15)
    @ConfigItem(keyName="entryRotation",name="Entry rotation (0 = any)",description="0 accepts any calibrated rotation. Set 2 for Rotation 2, 5 for Rotation 5, or another 1-15 value. Waits on this world; never hops to search for rotations. The observed run continues even if its rotation differs.",position=2)
    default int entryRotation(){return 0;}
    // Fixed internal defaults; removed options cannot retain stale saved overrides.
    default int weaponRange(){return 0;}
    @ConfigItem(keyName="logoutNoSupplies",name="Logout No Supplies",description="Before Jad (waves 1-61): when no Saradomin brew is left, click Logout once so the cave pauses after the current wave, keep fighting, then log out with auto-login suppressed (the same logout used for missing supplies and breaks). Waves 62-63 are ignored.",position=12)
    default boolean logoutNoSupplies(){return false;}
    @Range(min=5,max=70)
    @ConfigItem(keyName="restorePrayer",name="Restore prayer at",description="Sip a prayer potion or super restore before prayer is depleted.",position=12)
    default int restorePrayer(){return 25;}
    @Range(min=20,max=90)
    @ConfigItem(keyName="eatPercent",name="Heal at HP %",description="Healing starts at this HP percentage. Healing stops at a recovery buffer instead of repeatedly topping up to full. Low prayer alone does not start healing. Paused-wave recovery overbrew is separate.",position=6)
    default int eatPercent(){return 60;}
    @ConfigItem(keyName="usePurpleSweets",name="Use purple sweets",description="Heal to full during clear-wave gaps or while all remaining monsters are safely trapped. Bring sweets in your Inventory Setup. Emergency threshold healing takes priority; abort healing as soon as a threat approaches.",position=14)
    default boolean usePurpleSweets(){return false;}
    default boolean rangingPotion(){return true;}
    default boolean blowpipeSpecial(){return true;}
    @ConfigItem(keyName="energyPause",name="Late-wave recovery pauses",description="OFF by default. From the selected wave, click Logout once during combat, finish the wave, recover HP/stats/prayer and run energy to 40%, then world-hop to resume. Retains the old energyPause setting; never requests a pause during Jad.",position=8)
    default boolean energyPause(){return false;}
    default int resumeEnergy(){return 40;}
    @Range(min=1,max=62)
    @ConfigItem(keyName="recoveryStartWave",name="Recovery starting wave",description="When recovery pauses are enabled, a pause is requested only at 70% HP or below, subject to its safety gates. Restart after changing recovery settings.",position=9)
    default int recoveryStartWave(){return 56;}
    @ConfigItem(keyName="recoveryOverbrew",name="Overbrew regardless of prayer",description="Waves 53+: start one full overbrew batch per wave at about 95% HP, regardless of prayer. Sip until the full boosted HP target, then restore stats. Threshold healing remains enabled on every wave; low prayer alone does not start a healing batch. Also overbrew during late-wave recovery pauses.",position=10)
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
    /** Always on, never a setting (user, 2026-11-06): the cave trace explains every prayer, attack and switch. */
    default boolean recordRun(){return true;}
    default boolean diagnostics(){return recordRun();}
    default boolean exitOnDamage(){return false;}
    enum AfterExitBehavior {
        LOGOUT("Log out"), GET_ANOTHER_CAPE("Get another cape");
        private final String label;
        AfterExitBehavior(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }
    @ConfigItem(keyName="afterExitBehavior",name="After exit",description="After a cape or a failed cave attempt: log out, or bank/regear and start another cape. Missing supplies always stop and log out.",position=1000)
    default AfterExitBehavior afterExitBehavior(){return AfterExitBehavior.LOGOUT;}
}
