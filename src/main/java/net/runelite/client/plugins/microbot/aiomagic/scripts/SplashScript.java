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

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
    private static final String SPLASH_GEAR_HINT = "Wear metal armour (helm, platebody, platelegs, kiteshield) plus a piece"
            + " with a magic penalty such as green d'hide vambraces";
    private static final String STAFF_BONUS_NOTE = "Metal armour plus a staff only reaches about -55.";

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
    private final Set<Rs2CombatSpells> restockTried = new HashSet<>();
    private String lastLoggedDefaultNpc = "";
    private String lastNoTargetLog = "";
    private String lastErrorLog = "";

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
        restockTried.clear();
        lastLoggedDefaultNpc = "";
        lastNoTargetLog = "";
        lastErrorLog = "";
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                tick();
                lastErrorLog = "";
            } catch (Exception ex) {
                String msg = "Splashing hit an error, retrying: " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
                if (!msg.equals(lastErrorLog)) {
                    lastErrorLog = msg;
                    Microbot.log(msg);
                }
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
        boolean spellSettingChanged = cfg().splashSpellMode() == SplashSpellMode.CONFIGURED
                && activeSpell != null && plugin.getCombatSpell() != activeSpell;
        boolean needsEval = activeSpell == null || level != lastMagicLevel || spellSettingChanged
                || !canCastFromCarried(activeSpell);
        if (needsEval || !initialStockDone) {
            if (!selectAndStock(level)) return;
            if (activeSpell == null) return;
        }

        if (Rs2Magic.getCurrentAutoCastSpell() != activeSpell) {
            if (!Rs2Combat.setAutoCastSpell(activeSpell, false) && ++autocastFailures >= 5) {
                Microbot.showMessage("Unable to autocast " + spellName(activeSpell) + ". AIO Magic stopped. Wield a staff,"
                        + " switch to the standard spellbook and check you have the runes for it.");
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
        if (SplashLogic.isSplashSafe(bonus)) {
            Microbot.log("Magic attack bonus " + bonus + " - splash safe.");
            return true;
        }
        String msg = "Magic attack bonus is " + bonus + " (needs " + SplashLogic.SPLASH_SAFE_MAGIC_BONUS
                + " or lower); you may hit instead of splash.";
        if (cfg().requireSplashBonus()) {
            Microbot.log("WARNING: " + msg + " Stopping (Require -65 magic bonus is on).");
            Microbot.showMessage(msg + " AIO Magic stopped. " + SPLASH_GEAR_HINT
                    + ", or turn off 'Require -65 magic bonus'. " + STAFF_BONUS_NOTE);
            shutdown();
            return false;
        }
        Microbot.log("WARNING: " + msg + " Continuing anyway. " + SPLASH_GEAR_HINT + ". " + STAFF_BONUS_NOTE);
        return true;
    }

    private int totalMagicBonus() {
        List<Integer> ids = new ArrayList<>();
        for (Rs2ItemModel item : Rs2Equipment.items()) {
            if (item != null) ids.add(item.getId());
        }
        int total = 0;
        Map<Integer, ItemEquipmentStats> stats = equipmentStats(ids);
        for (int id : ids) {
            ItemEquipmentStats eq = stats.get(id);
            if (eq != null) total += eq.getAmagic();
        }
        return total;
    }

    private int magicBonusOf(int itemId) {
        ItemEquipmentStats eq = equipmentStats(itemId);
        return eq == null ? 0 : eq.getAmagic();
    }

    private ItemEquipmentStats equipmentStats(int itemId) {
        try {
            ItemStats stats = Microbot.getClientThread()
                    .runOnClientThreadOptional(() -> Microbot.getItemManager().getItemStats(itemId))
                    .orElse(null);
            return stats == null ? null : stats.getEquipment();
        } catch (Exception e) {
            return null;
        }
    }

    private Map<Integer, ItemEquipmentStats> equipmentStats(List<Integer> itemIds) {
        try {
            return Microbot.getClientThread().runOnClientThreadOptional(() -> {
                Map<Integer, ItemEquipmentStats> out = new HashMap<>();
                ItemManager im = Microbot.getItemManager();
                for (int id : itemIds) {
                    if (out.containsKey(id)) continue;
                    ItemStats stats = im.getItemStats(id);
                    if (stats != null && stats.getEquipment() != null) out.put(id, stats.getEquipment());
                }
                return out;
            }).orElseGet(HashMap::new);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private static String readable(String enumName) {
        StringBuilder out = new StringBuilder();
        for (String word : enumName.toLowerCase(Locale.ROOT).split("_")) {
            if (word.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(out.length() > 0 && word.equals("of") ? word
                    : Character.toUpperCase(word.charAt(0)) + word.substring(1));
        }
        return out.toString();
    }

    private static String spellName(Rs2CombatSpells spell) {
        return readable(spell.name());
    }

    private static String gp(long amount) {
        return String.format("%,d gp", amount);
    }

    private static String runeNames(Iterable<Runes> runes) {
        List<String> names = new ArrayList<>();
        for (Runes r : runes) names.add(SplashLogic.runeItemName(r).toLowerCase(Locale.ROOT) + "s");
        return String.join(", ", names);
    }

    private static List<Runes> shortRunes(Rs2CombatSpells spell, Map<Runes, Integer> carried) {
        List<Runes> out = new ArrayList<>();
        for (Map.Entry<Runes, Integer> e : spell.getRequiredRunes().entrySet()) {
            if (carried.getOrDefault(e.getKey(), 0) < e.getValue()) out.add(e.getKey());
        }
        return out.isEmpty() ? new ArrayList<>(spell.getRequiredRunes().keySet()) : out;
    }

    private void stopOutOfRunes(Rs2CombatSpells spell, Map<Runes, Integer> carried) {
        String fix = !bankingAllowed()
                ? "Bring more runes, or set 'Casts per trip' above 0 so the script restocks from the bank."
                : cfg().buyRunesOnGe()
                ? "Put more runes or coins in your bank, or raise 'Max gp per purchase'."
                : "Put more runes in your bank, or turn on 'Buy runes on the GE'.";
        Microbot.showMessage("Out of runes for " + spellName(spell) + ": need " + runeNames(shortRunes(spell, carried))
                + ". AIO Magic stopped. " + fix);
        shutdown();
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
        if (!isRunning()) return false;
        if (target == null) {
            if (bankingAllowed() && !initialStockDone) {
                // Bank contents are unknown until it has been opened once: look inside before giving up.
                initialStockDone = true;
                visitBank(null);
                return false;
            }
            Microbot.showMessage("No combat spell you can cast at Magic level " + level + ": no runes in your inventory"
                    + (bankingAllowed() ? " or bank" : "") + ". AIO Magic stopped. Put runes for a strike spell (e.g. air and mind runes"
                    + " for Wind Strike) in your bank or inventory, or "
                    + (cfg().buyRunesOnGe() ? "put more coins in your bank / raise 'Max gp per purchase'."
                    : "turn on 'Buy runes on the GE'."));
            shutdown();
            return false;
        }

        if (target != activeSpell) {
            Microbot.log("Splash spell: " + (activeSpell == null ? "none" : spellName(activeSpell)) + " -> " + spellName(target)
                    + " (Magic " + level + ")");
            autocastFailures = 0;
        }
        activeSpell = target;
        lastMagicLevel = level;

        int casts = SplashLogic.castsAvailable(target.getRequiredRunes(), carried);
        if (casts >= MIN_CASTS_BEFORE_RESTOCK) restockTried.clear();
        boolean needRestock = casts < MIN_CASTS_BEFORE_RESTOCK;
        initialStockDone = true;
        if (!needRestock || !bankingAllowed()) {
            if (casts < MIN_CASTS_BEFORE_RESTOCK) {
                stopOutOfRunes(target, carried);
                return false;
            }
            return true;
        }

        if (casts < MIN_CASTS_BEFORE_RESTOCK && restockTried.contains(target)) {
            stopOutOfRunes(target, carried);
            return false;
        }
        if (casts < MIN_CASTS_BEFORE_RESTOCK) restockTried.add(target);
        visitBank(target);
        // Re-check after the trip; the next tick re-evaluates (downgrades the spell if the restock failed).
        activeSpell = null;
        return false;
    }

    private Rs2CombatSpells chooseSpell(int level, Map<Runes, Integer> carried) {
        if (cfg().splashSpellMode() == SplashSpellMode.CONFIGURED) {
            Rs2CombatSpells fixed = plugin.getCombatSpell();
            if (fixed.getRequiredLevel() > level) {
                Microbot.showMessage(spellName(fixed) + " needs Magic level " + fixed.getRequiredLevel() + ". AIO Magic stopped."
                        + " Pick a lower 'Combat Spell', or set 'Splash spell' to Auto.");
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
        Microbot.log(spell == null ? "Visiting the bank to check for runes." : "Restocking runes for " + spellName(spell) + ".");
        if (!Rs2Bank.walkToBankAndUseBank()) {
            Microbot.showMessage("Could not reach a bank to " + (spell == null ? "check for runes" : "restock runes for "
                    + spellName(spell)) + ". AIO Magic stopped. Start closer to a bank or bring enough runes.");
            shutdown();
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
        Microbot.log("Wielding " + readable(best.name()) + " to save runes.");
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
            Microbot.showMessage("Out of runes for " + spellName(spell) + " and no coins to buy more. AIO Magic stopped."
                    + " Put coins or runes in your bank.");
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
            Microbot.showMessage("Cannot afford runes for " + spellName(spell) + " within " + gp(cap) + ". AIO Magic stopped."
                    + " Raise 'Max gp per purchase' or put more coins or runes in your bank.");
            shutdown();
            return;
        }
        Microbot.log("Buying " + String.join(", ", SplashLogic.describe(toBuy)) + " for about "
                + gp(SplashLogic.estimateCost(toBuy, prices)) + " (cap " + gp(cap) + ").");
        Rs2GrandExchange.walkToGrandExchange();
        if (!Rs2GrandExchange.openExchange()) {
            Microbot.showMessage("Could not open the Grand Exchange to buy runes for " + spellName(spell) + ". AIO Magic stopped."
                    + " Buy the runes yourself or start the script near the Grand Exchange.");
            shutdown();
            return;
        }
        Map<Runes, Integer> before = carriedRunes();
        for (Map.Entry<Runes, Integer> e : toBuy.entrySet()) {
            Rs2GrandExchange.buyItem(SplashLogic.runeItemName(e.getKey()), prices.get(e.getKey()), e.getValue());
            sleepUntil(Rs2GrandExchange::isOpen, 3000);
        }
        sleepUntil(Rs2GrandExchange::hasFinishedBuyingOffers, 90_000);
        Rs2GrandExchange.collectAllToInventory();
        sleepUntil(() -> unfilledRunes(toBuy, before).isEmpty(), 3000);
        Rs2GrandExchange.closeExchange();
        List<String> unfilled = unfilledRunes(toBuy, before);
        for (String offer : unfilled) Microbot.log("The GE offer for " + offer + " did not fill.");
        if (!unfilled.isEmpty() && !canCastFromCarried(spell)) {
            boolean one = unfilled.size() == 1;
            Microbot.showMessage("The GE offer" + (one ? "" : "s") + " for " + String.join(", ", unfilled) + " did not fill."
                    + " AIO Magic stopped. Check your GE offers, buy the runes yourself and start again.");
            shutdown();
            return;
        }
        returnToStart();
    }

    private List<String> unfilledRunes(Map<Runes, Integer> bought, Map<Runes, Integer> before) {
        Map<Runes, Integer> now = carriedRunes();
        List<String> out = new ArrayList<>();
        for (Map.Entry<Runes, Integer> e : bought.entrySet()) {
            long received = (long) now.getOrDefault(e.getKey(), 0) - before.getOrDefault(e.getKey(), 0);
            if (received < e.getValue()) out.add(String.format("%,d %ss", e.getValue(), SplashLogic.runeItemName(e.getKey())));
        }
        return out;
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
            boolean tooFar = useDefault && SplashLogic.isTargetTooFar(dist);
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
            WorldPoint anchor = startTile;
            targetNpc = Microbot.getRs2NpcCache().query()
                    .withName(configured)
                    .where(n -> n.isWithinDistance(anchor, SplashLogic.MAX_DRIFT_DISTANCE))
                    .nearestOnClientThread();
        }
        if (targetNpc == null) {
            String msg = useDefault
                    ? "No safe splash target (chicken, rat, man, cow...) within " + SplashLogic.MAX_TARGET_DISTANCE
                    + " tiles. Move closer to one or enter an NPC Name."
                    : "No " + configured + " within " + SplashLogic.MAX_DRIFT_DISTANCE
                    + " tiles of where you started. Start closer to one.";
            if (!msg.equals(lastNoTargetLog)) {
                lastNoTargetLog = msg;
                Microbot.log(msg);
            }
            return;
        }
        lastNoTargetLog = "";

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
