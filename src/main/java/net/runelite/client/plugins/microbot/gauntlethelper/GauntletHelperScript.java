package net.runelite.client.plugins.microbot.gauntlethelper;

import com.google.inject.Inject;

import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import lombok.Getter;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.misc.Rs2Food;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.grounditem.LootingParameters;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class GauntletHelperScript extends Script {

    public static State gh_state = State.idle;
    private Rs2PrayerEnum nextPrayer = Rs2PrayerEnum.PROTECT_RANGE;
    private HeadIcon BossHeadIcon = null;
    Rs2NpcModel hunllef = null;
    Rs2NpcModel Tornado = null;
    private final int CG_TORNADO = 9039;
    private final int PADDLEFISH_HEAL_VALUE = 20;
    private final AtomicBoolean attackNeeded = new AtomicBoolean(false);

    private final int CD_NPC = 151;
    private final int CD_PRAYOFFENCE = 200;
    private final int CD_PRAYPROTECT = 200;
    private final int CD_EAT = 600;
    private final int CD_DRINK = 600;
    private final int CD_WEAPON = 300;
    private final int CD_RECENTLY = 75;
    private final int BUFFER_MAGICBLAST = 450;

    private long startTime = -1;
    private long now = -1;
    private long TIME_NPC = 150;
    private long TIME_PRAYOFFENCE = -1;
    private long TIME_PRAYPROTECT = -1;
    private long TIME_EAT = -1;
    private long TIME_DRINK = -1;
    private long TIME_WEAPON = -1;
    private long MAGIC_BLAST_END = -1;

    private GauntletHelperConfig config;
    private GauntletHelperPlugin plugin;

    private static final Set<Integer> HUNLLEF_IDS = Set.of(
            9035, 9036, 9037, 9038, // Corrupted Hunllef variants
            9021, 9022, 9023, 9024  // Crystalline Hunllef variants
    );

    private static final Set<Integer> DANGEROUS_TILES = Set.of(
            36047, 36048, // Corrupted tiles (Ground object)
            36150, 36151 // Gauntlet tiles (Ground object)
    );

    private static final Set<Integer> FLOOR_TILES = Set.of(
            36149, 36046 // CG, G Floor Tiles
    );

    @Inject
    public GauntletHelperScript(GauntletHelperPlugin plugin, GauntletHelperConfig config) {
        this.config = config;
        this.plugin = plugin;
    }

    public boolean run() {

        now = System.currentTimeMillis();
        gh_state = config.startstate();
        TIME_NPC = now;
        TIME_PRAYPROTECT = now;
        TIME_EAT = now;
        TIME_DRINK = now;
        TIME_WEAPON = now;
        MAGIC_BLAST_END = now;
        sleep(300);

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                now = System.currentTimeMillis();

                switch (gh_state) {

                    case idle:
                        gh_state = State.fighting;
                        break;
                    case fighting:
                        if (now - TIME_NPC > CD_NPC) checkNPC();
                        if (now - TIME_PRAYPROTECT > CD_PRAYPROTECT) checkProtectPrayers();
                        if (now - MAGIC_BLAST_END < BUFFER_MAGICBLAST) checkProtectPrayers();
                        if (now - TIME_PRAYPROTECT < CD_RECENTLY) return; //Prayers changed, give it extra time

                        if (now - TIME_EAT > CD_EAT) checkFood();
                        if (now - TIME_EAT < CD_EAT) {checkPrayerPotions(); return;} // Combo drink allowed
                        if (now - TIME_EAT < CD_RECENTLY) return; //Eating occurred, give it extra time

                        if (now - TIME_WEAPON > CD_WEAPON) checkWeapon();

                        checkPrayerPotions();
                        //if (now - TIME_DRINK < CD_DRINK) return;
                        if (config.autoattack()) {checkAttack();}

                        checkAttackPrayers();
                        checkSteelSkin();

                        break;

                } // ----- End of States -----

                long totalTime = System.currentTimeMillis() - now;
                logVerbose("Loop took " + totalTime + " ms");

            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 50, TimeUnit.MILLISECONDS);
        return true;
    } //End of Run

    //------------------------------------------------------------------------------------------------------------------
    // --- NPC Checks

    public void checkNPC() {
        TIME_NPC = now;
        Tornado = Rs2Npc.getNpc(CG_TORNADO);
        hunllef = Rs2Npc.getNpcs()
                .filter(npc -> HUNLLEF_IDS.contains(npc.getId()))
                .findFirst()
                .orElse(null);

        if (hunllef == null) {
            nextPrayer = null;
            return;
        } else {gh_state = State.fighting;}

        BossHeadIcon = hunllef.getHeadIcon();
    }


    //------------------------------------------------------------------------------------------------------------------
    //----- Events


    public void proj_mageblast_end(){MAGIC_BLAST_END = System.currentTimeMillis();}
    public void proj_magic_seen(){nextPrayer = Rs2PrayerEnum.PROTECT_MAGIC;}
    public void proj_range_seen(){nextPrayer = Rs2PrayerEnum.PROTECT_RANGE;}

    private void checkProtectPrayers(){
        if (nextPrayer != null && !Rs2Prayer.isPrayerActive(nextPrayer)) {
            TIME_PRAYPROTECT = now;
            SendPrayerToggle(nextPrayer, true);
        }
    }

    private void checkWeapon(){
        if (BossHeadIcon == null) return;
        if (BossHeadIcon == HeadIcon.MELEE) {handleMeleeHeadIcon();}
        if (BossHeadIcon == HeadIcon.RANGED) {handleRangedHeadIcon();}
        if (BossHeadIcon == HeadIcon.MAGIC) {handleMagicHeadIcon();}
    }

    public synchronized Rs2PrayerEnum getNextPrayer() {
        return nextPrayer;
    }

    private static final int[] BOW_IDS = {
            ItemID.GAUNTLET_RANGED_T3_HM, ItemID.GAUNTLET_RANGED_T3,
            ItemID.GAUNTLET_RANGED_T2_HM, ItemID.GAUNTLET_RANGED_T2,
            ItemID.GAUNTLET_RANGED_T1_HM, ItemID.GAUNTLET_RANGED_T1
    };

    private static final int[] STAFF_IDS = {
            ItemID.GAUNTLET_MAGIC_T3_HM, ItemID.GAUNTLET_MAGIC_T3,
            ItemID.GAUNTLET_MAGIC_T2_HM, ItemID.GAUNTLET_MAGIC_T2,
            ItemID.GAUNTLET_MAGIC_T1_HM, ItemID.GAUNTLET_MAGIC_T1
    };

    private static final int[] HALBERD_IDS = {
            ItemID.GAUNTLET_MELEE_T3_HM, ItemID.GAUNTLET_MELEE_T3,
            ItemID.GAUNTLET_MELEE_T2_HM, ItemID.GAUNTLET_MELEE_T2,
            ItemID.GAUNTLET_MELEE_T1_HM, ItemID.GAUNTLET_MELEE_T1
    };


    private void handleMeleeHeadIcon() {
        if (!isStaffEquipped() && hasStaffInInventory()) {equipStaff();}
        else if (!isBowEquipped() && hasBowInInventory()) {equipBow();}
    }

    private void handleRangedHeadIcon() {
        if (!isStaffEquipped() && hasStaffInInventory()) {equipStaff();}
        else if (!isHalberdEquipped() && hasHalberdInInventory()) {equipHalberd();}
    }

    private void handleMagicHeadIcon() {
        if (!isBowEquipped() && hasBowInInventory()) {equipBow();}
        else if (!isHalberdEquipped() && hasHalberdInInventory()) {equipHalberd();}
    }

    private boolean hasWeaponInInventory(int[] ids) {
        for (int id : ids) {
            if (Rs2Inventory.contains(id)) { return true; }
        }
        return false;
    }

    private boolean isWeaponEquipped(int[] ids) {
        for (int id : ids) {
            if (Rs2Equipment.isWearing(id)) { return true; }
        }
        return false;
    }

    private void equipBestAvailable(int[] ids) {
        for (int id : ids) {
            if (Rs2Inventory.contains(id)) {
                Rs2Inventory.equip(id);
                if (!Rs2Player.isMoving()) {attackNeeded.set(true); }
                break;
            }
        }
    }

    private boolean hasBowInInventory() {return hasWeaponInInventory(BOW_IDS);}
    private boolean hasStaffInInventory() {return hasWeaponInInventory(STAFF_IDS);}
    private boolean hasHalberdInInventory() {return hasWeaponInInventory(HALBERD_IDS);}
    private boolean isBowEquipped() {return isWeaponEquipped(BOW_IDS);}
    private boolean isStaffEquipped() {return isWeaponEquipped(STAFF_IDS);}
    private boolean isHalberdEquipped() {return isWeaponEquipped(HALBERD_IDS);}

    private void equipBow() { equipBestAvailable(BOW_IDS); TIME_WEAPON = now;}
    private void equipStaff() { equipBestAvailable(STAFF_IDS); TIME_WEAPON = now;}
    private void equipHalberd() { equipBestAvailable(HALBERD_IDS); TIME_WEAPON = now;}

    ///  --------------------------------------------------------------------------------------------------------------
    ///  --- Prayers --------------------------------------------------------------------------------------------------
    /// ---------------------------------------------------------------------------------------------------------------

    private void checkAttackPrayers() {
        if (isBowEquipped() && (!Rs2Prayer.isPrayerActive(Rs2PrayerEnum.RIGOUR) && !Rs2Prayer.isPrayerActive(Rs2PrayerEnum.EAGLE_EYE) && !Rs2Prayer.isPrayerActive(Rs2PrayerEnum.DEAD_EYE))) {
            toggleRangeAttackPrayer();
        }
        if (isStaffEquipped() && (!Rs2Prayer.isPrayerActive(Rs2PrayerEnum.AUGURY) && !Rs2Prayer.isPrayerActive(Rs2PrayerEnum.MYSTIC_MIGHT) && !Rs2Prayer.isPrayerActive(Rs2PrayerEnum.MYSTIC_VIGOUR))) {
            toggleMagicAttackPrayer();
        }
        if ((isHalberdEquipped()) && (!Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PIETY)) && !(Rs2Prayer.isPrayerActive(Rs2PrayerEnum.INCREDIBLE_REFLEXES) && Rs2Prayer.isPrayerActive(Rs2PrayerEnum.ULTIMATE_STRENGTH))) {
            toggleMeleeAttackPrayer();
        }
    }

    private void checkSteelSkin(){
        if (config.HigherPrayers()) {return;}
        if (!Rs2Prayer.isPrayerActive(Rs2PrayerEnum.STEEL_SKIN) && !Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PIETY)) {
            SendPrayerToggle(Rs2PrayerEnum.STEEL_SKIN, true);
        }
    }

    private void toggleRangeAttackPrayer() {
        if (config.HigherPrayers()) {
            SendPrayerToggle(Rs2PrayerEnum.RIGOUR, true);
        } else if (config.TitansPrayers()){
            SendPrayerToggle(Rs2PrayerEnum.DEAD_EYE, true);
        } else {
            SendPrayerToggle(Rs2PrayerEnum.EAGLE_EYE, true);
        }
    }

    private void toggleMagicAttackPrayer() {
        if (config.HigherPrayers()) {
            SendPrayerToggle(Rs2PrayerEnum.AUGURY, true);
        } else if (config.TitansPrayers()) {
            SendPrayerToggle(Rs2PrayerEnum.MYSTIC_VIGOUR, true);
        } else {
            SendPrayerToggle(Rs2PrayerEnum.MYSTIC_MIGHT, true);
        }
    }

    private void toggleMeleeAttackPrayer() {
        SendPrayerToggle(Rs2PrayerEnum.PIETY, true);
    }

    private void checkFood() {
        int currentHp = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
        int maxHp = Microbot.getClient().getRealSkillLevel(Skill.HITPOINTS);
        int missingHp = maxHp - currentHp;
        if (config.TornadoCheck() && (Tornado == null)) {return;}
        if (config.eatFood()) { if (Rs2Player.isMoving()) { if (missingHp >= PADDLEFISH_HEAL_VALUE) { EatFood(); }}}
        else if (currentHp < config.emergencyeatvalue()) { EatFood(); }
    }

    private void EatFood() {
        Rs2Inventory.interact("Paddlefish", "Eat");
        TIME_EAT = now;
        if (!Rs2Player.isMoving()) {attackNeeded.set(true); }
    }

    private void checkPrayerPotions() {
        int currentPrayer = Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER);
        if (currentPrayer < config.ppotvalue()) {Rs2Inventory.interact("Egniol potion", "Drink");}
    }

    private void checkAttack(){
        if (hunllef == null) return;
        if (Rs2Player.isMoving()) return;
        if (attackNeeded.compareAndSet(true, false)) {
            Rs2Npc.interact(hunllef, "attack");
            attackNeeded.set(false);
        }
    }

    public void SendPrayerToggle(Rs2PrayerEnum prayer, boolean enable) {
        if (prayer == null) return;
        Rs2Prayer.toggle(prayer, enable, true);
    }


    private void logVerbose(String msg) {
        if (config.verboselog()) {
            Microbot.log(msg);
        }
    }


    @Override
    public void shutdown() {
        super.shutdown();
        if (mainScheduledFuture != null) {
            mainScheduledFuture.cancel(true);
        }
        Rs2Prayer.disableAllPrayers();
        Rs2Walker.setTarget(null);
    }



} // End of Script