package net.runelite.client.plugins.microbot.pestcontrol;

import com.google.common.collect.ImmutableSet;
import net.runelite.api.ObjectID;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsItem;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.pestcontrol.Portal;

import javax.inject.Inject;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer.isQuickPrayerEnabled;
import static net.runelite.client.plugins.pestcontrol.Portal.*;

public class PestControlScript extends Script {

    boolean initialise = true;
    boolean walkToCenter = false;
    private boolean wasInPestControl = false;
    PestControlConfig config;
    private final PestControlPlugin plugin;

    @Inject
    public PestControlScript(PestControlPlugin plugin, PestControlConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    /** The Void Knight NPC ID — the bot stays near this NPC during the game. */
    private static final int VOID_KNIGHT_ID = 2950;

    /** Defiler variant IDs to attack. */
    private static final Set<Integer> DEFILER_IDS = ImmutableSet.of(1701, 1702, 1703);

    /** Max tiles from the Void Knight the bot is allowed to wander. */
    private static final int STAY_NEAR_DISTANCE = 7;

    public static final boolean DEBUG = false;

    // Kept so PestControlOverlay still compiles (it references portals for debug display)
    public static List<Portal> portals = List.of(PURPLE, BLUE, RED, YELLOW);

    private void resetPortals() {
        for (Portal portal : portals) {
            portal.setHasShield(true);
        }
    }

    private static WorldPoint stepTowards(WorldPoint from, WorldPoint to, int maxStep) {
        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();
        int chebyshev = Math.max(Math.abs(dx), Math.abs(dy));
        if (chebyshev <= maxStep) return to;
        double scale = (double) maxStep / chebyshev;
        return new WorldPoint(
                from.getX() + (int) Math.round(dx * scale),
                from.getY() + (int) Math.round(dy * scale),
                from.getPlane()
        );
    }

    public boolean run(PestControlConfig config) {
        this.config = config;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                final boolean isInPestControl = isInPestControl();
                final boolean isInBoat = isInBoat();
                System.out.println("Initialise: " + initialise);
                System.out.println("Is in Pest Control: " + isInPestControl);
                System.out.println("Is in Boat: " + isInBoat);

                // ── Initialisation ────────────────────────────────────────────
                if (initialise && !isInPestControl && !isInBoat) {
                    Microbot.log("Initialising");
                    if (Rs2Player.getWorld() != config.world()) {
                        Microbot.hopToWorld(config.world());
                        sleep(1000, 3000);
                        Microbot.hopToWorld(config.world());
                        sleepUntil(() -> Rs2Player.getWorld() == config.world(), 7000);
                    }
                    if (Rs2Player.getWorldLocation().getRegionID() == 10537
                            && Rs2Player.getWorld() == config.world()) {
                        if (handleInventorySetup()) {
                            initialise = false;
                        }
                    } else {
                        Microbot.log("Traveling to Pest Island");
                        Rs2Walker.walkTo(new WorldPoint(2667, 2653, 0));
                    }
                }

                // ── Inside the minigame ───────────────────────────────────────
                if (isInPestControl) {
                    initialise = false;
                    wasInPestControl = true;

                    // Quick prayer
                    if (!isQuickPrayerEnabled()
                            && Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER) != 0
                            && config.quickPrayer()) {
                        final Widget prayerOrb = Rs2Widget.getWidget(ComponentID.MINIMAP_QUICK_PRAYER_ORB);
                        if (prayerOrb != null) {
                            Microbot.getMouse().click(prayerOrb.getCanvasLocation());
                            sleep(1000, 1500);
                        }
                    }

                    // Walk to Void Knight on first entry
                    if (!walkToCenter) {
                        WorldPoint knightPos = getVoidKnightPosition();
                        WorldPoint playerPos = Rs2Player.getWorldLocation();
                        if (knightPos == null) {
                            // Knight not visible yet — walk to map centre
                            WorldPoint centre = WorldPoint.fromRegion(
                                    playerPos.getRegionID(), 32, 17, playerPos.getPlane());
                            Rs2Walker.walkMiniMap(stepTowards(playerPos, centre, 14));
                            sleepUntil(() -> !Rs2Player.isMoving(), 4000);
                            return;
                        }
                        if (playerPos.distanceTo(knightPos) <= STAY_NEAR_DISTANCE) {
                            walkToCenter = true;
                        } else {
                            Rs2Walker.walkMiniMap(stepTowards(playerPos, knightPos, 14));
                            sleepUntil(() -> !Rs2Player.isMoving(), 4000);
                            return;
                        }
                    }

                    // Special attack
                    Rs2Combat.setSpecState(true, config.specialAttackPercentage() * 10);

                    // Drift check — return to Void Knight if too far
                    WorldPoint knightPos = getVoidKnightPosition();
                    if (knightPos != null) {
                        WorldPoint playerPos = Rs2Player.getWorldLocation();
                        if (playerPos.distanceTo(knightPos) > STAY_NEAR_DISTANCE) {
                            Microbot.log("Drifted too far — moving back to Void Knight");
                            Rs2Walker.walkMiniMap(stepTowards(playerPos, knightPos, 14));
                            sleepUntil(() -> !Rs2Player.isMoving(), 4000);
                            return;
                        }
                    }

                    // Attack a Defiler if not already in combat
                    if (!Microbot.getClient().getLocalPlayer().isInteracting()) {
                        if (!attackDefiler()) {
                            Microbot.log("No Defilers nearby — waiting");
                        }
                    }

                // ── Outside the game: board boat or wait ──────────────────────
                } else {
                    if (wasInPestControl) {
                        Rs2Walker.setTarget(null);
                        wasInPestControl = false;
                    }
                    resetPortals();
                    walkToCenter = false;
                    if (!isInBoat && !initialise) {
                        if (Microbot.getClient().getLocalPlayer().getCombatLevel() >= 100) {
                            Microbot.getRs2TileObjectCache().query().interact(ObjectID.GANGPLANK_25632);
                        } else if (Microbot.getClient().getLocalPlayer().getCombatLevel() >= 70) {
                            Microbot.getRs2TileObjectCache().query().interact(ObjectID.GANGPLANK_25631);
                        } else {
                            Microbot.getRs2TileObjectCache().query().interact(ObjectID.GANGPLANK_14315);
                        }
                        sleepUntil(this::isInBoat, 3000);
                    } else {
                        if (config.alchInBoat() && !config.alchItem().equalsIgnoreCase("")) {
                            Rs2Magic.alch(config.alchItem());
                            sleep(Rs2Random.between(1600, 1800));
                        }
                    }
                }

            } catch (Exception ex) {
                ex.printStackTrace();
                Microbot.log(ex.getMessage());
            }
        }, 0, 300, TimeUnit.MILLISECONDS);
        return true;
    }

    /**
     * Returns the Void Knight's world position, or null if not found.
     */
    private WorldPoint getVoidKnightPosition() {
        Rs2NpcModel voidKnight = Microbot.getRs2NpcCache().query()
                .withId(VOID_KNIGHT_ID)
                .nearestOnClientThread();
        if (voidKnight == null) return null;
        return Microbot.getClientThread().runOnClientThreadOptional(
                () -> voidKnight.getNpc().getWorldLocation()).orElse(null);
    }

    /**
     * Attacks the nearest Defiler (IDs 1701, 1702, 1703) that is close to the
     * Void Knight. Returns true if an attack was successfully started.
     */
    private boolean attackDefiler() {
        WorldPoint knightPos = getVoidKnightPosition();

        for (int defilerID : DEFILER_IDS) {
            Rs2NpcModel defiler = Microbot.getRs2NpcCache().query()
                    .withId(defilerID)
                    .where(n -> n.getNpc() != null && !n.getNpc().isDead())
                    .nearestOnClientThread();

            if (defiler == null) continue;

            // Only attack Defilers within double the stay-near radius of the Knight
            if (knightPos != null) {
                WorldPoint defilerPos = Microbot.getClientThread().runOnClientThreadOptional(
                        () -> defiler.getNpc().getWorldLocation()).orElse(null);
                if (defilerPos != null && defilerPos.distanceTo(knightPos) > STAY_NEAR_DISTANCE * 2) {
                    continue;
                }
            }

            if (defiler.click("Attack")) {
                Microbot.log("Attacking Defiler (ID " + defilerID + ")");
                sleepUntil(() -> !Microbot.getClient().getLocalPlayer().isInteracting(), 5000);
                return true;
            }
        }
        return false;
    }

    /**
     * Handles the inventory setup based on the provided configuration.
     * Returns true when no setup work is needed or setup was completed.
     */
    private boolean handleInventorySetup() {
        InventorySetup setup = config.inventorySetup();
        if (setup == null || isEmptySetup(setup)) return true;

        if (!Rs2Bank.isOpen()) {
            Rs2Bank.openBank();
            sleepUntil(Rs2Bank::isOpen, 3000);
            if (!Rs2Bank.isOpen()) return false;
        }

        Microbot.log("Starting Inv Setup");
        var inventorySetup = new Rs2InventorySetup(setup, mainScheduledFuture);

        if (inventorySetup.doesInventoryMatch() && inventorySetup.doesEquipmentMatch()) return true;
        if (!inventorySetup.loadEquipment() || !inventorySetup.loadInventory()) return false;

        Microbot.log("Inv Setup Finished");
        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 2000);
        return true;
    }

    private static boolean isEmptySetup(InventorySetup setup) {
        return isAllDummy(setup.getInventory()) && isAllDummy(setup.getEquipment());
    }

    private static boolean isAllDummy(List<InventorySetupsItem> items) {
        return items == null || items.stream().allMatch(
                item -> item == null || InventorySetupsItem.itemIsDummy(item));
    }

    public boolean isOutside() {
        WorldPoint playerLoc = Microbot.getClientThread().invoke(
                () -> Microbot.getClient().getLocalPlayer().getWorldLocation());
        return playerLoc.distanceTo(new WorldPoint(2644, 2644, 0)) < 20;
    }

    public boolean isInBoat() {
        return Microbot.getClientThread().runOnClientThreadOptional(
                () -> Microbot.getClient().getWidget(WidgetInfo.PEST_CONTROL_BOAT_INFO) != null
        ).orElse(false);
    }

    public boolean isInPestControl() {
        return Microbot.getClientThread().runOnClientThreadOptional(
                () -> Microbot.getClient().getWidget(WidgetInfo.PEST_CONTROL_BLUE_SHIELD) != null
        ).orElse(false);
    }

    public void exitBoat() {
        if (Microbot.getClient().getLocalPlayer().getCombatLevel() >= 100) {
            Microbot.getRs2TileObjectCache().query().interact(ObjectID.LADDER_25630);
        } else if (Microbot.getClient().getLocalPlayer().getCombatLevel() >= 70) {
            Microbot.getRs2TileObjectCache().query().interact(ObjectID.LADDER_25629);
        } else {
            Microbot.getRs2TileObjectCache().query().interact(ObjectID.LADDER_14314);
        }
        sleepUntil(() -> Microbot.getClient().getWidget(WidgetInfo.PEST_CONTROL_BOAT_INFO) == null, 3000);
    }

    @Override
    public void shutdown() {
        Microbot.log("Pest control about to shutdown");
        initialise = true;
        walkToCenter = false;
        wasInPestControl = false;
        super.shutdown();
    }
}
