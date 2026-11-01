package net.runelite.client.plugins.microbot.aiomagic.scripts;

import net.runelite.api.Actor;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemStats;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.aiomagic.AIOMagicConfig;
import net.runelite.client.plugins.microbot.aiomagic.AIOMagicPlugin;
import net.runelite.client.plugins.microbot.aiomagic.enums.SplashSpellMode;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.RuneFilter;
import net.runelite.client.plugins.microbot.util.magic.Rs2CombatSpells;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Staff;
import net.runelite.client.plugins.microbot.util.magic.Runes;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import javax.inject.Inject;
import net.runelite.client.game.ItemManager;
import net.runelite.http.api.item.ItemPrice;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Splashing with an auto-progressing spell: picks the best combat spell for the current Magic level and runes,
 * restocks runes from the bank (or the GE), checks the -65 magic bonus, and keeps the session alive (AFK re-click).
 */
public class SplashScript extends Script {
    private static final RuneFilter CARRIED = RuneFilter.builder().includeBank(false).build();
    private static final int MIN_CASTS_BEFORE_RESTOCK = 1;
    private static final int GE_PRICE_MARKUP_PERCENT = 115;

    private final AIOMagicPlugin plugin;
    private final Random random = new Random();

    private long lastAnimationTime = System.currentTimeMillis();
    private long lastClickTime;
    private long afkIntervalMs;
    private WorldPoint startTile;
    private boolean safetyChecked;
    private boolean initialStockDone;
    private int lastMagicLevel = -1;
    private Rs2CombatSpells activeSpell;
    private int autocastFailures;
    private String lastLoggedDefaultNpc = "";

    @Inject
    public SplashScript(AIOMagicPlugin plugin) {
        this.plugin = plugin;
    }

    private AIOMagicConfig cfg() {
        return plugin.getConfig();
    }

    public boolean run() {
        Microbot.enableAutoRunOn = false;
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyGeneralBasicSetup();
        Rs2AntibanSettings.simulateAttentionSpan = true;
        Rs2AntibanSettings.nonLinearIntervals = true;
        Rs2AntibanSettings.contextualVariability = true;
        Rs2AntibanSettings.usePlayStyle = true;
        Rs2AntibanSettings.moveMouseOffScreen = true;
        Rs2AntibanSettings.moveMouseOffScreenChance = 1.0;
        Rs2Antiban.setActivity(Activity.SPLASHING);
        lastAnimationTime = System.currentTimeMillis();
        lastClickTime = 0;
        afkIntervalMs = SplashLogic.nextAfkIntervalMs(random);
        startTile = null;
        safetyChecked = false;
        initialStockDone = false;
        lastMagicLevel = -1;
        activeSpell = null;
        autocastFailures = 0;
        lastLoggedDefaultNpc = "";
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                tick();
            } catch (Exception ex) {
                Microbot.log("Splash error: " + ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    private void tick() {
        if (startTile == null) startTile = Rs2Player.getWorldLocation();

        if (!safetyChecked) {
            safetyChecked = true;
            if (!checkSplashSafety()) return;
        }

        int level = Rs2Player.getRealSkillLevel(Skill.MAGIC);
        boolean needsEval = activeSpell == null || level != lastMagicLevel || !canCastFromCarried(activeSpell);
        if (needsEval || !initialStockDone) {
            if (!selectAndStock(level)) return;
            if (activeSpell == null) return;
        }

        if (Rs2Magic.getCurrentAutoCastSpell() != activeSpell) {
            if (!Rs2Combat.setAutoCastSpell(activeSpell, false) && ++autocastFailures >= 5) {
                Microbot.showMessage("Unable to autocast " + activeSpell.name() + " (wrong spellbook or runes?)");
                shutdown();
            }
            return;
        }
        autocastFailures = 0;

        if (Rs2Player.isMoving()) return;
        if (!returnIfDrifted()) return;

        engage();
    }

    // ------------------------------------------------------------------ safety

    private boolean checkSplashSafety() {
        int bonus = totalMagicBonus();
        if (!SplashLogic.isSplashSafe(bonus) && cfg().autoSplashGear()) {
            if (!setupSplashGear()) return false;
            bonus = totalMagicBonus();
        }
        if (SplashLogic.isSplashSafe(bonus)) {
            Microbot.log("Magic attack bonus " + bonus + " - splash safe.");
            return true;
        }
        String msg = "Magic attack bonus is " + bonus + " (needs <= " + SplashLogic.SPLASH_SAFE_MAGIC_BONUS
                + "); you will hit instead of splash.";
        if (cfg().requireSplashBonus()) {
            Microbot.log("WARNING: " + msg + " Stopping (Require -65 magic bonus is on).");
            Microbot.showMessage(msg);
            shutdown();
            return false;
        }
        Microbot.log("WARNING: " + msg + " Continuing anyway.");
        return true;
    }

    private int totalMagicBonus() {
        int total = 0;
        for (Rs2ItemModel item : Rs2Equipment.items()) {
            if (item != null) total += magicBonusOf(item.getId());
        }
        return total;
    }

    private int magicBonusOf(int itemId) {
        try {
            ItemStats stats = Microbot.getItemManager().getItemStats(itemId);
            ItemEquipmentStats eq = stats == null ? null : stats.getEquipment();
            return eq == null ? 0 : eq.getAmagic();
        } catch (Exception e) {
            return 0;
        }
    }

    // ------------------------------------------------------------------ splash gear

    /** Wears the cheapest metal-only outfit that reaches the splash bonus. False if the script stopped. */
    private boolean setupSplashGear() {
        Microbot.log("Magic bonus " + totalMagicBonus() + " is above " + SplashLogic.SPLASH_SAFE_MAGIC_BONUS
                + "; setting up splash gear.");
        if (!Rs2Bank.walkToBankAndUseBank()) {
            Microbot.log("Could not reach a bank for splash gear.");
            return true;
        }
        sleepUntil(Rs2Bank::isOpen, 5000);
        if (!Rs2Bank.isOpen()) return true;

        List<SplashGear.Piece> pieces = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        List<Rs2ItemModel> owned = new ArrayList<>(Rs2Equipment.items());
        owned.addAll(Rs2Inventory.all());
        owned.addAll(Rs2Bank.bankItems());
        for (Rs2ItemModel item : owned) {
            if (item == null || !seen.add(item.getId())) continue;
            ItemEquipmentStats eq = equipmentStats(item.getId());
            if (eq == null || eq.getAmagic() >= 0 || eq.getSlot() == SplashGear.WEAPON) continue;
            pieces.add(new SplashGear.Piece(eq.getSlot(), item.getName(), item.getId(), eq.getAmagic(), 0, true));
        }

        List<SplashGear.Piece> all = new ArrayList<>(pieces);
        int defence = Rs2Player.getRealSkillLevel(Skill.DEFENCE);
        ItemManager im = Microbot.getItemManager();
        int[] slots = {SplashGear.HEAD, SplashGear.BODY, SplashGear.LEGS, SplashGear.SHIELD};
        for (int m = 0; m < SplashGear.METALS.length; m++) {
            if (!SplashGear.metalWearable(m, defence)) continue;
            for (int p = 0; p < SplashGear.PIECES.length; p++) {
                String name = SplashGear.metalPieceName(SplashGear.METALS[m], SplashGear.PIECES[p]);
                for (ItemPrice ip : im.search(name)) {
                    if (!ip.getName().equalsIgnoreCase(name)) continue;
                    ItemEquipmentStats eq = equipmentStats(ip.getId());
                    if (eq != null && eq.getAmagic() < 0 && !seen.contains(ip.getId())) {
                        all.add(new SplashGear.Piece(slots[p], name, ip.getId(), eq.getAmagic(), ip.getPrice(), false));
                    }
                    break;
                }
            }
        }

        int target = SplashLogic.SPLASH_SAFE_MAGIC_BONUS;
        boolean allowBuy = cfg().buySplashGear();
        long maxGp = cfg().maxGpPerPurchase();
        SplashGear.Plan plan = SplashGear.select(pieces, 0, target, false);
        if (plan == null && allowBuy) {
            SplashGear.Plan withBuying = SplashGear.select(all, 0, target, false);
            if (withBuying != null && withBuying.cost * GE_PRICE_MARKUP_PERCENT / 100 <= maxGp) plan = withBuying;
        }
        if (plan == null) {
            SplashGear.Plan wish = SplashGear.select(all, 0, target, false);
            StringBuilder need = new StringBuilder();
            if (wish != null) {
                for (SplashGear.Piece pc : wish.pieces) if (!pc.owned) need.append(pc.name).append(", ");
            }
            String msg = wish == null
                    ? "Cannot reach a " + target + " magic bonus with metal armour at Defence " + defence + "."
                    : "Splash gear still needed: " + need + "about " + wish.cost + " gp ("
                    + (allowBuy ? "over the " + maxGp + " gp cap" : "buying is off") + ").";
            Microbot.log(msg);
            Microbot.showMessage(msg);
            Rs2Bank.closeBank();
            shutdown();
            return false;
        }

        // Take everything off (this banks any positive-bonus items), then withdraw only the planned pieces.
        Rs2Bank.depositEquipment();
        sleepUntil(() -> !Rs2Equipment.isWearing(), 3000);
        List<SplashGear.Piece> toBuy = new ArrayList<>();
        for (SplashGear.Piece pc : plan.pieces) {
            if (!pc.owned) {
                toBuy.add(pc);
            } else if (!Rs2Inventory.hasItem(pc.itemId)) {
                Rs2Bank.withdrawOne(pc.itemId);
                sleepUntil(() -> Rs2Inventory.hasItem(pc.itemId), 3000);
            }
        }
        if (!toBuy.isEmpty()) {
            long cost = 0;
            for (SplashGear.Piece pc : toBuy) cost += pc.cost * GE_PRICE_MARKUP_PERCENT / 100 + 1;
            int take = (int) Math.min(Math.min(cost, maxGp), Rs2Bank.count(ItemID.COINS));
            if (take > 0) {
                Rs2Bank.withdrawX(ItemID.COINS, take);
                sleepUntil(() -> Rs2Inventory.itemQuantity(ItemID.COINS) > 0, 3000);
            }
            Rs2Bank.closeBank();
            Microbot.log("Buying splash gear for about " + cost + " gp (cap " + maxGp + ").");
            Rs2GrandExchange.walkToGrandExchange();
            if (Rs2GrandExchange.openExchange()) {
                for (SplashGear.Piece pc : toBuy) {
                    Rs2GrandExchange.buyItem(pc.name, (int) (pc.cost * GE_PRICE_MARKUP_PERCENT / 100 + 1), 1);
                    sleepUntil(Rs2GrandExchange::isOpen, 3000);
                }
                sleepUntil(Rs2GrandExchange::hasFinishedBuyingOffers, 90_000);
                Rs2GrandExchange.collectAllToInventory();
                Rs2GrandExchange.closeExchange();
            }
        } else {
            Rs2Bank.closeBank();
        }

        for (SplashGear.Piece pc : plan.pieces) {
            if (!Rs2Inventory.hasItem(pc.itemId)) continue;
            if (!Rs2Inventory.interact(pc.itemId, "Wear")) Rs2Inventory.interact(pc.itemId, "Wield");
            sleepUntil(() -> Rs2Equipment.get(pc.itemId) != null, 3000);
        }
        StringBuilder worn = new StringBuilder();
        for (Rs2ItemModel it : Rs2Equipment.items()) if (it != null) worn.append(it.getName()).append(", ");
        Microbot.log("Splash gear worn: " + worn + "magic bonus now " + totalMagicBonus());
        returnToStart();
        return true;
    }

    private ItemEquipmentStats equipmentStats(int itemId) {
        try {
            ItemStats stats = Microbot.getItemManager().getItemStats(itemId);
            return stats == null ? null : stats.getEquipment();
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ spell + runes

    private boolean bankingAllowed() {
        return cfg().castsPerTrip() > 0;
    }

    private Map<Runes, Integer> carriedRunes() {
        return new HashMap<>(Rs2Magic.getRunes(CARRIED));
    }

    private Map<Runes, Integer> bankRunes() {
        Map<Runes, Integer> out = new HashMap<>();
        if (!bankingAllowed()) return out;
        for (Rs2ItemModel item : Rs2Bank.bankItems()) {
            Runes rune = Runes.byItemId(item.getId());
            if (rune != null) out.merge(rune, item.getQuantity(), (a, b) -> (int) Math.min((long) a + b, Integer.MAX_VALUE));
        }
        return out;
    }

    private boolean canCastFromCarried(Rs2CombatSpells spell) {
        return SplashLogic.castsAvailable(spell.getRequiredRunes(), carriedRunes()) >= MIN_CASTS_BEFORE_RESTOCK;
    }

    /** Picks the spell for this level/rune situation, restocks if needed. Returns false when the script stopped or is waiting. */
    private boolean selectAndStock(int level) {
        Map<Runes, Integer> carried = carriedRunes();
        Rs2CombatSpells target = chooseSpell(level, carried);
        if (target == null) {
            if (bankingAllowed() && !initialStockDone) {
                // Bank contents are unknown until it has been opened once: look inside before giving up.
                initialStockDone = true;
                visitBank(null);
                return false;
            }
            Microbot.showMessage("No castable combat spell: no runes available for your Magic level " + level);
            shutdown();
            return false;
        }

        if (target != activeSpell) {
            Microbot.log("Splash spell: " + (activeSpell == null ? "none" : activeSpell.name()) + " -> " + target.name()
                    + " (Magic " + level + ")");
            autocastFailures = 0;
        }
        activeSpell = target;
        lastMagicLevel = level;

        int casts = SplashLogic.castsAvailable(target.getRequiredRunes(), carried);
        boolean needRestock = casts < MIN_CASTS_BEFORE_RESTOCK || (!initialStockDone && casts < cfg().castsPerTrip() / 4);
        initialStockDone = true;
        if (!needRestock || !bankingAllowed()) {
            if (casts < MIN_CASTS_BEFORE_RESTOCK) {
                Microbot.showMessage("Out of runes for " + target.name());
                shutdown();
                return false;
            }
            return true;
        }

        visitBank(target);
        // Re-check after the trip; the next tick re-evaluates (downgrades the spell if the restock failed).
        activeSpell = null;
        return false;
    }

    private Rs2CombatSpells chooseSpell(int level, Map<Runes, Integer> carried) {
        if (cfg().splashSpellMode() == SplashSpellMode.CONFIGURED) {
            Rs2CombatSpells fixed = plugin.getCombatSpell();
            if (fixed.getRequiredLevel() > level) {
                Microbot.showMessage(fixed.name() + " needs Magic " + fixed.getRequiredLevel());
                shutdown();
                return null;
            }
            return fixed;
        }
        Map<Runes, Integer> supply = SplashLogic.merge(carried, bankRunes());
        // A staff we own (bank or inventory) will supply its runes once wielded.
        Rs2Staff staff = bestOwnedStaff(null);
        if (staff != null && cfg().useElementalStaff()) {
            supply = SplashLogic.merge(supply, SplashLogic.unlimited(staff.getRunes()));
        }
        Rs2CombatSpells best = SplashLogic.bestSpell(level, supply, 1);
        if (best == null && cfg().buyRunesOnGe()) {
            best = bestAffordableOnGe(level, carried);
        }
        return best;
    }

    private Rs2CombatSpells bestAffordableOnGe(int level, Map<Runes, Integer> carried) {
        Map<Runes, Integer> prices = gePrices(true);
        Rs2CombatSpells best = null;
        for (Rs2CombatSpells spell : SplashLogic.AUTO_ORDER) {
            if (spell.getRequiredLevel() > level) continue;
            int casts = SplashLogic.affordableCasts(spell.getRequiredRunes(), 100, carried, prices, cfg().maxGpPerPurchase());
            if (casts >= 100 && (best == null || spell.getRequiredLevel() >= best.getRequiredLevel())) best = spell;
        }
        return best;
    }

    // ------------------------------------------------------------------ bank / GE trip

    /** Walks to the nearest bank (by round trip), equips a staff and withdraws runes for the spell, then returns. */
    private void visitBank(Rs2CombatSpells spell) {
        Microbot.log(spell == null ? "Visiting the bank to check for runes." : "Restocking runes for " + spell.name() + ".");
        if (!Rs2Bank.walkToBankAndUseBank()) {
            Microbot.log("Could not reach a bank.");
            return;
        }
        sleepUntil(Rs2Bank::isOpen, 5000);
        if (!Rs2Bank.isOpen()) return;

        if (spell != null) {
            equipBestStaff(spell);
            Map<Runes, Integer> carried = carriedRunes();
            Map<Runes, Integer> missing = SplashLogic.runesToWithdraw(spell.getRequiredRunes(), cfg().castsPerTrip(), carried);
            for (Map.Entry<Runes, Integer> e : missing.entrySet()) {
                int id = e.getKey().getItemId();
                int take = Math.min(e.getValue(), Rs2Bank.count(id));
                if (take > 0) {
                    Rs2Bank.withdrawX(id, take);
                    sleepUntil(() -> Rs2Inventory.hasItem(id), 3000);
                }
            }
            carried = carriedRunes();
            if (SplashLogic.castsAvailable(spell.getRequiredRunes(), carried) < MIN_CASTS_BEFORE_RESTOCK
                    && cfg().buyRunesOnGe()) {
                buyRunesOnGe(spell, carried);
                return;
            }
        }
        Rs2Bank.closeBank();
        returnToStart();
    }

    private void equipBestStaff(Rs2CombatSpells spell) {
        if (!cfg().useElementalStaff()) return;
        Rs2Staff best = bestOwnedStaff(spell);
        if (best == null) return;
        Rs2ItemModel weapon = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
        if (weapon != null && weapon.getId() == best.getItemID()) return;
        if (Rs2Equipment.get(best.getItemID()) != null) return;
        if (!Rs2Inventory.hasItem(best.getItemID())) {
            if (Rs2Bank.count(best.getItemID()) < 1) return;
            Rs2Bank.withdrawOne(best.getItemID());
            sleepUntil(() -> Rs2Inventory.hasItem(best.getItemID()), 3000);
        }
        Microbot.log("Wielding " + best.name() + " to save runes.");
        Rs2Inventory.interact(best.getItemID(), "Wield");
        sleepUntil(() -> Rs2Equipment.get(best.getItemID()) != null, 3000);
    }

    /**
     * The owned staff (equipped, inventory or bank) that supplies the most of the spell's runes and keeps the
     * player splash-safe. Spell may be null (then the most useful staff for any strike spell is irrelevant: any
     * rune-providing staff qualifies).
     */
    private Rs2Staff bestOwnedStaff(Rs2CombatSpells spell) {
        int total = totalMagicBonus();
        Rs2ItemModel weapon = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
        int weaponBonus = weapon == null ? 0 : magicBonusOf(weapon.getId());
        Rs2Staff best = null;
        int bestScore = 0;
        for (Rs2Staff staff : Rs2Staff.values()) {
            if (staff == Rs2Staff.NONE) continue;
            boolean owned = (weapon != null && weapon.getId() == staff.getItemID())
                    || Rs2Inventory.hasItem(staff.getItemID())
                    || Rs2Bank.bankItems().stream().anyMatch(i -> i.getId() == staff.getItemID());
            if (!owned) continue;
            int score = 0;
            if (spell == null) {
                score = staff.getRunes().size();
            } else {
                for (Map.Entry<Runes, Integer> e : spell.getRequiredRunes().entrySet()) {
                    if (staff.provides(e.getKey())) score += e.getValue();
                }
            }
            if (score <= bestScore) continue;
            boolean isWielded = weapon != null && weapon.getId() == staff.getItemID();
            if (!isWielded
                    && !SplashLogic.staffKeepsSplashSafe(total, weaponBonus, magicBonusOf(staff.getItemID()))) {
                continue;
            }
            best = staff;
            bestScore = score;
        }
        return best;
    }

    private Map<Runes, Integer> gePrices(boolean withMarkup) {
        Map<Runes, Integer> prices = new EnumMap<>(Runes.class);
        for (Rs2CombatSpells spell : SplashLogic.AUTO_ORDER) {
            for (Runes r : spell.getRequiredRunes().keySet()) {
                if (prices.containsKey(r)) continue;
                int p = Math.max(1, Rs2GrandExchange.getPrice(r.getItemId()));
                prices.put(r, withMarkup ? Math.max(p + 1, p * GE_PRICE_MARKUP_PERCENT / 100) : p);
            }
        }
        return prices;
    }

    /** Called with the bank open and no usable runes: take coins, buy runes up to the gp cap, collect. */
    private void buyRunesOnGe(Rs2CombatSpells spell, Map<Runes, Integer> carried) {
        long cap = cfg().maxGpPerPurchase();
        int coinsInBank = Rs2Bank.count(ItemID.COINS);
        long budget = Math.min(cap, (long) coinsInBank + Rs2Inventory.itemQuantity(ItemID.COINS));
        if (budget <= 0) {
            Microbot.showMessage("Out of runes and no coins to buy more.");
            Rs2Bank.closeBank();
            shutdown();
            return;
        }
        int toWithdraw = (int) Math.min(budget, coinsInBank);
        if (toWithdraw > 0) {
            Rs2Bank.withdrawX(ItemID.COINS, toWithdraw);
            sleepUntil(() -> Rs2Inventory.itemQuantity(ItemID.COINS) > 0, 3000);
        }
        Rs2Bank.closeBank();

        Map<Runes, Integer> prices = gePrices(true);
        int casts = SplashLogic.affordableCasts(spell.getRequiredRunes(), cfg().castsPerTrip(), carried, prices, budget);
        Map<Runes, Integer> toBuy = SplashLogic.runesToWithdraw(spell.getRequiredRunes(), casts, carried);
        if (casts < 1 || toBuy.isEmpty()) {
            Microbot.showMessage("Cannot afford runes for " + spell.name() + " within " + cap + " gp.");
            shutdown();
            return;
        }
        Microbot.log("Buying " + String.join(", ", SplashLogic.describe(toBuy)) + " for about "
                + SplashLogic.estimateCost(toBuy, prices) + " gp (cap " + cap + ").");
        Rs2GrandExchange.walkToGrandExchange();
        if (!Rs2GrandExchange.openExchange()) return;
        for (Map.Entry<Runes, Integer> e : toBuy.entrySet()) {
            Rs2GrandExchange.buyItem(SplashLogic.runeItemName(e.getKey()), prices.get(e.getKey()), e.getValue());
            sleepUntil(Rs2GrandExchange::isOpen, 3000);
        }
        sleepUntil(Rs2GrandExchange::hasFinishedBuyingOffers, 90_000);
        Rs2GrandExchange.collectAllToInventory();
        Rs2GrandExchange.closeExchange();
        returnToStart();
    }

    private void returnToStart() {
        if (startTile != null && Rs2Player.getWorldLocation().distanceTo(startTile) > 3) {
            Rs2Walker.walkTo(startTile);
        }
    }

    // ------------------------------------------------------------------ AFK / targeting

    private boolean returnIfDrifted() {
        if (startTile == null) return true;
        if (SplashLogic.hasDrifted(Rs2Player.getWorldLocation().distanceTo(startTile))) {
            Microbot.log("Drifted from the start tile; walking back.");
            Rs2Walker.walkTo(startTile);
            return false;
        }
        return true;
    }

    private boolean isConfiguredTarget(String name) {
        String configured = plugin.getNpcName() == null ? "" : plugin.getNpcName().trim();
        return name != null && name.equalsIgnoreCase(configured);
    }

    private void engage() {
        long now = System.currentTimeMillis();
        if (Rs2Player.getAnimation() != -1) lastAnimationTime = now;

        String configured = plugin.getNpcName() == null ? "" : plugin.getNpcName().trim();
        boolean useDefault = configured.isEmpty();

        Actor interacting = Rs2Player.getInteracting();
        boolean validTarget = interacting != null && interacting.getName() != null
                && (useDefault ? SplashLogic.isSafeDefaultNpc(interacting.getName(), interacting.getCombatLevel())
                : isConfiguredTarget(interacting.getName()));
        if (validTarget) {
            int dist = Rs2Player.getWorldLocation().distanceTo(interacting.getWorldLocation());
            boolean tooFar = SplashLogic.isTargetTooFar(dist);
            boolean afkDue = SplashLogic.isAfkClickDue(now, lastClickTime, afkIntervalMs);
            boolean stalled = now - lastAnimationTime >= 10_000;
            if (!tooFar && !afkDue && !stalled) return;
            Microbot.log(tooFar ? "Target too far, re-targeting." : afkDue ? "AFK re-click." : "Splashing stalled. Re-engaging...");
        }

        if (Rs2AntibanSettings.actionCooldownActive) return;

        Rs2NpcModel targetNpc;
        if (useDefault) {
            targetNpc = Microbot.getRs2NpcCache().query()
                    .where(n -> SplashLogic.isSafeDefaultNpc(n.getName(), n.getCombatLevel()))
                    .nearestOnClientThread(SplashLogic.MAX_TARGET_DISTANCE);
            if (targetNpc != null && !String.valueOf(targetNpc.getName()).equalsIgnoreCase(lastLoggedDefaultNpc)) {
                lastLoggedDefaultNpc = String.valueOf(targetNpc.getName());
                Microbot.log("No NPC configured; defaulting to nearest safe target: " + lastLoggedDefaultNpc
                        + " (combat " + targetNpc.getCombatLevel() + ")");
            }
        } else {
            targetNpc = Microbot.getRs2NpcCache().query()
                    .withName(configured)
                    .nearestOnClientThread(SplashLogic.MAX_TARGET_DISTANCE);
        }
        if (targetNpc == null) {
            Microbot.log("Unable to find a splash target within " + SplashLogic.MAX_TARGET_DISTANCE + " tiles"
                    + (useDefault ? "" : ": " + configured));
            return;
        }

        sleep(plugin.nextClickDelay());
        if (targetNpc.click("Attack")) {
            lastClickTime = System.currentTimeMillis();
            afkIntervalMs = SplashLogic.nextAfkIntervalMs(random);
            Rs2Antiban.actionCooldown();
        }
    }

    @Override
    public void shutdown() {
        Rs2Antiban.resetAntibanSettings();
        super.shutdown();
    }
}
