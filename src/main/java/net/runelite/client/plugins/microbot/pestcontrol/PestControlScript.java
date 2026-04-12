package net.runelite.client.plugins.microbot.pestcontrol;

import com.google.common.collect.ImmutableSet;
import net.runelite.api.NPCComposition;
import net.runelite.api.NpcID;
import net.runelite.api.ObjectID;
import net.runelite.api.Skill;
import net.runelite.api.coords.LocalPoint;
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
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.pestcontrol.Portal;
import org.apache.commons.lang3.tuple.Pair;

import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer.isQuickPrayerEnabled;
import static net.runelite.client.plugins.microbot.util.walker.Rs2Walker.distanceToRegion;
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


    private static final Set<Integer> SPINNER_IDS = ImmutableSet.of(
            NpcID.SPINNER,
            NpcID.SPINNER_1710,
            NpcID.SPINNER_1711,
            NpcID.SPINNER_1712,
            NpcID.SPINNER_1713
    );

    private static final Set<Integer> BRAWLER_IDS = ImmutableSet.of(
            NpcID.BRAWLER,
            NpcID.BRAWLER_1736,
            NpcID.BRAWLER_1738,
            NpcID.BRAWLER_1737,
            NpcID.BRAWLER_1735
    );

    final int distanceToPortal = 8;
    public static final boolean DEBUG = false;

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
        if (chebyshev <= maxStep) {
            return to;
        }
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


                if (initialise && !isInPestControl && !isInBoat) {
                    Microbot.log("Initialising");
                    if (Rs2Player.getWorld() != config.world()) {
                        Microbot.hopToWorld(config.world());
                        sleep(1000, 3000);
                        Microbot.hopToWorld(config.world());
                        sleepUntil(() -> Rs2Player.getWorld() == config.world(), 7000);
                    }
                    if (Rs2Player.getWorldLocation().getRegionID() == 10537 && Rs2Player.getWorld() == config.world()) {

                        if (handleInventorySetup()) {
                            initialise = false;
                        }

                    } else {
                        Microbot.log("Traveling to Pest Island");
                        Rs2Walker.walkTo(new WorldPoint(2667, 2653, 0));
                    }
                }
                if (isInPestControl) {
                    initialise = false;
                    wasInPestControl = true;
                    if (!isQuickPrayerEnabled() && Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER) != 0 && config.quickPrayer()) {
                        final Widget prayerOrb = Rs2Widget.getWidget(ComponentID.MINIMAP_QUICK_PRAYER_ORB);
                        if (prayerOrb != null) {
                            Microbot.getMouse().click(prayerOrb.getCanvasLocation());
                            sleep(1000, 1500);
                        }
                    }
                    if (!walkToCenter) {
                        WorldPoint playerLoc = Rs2Player.getWorldLocation();
                        WorldPoint worldPoint = WorldPoint.fromRegion(playerLoc.getRegionID(), 32, 17, playerLoc.getPlane());
                        if (playerLoc.distanceTo(worldPoint) <= 4) {
                            walkToCenter = true;
                        } else {
                            Rs2Walker.walkMiniMap(stepTowards(playerLoc, worldPoint, 14));
                            sleepUntil(() -> !Rs2Player.isMoving(), 4000);
                            return;
                        }
                    }

                    Rs2Combat.setSpecState(true, config.specialAttackPercentage() * 10);
                    Widget activity = Rs2Widget.getWidget(26738700); //145 = 100%
                    if (activity != null && activity.getChild(0).getWidth() <= 20 && !Rs2Combat.inCombat()) {
                        Rs2NpcModel attackableNpc = Microbot.getClientThread().invoke(() ->
                                Microbot.getRs2NpcCache().query()
                                        .where(n -> n.getNpc() != null && !n.getNpc().isDead() && n.getNpc().getCombatLevel() > 0)
                                        .nearest());
                        if (attackableNpc != null) attackableNpc.click("Attack");
                        return;
                    }

                    var brawler = Microbot.getRs2NpcCache().query().withName("brawler").nearestOnClientThread();
                    if (brawler != null && brawler.getWorldLocation().distanceTo(Rs2Player.getWorldLocation()) < 3) {
                        brawler.click("Attack");
                        sleepUntil(() -> !Rs2Combat.inCombat());
                        return;
                    }

                    if (Microbot.getClient().getLocalPlayer().isInteracting())
                        return;


                    if (handleAttack(PestControlNpc.BRAWLER, 1)
                            || handleAttack(PestControlNpc.PORTAL, 1)
                            || handleAttack(PestControlNpc.SPINNER, 1)) {
                        return;
                    }

                    if (handleAttack(PestControlNpc.BRAWLER, 2)
                            || handleAttack(PestControlNpc.PORTAL, 2)
                            || handleAttack(PestControlNpc.SPINNER, 2)) {
                        return;
                    }
                    if (handleAttack(PestControlNpc.BRAWLER, 3)
                            || handleAttack(PestControlNpc.PORTAL, 3)
                            || handleAttack(PestControlNpc.SPINNER, 3)) {
                        return;
                    }
                    Rs2NpcModel portal = Microbot.getRs2NpcCache().query()
                            .where(n -> n.getName() != null && n.getName().toLowerCase().contains("portal")
                                    && n.getNpc() != null && !n.getNpc().isDead()
                                    && Arrays.stream(Microbot.getClientThread().runOnClientThreadOptional(() ->
                                        Microbot.getClient().getNpcDefinition(n.getId()).getActions()).orElse(new String[0]))
                                    .anyMatch(a -> a != null && a.equalsIgnoreCase("attack")))
                            .nearest();
                    if (portal != null) {
                        if (portal.click("Attack")) {
                            sleepUntil(() -> !Microbot.getClient().getLocalPlayer().isInteracting());
                        }
                    } else {
                        if (!Microbot.getClient().getLocalPlayer().isInteracting()) {
                            Rs2NpcModel attackableNpc = Microbot.getRs2NpcCache().query()
                                    .where(n -> n.getNpc() != null && !n.getNpc().isDead() && n.getNpc().getCombatLevel() > 0)
                                    .nearestOnClientThread();
                            if (attackableNpc != null) attackableNpc.click("Attack");
                        }
                    }

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
     * Handles the inventory setup based on the provided configuration.
     *
     * @return true when no setup work is needed (no setup configured, already
     *         matches, or successfully loaded); false when loading failed and
     *         the script should retry on the next tick.
     */
    private boolean handleInventorySetup() {

        InventorySetup setup = config.inventorySetup();
        if (setup == null || isEmptySetup(setup)) {
            return true;
        }

        Microbot.log("Starting Inv Setup");
        var inventorySetup = new Rs2InventorySetup(setup, mainScheduledFuture);

        if (inventorySetup.doesInventoryMatch() && inventorySetup.doesEquipmentMatch()) {
            return true;
        }

        if (!inventorySetup.loadEquipment() || !inventorySetup.loadInventory()) {
            return false;
        }

        Microbot.log("Inv Setup Finished");
        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 2000);
        return true;
    }

    private static boolean isEmptySetup(InventorySetup setup) {
        return isAllDummy(setup.getInventory()) && isAllDummy(setup.getEquipment());
    }

    private static boolean isAllDummy(List<InventorySetupsItem> items) {
        return items == null || items.stream().allMatch(item -> item == null || InventorySetupsItem.itemIsDummy(item));
    }


    public boolean isOutside() {
        WorldPoint playerLoc = Microbot.getClientThread().invoke(() -> Microbot.getClient().getLocalPlayer().getWorldLocation());
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

    private boolean handleAttack(PestControlNpc npcType, int priority) {
        if (priority == 1) {
            if (config.Priority1() == npcType) {
                if (npcType == PestControlNpc.BRAWLER) {
                    return attackBrawler();
                } else if (npcType == PestControlNpc.PORTAL) {
                    return attackPortals();
                } else if (npcType == PestControlNpc.SPINNER) {
                    return attackSpinner();
                }
            }
        } else if (priority == 2) {
            if (config.Priority2() == npcType) {
                if (npcType == PestControlNpc.BRAWLER) {
                    return attackBrawler();
                } else if (npcType == PestControlNpc.PORTAL) {
                    return attackPortals();
                } else if (npcType == PestControlNpc.SPINNER) {
                    return attackSpinner();
                }
            }
        } else {
            if (config.Priority2() == npcType) {
                if (npcType == PestControlNpc.BRAWLER) {
                    return attackBrawler();
                } else if (npcType == PestControlNpc.PORTAL) {
                    return attackPortals();
                } else if (npcType == PestControlNpc.SPINNER) {
                    return attackSpinner();
                }
            }
        }

        return false;
    }

    public Portal getClosestAttackablePortal() {
        List<Pair<Portal, Integer>> distancesToPortal = new ArrayList();
        for (Portal portal : portals) {
            if (!portal.isHasShield() && !portal.getHitPoints().getText().trim().equals("0")) {
                distancesToPortal.add(Pair.of(portal, distanceToRegion(portal.getRegionX(), portal.getRegionY())));
            }
        }

        Pair<Portal, Integer> closestPortal = distancesToPortal.stream().min(Map.Entry.comparingByValue()).orElse(null);

        if (closestPortal == null) return null;

        return closestPortal.getKey();
    }

    private static boolean attackPortal() {
        if (!Microbot.getClient().getLocalPlayer().isInteracting()) {
            Rs2NpcModel npcPortal = Microbot.getRs2NpcCache().query().withName("portal").nearestOnClientThread();
            if (npcPortal == null) return false;
            NPCComposition npc = Microbot.getClientThread().runOnClientThreadOptional(() ->
                    Microbot.getClient().getNpcDefinition(npcPortal.getId())).orElse(null);
            if (npc == null) return false;

            if (Arrays.stream(npc.getActions()).anyMatch(x -> x != null && x.equalsIgnoreCase("attack"))) {
                LocalPoint localPoint = npcPortal.getLocalLocation();
                if (localPoint != null && !Rs2Camera.isTileOnScreen(localPoint)) {
                    WorldPoint npcWp = Microbot.getClientThread().runOnClientThreadOptional(() ->
                            npcPortal.getNpc().getWorldLocation()).orElse(null);
                    WorldPoint playerWp = Rs2Player.getWorldLocation();
                    if (npcWp != null && playerWp != null) {
                        int angle = (int) Math.toDegrees(Math.atan2(
                                npcWp.getY() - playerWp.getY(),
                                npcWp.getX() - playerWp.getX()));
                        if (angle < 0) angle += 360;
                        angle = (angle - 90) % 360;
                        if (angle < 0) angle += 360;
                        Rs2Camera.setAngle(angle, 40);
                    }
                }
                return npcPortal.click("Attack");
            } else {
                return false;
            }
        }
        return false;
    }


    private boolean attackPortals() {
        Portal closestAttackablePortal = getClosestAttackablePortal();
        if (closestAttackablePortal == null) return false;
        for (Portal portal : portals) {
            if (!portal.isHasShield() && !portal.getHitPoints().getText().trim().equals("0") && closestAttackablePortal == portal) {
                if (!Rs2Walker.isCloseToRegion(distanceToPortal, portal.getRegionX(), portal.getRegionY())) {
                    Rs2Walker.walkTo(WorldPoint.fromRegion(Rs2Player.getWorldLocation().getRegionID(), portal.getRegionX(), portal.getRegionY(), Microbot.getClient().getTopLevelWorldView().getPlane()), 5);
                    attackPortal();
                } else {
                    attackPortal();
                }
                return true;
            }
        }
        return false;
    }

    private boolean attackSpinner() {
        for (int spinner : SPINNER_IDS) {
            if (Microbot.getRs2NpcCache().query().withId(spinner).interact("Attack")) {
                sleepUntil(() -> !Microbot.getClient().getLocalPlayer().isInteracting());
                return true;
            }
        }
        return false;
    }

    private boolean attackBrawler() {
        for (int brawler : BRAWLER_IDS) {
            if (Microbot.getRs2NpcCache().query().withId(brawler).interact("Attack")) {
                sleepUntil(() -> !Microbot.getClient().getLocalPlayer().isInteracting());
                return true;
            }
        }
        return false;
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
