package net.runelite.client.plugins.microbot.droefficientwalker.compat;

import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.GameObject;
import net.runelite.api.GroundObject;
import net.runelite.api.ItemID;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2BankID;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Scene-object helpers for the Hub build, read from {@code Microbot.getRs2TileObjectCache()} and
 * clicked through {@link Rs2TileObjectModel}. They keep the search radius, nearest-first order and
 * impostor handling the walker was written against.
 */
public final class SceneObjects {
    private static final Set<Integer> GRAND_EXCHANGE_BOOTH_IDS = new HashSet<>(Arrays.asList(10060, 30389));

    private SceneObjects() {
    }

    // ---- compositions and actions -------------------------------------------------------------

    public static ObjectComposition convertToObjectComposition(TileObject object) {
        return convertToObjectComposition(objectIdSafe(object));
    }

    public static ObjectComposition convertToObjectComposition(int objectId) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            ObjectComposition comp = Microbot.getClient().getObjectDefinition(objectId);
            if (comp == null) return null;
            return comp.getImpostorIds() == null ? comp : comp.getImpostor();
        }).orElse(null);
    }

    public static boolean hasAction(TileObject object, String action) {
        return hasAction(object, action, false);
    }

    public static boolean hasAction(TileObject object, String action, boolean exact) {
        return hasAction(convertToObjectComposition(object), action, exact);
    }

    public static boolean hasAction(ObjectComposition composition, String action) {
        return hasAction(composition, action, true);
    }

    public static boolean hasAction(ObjectComposition composition, String action, boolean exact) {
        if (composition == null || composition.getActions() == null || action == null) return false;
        return Arrays.stream(composition.getActions())
                .filter(Objects::nonNull)
                .anyMatch(a -> exact ? a.equalsIgnoreCase(action) : a.toLowerCase().contains(action.toLowerCase()));
    }

    // ---- interaction --------------------------------------------------------------------------

    public static boolean interact(TileObject object) {
        return interact(object, "");
    }

    public static boolean interact(TileObject object, String action) {
        if (object == null) return false;
        Rs2TileObjectModel model = new Rs2TileObjectModel(object);
        return action == null || action.isEmpty() ? model.click() : model.click(action);
    }

    public static boolean interact(WorldPoint location, String action) {
        if (location == null) return false;
        TileObject object = getAll(o -> location.equals(o.getWorldLocation())).stream().findFirst().orElse(null);
        return interact(object, action);
    }

    public static boolean interact(int objectId, String action) {
        return interact(findObjectById(objectId), action);
    }

    public static boolean interact(String name, String action) {
        if (name == null) return false;
        GameObject object = getGameObject(o -> {
            ObjectComposition comp = convertToObjectComposition(o);
            return comp != null && comp.getName() != null && comp.getName().toLowerCase().contains(name.toLowerCase());
        });
        return interact(object, action);
    }

    // ---- lookups ------------------------------------------------------------------------------

    public static TileObject findObjectById(int id) {
        return getAll(o -> objectIdSafe(o) == id).stream().findFirst().orElse(null);
    }

    public static GameObject findObject(Integer[] ids) {
        Set<Integer> wanted = new HashSet<>(Arrays.asList(ids));
        return getGameObject(o -> wanted.contains(objectIdSafe(o)));
    }

    public static TileObject findGroundObjectByLocation(WorldPoint location) {
        return getGroundObject(location);
    }

    /** Nearest ground object to {@code anchor} in the loaded scene. */
    public static GroundObject getGroundObject(WorldPoint anchor) {
        return first(scene(GroundObject.class, o -> true, anchor, Constants.SCENE_SIZE));
    }

    public static GameObject getGameObject(Predicate<GameObject> predicate) {
        return first(scene(GameObject.class, predicate, Rs2Player.getWorldLocation(), Constants.SCENE_SIZE));
    }

    public static GameObject getGameObject(int id, WorldPoint anchor, int distance) {
        return first(scene(GameObject.class, o -> objectIdSafe(o) == id, anchor, distance));
    }

    public static List<GameObject> getGameObjects(Predicate<GameObject> predicate, int distance) {
        WorldPoint here = Rs2Player.getWorldLocation();
        if (here == null) return Collections.emptyList();
        return scene(GameObject.class, predicate, here, distance);
    }

    public static <T extends TileObject> List<TileObject> getAll(Predicate<? super T> predicate) {
        return getAll(predicate, Constants.SCENE_SIZE);
    }

    public static <T extends TileObject> List<TileObject> getAll(Predicate<? super T> predicate, int distance) {
        WorldPoint here = Rs2Player.getWorldLocation();
        if (here == null) return Collections.emptyList();
        return getAll(predicate, here, distance);
    }

    @SuppressWarnings("unchecked")
    public static <T extends TileObject> List<TileObject> getAll(Predicate<? super T> predicate, WorldPoint anchor, int distance) {
        if (anchor == null) return Collections.emptyList();
        // Game objects first, then the other scene layers, each nearest first.
        List<TileObject> all = new ArrayList<>(scene(GameObject.class, o -> predicate.test((T) o), anchor, distance));
        all.addAll(scene(TileObject.class, o -> !(o instanceof GameObject) && predicate.test((T) o), anchor, distance));
        return all;
    }

    public static GameObject findBank() {
        return findBank(20);
    }

    public static GameObject findBank(int maxSearchRadius) {
        Predicate<GameObject> bankable = gameObject -> {
            WorldPoint loc = gameObject.getWorldLocation();
            if ((loc.equals(new WorldPoint(3147, 3449, 0)) || loc.equals(new WorldPoint(3148, 3449, 0)))
                    && !BankLocation.COOKS_GUILD.hasRequirements()) return false;
            if ((loc.equals(new WorldPoint(1248, 3759, 0)) || loc.equals(new WorldPoint(1249, 3759, 0)))
                    && !Rs2Player.getSkillRequirement(Skill.FARMING, 85, true)) return false;
            if (loc.equals(new WorldPoint(2099, 3920, 0))
                    && Rs2Player.getQuestState(Quest.DREAM_MENTOR) != QuestState.FINISHED) return false;
            if ((loc.equals(new WorldPoint(2098, 3920, 0)) || loc.equals(new WorldPoint(2097, 3920, 0)))
                    && !(Rs2Inventory.hasItem(ItemID.SEAL_OF_PASSAGE) || Rs2Equipment.isWearing(ItemID.SEAL_OF_PASSAGE)))
                return false;
            ObjectComposition comp = convertToObjectComposition(gameObject);
            return comp != null && (hasAction(comp, "Bank", false) || hasAction(comp, "Collect", false));
        };
        return getGameObjects(o -> Rs2BankID.BANK_ID_SET.contains(objectIdSafe(o)) && bankable.test(o), maxSearchRadius)
                .stream().findFirst().orElse(null);
    }

    public static WallObject findGrandExchangeBooth() {
        return findGrandExchangeBooth(20);
    }

    public static WallObject findGrandExchangeBooth(int maxSearchRadius) {
        WorldPoint here = Rs2Player.getWorldLocation();
        if (here == null) return null;
        return scene(WallObject.class, o -> GRAND_EXCHANGE_BOOTH_IDS.contains(objectIdSafe(o)), here, maxSearchRadius)
                .stream()
                .filter(o -> Rs2Tile.isTileReachable(o.getWorldLocation()))
                .findFirst().orElse(null);
    }

    public static WorldArea getWorldArea(GameObject gameObject) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            LocalPoint local = gameObject.getLocalLocation();
            if (local == null || !local.isInScene()) return null;
            LocalPoint southWest = new LocalPoint(
                    local.getX() - (gameObject.sizeX() - 1) * Perspective.LOCAL_TILE_SIZE / 2,
                    local.getY() - (gameObject.sizeY() - 1) * Perspective.LOCAL_TILE_SIZE / 2,
                    gameObject.getWorldView());
            WorldPoint origin = WorldPoint.fromLocal(Microbot.getClient(), southWest);
            return new WorldArea(origin, gameObject.sizeX(), gameObject.sizeY());
        }).orElse(null);
    }

    /** True when a path from the player ends beside {@code target}. */
    public static boolean canReach(WorldPoint target) {
        if (target == null) return false;
        List<WorldPoint> path = Rs2Player.getRs2WorldPoint().pathTo(target, true);
        if (path == null || path.isEmpty()) return false;
        WorldPoint end = path.get(path.size() - 1);
        WorldArea pathArea = new WorldArea(new WorldPoint(end.getX() - 1, end.getY() - 1, end.getPlane()), 2, 2);
        WorldArea objectArea = new WorldArea(new WorldPoint(target.getX() - 2, target.getY() - 2, target.getPlane()), 4, 4);
        return pathArea.intersectsWith2D(objectArea);
    }

    // ---- scene enumeration --------------------------------------------------------------------

    private static <T> T first(List<T> list) {
        return list.isEmpty() ? null : list.get(0);
    }

    /** Matching objects of one type within {@code distance} tiles of {@code anchor}, nearest first. */
    private static <T extends TileObject> List<T> scene(Class<T> type, Predicate<? super T> predicate,
                                                        WorldPoint anchor, int distance) {
        if (anchor == null) return Collections.emptyList();
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Client client = Microbot.getClient();
            LocalPoint anchorLocal = LocalPoint.fromWorld(client.getTopLevelWorldView(), anchor);
            if (anchorLocal == null && client.getLocalPlayer() != null) anchorLocal = client.getLocalPlayer().getLocalLocation();
            if (anchorLocal == null) return Collections.<T>emptyList();
            int radius = Math.min(distance, Constants.SCENE_SIZE) * Perspective.LOCAL_TILE_SIZE;
            Map<T, Integer> distances = new IdentityHashMap<>();
            List<T> matches = new ArrayList<>();
            LocalPoint from = anchorLocal;
            Microbot.getRs2TileObjectCache().getStream()
                    .map(Rs2TileObjectModel::getTileObject)
                    .filter(type::isInstance)
                    .map(type::cast)
                    .forEach(object -> {
                        LocalPoint local = object.getLocalLocation();
                        if (local == null || local.distanceTo(from) > radius || !predicate.test(object)) return;
                        matches.add(object);
                        distances.put(object, local.distanceTo(from));
                    });
            matches.sort(Comparator.comparingInt(distances::get));
            return matches;
        }).orElseGet(ArrayList::new);
    }

    private static int objectIdSafe(TileObject object) {
        return object == null ? -1 : Microbot.getClientThread().runOnClientThreadOptional(object::getId).orElse(-1);
    }
}
