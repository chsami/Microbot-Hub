package net.runelite.client.plugins.microbot.autogauntletprayer;

import com.google.inject.Provides;
import lombok.Getter;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.util.Set;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.HashSet;

import static java.lang.Thread.sleep;
import static net.runelite.client.plugins.microbot.Microbot.log;

@PluginDescriptor(
        name = PluginDescriptor.LiftedMango + "Auto Gauntlet Prayer",
        description = "Auto Gauntlet Prayer plugin",
        tags = {"liftedmango", "Gauntlet", "pvm", "prayer", "money making", "auto", "boss", "hunllef"},
        version = AutoGauntletPrayerPlugin.version,
        minClientVersion = "2.0.30",
        cardUrl = "",
        iconUrl = "",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)

public class AutoGauntletPrayerPlugin extends Plugin {
    public static final String version = "1.1";
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private AutoGauntletPrayerOverlay overlay;
    @Inject
    private AutoGauntletPrayerConfig config;
    @Provides
    AutoGauntletPrayerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoGauntletPrayerConfig.class);
    }

    @Getter
    private static int timer1Count = 0;
    @Getter
    private static long timer1Time = 0;

    public static long agpPrayTime;
    private final int PADDLEFISH_HEAL_VALUE = 20;
    private final int RANGE_PROJECTILE_MINIBOSS = 1705;
    private final int MAGE_PROJECTILE_MINIBOSS = 1701;
    private final int RANGE_PROJECTILE = 1711;
    private final int MAGE_PROJECTILE = 1707;
    private final int CG_RANGE_PROJECTILE = 1712;
    private final int CG_MAGE_PROJECTILE = 1708;
    private final int DEACTIVATE_MAGE_PROJECTILE = 1713;
    private final int CG_DEACTIVATE_MAGE_PROJECTILE = 1714;
    private final int MAGE_ANIMATION = 8754;
    private final int RANGE_ANIMATION = 8755;
    Rs2NpcModel hunllef = null;
    Rs2NpcModel Tornado = null;
    private final int CG_TORNADO = 9039;
    private int projectileCount = 0;
    private boolean AttackNeeded = false;
    private Rs2PrayerEnum nextPrayer = Rs2PrayerEnum.PROTECT_RANGE;

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


    @Override
    protected void startUp() throws Exception {
        log("Auto gauntlet prayer plugin started!");
        overlayManager.add(overlay);

        // Always default to Protect from Range when the plugin starts
        nextPrayer = Rs2PrayerEnum.PROTECT_RANGE;

        // Ensure all other prayers are off, then enable Protect Range
        Rs2Prayer.disableAllPrayers();
        SendPrayerToggle(Rs2PrayerEnum.PROTECT_RANGE, true);

        log("Defaulted to Protect from Range on plugin start.");
    }

    @Override
    protected void shutDown() throws Exception {
        log("Gauntlet plugin stopped!");
        Rs2Prayer.disableAllPrayers();
        overlayManager.remove(overlay);
        super.shutDown();
    }

    @Subscribe
    public void onGameTick(GameTick event) {
            //Microbot.log("Next prayer: " + nextPrayer);
            long TickStart = System.currentTimeMillis();

            if (nextPrayer != null && !Rs2Prayer.isPrayerActive(nextPrayer)) {
                SendPrayerToggle(nextPrayer, true);
            }

            Tornado = Rs2Npc.getNpc(CG_TORNADO);
            hunllef = Rs2Npc.getNpcs()
                    .filter(npc -> HUNLLEF_IDS.contains(npc.getId()))
                    .findFirst()
                    .orElse(null);

            if (hunllef == null) {
                nextPrayer = null;
                return;
            }

            HeadIcon headIcon = hunllef.getHeadIcon();

            // Protection Prayers happen above
            /// --- PRAYER management part 2
            checkAndToggleAttackPrayers();
            checkSteelSkin();

            Rs2Tab.switchTo(InterfaceTab.INVENTORY);

            /// --- Start of INVENTORY actions
            switch (headIcon) {
                case RANGED:
                    handleRangedHeadIcon();
                    break;
                case MAGIC:
                    handleMagicHeadIcon();
                    break;
                case MELEE:
                    handleMeleeHeadIcon();
                    break;
                default:
                    break;
            }

            checkFood();
            checkPrayerPotions();
            if (config.autoattack()) {checkAttack();}

            long TickEnd = System.currentTimeMillis();
            long TickDuration = TickEnd - TickStart;
            if (config.debugtoggle()) Microbot.log("Tick runtime: " + TickDuration);
        }; // --- End of Gametick


    @Subscribe
    public void onProjectileMoved(ProjectileMoved event) {
        int projectileId = event.getProjectile().getId();

        switch (projectileId) {
            case MAGE_PROJECTILE:
            case CG_MAGE_PROJECTILE:
            case MAGE_PROJECTILE_MINIBOSS:
                //SendPrayerToggle(Rs2PrayerEnum.PROTECT_MAGIC, true);
                break;

            case RANGE_PROJECTILE:
            case CG_RANGE_PROJECTILE:
            case RANGE_PROJECTILE_MINIBOSS:
                //SendPrayerToggle(Rs2PrayerEnum.PROTECT_RANGE, true);
                break;

            case CG_DEACTIVATE_MAGE_PROJECTILE:
            case DEACTIVATE_MAGE_PROJECTILE:
                projectileCount++;
                if (projectileCount >= 56) {
                    SendPrayerToggleDelay(nextPrayer, true, 300);
                    projectileCount = 0; // reset after the last hit
                }
                break;
            default:
                break;
        }

    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged event) {
        if (!(event.getActor() instanceof NPC)) return;

        NPC npc = (NPC) event.getActor();
        if (!HUNLLEF_IDS.contains(npc.getId())) return;

        int animationID = npc.getAnimation();
        switch (animationID) {
            case MAGE_ANIMATION:
                nextPrayer = Rs2PrayerEnum.PROTECT_MAGIC;
                break;
            case RANGE_ANIMATION:
                nextPrayer = Rs2PrayerEnum.PROTECT_RANGE;
                break;
            default:
                break;
        }
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

    public synchronized Rs2PrayerEnum getNextPrayer() {
        return nextPrayer;
    }

    private void handleRangedHeadIcon() {
        if (hasStaffInInventory() && !isHalberdEquipped()) { equipStaff(); }
        else if (hasHalberdInInventory() && !isStaffEquipped()) { equipHalberd(); }
    }

    private void handleMagicHeadIcon() {
        if (hasBowInInventory() && !isHalberdEquipped()) { equipBow(); }
        else if (hasHalberdInInventory() && !isBowEquipped()) { equipHalberd(); }
    }

    private void handleMeleeHeadIcon() {
        if (hasStaffInInventory() && !isBowEquipped()) { equipStaff(); }
        else if (hasBowInInventory() && !isStaffEquipped()) { equipBow(); }
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
                if (!Rs2Player.isMoving()) {AttackNeeded = true; }
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

    private void equipBow() { equipBestAvailable(BOW_IDS); }
    private void equipStaff() { equipBestAvailable(STAFF_IDS); }
    private void equipHalberd() { equipBestAvailable(HALBERD_IDS); }

    ///  --------------------------------------------------------------------------------------------------------------
    ///  --- Prayers --------------------------------------------------------------------------------------------------
    /// ---------------------------------------------------------------------------------------------------------------

    private void checkAndToggleAttackPrayers() {
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
        if (!Rs2Prayer.isPrayerActive(Rs2PrayerEnum.STEEL_SKIN)) {
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
        if (!Rs2Player.isMoving()) {AttackNeeded = true; }
    }

    private void checkPrayerPotions() {
        int currentPrayer = Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER);
        if (currentPrayer < config.ppotvalue()) {Rs2Inventory.interact("Egniol potion", "Drink");}
    }

    private void checkAttack(){
        if (hunllef == null) return;
        if (Rs2Player.isMoving()) return;
        if (AttackNeeded) {
                Rs2Npc.interact(hunllef, "attack");
                AttackNeeded = false;
        }
    }

    private void checkAttackDelay(int delay){
        if (hunllef == null) return;
        if (Rs2Player.isMoving()) return;
        if (AttackNeeded) {
            Microbot.getClientThread().runOnSeperateThread(() -> {
                try {Thread.sleep(delay);} catch (InterruptedException ignored) {}
                Rs2Npc.interact(hunllef, "attack");
                AttackNeeded = false;
                return true;
            });
        }
    }

    private void SendPrayerToggle(Rs2PrayerEnum prayer, boolean enable) {
        if (prayer == null) return;
        boolean currentlyActive = Rs2Prayer.isPrayerActive(prayer);
        if (currentlyActive == enable) return;
        Rs2Prayer.toggle(prayer, enable, true);
    }

    private void SendPrayerToggleDelay(Rs2PrayerEnum prayer, boolean enable, int delay) {
        if (prayer == null) return;
        boolean currentlyActive = Rs2Prayer.isPrayerActive(prayer);
        if (currentlyActive == enable) return;

        Microbot.getClientThread().runOnSeperateThread(() -> {

            try { Thread.sleep(delay); } catch (InterruptedException ignored) {}
            try { Rs2Prayer.toggle(prayer, enable, true);
            } catch (Exception e) {
                Microbot.log("safeTogglePrayer error: " + e.getMessage());
            }
            Rs2Tab.switchTo(InterfaceTab.INVENTORY);
            return true;
        });

    }
}
