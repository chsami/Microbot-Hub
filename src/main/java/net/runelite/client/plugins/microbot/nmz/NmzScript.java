package net.runelite.client.plugins.microbot.nmz;

import lombok.Getter;
import lombok.Setter;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcCache;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.antiban.enums.PlayStyle;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.security.Encryption;
import net.runelite.client.plugins.microbot.util.security.LoginManager;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

public class NmzScript extends Script {

    private NmzConfig config;
    private NmzPlugin plugin;

    public static boolean useOverload = false;

    public static PrayerPotionScript prayerPotionScript;

    public static int maxHealth = Rs2Random.between(2, 8);
    public static int minAbsorption = Rs2Random.between(100, 300);

    private WorldPoint center = new WorldPoint(Rs2Random.between(2270, 2276), Rs2Random.between(4693, 4696), 0);

    @Getter
    @Setter
    private static boolean hasSurge = false;
    private boolean initialized = false;
    private long lastCombatTime = 0;

    @Inject
    private Rs2TileObjectCache tileObjectCache;
    @Inject
    private Rs2NpcCache npcCache;

    public boolean canStartNmz() {
        return Rs2Inventory.count("overload (4)") == config.overloadPotionAmount() ||
                (Rs2Inventory.hasItem("prayer potion") && config.togglePrayerPotions());
    }

    @Inject
    public NmzScript(NmzPlugin plugin, NmzConfig config) {
        this.plugin = plugin;
        this.config = config;
    }


    public boolean run() {
        prayerPotionScript = new PrayerPotionScript();
        Microbot.getSpecialAttackConfigs().setSpecialAttack(true);
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.setActivity(Activity.GENERAL_COMBAT);
        Rs2Antiban.setActivityIntensity(ActivityIntensity.LOW);
        Rs2Antiban.setPlayStyle(PlayStyle.MODERATE);
        Rs2Antiban.activateAntiban();
        Rs2AntibanSettings.moveMouseOffScreen = true;
        Rs2AntibanSettings.simulateMistakes = true;
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.usePlayStyle = true;
        Rs2AntibanSettings.behavioralVariability = true;
        Rs2AntibanSettings.nonLinearIntervals = true;
        Rs2AntibanSettings.actionCooldownChance = 0.00;
        Rs2AntibanSettings.moveMouseOffScreenChance = 1.00;


        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!initialized) {
                    initialized = true;
                    // Skip inventory setup and lobby walk if already inside the NMZ instance
                    boolean isInNmzInstance = Microbot.getClient().getLocalPlayer().getWorldLocation().getY() > 4500;
                    if (!isInNmzInstance) {
                        if (config.inventorySetupon()) {
                            if (config.inventorySetup() != null) {
                                var inventorySetup = new Rs2InventorySetup(config.inventorySetup(), mainScheduledFuture);
                                if (!inventorySetup.doesInventoryMatch() || !inventorySetup.doesEquipmentMatch()) {
                                    Rs2Walker.walkTo(Rs2Bank.getNearestBank().getWorldPoint(), 20);
                                    if (!inventorySetup.loadEquipment() || !inventorySetup.loadInventory()) {
                                        Microbot.log("Failed to load inventory setup");
                                        Microbot.stopPlugin(plugin);
                                        return;
                                    }
                                    Rs2Bank.closeBank();
                                }
                            }
                        }
                        Rs2Walker.walkTo(new WorldPoint(2609, 3114, 0), 5);
                    }
                }
                if (!super.run()) return;
                if (Rs2AntibanSettings.actionCooldownActive) return;
                Rs2Combat.setAutoRetaliate(true);
                boolean isOutsideNmz = isOutside();
                useOverload = Microbot.getClient().getBoostedSkillLevel(Skill.RANGED) == Microbot.getClient().getRealSkillLevel(Skill.RANGED) && config.overloadPotionAmount() > 0;
                if (isOutsideNmz) {
                    Rs2Walker.setTarget(null);
                    handleOutsideNmz();
                } else {
                    handleInsideNmz();
                }
            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        Rs2Antiban.deactivateAntiban();
        Rs2Antiban.resetAntibanSettings();
        initialized = false;
    }

    public boolean isOutside() {
        WorldPoint loc = Microbot.getClientThread().invoke(() -> Microbot.getClient().getLocalPlayer().getWorldLocation());
        return loc != null && loc.distanceTo(new WorldPoint(2602, 3116, 0)) < 20;
    }

    public void handleOutsideNmz() {
        boolean hasStartedDream = Microbot.getVarbitValue(VarbitID.NZONE_PURCHASEDDREAM) > 0;
        if (config.togglePrayerPotions())
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, false);
        if (!hasStartedDream) {
            startNmzDream();
        } else {
            final String overload = "Overload (4)";
            final String absorption = "Absorption (4)";
            storePotions(ObjectID.NZONE_BARREL_3, "overload", config.overloadPotionAmount());
            storePotions(ObjectID.NZONE_BARREL_4, "absorption", config.absorptionPotionAmount());
            handleStore();
            fetchOverloadPotions(ObjectID.NZONE_BARREL_3, overload, config.overloadPotionAmount());
            if (Rs2Inventory.hasItemAmount(overload, config.overloadPotionAmount())) {
                fetchPotions(ObjectID.NZONE_BARREL_4, absorption, config.absorptionPotionAmount());
            }
        }
        if (canStartNmz()) {
            consumeEmptyVial();
        } else {
            sleep(2000);
        }
    }

    public void handleInsideNmz() {
        if (Rs2Player.isInCombat()) {
            lastCombatTime = System.currentTimeMillis();
        }
        Rs2Antiban.takeMicroBreakByChance();
        if (!Rs2Player.isInCombat() && System.currentTimeMillis() - lastCombatTime > 20000) {
            Rs2NpcModel closestNpc = npcCache.query().nearest();
            if (closestNpc != null) {
                if (closestNpc.click("Attack")) {
                    Rs2Antiban.actionCooldown();
                }
            }
        }
        prayerPotionScript.run();
        if (config.togglePrayerPotions())
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, true);
        if (!useOrbs() && config.walkToCenter()) {
            walkToCenter();
        }
        useOverloadPotion();
        manageSelfHarm();
        useAbsorptionPotion();
    }

    private void walkToCenter() {
        if (center.distanceTo(Rs2Player.getWorldLocation()) > 4) {
            Rs2Walker.walkTo(center, 6);
        }
    }

    public void startNmzDream() {
        // Set new center so that it is random for every time joining the dream
        center = new WorldPoint(Rs2Random.between(2270, 2276), Rs2Random.between(4693, 4696), 0);
        Rs2NpcModel dominic = npcCache.query().withName("Dominic Onion").nearest();
        if (dominic != null) dominic.click("Dream");
        sleepUntil(() -> Rs2Widget.hasWidget("Which dream would you like to experience?"));
        Rs2Widget.clickWidget("Previous:");
        sleepUntil(() -> Rs2Widget.hasWidget("Click here to continue"));
        Rs2Widget.clickWidget("Click here to continue");
        sleepUntil(() -> Rs2Widget.hasWidget("Agree to pay"));
        if (Rs2Widget.hasWidget("Agree to pay")) {
            Rs2Keyboard.typeString("1");
            Rs2Keyboard.enter();
        }
    }

    public boolean useOrbs() {
        boolean orbHasSpawned = false;
        if (config.useZapper()) {
            orbHasSpawned = interactWithObject(ObjectID.NZONE_POWERUP_ZAPPER);
        }
        if (config.useReccurentDamage()) {
            orbHasSpawned = interactWithObject(ObjectID.NZONE_POWERUP_DAMAGEMULTIPLIER);
        }

        if (config.usePowerSurge()) {
            orbHasSpawned = interactWithObject(ObjectID.NZONE_POWERUP_SPECIALATTACK);
        }

        return orbHasSpawned;
    }

    public boolean interactWithObject(int objectId) {
        Rs2TileObjectModel obj = tileObjectCache.query().withId(objectId).nearest();
        if (obj != null) {
            sleep(1000, 15000);
            WorldPoint playerLoc = Microbot.getClientThread().invoke(() -> Microbot.getClient().getLocalPlayer().getWorldLocation());
            if (playerLoc != null && playerLoc.distanceTo(obj.getWorldLocation()) >= 15) {
                Rs2Walker.walkFastLocal(obj.getLocalLocation());
                sleepUntil(() -> {
                    WorldPoint loc = Microbot.getClientThread().invoke(() -> Microbot.getClient().getLocalPlayer().getWorldLocation());
                    return loc != null && loc.distanceTo(obj.getWorldLocation()) < 15;
                }, 10000);
            }
            obj.click();
            // Wait for the power-up to despawn to prevent repeated clicks on the same orb
            sleepUntil(() -> tileObjectCache.query().withId(objectId).nearest() == null, 3000);
            return true;
        }
        return false;
    }

    private void fetchOverloadPotions(int objectId, String itemName, int requiredAmount) {
        int currentAmount = Rs2Inventory.count(itemName);

        if (currentAmount == requiredAmount) return;

        int neededAmount = requiredAmount - currentAmount;

        Rs2TileObjectModel obj = tileObjectCache.query().withId(objectId).nearest();
        if (obj == null) return;
        obj.click("Take");
        String widgetText = "How many doses of ";
        sleepUntil(() -> Rs2Widget.hasWidget(widgetText));

        if (Rs2Widget.hasWidget(widgetText)) {
            // Each potion has 4 doses, so request the correct number of doses
            sleep(Rs2Random.between(400, 900));
            Rs2Keyboard.typeString(Integer.toString(neededAmount * 4));
            sleep(Rs2Random.between(200, 500));
            Rs2Keyboard.enter();
            sleepUntil(() -> Rs2Inventory.count(itemName) == requiredAmount);
        }
    }


    public void manageSelfHarm() {
        int currentHP = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
        int currentRangedLevel = Microbot.getClient().getBoostedSkillLevel(Skill.RANGED);
        int realRangedLevel = Microbot.getClient().getRealSkillLevel(Skill.RANGED);
        boolean hasOverloadPotions = config.overloadPotionAmount() > 0;

        if (currentHP >= maxHealth
                && !useOverload
                && (!hasOverloadPotions || currentRangedLevel != realRangedLevel)) {
            maxHealth = 1;

            if (Rs2Inventory.hasItem(ItemID.DS2_ORB)) {
                Rs2Inventory.interact(ItemID.DS2_ORB, "feel");
                Rs2Antiban.actionCooldown();
            } else if (Rs2Inventory.hasItem(ItemID.HUNDRED_DWARF_COOL_ROCKCAKE)) {
                Rs2Inventory.interact(ItemID.HUNDRED_DWARF_COOL_ROCKCAKE, "guzzle");
                Rs2Antiban.actionCooldown();
            }

            if (currentHP == 1) {
                maxHealth = Rs2Random.between(2, 4);
            }
        }

        if (config.randomlyTriggerRapidHeal()) {
            randomlyToggleRapidHeal();
        }
    }

    public void randomlyToggleRapidHeal() {
        if (Rs2Random.between(1, 50) == 2) {
            Rs2Prayer.toggle(Rs2PrayerEnum.RAPID_HEAL, true);
            sleep(300, 600);
            Rs2Prayer.toggle(Rs2PrayerEnum.RAPID_HEAL, false);
        }
    }

    public void useOverloadPotion() {
        if (useOverload && Rs2Inventory.hasItem("overload") && Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS) > 50) {
            Rs2Inventory.interact(x -> x.getName().toLowerCase().contains("overload"), "drink");
            sleep(10000);
        }
    }

    public void useAbsorptionPotion() {
        if (Microbot.getVarbitValue(VarbitID.NZONE_ABSORB_POTION_EFFECTS) < minAbsorption && Rs2Inventory.hasItem("absorption")) {
            for (int i = 0; i < Rs2Random.between(4, 8); i++) {
                Rs2Inventory.interact(x -> x.getName().toLowerCase().contains("absorption"), "drink");
                sleep(600, 1000);
            }
            minAbsorption = Rs2Random.between(100, 300);
        }
    }

    private void storePotions(int objectId, String itemName, int requiredAmount) {
        if (Rs2Inventory.count(itemName) == requiredAmount) return;
        if (Rs2Inventory.get(itemName) == null) return;

        Rs2TileObjectModel obj = tileObjectCache.query().withId(objectId).nearest();
        if (obj == null) return;
        obj.click("Store");
        String storeWidgetText = "Store all your ";
        sleepUntil(() -> Rs2Widget.hasWidget(storeWidgetText));
        if (Rs2Widget.hasWidget(storeWidgetText)) {
            sleep(Rs2Random.between(400, 900));
            Rs2Keyboard.typeString("1");
            sleep(Rs2Random.between(200, 500));
            Rs2Keyboard.enter();
            sleepUntil(() -> !Rs2Inventory.hasItem(objectId));
            Rs2Inventory.dropAll(itemName);
        }
    }

    private void fetchPotions(int objectId, String itemName, int requiredAmount) {
        if (Rs2Inventory.count(itemName) == requiredAmount) return;

        Rs2TileObjectModel obj = tileObjectCache.query().withId(objectId).nearest();
        if (obj == null) return;
        obj.click("Take");
        String widgetText = "How many doses of ";
        sleepUntil(() -> Rs2Widget.hasWidget(widgetText));
        if (Rs2Widget.hasWidget(widgetText)) {
            sleep(Rs2Random.between(400, 900));
            Rs2Keyboard.typeString(Integer.toString(requiredAmount * 4));
            sleep(Rs2Random.between(200, 500));
            Rs2Keyboard.enter();
            sleepUntil(() -> Rs2Inventory.count(itemName) == requiredAmount);
        }
    }

    public void consumeEmptyVial() {
        if (Microbot.getClientThread().runOnClientThreadOptional(() ->
                Rs2Widget.getWidget(129, 6) == null || Rs2Widget.getWidget(129, 6).isHidden())
                .orElse(false)) {
            Rs2TileObjectModel vial = tileObjectCache.query().withId(ObjectID.NZONE_LOBBY_VIAL).nearest();
            if (vial != null) vial.click("drink");
        }
        sleep(2000, 4000);
        Widget widget = Rs2Widget.getWidget(129, 6);
        if (!Microbot.getClientThread().runOnClientThreadOptional(widget::isHidden).orElse(false)) {
            Rs2Widget.clickWidget(widget.getId());
            sleep(300);
            Rs2Widget.clickWidget(widget.getId());
        }
        sleep(2000, 4000);
    }

    public void handleStore() {
        if (canStartNmz()) return;
        int overloadAmt = Microbot.getVarbitValue(VarbitID.NZONE_POTION_3);
        int absorptionAmt = Microbot.getVarbitValue(VarbitID.NZONE_POTION_4);

        // Varbits are in doses; config is in 4-dose potions
        int overloadDosesNeeded = Math.max(0, config.overloadPotionAmount() * 4 - overloadAmt);
        int absorptionDosesNeeded = Math.max(0, config.absorptionPotionAmount() * 4 - absorptionAmt);

        if (overloadDosesNeeded == 0 && absorptionDosesNeeded == 0) return;

        // Each shop purchase gives one 4-dose potion (ceiling division)
        int overloadToBuy = (overloadDosesNeeded + 3) / 4;
        int absorptionToBuy = (absorptionDosesNeeded + 3) / 4;

        // NMZ reward shop costs: Overload 1,500 pts / Absorption 1,000 pts per 4-dose potion
        int totalCost = overloadToBuy * 1500 + absorptionToBuy * 1000;
        int nmzPoints = Microbot.getVarbitPlayerValue(VarPlayerID.NZONE_REWARDPOINTS);

        if (nmzPoints < totalCost) {
            Microbot.showMessage("BOT SHUTDOWN: Not enough points to buy potions (have " + nmzPoints + ", need " + totalCost + ")");
            Microbot.stopPlugin(plugin);
            return;
        }

        Rs2TileObjectModel chest = tileObjectCache.query().withId(ObjectID.NZONE_LOBBY_CHEST).nearest();
        if (chest == null) return;
        chest.click();
        sleepUntil(() -> Rs2Widget.isWidgetVisible(13500418) || Rs2Bank.isBankPinWidgetVisible(), 10000);
        if (Rs2Bank.isBankPinWidgetVisible()) {
            try {
                Rs2Bank.handleBankPin(Encryption.decrypt(LoginManager.getActiveProfile().getBankPin()));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            sleepUntil(() -> Rs2Widget.isWidgetVisible(13500418), 10000);
        }

        Widget benefitsBtn = Rs2Widget.getWidget(13500418);
        if (benefitsBtn == null) return;
        if (benefitsBtn.getSpriteId() != 813) {
            Rs2Widget.clickWidgetFast(benefitsBtn, 4, 4);
            sleepUntil(() -> {
                Widget btn = Rs2Widget.getWidget(13500418);
                return btn != null && btn.getSpriteId() == 813;
            }, 3000);
        }

        for (int i = 0; i < overloadToBuy; i++) {
            Widget nmzRewardShop = Rs2Widget.getWidget(206, 6);
            if (nmzRewardShop == null) break;
            Rs2Widget.clickWidgetFast(nmzRewardShop.getChild(6), 6, 4);
            sleep(600, 1000);
        }

        for (int i = 0; i < absorptionToBuy; i++) {
            Widget nmzRewardShop = Rs2Widget.getWidget(206, 6);
            if (nmzRewardShop == null) break;
            Rs2Widget.clickWidgetFast(nmzRewardShop.getChild(9), 9, 4);
            sleep(600, 1000);
        }
    }

}
