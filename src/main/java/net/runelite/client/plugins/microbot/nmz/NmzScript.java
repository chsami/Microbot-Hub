package net.runelite.client.plugins.microbot.nmz;

import lombok.Getter;
import lombok.Setter;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.InterfaceID;
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
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import javax.inject.Inject;
import java.awt.event.KeyEvent;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class NmzScript extends Script {

    private NmzConfig config;
    private NmzPlugin plugin;

    public static boolean useOverload = false;

    public static int maxHealth = Rs2Random.between(2, 8);
    public static int minAbsorption = Rs2Random.between(100, 300);

    private WorldPoint center = new WorldPoint(Rs2Random.between(2270, 2276), Rs2Random.between(4693, 4696), 0);

    @Getter
    @Setter
    private static boolean hasSurge = false;
    private boolean initialized = false;
    private long lastCombatTime = 0;
    private boolean wasInside;
    private int lobbyFailures;
    private boolean halted;

    @Inject
    private Rs2TileObjectCache tileObjectCache;
    @Inject
    private Rs2NpcCache npcCache;

    public boolean canStartNmz() {
        return Rs2Inventory.count("overload (4)") >= config.overloadPotionAmount()
                && Rs2Inventory.count("absorption (4)") >= config.absorptionPotionAmount()
                && (!config.togglePrayerPotions() || Rs2Inventory.hasItem("prayer potion"));
    }

    @Inject
    public NmzScript(NmzPlugin plugin, NmzConfig config) {
        this.plugin = plugin;
        this.config = config;
    }


    public boolean run() {
        initialized = wasInside = halted = false;
        lobbyFailures = 0;
        lastCombatTime = 0;
        useOverload = hasSurge = false;
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
                if (halted || !Microbot.isLoggedIn()) return;
                if (!initialized) {
                    WorldPoint playerLocation = Rs2Player.getWorldLocation();
                    if (playerLocation == null) return;
                    initialized = true;
                    // Skip inventory setup and lobby walk if already inside the NMZ instance
                    boolean isInNmzInstance = playerLocation.getY() > 4500;
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
                if (!handleRoundTransition(isOutsideNmz)) return;
                useOverload = shouldUseOverload(Microbot.getClient().getBoostedSkillLevel(Skill.RANGED), Microbot.getClient().getRealSkillLevel(Skill.RANGED), config.overloadPotionAmount(), Rs2Inventory.hasItem("overload"));
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
        return isNearLobby(Rs2Player.getWorldLocation());
    }

    static boolean isNearLobby(WorldPoint loc) {
        return loc != null && loc.distanceTo(new WorldPoint(2602, 3116, 0)) < 20;
    }

    boolean handleRoundTransition(boolean outside) {
        if (halted) return false;
        if (!outside) wasInside = true;
        else if (wasInside) {
            wasInside = false;
            hasSurge = useOverload = false;
            lastCombatTime = 0;
            lobbyFailures = 0;
            if (!config.repeatRounds()) {
                stopWithReason("NMZ round complete; Repeat rounds is off");
                return false;
            }
            Microbot.log("NMZ round complete; restocking for the next dream");
        }
        return true;
    }

    protected void stopWithReason(String reason) {
        halted = true;
        Microbot.status = reason;
        Microbot.log(reason);
        Microbot.stopPlugin(plugin);
    }

    private void lobbyResult(boolean success, String operation) {
        if (halted) return;
        if (success) { lobbyFailures = 0; return; }
        Microbot.status = "NMZ: retrying " + operation;
        if (++lobbyFailures >= 3) stopWithReason("NMZ stopped: no progress while " + operation + ". Check supplies, coffer and dialogue.");
    }

    public void handleOutsideNmz() {
        if (halted) return;
        if (config.overloadPotionAmount() < 0 || config.absorptionPotionAmount() < 0
                || config.overloadPotionAmount() > 28 || config.absorptionPotionAmount() > 28
                || config.overloadPotionAmount() + config.absorptionPotionAmount() > 28) {
            stopWithReason("NMZ stopped: potion amounts must fit in 28 inventory slots");
            return;
        }
        boolean hasStartedDream = Microbot.getVarbitValue(VarbitID.NZONE_PURCHASEDDREAM) > 0;
        if (config.togglePrayerPotions())
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, false);
        if (!hasStartedDream) {
            startNmzDream();
            lobbyResult(Microbot.getVarbitValue(VarbitID.NZONE_PURCHASEDDREAM) > 0, "purchasing the previous dream");
            return;
        }
        if (Rs2Inventory.hasItem(ItemID.VIAL_EMPTY)) {
            Rs2Inventory.dropAll(ItemID.VIAL_EMPTY);
            lobbyResult(sleepUntil(() -> !Rs2Inventory.hasItem(ItemID.VIAL_EMPTY), 3000), "clearing empty vials");
            return;
        }
        if (needsStoring("overload", config.overloadPotionAmount())) {
            lobbyResult(storePotions(ObjectID.NZONE_BARREL_3, "overload"), "storing leftover overloads");
            return;
        }
        if (needsStoring("absorption", config.absorptionPotionAmount())) {
            lobbyResult(storePotions(ObjectID.NZONE_BARREL_4, "absorption"), "storing leftover absorptions");
            return;
        }
        int missingSlots = Math.max(0, config.overloadPotionAmount() - Rs2Inventory.count("overload (4)"))
                + Math.max(0, config.absorptionPotionAmount() - Rs2Inventory.count("absorption (4)"));
        if (missingSlots > Rs2Inventory.emptySlotCount()) {
            stopWithReason("NMZ stopped: not enough inventory space for configured potions");
            return;
        }
        if (dosesToBuy(VarbitID.NZONE_POTION_3, "overload", config.overloadPotionAmount()) > 0
                || dosesToBuy(VarbitID.NZONE_POTION_4, "absorption", config.absorptionPotionAmount()) > 0) {
            lobbyResult(handleStore(), "buying potion doses");
            return;
        }
        if (Rs2Inventory.count("overload (4)") < config.overloadPotionAmount()) {
            lobbyResult(fetchPotions(ObjectID.NZONE_BARREL_3, "Overload (4)", config.overloadPotionAmount()), "withdrawing overloads");
            return;
        }
        if (Rs2Inventory.count("absorption (4)") < config.absorptionPotionAmount()) {
            lobbyResult(fetchPotions(ObjectID.NZONE_BARREL_4, "Absorption (4)", config.absorptionPotionAmount()), "withdrawing absorptions");
            return;
        }
        if (!canStartNmz()) {
            stopWithReason("NMZ stopped: Auto prayer potion requires carried prayer potions");
            return;
        }
        lobbyResult(consumeEmptyVial(), "entering the dream");
    }

    private boolean needsStoring(String name, int desired) {
        int all = Rs2Inventory.count(name), full = Rs2Inventory.count(name + " (4)");
        return all > full || full > desired;
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
        if (isAwayFromCenter(center, Rs2Player.getWorldLocation())) {
            Rs2Walker.walkTo(center, 6);
        }
    }

    static boolean isAwayFromCenter(WorldPoint center, WorldPoint loc) {
        return loc != null && center.distanceTo(loc) > 4;
    }

    public void startNmzDream() {
        // Set new center so that it is random for every time joining the dream
        center = new WorldPoint(Rs2Random.between(2270, 2276), Rs2Random.between(4693, 4696), 0);
        Rs2NpcModel dominic = npcCache.query().withName("Dominic Onion").nearestOnClientThread();
        if (dominic == null || !dominic.click("Dream")) return;
        if (!sleepUntil(() -> Rs2Widget.hasWidget("Which dream would you like to experience?"))) return;
        if (!Rs2Widget.clickWidget("Previous:")) return;
        if (!sleepUntil(() -> Rs2Widget.hasWidget("Click here to continue"))) return;
        if (!Rs2Widget.clickWidget("Click here to continue")) return;
        if (!sleepUntil(() -> Rs2Widget.hasWidget("Agree to pay"))) return;
        if (Rs2Widget.hasWidget("Agree to pay")) {
            Rs2Keyboard.typeString("1");
            Rs2Keyboard.enter();
            sleepUntil(() -> Microbot.getVarbitValue(VarbitID.NZONE_PURCHASEDDREAM) > 0, 5000);
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

    private static WorldPoint getLocalPlayerWorldLocation() {
        return Microbot.getClientThread().invoke(() -> {
            Player player = Microbot.getClient().getLocalPlayer();
            return player == null ? null : player.getWorldLocation();
        });
    }

    public boolean interactWithObject(int objectId) {
        Rs2TileObjectModel obj = tileObjectCache.query().withId(objectId).nearest();
        if (obj != null) {
            sleep(1000, 15000);
            WorldPoint playerLoc = getLocalPlayerWorldLocation();
            if (playerLoc != null && playerLoc.distanceTo(obj.getWorldLocation()) >= 15) {
                Rs2Walker.walkFastLocal(obj.getLocalLocation());
                sleepUntil(() -> {
                    WorldPoint loc = getLocalPlayerWorldLocation();
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

    public void manageSelfHarm() {
        int currentHP = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
        if (shouldSelfHarm(currentHP, maxHealth, useOverload)) {
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

    static boolean shouldUseOverload(int boostedRangedLevel, int realRangedLevel, int overloadPotionAmount, boolean hasOverloadInInventory) {
        return boostedRangedLevel == realRangedLevel && overloadPotionAmount > 0 && hasOverloadInInventory;
    }

    static boolean shouldSelfHarm(int currentHP, int maxHealth, boolean useOverload) {
        return currentHP >= maxHealth && !useOverload;
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

    boolean storePotions(int objectId, String itemName) {
        if (Rs2Inventory.count(itemName) == 0) return true;
        Rs2TileObjectModel obj = tileObjectCache.query().withId(objectId).nearest();
        if (obj == null || !obj.click("Store")) return false;
        if (!sleepUntil(() -> Rs2Widget.hasWidget("Store all your "), 5000)) return false;
        Rs2Keyboard.typeString("1");
        Rs2Keyboard.enter();
        // The old code tested the barrel OBJECT id against inventory, then dropped
        // leftovers. Only an observed inventory change confirms a deposit.
        return sleepUntil(() -> Rs2Inventory.count(itemName) == 0, 5000);
    }

    boolean fetchPotions(int objectId, String itemName, int requiredAmount) {
        int missing = Math.max(0, requiredAmount - Rs2Inventory.count(itemName));
        if (missing == 0) return true;
        if (Rs2Inventory.emptySlotCount() < missing) return false;
        Rs2TileObjectModel obj = tileObjectCache.query().withId(objectId).nearest();
        if (obj == null || !obj.click("Take")) return false;
        if (!sleepUntil(() -> Rs2Widget.hasWidget("How many doses of "), 5000)) return false;
        Rs2Keyboard.typeString(Integer.toString(missing * 4));
        Rs2Keyboard.enter();
        return sleepUntil(() -> Rs2Inventory.count(itemName) >= requiredAmount, 5000);
    }

    public boolean consumeEmptyVial() {
        if (!Rs2Widget.isWidgetVisible(129, 6)) {
            Rs2TileObjectModel vial = tileObjectCache.query().withId(ObjectID.NZONE_LOBBY_VIAL).nearest();
            if (vial == null || !vial.click("Drink")) return false;
            if (!sleepUntil(() -> Rs2Widget.isWidgetVisible(129, 6) || !isOutside(), 5000)) return false;
        }
        // Reacquire the visible button instead of dereferencing a missing widget,
        // and stop clicking as soon as the player leaves the lobby.
        for (int i = 0; i < 2 && isOutside(); i++) {
            if (!Rs2Widget.isWidgetVisible(129, 6)) break;
            Rs2Widget.clickWidget(129, 6);
            if (sleepUntil(() -> !isOutside(), 2500)) return true;
        }
        return !isOutside();
    }

    int dosesToBuy(int varbit, String potion, int desired) {
        return Math.max(0, (desired - Rs2Inventory.count(potion + " (4)")) * 4 - Microbot.getVarbitValue(varbit));
    }

    public boolean handleStore() {
        int overloadToBuy = dosesToBuy(VarbitID.NZONE_POTION_3, "overload", config.overloadPotionAmount());
        int absorptionToBuy = dosesToBuy(VarbitID.NZONE_POTION_4, "absorption", config.absorptionPotionAmount());
        // Refill a barrel only when it cannot supply this trip. Do not reopen
        // the shop after withdrawing the bottles that were just prepared.
        if (config.fillPotionBarrels()) {
            if (overloadToBuy > 0) overloadToBuy = Math.max(0, 255 - Microbot.getVarbitValue(VarbitID.NZONE_POTION_3));
            if (absorptionToBuy > 0) absorptionToBuy = Math.max(0, 255 - Microbot.getVarbitValue(VarbitID.NZONE_POTION_4));
        }
        if (overloadToBuy == 0 && absorptionToBuy == 0) return true;

        // Shop quantities and stored varbits are DOSES, not four-dose bottles.
        int totalCost = overloadToBuy * 1500 + absorptionToBuy * 1000;
        int nmzPoints = Microbot.getVarbitPlayerValue(VarPlayerID.NZONE_REWARDPOINTS);
        if (nmzPoints < totalCost) {
            stopWithReason("NMZ stopped: not enough points for potion doses (have " + nmzPoints + ", need " + totalCost + ")");
            return false;
        }

        Rs2TileObjectModel chest = tileObjectCache.query().withId(ObjectID.NZONE_LOBBY_CHEST).nearest();
        if (chest == null || !chest.click()) return false;
        if (!sleepUntil(() -> Rs2Widget.isWidgetVisible(13500418) || Rs2Bank.isBankPinWidgetVisible(), 10000)) return false;
        if (!handleRewardChestBankPin()) {
            halted = true;
            return false;
        }
        if (!Rs2Widget.isWidgetVisible(206, 6)) {
            if (!Rs2Widget.clickWidget("Benefits", Optional.of(206), 2, false)) return false;
            if (!sleepUntil(() -> Rs2Widget.isWidgetVisible(206, 6), 3000)) return false;
        }
        try {
            return buyDoses(6, VarbitID.NZONE_POTION_3, overloadToBuy)
                    && buyDoses(9, VarbitID.NZONE_POTION_4, absorptionToBuy);
        } finally {
            Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
            sleepUntil(() -> !Rs2Widget.isWidgetVisible(13500418), 3000);
        }
    }

    boolean buyDoses(int child, int stockVarbit, int doses) {
        int target = Microbot.getVarbitValue(stockVarbit) + doses;
        while (!Thread.currentThread().isInterrupted() && Microbot.getVarbitValue(stockVarbit) < target) {
            int before = Microbot.getVarbitValue(stockVarbit);
            String[] actions = Microbot.getClientThread().invoke(() -> {
                Widget parent = Rs2Widget.getWidget(206, 6);
                Widget item = parent == null ? null : parent.getChild(child);
                return item == null || item.isHidden() ? null : item.getActions();
            });
            int remaining = target - before;
            int buyX = buyOperation(actions, "buyx");
            int selectedOperation = buyX;
            if (selectedOperation == 0) {
                for (int batch : new int[]{50, 10, 5, 1}) {
                    if (remaining >= batch && (selectedOperation = buyOperation(actions, "buy" + batch)) > 0) break;
                }
            }
            final int operation = selectedOperation;
            if (operation == 0) return false;
            boolean clicked = Microbot.getClientThread().invoke((java.util.function.Supplier<Boolean>) () -> {
                Widget parent = Rs2Widget.getWidget(206, 6);
                Widget item = parent == null ? null : parent.getChild(child);
                if (item == null || item.isHidden()) return false;
                Rs2Widget.clickWidgetFast(item, child, operation);
                return true;
            });
            if (!clicked) return false;
            if (buyX > 0) {
                if (!sleepUntil(this::purchasePromptOpen, 5000)) return false;
                Rs2Keyboard.typeString(Integer.toString(target - before));
                Rs2Keyboard.enter();
            }
            if (!sleepUntil(() -> Microbot.getVarbitValue(stockVarbit) > before, 5000)) return false;
        }
        return Microbot.getVarbitValue(stockVarbit) >= target;
    }

    // Live chatbox layout: child 42 is a GRAPHIC close icon; the quantity
    // title is MES_TEXT (child 43). A withdrawal prompt must not accept a buy amount.
    boolean purchasePromptOpen() {
        return Microbot.getClientThread().invoke((java.util.function.Supplier<Boolean>) () -> {
            Widget prompt = Rs2Widget.getWidget(InterfaceID.Chatbox.MES_TEXT);
            if (prompt == null || prompt.isHidden() || prompt.getText() == null) return false;
            String title = prompt.getText().replaceAll("<[^>]*>", "").trim().toLowerCase(Locale.ROOT);
            return title.startsWith("enter amount") || title.contains("how many") && title.contains("buy");
        });
    }

    static int buyOperation(String[] actions, String wanted) {
        if (actions != null) for (int i = 0; i < actions.length; i++) {
            if (actions[i] != null && actions[i].replaceAll("<[^>]*>", "").replaceAll("[^a-zA-Z0-9]", "").equalsIgnoreCase(wanted)) return i + 1;
        }
        return 0;
    }

    boolean handleRewardChestBankPin() {
        if (!Rs2Bank.isBankPinWidgetVisible()) return true;
        for (int attempt = 0; attempt < 2; attempt++) {
            if (Rs2Bank.handleBankPin() || !Rs2Bank.isBankPinWidgetVisible()) {
                sleepUntil(() -> Rs2Widget.isWidgetVisible(13500418), 10000);
                return true;
            }
        }
        Microbot.showMessage("BOT SHUTDOWN: Could not enter your bank PIN at the NMZ reward chest.\n"
                + "NMZ potions were not bought.\n\n"
                + "Check that the Bank PIN field in your Microbot profile holds your 4-digit PIN,\n"
                + "or enter the PIN yourself and start the script again.");
        Microbot.stopPlugin(plugin);
        return false;
    }
}
