package net.runelite.client.plugins.microbot.autogauntletprayer;

import com.google.inject.Provides;
import lombok.Getter;
import net.runelite.api.HeadIcon;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
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
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.util.Set;

import static java.lang.Thread.sleep;
import static net.runelite.client.plugins.microbot.Microbot.log;

@PluginDescriptor(
        name = PluginDescriptor.LiftedMango + "Auto Gauntlet Prayer",
        description = "Auto Gauntlet Prayer plugin",
        tags = {"liftedmango", "Gauntlet", "pvm", "prayer", "money making", "auto", "boss"},
        version = AutoGauntletPrayerPlugin.version,
        minClientVersion = "2.0.13",
        cardUrl = "",
        iconUrl = "",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)

public class AutoGauntletPrayerPlugin extends Plugin {
    public static final String version = "1.0.8";
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
    private Rs2PrayerEnum nextPrayer = Rs2PrayerEnum.PROTECT_RANGE;

    private static final Set<Integer> HUNLLEF_IDS = Set.of(
            9035, 9036, 9037, 9038, // Corrupted Hunllef variants
            9021, 9022, 9023, 9024  // Crystalline Hunllef variants
    );

    private static final Set<Integer> DANGEROUS_TILES = Set.of(
            36047, 36048, // Corrupted tiles (Ground object)
            36150, 36151 // Gauntlet tiles (Ground object)
    );

    long lastPrayerSwitch;

    @Override
    protected void startUp() throws Exception {
        log("Auto gauntlet prayer plugin started!");
        overlayManager.add(overlay);    }

    @Override
    protected void shutDown() throws Exception {
        log("Gauntlet plugin stopped!");
        Rs2Prayer.disableAllPrayers();
        super.shutDown();
        overlayManager.remove(overlay);    }

    @Subscribe
    public void onGameTick(GameTick event) {
        Microbot.log("Next prayer: " + nextPrayer);

        if (nextPrayer != null && !Rs2Prayer.isPrayerActive(nextPrayer)) {
            Microbot.getClientThread().runOnSeperateThread(() -> {
            Rs2Prayer.toggle(nextPrayer, true, true);
            sleep(5);
            Rs2Tab.switchTo(InterfaceTab.INVENTORY);
            return true;
        });
        }

        Rs2NpcModel hunllef = Rs2Npc.getNpcs()
                .filter(npc -> HUNLLEF_IDS.contains(npc.getId()))
                .findFirst()
                .orElse(null);

        if (hunllef == null) {
            nextPrayer = null;
            return;
        }

        HeadIcon headIcon = hunllef.getHeadIcon();

        switch (headIcon) {
            case RANGED:
                if (!config.DisableWeapon()) handleRangedHeadIcon();
                break;
            case MAGIC:
                if (!config.DisableWeapon()) handleMagicHeadIcon();
                break;
            case MELEE:
                if (!config.DisableWeapon()) handleMeleeHeadIcon();
                break;
            default:
                break;
        }
        checkAndTogglePrayers();
        checkPrayerPotions();
    }

    @Subscribe
    public void onProjectileMoved(ProjectileMoved event) {
        /*
        int projectileId = event.getProjectile().getId();

        switch (projectileId) {
            case MAGE_PROJECTILE:
            case CG_MAGE_PROJECTILE:
            case MAGE_PROJECTILE_MINIBOSS:
                Microbot.getClientThread().runOnSeperateThread(() -> {
                    Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC, true, true);
                    sleep(15);
                    Rs2Tab.switchTo(InterfaceTab.INVENTORY);
                    return true;
                });
                break;
            case RANGE_PROJECTILE:
            case CG_RANGE_PROJECTILE:
            case RANGE_PROJECTILE_MINIBOSS:
                Microbot.getClientThread().runOnSeperateThread(() -> {
                Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_RANGE, true, true);
                sleep(15);
                Rs2Tab.switchTo(InterfaceTab.INVENTORY);
                return true;
        });
        break;
            default:
                break;
        }

         */

        AutoGauntletPrayerScript.timer1();
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged event) {
        if (!(event.getActor() instanceof NPC)) return;

        if (config.DisablePrayer()) return;

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

        Microbot.getClientThread().runOnSeperateThread(() -> {
            sleep(25);
            try {
                Rs2Prayer.toggle(nextPrayer, true, true);

            } catch (Exception e) {
                Microbot.log("Prayer error");
            }
            return true;

        });
    }

    // ---- GAUNTLET WEAPON PRIORITY ARRAYS ----
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

    public synchronized Rs2PrayerEnum getNextPrayer()
    {
        return nextPrayer;
    }

    private void handleRangedHeadIcon()
    {
        if (hasStaffInInventory() && !isHalberdEquipped())
        {
            equipStaff();
        }
        else if (hasHalberdInInventory() && !isStaffEquipped())
        {
            equipHalberd();
        }
    }

    private void handleMagicHeadIcon()
    {
        if (hasBowInInventory() && !isHalberdEquipped())
        {
            equipBow();
        }
        else if (hasHalberdInInventory() && !isBowEquipped())
        {
            equipHalberd();
        }
    }

    private void handleMeleeHeadIcon()
    {
        if (hasStaffInInventory() && !isBowEquipped())
        {
            equipStaff();
        }
        else if (hasBowInInventory() && !isStaffEquipped())
        {
            equipBow();
        }
    }

// ---- GENERIC HELPERS ----

    private boolean hasWeaponInInventory(int[] ids) {
        for (int id : ids) {
            if (Rs2Inventory.contains(id)) {
                return true;
            }
        }
        return false;
    }

    private boolean isWeaponEquipped(int[] ids) {
        for (int id : ids) {
            if (Rs2Equipment.isWearing(id)) {
                return true;
            }
        }
        return false;
    }

    private void equipBestAvailable(int[] ids) {
        for (int id : ids) {
            if (Rs2Inventory.contains(id)) {
                Rs2Inventory.equip(id);
                break;
            }
        }
    }

// ---- SPECIFIC HELPERS ----

    private boolean hasBowInInventory() {
        return hasWeaponInInventory(BOW_IDS);
    }

    private boolean hasStaffInInventory() {
        return hasWeaponInInventory(STAFF_IDS);
    }

    private boolean hasHalberdInInventory() {
        return hasWeaponInInventory(HALBERD_IDS);
    }

    private boolean isBowEquipped() {
        return isWeaponEquipped(BOW_IDS);
    }

    private boolean isStaffEquipped() {
        return isWeaponEquipped(STAFF_IDS);
    }

    private boolean isHalberdEquipped() {
        return isWeaponEquipped(HALBERD_IDS);
    }

    private void equipBow() {
        equipBestAvailable(BOW_IDS);
    }

    private void equipStaff() {
        equipBestAvailable(STAFF_IDS);
    }

    private void equipHalberd() {
        equipBestAvailable(HALBERD_IDS);
    }

    private void checkAndTogglePrayers() {
        if (isBowEquipped() && !Rs2Prayer.isPrayerActive(Rs2PrayerEnum.RIGOUR)) {
            toggleRigourPrayer();
        }
        if (isStaffEquipped() && !Rs2Prayer.isPrayerActive(Rs2PrayerEnum.AUGURY)) {
            toggleAuguryPrayer();
        }
        if (isHalberdEquipped() && !Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PIETY)) {
            togglePietyPrayer();
        }
    }


    private void toggleRigourPrayer() {
        if (!config.MysticMight()) {
            Microbot.getClientThread().runOnSeperateThread(() -> {
                Rs2Prayer.toggle(Rs2PrayerEnum.RIGOUR, true, true);
                sleep(5);
                Rs2Tab.switchTo(InterfaceTab.INVENTORY);
                return true;
            });
        } else {
            if (!Rs2Prayer.isPrayerActive(Rs2PrayerEnum.STEEL_SKIN)) {
                Microbot.getClientThread().runOnSeperateThread(() -> {
                Rs2Prayer.toggle(Rs2PrayerEnum.STEEL_SKIN, true, true);
                sleep(5);
                Rs2Tab.switchTo(InterfaceTab.INVENTORY);
                return true;
            });
            }
            Microbot.getClientThread().runOnSeperateThread(() -> {
            Rs2Prayer.toggle(Rs2PrayerEnum.EAGLE_EYE, true, true);
            sleep(5);
            Rs2Tab.switchTo(InterfaceTab.INVENTORY);
            return true;
        });
        }
    }

    private void toggleAuguryPrayer() {
        if (!config.MysticMight()) {
            Microbot.getClientThread().runOnSeperateThread(() -> {
            Rs2Prayer.toggle(Rs2PrayerEnum.AUGURY, true, true);
            sleep(5);
            Rs2Tab.switchTo(InterfaceTab.INVENTORY);
            return true;
        });
        } else {
            if (!Rs2Prayer.isPrayerActive(Rs2PrayerEnum.STEEL_SKIN)) {
                Microbot.getClientThread().runOnSeperateThread(() -> {
                Rs2Prayer.toggle(Rs2PrayerEnum.STEEL_SKIN, true,true);
                sleep(5);
                Rs2Tab.switchTo(InterfaceTab.INVENTORY);
                return true;
            });

            }
            Microbot.getClientThread().runOnSeperateThread(() -> {
            Rs2Prayer.toggle(Rs2PrayerEnum.MYSTIC_MIGHT, true, true);
            sleep(5);
            Rs2Tab.switchTo(InterfaceTab.INVENTORY);
            return true;
        });
        }
    }

    private void togglePietyPrayer() {
        if (!config.MysticMight()) {
            Rs2Prayer.toggle(Rs2PrayerEnum.PIETY, true);
        } else {
            if (!Rs2Prayer.isPrayerActive(Rs2PrayerEnum.STEEL_SKIN)) {
                Rs2Prayer.toggle(Rs2PrayerEnum.STEEL_SKIN, true);
            }
            Rs2Prayer.toggle(Rs2PrayerEnum.ULTIMATE_STRENGTH, true);
            Rs2Prayer.toggle(Rs2PrayerEnum.INCREDIBLE_REFLEXES, true);
        }
    }

    private void checkPrayerPotions() {
        int currentPrayer = Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER);
        if (currentPrayer <30) {Rs2Inventory.interact("Egniol potion", "Drink");}
    }
}