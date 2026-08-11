package net.runelite.client.plugins.custom.zulrah.actions;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsItem;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.misc.Rs2Potion;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.poh.PohTeleports;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.shared.LocationService;

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
    private static final String GE_PORTAL_NAME = "Grand Exchange Portal"; // POH portal set to the GE
    private static final int STEPPING_STONE_ID = 10663;    // Zul-Andra stepping stone
    private static final WorldPoint STEPPING_STONE_DEST = new WorldPoint(2150, 3070, 0);
    private static final WorldPoint ZULANDRA_DOCK = new WorldPoint(2212, 3057, 0); // by Priestess Zul-Gwenwynig
    private static final int DRAMEN_STAFF_ID = 772;
    private static final int LOBSTER_ID = 379;

    // Route objects, all found via the tile-object cache by name.
    private static final String ALTAR_NAME = "Altar";              // POH altar, "Pray"
    private static final String FAIRY_RING_NAME = "Fairy ring";     // POH fairy ring, "Last-destination"
    private static final String BOAT_NAME = "Sacrificial boat";     // Zul-Andra shrine boat, default action

    private static final int STEP_TIMEOUT_MS = 20_000;

    // LocationService is a shared @RequiredArgsConstructor/@Singleton whose only dependency (the
    // tile-object cache) is a final @Inject field; Lombok leaves the generated constructor un-annotated,
    // so Guice can't build it directly ("no suitable constructor"). We build it lazily from
    // Microbot.getRs2TileObjectCache() on first use instead. This action needs no injected dependencies,
    // so it's discovered via its default constructor.
    private LocationService locationService;

    private LocationService locationService() {
        if (locationService == null) {
            locationService = new LocationService(Microbot.getRs2TileObjectCache());
        }
        return locationService;
    }

    @Override
    public int order() {
        return 1100; // after LootAction (1000)
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
        // Teleport to the house and restore prayer. Turn every prayer off first so we don't burn prayer
        // points (or sit on the wrong overhead) while teleporting/travelling.
        Rs2Prayer.disableAllPrayers();
        locationService().teleportToHouse();
        if (!PohTeleports.isInHouse()) {
            log.warn("[prep] not in the POH after Teleport to House");
            return false;
        }
        if (!prayAtAltar()) {
            return false;
        }

        // POH portal -> Grand Exchange. Find the portal by name (its id varies with the portal
        // frame/decoration), via the tile-object cache like every other object on the route.
        if (!interactObject(GE_PORTAL_NAME, null)) {
            return false;
        }
        if (!sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo(BankLocation.GRAND_EXCHANGE.getWorldPoint()) < 20, STEP_TIMEOUT_MS)) {
            log.warn("[prep] did not arrive at the Grand Exchange");
            return false;
        }

        // Bank: wear magic gear -> deposit inventory -> heal to full if needed -> load the magic inventory.
        return doBankStep(ctx);
    }

    /** From the house, fairy ring + stepping stone + dock + boat back to Zulrah's shrine. */
    private boolean travelLeg(FightContext ctx) {
        Rs2Prayer.disableAllPrayers();
        locationService().teleportToHouse();
        if (!PohTeleports.isInHouse()) {
            log.warn("[prep] not in the POH before travelling to Zulrah");
            return false;
        }
        // When the banking leg was skipped ("Travel to Zulrah" start) prayer wasn't restored yet, so
        // top it up here while we're at the altar. On a normal full trip the bank leg already did this,
        // so skip it to avoid a redundant altar click.
        if (ctx.isPrepSkipBank() && !prayAtAltar()) {
            return false;
        }
        return fairyRingToZulAndra()
//                && crossSteppingStone()
                && walkToDock()
                && ensureMagicEquipment(ctx)
                && prePot(ctx)
                && boardBoat();
    }

    /**
     * Drink one dose of every stat-boosting potion we're carrying (ranging for the atlatl, magic, any
     * combat booster) right before boarding, so the boosts are fresh going into the fight. We drink
     * straight from the inventory rather than {@link Rs2InventorySetup#prePot()}, which is bank-oriented
     * (it inspects bank items) and finds nothing at the dock — hence the "no additional items to pre-pot".
     * Matches on the base name (dose suffix stripped) against the range/magic/combat variant lists, so it
     * won't touch the prayer restore or antipoison. Best-effort: never blocks boarding.
     */
    private boolean prePot(FightContext ctx) {
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
            // Exact base-name match (not contains) so e.g. "Combat potion" can't also match "Super
            // combat potion"; drink each distinct booster once (one dose gives the full boost).
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

    /**
     * Make sure the magic combat gear is fully on before boarding, so the fight starts correctly. This
     * matters because {@link #fairyRingToZulAndra()} wields the Dramen staff (needed for the fairy ring),
     * which displaces the magic weapon into the inventory — loadEquipment swaps it back. No-op if the
     * gear already matches.
     */
    private boolean ensureMagicEquipment(FightContext ctx) {
        Rs2InventorySetup magic = ctx.getMagicSetup();
        if (magic == null) {
            log.warn("[prep] no magic inventory setup configured");
            return false;
        }
        if (magic.doesEquipmentMatch()) {
            return true;
        }
        // Equip straight from the inventory with wearEquipment() — NOT loadEquipment(), which tries to
        // withdraw from the bank and, with the bank closed at the boat, just spams "missing equipment …
        // (bank closed)" for the staff that's already in our inventory. wearEquipment() wields each
        // setup piece from the inventory, swapping the Dramen staff back to the magic weapon.
        log.info("[prep] re-equipping the magic gear (from inventory) before boarding the boat");
        magic.wearEquipment();
        if (!sleepUntil(magic::doesEquipmentMatch, STEP_TIMEOUT_MS)) {
            log.warn("[prep] magic equipment not fully on before boarding the boat");
            return false;
        }
        return true;
    }

    private boolean prayAtAltar() {
        log.info("[prep] restoring prayer at the altar");
        if (!interactObject(ALTAR_NAME, "Pray")) {
            return false;
        }
        // Praying at a POH altar restores prayer to full; wait until it's topped (already-full is instant).
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

        // Switch to the magic combat gear BEFORE depositing. After a mage-phase kill we're wearing the
        // RANGE swap gear with the magic gear sitting in the INVENTORY, so wield it straight from the
        // inventory with wearEquipment() (it uses Rs2Inventory.wield). loadEquipment() on its own is
        // bank-centric — it deposits the inventory and withdraws gear from the bank — so it doesn't
        // re-equip the magic gear that's in our inventory and ends up banking everything, leaving us in
        // range gear. We only fall back to loadEquipment() to pull a piece that genuinely isn't carried.
        magic.wearEquipment();
        sleepUntil(magic::doesEquipmentMatch, 3_000);
        if (!magic.doesEquipmentMatch()) {
            magic.loadEquipment();
        }
        if (!sleepUntil(magic::doesEquipmentMatch, STEP_TIMEOUT_MS)) {
            log.warn("[prep] couldn't fully equip the magic gear before banking");
            return false;
        }

        // Build the desired inventory (id -> quantity) from the magic setup, skipping the rune pouch and
        // any rune rows so the pouch is never touched or reconciled. Everything else — the carried range
        // gear, ring of recoil, Dramen staff, potions and food — is what we want to keep/top up.
        Map<Integer, Integer> desired = new LinkedHashMap<>();
        for (InventorySetupsItem item : magic.getInventoryItems()) {
            if (item == null || item.getId() <= 0 || isRuneLike(item.getName())) {
                continue;
            }
            desired.merge(item.getId(), Math.max(1, item.getQuantity()), Integer::sum);
        }

        // Deposit only what does NOT belong in the inventory setup: loot, and part-used potions (a
        // prayer potion(3) has a different id than the setup's (4), so it's banked and replaced). Anything
        // matching the setup exactly — the swap gear, recoil, Dramen staff, full potions, remaining
        // karambwans — and the rune pouch/runes are kept. Worn gear is untouched by deposit.
        Rs2Bank.depositAllExcept(item -> item != null
                && (isRuneLike(item.getName()) || desired.containsKey(item.getId())));
        sleep(400, 800);

        // If hurt, eat lobsters to full, then bank the leftovers (keeping setup items + the rune pouch).
        if (Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS) < Rs2Player.getRealSkillLevel(Skill.HITPOINTS)) {
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
            Rs2Bank.depositAllExcept(item -> item != null
                    && (isRuneLike(item.getName()) || desired.containsKey(item.getId()))); // leftover lobsters
            sleep(300, 600);
        }

        // Top up each setup item to its target with withdrawDeficit (withdraws target - current). Items we
        // kept in full withdraw nothing; partly-consumed karambwans pull just enough to reach 16; a potion
        // that was banked because it was part-used is replaced with a fresh one.
        for (Map.Entry<Integer, Integer> want : desired.entrySet()) {
            if (!Rs2Bank.withdrawDeficit(want.getKey(), want.getValue())) {
                log.warn("[prep] could not top up item id {} to {}", want.getKey(), want.getValue());
            }
            Rs2Inventory.waitForInventoryChanges(1800);
        }
        Rs2Bank.closeBank();

        // Verify the gear is on and every non-rune setup item is present. We deliberately do NOT call
        // doesInventoryMatch() — it would fail on the rune pouch we purposely left alone.
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

    /** Rune pouch / runes are matched by name so we never deposit, withdraw or reconcile the pouch. */
    private static boolean isRuneLike(String name) {
        return name != null && name.toLowerCase().contains("rune");
    }

    /**
     * Finds the nearest matching object via the tile-object cache and clicks it, using {@code action}
     * (or the default left-click when {@code action} is null). Returns false if nothing matched.
     */
    private boolean interactObject(String name, String action) {
        Rs2TileObjectModel obj = Microbot.getRs2TileObjectCache()
                .query()
                .withName(name)
                .nearestOnClientThread();
        if (obj == null) {
            log.warn("[prep] object '{}' not found nearby", name);
            return false;
        }
        // Face the object before clicking (only when it's off-screen) so the click reliably lands.
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
        // One-click to the last destination (Zul-Andra), matching the manual routine. Relies on
        // last-used being Zul-Andra.
        if (!interactObject(FAIRY_RING_NAME, "Last-destination")) {
            log.warn("[prep] POH fairy ring not usable");
            return false;
        }
        // Wait for the teleport to actually LAND before returning. Otherwise the next step's walk is
        // issued while we're still in the POH / mid-teleport, so the click lands nowhere and nothing
        // re-issues it once we arrive — the character just stands at the landing spot.
        if (!sleepUntil(() -> !PohTeleports.isInHouse(), STEP_TIMEOUT_MS)) {
            log.warn("[prep] fairy ring didn't fire (still in the POH — wrong last destination?)");
            return false;
        }
        sleepUntil(() -> !Rs2Player.isAnimating(), STEP_TIMEOUT_MS); // let the teleport animation finish
        sleep(600, 1000);                                           // let the Zul-Andra region settle
        return true;
    }

    private boolean crossSteppingStone() {
        log.info("[prep] crossing the stepping stone");
        if (!sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo(STEPPING_STONE_DEST) <= 2, STEP_TIMEOUT_MS)) {
            log.warn("[prep] did not cross the stepping stone (Agility requirement?)");
            return false;
        }
        Microbot.getRs2TileObjectCache().query().withName("Stepping stone").nearestOnClientThread().click();
        sleepUntil(() -> !Rs2Player.isAnimating());
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
        // Clicking the boat first opens a "Do you want to return to the shrine?" option dialogue — pick
        // "Yes" to actually set sail.
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
}
