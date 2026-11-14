package net.runelite.client.plugins.microbot.droefficientwalker;

import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.boat.data.Heading;
import net.runelite.client.plugins.microbot.api.boat.models.Rs2BoatModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.droefficientwalker.sailing.core.*;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.droefficientwalker.compat.SceneObjects;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.Rectangle;
import java.awt.geom.Ellipse2D;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Client-specific I/O; no path search, bank travel, or sleeps on the client thread. */
final class MicrobotSailingFacade {
    static final int SUMMON_BOAT_TABLET = 31441; // Summon boat, NOT Teleport to boat (31443).
    static final int WORLD_MAP_ROOT = 38993927; // Same world-map widget used by maintained Efficient Walker.
    private static final int MOVE_MASK = CollisionDataFlag.BLOCK_MOVEMENT_FULL
            | CollisionDataFlag.BLOCK_MOVEMENT_NORTH_WEST | CollisionDataFlag.BLOCK_MOVEMENT_NORTH
            | CollisionDataFlag.BLOCK_MOVEMENT_NORTH_EAST | CollisionDataFlag.BLOCK_MOVEMENT_EAST
            | CollisionDataFlag.BLOCK_MOVEMENT_SOUTH_EAST | CollisionDataFlag.BLOCK_MOVEMENT_SOUTH
            | CollisionDataFlag.BLOCK_MOVEMENT_SOUTH_WEST | CollisionDataFlag.BLOCK_MOVEMENT_WEST
            | CollisionDataFlag.BLOCK_MOVEMENT_OBJECT | CollisionDataFlag.BLOCK_MOVEMENT_FLOOR_DECORATION
            | CollisionDataFlag.BLOCK_MOVEMENT_FLOOR;
    private final Client client;
    private final EfficientSailingConfig config;
    private final Consumer<String> diagnostic;
    private volatile SailingSnapshot snapshot;
    private volatile BooleanSupplier permitted = () -> false;
    private final ThreadLocal<BooleanSupplier> actionScope = new ThreadLocal<>();
    private int sailsPendingTick = -100, sailsPendingMode = -1;
    private String lastUiFingerprint = "";
    private long lastUiClick;
    private Hull cachedHull;
    private String cachedHullType;
    private int cachedHullMargin = -1;
    private volatile double observedCruiseSpeed;
    private String nativeHullKey="";
    private static final int BASE_SPEED=varbitId("SAILING_SIDEPANEL_BOAT_BASESPEED");
    private static final int SPEED_CAP=varbitId("SAILING_SIDEPANEL_BOAT_SPEEDCAP");
    private static final int ACCELERATION=varbitId("SAILING_SIDEPANEL_BOAT_ACCELERATION");
    private static final int START_ON_HEADING=varbitId("SAILING_START_BOAT_WHEN_SETTING_HEADING");
    private static int varbitId(String name) {
        try { return VarbitID.class.getField(name).getInt(null); }
        catch (ReflectiveOperationException ex) { return -1; }
    }
    private int motionVarbit(int id,int fallback) {return id<0?fallback:client.getVarbitValue(id);}


    MicrobotSailingFacade(Client client, EfficientSailingConfig config, Consumer<String> diagnostic) {
        this.client = client; this.config = config; this.diagnostic = diagnostic;
    }
    void permit(BooleanSupplier permitted) { this.permitted = permitted; }
    void beginStep() { actionScope.set(permitted); }
    void endStep() { actionScope.remove(); }
    static long now() { return System.nanoTime() / 1_000_000L; }
    static boolean humanOrPaused() { return Microbot.pauseAllScripts.get() || InputArbiter.isHuman(); }
    private BooleanSupplier actionPermission() {
        BooleanSupplier scope = actionScope.get();
        final BooleanSupplier permit = scope == null ? permitted : scope;
        return () -> permit.getAsBoolean() && !humanOrPaused() && Microbot.isLoggedIn();
    }
    private boolean canAct() { return actionPermission().getAsBoolean(); }
    SailingSnapshot latest() { return snapshot; }
    double observedCruiseSpeed() { return observedCruiseSpeed; }
    private <T> T read(Supplier<T> supplier) { return Microbot.getClientThread().runOnClientThreadOptional(supplier::get).orElse(null); }

    /** Invoked from GameTick, not from an executor. */
    void capture() {
        Player player = client.getLocalPlayer();
        boolean logged = client.getGameState() == GameState.LOGGED_IN && player != null;
        if (!logged) { snapshot = null; return; }
        WorldView top = client.getTopLevelWorldView();
        boolean deck = player.getWorldView() != null && !player.getWorldView().isTopLevel();
        Rs2BoatModel boat = deck && Microbot.getRs2BoatCache() != null ? Microbot.getRs2BoatCache().getLocalBoat() : null;
        SeaPoint sea = null; String type = deck ? "pending-deck" : "none"; int heading = 0, viewId = -1;
        boolean navigating = false, moving = false, backwards = false;
        int movementMode = -1;
        SeaPose pose=null; Hull nativeHull=null;
        double fullSpeed=3,acceleration=1; boolean headingAutoStarts=true;
        if (boat != null && (boat.getWorldView() == null
                || boat.getWorldView().getId() != player.getWorldView().getId()
                || top == null || boat.getTargetLocation() == null)) boat = null;
        if (boat != null) {
            // Do not use player.getWorldLocation(): that is an instanced deck position.
            LocalPoint lp = boat.getTargetLocation();
            // Server target pose is authoritative for control. Display interpolation lags it.
            // Preserve fine coordinates and the entity's true origin rather than flooring the hull.
            pose = new SeaPose(top.getBaseX()+lp.getX()/128.0-.5,
                    top.getBaseY()+lp.getY()/128.0-.5,12-boat.getTargetOrientation()/128.0);
            sea = pose.tile();
            type = boat.getBoatType().name();
            // Unsupported guest/quest vessels are not silently treated as a raft.
            if (!type.equals("RAFT") && !type.equals("SKIFF") && !type.equals("SLOOP")) { boat = null; sea = null; type = "unsupported:" + type; }
            if (boat != null) {
                viewId = player.getWorldView().getId();
                heading = pose.heading16();
                nativeHull = nativeHull(boat.getConfig());
                fullSpeed = Math.max(motionVarbit(BASE_SPEED,384),motionVarbit(SPEED_CAP,384))/128.0;
                acceleration = motionVarbit(ACCELERATION,128)/128.0;
                headingAutoStarts = motionVarbit(START_ON_HEADING,1)!=0;
                navigating = boat.isNavigating();
                // The side panel also has transitional modes. "Not stopped" is not
                // evidence that sails are raised. Copy the live mode on this client tick.
                movementMode = client.getVarbitValue(VarbitID.SAILING_SIDEPANEL_BOAT_MOVE_MODE);
                if (movementMode == 4) movementMode = observedMovementMode(movementMode, ui());
                moving = movementMode >= 1 && movementMode <= 4;
                backwards = movementMode == 3;
            }
        }
        Hull hull = boat==null?hullFor(type):nativeHull;
        if(boat==null) {pose=null;sailsPendingMode=-1;}
        ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
        int coins = 0, occupied = 0, summonTablets = 0;
        if (inv != null) for (Item item : inv.getItems()) {
            if (item.getId() > -1 && item.getQuantity() > 0) occupied++;
            if (item.getId() == 995) coins += item.getQuantity();
            if (item.getId() == SUMMON_BOAT_TABLET) summonTablets += item.getQuantity();
        }
        int bankCoins = -1, bankSummonTablets = -1;
        boolean bankOpen = Rs2Bank.isOpen();
        if (bankOpen) {
            ItemContainer bank = client.getItemContainer(InventoryID.BANK);
            if (bank != null) {
                bankCoins = 0; bankSummonTablets = 0;
                for (Item item : bank.getItems()) {
                    if (item.getId() == 995) bankCoins += item.getQuantity();
                    if (item.getId() == SUMMON_BOAT_TABLET) bankSummonTablets += item.getQuantity();
                }
            }
        }
        List<HazardZone> hazards = new ArrayList<>();
        int shipwrightId = -1; WorldPoint shipwrightPoint = null;
        String[] dangerNames = config.dangerNames().toLowerCase(Locale.ROOT).split(",");
        if (top != null) for (NPC npc : top.npcs()) {
            if (npc == null || npc.getName() == null) continue;
            String name = npc.getName().toLowerCase(Locale.ROOT);
            WorldPoint location = npc.getWorldLocation();
            if (boat == null && (name.startsWith("shipwright") || name.equals("junior jim")) && location.getPlane() == player.getWorldLocation().getPlane()
                    && (shipwrightPoint == null || location.distanceTo2D(player.getWorldLocation()) < shipwrightPoint.distanceTo2D(player.getWorldLocation()))) {
                shipwrightId = npc.getId(); shipwrightPoint = location;
            }
            if (boat != null) for (String part : dangerNames) {
                if (!part.trim().isEmpty() && name.contains(part.trim())) {
                    hazards.add(HazardZone.circle(name, location.getX(), location.getY(), config.dangerRadius())); break;
                }
            }
        }
        SceneChart chart = boat != null ? captureChart(top, client.getTickCount()) : null;
        SailingSnapshot previous = snapshot;
        if (boat == null || previous == null || !previous.onBoat || previous.sea == null
                || previous.boatViewId != viewId || !previous.boatType.equals(type)) observedCruiseSpeed = 0;
        else if (moving && previous.moving && client.getTickCount() > previous.tick && client.getTickCount() - previous.tick <= 4) {
            double observed = pose.distance(previous.pose==null?new SeaPose(previous.sea,previous.heading):previous.pose) / (client.getTickCount() - previous.tick);
            if (observed > 0) observedCruiseSpeed = Math.max(observed, observedCruiseSpeed);
        }
        Widget map = client.getWidget(WORLD_MAP_ROOT);
        snapshot = new SailingSnapshot(now(), client.getTickCount(), client.getRealSkillLevel(Skill.SAILING),
                client.getBoostedSkillLevel(Skill.HITPOINTS), coins, 28 - occupied, bankCoins,
                heading, viewId, true, boat != null, navigating, moving, bankOpen,
                map != null && !map.isHidden(), player.getWorldLocation(), sea, type, hull, chart, hazards,
                shipwrightId, shipwrightPoint, summonTablets, bankSummonTablets, backwards, movementMode,
                pose,fullSpeed,acceleration,headingAutoStarts,top==null?-1:top.getId());
    }
    void invalidate() { snapshot = null; }
    private Hull nativeHull(WorldEntityConfig bounds) {
        if(bounds==null)return null;
        String key=bounds.getBoundsX()+","+bounds.getBoundsY()+","+bounds.getBoundsWidth()+","+bounds.getBoundsHeight()+","+config.hullMargin();
        if(!key.equals(nativeHullKey)||cachedHull==null||!cachedHull.nativeGeometry) {
            try {cachedHull=Hull.nativeBounds(bounds.getBoundsX(),bounds.getBoundsY(),bounds.getBoundsWidth(),bounds.getBoundsHeight(),config.hullMargin());}
            catch(IllegalArgumentException ex) {diagnostic.accept("native-hull rejected "+key+": "+ex.getMessage());return null;}
            nativeHullKey=key;
        }
        return cachedHull;
    }
    private Hull hullFor(String type) {
        int margin = config.hullMargin();
        if (cachedHull == null || margin != cachedHullMargin || !type.equals(cachedHullType)) {
            cachedHull = type.equals("SLOOP") ? new Hull(3, 8, margin)
                    : type.equals("SKIFF") ? new Hull(2, 5, margin) : new Hull(1, 3, margin);
            cachedHullType = type; cachedHullMargin = margin;
        }
        return cachedHull;
    }
    private SceneChart captureChart(WorldView top, int tick) {
        if (top == null || top.isInstance() || top.getPlane() != 0) return null;
        CollisionData[] maps = top.getCollisionMaps();
        if (maps == null || maps.length == 0 || maps[0] == null) return null;
        int[][] flags = maps[0].getFlags();
        final int edge = 8;
        if (flags == null || flags.length <= edge * 2 || flags[0] == null || flags[0].length <= edge * 2) return null;
        int width = flags.length - edge * 2, height = flags[0].length - edge * 2;
        byte[] cells = new byte[width * height];
        for (int x = 0; x < width; x++) for (int y = 0; y < height; y++) {
            int f = flags[x + edge] == null || y + edge >= flags[x + edge].length ? -1 : flags[x + edge][y + edge];
            cells[x * height + y] = f == -1 || f == 0xffffff ? SeaGrid.UNKNOWN
                    : (f & MOVE_MASK) == 0 ? SeaGrid.OPEN : SeaGrid.BLOCKED;
        }
        return new SceneChart(tick, top.getBaseX() + edge, top.getBaseY() + edge, width, height, cells);
    }
    private Rs2BoatModel boatNow() {
        return read(() -> {
            Player p = client.getLocalPlayer();
            if (p == null || p.getWorldView() == null || p.getWorldView().isTopLevel()) return null;
            Rs2BoatModel b = Microbot.getRs2BoatCache().getLocalBoat();
            return b != null && b.getWorldView() != null && b.getWorldView().getId() == p.getWorldView().getId() ? b : null;
        });
    }
    boolean closeMap() {
        if (!canAct()) return false;
        Boolean open = read(() -> { Widget w = client.getWidget(WORLD_MAP_ROOT); return w != null && !w.isHidden(); });
        if (Boolean.FALSE.equals(open)) return true;
        if (open == null) return false;
        Rectangle button = read(() -> {
            Widget close = client.getWidget(InterfaceID.Worldmap.CLOSE);
            return close == null || close.isHidden() ? null : new Rectangle(close.getBounds());
        });
        if (button == null || button.width <= 0 || button.height <= 0 || !canAct()) return false;
        NewMenuEntry entry = new NewMenuEntry().option("Close").target("").identifier(1)
                .type(MenuAction.CC_OP).param0(-1).param1(InterfaceID.Worldmap.CLOSE).forceLeftClick(false);
        // Dispatch only; the controller waits for a fresh snapshot confirming map closure.
        return SailingInput.click(new Point((int) button.getCenterX(), (int) button.getCenterY()),
                entry, actionPermission());
    }
    boolean navigate() {
        if (!canAct()) return false;
        Rs2BoatModel boat = boatNow();
        if (boat == null) return false;
        if (Boolean.TRUE.equals(read(boat::isNavigating))) return true;
        Rs2TileObjectModel helm = Microbot.getRs2TileObjectCache().query().fromWorldView()
                .where(o -> SceneObjects.hasAction(o, "Navigate", false)).nearestOnClientThread();
        if (helm == null || !canAct()) return false;
        helm.click("Navigate");
        return true;
    }
    private Rs2TileObjectModel gangplank(int id) {
        Rs2TileObjectModel exact = Microbot.getRs2TileObjectCache().query().withIds(new int[]{id}).nearestOnClientThread();
        if (exact != null) return exact;
        // Some scene caches expose the active/impostor gangplank ID rather than its native
        // port ID. Require a Board action, a gangplank name and the selected port's vicinity.
        PortCatalog.Port expected = null;
        for (net.runelite.client.plugins.microbot.api.boat.data.PortLocation location : net.runelite.client.plugins.microbot.api.boat.data.PortLocation.values())
            if (location.getGangplankObject() == id) { expected = new PortCatalog.Port(location); break; }
        final PortCatalog.Port port = expected;
        if (port == null) return null;
        return Microbot.getRs2TileObjectCache().query().where(o -> {
            WorldPoint point = o.getWorldLocation();
            return point != null && point.getPlane() == 0 && o.getName() != null
                    && o.getName().toLowerCase(Locale.ROOT).contains("gangplank")
                    && new SeaPoint(point.getX(), point.getY()).distance(port.sea) <= 40
                    && SceneObjects.hasAction(o, "Board", false);
        }).nearestOnClientThread();
    }
    WorldPoint gangplankPoint(int id) {
        Rs2TileObjectModel gangplank = gangplank(id);
        return gangplank == null ? null : read(gangplank::getWorldLocation);
    }
    PortCatalog.Port nearbyBoardingPort(SailingSnapshot s, String forcedPort) {
        if (s == null || s.onBoat || s.player == null || s.player.getPlane() != 0) return null;
        PortCatalog.Port best = null; double distance = 37;
        for (net.runelite.client.plugins.microbot.api.boat.data.PortLocation location : net.runelite.client.plugins.microbot.api.boat.data.PortLocation.values()) {
            PortCatalog.Port port = new PortCatalog.Port(location);
            if (port.gangplankId <= 0 || port.level > s.sailingLevel || PortCatalog.excluded(config, port.key)
                    || !forcedPort.isEmpty() && !forcedPort.equals(port.key)
                    || new SeaPoint(s.player.getX(), s.player.getY()).distance(port.sea) > 60) continue;
            Rs2TileObjectModel plank = gangplank(port.gangplankId);
            WorldPoint point = plank == null ? null : read(plank::getWorldLocation);
            if (point == null || point.getPlane() != s.player.getPlane() || point.distanceTo2D(s.player) >= distance
                    || !Boolean.TRUE.equals(read(() -> SceneObjects.hasAction(plank, "Board", false)))) continue;
            distance = point.distanceTo2D(s.player); best = port;
        }
        return best;
    }
    boolean atDock(int gangplankId) {
        SailingSnapshot s = snapshot;
        WorldPoint point = gangplankPoint(gangplankId);
        return s != null && !s.onBoat && s.player != null && point != null
                && point.getPlane() == s.player.getPlane() && point.distanceTo2D(s.player) <= 4;
    }
    boolean board(int gangplankId) {
        if (!canAct()) return false;
        if (Rs2Inventory.isItemSelected()) { Rs2Inventory.deselect(); return false; }
        Rs2TileObjectModel gangplank = gangplank(gangplankId);
        if (gangplank == null || !canAct()) return false;
        if (!net.runelite.client.plugins.microbot.util.camera.Rs2Camera.isTileOnScreen(gangplank.getLocalLocation()))
            net.runelite.client.plugins.microbot.util.camera.Rs2Camera.turnTo(gangplank);
        Object[] action = read(() -> {
            Rs2TileObjectModel current = gangplank(gangplankId);
            if (current == null || current.getWorldView() == null) return null;
            ObjectComposition definition = current.getObjectComposition();
            if (definition == null) return null;
            if (definition.getImpostorIds() != null) definition = definition.getImpostor();
            if (definition == null || definition.getActions() == null) return null;
            int option = -1;
            String[] actions = definition.getActions();
            for (int i = 0; i < Math.min(5, actions.length); i++)
                if ("Board".equalsIgnoreCase(actions[i])) { option = i; break; }
            LocalPoint local = LocalPoint.fromWorld(current.getWorldView(), current.getWorldLocation());
            java.awt.Shape shape = current.getClickbox();
            if (option < 0 || local == null || shape == null) return null;
            Rectangle bounds = shape.getBounds().intersection(new Rectangle(client.getViewportXOffset(),
                client.getViewportYOffset(), client.getViewportWidth(), client.getViewportHeight()));
            if (bounds.isEmpty()) return null;
            MenuAction[] options = {MenuAction.GAME_OBJECT_FIRST_OPTION, MenuAction.GAME_OBJECT_SECOND_OPTION,
                MenuAction.GAME_OBJECT_THIRD_OPTION, MenuAction.GAME_OBJECT_FOURTH_OPTION, MenuAction.GAME_OBJECT_FIFTH_OPTION};
            NewMenuEntry entry = new NewMenuEntry().option("Board").target(definition.getName())
                .identifier(current.getId()).type(options[option]).param0(local.getSceneX()).param1(local.getSceneY())
                .itemId(-1).worldViewId(current.getWorldView().getId());
            return new Object[]{entry, bounds};
        });
        if (action == null || !canAct()) return false;
        Rectangle bounds = (Rectangle) action[1];
        return SailingInput.click(new Point((int) bounds.getCenterX(), (int) bounds.getCenterY()),
            (NewMenuEntry) action[0], actionPermission());
    }
    boolean summonCasting() {
        Boolean busy=read(()->client.getLocalPlayer()==null || client.getLocalPlayer().getAnimation()!=-1);
        return !Boolean.FALSE.equals(busy);
    }
    boolean withdrawSummonTablet() {
        // A noted tablet cannot be broken at the dock. Verify item mode before withdrawing.
        return canAct() && Rs2Bank.isOpen() && Rs2Bank.setWithdrawAsItem()
                && canAct() && Rs2Bank.withdrawX(SUMMON_BOAT_TABLET, 1);
    }
    boolean breakSummonTablet(int gangplankId) {
        if (!canAct() || !config.useSummonBoatTablet()
                || config.boatName().trim().isEmpty() || Rs2Bank.isOpen() || !atDock(gangplankId)) return false;
        if (Rs2Inventory.isItemSelected()) { Rs2Inventory.deselect(); return false; }
        if (!Rs2Tab.switchToInventoryTab() || !canAct()) return false;
        Object[] action = read(() -> {
            var tablet = Rs2Inventory.get(SUMMON_BOAT_TABLET);
            Widget inventory = client.getWidget(ComponentID.INVENTORY_CONTAINER);
            if (tablet == null || tablet.isNoted() || inventory == null || inventory.isHidden()) return null;
            Widget item = inventory.getChild(tablet.getSlot());
            if (item == null || item.isHidden() || item.getItemId() != SUMMON_BOAT_TABLET) return null;
            Rectangle bounds = item.getBounds();
            if (bounds == null || bounds.width <= 0 || bounds.height <= 0) return null;
            // CC_OP indexes belong to the live widget; item-definition indexes can select Drop.
            String[] actions = item.getActions();
            if (actions != null) for (int i = 0; i < actions.length; i++) if ("Break".equalsIgnoreCase(actions[i]))
                return new Object[]{new NewMenuEntry().option("Break").target(tablet.getName()).identifier(i + 1)
                    .type(MenuAction.CC_OP).param0(tablet.getSlot()).param1(inventory.getId())
                    .itemId(SUMMON_BOAT_TABLET).forceLeftClick(false), new Rectangle(bounds)};
            return null;
        });
        if (action == null || !canAct()) return false;
        Rectangle bounds = (Rectangle) action[1];
        return SailingInput.click(new Point((int) bounds.getCenterX(), (int) bounds.getCenterY()),
                (NewMenuEntry) action[0], actionPermission());
    }
    boolean beginRecovery(int npcId) {
        return beginRecovery(npcId, false);
    }
    boolean beginRecovery(int npcId, boolean confirmedLostBoat) {
        if (!canAct() || config.useSummonBoatTablet() && !confirmedLostBoat || config.boatName().trim().isEmpty()) return false;
        Rs2NpcModel npc = Rs2Npc.getNpc(npcId);
        if (npc == null) return false;
        String action = Rs2Npc.getAvailableAction(npc, Arrays.asList("Recover-boat", "Retrieve-boat"));
        return action != null && !action.isEmpty() && canAct() && Rs2Npc.interact(npc, action);
    }
    boolean openNearbyBank() {
        if (!canAct()) return false;
        if (Rs2Bank.isOpen()) return true;
        SailingSnapshot s = snapshot;
        if (s == null || s.onBoat) return false;
        boolean ferox = s.player.getX() >= 3110 && s.player.getX() <= 3155 && s.player.getY() >= 3610 && s.player.getY() <= 3655;
        Rs2TileObjectModel bank = Microbot.getRs2TileObjectCache().query().where(o -> {
            String name = o.getName();
            if (name == null || o.getWorldLocation().distanceTo2D(s.player) > 12) return false;
            String n = name.toLowerCase(Locale.ROOT);
            if (ferox && !n.contains("bank chest")) return false;
            return (n.contains("bank chest") || n.contains("bank booth"))
                    && (SceneObjects.hasAction(o, "Bank", false) || SceneObjects.hasAction(o, "Use", false));
        }).nearestOnClientThread();
        if (bank != null && canAct()) {
            String action = Boolean.TRUE.equals(read(() -> SceneObjects.hasAction(bank, "Bank", false))) ? "Bank" : "Use";
            if (!canAct()) return false;
            bank.click(action); return true;
        }
        if (ferox) return false;
        Rs2NpcModel banker = Rs2Npc.getNpc("Banker");
        WorldPoint point = banker == null ? null : read(banker::getWorldLocation);
        return point != null && point.distanceTo2D(s.player) <= 10 && canAct() && Rs2Npc.interact(banker, "Bank");
    }
    boolean withdrawCoins(int amount) {
        if (!canAct() || amount <= 0 || !Rs2Bank.isOpen()) return false;
        return canAct() && Rs2Bank.withdrawX(995, amount);
    }
    void closeBank() { if (canAct() && Rs2Bank.isOpen()) Rs2Bank.closeBank(); }

    /** Semantic, desired-state controls; no blind sail toggle and no sleepUntil. */
    boolean sails(boolean raised) { return movement(raised?2:0); }
    boolean slowForward() { return movement(1); }
    private boolean movement(int desired) {
        if(!canAct())return false;
        Rs2BoatModel boat=boatNow();
        if(boat==null||!Boolean.TRUE.equals(read(boat::isNavigating)))return false;
        Integer mode=read(()->client.getVarbitValue(VarbitID.SAILING_SIDEPANEL_BOAT_MOVE_MODE));
        Integer tick=read(client::getTickCount);
        if(mode==null||tick==null)return false;
        UiState state=ui();
        mode=observedMovementMode(mode,state);
        if(mode==desired){sailsPendingMode=-1;return true;}
        if(sailsPendingMode==desired&&tick-sailsPendingTick<3)return true;
        if(!canAct())return false;
        Rs2Tab.switchTo(InterfaceTab.COMBAT);
        state=ui();
        UiElement slow=slowSailButton(state);
        if(desired==1&&slow!=null) {
            for(int i=0;i<slow.actions.length;i++)if(normalizeControl(slow.actions[i]).equals("set sails")) {
                if(!clickOperation(slow,i,true))return false;
                sailsPendingMode=desired;sailsPendingTick=tick;
                diagnostic.accept("mode-control requested=1 observed="+mode+" action=Set sails slow-button");return true;
            }
        }
        String[] aliases=desired==0?new String[]{"Stop boat","Stop","Lower sails","Unset sails","Un-set sails"}
                :desired==1?(mode==2||mode==4
                    ?new String[]{"Lower speed","Move forwards","Move forward","Slow forward","Half sails"}
                    :new String[]{"Move forwards","Move forward","Slow forward","Half sails"})
                :mode==1?new String[]{"Raise speed","Set sails","Full sails","Raise sails","Set sail"}
                    :new String[]{"Set sails","Full sails","Raise sails","Set sail"};
        for(String alias:aliases)for(UiElement e:state.elements) {
            if(e.group()!=937)continue;
            for(int i=0;i<e.actions.length;i++)if(normalizeControl(e.actions[i]).equals(normalizeControl(alias))) {
                if(!clickOperation(e,i,true))return false;
                sailsPendingMode=desired;sailsPendingTick=tick;
                diagnostic.accept("mode-control requested="+desired+" observed="+mode+" action="+e.actions[i]);return true;
            }
        }
        // Some side-panel revisions expose a speed ladder rather than named modes.
        // Select a verified action, advance ONE step, then observe the actual mode.
        int ladderMode=mode==4?2:mode;
        String ladder=ladderMode<desired?"Faster":"Slower";
        if(ladderMode>=0&&ladderMode<=2)for(UiElement e:state.elements) {
            if(e.group()!=937)continue;
            for(int i=0;i<e.actions.length;i++)if(normalizeControl(e.actions[i]).equals(normalizeControl(ladder))) {
                if(!clickOperation(e,i,true))return false;
                sailsPendingMode=ladderMode+(ladderMode<desired?1:-1);sailsPendingTick=tick;
                diagnostic.accept("mode-ladder requested="+desired+" observed="+mode+" action="+e.actions[i]);return true;
            }
        }
        StringBuilder available=new StringBuilder();
        for(UiElement e:state.elements)if(e.group()==937&&e.actions.length>0)available.append(e.fingerprint()).append(';');
        diagnostic.accept("mode-control unavailable desired="+desired+" observed="+mode+" actions="+available);
        return false; // Unknown UI is not permission to click a guessed index.
    }
    /** The native panel's main and slow buttons can both be labelled Set sails. */
    static UiElement slowSailButton(UiState state) {
        for(UiElement main:state.elements) {
            if(main.group()!=937||main.index!=0||!hasSailAction(main))continue;
            UiElement slow=null;boolean reverse=false;
            for(UiElement sibling:state.elements)if(sibling.id==main.id) {
                if(sibling.index==1)for(String action:sibling.actions)
                    if(normalizeControl(action).equals("reverse"))reverse=true;
                if(sibling.index==2&&hasSailAction(sibling))slow=sibling;
            }
            if(reverse&&slow!=null)return slow;
        }
        return null;
    }
    static int observedMovementMode(int raw,UiState state) {
        if(raw!=4)return raw;
        for(UiElement main:state.elements)if(main.group()==937&&main.index==0) {
            for(String action:main.actions) {
                String label=normalizeControl(action);
                if(label.equals("set sails"))return 0;
                if(label.equals("unset sails")||label.equals("un set sails"))return 2;
            }
        }
        return raw;
    }
    private static boolean hasSailAction(UiElement element) {
        for(String action:element.actions) {
            String label=normalizeControl(action);
            if(label.equals("set sails")||label.equals("unset sails")||label.equals("un set sails"))return true;
        }
        return false;
    }
    private static String normalizeControl(String s) {
        return RecoveryQuote.clean(s).replace('-',' ').replace('_',' ').trim().toLowerCase(Locale.ROOT);
    }
    boolean reverse() {
        return facilityControl("Reverse");
    }
    boolean stopReverse() {
        return facilityControl("Stop boat");
    }
    private boolean facilityControl(String action) {
        if (!canAct()) return false;
        Rs2BoatModel boat = boatNow();
        if (boat == null || !Boolean.TRUE.equals(read(boat::isNavigating))) return false;
        Rs2Tab.switchTo(InterfaceTab.COMBAT);
        UiState ui = ui();
        for (UiElement element : ui.elements) {
            if (element.group() != 937) continue; // Sailing side panel, not unrelated Reverse options.
            for (int i = 0; i < element.actions.length; i++) {
                if (RecoveryQuote.clean(element.actions[i]).equalsIgnoreCase(action)) return clickOperation(element, i);
            }
        }
        return false;
    }
    boolean steer(SeaPoint target, int requestedHeading) {
        if (!canAct()) return false;
        SailingSnapshot s = snapshot;
        Rs2BoatModel boat = boatNow();
        if (s == null || !s.onBoat || s.sea == null || boat == null || !Boolean.TRUE.equals(read(boat::isNavigating))) return false;
        int direction = requestedHeading < 0 ? Hull.heading(s.sea, target) : Math.floorMod(requestedHeading, 16);
        // Re-anchor the intended heading on the freshest hull position. The boat may have
        // moved since the planner snapshot; a fixed stale map point can change the bearing.
        double radians = direction * Math.PI / 8;
        int distance = Math.max(14, Math.min(20, config.lookAhead()));
        if (config.steering() == EfficientSailingConfig.Steering.MINIMAP_THEN_HEADING) {
            Point click = read(() -> {
                WorldView top=client.getTopLevelWorldView();
                LocalPoint rendered=boat.getLocalLocation();
                if(top==null||top.getId()!=s.topViewId||rendered==null)return null;
                WorldPoint wp=WorldPoint.fromLocal(top,rendered.getX(),rendered.getY(),0);
                SeaPoint root=new SeaPoint(wp.getX(),wp.getY());
                SeaPoint aim=root.add((int)Math.round(Math.cos(radians)*distance),(int)Math.round(Math.sin(radians)*distance));
                return minimapPoint(root,aim);
            });
            if (click != null && canAct()) {
                if (!SailingInput.click(click, null, actionPermission())) return false;
                diagnostic.accept("steer input=minimap target=" + target);
                return true;
            }
        }
        if (!canAct()) return false;
        int nativeDirection = Math.floorMod(12 - direction, 16);
        NewMenuEntry entry = new NewMenuEntry().option("Set-Heading").target("").identifier(nativeDirection)
                .type(MenuAction.SET_HEADING).param0(0).param1(0).forceLeftClick(false);
        entry.setWorldViewId(s.topViewId); // SET_HEADING belongs to the ocean view, not the instanced deck.
        Point point = read(() -> new Point(Math.max(2, client.getCanvasWidth() / 2), Math.max(2, client.getCanvasHeight() / 2)));
        if (!SailingInput.click(point, entry, actionPermission())) return false;
        diagnostic.accept("steer input=SET_HEADING target=" + target + " native=" + nativeDirection);
        return true;
    }
    private Point minimapPoint(SeaPoint sea, SeaPoint target) {
        Widget map = client.getWidget(WORLD_MAP_ROOT);
        if (map != null && !map.isHidden()) return null;
        WorldView top = client.getTopLevelWorldView();
        if (top == null) return null;
        Widget draw = null;
        int[] candidates = {ComponentID.FIXED_VIEWPORT_MINIMAP_DRAW_AREA, ComponentID.RESIZABLE_VIEWPORT_MINIMAP_DRAW_AREA,
                ComponentID.RESIZABLE_VIEWPORT_BOTTOM_LINE_MINIMAP_DRAW_AREA};
        for (int id : candidates) { Widget w = client.getWidget(id); if (w != null && !w.isHidden()) { draw = w; break; } }
        if (draw == null) return null;
        LocalPoint end = LocalPoint.fromWorld(top, new WorldPoint(target.x, target.y, 0));
        LocalPoint origin = LocalPoint.fromWorld(top, new WorldPoint(sea.x, sea.y, 0));
        if (end == null || origin == null) return null;
        Point p = Perspective.localToMinimap(client, end);
        Point centreCheck = Perspective.localToMinimap(client, origin);
        Rectangle b = draw.getBounds();
        if (p == null || centreCheck == null || b.width <= 0 || b.height <= 0) return null;
        Ellipse2D clip = new Ellipse2D.Double(b.x + b.width * .1, b.y + b.height * .1, b.width * .8, b.height * .8);
        // Wrong deck/world-view projection cannot be allowed to click another UI control.
        if (!clip.contains(p.getX(), p.getY()) || Math.hypot(centreCheck.getX() - b.getCenterX(), centreCheck.getY() - b.getCenterY()) > Math.min(b.width, b.height) * .3) return null;
        return p;
    }

    static final class UiElement {
        final int id, index;
        final String text, subtree;
        final String[] actions;
        final Rectangle bounds;
        UiElement(Widget widget, String subtree) {
            id = widget.getId(); index = widget.getIndex(); text = RecoveryQuote.clean(widget.getText());
            String[] a = widget.getActions(); actions = a == null ? new String[0] : a.clone();
            bounds = new Rectangle(widget.getBounds()); this.subtree = subtree;
        }
        int group() { return id >>> 16; }
        String fingerprint() { return id + ":" + index + ":" + text + ":" + Arrays.toString(actions); }
    }
    static final class UiState {
        final List<UiElement> elements;
        final boolean hasContinue, hasOptions;
        UiState(List<UiElement> elements, boolean hasContinue, boolean hasOptions) {
            this.elements = elements; this.hasContinue = hasContinue; this.hasOptions = hasOptions;
        }
        Set<Integer> boatGroups(boolean recovery) {
            Set<Integer> groups = new HashSet<>();
            for (UiElement e : elements) {
                if (e.group() == 162 || e.group() == 149 || e.group() == 595 || e.text.length() > 180) continue;
                String t = e.text.toLowerCase(Locale.ROOT);
                if (t.contains("boat") && (t.contains("select") || t.contains("choose")
                        || (recovery && (t.contains("recover") || t.contains("retriev") || t.contains("summon")))
                        || (!recovery && t.contains("board")))) groups.add(e.group());
            }
            return groups;
        }
        String relevantText(boolean recovery) {
            Set<Integer> groups = boatGroups(recovery);
            groups.add(219); groups.add(231); groups.add(217); // Choice/NPC/player dialogue, never chat history.
            StringBuilder out = new StringBuilder();
            for (UiElement e : elements) if (groups.contains(e.group()) && !e.text.isEmpty()) {
                if (out.length() > 3500) break;
                out.append('[').append(e.id).append(':').append(e.index).append("] ").append(e.text).append(" ");
            }
            return out.toString();
        }
    }
    UiState ui() {
        UiState result = read(() -> {
            List<UiElement> elements = new ArrayList<>();
            Set<Widget> seen = Collections.newSetFromMap(new IdentityHashMap<Widget, Boolean>());
            Widget[] roots = client.getWidgetRoots();
            if (roots != null) for (Widget root : roots) collect(root, elements, seen, 0);
            return new UiState(elements, Rs2Dialogue.hasContinue(), Rs2Dialogue.hasSelectAnOption());
        });
        return result == null ? new UiState(Collections.emptyList(), false, false) : result;
    }
    private void collect(Widget widget, List<UiElement> elements, Set<Widget> seen, int depth) {
        if (widget == null || widget.isHidden() || depth > 18 || elements.size() > 5000 || !seen.add(widget)) return;
        elements.add(new UiElement(widget, subtreeText(widget, 0)));
        Widget[][] sets = {widget.getChildren(), widget.getDynamicChildren(), widget.getStaticChildren(), widget.getNestedChildren()};
        for (Widget[] children : sets) if (children != null) for (Widget child : children) collect(child, elements, seen, depth + 1);
    }
    private String subtreeText(Widget widget, int depth) {
        StringBuilder text = new StringBuilder();
        appendSubtree(widget, depth, text, Collections.newSetFromMap(new IdentityHashMap<Widget, Boolean>()));
        return text.toString();
    }
    private void appendSubtree(Widget widget, int depth, StringBuilder text, Set<Widget> seen) {
        if (widget == null || widget.isHidden() || depth > 6 || text.length() > 1600 || !seen.add(widget)) return;
        text.append('\n').append(RecoveryQuote.clean(widget.getText())).append('\n');
        Widget[][] sets = {widget.getChildren(), widget.getDynamicChildren(), widget.getStaticChildren(), widget.getNestedChildren()};
        for (Widget[] children : sets) if (children != null) for (Widget child : children) appendSubtree(child, depth + 1, text, seen);
    }
    boolean chooseBoat(UiState ui, boolean recovery) {
        String wanted = config.boatName().trim();
        if (wanted.isEmpty()) return false;
        Set<Integer> groups = ui.boatGroups(recovery);
        for (UiElement e : ui.elements) {
            if (!groups.contains(e.group()) || e.bounds.width <= 0 || e.bounds.height <= 0) continue;
            boolean exact = Arrays.stream(e.subtree.split("\\n")).anyMatch(line -> RecoveryQuote.clean(line).equalsIgnoreCase(wanted));
            if (!exact) continue;
            String row = e.subtree.toLowerCase(Locale.ROOT);
            if (row.contains("no teleport focus") || row.contains("requires a teleport focus")
                    || row.contains("does not have a teleport focus")) continue;
            for (int i = 0; i < e.actions.length; i++) {
                String a = RecoveryQuote.clean(e.actions[i]).toLowerCase(Locale.ROOT);
                if (a.equals("select") || (recovery && (a.equals("recover") || a.equals("retrieve") || a.equals("summon") || a.equals("summon-boat"))) || (!recovery && a.equals("board"))) {
                    return clickOperation(e, i);
                }
            }
        }
        return false;
    }
    /** Only used after this controller initiated Summon boat and selected the named boat. */
    boolean confirmSummon(UiState ui) {
        if (!canAct() || !config.useSummonBoatTablet() || !ui.hasOptions) return false;
        String context = ui.relevantText(true).toLowerCase(Locale.ROOT);
        if (!context.contains("boat") || (!context.contains("summon") && !RecoveryQuote.warnsCargoLoss(context))) return false;
        if (RecoveryQuote.destructive(context)) return false;
        // Require a separate question/prompt. The affirmative option's own wording
        // must not turn an unrelated dialogue (or the title itself) into consent.
        boolean summonPrompt = false;
        for (UiElement e : ui.elements) {
            if (e.group() != 219) continue;
            String text = RecoveryQuote.clean(e.text).toLowerCase(Locale.ROOT);
            if (text.matches("^(yes|no)([,. ].*)?$")) continue;
            if (text.contains("boat") && (text.contains("summon") || RecoveryQuote.warnsCargoLoss(text))) {
                summonPrompt = true;
            }
        }
        if (!summonPrompt) return false;
        for (UiElement e : ui.elements) {
            if (e.group() != 219) continue;
            String option = RecoveryQuote.clean(e.text).toLowerCase(Locale.ROOT);
            if (!option.matches("^yes([,. ].*)?$")) continue;
            if (RecoveryQuote.destructive(option) || RecoveryQuote.amount(option).isPresent()) continue;
            if (!uiClickAllowed(e.fingerprint()) || !canAct()) return false;
            return Rs2Dialogue.clickOption(e.text, true);
        }
        return false;
    }
    boolean payRecovery(UiState ui, long maximum, int coins) {
        return payRecovery(ui, maximum, coins, false);
    }
    /** confirmedLostBoat is set only after the controller observes the game's lost-at-sea denial. */
    boolean payRecovery(UiState ui, long maximum, int coins, boolean confirmedLostBoat) {
        if (!canAct() || config.useSummonBoatTablet() && !confirmedLostBoat || !ui.hasOptions) return false;
        for (UiElement e : ui.elements) {
            if (e.group() != 219) continue;
            OptionalLong amount = RecoveryQuote.amount(e.text);
            if (!amount.isPresent()) continue;
            if (amount.getAsLong() > maximum || amount.getAsLong() > coins) {
                throw new IllegalStateException("Recovery quote " + amount.getAsLong() + " exceeds fee cap or available coins");
            }
            if (!uiClickAllowed(e.fingerprint()) || !canAct()) return false;
            diagnostic.accept("recovery-payment explicitQuote=" + amount.getAsLong());
            return Rs2Dialogue.clickOption(e.text, true);
        }
        return false;
    }
    boolean continueRecovery(UiState ui) {
        if (!canAct() || !ui.hasContinue) return false;
        String text = ui.relevantText(true).toLowerCase(Locale.ROOT);
        if (!text.contains("boat") && !text.contains("shipwright") && !text.contains("port wizard")) return false;
        if (RecoveryQuote.destructive(text)) return false;
        if (!uiClickAllowed("continue:" + text)) return false;
        if (!canAct()) return false;
        Rs2Dialogue.clickContinue(); return true;
    }
    private boolean uiClickAllowed(String fingerprint) {
        long now = now();
        if (fingerprint.equals(lastUiFingerprint) && now - lastUiClick < 1800) return false;
        lastUiFingerprint = fingerprint; lastUiClick = now; return true;
    }
    private boolean clickOperation(UiElement element, int operation) { return clickOperation(element,operation,false); }
    private boolean clickOperation(UiElement element, int operation, boolean navigation) {
        if (!canAct() || (!navigation && !uiClickAllowed(element.fingerprint()))) return false;
        Boolean same = read(() -> {
            Widget w = client.getWidget(element.id);
            if (w == null) return false;
            if (element.index >= 0) w = w.getChild(element.index);
            return w != null && !w.isHidden() && Arrays.equals(w.getActions(), element.actions)
                    && RecoveryQuote.clean(w.getText()).equals(element.text);
        });
        if (!Boolean.TRUE.equals(same) || !canAct()) return false;
        NewMenuEntry entry = new NewMenuEntry().option(element.actions[operation]).target("").identifier(operation + 1)
                .type(MenuAction.CC_OP).param0(element.index).param1(element.id).forceLeftClick(false);
        return SailingInput.click(new Point((int) element.bounds.getCenterX(), (int) element.bounds.getCenterY()), entry, actionPermission());
    }
}
