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
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
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

    private final int CD_NPC = 450;
    private final int CD_PRAYOFFENCE = 100;
    private final int CD_PRAYPROTECT = 100;
    private final int CD_EAT = 600;
    private final int CD_EATCHAIN = 1400;
    private final int CD_DRINK = 600;
    private final int CD_WEAPON = 600;
    private final int CD_RECENTLY = 25;
    private final int CD_ATTACK = 750;
    private final int CD_STEEL = 300;
    private final int CD_TICK = 600;
    private final int BUFFER_MAGICBLAST = 400;
    private static final int LOOPS_PER_TICK = 5;

    private final int STAT_HP = -1;
    private final int STAT_PRAYER = -1;
    private int LoopCount = 0;

    private boolean HP_WENT_UP = false;
    private boolean PRAY_WENT_UP = false;
    private boolean shutdown_requested = false;

    private long startTime = -1;
    private long now = -1;
    private long TIME_NPC = 150;
    private long TIME_PRAYOFFENCE = -1;
    private long TIME_PRAYPROTECT = -1;
    private long TIME_EAT_ATTEMPTED = -1;
    private long TIME_DRINK = -1;
    private long TIME_WEAPON = -1;
    private long TIME_ATTACK = -1;
    private long TIME_STEEL = -1;
    private long MAGIC_BLAST_END = -1;

    private final AtomicBoolean tickHappened = new AtomicBoolean(false);

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
        TIME_EAT_ATTEMPTED = now;
        TIME_DRINK = now;
        TIME_WEAPON = now;
        MAGIC_BLAST_END = now;
        sleep(300);

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if (shutdown_requested) return;

                now = System.currentTimeMillis();

                switch (gh_state) {

                    case idle:
                        //gh_state = State.fighting;
                        break;
                    case fighting:
                        /// NOTE: Loop takes 47-60ms
                        if (!tickHappened.get()) break;
                        LoopCount++;
                        if (LoopCount > LOOPS_PER_TICK) {
                            LoopCount = 0;
                            tickHappened.set(false);
                            checkProtectPrayers();
                            break;
                        }

                        if (now - TIME_NPC > CD_NPC) checkNPC();
                        checkVitalsStart();

                        //Fast Section
                        checkProtectPrayers();
                        //if (now - TIME_PRAYPROTECT > CD_PRAYPROTECT) checkProtectPrayers();
                        if (now - MAGIC_BLAST_END < BUFFER_MAGICBLAST) checkProtectPrayers(); //turbo check after magic blast happens
                        if (now - TIME_PRAYOFFENCE > CD_PRAYOFFENCE) checkAttackPrayers();
                        if (now - TIME_STEEL > CD_STEEL) checkSteelSkin();
                        if ((now - TIME_PRAYPROTECT < CD_RECENTLY) || (now - TIME_PRAYOFFENCE < CD_RECENTLY) || (now - TIME_STEEL < CD_RECENTLY)) break; //Prayers changed, return simulates small sleep

                        //Slow Section
                        if (now - TIME_EAT_ATTEMPTED > CD_EAT) checkFood();
                        if (now - TIME_EAT_ATTEMPTED < CD_RECENTLY) {checkPrayerPotions(); break;} //Eating action occurred, return simulates sleep. Combo drink attempted
                        if (now - TIME_DRINK > CD_DRINK) checkPrayerPotions(); //Non-Blocking

                        if (now - TIME_WEAPON > CD_WEAPON) checkWeapon();
                        checkAttack();
                        break;

                } // ----- End of States -----

                long totalTime = System.currentTimeMillis() - now;
                logVerbose("Loop took " + totalTime + "ms" + " and tickstart is " + tickHappened.get());

            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 57, TimeUnit.MILLISECONDS);
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

        if (hunllef == null && gh_state == State.fighting) {
            gh_state = State.idle;
            Rs2Prayer.disableAllPrayers();
            nextPrayer = null;
            return;
        } else {gh_state = State.fighting;}

        BossHeadIcon = hunllef.getHeadIcon();
    }


    //------------------------------------------------------------------------------------------------------------------
    //----- Events

    // Note: The below method is triggered by a subscribe on the plugin file to (GameTick event) so it happens every 600ms
    public void event_gametick(){
        tickHappened.set(true);
        if (LoopCount > LOOPS_PER_TICK * 2) { tickHappened.set(true); } //failsafe
    }


    //The below three methods are triggered by subscribed events
    public void anim_magic_seen(){if (nextPrayer == null) nextPrayer = Rs2PrayerEnum.PROTECT_MAGIC;}
    public void anim_range_seen(){if (nextPrayer == null) nextPrayer = Rs2PrayerEnum.PROTECT_RANGE;}
    public void proj_mageblast_end(){MAGIC_BLAST_END = System.currentTimeMillis();}
    public void proj_magic_seen(){nextPrayer = Rs2PrayerEnum.PROTECT_MAGIC;}
    public void proj_range_seen(){nextPrayer = Rs2PrayerEnum.PROTECT_RANGE;}

    private void checkProtectPrayers(){
        if (nextPrayer == null){logVerbose(nextPrayer + " is null on check prayers");}
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

    //------------------------------------------------------------------------------------------------------------------
    //---Handlers

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
        else if (!isBowEquipped() && !isStaffEquipped() && hasBowInInventory()) {equipBow();}
    }

    private void handleRangedHeadIcon() {
        if (!isStaffEquipped() && hasStaffInInventory()) {equipStaff();}
        else if (!isHalberdEquipped() && !isStaffEquipped() && hasHalberdInInventory()) {equipHalberd();}
    }

    private void handleMagicHeadIcon() {
        if (!isBowEquipped() && hasBowInInventory()) {equipBow();}
        else if (!isHalberdEquipped() && !isBowEquipped() && hasHalberdInInventory()) {equipHalberd();}
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
                logVerbose("weapon change attempted");
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
            TIME_STEEL = now;
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

    //------------------------------------------------------------------------------------------------------------------
    //---Stats

    private void checkVitalsStart(){
        int HP = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
        if (HP > STAT_HP) {HP_WENT_UP = true;}
        int PRAY = Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER);
        if (PRAY > STAT_PRAYER) {PRAY_WENT_UP = true;}

    }

    private void checkFood() {
        int currentHp = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
        int maxHp = Microbot.getClient().getRealSkillLevel(Skill.HITPOINTS);
        int missingHp = maxHp - currentHp;
        if (currentHp <= 0) return;
        if (missingHp < (PADDLEFISH_HEAL_VALUE - config.eatoverhealvalue())) return;
        if (config.eatFoodChain()) {EatFood();} //Continue eating once started
        if (currentHp < config.lowhpeatvalue()) {EatFood();}
        if (config.TornadoCheck() && (Tornado == null)) {return;}
        if (config.eatFoodMoving() && Rs2Player.isMoving()) {EatFood();}
    }

    private void EatFood() {
        if (!config.enablefood()) return;
        if (now - TIME_EAT_ATTEMPTED < CD_RECENTLY) return;
        Rs2Inventory.interact("Paddlefish", "Eat");
        logVerbose("Eat attempted");
        TIME_EAT_ATTEMPTED = now;
        if (!Rs2Player.isMoving()) {attackNeeded.set(true); }
    }

    private void checkPrayerPotions() {
        if (!config.enabledrink()) return;
        int currentPrayer = Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER);
        if (currentPrayer < config.ppotvalue()) {Rs2Inventory.interact("Egniol potion", "Drink");
                                                    TIME_DRINK = now;}
    }

    private void checkAttack(){
        if (!config.autoattack()) return;
        if (hunllef == null) return;
        if (Rs2Player.isMoving()) return;
        if (attackNeeded.compareAndSet(true, false)) {
            if (now - TIME_EAT_ATTEMPTED > (CD_EAT * 3)) {
                logVerbose("Attempting attack");
                Rs2Npc.interact(hunllef, "attack");
                TIME_ATTACK = now;
            }
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

    public void startup2(){
        shutdown_requested = false;
    }

    @Override
    public void shutdown() {
        shutdown_requested = true;
        super.shutdown();
        if (mainScheduledFuture != null) {
            mainScheduledFuture.cancel(true);
        }
        Rs2Prayer.disableAllPrayers();
        Rs2Walker.setTarget(null);
    }



} // End of Script