package net.runelite.client.plugins.microbot.droefficientwalker;

import java.util.*;
import java.util.function.BooleanSupplier;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.*;
import net.runelite.client.plugins.microbot.droefficientwalker.support.TransportFactory;
import net.runelite.client.plugins.microbot.droefficientwalker.support.TransportObjectClick;
import net.runelite.client.plugins.microbot.droefficientwalker.support.TransportResources;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.droefficientwalker.compat.SceneObjects;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.Rs2Pvp;
import net.runelite.client.plugins.microbot.util.poh.data.*;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.api.widgets.Widget;

/** A coordinate-free house leg: entry -> observed house -> selected facility -> external landing. */
public final class MaxPohSupport {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(MaxPohSupport.class);
    private static Client client;
    private static EfficientWalkerConfig config;
    private static ConfigManager settings;
    private static String profile;
    private static Set<String> known = Set.of();
    private static volatile List<Transport> routes = List.of();
    private static List<Transport> networkDestinations;
    private static int houseSince = -1, lastHouseScan = -100;
    private static boolean panelFilledThisVisit;
    private static int loggedFingerprint;
    private static volatile Set<Entry> planningEntries;
    // Multiple POH facility polls in the same client tick must not rescan the entire scene.
    // Retain only a tick-scoped snapshot; a new scene/tick always gets a fresh read.
    private static int houseObjectScanTick = -1;
    private static Scene houseObjectScanScene;
    private static List<TileObject> houseObjectScanSnapshot = List.of();
    private static final String SNAPSHOT_KEY = "maxPohFacilitiesV1";
    // Furniture confirmed in the supplied 20261011-154103-657 recording. Nexus
    // destinations still come from the account's configured slots, never all spells.
    private static final Set<String> RECORDED_MAX_HOUSE = Set.of(
        "BOX:ORNATE", "TREE", "RING", "NEXUS", "DIGSITE", "OBELISK");
    private MaxPohSupport() { }

    static void configure(Client c, EfficientWalkerConfig cfg, ConfigManager manager) {
        client = c; config = cfg; settings = manager;
        profile = null; known = Set.of(); routes = List.of(); networkDestinations = null;
        houseSince = -1; lastHouseScan = -100; loggedFingerprint = 0;
        planningEntries = null;
        houseObjectScanTick = -1;
        houseObjectScanScene = null;
        houseObjectScanSnapshot = List.of();
    }

    /** Called on the client thread. Never teleports, opens menus, or stores instance coordinates. */
    static void observe() {
        if (config == null || !config.useMaxPoh()) { routes = List.of(); return; }
        String current = settings == null ? null : settings.getRSProfileKey();
        if (!Objects.equals(current, profile)) {
            profile = current;
            known = settings == null ? Set.of() : decode(settings.getRSProfileConfiguration(EfficientWalkerConfig.GROUP, SNAPSHOT_KEY));
            routes = List.of(); houseSince = -1;
        }
        if (!inHouse() || client.getVarbitValue(VarbitID.POH_BUILDING_MODE) != 0) { houseSince = -1; panelFilledThisVisit = false; return; }
        if (houseSince < 0) houseSince = client.getTickCount();
        if (client.getTickCount() - houseSince < 4 || client.getTickCount() - lastHouseScan < 10) return;
        lastHouseScan = client.getTickCount();
        if (!panelFilledThisVisit) { panelFilledThisVisit = true; net.runelite.client.plugins.microbot.droefficientwalker.compat.PohPanelDetect.autoDetect(); }
        Set<String> detected = new TreeSet<>();
        for (TileObject object : houseObjects()) {
            ObjectComposition def = SceneObjects.convertToObjectComposition(object);
            if (def == null) continue;
            detect(object.getId(), def.getName(), detected);
            for (PohPortal portal : PohPortal.values())
                for (int id : portal.getObjectIds()) if (id == object.getId()) detected.add("PORTAL:" + portal.name());
        }
        if (detected.contains("NEXUS"))
            for (NexusPortal portal : NexusPortal.getAvailableTeleports()) detected.add("NEXUS:" + portal.name());
        if (!detected.equals(known)) {
            known = Set.copyOf(detected);
            if (settings != null && profile != null)
                settings.setRSProfileConfiguration(EfficientWalkerConfig.GROUP, SNAPSHOT_KEY, String.join(",", detected));
        }
    }

    static Set<String> decode(String text) {
        return text == null || text.isBlank() ? Set.of() : Set.copyOf(Arrays.asList(text.split(",")));
    }

    /** Read the existing Microbot house settings without depending on the Swing panel or its inside tile. */
    static Set<String> configuredFacilities(ConfigManager manager) {
        if (manager == null) return Set.of();
        Set<String> result = new TreeSet<>();
        Map<String,String> features = Map.of("mountedGlory", "GLORY", "mountedDigsite", "DIGSITE",
            "mountedXerics", "XERIC", "mountedMyths", "MYTHICAL", "fairyRing", "RING",
            "spiritTree", "TREE", "wildyObelisk", "OBELISK");
        features.forEach((key, token) -> {
            if (Boolean.parseBoolean(manager.getConfiguration(ShortestPathPlugin.CONFIG_GROUP, key))) result.add(token);
        });
        String box = manager.getConfiguration(ShortestPathPlugin.CONFIG_GROUP, "jewelleryBox");
        if (box != null && Arrays.stream(JewelleryBoxType.values()).anyMatch(b -> b != JewelleryBoxType.NONE && b.name().equals(box)))
            result.add("BOX:" + box);
        for (String[] list : new String[][]{{"Portal Room", "PORTAL:"}, {"Nexus Teleport", "NEXUS:"}}) {
            String stored = manager.getConfiguration(ShortestPathPlugin.CONFIG_GROUP, list[0]);
            if (stored != null) for (String name : stored.split(",")) {
                String value = name.trim();
                boolean valid = list[1].equals("PORTAL:")
                    ? Arrays.stream(PohPortal.values()).anyMatch(p -> p.name().equals(value))
                    : Arrays.stream(NexusPortal.values()).anyMatch(p -> p.name().equals(value));
                if (valid) result.add(list[1] + value);
            }
        }
        return Set.copyOf(result);
    }

    private static Set<String> availableFacilities() {
        Set<String> configured = configuredFacilities(settings);
        // A live scan is authoritative. Before one is available, use existing settings;
        // a fresh installation can use the furniture in the user's supplied max-house recording.
        Set<String> result = new TreeSet<>(!configured.isEmpty() ? configured
            : !known.isEmpty() ? known : RECORDED_MAX_HOUSE);
        if (result.contains("NEXUS") || result.stream().anyMatch(f -> f.startsWith("NEXUS:")))
            for (NexusPortal portal : NexusPortal.getAvailableTeleports()) result.add("NEXUS:" + portal.name());
        return result;
    }

    static void detect(int id, String name, Set<String> result) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (id == 29229 || n.contains("spiritual fairy tree")) { result.add("RING"); result.add("TREE"); }
        else if (n.equals("fairy ring")) result.add("RING");
        else if (n.equals("spirit tree")) result.add("TREE");
        if (id == 29156 || n.contains("ornate jewellery box")) result.add("BOX:ORNATE");
        else if (n.contains("fancy jewellery box")) result.add("BOX:FANCY");
        else if (n.contains("basic jewellery box")) result.add("BOX:BASIC");
        if (Arrays.asList(NexusPortal.PORTAL_IDS).contains(id) || n.equals("portal nexus")) result.add("NEXUS");
        if (Arrays.asList(MountedDigsite.IDS).contains(id) || n.equals("digsite pendant")) result.add("DIGSITE");
        if (Arrays.asList(MountedXerics.IDS).contains(id)) result.add("XERIC");
        if (id == 31554) result.add("OBELISK");
        if (n.equals("amulet of glory")) result.add("GLORY");
        if (n.contains("mythical cape")) result.add("MYTHICAL");
    }

    /** Publish immutable external endpoints once per planning snapshot. All game reads are client-thread bound. */
    static void refresh() {
        if (config == null || !config.useMaxPoh() || !config.useTeleportations()) { routes = List.of(); return; }
        if (client != null && !client.isClientThread()) {
            Microbot.getClientThread().invoke((java.util.function.Supplier<Boolean>) () -> { refresh(); return true; });
            return;
        }
        if (config == null || !config.useMaxPoh() || !config.useTeleportations()) { routes = List.of(); return; }
        observe();
        Set<String> known = availableFacilities();
        List<Exit> exits = new ArrayList<>();
        HouseLocation house = currentHouse();
        if (house != null) exits.add(new Exit("EXIT", house.name().replace('_', ' '), house.getPortalLocation(), null));
        for (JewelleryBoxType box : JewelleryBoxType.values()) if (known.contains("BOX:" + box.name()))
            for (JewelleryBox tp : box.getAvailableTeleports()) {
                if (tp == JewelleryBox.FORTIS_COLOSSEUM && Microbot.getVarbitPlayerValue(net.runelite.api.gameval.VarPlayerID.COLOSSEUM_GLORY) < 12000) continue;
                Exit exit = new Exit("BOX", tp.getLocation().getDestination(), tp.getDestination(), tp);
                copyPohGates(exit, tp); exits.add(exit);
            }
        for (NexusPortal tp : NexusPortal.values()) if (known.contains("NEXUS:" + tp.name()))
            exits.add(new Exit("NEXUS", tp.getText(), tp.getDestination(), tp));
        if (known.contains("DIGSITE")) for (MountedDigsite tp : MountedDigsite.values()) {
            if (tp == MountedDigsite.FOSSIL_ISLAND && Rs2Player.getQuestState(Quest.BONE_VOYAGE) != QuestState.FINISHED) continue;
            if (tp == MountedDigsite.LITHKREN && Rs2Player.getQuestState(Quest.DRAGON_SLAYER_II) != QuestState.FINISHED) continue;
            exits.add(new Exit("DIGSITE", tp.getDestinationName(), tp.getDestination(), tp));
        }
        if (known.contains("XERIC")) for (MountedXerics tp : MountedXerics.values())
            exits.add(new Exit("XERIC", tp.getDestinationName(), tp.getDestination(), tp));
        if (known.contains("GLORY")) for (MountedGlory tp : MountedGlory.values())
            exits.add(new Exit("GLORY", tp.getDestinationName(), tp.getDestination(), tp));
        if (known.contains("MYTHICAL")) for (MountedMythical tp : MountedMythical.values())
            exits.add(new Exit("MYTHICAL", "Teleport", tp.getDestination(), tp));
        for (PohPortal tp : PohPortal.values()) if (known.contains("PORTAL:" + tp.name()))
            exits.add(new Exit("PORTAL", tp.name().replace('_', ' '), tp.getDestination(), tp));
        if (networkDestinations == null) {
            Map<String, Transport> unique = new LinkedHashMap<>();
            for (Set<Transport> group : TransportResources.loadAllFromResources().values()) for (Transport tp : group) {
                if (tp.getType() != TransportType.FAIRY_RING && tp.getType() != TransportType.SPIRIT_TREE
                    && tp.getType() != TransportType.WILDERNESS_OBELISK) continue;
                Transport dest = TransportFactory.destination(tp);
                if (dest.getDestination() != null) unique.putIfAbsent(dest.getType() + ":" + dest.getDisplayInfo(), dest);
            }
            networkDestinations = List.copyOf(unique.values());
        }
        for (Transport tp : networkDestinations) {
            String family = tp.getType() == TransportType.FAIRY_RING ? "RING"
                : tp.getType() == TransportType.SPIRIT_TREE ? "TREE" : "OBELISK";
            if (!known.contains(family)) continue;
            if (family.equals("RING") && (!config.useFairyRings()
                || Rs2Player.getQuestState(Quest.FAIRYTALE_II__CURE_A_QUEEN) == QuestState.NOT_STARTED)) continue;
            if (family.equals("TREE") && (!config.useSpiritTrees() || !treeEnabled(tp.getDestination()))) continue;
            if (family.equals("OBELISK") && (!config.allowWildernessTravel() || !config.useWildernessObelisks())) continue;
            Exit exit = new Exit(family, tp.getDisplayInfo(), tp.getDestination(), null);
            exit.gates = tp; exits.add(exit);
        }
        List<Transport> next = new ArrayList<>();
        boolean inside = inHouse();
        // One client-thread snapshot for worker-side graph admission, rather than
        // another round trip for each equivalent cape/tablet house destination.
        planningEntries = client.getWorldType() != null && client.getWorldType().contains(WorldType.MEMBERS)
            ? inside ? Set.of(Entry.INSIDE)
            : !Rs2Player.isTeleBlocked() && Rs2Pvp.getWildernessLevelFrom(Rs2Player.getWorldLocation()) <= 20
                ? Set.of(Entry.CAPE, Entry.TABLET) : Set.of()
            : Set.of();
        for (Exit exit : exits) {
            if (!gatesPass(exit.gates) || !landingAllowed(exit)) continue;
            if (inside) next.add(new Leg(exit, Entry.INSIDE));
            else {
                next.add(new Leg(exit, Entry.CAPE)); next.add(new Leg(exit, Entry.TABLET));
            }
        }
        routes = List.copyOf(next);
        int fingerprint = fingerprint();
        if (config.enableDebugging() && fingerprint != loggedFingerprint) {
            loggedFingerprint = fingerprint;
            LOG.info("[EfficientWalker] Max POH house={} inside={} facilities={} externalLegs={}",
                house, inside, known, next.size());
        }
    }

    private static void copyPohGates(Exit exit, PohTeleport tp) {
        exit.gates = new Transport(null, tp.getDestination(),tp.displayInfo(),TransportType.POH,true,6);
        if (tp == JewelleryBox.TEARS_OF_GUTHIX) exit.gates.getQuests().put(Quest.TEARS_OF_GUTHIX,QuestState.FINISHED);
        if (tp == JewelleryBox.MISCELLANIA) exit.gates.getQuests().put(Quest.THRONE_OF_MISCELLANIA,QuestState.FINISHED);
        if (tp == JewelleryBox.DONDAKAN) exit.gates.getQuests().put(Quest.BETWEEN_A_ROCK,QuestState.FINISHED);
        if (tp == JewelleryBox.FARMING_GUILD)
            exit.point = onClient(() -> Rs2Player.getBoostedSkillLevel(Skill.FARMING) >= 45)
                ? new WorldPoint(1248,3725,0) : new WorldPoint(1248,3719,0);
    }

    private static boolean treeEnabled(WorldPoint p) {
        if (p.equals(new WorldPoint(2613,3855,0))) return config.spiritTreeEtceteria();
        if (p.equals(new WorldPoint(2800,3203,0))) return config.spiritTreeBrimhaven();
        if (p.equals(new WorldPoint(3058,3257,0))) return config.spiritTreePortSarim();
        if (p.equals(new WorldPoint(1693,3540,0))) return config.spiritTreeHosidius();
        if (p.equals(new WorldPoint(1251,3750,0))) return config.spiritTreeFarmingGuild();
        return true;
    }

    static boolean gatesPass(Transport tp) {
        if (tp == null) return true;
        int[] levels = tp.getSkillLevels();
        for (Skill skill : Skill.values())
            if (levels != null && skill.ordinal() < levels.length && levels[skill.ordinal()] > client.getBoostedSkillLevel(skill)) return false;
        for (var q : tp.getQuests().entrySet()) if (Rs2Player.getQuestState(q.getKey()) != q.getValue()) return false;
        for (var b : tp.getVarbits()) if (!b.matches(Microbot.getVarbitValue(b.getVarbitId()))) return false;
        for (var b : tp.getVarplayers()) if (!b.matches(Microbot.getVarbitPlayerValue(b.getVarplayerId()))) return false;
        return true;
    }

    private static boolean landingAllowed(Exit exit) {
        if (exit.family.equals("RING") && !config.useFairyRings()) return false;
        if (exit.family.equals("TREE") && (!config.useSpiritTrees() || !treeEnabled(exit.point))) return false;
        if (exit.family.equals("OBELISK")) return config.allowWildernessTravel() && config.useWildernessObelisks();
        return CatalogTeleportSupport.wildernessDestinationAllowed(exit.label, exit.point, config);
    }

    public static List<Transport> routes() { return routes; }
    public static boolean enabled() { return config != null && config.useMaxPoh() && config.useTeleportations(); }
    public static int fingerprint() { return routes.isEmpty() ? 0 : routes.stream().map(Transport::getDisplayInfo).collect(java.util.stream.Collectors.toList()).hashCode(); }
    public static boolean isLeg(Transport tp) { return tp instanceof Leg; }
    public static boolean isHouseEdge(net.runelite.client.plugins.microbot.droefficientwalker.support.Rs2TransportEdge edge) {
        return edge != null && "Max POH".equals(edge.getAction());
    }
    public static boolean requiresStaff(net.runelite.client.plugins.microbot.droefficientwalker.support.Rs2TransportEdge edge) {
        return isHouseEdge(edge) && edge.getDisplayInfo() != null && edge.getDisplayInfo().startsWith("Max POH ")
            && edge.getDisplayInfo().contains(" -> RING:")
            && edge.getItemRequirements().stream().anyMatch(r -> r.getStaffAlternatives().contains(772));
    }
    public static boolean usable(Transport tp) {
        if (!(tp instanceof Leg) || config == null || !config.useMaxPoh() || !config.useTeleportations()) return false;
        Leg leg = (Leg) tp;
        Set<Entry> snapshot = planningEntries;
        if (client != null && !client.isClientThread() && snapshot != null) return snapshot.contains(leg.entry);
        return onClient(() -> client != null && client.getWorldType().contains(WorldType.MEMBERS)
            && (leg.entry == Entry.INSIDE ? inHouse() : !Rs2Player.isTeleBlocked()
                && Rs2Pvp.getWildernessLevelFrom(Rs2Player.getWorldLocation()) <= leg.getMaxWildernessLevel()));
    }

    /** A house hop costs a teleport, a house load and a facility; walking a short way is always better.
     *  A landing inside the landed radius would also count as complete at once and loop the planner. */
    static boolean landsNear(Transport tp, WorldPoint start) {
        WorldPoint landing = tp == null ? null : tp.getDestination();
        return landing != null && start != null && landing.getPlane() == start.getPlane()
            && landing.distanceTo2D(start) <= NEAR_LANDING_TILES;
    }
    static final int NEAR_LANDING_TILES = 15;
    static final int HOUSE_SETTLE_MIN_MS = 1200, HOUSE_SETTLE_MAX_MS = 1500;

    static final int ACTION_PAUSE_MIN_MS = 150, ACTION_PAUSE_MAX_MS = 350;

    static int pauseMillis(boolean houseLoading) {
        int min = houseLoading ? HOUSE_SETTLE_MIN_MS : ACTION_PAUSE_MIN_MS;
        int max = houseLoading ? HOUSE_SETTLE_MAX_MS : ACTION_PAUSE_MAX_MS;
        return java.util.concurrent.ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    /** Reuse the observed-house executor for legacy furniture without enabling the Max POH planner. */
    public static boolean executeFacility(PohTeleport teleport) {
        Exit exit = facilityExit(teleport);
        return exit != null && new Execution(false).run(new Leg(exit, Entry.INSIDE));
    }

    static Exit facilityExit(PohTeleport teleport) {
        if (teleport == null || teleport.getDestination() == null) return null;
        String family, label;
        if (teleport instanceof JewelleryBox) { family = "BOX"; label = ((JewelleryBox) teleport).getLocation().getDestination(); }
        else if (teleport instanceof NexusPortal) { family = "NEXUS"; label = ((NexusPortal) teleport).getText(); }
        else if (teleport instanceof net.runelite.client.plugins.microbot.droefficientwalker.support.poh.NexusPortal) {
            family = "NEXUS"; label = ((net.runelite.client.plugins.microbot.droefficientwalker.support.poh.NexusPortal) teleport).getText();
        }
        else if (teleport instanceof MountedDigsite) { family = "DIGSITE"; label = ((MountedDigsite) teleport).getDestinationName(); }
        else if (teleport instanceof MountedXerics) { family = "XERIC"; label = ((MountedXerics) teleport).getDestinationName(); }
        else if (teleport instanceof MountedGlory) { family = "GLORY"; label = ((MountedGlory) teleport).getDestinationName(); }
        else if (teleport instanceof MountedMythical) { family = "MYTHICAL"; label = "Teleport"; }
        else if (teleport instanceof PohPortal) { family = "PORTAL"; label = ((PohPortal) teleport).name().replace('_', ' '); }
        else return null;
        Exit exit = new Exit(family, label, teleport.getDestination(), teleport);
        copyPohGates(exit, teleport);
        return exit;
    }

    static boolean canTravelWithCarriedSupplies() {
        return routes.stream().filter(MaxPohSupport::usable).anyMatch(t -> t.getItemRequirements().stream()
            .allMatch(r -> r.isSatisfiedBy(id -> Rs2Inventory.itemQuantity(id) + (TravelEquipment.isWearing(id) ? 1 : 0))));
    }

    enum Entry { INSIDE, CAPE, TABLET }
    static final class Exit {
        final String family, label; WorldPoint point; final PohTeleport teleport; Transport gates;
        Exit(String family, String label, WorldPoint point, PohTeleport teleport) {
            this.family = family; this.label = label; this.point = point; this.teleport = teleport;
        }
    }
    static final class Leg extends Transport {
        final Exit exit; final Entry entry;
        Leg(Exit exit, Entry entry) {
            super(null, exit.point,
                "Max POH " + entry + " -> " + exit.family + ": " + exit.label,
                TransportType.TELEPORTATION_ITEM,
                true, "Max POH", exit.family + ":" + exit.label + ":" + entry, -1,
                exit.family.equals("EXIT") ? (entry == Entry.INSIDE ? 4 : 16)
                    : entry == Entry.INSIDE ? 16 : 28);
            this.exit = exit; this.entry = entry;
            if (entry == Entry.CAPE) setItemIdRequirements(Set.of(Set.of(9789,9790)));
            if (entry == Entry.TABLET) setItemIdRequirements(Set.of(Set.of(8013)));
            if (exit.family.equals("RING") && Microbot.getVarbitValue(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE) != 1)
                getItemRequirements().add(new TransportItemRequirement(Map.of(772,1,9084,1), Set.of(772,9084),Set.of(),false));
        }
        @Override public boolean isConsumable() { return entry == Entry.TABLET; }
        @Override public int getMaxWildernessLevel() { return entry == Entry.INSIDE ? 0 : 20; }
    }

    public static boolean insideHouse() {
        return onClient(() -> {
            WorldView view = client.getTopLevelWorldView();
            Scene scene = view == null ? null : view.getScene();
            // Ordinary outdoor ticks must never scan every tile looking for a house portal.
            return scene != null && scene.isInstance()
                && (inHouse() || client.getVarbitValue(VarbitID.POH_BUILDING_MODE) != 0);
        });
    }

    static boolean inHouse() {
        // POH objects can have template coordinates in the general cache. Presence and
        // clicks use the loaded scene rather than a distance/nearest query on those coordinates.
        if (client == null) return false;
        if (!client.isClientThread()) return onClient(MaxPohSupport::inHouse);
        if (client.getGameState() != GameState.LOGGED_IN) return false;
        if (Microbot.getRs2TileObjectCache() != null
            && !Microbot.getRs2TileObjectCache().query().withId(4525).toList().isEmpty()) return true;
        return houseObjects().stream().anyMatch(o -> o.getId() == 4525);
    }

    static HouseLocation currentHouse() {
        if (client == null) return null;
        int location = client.getVarbitValue(VarbitID.POH_HOUSE_LOCATION);
        return Arrays.stream(HouseLocation.values()).filter(h -> h.getVarbitValue() == location).findFirst().orElse(null);
    }

    private static List<TileObject> houseObjects() {
        WorldView view = client == null ? null : client.getTopLevelWorldView();
        Scene scene = view == null ? null : view.getScene();
        boolean clientTick = client != null && client.isClientThread();
        int tick = clientTick ? client.getTickCount() : -1;
        if (clientTick && houseObjectScanTick == tick && houseObjectScanScene == scene)
            return houseObjectScanSnapshot;
        Tile[][][] tiles = scene == null ? null : scene.getTiles();
        Set<TileObject> result = Collections.newSetFromMap(new IdentityHashMap<>());
        if (tiles != null) for (Tile[][] plane : tiles) if (plane != null)
            for (Tile[] row : plane) if (row != null) for (Tile tile : row) if (tile != null) {
                if (tile.getGameObjects() != null) Collections.addAll(result, tile.getGameObjects());
                result.add(tile.getDecorativeObject()); result.add(tile.getWallObject()); result.add(tile.getGroundObject());
            }
        if (result.isEmpty() && Microbot.getRs2TileObjectCache() != null)
            result.addAll(Microbot.getRs2TileObjectCache().query().toList());
        result.remove(null);
        List<TileObject> snapshot = new ArrayList<>(result);
        if (clientTick)
        {
            houseObjectScanSnapshot = snapshot;
            houseObjectScanScene = scene;
            houseObjectScanTick = tick;
        }
        return snapshot;
    }

    static boolean leaveHouse() {
        // Furniture travel is deliberately unavailable in construction mode, but exiting
        // the house must ALWAYS work there. House Options has a dedicated Leave action
        // and does not depend on the normal-mode exit portal object being interactable.
        if (onClient(() -> insideHouse() && client.getVarbitValue(VarbitID.POH_BUILDING_MODE) != 0)) {
            String step = Microbot.getClientThread().invoke(
                net.runelite.client.plugins.microbot.droefficientwalker.support.HouseExitControls::next);
            return step != null && net.runelite.client.plugins.microbot.droefficientwalker.support.HouseExitControls.click(step);
        }
        Leg exit = Microbot.getClientThread().invoke(() -> {
            HouseLocation house = currentHouse();
            return house == null ? null : new Leg(new Exit("EXIT", house.name(), house.getPortalLocation(), null), Entry.INSIDE);
        });
        return exit != null && execute(exit);
    }

    public static boolean execute(Transport transport) {
        return transport instanceof Leg && new Execution(true).run((Leg) transport);
    }

    /** Per-call cancellation policy; legacy furniture does not depend on the Max POH toggle. */
    static final class Execution {
        private final boolean requireMaxPoh;
        Execution(boolean requireMaxPoh) { this.requireMaxPoh = requireMaxPoh; }
        boolean active() {
            return client != null && !client.isClientThread() && config != null && config.useTeleportations()
                && (!requireMaxPoh || config.useMaxPoh()) && !Thread.currentThread().isInterrupted()
                && !Microbot.pauseAllScripts.get()
                && !net.runelite.client.plugins.microbot.util.input.InputArbiter.isHuman();
        }
        boolean pause(boolean houseLoading) {
            if (!active()) return false;
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(pauseMillis(houseLoading));
            while (active()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) return true;
                try {
                    java.util.concurrent.TimeUnit.NANOSECONDS.sleep(Math.min(remaining, 50_000_000L));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt(); return false;
                }
            }
            return false;
        }
        boolean run(Leg leg) {
        if (!active()) return false;
        if (requireMaxPoh && !onClient(() -> usable(leg))) return false;
        if (!requireMaxPoh && !onClient(MaxPohSupport::inHouse)) return false;
        if (onClient(() -> client.getVarbitValue(VarbitID.POH_BUILDING_MODE) != 0 && inHouse())) return false;
        if (!onClient(MaxPohSupport::inHouse)) {
            if (!onClient(() -> Rs2Pvp.getWildernessLevelFrom(Rs2Player.getWorldLocation()) <= leg.getMaxWildernessLevel())) return false;
            // The actual outside portal is already visible: do not consume another
            // tablet/cape teleport just to land at this same portal again.
            boolean atOwnPortal = onClient(() -> objectFor("ENTRY",null) != null);
            boolean entered = atOwnPortal;
            if (!atOwnPortal) switch (leg.entry) {
                case CAPE:
                    for (int id : new int[]{9789,9790}) {
                        if (TravelEquipment.isWearing(id)) { entered = Rs2Equipment.interact(id,"Tele to POH"); break; }
                        if (Rs2Inventory.hasItem(id)) { entered = Rs2Inventory.interact(id,"Tele to POH"); break; }
                    }
                    break;
                case TABLET:
                    // OSRS may be configured to teleport Outside (varbit 2187=1).
                    // Prefer an explicit interior teleport, with the existing
                    // observed outside-portal recovery retained as fallback.
                    entered = (onClient(() -> Microbot.getVarbitValue(2187) == 1)
                        && Rs2Inventory.interact(8013,"Inside"))
                        || Rs2Inventory.interact(8013,"Break");
                    break;
                default: return false;
            }
            if (!entered) return false;
            // A cape or ordinary Break action can land Outside. Confirm the actual
            // player's house entrance before interacting; never break a second tablet.
            if (!atOwnPortal && !waitFor(() -> onClient(() -> inHouse() || objectFor("ENTRY",null) != null), 8000)) return false;
            if (!onClient(MaxPohSupport::inHouse)) {
                if (!clickFamily("ENTRY", "Home") || !waitFor(() -> onClient(MaxPohSupport::inHouse), 8000)) return false;
            }
        }
        if (!waitFor(() -> onClient(() -> inHouse() && objectFor(leg.exit.family, leg.exit) != null), 8000)) {
            LOG.warn("[EfficientWalker][POH] facility not visible after house load family={} destination={} inside={} world={}",
                leg.exit.family, leg.exit.label, onClient(MaxPohSupport::inHouse), client.getWorld());
            return false;
        }
        // Observe house/furniture readiness before settling and before any inside input (including staff equip).
        if (!pause(true)) return false;
        if (leg.exit.family.equals("RING") && onClient(() -> Microbot.getVarbitValue(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE) != 1)) {
            if (!onClient(() -> TravelEquipment.isWearing(772,9084))) {
                int staff = Rs2Inventory.hasItem(772) ? 772 : Rs2Inventory.hasItem(9084) ? 9084 : -1;
                if (staff < 0 || !pause(false) || !Rs2Inventory.equip(staff)
                    || !waitFor(() -> onClient(() -> TravelEquipment.isWearing(staff)),2800)) return false;
            }
        }
        onClient(() -> { observe(); return true; });
        if (!onClient(() -> gatesPass(leg.exit.gates) && landingAllowed(leg.exit))) return false;
        String action = leg.exit.family.equals("EXIT") ? "Enter"
            : leg.exit.family.equals("TREE") ? "Tree" : leg.exit.family.equals("RING") ? "Ring-configure"
            : leg.exit.family.equals("OBELISK") ? "Teleport to destination" : "Teleport Menu";
        TileObject object = Microbot.getClientThread().invoke(() -> objectFor(leg.exit.family, leg.exit));
        if (object == null) return false;
        if (leg.exit.family.equals("PORTAL")) {
            String[] actions = Microbot.getClientThread().invoke(() -> SceneObjects.convertToObjectComposition(object).getActions());
            action = Arrays.stream(actions).filter(a -> a != null && !a.equalsIgnoreCase("Remove")).findFirst().orElse("Enter");
        } else if (leg.exit.family.equals("GLORY") || leg.exit.family.equals("MYTHICAL")) action = leg.exit.label;
        else if (leg.exit.family.equals("TREE") && !onClient(() -> hasAction(object,"Tree"))) action = "Travel";
        else if (leg.exit.family.equals("RING") && !onClient(() -> hasAction(object,"Ring-configure"))) action = "Configure";
        else if (leg.exit.family.equals("DIGSITE") || leg.exit.family.equals("XERIC")) {
            if (onClient(() -> hasAction(object, leg.exit.label))) action = leg.exit.label;
        }
        boolean nexus = leg.exit.family.equals("NEXUS");
        // An open Nexus covers the furniture, so a click aimed at any object lands on whichever
        // destination row is under it. Once open, only the verified row's shortcut is input.
        if (!nexus && !closeNexus()) return false;
        if (!(nexus && onClient(MaxPohSupport::nexusOpen)) && (!pause(false) || !TransportObjectClick.click(object, action))) return false;
        if (nexus) {
            if (!waitForMenu(() -> onClient(() -> nexusRow(leg.exit.label) != null))) {
                LOG.warn("[EfficientWalker][POH] Nexus row unavailable destination={} widgetOpen={} moving={}",
                    leg.exit.label, onClient(MaxPohSupport::nexusOpen), Rs2Player.isMoving());
                closeNexus(); return false;
            }
            String shortcut = Microbot.getClientThread().invoke(() -> {
                Widget row = nexusRow(leg.exit.label);
                return row == null ? null : shortcut(row.getText());
            });
            if (!pause(false)) return false;
            if (shortcut != null) {
                Rs2Keyboard.typeString(shortcut);
            } else {
                // Some client revisions expose a clickable destination row with no
                // keyboard shortcut. Click ONLY the unique exact-text, visible row;
                // nexusRow() rejects ambiguous matches and disabled options.
                Widget row = Microbot.getClientThread().invoke(() -> nexusRow(leg.exit.label));
                if (row == null || !Rs2Widget.clickWidget(row)) {
                    LOG.warn("[EfficientWalker][POH] Nexus exact destination row not clickable destination={}", leg.exit.label);
                    closeNexus(); return false;
                }
                LOG.info("[EfficientWalker][POH] Nexus selected by exact row destination={}", leg.exit.label);
            }
        } else if (leg.exit.family.equals("RING")) {
            if (!configureRing(leg.exit.label)) return false;
        } else if (!leg.exit.family.equals("EXIT") && !leg.exit.family.equals("PORTAL") && !leg.exit.family.equals("GLORY") && !leg.exit.family.equals("MYTHICAL")
            && !action.equalsIgnoreCase(leg.exit.label)) {
            int group = leg.exit.family.equals("BOX") ? 590 : leg.exit.family.equals("OBELISK") ? 475 : 187;
            if (!waitForMenu(() -> onClient(() -> exitWidget(group, leg.exit.label) != null))) {
                LOG.warn("[EfficientWalker][POH] menu row unavailable family={} destination={} group={} menuOpen={} moving={}",
                    leg.exit.family, leg.exit.label, group, onClient(() -> {
                        Widget root = client.getWidget(group, 0);
                        return root != null && !root.isHidden();
                    }), Rs2Player.isMoving());
                return false;
            }
            Widget widget = Microbot.getClientThread().invoke(() -> exitWidget(group, leg.exit.label));
            if (widget == null || !pause(false) || !Rs2Widget.clickWidget(widget)) {
                LOG.warn("[EfficientWalker][POH] menu row not clicked family={} destination={}",
                    leg.exit.family, leg.exit.label);
                return false;
            }
        }
        return waitFor(() -> {
            if (onClient(() -> Rs2Widget.isWildernessInterfaceOpen())) {
                if (!config.allowWildernessTravel()) return false;
                if (!pause(false)) return false;
                Rs2Widget.enterWilderness();
            }
            return onClient(() -> landed(leg.exit.point, client.getLocalPlayer() == null ? null : client.getLocalPlayer().getWorldLocation(), inHouse()));
        }, 16000);
    }

    private boolean waitFor(BooleanSupplier condition, long millis) {
        boolean[] reached = {false};
        net.runelite.client.plugins.microbot.util.Global.sleepUntil(() -> {
            if (!active()) return true;
            reached[0] = condition.getAsBoolean();
            return reached[0];
        }, (int) millis);
        return reached[0] && active();
    }

    /** Furniture can be a long walk from where the player stands; its menu only opens on arrival. */
    private boolean waitForMenu(BooleanSupplier ready) {
        if (waitFor(ready, 6000)) return true;
        long deadline = System.currentTimeMillis() + 14000;
        while (active() && System.currentTimeMillis() < deadline && Rs2Player.isMoving())
            if (waitFor(ready, 1200)) return true;
        return false;
    }

    private boolean clickFamily(String family, String action) {
        TileObject object = Microbot.getClientThread().invoke(() -> objectFor(family,null));
        return object != null && pause(false) && TransportObjectClick.click(object,action);
    }

    private boolean closeNexus() {
        if (!onClient(MaxPohSupport::nexusOpen)) return true;
        if (!pause(false)) return false;
        Rs2Keyboard.keyPress(java.awt.event.KeyEvent.VK_ESCAPE);
        return waitFor(() -> !onClient(MaxPohSupport::nexusOpen), 1800);
    }

    private boolean configureRing(String code) {
        FairyRingSelection selection = new FairyRingSelection();
        boolean[] confirmed = {false};
        boolean finished = waitFor(() -> {
            int child = Microbot.getClientThread().invoke(() -> {
                int[] values = {client.getVarbitValue(3985),client.getVarbitValue(3986),client.getVarbitValue(3987)};
                int candidate = FairyRingNetwork.getNextDialWidgetChild(code,values);
                return selection.step(code,values,client.getTickCount(), candidate >= 0 && FairyRingNetwork.isFirstWidgetActionEnabled(client,client.getWidget(398,candidate)));
            });
            if (child < 0) return true;
            if (child > 0) {
                if (!pause(false)) return false;
                boolean clicked = Rs2Widget.clickWidget(398,child);
                if (child == 26) { confirmed[0] = clicked; return clicked; }
            }
            return false;
        }, 22000);
        return finished && confirmed[0];
    }
    }

    /** InterfaceID.TELENEXUS_TELEPORT. */
    private static final int NEXUS_GROUP = 17;

    static boolean landed(WorldPoint expected, WorldPoint actual, boolean house) {
        return !house && actual != null && expected.getPlane() == actual.getPlane() && expected.distanceTo2D(actual) <= 6;
    }
    private static boolean onClient(BooleanSupplier task) {
        if (client == null) return false;
        if (client != null && client.isClientThread()) return task.getAsBoolean();
        return Microbot.getClientThread().invoke((java.util.function.Supplier<Boolean>)task::getAsBoolean);
    }
    private static boolean hasAction(TileObject object, String action) {
        ObjectComposition def = SceneObjects.convertToObjectComposition(object);
        return def != null && def.getActions() != null && Arrays.stream(def.getActions()).anyMatch(action::equalsIgnoreCase);
    }
    static boolean currentEntrance(int objectId, WorldPoint tile, HouseLocation house) {
        return house != null && tile != null && objectId == house.getPortalId()
            && tile.getPlane() == house.getPortalLocation().getPlane()
            && tile.distanceTo2D(house.getPortalLocation()) <= 10;
    }
    private static TileObject objectFor(String family, Exit exit) {
        return houseObjects().stream().filter(object -> {
            ObjectComposition def = SceneObjects.convertToObjectComposition(object);
            if (def == null) return false;
            Set<String> facilities = new HashSet<>(); detect(object.getId(),def.getName(),facilities);
            if (family.equals("EXIT")) return object.getId() == 4525 && hasAction(object,"Enter");
            if (family.equals("ENTRY")) {
                HouseLocation house = currentHouse();
                return currentEntrance(object.getId(),object.getWorldLocation(),house)
                    && "Portal".equals(def.getName()) && hasAction(object,"Home");
            }
            if (family.equals("PORTAL")) return exit != null && exit.teleport instanceof PohPortal
                && Arrays.stream(((PohPortal)exit.teleport).getObjectIds()).anyMatch(id -> id == object.getId());
            return facilities.contains(family) || family.equals("BOX") && facilities.stream().anyMatch(f -> f.startsWith("BOX:"));
        }).findFirst().orElse(null);
    }
    private static boolean nexusOpen() {
        Widget root = client.getWidget(NEXUS_GROUP, 0);
        return root != null && !root.isHidden();
    }
    /** The one destination row whose text, without its shortcut prefix, is exactly the label. */
    static Widget nexusRow(String label) {
        if (!nexusOpen()) return null;
        List<Widget> rows = new ArrayList<>();
        collect(client.getWidget(NEXUS_GROUP, 0), Collections.newSetFromMap(new IdentityHashMap<>()), rows);
        Widget match = null;
        for (Widget row : rows) {
            if (!nexusRowMatches(row.getText(), label)) continue;
            if (match != null) return null;
            match = row;
        }
        return match;
    }
    static boolean nexusRowMatches(String text, String label) {
        // Shortcuts vary by client revision; when absent we can still use the
        // exact, unique visible destination widget rather than guessing a key.
        return text != null && !text.contains("<str>") && !normalize(label).isEmpty()
            && menuDestination(text).equals(normalize(label));
    }
    private static void collect(Widget widget, Set<Widget> seen, List<Widget> out) {
        if (widget == null || !seen.add(widget) || widget.isHidden()) return;
        if (widget.getText() != null && !widget.getText().isEmpty()) out.add(widget);
        for (Widget[] children : new Widget[][]{widget.getStaticChildren(),widget.getDynamicChildren(),widget.getNestedChildren(),widget.getChildren()})
            if (children != null) for (Widget child : children) collect(child, seen, out);
    }
    static Widget exitWidget(int group, String label) {
        Widget[] roots = {client.getWidget(group,0),client.getWidget(group,1),client.getWidget(group,3)};
        if (roots != null) for (Widget root : roots) {
            Widget found = find(root,label,Collections.newSetFromMap(new IdentityHashMap<>()));
            if (found != null) return found;
        }
        return null;
    }
    private static Widget find(Widget widget, String label, Set<Widget> seen) {
        if (widget == null || !seen.add(widget) || widget.isHidden()) return null;
        String text = widget.getText();
        // Only select the exact, enabled destination. A substring match can select
        // "West Ardougne" when "Ardougne" was requested, or a container title.
        if (text != null && !text.contains("<str>") && menuDestination(text).equals(normalize(label))) return widget;
        for (Widget[] children : new Widget[][]{widget.getStaticChildren(),widget.getDynamicChildren(),widget.getNestedChildren(),widget.getChildren()})
            if (children != null) for (Widget child : children) { Widget found = find(child,label,seen); if (found != null) return found; }
        return null;
    }
    private static String normalize(String text) {
        return text == null ? "" : text.replaceAll("<[^>]+>", "").toLowerCase(Locale.ROOT).trim()
            .replaceFirst("^[0-9a-z]+\\s*:\\s*", "");
    }
    /** Remove a real shortcut token, never a letter from the destination itself. */
    static String menuDestination(String text) {
        if (text == null) return "";
        return normalize(text.replaceFirst("(?i)^\\s*<col=[0-9a-f]{3,8}>\\s*[0-9a-z]\\s*</col>\\s*[:.)-]?\\s*", ""));
    }
    static String shortcut(String text) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)<col=[0-9a-f]{3,8}>\\s*([0-9a-z])\\s*</col>").matcher(text == null ? "" : text);
        return m.find() ? m.group(1) : null;
    }

}
