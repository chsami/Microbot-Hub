package net.runelite.client.plugins.microbot.zulrahslayer.actions;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.IEntity;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsItem;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.misc.Rs2Potion;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.poh.PohTeleports;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * The between-kills restock + travel loop, armed by {@link LootAction} once looting finishes (only
 * when {@code restockBetweenKills} is enabled). Runs after the fight is over ({@code phase == null}),
 * as ONE blocking sequence on the tick worker thread — the underlying Rs2 helpers (teleport, bank,
 * walk) all block via {@code sleepUntil}, so a linear script fits them better than a per-tick machine.
 * While it runs, the tick pipeline's overlap guard skips further ticks; no combat action needs to run
 * between kills anyway. On any step failure it aborts, logs, and clears the flag (the bot goes idle so
 * the problem is visible rather than looping).
 *
 * <p>Route mirrors the manual routine: teleport to house -> pray at altar -> POH portal to the GE ->
 * deposit all, eat lobsters to full if hurt, load the magic setup -> teleport to house -> equip Dramen
 * staff, fairy ring (last destination) to Zul-Andra -> cross the stepping stone -> walk to the dock ->
 * board the boat -> dismiss the arrival dialogue. The existing spawn handling then runs the fight.
 */
@Slf4j
public class ReturnToZulrahAction implements ZulrahAction {

    // Confirmed object ids on the route.
    private static final String GE_PORTAL_NAME = "Grand Exchange Portal";
    private static final WorldPoint ZULANDRA_DOCK = new WorldPoint(2212, 3057, 0);
    private static final int DRAMEN_STAFF_ID = 772;
    private static final int LOBSTER_ID = 379;

    private static final String ALTAR_NAME = "Altar";
    private static final String FAIRY_RING_NAME = "Fairy ring";
    private static final String BOAT_NAME = "Sacrificial boat";

    private static final int STEP_TIMEOUT_MS = 20_000;
    private static final int POH_PORTAL_ID = 4525;

    @Override
    public int order() {
        return 1100;
    }

    @Override
    public String key() {
        return "return-to-zulrah";
    }

    @Override
    public boolean needsExecution(ZulrahState state) {
        FightContext ctx = state.context();
        return ctx.isPrepPending() && ctx.getPhase() == null;
    }

    @Override
    public Object execute(ZulrahState state) {
        FightContext ctx = state.context();
        try {
            if (runPrep(ctx)) {
                log.info("[prep] back at the shrine; handing off to the fight.");
                return "prep-complete";
            }
            log.error("[prep] between-kills routine aborted; the bot is now idle. Check the step above.");
            return "prep-aborted";
        } catch (Exception ex) {
            log.error("[prep] between-kills routine threw; aborting.", ex);
            return "prep-error";
        } finally {
            ctx.setPrepPending(false);
        }
    }

    /**
     * Runs the trip; returns false at the first step that doesn't reach its precondition. The banking
     * leg is skipped when {@code ctx.isPrepSkipBank()} is set (the "Travel to Zulrah" start point) —
     * only the travel leg runs then.
     */
    private boolean runPrep(FightContext ctx) {
        if (!ctx.isPrepSkipBank() && !bankLeg(ctx)) {
            return false;
        }
        return travelLeg(ctx);
    }

    /** Teleport to the house, restore prayer, take the POH portal to the GE and restock the magic setup. */
    private boolean bankLeg(FightContext ctx) {
        Rs2Prayer.disableAllPrayers();
        teleportToHouse();
        if (!PohTeleports.isInHouse()) {
            log.warn("[prep] not in the POH after Teleport to House");
            return false;
        }
        if (!prayAtAltar()) {
            return false;
        }

        if (!interactObject(GE_PORTAL_NAME, null)) {
            return false;
        }
        if (!sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo(BankLocation.GRAND_EXCHANGE.getWorldPoint()) < 20, STEP_TIMEOUT_MS)) {
            log.warn("[prep] did not arrive at the Grand Exchange");
            return false;
        }

        return doBankStep(ctx);
    }

    private boolean travelLeg(FightContext ctx) {
        Rs2Prayer.disableAllPrayers();
        teleportToHouse();
        if (!PohTeleports.isInHouse()) {
            log.warn("[prep] not in the POH before travelling to Zulrah");
            return false;
        }

        if (ctx.isPrepSkipBank() && !prayAtAltar()) {
            return false;
        }
        return fairyRingToZulAndra()
                && walkToDock()
                && ensureMagicEquipment(ctx)
                && prePot()
                && boardBoat();
    }

    private boolean prePot() {
        Set<String> boosters = new HashSet<>();
        addLower(boosters, Rs2Potion.getRangePotionsVariants());
        addLower(boosters, Rs2Potion.getMagicPotionsVariants());
        addLower(boosters, Rs2Potion.getCombatPotionsVariants());

        Set<String> drunk = new HashSet<>();
        for (Rs2ItemModel potion : Rs2Inventory.getPotions()) {
            String name = potion.getName();
            if (name == null) {
                continue;
            }
            String base = name.replaceAll("\\(\\d+\\)$", "").trim().toLowerCase();

            if (boosters.contains(base) && drunk.add(base)) {
                log.info("[prep] pre-potting {}", name);
                Rs2Inventory.interact(potion, "Drink");
                sleep(600, 1000);
            }
        }
        if (drunk.isEmpty()) {
            log.info("[prep] no stat-boosting potions in the inventory to pre-pot");
        }
        return true;
    }

    private static void addLower(Set<String> target, List<String> names) {
        for (String n : names) {
            if (n != null) {
                target.add(n.toLowerCase());
            }
        }
    }

    private boolean ensureMagicEquipment(FightContext ctx) {
        Rs2InventorySetup magic = ctx.getMagicSetup();
        if (magic == null) {
            log.warn("[prep] no magic inventory setup configured");
            return false;
        }
        if (magic.doesEquipmentMatch()) {
            return true;
        }

        log.info("[prep] re-equipping the magic gear (from inventory) before boarding the boat");
        magic.wearEquipment();
        if (!sleepUntil(magic::doesEquipmentMatch, STEP_TIMEOUT_MS)) {
            log.warn("[prep] magic equipment not fully on before boarding the boat");
            return false;
        }
        return true;
    }

    private boolean prayAtAltar() {
        if (Rs2Player.getBoostedSkillLevel(Skill.PRAYER) >= Rs2Player.getRealSkillLevel(Skill.PRAYER)) {
            return true;
        }

        log.info("[prep] restoring prayer at the altar");
        if (!interactObject(ALTAR_NAME, "Pray")) {
            return false;
        }
        sleepUntil(() -> Rs2Player.getBoostedSkillLevel(Skill.PRAYER) >= Rs2Player.getRealSkillLevel(Skill.PRAYER),
                STEP_TIMEOUT_MS);
        return true;
    }

    private boolean doBankStep(FightContext ctx) {
        log.info("[prep] banking at the Grand Exchange");
        Rs2Bank.walkToBankAndUseBank(BankLocation.GRAND_EXCHANGE);
        if (!sleepUntil(Rs2Bank::isOpen, STEP_TIMEOUT_MS)) {
            log.warn("[prep] could not open the GE bank");
            return false;
        }

        Rs2InventorySetup magic = ctx.getMagicSetup();
        if (magic == null) {
            log.warn("[prep] no magic inventory setup configured");
            return false;
        }

        magic.wearEquipment();
        sleepUntil(magic::doesEquipmentMatch, 3_000);
        if (!magic.doesEquipmentMatch()) {
            magic.loadEquipment();
        }
        if (!sleepUntil(magic::doesEquipmentMatch, STEP_TIMEOUT_MS)) {
            log.warn("[prep] couldn't fully equip the magic gear before banking");
            return false;
        }

        Map<Integer, Integer> desired = new LinkedHashMap<>();
        for (InventorySetupsItem item : magic.getInventoryItems()) {
            if (item == null || item.getId() <= 0 || isRuneLike(item.getName())) {
                continue;
            }
            desired.merge(item.getId(), Math.max(1, item.getQuantity()), Integer::sum);
        }

        Rs2Bank.depositAllExcept(item -> keepDuringRestock(item, desired));
        sleep(400, 800);

        if (Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS) < Rs2Player.getRealSkillLevel(Skill.HITPOINTS)) {
            eatLobsterToFullHp(desired);
        }

        getItemsFromBank(desired);
        Rs2Bank.closeBank();

        if (!magic.doesEquipmentMatch()) {
            log.warn("[prep] equipment doesn't match the magic setup after restock");
            return false;
        }
        for (Integer id : desired.keySet()) {
            if (!Rs2Inventory.hasItem(id)) {
                log.warn("[prep] restock incomplete — missing item id {} (not enough in the bank?)", id);
                return false;
            }
        }
        return true;
    }

    private static void getItemsFromBank(Map<Integer, Integer> desired) {
        for (Map.Entry<Integer, Integer> want : desired.entrySet()) {
            if (!Rs2Bank.withdrawDeficit(want.getKey(), want.getValue())) {
                log.warn("[prep] could not top up item id {} to {}", want.getKey(), want.getValue());
            }
            Rs2Inventory.waitForInventoryChanges(1800);
        }
    }

    private static void eatLobsterToFullHp(Map<Integer, Integer> desired) {
        log.info("[prep] HP below full — eating lobsters");
        Rs2Bank.withdrawX(LOBSTER_ID, Rs2Random.between(8, 12));
        sleepUntil(() -> Rs2Inventory.hasItem(LOBSTER_ID), 3_000);
        long deadline = System.currentTimeMillis() + STEP_TIMEOUT_MS;
        while (Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS) < Rs2Player.getRealSkillLevel(Skill.HITPOINTS)
                && Rs2Inventory.hasItem(LOBSTER_ID)
                && System.currentTimeMillis() < deadline) {
            Rs2Inventory.interact(LOBSTER_ID, "Eat");
            sleep(1_200, 1_600);
        }
        Rs2Bank.depositAllExcept(item -> keepDuringRestock(item, desired));
        sleep(300, 600);
    }

    private static boolean isRuneLike(String name) {
        return name != null && name.toLowerCase().contains("rune");
    }

    /** Keep (do not deposit) the rune pouch/runes and anything that belongs in the magic setup's inventory. */
    private static boolean keepDuringRestock(Rs2ItemModel item, Map<Integer, Integer> desired) {
        return item != null && (isRuneLike(item.getName()) || desired.containsKey(item.getId()));
    }

    private boolean interactObject(String name, String action) {
        Rs2TileObjectModel obj = Microbot.getRs2TileObjectCache()
                .query()
                .withName(name)
                .nearestOnClientThread();
        if (obj == null) {
            log.warn("[prep] object '{}' not found nearby", name);
            return false;
        }
        if (!Rs2Camera.isTileOnScreen(obj)) {
            Rs2Camera.turnTo(obj);
        }
        return action == null ? obj.click() : obj.click(action);
    }

    private boolean fairyRingToZulAndra() {
        log.info("[prep] equipping Dramen staff and taking the fairy ring to Zul-Andra");
        if (Rs2Inventory.hasItem(DRAMEN_STAFF_ID)) {
            Rs2Inventory.wield(DRAMEN_STAFF_ID);
            sleep(300, 600);
        }

        if (!interactObject(FAIRY_RING_NAME, "Last-destination")) {
            log.warn("[prep] POH fairy ring not usable");
            return false;
        }

        if (!sleepUntil(() -> !PohTeleports.isInHouse(), STEP_TIMEOUT_MS)) {
            log.warn("[prep] fairy ring didn't fire (still in the POH — wrong last destination?)");
            return false;
        }
        sleepUntil(() -> !Rs2Player.isAnimating(), STEP_TIMEOUT_MS);
        sleep(600, 1000);
        return true;
    }

    private boolean walkToDock() {
        log.info("[prep] walking to the dock");
        Rs2Walker.walkTo(ZULANDRA_DOCK);
        return sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo(ZULANDRA_DOCK) <= 4, STEP_TIMEOUT_MS);
    }

    private boolean boardBoat() {
        log.info("[prep] boarding the boat to Zulrah's shrine");
        if (!interactObject(BOAT_NAME, null)) {
            return false;
        }

        if (!Rs2Dialogue.sleepUntilHasDialogueOption("Yes")) {
            log.warn("[prep] boat 'return to the shrine' option never appeared");
            return false;
        }
        Rs2Dialogue.clickOption("Yes");

        sleep(3000, 4200);

        sleepUntil(() -> Rs2Dialogue.isInDialogue() && Rs2Dialogue.hasContinue());
        Rs2Dialogue.clickContinue();
        return true;
    }

    public void teleportToHouse() {
        Rs2Magic.cast(MagicAction.TELEPORT_TO_HOUSE);
        sleepUntil(Rs2Player::isAnimating);
        sleepUntil(() -> !Rs2Player.isAnimating());
        sleep(600, 1200);
        sleepUntil(() -> Microbot.getRs2TileObjectCache().query()
                .where(IEntity::isReachable)
                .where(object -> object.getId() == POH_PORTAL_ID)
                .nearestOnClientThread(40) != null);
    }
}
