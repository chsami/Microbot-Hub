package net.runelite.client.plugins.microbot.dailytasks;

import lombok.Getter;
import net.runelite.api.ItemID;
import net.runelite.api.MenuAction;
import net.runelite.api.ObjectComposition;
import net.runelite.api.VarPlayer;
import net.runelite.api.Varbits;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.*;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import static net.runelite.client.plugins.microbot.Microbot.doInvoke;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntilOnClientThread;


public enum DailyTask {

    HERB_BOXES(
            "Herb Boxes",
            new WorldPoint(2608, 3114, 0),
            () -> Microbot.getClient().getVarbitValue(VarbitID.IRONMAN) == 0
                    && Microbot.getClient().getVarpValue(VarPlayerID.NZONE_REWARDPOINTS) >= 9500
                    && Microbot.getClient().getVarbitValue(VarbitID.NZONE_HERBBOXES_PURCHASED) < 15,
            () -> {
                Microbot.getRs2TileObjectCache().query().interact(26273, "Search");
                sleepUntil(() -> Rs2Widget.findWidget("Dom Onion") != null);
                doInvoke(new NewMenuEntry("Buy-50", "Herb box", 4, MenuAction.CC_OP, 20, 13500420, false), new Rectangle(1, 1));
                Rs2Inventory.waitForInventoryChanges(1000);
                Rs2Inventory.interact("Herb box", "Bank-all");
                sleepUntil(() -> !Rs2Inventory.hasItem("Herb box"), 20000);

            },
            DailyTasksConfig::collectHerbBoxes
    ),

    BATTLESTAVES(
            "Battlestaves",
            new WorldPoint(3201, 3436, 0),
            () -> Microbot.getClient().getVarbitValue(Varbits.DIARY_VARROCK_EASY) == 1
                    && Microbot.getClient().getVarbitValue(Varbits.DAILY_STAVES_COLLECTED) == 0,
            () -> {
                Microbot.getRs2TileObjectCache().query().interact(30357);
                sleepUntil(() -> Rs2Widget.findWidget("discounted battlestaves") != null);
                Rs2Widget.clickWidget("Click here to continue");
                sleepUntil(() -> Rs2Widget.findWidget("Yes") != null);
                Rs2Widget.clickWidget("Yes");
                Rs2Inventory.waitForInventoryChanges(1000);
            },
            DailyTasksConfig::collectStaves
    ),

    PURE_ESSENCE(
            "Pure Essence",
            new WorldPoint(2684, 3323, 0),
            () -> Microbot.getClient().getVarbitValue(Varbits.DIARY_ARDOUGNE_MEDIUM) == 1
                    && Microbot.getClient().getVarbitValue(Varbits.DAILY_ESSENCE_COLLECTED) == 0,
            () -> {
                Microbot.getRs2NpcCache().query().withId(8481).interact("Claim");
                Rs2Inventory.waitForInventoryChanges(1000);
            },
            DailyTasksConfig::collectEssence
    ),

//    FREE_RUNES(
//            "Free Runes",
//            new WorldPoint(3253, 3401, 0),
//            () -> Microbot.getClient().getVarbitValue(Varbits.DIARY_WILDERNESS_EASY) == 1
//                    && Microbot.getClient().getVarbitValue(Varbits.DAILY_RUNES_COLLECTED) == 0,
//            () -> {
//            },
//            DailyTasksConfig::collectRunes,
//            List.of()
//    ),

    FLAX(
            "Flax",
            new WorldPoint(2738, 3444, 0),
            () -> Microbot.getClient().getVarbitValue(Varbits.DIARY_KANDARIN_EASY) == 1
                    && Microbot.getClient().getVarbitValue(Varbits.DAILY_FLAX_STATE) == 0,
            () -> {
                Microbot.getRs2NpcCache().query().withId(5522).interact("Exchange");
                sleepUntil(Rs2Dialogue::isInDialogue);
                Rs2Dialogue.clickOption("Agree");
                Rs2Inventory.waitForInventoryChanges(1000);
            },
            DailyTasksConfig::collectFlax
    ),

//    BONEMEAL(
//            "Bonemeal and Slime",
//            new WorldPoint(3442, 3489, 0),
//            () -> {
//                if (Microbot.getClient().getVarbitValue(Varbits.DIARY_MORYTANIA_MEDIUM) != 1) {
//                    return false;
//                }
//                int collected = Microbot.getClient().getVarbitValue(Varbits.DAILY_BONEMEAL_STATE);
//                int max = 13;
//                if (Microbot.getClient().getVarbitValue(Varbits.DIARY_MORYTANIA_HARD) == 1) {
//                    max += 13;
//                    if (Microbot.getClient().getVarbitValue(Varbits.DIARY_MORYTANIA_ELITE) == 1) {
//                        max += 13;
//                    }
//                }
//                return collected < max;
//            },
//            () -> {
//            },
//            DailyTasksConfig::collectBonemeal,
//            List.of()
//    ),
//
//    DYNAMITE(
//            "Dynamite",
//            new WorldPoint(1630, 3742, 0),
//            () -> Microbot.getClient().getVarbitValue(Varbits.DIARY_KOUREND_MEDIUM) == 1
//                    && Microbot.getClient().getVarbitValue(Varbits.DAILY_DYNAMITE_COLLECTED) == 0,
//            () -> {
//            },
//            DailyTasksConfig::collectDynamite,
//            List.of()
//    ),

    MISCELLANIA(
            "Miscellania",
            new WorldPoint(2532, 3863, 0),
            () -> Microbot.getClient().getVarpValue(VarPlayer.THRONE_OF_MISCELLANIA) > 0,
            () -> {
                sleepUntil(() -> Rs2Player.getWorldLocation().getRegionID() == 10044, 10000);
                Microbot.getRs2TileObjectCache().query().interact(15079);
                sleepUntilOnClientThread(() -> Microbot.getClient().getVarbitValue(Varbits.KINGDOM_APPROVAL) == 127, 10000);
                Rs2Walker.walkTo(new WorldPoint(2502, 3858, 1), 5);
                Microbot.getRs2NpcCache().query().withId(5448).interact("Collect");
                sleepUntil(() -> Rs2Dialogue.hasDialogueOption("Collect resources"));
                Rs2Dialogue.clickOption("Collect resources");
                sleepUntil(() -> Rs2Widget.findWidget("Resources Collected") != null);
                var closeBtn = Rs2Widget.findWidget(537, null);
                Rs2Widget.clickWidget(closeBtn);
                sleepUntil(Rs2Dialogue::isInDialogue);
                Rs2Dialogue.clickContinue();
                sleepUntil(() -> Rs2Dialogue.hasDialogueText("distributing their effort"));
                Rs2Dialogue.clickContinue();
                sleepUntil(() -> !Rs2Dialogue.isInDialogue());
                var depositBtn = Rs2Widget.findWidget("Deposit");
                Rs2Widget.clickWidget(depositBtn);
                sleepUntil(() -> Rs2Widget.findWidget("Click here to continue") != null);
                Rs2Widget.clickWidget("Click here to continue");
                sleepUntil(() -> Rs2Widget.findWidget("Enter amount") != null);
                Rs2Keyboard.typeString(String.valueOf(50000));
                Rs2Keyboard.enter();
            },
            config -> false
    ),

    RAKE_MISCELLANIA(
            "Rake Miscellania",
            new WorldPoint(2527, 3851, 0),
            // Gate only on the quest being done — NOT on KINGDOM_APPROVAL, which can read a stale
            // "maxed" value on login. We travel and try to rake regardless, deciding we are finished
            // from the live on-island signals instead.
            () -> Microbot.getClient().getVarpValue(VarPlayer.THRONE_OF_MISCELLANIA) > 0,
            DailyTask::rakeMiscellania,
            DailyTasksConfig::rakeMiscellania,
            true
    );

    @Getter
    private final String name;
    @Getter
    private final WorldPoint location;
    private final BooleanSupplier isAvailable;
    private final Runnable executeTask;
    @Getter
    private final Function<DailyTasksConfig, Boolean> configEnabled;
    private final boolean handlesOwnTravel;

    DailyTask(String name, WorldPoint location, BooleanSupplier isAvailable,
              Runnable executeTask, Function<DailyTasksConfig, Boolean> configEnabled) {
        this(name, location, isAvailable, executeTask, configEnabled, false);
    }

    DailyTask(String name, WorldPoint location, BooleanSupplier isAvailable,
              Runnable executeTask, Function<DailyTasksConfig, Boolean> configEnabled, boolean handlesOwnTravel) {
        this.name = name;
        this.location = location;
        this.isAvailable = isAvailable;
        this.executeTask = executeTask;
        this.configEnabled = configEnabled;
        this.handlesOwnTravel = handlesOwnTravel;
    }

    /** When true, the script skips its generic pre-walk and the task navigates itself (e.g. bank then fairy ring). */
    public boolean handlesOwnTravel() {
        return handlesOwnTravel;
    }

    public boolean isAvailable() {

        return isAvailable.getAsBoolean();
    }


    public void execute() {
        try {
            executeTask.run();
        } catch (Exception e) {
            Thread.currentThread().interrupt();
        }
    }

    public boolean isEnabled(DailyTasksConfig config) {
        return configEnabled.apply(config);
    }

    public boolean isInRange() {
        return Rs2Player.distanceTo(location) < 20;
    }

    // ---- Rake Miscellania ---------------------------------------------------------------------

    /**
     * Backup max-favour signal, set by {@link DailyTasksPlugin}'s chat subscriber when Gardener
     * Gunnhild tells us to stop. The KINGDOM_APPROVAL varbit ({@link #MAX_APPROVAL}) is authoritative.
     */
    public static volatile boolean gunnhildMaxFavor = false;

    private static final WorldPoint MISCELLANIA_PATCHES = new WorldPoint(2527, 3851, 0);
    private static final int MISCELLANIA_REGION = 10044;
    private static final int MAX_APPROVAL = 127;

    /** KINGDOM_APPROVAL at max, via the event-maintained player-state cache (thread-safe, no spurious 0). */
    private static boolean approvalAtMax() {
        return Microbot.getVarbitValue(Varbits.KINGDOM_APPROVAL) >= MAX_APPROVAL;
    }

    private static boolean hasRakeAction(Rs2TileObjectModel object) {
        if (object == null) return false;
        ObjectComposition comp = object.getObjectComposition();
        if (comp == null || comp.getActions() == null) return false;
        for (String action : comp.getActions()) {
            if (action != null && action.toLowerCase().contains("rake")) return true;
        }
        return false;
    }

    /** Nearest patch around the field that still offers a "Rake" action (name-agnostic, weed-stage proof). */
    private static Rs2TileObjectModel rakeablePatch() {
        return Microbot.getRs2TileObjectCache().query()
                .within(MISCELLANIA_PATCHES, 12)
                .where(DailyTask::hasRakeAction)
                .nearest(MISCELLANIA_PATCHES, 12);
    }

    private static void rakeMiscellania() {
        gunnhildMaxFavor = false;

        // Fairy-ring access needs a Dramen or Lunar staff equipped — UNLESS the Elite Lumbridge &
        // Draynor Diary is complete, which removes the staff requirement entirely. (Varbit read must
        // run on the client thread.)
        boolean fairyRingItemless = Microbot.getClientThread()
                .runOnClientThreadOptional(() -> Microbot.getClient().getVarbitValue(Varbits.DIARY_LUMBRIDGE_ELITE) == 1)
                .orElse(false);
        boolean fairyStaffEquipped = Rs2Equipment.isWearing(ItemID.DRAMEN_STAFF) || Rs2Equipment.isWearing(ItemID.LUNAR_STAFF);
        boolean fairyStaffInInv = Rs2Inventory.hasItem(ItemID.DRAMEN_STAFF) || Rs2Inventory.hasItem(ItemID.LUNAR_STAFF);

        // 1. Ensure a rake (and a fairy-ring staff only if one is actually needed) — bank when something is missing.
        boolean needRake = !Rs2Inventory.hasItem(ItemID.RAKE);
        boolean needStaff = !fairyRingItemless && !fairyStaffEquipped && !fairyStaffInInv;
        if (needRake || needStaff) {
            if (!Rs2Bank.walkToBankAndUseBank()) return;
            sleepUntil(Rs2Bank::isOpen, 10000);
            if (needRake) Rs2Bank.withdrawOne(ItemID.RAKE);
            if (needStaff) {
                Rs2Bank.withdrawOne(ItemID.DRAMEN_STAFF);
                sleepUntil(() -> Rs2Inventory.hasItem(ItemID.DRAMEN_STAFF), 2000);
                if (!Rs2Inventory.hasItem(ItemID.DRAMEN_STAFF)) Rs2Bank.withdrawOne(ItemID.LUNAR_STAFF);
            }
            sleepUntil(() -> Rs2Inventory.hasItem(ItemID.RAKE), 5000);
            Rs2Bank.closeBank();
        }
        if (!Rs2Inventory.hasItem(ItemID.RAKE)) {
            Microbot.log("Rake Miscellania: no rake in inventory or bank — skipping.");
            return;
        }

        // 2. Ensure fairy-ring access: equip a Dramen/Lunar staff if one is needed and not already worn.
        if (!fairyRingItemless && !Rs2Equipment.isWearing(ItemID.DRAMEN_STAFF) && !Rs2Equipment.isWearing(ItemID.LUNAR_STAFF)) {
            if (Rs2Inventory.hasItem(ItemID.DRAMEN_STAFF)) {
                Rs2Inventory.equip(ItemID.DRAMEN_STAFF);
            } else if (Rs2Inventory.hasItem(ItemID.LUNAR_STAFF)) {
                Rs2Inventory.equip(ItemID.LUNAR_STAFF);
            } else {
                Microbot.log("Rake Miscellania: no Dramen/Lunar staff and no Elite Lumbridge diary — cannot reach Miscellania, skipping.");
                return;
            }
            sleepUntil(() -> Rs2Equipment.isWearing(ItemID.DRAMEN_STAFF) || Rs2Equipment.isWearing(ItemID.LUNAR_STAFF), 3000);
        }

        // 3. Travel to the patches; the walker routes through the CIP fairy ring (staff equipped, or diary done).
        if (Rs2Player.getWorldLocation().getRegionID() != MISCELLANIA_REGION) {
            Rs2Walker.walkTo(MISCELLANIA_PATCHES, 5);
            sleepUntil(() -> Rs2Player.getWorldLocation().getRegionID() == MISCELLANIA_REGION, 60000);
        }
        if (Rs2Player.getWorldLocation().getRegionID() != MISCELLANIA_REGION) {
            Microbot.log("Rake Miscellania: failed to reach Miscellania — skipping.");
            return;
        }
        if (Rs2Player.distanceTo(MISCELLANIA_PATCHES) > 6) Rs2Walker.walkTo(MISCELLANIA_PATCHES, 3);

        // 4. Rake every weed on both patches, then decide whether we are finished. We never stop part-way:
        //    while any patch still offers a "Rake" action we keep raking (waitForAnimation blocks until each
        //    rake finishes, so we never spam-click a patch we are already raking). Only once nothing is left
        //    to rake do we check whether favour is maxed — Gardener Gunnhild's "stop" line (chat subscriber)
        //    or the KINGDOM_APPROVAL varbit. If neither, the patches simply have not regrown yet, so we wait
        //    for them and keep raking. Because we always clear the weeds first, a stale login-time approval
        //    value can never make us skip real raking.
        while (Microbot.isLoggedIn() && Microbot.isPluginEnabled(DailyTasksPlugin.class)) {
            if (gunnhildMaxFavor) break;                       // Gunnhild told us to stop = definitively maxed

            Rs2TileObjectModel patch = rakeablePatch();
            if (patch != null) {
                patch.click("Rake");
                Rs2Player.waitForAnimation(3000);             // rake to completion; never re-click mid-rake
                continue;
            }

            // Nothing left to rake right now.
            if (approvalAtMax()) break;                        // raked everything available and favour is maxed
            // Not maxed yet — wait (event-derived) for the weeds to regrow, then keep raking.
            sleepUntil(() -> gunnhildMaxFavor || rakeablePatch() != null || approvalAtMax(), 70000);
        }
    }
}