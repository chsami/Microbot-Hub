package net.runelite.client.plugins.microbot.droslayer.walker;

import net.runelite.api.*;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.droslayer.walker.support.banking.Rs2WalkerBankingPlanner;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.droslayer.walker.support.Rs2TransportEdge;
import net.runelite.client.plugins.microbot.droslayer.walker.support.TransportResources;
import net.runelite.client.plugins.microbot.droslayer.walker.support.TransportExecutionRegistry;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.droslayer.support.SceneObjects;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.microbot.util.magic.Runes;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.Rs2Pvp;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Adds Dro's maintained minigame-teleport, charter-ship, Kourend minecart and selected
 * NPC-network data (including Elkoy's Tree Gnome Village shortcut) to the Dro
 * Walker graph without giving Dro's Pathfinder any route-selection authority.
 *
 * <p>The static TSV files are used as an encyclopedia only.  They are converted to the
 * v1.11.2 transport edges, then Efficient Walker's own planner decides
 * whether they beat walking, its native teleports, Spirit Trees, fairy rings and every other
 * route already supported by the engine.</p>
 *
 * <p>Minigame teleports are rebuilt from the player's current tile for each new target and are
 * admitted only while their shared 20-minute cooldown is ready. Charter routes retain the
 * quest/varbit requirements from Dro's table and are additionally fail-closed at runtime:
 * if a destination is absent from the charter interface for this account, that exact route is
 * blacklisted for the rest of the session and Efficient Walker replans.</p>
 */
final class NetworkTransportSupport
{
    private boolean magicMushtreesAllowed = true;

    void setMagicMushtreesAllowed(boolean allowed)
    {
        if (magicMushtreesAllowed != allowed) installedKey = null;
        magicMushtreesAllowed = allowed;
    }

    boolean magicMushtreesAllowed() { return magicMushtreesAllowed; }
    private static final Logger log = LoggerFactory.getLogger(NetworkTransportSupport.class);

    private static final int COINS = 995;

    // Balloon fuel is destination-specific.  The upstream balloon resource only stores
    // unlock varbits, not the one-log-per-flight fuel requirement, so Dro must gate it here.
    private static final WorldPoint BALLOON_ENTRANA = new WorldPoint(2808, 3354, 0);
    private static final WorldPoint BALLOON_TAVERLEY = new WorldPoint(2941, 3420, 0);
    private static final WorldPoint BALLOON_CRAFTING_GUILD = new WorldPoint(2923, 3302, 0);
    private static final WorldPoint BALLOON_VARROCK = new WorldPoint(3299, 3482, 0);
    private static final WorldPoint BALLOON_CASTLE_WARS = new WorldPoint(2459, 3110, 0);
    private static final WorldPoint BALLOON_GRAND_TREE = new WorldPoint(2478, 3456, 0);
    private static final int BALLOON_INTERACTION_COST = 12;

    // Maintained Lunar network transitions that are missing from the walker graph.
    // Keep the import deliberately narrow so the working global transport graph is untouched.
    private static final int PIRATES_COVE_MIN_X = 2205;
    private static final int PIRATES_COVE_MAX_X = 2232;
    private static final int PIRATES_COVE_MIN_Y = 3788;
    private static final int PIRATES_COVE_MAX_Y = 3806;
    private static final int LUNAR_ISLE_MIN_X = 2080;
    private static final int LUNAR_ISLE_MAX_X = 2145;
    private static final int LUNAR_ISLE_MIN_Y = 3880;
    private static final int LUNAR_ISLE_MAX_Y = 3945;
    private static final WorldPoint MOONCLAN_LANDING = new WorldPoint(2113, 3915, 0);

    // Lovakengj/Kourend minecart network from Dro minecarts.tsv.
    // The network costs 20 coins per ride until The Forsaken Tower completion state
    // (varbit 7796 == 11), after which rides are free.
    private static final int KOUREND_MINECART_OBJECT_ID = 28835;
    private static final int MINECART_MENU_GROUP_ID = 947;
    private static final int MINECART_MENU_CHOICES_CHILD_ID = 9;
    private static final int KOUREND_MINECART_FARE = 20;
    private static final int FORSAKEN_TOWER_MINECART_VARBIT = 7796;
    private static final int FORSAKEN_TOWER_MINECART_FREE_VALUE = 11;
    private static final int ADVENTURE_LOG_GROUP_ID = 187;
    private static final int ADVENTURE_LOG_CHOICES_CHILD_ID = 3;
    private static final int MINECART_TARGET_REGION_RADIUS = 180;
    private static final long MINECART_MENU_WAIT_MS = 6_000L;
    private static final long MINECART_LANDING_WAIT_MS = 12_000L;
    private static final WorldPoint[] AL_KHARID_GATE_WEST = {
        new WorldPoint(3267, 3227, 0), new WorldPoint(3267, 3228, 0)
    };
    private static final WorldPoint[] AL_KHARID_GATE_EAST = {
        new WorldPoint(3268, 3227, 0), new WorldPoint(3268, 3228, 0)
    };
    private static final int[] AL_KHARID_GATE_WEST_IDS = {2786, 2787};
    private static final int[] AL_KHARID_GATE_EAST_IDS = {2788, 2789};
    private static final int AL_KHARID_TOLL = 10;
    private static final int AL_KHARID_GATE_BORDER_X = 3267;
    private static final int AL_KHARID_MIN_Y = 3130;
    private static final int AL_KHARID_MAX_Y = 3330;


    // Mount Karuulm Slayer Dungeon rock barriers from Dro's maintained
    // shortest-path transport table. Keep these as a narrow overlay on the
    // stable v1.11.17 walker so dungeon replans can cross the room barriers
    // without changing any other route-selection or bank behavior.
    private static final int KARUULM_DUNGEON_ROCKS_ID = 34544;
    private static final String KARUULM_DUNGEON_ROCKS_ACTION = "Climb";
    private static final String KARUULM_DUNGEON_ROCKS_TARGET = "Rocks";
    private static final WorldPoint[][] KARUULM_DUNGEON_ROCK_EDGES = {
        {new WorldPoint(1303, 10205, 0), new WorldPoint(1301, 10205, 0)},
        {new WorldPoint(1303, 10206, 0), new WorldPoint(1301, 10206, 0)},
        {new WorldPoint(1301, 10205, 0), new WorldPoint(1303, 10205, 0)},
        {new WorldPoint(1301, 10206, 0), new WorldPoint(1303, 10206, 0)},
        {new WorldPoint(1311, 10214, 0), new WorldPoint(1311, 10216, 0)},
        {new WorldPoint(1312, 10214, 0), new WorldPoint(1312, 10216, 0)},
        {new WorldPoint(1311, 10216, 0), new WorldPoint(1311, 10214, 0)},
        {new WorldPoint(1312, 10216, 0), new WorldPoint(1312, 10214, 0)},
        {new WorldPoint(1320, 10205, 0), new WorldPoint(1322, 10205, 0)},
        {new WorldPoint(1320, 10206, 0), new WorldPoint(1322, 10206, 0)},
        {new WorldPoint(1322, 10205, 0), new WorldPoint(1320, 10205, 0)},
        {new WorldPoint(1322, 10206, 0), new WorldPoint(1320, 10206, 0)}
    };

    // Children of the Sun unlocks the direct Primio link between Varrock and Civitas.
    private static final WorldPoint PRIMIO_VARROCK = new WorldPoint(3280, 3412, 0);
    private static final WorldPoint PRIMIO_CIVITAS = new WorldPoint(1700, 3141, 0);
    private static final int PRIMIO_VARROCK_NPC_ID = 12888;
    private static final int PRIMIO_CIVITAS_NPC_ID = 12889;

    // Tree Gnome Village maze shortcut from Dro npcs.tsv.  The Spirit Tree lands
    // on the south-east side of the maze; the walker can handle the ground
    // leg/Loose Railing, then Elkoy's Follow transport jumps to the village side.
    // Both directions are injected so the shortcut is useful outside Farm Tree Run too.
    private static final WorldPoint ELKOY_VILLAGE_SIDE = new WorldPoint(2503, 3193, 0);
    private static final WorldPoint ELKOY_SOUTH_SIDE = new WorldPoint(2515, 3159, 0);
    private static final WorldPoint ELKOY_RAIL_SOUTH = new WorldPoint(2515, 3160, 0);
    private static final WorldPoint ELKOY_RAIL_SPIRIT_TREE_SIDE = new WorldPoint(2515, 3161, 0);
    private static final int ELKOY_NPC_ID = 4968;
    // Rs2Walker treats NPC transports as terminal travel: once the exact planned edge is next,
    // let the NPC interaction server-walk the final few tiles instead of waiting on the catalogue
    // origin or letting the generic object executor see Elkoy's synthetic id=-1 edge.
    private static final int ELKOY_INTERACT_RADIUS = 6;
    private static final int ELKOY_NPC_WALK_RADIUS = 20;
    private static final long ELKOY_LOAD_WAIT_MS = 4_000L;
    private static final long ELKOY_LANDING_WAIT_MS = 10_000L;

    // Fossil Island transport network. These mirror Dro's maintained shortest-path
    // transport data, but are executed here so the Efficient Walker never has to
    // understand Mycelium widgets / Fossil rowboat option menus itself.
    private static final WorldPoint FOSSIL_DIGSITE_BARGE = new WorldPoint(3362, 3445, 0);
    private static final WorldPoint FOSSIL_CAMP = new WorldPoint(3724, 3807, 0);
    private static final WorldPoint FOSSIL_NORTH = new WorldPoint(3734, 3893, 0);
    private static final WorldPoint FOSSIL_SEA = new WorldPoint(3763, 3899, 0);
    private static final WorldPoint FOSSIL_WRECK_BANK = new WorldPoint(3766, 3898, 0);
    private static final int FOSSIL_BARGE_GUARD_ID = 8012;
    private static final int FOSSIL_CAMP_ROWBOAT_ID = 30914;
    private static final int FOSSIL_NORTH_ROWBOAT_ID = 30915;
    private static final int FOSSIL_SEA_ROWBOAT_ID = 30919;
    private static final WorldPoint FOSSIL_HOLE_GROVE = new WorldPoint(3713, 3830, 0);
    private static final WorldPoint FOSSIL_HOLE_SHORE = new WorldPoint(3715, 3815, 0);
    private static final WorldPoint[] FOSSIL_HOLE_SHORE_ORIGINS = {
        new WorldPoint(3713, 3816, 0), new WorldPoint(3714, 3815, 0),
        new WorldPoint(3715, 3815, 0), new WorldPoint(3716, 3815, 0),
        new WorldPoint(3717, 3815, 0), new WorldPoint(3718, 3816, 0)
    };
    private static final WorldPoint[] FOSSIL_HOLE_GROVE_ORIGINS = {
        new WorldPoint(3713, 3830, 0), new WorldPoint(3714, 3830, 0),
        new WorldPoint(3715, 3830, 0)
    };
    private static final int RUBBER_CAP_MUSHROOM_ID = 30606;
    private static final WorldPoint RUBBER_CAP_DESTINATION = new WorldPoint(3685, 3756, 0);
    private static final WorldPoint[] RUBBER_CAP_ORIGINS = {
        new WorldPoint(3666, 3810, 0),
        new WorldPoint(3666, 3809, 0),
        new WorldPoint(3663, 3808, 0),
        new WorldPoint(3664, 3808, 0)
    };
    private static final long FOSSIL_LANDING_WAIT_MS = 12_000L;
    private static final long FOSSIL_FAST_ROWBOAT_MENU_WAIT_MS = 1_500L;

    private static final MushtreeStop[] MUSHTREE_STOPS = {
        // IMPORTANT: a Mushtree has two different coordinates in this bridge:
        //   1) route origins: walkable tiles the planner can actually reach before using it;
        //   2) object tile: the live Magic Mushtree tile used only for the physical Use click.
        // v12 accidentally used the live object tile as the route origin for the three 30924
        // trees. Those object tiles are occupied/non-walkable, so the planner ignored the custom
        // Mycelium edges and preferred the damaging Rubber cap mushroom instead.
        //
        // House on the Hill deliberately keeps the already-proven v9-v12 origin unchanged.
        new MushtreeStop("House on the Hill",
            new WorldPoint[]{new WorldPoint(3764, 3880, 1)},
            new WorldPoint(3764, 3880, 1), new WorldPoint(3764, 3879, 1), 30920),
        new MushtreeStop("Verdant Valley",
            new WorldPoint[]{new WorldPoint(3757, 3756, 0), new WorldPoint(3757, 3757, 0)},
            new WorldPoint(3758, 3756, 0), new WorldPoint(3760, 3758, 0), 30924),
        new MushtreeStop("Sticky Swamp",
            new WorldPoint[]{new WorldPoint(3676, 3755, 0), new WorldPoint(3676, 3756, 0)},
            new WorldPoint(3677, 3755, 0), new WorldPoint(3676, 3755, 0), 30924),
        new MushtreeStop("Mushroom Meadow",
            new WorldPoint[]{new WorldPoint(3676, 3871, 0), new WorldPoint(3676, 3872, 0)},
            new WorldPoint(3674, 3871, 0), new WorldPoint(3676, 3871, 0), 30924)
    };
    private static final long MUSHTREE_WIDGET_WAIT_MS = 5_000L;
    private static final long MUSHTREE_LANDING_WAIT_MS = 10_000L;

    // Dro quetzals.tsv (2.6.22 / commit 8bcc6520). Efficient Walker owns route
    // selection and execution; these coordinates/unlock varbits are only transport metadata.
    private static final QuetzalStop[] QUETZAL_STOPS = {
        new QuetzalStop(new WorldPoint(1389, 2901, 0), "Aldarin", -1),
        new QuetzalStop(new WorldPoint(1697, 3140, 0), "Civitas illa Fortis", -1),
        new QuetzalStop(new WorldPoint(1585, 3053, 0), "Hunter Guild", -1),
        new QuetzalStop(new WorldPoint(1510, 3221, 0), "Quetzacalli Gorge", -1),
        new QuetzalStop(new WorldPoint(1548, 2995, 0), "Sunset Coast", -1),
        new QuetzalStop(new WorldPoint(1437, 3171, 0), "The Teomat", -1),
        new QuetzalStop(new WorldPoint(1779, 3111, 0), "Fortis Colosseum", 9958),
        new QuetzalStop(new WorldPoint(1700, 3037, 0), "Outer Fortis", 9957),
        new QuetzalStop(new WorldPoint(1670, 2933, 0), "Colossal Wyrm Remains", 9956),
        new QuetzalStop(new WorldPoint(1446, 3108, 0), "Cam Torum Entrance", 9955),
        new QuetzalStop(new WorldPoint(1613, 3300, 0), "Salvager Overlook", 11379),
        new QuetzalStop(new WorldPoint(1226, 3091, 0), "Tal Teklan", -1),
        new QuetzalStop(new WorldPoint(1344, 3022, 0), "Kastori", 17757),
        new QuetzalStop(new WorldPoint(1411, 3361, 0), "Auburnvale", -1)
    };
    private static final int LAST_MINIGAME_TELEPORT_VARPLAYER = 888;
    private static final int MINIGAME_COOLDOWN_MINUTES = 20;

    /*
     * The graph charges four route-score units for every transport edge regardless of
     * animation/UI time.  Do not make these transports artificially attractive merely because of
     * that historical fixed edge cost.  These admission gates are deliberately conservative and
     * represent the real interaction/loading overhead before the edge is even offered to the graph.
     * They do not replace Efficient Walker's route calculation.
     */
    // A Grouping/minigame teleport is protected for 15 seconds after it is activated, which is
    // roughly 55 run tiles. Price it conservatively at 60 tiles: never offer a minigame
    // teleport unless its 60-tile time cost plus the run after landing is strictly shorter
    // than running directly.
    private static final int MINIGAME_FIXED_RUN_TILE_COST = 60;
    private static final int CHARTER_MIN_RAW_TILE_SAVINGS = 40;
    private static final int CHARTER_APPROX_SCORE = 20;

    // A small carried coin reserve unlocks Port Sarim/Karamja shipping and the Shilo cart.
    // Keeping this at 200 covers the ordinary island transitions without over-withdrawing.
    private static final int KARAMJA_TRAVEL_COIN_RESERVE = 200;
    private static final int KARAMJA_MIN_X = 2680;
    private static final int KARAMJA_MAX_X = 3005;
    private static final int KARAMJA_MIN_Y = 2880;
    private static final int KARAMJA_MAX_Y = 3265;

    private static final int MINIGAME_ORIGIN_RADIUS = 4;
    private static final int CHARTER_ORIGIN_RADIUS = 10;
    private static final int LANDING_RADIUS = 8;
    private static final long EXECUTION_TIMEOUT_MS = 75_000L;
    private static final long SHARED_TRANSPORT_LANDING_WAIT_MS = 2_000L;

    // Current Dro grouping/minigame interface components (same interface used by Rs2Walker).
    private static final int GROUPING_BUTTON_COMPONENT_ID = 46333957; // 707.5
    private static final int DROPDOWN_BUTTON_COMPONENT_ID = 4980760;  // 76.24
    private static final int MINIGAME_LIST_COMPONENT_ID = 4980758;    // 76.22
    private static final int SELECTED_MINIGAME_COMPONENT_ID = 4980747; // 76.11
    private static final int TELEPORT_BUTTON_COMPONENT_ID = 4980768; // 76.32

    // Measured Grouping/minigame teleport time once activation is complete. Keep ownership
    // for the full 15 seconds before judging the landing; activation/UI setup is deliberately
    // not included in this timer.
    private static final long MINIGAME_UI_WAIT_MS = 8_000L;
    private static final long MINIGAME_FIXED_CHANNEL_MS = 15_000L;
    private static final long MINIGAME_LANDING_GRACE_MS = 10_000L;
    private static final long QUETZAL_MAP_VISIBLE_WAIT_MS = 7_000L;
    private static final long QUETZAL_ICON_READY_WAIT_MS = 3_000L;

    // Nightmare Zone is unusual: the Grouping entry exists as a named destination, but the
    // account must have completed at least five qualifying boss quests. Keep this check local
    // and account-specific so an ineligible account never offers NMZ to Efficient Walker.
    private static final Set<String> NIGHTMARE_ZONE_QUESTS = Set.of(
        "The Ascent of Arceuus", "Contact!", "The Corsair Curse", "The Depths of Despair",
        "Desert Treasure I", "Dragon Slayer I", "Dream Mentor", "Fairytale I - Growing Pains",
        "Family Crest", "Fight Arena", "The Fremennik Isles", "Getting Ahead", "The Grand Tree",
        "The Great Brain Robbery", "Grim Tales", "Haunted Mine", "Holy Grail",
        "Horror from the Deep", "In Search of the Myreque", "Legends' Quest", "Lost City",
        "Lunar Diplomacy", "Monkey Madness I", "Mountain Daughter", "My Arm's Big Adventure",
        "One Small Favour", "Recipe for Disaster", "Roving Elves", "Shadow of the Storm",
        "Shilo Village", "Song of the Elves", "Tale of the Righteous", "Tree Gnome Village",
        "Troll Romance", "Troll Stronghold", "Vampyre Slayer", "What Lies Below", "Witch's House"
    );

    private final Client client;
    private final EfficientWalkerConfig config;
    private final ClassLoader engineLoader;
    private final Class<?> engineClass;
    private final Object engine;
    private final String enginePackage;
    private final CatalogTeleportSupport catalogTeleportSupport;

    private final IdentityHashMap<Object, NetworkEdge> injectedEdges = new IdentityHashMap<>();
    private Object staleActiveTransport;
    private NetworkEdge staleActiveEdge;
    // The engine lives in an isolated classloader, so public g.d objects cannot be
    // passed into it. Keep the maintained Dro Transport next to the shadow edge identity.
    private final IdentityHashMap<Object, Transport> sharedCatalogEdges = new IdentityHashMap<>();
    private final Set<String> sessionCharterBlacklist = new HashSet<>();
    private final Set<String> requestCharterBlacklist = new HashSet<>();
    private final Set<String> sessionMinigameBlacklist = new HashSet<>();
    private final Set<String> sessionNetworkBlacklist = new HashSet<>();
    private final Set<String> requestNetworkBlacklist = new HashSet<>();
    private final Set<String> failedSharedEdges = new HashSet<>();

    private ExecutorService actionExecutor = newActionExecutor();

    private ExecutorService newActionExecutor()
    {
        return WalkerMouseControl.profiledExecutor(Executors.newSingleThreadExecutor(runnable ->
        {
            Thread thread = new Thread(runnable, "EfficientWalker-network-transport");
            thread.setDaemon(true);
            return thread;
        }));
    }

    private synchronized ExecutorService actionExecutor()
    {
        if (actionExecutor == null || actionExecutor.isShutdown() || actionExecutor.isTerminated())
        {
            actionExecutor = newActionExecutor();
            log.info("[EfficientWalker] network transport executor restarted after plugin/profile lifecycle reset");
        }
        return actionExecutor;
    }

    private List<CharterRow> charterRows = Collections.emptyList();
    private List<MinigameRow> minigameRows = Collections.emptyList();
    private List<MinecartStop> kourendMinecartStops = Collections.emptyList();
    private List<Transport> sharedCatalog = Collections.emptyList();
    private List<Object> baseTransports = Collections.emptyList();
    private List<Object> installedMerged;

    private Class<?> transportClass;
    private Constructor<?> transportCtor;
    private Constructor<?> requirementCtor;
    private Constructor<?> skillCtor;
    private Method allowTransportMethod;
    private Method buildPlannerMethod;
    private Method collectObjectIdsMethod;
    private Field transportListField;
    private Field activeTransportField;
    private Field transportOriginField;
    private Field transportDestinationField;
    private Field transportActionField;
    private Field transportTargetField;
    private Field transportObjectIdField;

    private boolean installed;
    private boolean caveBankSuppliesAllowed, caveLightAvailable;

    void setCaveBankSuppliesAllowed(boolean allowed) {
        if (caveBankSuppliesAllowed != allowed) installedKey = null;
        caveBankSuppliesAllowed = allowed;
    }

    private String installedKey;
    private WorldPoint requestTarget;
    private WorldPoint minigamesSuppressedForTarget;

    private Object currentTransport;
    private NetworkEdge currentEdge;
    private Future<ExecutionResult> actionFuture;
    private long actionStartedAt;
    private WorldPoint replanTarget;

    NetworkTransportSupport(Client client, EfficientWalkerConfig config, ClassLoader engineLoader,
                            Class<?> engineClass, Object engine, String enginePackage,
                            CatalogTeleportSupport catalogTeleportSupport)
    {
        this.client = Objects.requireNonNull(client);
        this.config = Objects.requireNonNull(config);
        this.engineLoader = Objects.requireNonNull(engineLoader);
        this.engineClass = Objects.requireNonNull(engineClass);
        this.engine = Objects.requireNonNull(engine);
        this.enginePackage = Objects.requireNonNull(enginePackage);
        this.catalogTeleportSupport = Objects.requireNonNull(catalogTeleportSupport);
    }

    void install()
    {
        try
        {
            charterRows = readCharterRows();
            minigameRows = readMinigameRows();
            kourendMinecartStops = readKourendMinecartStops();
            capabilityGroups = new ArrayList<>(TransportResources.loadAllFromResources().values());
            sharedCatalog = loadSharedCatalog();

            transportClass = Class.forName(enginePackage + ".TransportCatalog$TransportEdge", true, engineLoader);
            Class<?> requirementClass = Class.forName(enginePackage + ".TransportCatalog$VarRequirement", true, engineLoader);
            Class<?> skillClass = Class.forName(enginePackage + ".TransportCatalog$SkillRequirement", true, engineLoader);
            Class<?> plannerClass = Class.forName(enginePackage + ".CollisionRoutePlanner", true, engineLoader);

            transportCtor = transportClass.getDeclaredConstructor(
                WorldPoint.class, WorldPoint.class, String.class, String.class, int.class,
                List.class, List.class, List.class, List.class, List.class,
                boolean.class, int[].class);
            transportCtor.setAccessible(true);

            requirementCtor = requirementClass.getDeclaredConstructor(int.class, int.class, char.class);
            requirementCtor.setAccessible(true);
            skillCtor = skillClass.getDeclaredConstructor(Skill.class, int.class);
            skillCtor.setAccessible(true);

            transportListField = field(engineClass, "transports");
            activeTransportField = field(engineClass, "activeTransport");
            transportOriginField = field(transportClass, "origin");
            transportDestinationField = field(transportClass, "destination");
            transportActionField = field(transportClass, "action");
            transportTargetField = field(transportClass, "target");
            transportObjectIdField = field(transportClass, "objectId");
            allowTransportMethod = engineClass.getDeclaredMethod("canUseTransport", transportClass);
            allowTransportMethod.setAccessible(true);
            buildPlannerMethod = plannerClass.getDeclaredMethod("create", List.class, Predicate.class);
            buildPlannerMethod.setAccessible(true);
            collectObjectIdsMethod = engineClass.getDeclaredMethod("collectTransportObjectIds", List.class);
            collectObjectIdsMethod.setAccessible(true);

            adoptCurrentGraphAsBase();
            installed = true;
            log.info("[EfficientWalker] extra transport data ready: charterRows={} minigameRows={} kourendMinecartStops={} sharedRoutes={}",
                charterRows.size(), minigameRows.size(), kourendMinecartStops.size(), sharedCatalog.size());
        }
        catch (Throwable ex)
        {
            installed = false;
            injectedEdges.clear();
            log.warn("[EfficientWalker] network transport extension unavailable; continuing with the existing graph", ex);
        }
    }

    /** Rebuild the additive layer for a new route target. */
    void refreshForPlanning(WorldPoint target)
    {
        WalkerRequestCancellation.checkInterrupted();
        if (!installed)
        {
            return;
        }

        try
        {
            maybeAdoptExternalGraph();

            if (preferredRouteTarget != null && target != null && !preferredRouteTarget.equals(target))
                clearCompleteRoute();
            if (!StickySwampExit.bankTarget(target)) stickySwampBankJourney = false;
            else if (StickySwampExit.nearby(playerLocation())) stickySwampBankJourney = true;
            StickySwampExit.setBankJourney(stickySwampBankJourney);
            if (!Objects.equals(requestTarget, target))
            {
                requestTarget = target;
                tzhaarDoorFailure=null;
                // A failed house interaction is not a permanent account capability loss.
                failedSharedEdges.removeIf(key -> key.contains(":Max POH:"));
                wildernessObeliskHops = 0;
                requestCharterBlacklist.clear();
                requestNetworkBlacklist.clear();

                // A weighted decision is tied to its final destination, not to temporary bank
                // or recovery targets used while reaching it. Preserve that one-target suppression
                // through target=null / bank legs, and clear it only when a genuinely different
                // non-null destination becomes the new request.
                if (target != null
                    && minigamesSuppressedForTarget != null
                    && !minigamesSuppressedForTarget.equals(target))
                {
                    minigamesSuppressedForTarget = null;
                }
            }

            caveLightAvailable = Microbot.getClientThread() != null && Microbot.getClientThread().runOnClientThreadOptional(
                () -> CaveLightSource.prepare(caveBankSuppliesAllowed) != null).orElse(false);
            String key = contextKey(target);
            if (!Objects.equals(installedKey, key) || installedMerged == null)
            {
                rebuildGraph(target);
            }
        }
        catch (Throwable ex)
        {
            WalkerRequestCancellation.rethrowCancellation(ex);
            log.warn("[EfficientWalker] failed to refresh network transport graph", ex);
            if (preferredRoute != null) {
                clearCompleteRoute();
                try { rebuildGraph(target); }
                catch (ReflectiveOperationException fallback) { log.warn("[EfficientWalker] route fallback unavailable",fallback); }
            }
        }
    }

    /**
     * @return true when this bridge owns the current transition tick and the engine
     * should wait for its UI interaction to finish.
     */
    private volatile String tzhaarDoorFailure;
    String tzhaarDoorFailure(){return tzhaarDoorFailure;}

    boolean beforeEngineTick()
    {
        if(tzhaarDoorFailure!=null)return true;
        if (!installed)
        {
            return false;
        }

        // Once dispatched, this owner must consume the result/landing before changed
        // preferences decide whether a newly selected canoe edge may begin.
        if (currentSharedTransport != null && actionFuture != null)
        {
            return beforeSharedTransportTick(currentSharedTransport);
        }

        NetworkEdge edge = activeInjectedEdge();
        Object selected = activeTransportFieldValue();
        if (!canoeAllowed(selected))
        {
            WorldPoint goal = engineDestination();
            cancelFuture();
            clearCompleteRoute();
            resetExecution(false);
            installedKey = null;
            if (goal != null) replanTarget = goal;
            return true;
        }
        Transport shared = sharedCatalogEdges.get(selected);
        if (shared == null && actionFuture == null) shared = nextPlannedHouseLeg();
        if (shared != null)
        {
            return beforeSharedTransportTick(shared);
        }
        // Passive/native edges are injected only to repair missing graph data (for example the
        // Al Kharid toll/open gate). The engine must execute those normally.
        if (edge != null && edge.type == EdgeType.NATIVE)
        {
            return false;
        }

        // Once our custom minigame interaction has been dispatched, keep ownership until that
        // worker finishes. The engine can temporarily clear its active edge while the
        // Grouping interface/channel is running; treating that as cancellation is what caused
        // the old ~1 second give-up/rebank/replan loop.
        boolean latchedCustomTransport = actionFuture != null
            && currentEdge != null
            && currentEdge.type != EdgeType.NATIVE;
        if (latchedCustomTransport)
        {
            edge = currentEdge;
        }
        else if (edge == null)
        {
            resetExecution(false);
            return false;
        }

        Object active = activeTransportFieldValue();
        if (!latchedCustomTransport && currentTransport != active)
        {
            cancelFuture();
            currentTransport = active;
            currentEdge = edge;
            actionStartedAt = 0L;
            debug("selected {} edge {} -> {} ({})",
                edgeTypeName(edge.type),
                edge.origin, edge.destination, edge.label);
        }
        else if (!latchedCustomTransport)
        {
            currentEdge = edge;
        }

        WorldPoint player = playerLocation();
        if (player == null)
        {
            return false;
        }

        if (near(player, edge.destination, LANDING_RADIUS))
        {
            if (edge.type == EdgeType.WILDERNESS_OBELISK && actionFuture != null)
            {
                wildernessObeliskHops++;
                cancelFuture();
            }
            debug("{} landed at {} ({})",
                edgeTypeName(edge.type), player, edge.label);
            WorldPoint target = requiresRouteHandoff(edge) ? engineDestination() : null;
            resetExecution(false);
            if (target != null)
            {
                // These custom long transports finish like Rs2Walker's terminal transports:
                // hand the planner the observed landing, never a stale id=-1 edge.
                replanTarget = target;
                obeliskReplan = edge.type == EdgeType.WILDERNESS_OBELISK;
                return true;
            }
            return false;
        }

        if (actionFuture != null)
        {
            if (!actionFuture.isDone())
            {
                if (System.currentTimeMillis() - actionStartedAt > EXECUTION_TIMEOUT_MS)
                {
                    cancelFuture();
                    failEdge(edge, ExecutionResult.FAILED, "transport interaction timed out");
                }
                return true;
            }

            ExecutionResult result = consumeFutureResult();
            if (result == ExecutionResult.SUCCESS)
            {
                if (edge.type == EdgeType.WILDERNESS_OBELISK) wildernessObeliskHops++;
                // Rs2Walker finishHandledTransport() restarts/recalculates after a long NPC
                // transport.  Do the same for Elkoy so the engine never gets a tick
                // with the stale synthetic id=-1 transition still active after the landing.
                WorldPoint target = requiresRouteHandoff(edge) ? engineDestination() : null;
                EdgeType finishedType = edge.type;
                resetExecution(false);
                if (target != null)
                {
                    replanTarget = target;
                    obeliskReplan = finishedType == EdgeType.WILDERNESS_OBELISK;
                    debug("{} transport handoff from landing {}; replanning {}",
                        edgeTypeName(finishedType), playerLocation(), target);
                    return true;
                }
                return false;
            }

            if(result==ExecutionResult.BANK_OPEN){resetExecution(false);return true;}
            failEdge(edge, result, "transport interaction failed");
            return true;
        }

        int interactRadius = interactionRadiusFor(edge);
        if (!near(player, edge.origin, interactRadius))
        {
            return false; // Efficient Walker still needs to approach the exact selected edge.
        }

        if (edge.type == EdgeType.ELKOY)
        {
            // Rs2Walker guard: once the player is on the destination side of the exact planned
            // edge, never cross it back. A legitimate reverse journey is a separate selected edge
            // with origin/destination swapped. Consume this tick and replan the unchanged goal so
            // the generic object executor cannot see Elkoy's synthetic id=-1 row.
            if (isOnElkoyDestinationSide(edge, player))
            {
                WorldPoint target = engineDestination();
                debug("Elkoy selected edge already behind player at {}; suppressing {} -> {}",
                    player, edge.origin, edge.destination);
                resetExecution(false);
                if (target != null)
                {
                    replanTarget = target;
                }
                return true;
            }

            // Exact selected NPC edge now owns the tick as soon as its origin is within the
            // bounded server-approach radius. Rs2Walker resolves the NPC/action for
            // TransportType.NPC instead of asking an object executor to handle id=-1. The worker
            // below waits briefly for Elkoy to load before validating the live Follow action.
        }

        if (edge.type == EdgeType.MINIGAME && !minigameCooldownReady())
        {
            failEdge(edge, ExecutionResult.COOLDOWN, "minigame teleport cooldown became active");
            return true;
        }

        actionStartedAt = System.currentTimeMillis();
        final NetworkEdge executionEdge = edge;
        actionFuture = actionExecutor().submit(() ->
        {
            switch (executionEdge.type)
            {
                case MINIGAME:
                    return executeMinigame(executionEdge);
                case CHARTER:
                    return executeCharter(executionEdge);
                case CHARTER_EXIT:
                    return disembark(executionEdge.destination);
                case QUETZAL:
                    return finishMenuExecution(EdgeType.QUETZAL, executeQuetzal(executionEdge));
                case PRIMIO:
                    return executePrimio(executionEdge);
                case MINECART:
                    return finishMenuExecution(EdgeType.MINECART, executeMinecart(executionEdge));
                case ELKOY:
                    return executeElkoy(executionEdge);
                case MUSHTREE:
                    return finishMenuExecution(EdgeType.MUSHTREE, executeMagicMushtree(executionEdge));
                case FOSSIL_BARGE:
                    return executeFossilBarge(executionEdge);
                case FOSSIL_ROWBOAT:
                    return finishMenuExecution(EdgeType.FOSSIL_ROWBOAT, executeFossilRowboat(executionEdge));
                case FOSSIL_HOLE:
                    return executeFossilHole(executionEdge);
                case WILDERNESS_OBELISK:
                    return executeWildernessObelisk(executionEdge);
                default:
                    return ExecutionResult.FAILED;
            }
        });
        return true;
    }

    /** Own the selected house hop before the generic object executor tries its synthetic id. */
    private Transport nextPlannedHouseLeg()
    {
        if (!config.useMaxPoh()) return null;
        try
        {
            if (field(engineClass, "activeTeleportProvider").get(engine) != null) return null;
            List<?> next = (List<?>) field(engineClass, "localRouteTransports").get(engine);
            WorldPoint player = playerLocation();
            if (next != null && !next.isEmpty()) {
                Object selected = next.get(0);
                Transport source = sharedCatalogEdges.get(selected);
                if (MaxPohSupport.isLeg(source)
                    && Objects.equals(transportWorldPoint(selected, transportOriginField), player)) return source;
            }
            // Global preview paths are published before local transitions (N/O) are
            // materialized. The house jump must be intercepted at that earlier boundary.
            List<?> path = (List<?>) field(engineClass, "globalRoute").get(engine);
            if (path == null || path.size() < 2 || !Objects.equals(player, path.get(0))) return null;
            for (var entry : sharedCatalogEdges.entrySet())
                if (MaxPohSupport.isLeg(entry.getValue())
                    && Objects.equals(path.get(0), transportWorldPoint(entry.getKey(), transportOriginField))
                    && Objects.equals(path.get(1), transportWorldPoint(entry.getKey(), transportDestinationField)))
                    return entry.getValue();
            return null;
        }
        catch (ReflectiveOperationException ex)
        {
            throw new IllegalStateException("Unable to inspect planned house hop", ex);
        }
    }

    boolean hasPendingAction() { return actionFuture != null; }

    Transport pendingHouseLeg()
    {
        if (MaxPohSupport.isLeg(currentSharedTransport) && actionFuture != null) return currentSharedTransport;
        Transport active = sharedCatalogEdges.get(activeTransportFieldValue());
        return MaxPohSupport.isLeg(active) ? active : nextPlannedHouseLeg();
    }

    // A route may have been canceled immediately after boarding a ship. The engine then
    // loses its transition marker and reports SEARCH_LIMIT, so the normal CHARTER_EXIT path
    // cannot execute. Recover the deck separately, on the existing transport worker.
    private Future<ExecutionResult> blockedDeckFuture;
    private WorldPoint blockedDeckAttempted;
    private WorldPoint blockedDeckTarget;

    boolean hasRecoverableBlockedShipDeck()
    {
        // Keep ownership through the landing tick; otherwise the player changes to plane 0
        // before the result can be consumed and the original route never resumes.
        if (blockedDeckFuture != null) return true;
        WorldPoint player = playerLocation();
        Transport exit = CharterTransportCatalog.exitFrom(player);
        return exit != null && !exit.getDestination().equals(blockedDeckAttempted);
    }

    boolean tickBlockedShipDeckRecovery(WorldPoint target)
    {
        WorldPoint player = playerLocation();
        if (blockedDeckFuture == null)
        {
            Transport exit = CharterTransportCatalog.exitFrom(player);
            if (exit == null || target == null || Objects.equals(exit.getDestination(), blockedDeckAttempted)) return false;
            // Remember the dock, not the exact deck tile: short movement cannot reset the retry budget.
            blockedDeckAttempted = exit.getDestination();
            blockedDeckTarget = target;
            blockedDeckFuture = actionExecutor().submit(() -> disembark(exit.getDestination()));
            debug("blocked ship-deck recovery started deck={} dock={} target={}",
                player, exit.getDestination(), target);
            return true;
        }
        if (!blockedDeckFuture.isDone()) return true;
        ExecutionResult result;
        try { result = blockedDeckFuture.get(); }
        catch (Exception ex) {
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            debug("blocked ship-deck recovery error: {}", ex.getMessage());
            result = ExecutionResult.FAILED;
        }
        blockedDeckFuture = null;
        WorldPoint goal = blockedDeckTarget;
        blockedDeckTarget = null;
        if (result == ExecutionResult.SUCCESS && playerLocation() != null && playerLocation().getPlane() == 0)
        {
            debug("blocked ship-deck recovery landed={} replan={}", playerLocation(), goal);
            replanTarget = goal;
            return true;
        }
        debug("blocked ship-deck recovery exhausted deck={} target={} result={}", blockedDeckAttempted, goal, result);
        return false; // Expose BLOCKED to the caller after the bounded three attempts.
    }

    boolean hasRecoverableBlockedCustomEdge()
    {
        if (!installed)
        {
            return false;
        }
        NetworkEdge edge = activeInjectedEdge();
        WorldPoint player = playerLocation();
        Object active = activeTransportFieldValue();
        Transport shared = sharedCatalogEdges.get(active);
        if (shared != null)
        {
            return sharedCanStartFrom(player, shared);
        }
        return edge != null
            && edge.type != EdgeType.NATIVE
            && player != null
            && near(player, edge.origin, interactionRadiusFor(edge));
    }

    // Kept for binary/source compatibility with callers built against v7/v8.
    boolean hasRecoverableBlockedElkoyEdge()
    {
        return hasRecoverableBlockedCustomEdge();
    }

    WorldPoint consumeReplanTarget()
    {
        WorldPoint value = replanTarget;
        replanTarget = null;
        return value;
    }

    private boolean obeliskReplan;
    private boolean stickySwampBankRetry;
    private boolean stickySwampBankJourney;

    boolean consumeStickySwampBankRetry()
    {
        boolean result = stickySwampBankRetry;
        stickySwampBankRetry = false;
        return result;
    }

    boolean consumeObeliskReplan()
    {
        boolean result = obeliskReplan;
        obeliskReplan = false;
        return result;
    }

    void cancel()
    {
        tzhaarDoorFailure=null;
        obeliskReplan = false;
        stickySwampBankRetry = false;
        stickySwampBankJourney = false;
        StickySwampExit.setBankJourney(false);
        clearCompleteRoute();
        cancelFuture();
        if (blockedDeckFuture != null) blockedDeckFuture.cancel(true);
        blockedDeckFuture = null;
        blockedDeckAttempted = null;
        blockedDeckTarget = null;
        resetExecution(false);
        replanTarget = null;
        failedSharedEdges.removeIf(key -> key.contains(":Max POH:"));
    }

    void resetSession()
    {
        cancel();
        sessionCharterBlacklist.clear();
        requestCharterBlacklist.clear();
        sessionMinigameBlacklist.clear();
        sessionNetworkBlacklist.clear();
        requestNetworkBlacklist.clear();
        failedSharedEdges.clear();
        minigamesSuppressedForTarget = null;
        installedKey = null;
        requestTarget = null;
    }

    synchronized void shutdown()
    {
        cancel();
        if (actionExecutor != null)
        {
            actionExecutor.shutdownNow();
            actionExecutor = null;
        }
    }

    private Collection<Set<Transport>> capabilityGroups = Collections.emptyList();
    private String selectedTransitionRejection;
    String selectedTransitionRejection() { return selectedTransitionRejection; }

    /** Capability admission deliberately does not require supplies that the bank is about to withdraw. */
    boolean canExecuteSelectedEdges(List<Rs2TransportEdge> selected)
    {
        selectedTransitionRejection = null;
        if (selected == null) return rejectSelected(null, "missing selected transitions");
        for (Rs2TransportEdge edge : selected) {
            if (edge == null) return rejectSelected(null, "missing transition");
            if (catalogTeleportSupport.supportsSelectedEdge(edge)) continue;
            // POH TABLET legs can be planned against bank stock before tablet 8013 is
            // withdrawn. Recheck the maintained house facility and gate constraints;
            // do not require the currently-installed inventory-filtered graph here.
            if (MaxPohSupport.isHouseEdge(edge) && MaxPohSupport.enabled()
                && MaxPohSupport.routes().stream().anyMatch(source ->
                    MaxPohSupport.isLeg(source) && sameTransition(edge, source)
                        && MaxPohSupport.usable(source))) continue;
            if (sharedCatalog.stream().anyMatch(source -> sameTransition(edge, source))
                || sharedCatalogEdges.values().stream().anyMatch(source -> sameTransition(edge, source))) continue;
            // The maintained resource proves the full identity; the native graph must also
            // contain its actual interaction, or the dedicated network must own that source.
            boolean represented = false;
            for (Set<Transport> group : capabilityGroups) {
                if (group == null) continue;
                for (Transport source : group) {
                    if (!sameTransition(edge, source)) continue;
                    represented = baseTransports.stream().anyMatch(nativeEdge -> nativeInteractionMatches(source, nativeEdge))
                        || dedicatedNetworkSupports(source);
                    if (represented) break;
                }
                if (represented) break;
            }
            if (!represented) return rejectSelected(edge, "no matching executable capability");
        }
        return true;
    }

    boolean canExecuteCompleteRoute(CompleteRouteDiscovery.Choice choice)
    {
        if (choice == null || choice.route == null) return rejectSelected(null, "missing complete route");
        if (!canExecuteSelectedEdges(TransportSupplyPlanner.edges(choice.route))) return false;
        try {
            List<?> providers = unrestrictedProviders != null ? unrestrictedProviders
                : (List<?>) field(engineClass, "teleportProviders").get(engine);
            if (choice.prefix != null && providers.stream().noneMatch(provider -> providerMatchesPrefix(provider, choice.prefix)))
                return rejectSelected(null, "native prefix unavailable: " + choice.prefix.name + " -> " + choice.prefix.landing);
            // Native item providers can only execute as the first route leg.
            for (int i = 0; i < choice.route.getSteps().size(); i++) {
                Rs2TransportEdge edge = choice.route.getSteps().get(i).getTransport().orElse(null);
                if (edge != null && catalogTeleportSupport.supportsSelectedEdge(edge) && (i != 0 || choice.prefix != null))
                    return rejectSelected(edge, "native item teleport must be the initial prefix");
            }
            return true;
        } catch (ReflectiveOperationException ex) { return rejectSelected(null, "cannot inspect native prefix capability"); }
    }

    private boolean rejectSelected(Rs2TransportEdge edge, String reason)
    {
        selectedTransitionRejection = reason + (edge == null ? "" : ": " + transitionIdentity(edge));
        log.info("[EfficientWalker] selected transition rejected: {}", selectedTransitionRejection);
        return false;
    }

    static String transitionIdentity(Rs2TransportEdge edge)
    {
        return "executor=" + edge.getExecutor() + " type=" + edge.getType() + " origin=" + edge.getOrigin()
            + " destination=" + edge.getDestination() + " object=" + edge.getObjectId()
            + " action=" + edge.getAction() + " target=" + edge.getTarget() + " display=" + edge.getDisplayInfo();
    }

    static boolean sameTransition(Rs2TransportEdge chosen, Transport source)
    {
        return chosen != null && source != null && TransportExecutionRegistry.executorFor(source)
            .map(executor -> executor.name().equals(chosen.getExecutor().name())).orElse(false)
            && source.getType().name().equals(chosen.getType().name())
            && Objects.equals(chosen.getOrigin(), source.getOrigin())
            && Objects.equals(chosen.getDestination(), source.getDestination())
            && chosen.getObjectId() == source.getObjectId()
            && Objects.equals(chosen.getAction(), source.getAction())
            && Objects.equals(chosen.getTarget(), source.getName())
            && Objects.equals(chosen.getDisplayInfo(), source.getDisplayInfo());
    }

    private boolean nativeInteractionMatches(Transport source, Object edge)
    {
        return Objects.equals(source.getOrigin(), transportWorldPoint(edge, transportOriginField))
            && Objects.equals(source.getDestination(), transportWorldPoint(edge, transportDestinationField))
            && source.getObjectId() == transportInt(edge, transportObjectIdField)
            && Objects.equals(source.getAction(), transportString(edge, transportActionField))
            && Objects.equals(source.getName(), transportString(edge, transportTargetField));
    }

    private boolean dedicatedNetworkSupports(Transport source)
    {
        // A missing carried fare must not hide this maintained executor before withdrawal.
        if (source.getType() == TransportType.MINECART && source.getObjectId() == KOUREND_MINECART_OBJECT_ID
            && kourendMinecartStops.stream().anyMatch(stop -> stop.point.equals(source.getOrigin()))
            && kourendMinecartStops.stream().anyMatch(stop -> stop.point.equals(source.getDestination()))) return true;
        if (source.getType() == TransportType.TELEPORTATION_MINIGAME && minigameRows.stream().anyMatch(row ->
            row.destination.equals(source.getDestination()) && Objects.equals(row.displayInfo, source.getDisplayInfo()))) return true;
        if (source.getType() == TransportType.CHARTER_SHIP && charterRows.stream().anyMatch(row ->
            Objects.equals(row.origin, source.getOrigin()) && Objects.equals(row.destination, source.getDestination()))) return true;
        return injectedEdges.values().stream().anyMatch(edge -> networkMatchesSource(source, edge));
    }

    private static boolean networkMatchesSource(Transport source, NetworkEdge edge)
    {
        return
            (source.getOrigin() == null || Objects.equals(source.getOrigin(), edge.origin)) && Objects.equals(source.getDestination(), edge.destination)
                && ((edge.type == EdgeType.CHARTER && source.getType() == TransportType.CHARTER_SHIP)
                    || (edge.type == EdgeType.CHARTER_EXIT && CharterTransportCatalog.isDisembark(source))
                    || (edge.type == EdgeType.MINECART && source.getType() == TransportType.MINECART)
                    || (edge.type == EdgeType.MINIGAME && source.getType() == TransportType.TELEPORTATION_MINIGAME)
                    || (edge.type == EdgeType.QUETZAL && source.getType() == TransportType.QUETZAL)
                    || (edge.type == EdgeType.MUSHTREE && source.getType() == TransportType.MAGIC_MUSHTREE)
                    || (edge.type == EdgeType.WILDERNESS_OBELISK && source.getType() == TransportType.WILDERNESS_OBELISK));
    }

    private boolean providerMatchesPrefix(Object provider, WeightedRouteAdvisor.BankedTeleportOption prefix)
    {
        try {
            var destination = provider.getClass().getDeclaredMethod("getDestination");
            destination.setAccessible(true);
            return Objects.equals(prefix.name, field(provider.getClass(), "name").get(provider))
                && Objects.equals(prefix.landing, destination.invoke(provider));
        } catch (ReflectiveOperationException ex) { return false; }
    }

    private WorldPoint preferredRouteTarget;
    private CompleteRouteDiscovery.Choice preferredRoute;
    private List<?> unrestrictedProviders;
    private boolean completeRouteReady;
    private int preferredStepIndex;
    private boolean preferredPrefixUsed;
    private WorldPoint preferredNativeLanding;
    boolean completeRouteReady() { return completeRouteReady; }

    /** Hold the complete winner through preparation and the native final preview. */
    void preferCompleteRoute(WorldPoint target, CompleteRouteDiscovery.Choice choice)
    {
        clearCompleteRoute();
        preferredRouteTarget = target;
        preferredRoute = choice;
        preferredStepIndex = 0;
        preferredPrefixUsed = false;
        installedKey = null;
    }

    void clearCompleteRoute()
    {
        if (unrestrictedProviders != null) {
            try { setField(engineClass, engine, "teleportProviders", unrestrictedProviders); }
            catch (ReflectiveOperationException ex) { throw new IllegalStateException("Cannot restore teleport providers",ex); }
            unrestrictedProviders = null;
        }
        completeRouteReady = false;
        preferredRouteTarget = null;
        preferredRoute = null;
        preferredNativeLanding = null;
        installedKey = null;
    }

    private boolean representsSelectedTransition(Rs2TransportEdge chosen, Object edge)
    {
        Transport source = sharedCatalogEdges.get(edge);
        if (source != null) return sameTransition(chosen, source);
        // Compare endpoints before searching the maintained capability catalogue. Dedicated
        // network edges may encode a synthetic object id and must keep their own matcher.
        if (!Objects.equals(chosen.getOrigin(), transportWorldPoint(edge, transportOriginField))
            || !Objects.equals(chosen.getDestination(), transportWorldPoint(edge, transportDestinationField))) return false;
        NetworkEdge injected = injectedEdges.get(edge);
        if (injected == null && (chosen.getObjectId() != transportInt(edge, transportObjectIdField)
            || !Objects.equals(chosen.getAction(), transportString(edge, transportActionField))
            || !Objects.equals(chosen.getTarget(), transportString(edge, transportTargetField)))) return false;
        // Native projections omit executor metadata; resolve only the already matched identity.
        for (Set<Transport> group : capabilityGroups) {
            if (group == null) continue;
            for (Transport candidate : group) {
                if (!sameTransition(chosen, candidate)) continue;
                if (nativeInteractionMatches(candidate, edge)) return true;
                NetworkEdge network = injectedEdges.get(edge);
                if (network != null && networkMatchesSource(candidate, network)) return true;
            }
        }
        return false;
    }

    private boolean onPreferredRoute(Object edge)
    {
        WorldPoint from = transportWorldPoint(edge, transportOriginField);
        WorldPoint to = transportWorldPoint(edge, transportDestinationField);
        if (from == null || to == null) return false;
        // Preserve local doors/gates and collision recovery, even if absent from the graph route.
        if (from.getPlane() == to.getPlane() && from.distanceTo2D(to) <= 8) return true;
        for (var step : preferredRoute.route.getSteps().subList(preferredStepIndex, preferredRoute.route.getSteps().size())) {
            var transport = step.getTransport().orElse(null);
            if (MaxPohSupport.isHouseEdge(transport)) {
                Transport source = sharedCatalogEdges.get(edge);
                if (!MaxPohSupport.isLeg(source) || !Objects.equals(transport.getTarget(), source.getName())) continue;
            }
            if (transport != null && transport.getType() == net.runelite.client.plugins.microbot.droslayer.walker.support.Rs2TransportType.TELEPORTATION_SPELL
                && sharedSpellName(transport.getDisplayInfo())
                && !sameSharedSpell(transport, sharedCatalogEdges.get(edge))) continue;
            if (transport != null && to.equals(step.getTo())
                && (transport.isTeleport() || from.equals(step.getFrom()))
                && representsSelectedTransition(transport, edge)) return true;
        }
        return false;
    }

    private static boolean sameSharedSpell(
        net.runelite.client.plugins.microbot.droslayer.walker.support.Rs2TransportEdge chosen,
        Transport source)
    {
        return source != null && source.getType() == TransportType.TELEPORTATION_SPELL
            && Objects.equals(chosen.getDisplayInfo(), source.getDisplayInfo());
    }

    private void applyCompleteRoute(WorldPoint target, List<Object> merged) throws ReflectiveOperationException
    {
        if (preferredRoute == null || !Objects.equals(target, preferredRouteTarget)) return;
        completeRouteReady = false;
        WorldPoint player = playerLocation();
        List<WorldPoint> path = preferredRoute.route.getPath();
        if (player != null && preferredNativeLanding != null
            && player.getPlane() == preferredNativeLanding.getPlane()
            && player.distanceTo2D(preferredNativeLanding) <= 2) {
            if (preferredRoute.prefix != null) preferredPrefixUsed = true;
            else preferredStepIndex = Math.max(preferredStepIndex, 1);
        }
        // Trim completed legs so consumed supplies cannot invalidate an already-completed transition.
        if (player != null) {
            int closest = Integer.MAX_VALUE;
            int reached = preferredStepIndex;
            for (int i = preferredStepIndex; i < path.size(); i++) {
                WorldPoint point = path.get(i);
                if (point.getPlane() != player.getPlane()) continue;
                int distance = point.distanceTo2D(player);
                if (distance <= 2 && distance < closest) { closest = distance; reached = i; }
            }
            if (closest <= 2) {
                preferredStepIndex = reached;
                if (preferredRoute.prefix != null) preferredPrefixUsed = true;
            }
        }
        if (unrestrictedProviders == null)
            unrestrictedProviders = (List<?>) field(engineClass, "teleportProviders").get(engine);
        WorldPoint landing = preferredStepIndex == 0 && !preferredPrefixUsed ? preferredRoute.firstTeleportLanding() : null;
        boolean housePrefix = preferredRoute.prefix == null && !preferredRoute.route.getSteps().isEmpty()
            && MaxPohSupport.isHouseEdge(preferredRoute.route.getSteps().get(0).getTransport().orElse(null));
        boolean sharedSpellPrefix = preferredRoute.prefix == null && !preferredRoute.route.getSteps().isEmpty()
            && preferredRoute.route.getSteps().get(0).getTransport()
                .map(edge -> edge.getType() == net.runelite.client.plugins.microbot.droslayer.walker.support.Rs2TransportType.TELEPORTATION_SPELL
                    && sharedSpellName(edge.getDisplayInfo()))
                .orElse(false);
        List<Object> providers = new ArrayList<>();
        // Keep spell execution on its admitted graph edge: a same-landing tablet
        // is a different input and may not be carried.
        // A house exit can share a landing with worn jewellery. Preserve the selected
        // composite interaction rather than replacing it with that unrelated provider.
        if (landing != null && !housePrefix && !sharedSpellPrefix) for (Object provider : unrestrictedProviders) {
            Method method = provider.getClass().getDeclaredMethod("getDestination");
            method.setAccessible(true);
            WorldPoint actual = (WorldPoint) method.invoke(provider);
            boolean same = landing.equals(actual);
            if (!same && preferredRoute.prefix == null && actual != null
                && landing.getPlane() == actual.getPlane() && landing.distanceTo2D(actual) <= 12) {
                // Catalogs may record different tiles inside the same teleport landing area.
                // Require the same item identity as well; proximity alone is not sufficient.
                var first = preferredRoute.route.getSteps().get(0).getTransport().orElse(null);
                int[] ids = (int[]) field(provider.getClass(), "itemIds").get(provider);
                same = first != null && first.getItemRequirements().stream().anyMatch(requirement ->
                    Arrays.stream(ids).anyMatch(id -> requirement.getAlternatives().containsKey(id)));
            }
            if (same && (preferredRoute.prefix != null ? providerMatchesPrefix(provider, preferredRoute.prefix)
                : !preferredRoute.route.getSteps().isEmpty() && catalogTeleportSupport.supportsSelectedProvider(provider,
                    preferredRoute.route.getSteps().get(0).getTransport().orElse(null)))) {
                providers.add(provider);
                preferredNativeLanding = actual;
            }
        }
        // A typed route may use a graph teleport (Grouping/portal) instead of a native item provider.
        if (preferredRoute.prefix != null && !preferredPrefixUsed && providers.isEmpty())
        {
            debug("complete-route invalidated before execution: selected teleport provider unavailable target={}", target);
            clearCompleteRoute(); // Preserve the ordinary graph rather than crashing a client tick.
            return;
        }
        // Index the installed graph ONCE by endpoint. Previously a full merged.stream()
        // repeatedly called representsSelectedTransition(), which in turn scanned every
        // capability group for EVERY graph edge, freezing the game thread for >10 seconds.
        Map<WorldPoint, Map<WorldPoint, List<Object>>> mergedByEndpoints = new HashMap<>();
        for (Object candidate : merged) {
            WorldPoint from = transportWorldPoint(candidate, transportOriginField);
            WorldPoint to = transportWorldPoint(candidate, transportDestinationField);
            if (from != null && to != null) {
                // Global catalogue transports are anchored only for planning; retain their selected identity.
                Transport source = sharedCatalogEdges.get(candidate);
                WorldPoint canonicalFrom = source != null && source.getOrigin() == null ? null : from;
                mergedByEndpoints.computeIfAbsent(canonicalFrom, key -> new HashMap<>())
                    .computeIfAbsent(to, key -> new ArrayList<>()).add(candidate);
            }
        }
        // Do not substitute a different route if a selected transition cannot execute here.
        for (var step : preferredRoute.route.getSteps().subList(preferredStepIndex, preferredRoute.route.getSteps().size())) {
            var chosen = step.getTransport().orElse(null);
            if (chosen == null) continue;
            if (chosen.isTeleport() && landing != null && landing.equals(step.getTo()) && !providers.isEmpty()) continue;
            List<Object> matching = mergedByEndpoints
                .getOrDefault(chosen.getOrigin(), Collections.emptyMap())
                .getOrDefault(chosen.getDestination(), Collections.emptyList());
            boolean represented = matching.stream().anyMatch(edge -> representsSelectedTransition(chosen, edge));
            if (!represented)
            {
                debug("complete-route invalidated before execution: selected transition unavailable target={} edge={}",
                    target, transitionIdentity(chosen));
                clearCompleteRoute(); // No client-thread exception or partial preferred-route filter.
                return;
            }
        }
        setField(engineClass, engine, "teleportProviders", Collections.unmodifiableList(providers));
        completeRouteReady = true;
        merged.removeIf(edge -> !onPreferredRoute(edge));
    }

    private void rebuildGraph(WorldPoint target) throws ReflectiveOperationException
    {
        maybeAdoptExternalGraph();
        injectedEdges.clear();
        staleActiveTransport = null;
        sharedCatalogEdges.clear();

        ArrayList<Object> merged = new ArrayList<>(baseTransports);
        correctWildernessShortcutRequirements(merged);
        // The CJQ table admits partial quest progress. Match the complete-route
        // catalogue's completion requirement for both entering and leaving this ring.
        if (!GreatConchTransports.unlocked(Rs2Player.getQuestState(Quest.TROUBLED_TORTUGANS)))
            merged.removeIf(edge -> GreatConchTransports.isRingConnection(
                transportWorldPoint(edge, transportOriginField),
                transportWorldPoint(edge, transportDestinationField)));
        // Legacy capture-to-prison shortcut has no executable interaction and bypasses
        // the main gate. Use the real, requirement-checked crossing instead.
        merged.removeIf(edge -> new WorldPoint(2724,2747,0).equals(transportWorldPoint(edge, transportOriginField))
            && new WorldPoint(2770,2793,0).equals(transportWorldPoint(edge, transportDestinationField)));
        merged.removeIf(transport -> {
            if (!canoeAllowed(transport)) return true;
            Transport shared = sharedCatalogEdges.get(transport);
            if ((!config.allowWildernessTravel() || !config.useWildernessLevers()
                || !client.getWorldType().contains(WorldType.MEMBERS)))
            {
                try
                {
                    int id = transportObjectIdField.getInt(transport);
                    if (id == 1814 || id == 1815 || id == 1816 || id == 1817
                        || id == 26761 || id == 5959 || id == 5960) return true;
                }
                catch (IllegalAccessException ex) { throw new IllegalStateException(ex); }
            }
            return shared != null && failedSharedEdges.contains(sharedEdgeKey(shared));
        });
        // The cabin's ladder is unusable from outside without its door key. A plain
        // walking edge must not bypass the door's catalog item requirement.
        WorldPoint accessStart = playerLocation();
        boolean brassKey = Rs2Inventory.hasItem(983);
        merged.removeIf(edge -> !EdgevilleDungeonAccess.allowed(
            transportInt(edge, transportObjectIdField), transportWorldPoint(edge, transportOriginField), accessStart, brassKey));
        int replacedDuplicates = removeKnownCustomTransportDuplicates(merged);
        int shiloCartRepaired = replaceShiloBrokenCartEdges(merged);
        int taverleyPipeReverseSuppressed = suppressTaverleyBlueDragonPipeReverse(merged, target);
        int karuulmRocksAdded = addKaruulmDungeonRockEdges(merged);
        int sharedAdded = addSharedCatalogEdges(merged);
        WorldPoint houseStart = playerLocation();
        for (Transport source : MaxPohSupport.routes()) {
            if (!MaxPohSupport.usable(source) || !sharedRequirementsSatisfiedNow(source)
                || failedSharedEdges.contains(sharedEdgeKey(source))
                || MaxPohSupport.landsNear(source, houseStart)) continue;
            Object edge = buildSharedCatalogTransport(source, houseStart);
            if (edge != null) { merged.add(0, edge); sharedCatalogEdges.put(edge,source); }
        }
        int alKharidGateAdded = addAlKharidGateEdges(merged);
        int primioAdded = addPrimioEdges(merged);
        int quetzalAdded = addQuetzalEdges(merged);
        int elkoyAdded = addElkoyEdges(merged);
        int mushtreeAdded = addMagicMushtreeEdges(merged);
        int fossilAdded = addFossilIslandEdges(merged) + addFossilHoleEdges(merged);
        int wildernessObelisksAdded = addWildernessObeliskEdges(merged);
        int wildernessLeversAdded = addWildernessLeverEdges(merged);
        int minecartAdded = 0;
        int minecartFareSkipped = 0;
        WorldPoint start = playerLocation();
        int coins = Rs2Inventory.itemQuantity(COINS);
        boolean members = client.getWorldType().contains(WorldType.MEMBERS);
        int nativeTeleportScore = bestCarriedNativeTeleportScore(start, target);

        if (target != null && members && !kourendMinecartStops.isEmpty())
        {
            int minecartFare = currentKourendMinecartFare();
            ArrayList<Object> minecartEdges = new ArrayList<>();
            for (MinecartStop origin : kourendMinecartStops)
            {
                for (MinecartStop destination : kourendMinecartStops)
                {
                    if (origin == destination)
                    {
                        continue;
                    }
                    if (requestNetworkBlacklist.contains(networkKey(EdgeType.MINECART, origin.point, destination.point)))
                    {
                        continue;
                    }
                    if (coins < minecartFare)
                    {
                        minecartFareSkipped++;
                        continue;
                    }

                    Object shadow = buildMinecartTransport(origin, destination, minecartFare);
                    if (shadow != null)
                    {
                        minecartEdges.add(shadow);
                        minecartAdded++;
                    }
                }
            }

            // Put our explicit Kourend rows before any preserved duplicates at these cart tiles,
            // so the route uses the execution path that understands the Adventure Log menu and fare.
            if (!minecartEdges.isEmpty())
            {
                merged.addAll(0, minecartEdges);
            }
        }

        charterCabinFever = CharterFare.cabinFever();
        charterCharos = CharterFare.charos();
        // Ship decks need an explicit exit even when a journey starts aboard a ship.
        for (Transport exit : CharterTransportCatalog.deckExits())
            addCustomEdgeIfAllowed(merged, EdgeType.CHARTER_EXIT, exit.getOrigin(), exit.getDestination(),
                exit.getDisplayInfo(), "Gangplank", -1, "Cross");
        if (start != null && start.getPlane() == 1)
            for (Transport exit : CharterTransportCatalog.deckExits())
                if (CharterTransportCatalog.onDeck(start, exit.getDestination()) && !start.equals(exit.getOrigin()))
                    addCustomEdgeIfAllowed(merged, EdgeType.CHARTER_EXIT, start, exit.getDestination(),
                        exit.getDisplayInfo(), "Gangplank", -1, "Cross");
        int charterAdded = 0;
        int charterFareSkipped = 0;
        if (config.useCharterShips() && target != null && members)
        {
            for (CharterRow row : charterRows)
            {
                String edgeKey = charterKey(row.origin, row.destination);
                if (sessionCharterBlacklist.contains(edgeKey) || requestCharterBlacklist.contains(edgeKey))
                {
                    continue;
                }
                if (coins < charterFare(row))
                {
                    charterFareSkipped++;
                    continue;
                }
                if (!runtimeCharterRequirementsPass(row))
                {
                    continue;
                }
                if (!charterWorthConsidering(row, target, nativeTeleportScore))
                {
                    continue;
                }

                Object shadow = buildCharterTransport(row);
                if (shadow != null)
                {
                    merged.add(shadow);
                    charterAdded++;
                }
            }
        }

        int minigameAdded = 0;
        boolean cooldownReady = minigameCooldownReady();
        if (config.useMinigameTeleports()
            && target != null
            && start != null
            && cooldownReady
            && !Objects.equals(minigamesSuppressedForTarget, target))
        {
            int wildernessLevel = Rs2Pvp.getWildernessLevelFrom(start);
            for (MinigameRow row : minigameRows)
            {
                if (sessionMinigameBlacklist.contains(row.displayInfo.toLowerCase(Locale.ROOT)))
                {
                    continue;
                }
                if (row.membersOnly && !members)
                {
                    continue;
                }
                if (wildernessLevel > row.maxWildernessLevel)
                {
                    continue;
                }
                if (!runtimeMinigameRequirementsPass(row))
                {
                    continue;
                }
                if (!minigameWorthConsidering(start, target, row, nativeTeleportScore)
                    || preferCarriedHouseOverMinigame(target, row.destination))
                {
                    continue;
                }

                Object shadow = buildMinigameTransport(start, row);
                if (shadow != null)
                {
                    merged.add(shadow);
                    minigameAdded++;
                }
            }
        }

        if (!magicMushtreesAllowed)
        {
            // Filter after all sources are merged, including preserved and shared catalogue rows.
            merged.removeIf(transport -> {
                try
                {
                    int id = transportObjectIdField.getInt(transport);
                    return id == 30920 || id == 30924
                        || "Magic Mushtree".equalsIgnoreCase(transportString(transport, transportTargetField));
                }
                catch (IllegalAccessException ex) { throw new IllegalStateException(ex); }
            });
        }
        applyCompleteRoute(target, merged);
        List<Object> immutableMerged = Collections.unmodifiableList(merged);
        Predicate<Object> predicate = transport -> invokeBoolean(allowTransportMethod, engine, transport);
        Object planner = buildPlannerMethod.invoke(null, immutableMerged, predicate);
        Object objectIds = collectObjectIdsMethod.invoke(null, immutableMerged);

        setField(engineClass, engine, "transports", immutableMerged);
        setField(engineClass, engine, "planner", planner);
        setField(engineClass, engine, "transportObjectIds", objectIds);
        installedMerged = immutableMerged;
        installedKey = contextKey(target);

        debug("extra transport graph target={} shared={} charters={} minigames={} minecarts={} alKharidGates={} karuulmRocks={} primio={} quetzals={} elkoy={} mushtrees={} fossil={} wildernessObelisks={} wildernessLevers={} replacedDuplicates={} shiloCartRepaired={} taverleyPipeReverseSuppressed={} cooldownReady={} coins={} charterFareSkipped={} minecartFareSkipped={} minecartFare={} totalTransports={}",
            target, sharedAdded, charterAdded, minigameAdded, minecartAdded, alKharidGateAdded, karuulmRocksAdded, primioAdded, quetzalAdded, elkoyAdded,
            mushtreeAdded, fossilAdded, wildernessObelisksAdded, wildernessLeversAdded, replacedDuplicates, shiloCartRepaired, taverleyPipeReverseSuppressed, cooldownReady, coins, charterFareSkipped,
            minecartFareSkipped, currentKourendMinecartFare(), immutableMerged.size());
    }

    private void correctWildernessShortcutRequirements(List<Object> merged) throws ReflectiveOperationException
    {
        // The legacy Deep Wilderness Dungeon crevice tests Lumbridge medium task
        // count (6312), rather than Wilderness medium completion (4467).
        for (Object edge : merged)
            if (transportInt(edge, transportObjectIdField) == 17030)
                field(edge.getClass(), "varbitRequirements").set(edge,
                    List.of(requirementCtor.newInstance(4467, 1, '=')));
    }

    /** Add the verified Mount Karuulm dungeon rock crossings without altering route policy. */
    private int addKaruulmDungeonRockEdges(List<Object> merged) throws ReflectiveOperationException
    {
        int added = 0;
        for (WorldPoint[] edge : KARUULM_DUNGEON_ROCK_EDGES)
        {
            WorldPoint origin = edge[0];
            WorldPoint destination = edge[1];
            boolean alreadyPresent = false;
            for (Object existing : merged)
            {
                WorldPoint existingOrigin = transportWorldPoint(existing, transportOriginField);
                WorldPoint existingDestination = transportWorldPoint(existing, transportDestinationField);
                String existingAction = transportString(existing, transportActionField);
                String existingTarget = transportString(existing, transportTargetField);
                int existingId = transportObjectIdField.getInt(existing);
                if (origin.equals(existingOrigin)
                    && destination.equals(existingDestination)
                    && KARUULM_DUNGEON_ROCKS_ID == existingId
                    && KARUULM_DUNGEON_ROCKS_ACTION.equalsIgnoreCase(existingAction == null ? "" : existingAction)
                    && KARUULM_DUNGEON_ROCKS_TARGET.equalsIgnoreCase(existingTarget == null ? "" : existingTarget))
                {
                    alreadyPresent = true;
                    break;
                }
            }
            if (alreadyPresent)
            {
                continue;
            }

            Object transport = transportCtor.newInstance(
                origin, destination, KARUULM_DUNGEON_ROCKS_ACTION, KARUULM_DUNGEON_ROCKS_TARGET,
                KARUULM_DUNGEON_ROCKS_ID,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), false, new int[0]);
            merged.add(0, transport);
            added++;
        }
        return added;
    }

    /**
     * Taverley Dungeon's 70 Agility obstacle pipe is bidirectional in the maintained shortcut
     * table.  When the requested destination is already on the blue-dragon side, retaining the
     * westbound edge allows a live-scene replan immediately after the squeeze-through to select
     * the same pipe in reverse and walk the player back to the entrance side.
     *
     * Keep the normal eastbound edge (2886,9799 -> 2892,9799), but remove only the exact
     * westbound reverse edge while the final target is on the blue-dragon side.  Other Taverley
     * destinations, including black dragons/hellhounds and routes leaving the dungeon, retain the
     * original bidirectional shortcut data.
     */
    private int suppressTaverleyBlueDragonPipeReverse(List<Object> merged, WorldPoint target)
    {
        if (target == null || target.getPlane() != 0
            || target.getX() < 2892 || target.getX() > 2940
            || target.getY() < 9750 || target.getY() > 9850)
        {
            return 0;
        }

        final WorldPoint inside = new WorldPoint(2892, 9799, 0);
        final WorldPoint outside = new WorldPoint(2886, 9799, 0);
        final int obstaclePipeId = 16509;
        int before = merged.size();

        merged.removeIf(existing ->
        {
            try
            {
                WorldPoint origin = transportWorldPoint(existing, transportOriginField);
                WorldPoint destination = transportWorldPoint(existing, transportDestinationField);
                int id = transportObjectIdField.getInt(existing);
                String action = transportString(existing, transportActionField);
                String object = transportString(existing, transportTargetField);

                return inside.equals(origin)
                    && outside.equals(destination)
                    && id == obstaclePipeId
                    && "Squeeze-through".equalsIgnoreCase(action == null ? "" : action)
                    && "Obstacle pipe".equalsIgnoreCase(object == null ? "" : object);
            }
            catch (IllegalAccessException ex)
            {
                throw new IllegalStateException(ex);
            }
        });

        int removed = before - merged.size();
        if (removed > 0)
        {
            debug("Taverley blue-dragon route: suppressed reverse obstacle-pipe edge {} -> {} target={}",
                inside, outside, target);
        }
        return removed;
    }

    /**
     * The bundled Efficient Walker engine data still contains the pre-update Shilo Village
     * Broken cart object id (28931). Current Dro's maintained transports.tsv uses
     * object id 2216 for the same "Climb over Broken cart" transition. Replace only
     * those exact Shilo entrance edges so the engine keeps its normal route
     * selection/execution logic while interacting with the live object that actually exists.
     */
    private int replaceShiloBrokenCartEdges(List<Object> merged) throws ReflectiveOperationException
    {
        final int brokenCartId = 2216;
        final String action = "Climb over";
        final String target = "Broken cart";
        final WorldPoint westLanding = new WorldPoint(2876, 2952, 0);
        final WorldPoint eastLanding = new WorldPoint(2880, 2952, 0);

        WorldPoint[] eastOrigins = {
            new WorldPoint(2879, 2954, 0),
            new WorldPoint(2880, 2953, 0),
            new WorldPoint(2880, 2952, 0),
            new WorldPoint(2880, 2951, 0),
            new WorldPoint(2879, 2950, 0)
        };
        WorldPoint[] westOrigins = {
            new WorldPoint(2877, 2954, 0),
            new WorldPoint(2876, 2953, 0),
            new WorldPoint(2876, 2952, 0),
            new WorldPoint(2876, 2951, 0),
            new WorldPoint(2877, 2950, 0)
        };

        // Remove every preserved copy of this exact cart crossing, regardless of its stale id.
        merged.removeIf(existing -> {
            WorldPoint origin = transportWorldPoint(existing, transportOriginField);
            WorldPoint destination = transportWorldPoint(existing, transportDestinationField);
            String existingAction = transportString(existing, transportActionField);
            String existingTarget = transportString(existing, transportTargetField);
            return isShiloCartEndpoint(origin, destination)
                && action.equalsIgnoreCase(existingAction == null ? "" : existingAction)
                && target.equalsIgnoreCase(existingTarget == null ? "" : existingTarget);
        });

        int added = 0;
        for (WorldPoint origin : eastOrigins)
        {
            Object edge = transportCtor.newInstance(origin, westLanding, action, target, brokenCartId,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), false, new int[0]);
            merged.add(0, edge);
            added++;
        }
        for (WorldPoint origin : westOrigins)
        {
            Object edge = transportCtor.newInstance(origin, eastLanding, action, target, brokenCartId,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), false, new int[0]);
            merged.add(0, edge);
            added++;
        }
        return added;
    }

    private boolean isShiloCartEndpoint(WorldPoint origin, WorldPoint destination)
    {
        if (origin == null || destination == null || origin.getPlane() != 0 || destination.getPlane() != 0)
        {
            return false;
        }
        boolean eastToWest = origin.getX() >= 2879 && origin.getX() <= 2880
            && origin.getY() >= 2950 && origin.getY() <= 2954
            && destination.equals(new WorldPoint(2876, 2952, 0));
        boolean westToEast = origin.getX() >= 2876 && origin.getX() <= 2877
            && origin.getY() >= 2950 && origin.getY() <= 2954
            && destination.equals(new WorldPoint(2880, 2952, 0));
        return eastToWest || westToEast;
    }

    private List<Transport> loadSharedCatalog()
    {
        EnumSet<TransportType> supported = EnumSet.of(
            TransportType.BOAT,
            TransportType.SHIP,
            TransportType.NPC,
            TransportType.CANOE,
            TransportType.GNOME_GLIDER,
            TransportType.MAGIC_CARPET,
            TransportType.HOT_AIR_BALLOON,
            TransportType.TELEPORTATION_PORTAL,
            TransportType.TELEPORTATION_LEVER,
            TransportType.SEASONAL_TRANSPORT);
        ArrayList<Transport> routes = new ArrayList<>(SlayerDungeonTransports.create());
        routes.addAll(OuraniaTransports.create());
        routes.addAll(MotherlodeTransports.create());
        HashSet<String> seen = new HashSet<>();
        try
        {
            for (Set<Transport> group : net.runelite.client.plugins.microbot.droslayer.walker.support.TransportResources.loadAllFromResources().values())
            {
                if (group == null) continue;
                for (Transport source : group)
                {
                    if (source == null || source.getDestination() == null)
                    {
                        continue;
                    }
                    if (OuraniaTransports.replaces(source)) continue;
                    if (MotherlodeTransports.replaces(source)) continue;
                    // Only Sticky Swamp's outbound copies bypass its working Mycelium
                    // destination handler and repeatedly click Use without choosing a stop.
                    if (StickySwampExit.replaces(source)) continue;
                    if (SlayerDungeonTransports.isSupersededTransport(source.getObjectId(), source.getOrigin())) continue;
                    // Charters and their deck exits have one dedicated executor. Shared
                    // copies would bypass its arrival checks and failure blacklist.
                    if (source.getType() == TransportType.CHARTER_SHIP
                        || CharterTransportCatalog.isDisembark(source)) continue;
                    boolean standardShared = source.getOrigin() != null && supported.contains(source.getType());
                    boolean underground = isUndergroundObjectTransport(source) || WebTravel.isWeb(source);
                    boolean lunarPlain = isLunarNetworkPlainTransport(source) || ApeAtollTransports.isGate(source);
                    boolean moonclanSpell = isSharedTeleportSpell(source);
                    boolean recordedSurfaceLink = StickySwampExit.zipline(source) || MagicAxesHutTransports.isEntry(source)
                        || TzhaarAccess.isLink(source) || WeissBoulderAccess.isLink(source)
                        || TemporossAccess.isArenaShortcut(source)
                        || TirannwnObstacleSupport.isRecordedSurfaceLink(source);
                    if ((!standardShared && !underground && !lunarPlain && !moonclanSpell && !recordedSurfaceLink)
                        || !TransportExecutionRegistry.canExecute(source))
                    {
                        continue;
                    }
                    if (seen.add(sharedEdgeKey(source)))
                    {
                        routes.add(source);
                    }
                }
            }
        }
        catch (RuntimeException ex)
        {
            log.warn("[EfficientWalker] maintained shared transport catalogue unavailable", ex);
        }
        return Collections.unmodifiableList(routes);
    }

    private int addSharedCatalogEdges(List<Object> merged) throws ReflectiveOperationException
    {
        if (sharedCatalog.isEmpty())
        {
            return 0;
        }

        Set<String> maintainedObjects = new HashSet<>();
        for (Transport source : sharedCatalog)
        {
            if (isUndergroundObjectTransport(source) || isLunarNetworkPlainTransport(source) || WebTravel.isWeb(source) || TzhaarAccess.isLink(source))
                maintainedObjects.add(catalogEdgeKey(source.getOrigin(), source.getDestination(),
                    source.getAction(), source.getName(), source.getObjectId()));
        }
        merged.removeIf(existing -> ApeAtollTransports.replacesTravel(transportInt(existing, transportObjectIdField),
            transportWorldPoint(existing, transportOriginField)) || MotherlodeTransports.replaces(transportInt(existing, transportObjectIdField),
            transportWorldPoint(existing, transportOriginField)) || maintainedObjects.contains(catalogEdgeKey(
            transportWorldPoint(existing, transportOriginField),
            transportWorldPoint(existing, transportDestinationField),
            transportString(existing, transportActionField),
            transportString(existing, transportTargetField),
            transportInt(existing, transportObjectIdField))));

        HashSet<String> seen = new HashSet<>();
        for (Object existing : merged)
        {
            WorldPoint origin = transportWorldPoint(existing, transportOriginField);
            WorldPoint destination = transportWorldPoint(existing, transportDestinationField);
            if (origin == null || destination == null) continue;
            seen.add(catalogEdgeKey(origin, destination,
                transportString(existing, transportActionField),
                transportString(existing, transportTargetField),
                transportInt(existing, transportObjectIdField)));
        }

        WorldPoint start = playerLocation();
        int added = 0;
        for (Transport source : sharedCatalog)
        {
            if (!sharedRequirementsSatisfiedNow(source, true)
                || failedSharedEdges.contains(sharedEdgeKey(source)))
            {
                continue;
            }
            String key = catalogEdgeKey(source.getOrigin(), source.getDestination(),
                source.getAction(), source.getName(), source.getObjectId());
            if (!seen.add(key))
            {
                continue;
            }
            Object edge = buildSharedCatalogTransport(source, start);
            if (edge != null)
            {
                // Put maintained edges first so an obsolete preserved duplicate cannot win by
                // identity before the executable current transport.
                merged.add(0, edge);
                sharedCatalogEdges.put(edge, source);
                added++;
            }
        }
        return added;
    }

    /** Object-backed underground links, including internal doors and above/below-plane stairs. */
    static boolean isUndergroundObjectTransport(Transport source)
    {
        if (source == null || source.getType() != TransportType.TRANSPORT
            || source.getOrigin() == null || source.getDestination() == null
            || source.getObjectId() <= 0)
            return false;
        WorldPoint from = source.getOrigin();
        WorldPoint to = source.getDestination();
        return MotherlodeTransports.isUpperLadderDescent(source)
            || from.getY() >= 6400 || to.getY() >= 6400
            || from.getPlane() != to.getPlane()
            || from.distanceTo2D(to) > 8;
    }

    static boolean isTrollStrongholdSecretEntrance(Transport source)
    {
        if (source == null || source.getType() != TransportType.TRANSPORT
            || source.getObjectId() != 3762 || !"Open".equals(source.getAction())
            || !new WorldPoint(2823, 10050, 0).equals(source.getDestination())) return false;
        return new WorldPoint(2827, 3646, 0).equals(source.getOrigin())
            || new WorldPoint(2828, 3646, 0).equals(source.getOrigin());
    }

    private boolean undergroundUnlocksSatisfied(Transport source)
    {
        for (var requirement : source.getVarbits())
            if (!requirement.matches(Microbot.getVarbitValue(requirement.getVarbitId()))) return false;
        for (var requirement : source.getVarplayers())
            if (!requirement.matches(Microbot.getVarbitPlayerValue(requirement.getVarplayerId()))) return false;
        List<QuestState> states = List.of(QuestState.NOT_STARTED, QuestState.IN_PROGRESS, QuestState.FINISHED);
        for (var requirement : source.getQuests().entrySet())
        {
            QuestState current = Rs2Player.getQuestState(requirement.getKey());
            if (current == null || states.indexOf(current) < states.indexOf(requirement.getValue())) return false;
        }
        Skill[] skills = Skill.values();
        int[] levels = source.getSkillLevels();
        for (int i = 0; i < Math.min(skills.length, levels.length); i++)
            if (levels[i] > 0 && Rs2Player.getRealSkillLevel(skills[i]) < levels[i]) return false;
        return true;
    }

    private boolean sharedRequirementsSatisfiedNow(Transport source)
    {
        return sharedRequirementsSatisfiedNow(source, false);
    }

    private boolean sharedRequirementsSatisfiedNow(Transport source, boolean planning)
    {
        if (LumbridgeSwampCaveAccess.isEntrance(source)
            && !(planning ? caveLightAvailable : CaveLightSource.hasLight())) return false;
        // This is the recorded long approach. The Trollheim teleport approach never
        // passes through this door and keeps its own spell/tablet requirements.
        if (isTrollStrongholdSecretEntrance(source)
            && Rs2Player.getQuestState(Quest.TROLL_STRONGHOLD) != QuestState.FINISHED) return false;
        if (source.getType() == TransportType.TELEPORTATION_LEVER
            && (!config.allowWildernessTravel() || !config.useWildernessLevers()
                || !client.getWorldType().contains(WorldType.MEMBERS))) return false;
        // This recorded link belongs only to a bank journey that started at Sticky Swamp.
        if (StickySwampExit.zipline(source)
            && (!stickySwampBankJourney || !undergroundUnlocksSatisfied(source))) return false;
        if (MaxPohSupport.isLeg(source) && !MaxPohSupport.usable(source)) return false;
        if (WebTravel.isWeb(source) && !WebTravel.canCut()) return false;
        if (MagicAxesHutTransports.isEntry(source) && MagicAxesDoorAction.accessProblem(
            Rs2Inventory.hasItem(MagicAxesHutTransports.LOCKPICK), Rs2Player.getBoostedSkillLevel(Skill.THIEVING)) != null) return false;
        if (source.getType() == TransportType.CANOE && !WildernessTravelPolicy.canoeAllowed(
            source.getOrigin(), source.getDestination(), config.useCanoes(), config.allowWildernessTravel())) return false;
        // Do not marshal a player lookup for every catalog edge: only the cabin needs it.
        if (EdgevilleDungeonAccess.requiresCabinAccess(source.getObjectId(), source.getOrigin())
            && !EdgevilleDungeonAccess.allowed(source.getObjectId(), source.getOrigin(), playerLocation(), Rs2Inventory.hasItem(983))) return false;
        if (ApeAtollTransports.isGate(source) && !undergroundUnlocksSatisfied(source)) return false;
        if (ApeAtollTransports.isEntrance(source) && Microbot.getVarbitValue(5027) < 35) return false;
        // Surface-to-surface Skavid shortcuts work only WITHOUT the map. With it,
        // Enter takes the player underground instead of to the catalog landing tile.
        if (source.getObjectId() >= 2805 && source.getObjectId() <= 2809
            && Rs2Inventory.hasItem("Skavid map", true)) return false;
        // Preserve the Barbarian Training unlock on the corrected whirlpool link.
        if (source.getObjectId() == 25274 && Microbot.getVarbitValue(3759) <= 1) return false;
        // The cave guard refuses entry without equipped smoke protection.
        if (source.getObjectId() == 30176
            && !TravelEquipment.isWearing("Slayer helmet", false)
            && !TravelEquipment.isWearing("Facemask", false))
            return false;
        if (isUndergroundObjectTransport(source) && !undergroundUnlocksSatisfied(source))
            return false;
        if (source.getType() == TransportType.HOT_AIR_BALLOON)
        {
            if (!config.useHotAirBalloons())
            {
                return false;
            }
            int requiredLog = balloonLogItemId(source);
            if (requiredLog <= 0 || Rs2Inventory.itemQuantity(requiredLog) < 1)
            {
                return false;
            }
        }
        if (source.getType() == TransportType.TELEPORTATION_SPELL
            && !Rs2WalkerBankingPlanner.hasRequiredTransportItems(source))
        {
            return false;
        }
        if (source.isMembers() && !client.getWorldType().contains(WorldType.MEMBERS))
        {
            return false;
        }
        if (source.getCurrencyAmount() > 0)
        {
            if (!"Coins".equalsIgnoreCase(source.getCurrencyName())
                || !net.runelite.client.plugins.microbot.droslayer.walker.support.TravelCurrency.hasAmount(
                    Rs2Inventory.itemQuantity(COINS), 0, source.getCurrencyAmount(), false))
            {
                return false;
            }
        }
        if (source.getType() != TransportType.TELEPORTATION_SPELL)
        {
            for (var requirement : source.getItemRequirements())
            {
                if (!requirement.isSatisfiedBy(id -> Rs2Inventory.itemQuantity(id)
                    + (TravelEquipment.isWearing(id) ? 1 : 0)))
                {
                    return false;
                }
            }
        }
        return true;
    }

    private Object buildSharedCatalogTransport(Transport source, WorldPoint start) throws ReflectiveOperationException
    {
        // The graph indexes fixed endpoints. Spells have no catalogue origin,
        // so anchor admitted spell edges to this planning snapshot.
        WorldPoint origin = source.getOrigin();
        // The recording approaches the wall-mounted zip line from its west tile.
        if (StickySwampExit.zipline(source)) origin = new WorldPoint(3764, 3883, 1);
        if (origin == null && (isSharedTeleportSpell(source) || MaxPohSupport.isLeg(source))) origin = start;
        if (origin == null || source.getDestination() == null) return null;

        ArrayList<Object> skills = new ArrayList<>();
        Skill[] allSkills = Skill.values();
        int[] levels = source.getSkillLevels();
        for (int i = 0; i < Math.min(levels.length, allSkills.length); i++)
        {
            if (levels[i] > 0)
            {
                skills.add(skillCtor.newInstance(allSkills[i], levels[i]));
            }
        }

        ArrayList<Object> varbits = new ArrayList<>();
        for (var requirement : source.getVarbits())
        {
            char comparator = requirement.getOperator().name().contains("GREATER") ? '>' : '=';
            varbits.add(requirementCtor.newInstance(
                requirement.getVarbitId(), requirement.getValue(), comparator));
        }

        ArrayList<Object> varplayers = new ArrayList<>();
        for (var requirement : source.getVarplayers())
        {
            char comparator = requirement.getOperator().name().contains("GREATER") ? '>' : '=';
            varplayers.add(requirementCtor.newInstance(
                requirement.getVarplayerId(), requirement.getValue(), comparator));
        }

        boolean underground = isUndergroundObjectTransport(source);
        return transportCtor.newInstance(
            origin, source.getDestination(),
            source.getAction(), source.getName(), source.getObjectId(),
            skills, Collections.emptyList(), underground ? Collections.emptyList() : varbits,
            underground ? Collections.emptyList() : varplayers,
            underground ? Collections.emptyList() : new ArrayList<>(source.getQuests().keySet()),
            false, new int[0]);
    }

    private int routeDistanceEstimate(WorldPoint start, WorldPoint target)
    {
        if (start == null || target == null || start.getPlane() != target.getPlane())
        {
            return 1_000_000;
        }
        return Math.max(Math.abs(start.getX() - target.getX()), Math.abs(start.getY() - target.getY()));
    }

    Map<Integer, Integer> bankBackedBalloonLogRequirements(WorldPoint start, WorldPoint target)
    {
        if (!config.useHotAirBalloons() || !config.useItemsFromBank()
            || start == null || target == null)
        {
            return Map.of();
        }

        int direct = routeDistanceEstimate(start, target);
        Transport best = null;
        int bestScore = direct;
        for (Transport source : sharedCatalog)
        {
            if (source == null || source.getType() != TransportType.HOT_AIR_BALLOON
                || source.getOrigin() == null || source.getDestination() == null)
            {
                continue;
            }
            if (!balloonUnlockRequirementsSatisfied(source))
            {
                continue;
            }
            int logId = balloonLogItemId(source);
            if (logId <= 0 || Rs2Bank.count(logId) <= 0)
            {
                continue;
            }
            int score = routeDistanceEstimate(start, source.getOrigin())
                + BALLOON_INTERACTION_COST
                + routeDistanceEstimate(source.getDestination(), target);
            if (score < bestScore)
            {
                bestScore = score;
                best = source;
            }
        }
        if (best == null)
        {
            return Map.of();
        }
        int logId = balloonLogItemId(best);
        int missing = Math.max(0, 1 - Rs2Inventory.itemQuantity(logId));
        return missing > 0 ? Map.of(logId, missing) : Map.of();
    }

    Map<Integer, Integer> bankBackedMoonclanRuneRequirements(WorldPoint target)
    {
        if (!config.useTeleportations() || !config.useItemsFromBank() || target == null
            || !isLunarIsle(target) || !Rs2Spells.MOONCLAN_TELEPORT.hasRequirements())
        {
            return Map.of();
        }
        Map<Runes, Integer> carried = net.runelite.client.plugins.microbot.droslayer.walker.support.TravelRunes.getRunes();
        LinkedHashMap<Integer, Integer> missing = new LinkedHashMap<>();
        addRuneDeficit(missing, Runes.EARTH, 2, carried);
        addRuneDeficit(missing, Runes.ASTRAL, 2, carried);
        addRuneDeficit(missing, Runes.LAW, 1, carried);
        for (Map.Entry<Integer, Integer> entry : missing.entrySet())
        {
            if (Rs2Bank.count(entry.getKey()) < entry.getValue())
            {
                return Map.of();
            }
        }
        return missing;
    }

    private static void addRuneDeficit(Map<Integer, Integer> missing, Runes rune, int required,
                                       Map<Runes, Integer> carried)
    {
        int deficit = Math.max(0, required - carried.getOrDefault(rune, 0));
        if (deficit > 0)
        {
            missing.put(rune.getItemId(), deficit);
        }
    }

    private boolean balloonUnlockRequirementsSatisfied(Transport source)
    {
        if (source == null
            || Rs2Player.getQuestState(Quest.ENLIGHTENED_JOURNEY) != QuestState.FINISHED)
        {
            return false;
        }
        for (var requirement : source.getVarbits())
        {
            if (!requirement.matches(Microbot.getVarbitValue(requirement.getVarbitId())))
            {
                return false;
            }
        }
        return true;
    }

    private static int balloonLogItemId(Transport source)
    {
        if (source == null || source.getDestination() == null) return -1;
        WorldPoint destination = source.getDestination();
        if (near(destination, BALLOON_ENTRANA, 2) || near(destination, BALLOON_TAVERLEY, 2))
            return ItemID.LOGS;
        if (near(destination, BALLOON_CRAFTING_GUILD, 2)) return ItemID.OAK_LOGS;
        if (near(destination, BALLOON_VARROCK, 2)) return ItemID.WILLOW_LOGS;
        if (near(destination, BALLOON_CASTLE_WARS, 2)) return ItemID.YEW_LOGS;
        if (near(destination, BALLOON_GRAND_TREE, 2)) return ItemID.MAGIC_LOGS;
        return -1;
    }

    private static boolean isSharedTeleportSpell(Transport source)
    {
        return source != null
            && source.getType() == TransportType.TELEPORTATION_SPELL
            && sharedSpellName(source.getDisplayInfo());
    }

    private static boolean sharedSpellName(String name)
    {
        return "Moonclan Teleport".equalsIgnoreCase(name) || "Ice Plateau Teleport".equalsIgnoreCase(name);
    }

    private static boolean isLunarNetworkPlainTransport(Transport source)
    {
        return source != null
            && source.getType() == TransportType.TRANSPORT
            && source.getOrigin() != null && source.getDestination() != null
            && ((isPiratesCove(source.getOrigin()) && isPiratesCove(source.getDestination()))
                || (isLunarIsle(source.getOrigin()) && isLunarIsle(source.getDestination())));
    }

    private static boolean isPiratesCove(WorldPoint point)
    {
        return point != null
            && point.getX() >= PIRATES_COVE_MIN_X && point.getX() <= PIRATES_COVE_MAX_X
            && point.getY() >= PIRATES_COVE_MIN_Y && point.getY() <= PIRATES_COVE_MAX_Y;
    }

    private static boolean isLunarIsle(WorldPoint point)
    {
        return point != null
            && point.getX() >= LUNAR_ISLE_MIN_X && point.getX() <= LUNAR_ISLE_MAX_X
            && point.getY() >= LUNAR_ISLE_MIN_Y && point.getY() <= LUNAR_ISLE_MAX_Y;
    }

    private static String catalogEdgeKey(WorldPoint origin, WorldPoint destination,
                                         String action, String target, int objectId)
    {
        return String.valueOf(origin) + ">" + String.valueOf(destination) + ":" + objectId
            + ":" + Objects.toString(action, "") + ":" + Objects.toString(target, "");
    }

    private int addElkoyEdges(List<Object> merged) throws ReflectiveOperationException
    {
        // Dro's npcs.tsv gates both Follow;Elkoy edges on Tree Gnome Village.
        // Keep that same account requirement rather than offering an unusable shortcut.
        if (Rs2Player.getQuestState(Quest.TREE_GNOME_VILLAGE) != QuestState.FINISHED)
        {
            return 0;
        }

        ArrayList<Object> elkoyEdges = new ArrayList<>(2);
        if (!requestNetworkBlacklist.contains(networkKey(
            EdgeType.ELKOY, ELKOY_SOUTH_SIDE, ELKOY_VILLAGE_SIDE)))
        {
            elkoyEdges.add(buildCustomNetworkTransport(
                EdgeType.ELKOY,
                ELKOY_SOUTH_SIDE,
                ELKOY_VILLAGE_SIDE,
                "Tree Gnome Village",
                "Elkoy",
                ELKOY_NPC_ID,
                "Follow"));
        }
        if (!requestNetworkBlacklist.contains(networkKey(
            EdgeType.ELKOY, ELKOY_VILLAGE_SIDE, ELKOY_SOUTH_SIDE)))
        {
            elkoyEdges.add(buildCustomNetworkTransport(
                EdgeType.ELKOY,
                ELKOY_VILLAGE_SIDE,
                ELKOY_SOUTH_SIDE,
                "Tree Gnome Village maze exit",
                "Elkoy",
                ELKOY_NPC_ID,
                "Follow"));
        }

        // Put these before any duplicate NPC rows.  The planner still
        // chooses whether Elkoy is useful, but our edge is guaranteed to use the executor
        // below that understands Follow + retries + landing verification.
        if (!elkoyEdges.isEmpty())
        {
            merged.addAll(0, elkoyEdges);
        }
        return elkoyEdges.size();
    }


    /**
     * Remove preserved rows that overlap custom executors. The identity map can only intercept
     * the synthetic edge that we add; if an older equivalent g.d survives beside it the planner
     * is free to select that identity instead. That was the v8 Elkoy failure: the route selected
     * Follow;Elkoy id=4968 and the generic object executor rejected the NPC as object-unavailable.
     */
    private int removeKnownCustomTransportDuplicates(List<Object> merged)
    {
        int before = merged.size();
        merged.removeIf(this::isKnownCustomTransportDuplicate);
        return before - merged.size();
    }

    private boolean isKnownCustomTransportDuplicate(Object transport)
    {
        if (transport == null)
        {
            return false;
        }

        String action = transportString(transport, transportActionField);
        if ("Charter".equalsIgnoreCase(action)) return true;
        String target = transportString(transport, transportTargetField);
        int objectId = transportInt(transport, transportObjectIdField);
        if (SlayerDungeonTransports.isSupersededTransport(objectId, transportWorldPoint(transport, transportOriginField))) return true;
        WorldPoint origin = transportWorldPoint(transport, transportOriginField);
        WorldPoint destination = transportWorldPoint(transport, transportDestinationField);

        // Always replace preserved balloon edges with the maintained, prerequisite-aware
        // copies above. Otherwise a preserved Basket edge can bypass the config toggle and
        // route without the required destination log.
        if ((objectId == 19128 || objectId == 19129)
            && "Use".equalsIgnoreCase(action) && "Basket".equalsIgnoreCase(target))
        {
            return true;
        }

        // Replace the Hole rows with live-object execution. Both stored IDs
        // failed lookup in the client log even after the direction mapping was corrected.
        if ((objectId == 31481 || objectId == 31482)
            && "Hole".equalsIgnoreCase(target)
            && origin != null && destination != null
            && origin.getX() >= 3713 && origin.getX() <= 3718
            && (origin.getY() == 3815 || origin.getY() == 3816 || origin.getY() == 3830)
            && (destination.getY() == 3815 || destination.getY() == 3830))
        {
            return true;
        }

        if ("Follow".equalsIgnoreCase(action)
            && ("Elkoy".equalsIgnoreCase(target) || objectId == ELKOY_NPC_ID))
        {
            return true;
        }

        if ("Use".equalsIgnoreCase(action)
            && "Magic Mushtree".equalsIgnoreCase(target)
            && (objectId == 30920 || objectId == 30924))
        {
            return true;
        }

        if ("Quick-Travel".equalsIgnoreCase(action)
            && ("Barge guard".equalsIgnoreCase(target) || objectId == FOSSIL_BARGE_GUARD_ID)
            && nearPoint(origin, FOSSIL_DIGSITE_BARGE, 3)
            && nearPoint(destination, FOSSIL_CAMP, 3))
        {
            return true;
        }

        // Only replace the Fossil Island LOCAL rowboat network. Keep the camp -> Digsite
        // Rowboat ship row intact because that is a distinct long-distance terminal route.
        if ("Travel".equalsIgnoreCase(action)
            && "Rowboat".equalsIgnoreCase(target)
            && (objectId == FOSSIL_CAMP_ROWBOAT_ID
                || objectId == FOSSIL_NORTH_ROWBOAT_ID
                || objectId == FOSSIL_SEA_ROWBOAT_ID)
            && isFossilLocalRowboatPoint(origin)
            && isFossilLocalRowboatPoint(destination))
        {
            return true;
        }
        return false;
    }

    private int addMagicMushtreeEdges(List<Object> merged) throws ReflectiveOperationException
    {
        if (!magicMushtreesAllowed) return 0;
        if (Rs2Player.getQuestState(Quest.BONE_VOYAGE) != QuestState.FINISHED)
        {
            return 0;
        }

        ArrayList<Object> edges = new ArrayList<>();
        for (MushtreeStop origin : MUSHTREE_STOPS)
        {
            for (WorldPoint routeOrigin : origin.routeOrigins)
            {
                for (MushtreeStop destination : MUSHTREE_STOPS)
                {
                    if (origin == destination)
                    {
                        continue;
                    }
                    String key = networkKey(EdgeType.MUSHTREE, routeOrigin, destination.landing);
                    if (sessionNetworkBlacklist.contains(key) || requestNetworkBlacklist.contains(key))
                    {
                        continue;
                    }
                    edges.add(buildCustomNetworkTransport(
                        EdgeType.MUSHTREE,
                        routeOrigin,
                        destination.landing,
                        destination.label,
                        "Magic Mushtree",
                        origin.objectId,
                        "Use"));
                }
            }
        }
        if (!edges.isEmpty())
        {
            merged.addAll(0, edges);
        }
        return edges.size();
    }

    private int addFossilHoleEdges(List<Object> merged) throws ReflectiveOperationException
    {
        if (Rs2Player.getRealSkillLevel(Skill.AGILITY) < 70
            || Rs2Player.getQuestState(Quest.BONE_VOYAGE) != QuestState.FINISHED)
        {
            return 0;
        }
        ArrayList<Object> edges = new ArrayList<>();
        for (WorldPoint origin : FOSSIL_HOLE_SHORE_ORIGINS)
        {
            addCustomEdgeIfAllowed(edges, EdgeType.FOSSIL_HOLE, origin,
                FOSSIL_HOLE_GROVE, "Hardwood grove", "Hole", 31481, "Climb through");
        }
        for (WorldPoint origin : FOSSIL_HOLE_GROVE_ORIGINS)
        {
            addCustomEdgeIfAllowed(edges, EdgeType.FOSSIL_HOLE, origin,
                FOSSIL_HOLE_SHORE, "Museum Camp", "Hole", 31482, "Climb through");
        }
        merged.addAll(0, edges);
        return edges.size();
    }

    /** Import the shared wilderness obelisk interaction tiles and destinations into Dro's graph. */
    private int addWildernessObeliskEdges(List<Object> merged) throws ReflectiveOperationException
    {
        if ((!config.allowWildernessTravel() || !config.useWildernessObelisks()) || wildernessObeliskHops >= 3) return 0;
        LinkedHashMap<String, List<WorldPoint>> origins = new LinkedHashMap<>();
        LinkedHashMap<String, WorldPoint> destinations = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> objectIds = new LinkedHashMap<>();
        try (InputStream input = net.runelite.client.plugins.microbot.droslayer.walker.support.TransportResources.openResource("wilderness_obelisks.tsv"))
        {
            if (input == null)
            {
                log.warn("[EfficientWalker] wilderness obelisk catalogue unavailable");
                return 0;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8)))
            {
                String line;
                while ((line = reader.readLine()) != null)
                {
                    if (line.isBlank() || line.startsWith("#")) continue;
                    String[] fields = line.split("\\t", -1);
                    if (fields.length < 7 || fields[6].isBlank()) continue;
                    String label = fields[6].trim();
                    if (!fields[0].isBlank() && !fields[2].isBlank())
                    {
                        WorldPoint origin = parsePoint(fields[0]);
                        String[] interaction = fields[2].split(";", -1);
                        if (origin != null && interaction.length >= 3)
                        {
                            origins.computeIfAbsent(label, ignored -> new ArrayList<>()).add(origin);
                            objectIds.put(label, Integer.parseInt(interaction[2]));
                        }
                    }
                    else if (!fields[1].isBlank())
                    {
                        WorldPoint destination = parsePoint(fields[1]);
                        if (destination != null) destinations.put(label, destination);
                    }
                }
            }
        }
        catch (IOException | RuntimeException ex)
        {
            log.warn("[EfficientWalker] could not read wilderness obelisk catalogue", ex);
            return 0;
        }

        ArrayList<Object> edges = new ArrayList<>();
        for (Map.Entry<String, List<WorldPoint>> source : origins.entrySet())
        {
            int objectId = objectIds.getOrDefault(source.getKey(), -1);
            for (Map.Entry<String, WorldPoint> destination : destinations.entrySet())
            {
                if (source.getKey().equals(destination.getKey())) continue;
                for (WorldPoint origin : source.getValue())
                {
                    addCustomEdgeIfAllowed(edges, EdgeType.WILDERNESS_OBELISK, origin,
                        destination.getValue(), destination.getKey(), "Obelisk", objectId, "Activate");
                }
            }
        }
        merged.addAll(0, edges);
        return edges.size();
    }

    /** Add the shared teleport-lever links using the walker's ordinary object executor. */
    private int addWildernessLeverEdges(List<Object> merged)
    {
        if ((!config.allowWildernessTravel() || !config.useWildernessLevers()
                || !client.getWorldType().contains(WorldType.MEMBERS))) return 0;
        int added = 0;
        try (InputStream input = net.runelite.client.plugins.microbot.droslayer.walker.support.TransportResources.openResource("teleportation_levers.tsv"))
        {
            if (input == null) return 0;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8)))
            {
                String line;
                while ((line = reader.readLine()) != null)
                {
                    if (line.isBlank() || line.startsWith("#")) continue;
                    String[] fields = line.split("\\t", -1);
                    if (fields.length < 7 || fields[0].isBlank() || fields[1].isBlank() || fields[2].isBlank()) continue;
                    String[] interaction = fields[2].split(";", -1);
                    if (interaction.length < 3) continue;
                    WorldPoint origin = parsePoint(fields[0]);
                    WorldPoint destination = parsePoint(fields[1]);
                    if (origin == null || destination == null) continue;
                    int objectId = Integer.parseInt(interaction[2]);
                    Object edge = transportCtor.newInstance(
                        origin, destination, interaction[0], interaction[1], objectId,
                        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                        Collections.emptyList(), Collections.emptyList(), false, new int[0]);
                    injectedEdges.put(edge, new NetworkEdge(
                        EdgeType.NATIVE, origin, destination, "Wilderness lever", interaction[1], objectId, 0));
                    merged.add(edge);
                    added++;
                }
            }
        }
        catch (IOException | ReflectiveOperationException | RuntimeException ex)
        {
            log.warn("[EfficientWalker] could not import wilderness teleport levers", ex);
        }
        return added;
    }

    private int addFossilIslandEdges(List<Object> merged) throws ReflectiveOperationException
    {
        if (Rs2Player.getQuestState(Quest.BONE_VOYAGE) != QuestState.FINISHED)
        {
            return 0;
        }

        ArrayList<Object> edges = new ArrayList<>();

        addCustomEdgeIfAllowed(edges, EdgeType.FOSSIL_BARGE,
            FOSSIL_DIGSITE_BARGE, FOSSIL_CAMP, "Fossil Island",
            "Barge guard", FOSSIL_BARGE_GUARD_ID, "Quick-Travel");

        // Fossil Island camp / north-coast / sea rowboat menu. The sea landing is beside
        // BankLocation.FOSSIL_ISLAND_WRECK (3766,3898), so a bank target there naturally
        // routes through this network and walks the final few tiles.
        addCustomEdgeIfAllowed(edges, EdgeType.FOSSIL_ROWBOAT,
            FOSSIL_CAMP, FOSSIL_NORTH, "2. North of Island",
            "Rowboat", FOSSIL_CAMP_ROWBOAT_ID, "Travel");
        addCustomEdgeIfAllowed(edges, EdgeType.FOSSIL_ROWBOAT,
            FOSSIL_NORTH, FOSSIL_CAMP, "2. The Camp",
            "Rowboat", FOSSIL_NORTH_ROWBOAT_ID, "Travel");
        addCustomEdgeIfAllowed(edges, EdgeType.FOSSIL_ROWBOAT,
            FOSSIL_NORTH, FOSSIL_SEA, "3. To Sea",
            "Rowboat", FOSSIL_NORTH_ROWBOAT_ID, "Travel");
        addCustomEdgeIfAllowed(edges, EdgeType.FOSSIL_ROWBOAT,
            FOSSIL_SEA, FOSSIL_NORTH, "2. North of Island",
            "Rowboat", FOSSIL_SEA_ROWBOAT_ID, "Travel");
        addCustomEdgeIfAllowed(edges, EdgeType.FOSSIL_ROWBOAT,
            FOSSIL_CAMP, FOSSIL_SEA, "4. To Sea",
            "Rowboat", FOSSIL_CAMP_ROWBOAT_ID, "Travel");
        addCustomEdgeIfAllowed(edges, EdgeType.FOSSIL_ROWBOAT,
            FOSSIL_SEA, FOSSIL_CAMP, "3. The Camp",
            "Rowboat", FOSSIL_SEA_ROWBOAT_ID, "Travel");

        if (!edges.isEmpty())
        {
            merged.addAll(0, edges);
        }
        return edges.size();
    }

    private void addCustomEdgeIfAllowed(List<Object> edges, EdgeType type,
                                        WorldPoint origin, WorldPoint destination,
                                        String label, String target, int id, String action)
        throws ReflectiveOperationException
    {
        String key = networkKey(type, origin, destination);
        if (sessionNetworkBlacklist.contains(key) || requestNetworkBlacklist.contains(key))
        {
            return;
        }
        edges.add(buildCustomNetworkTransport(type, origin, destination, label, target, id, action));
    }

    private int addPrimioEdges(List<Object> merged) throws ReflectiveOperationException
    {
        if (Rs2Player.getQuestState(Quest.CHILDREN_OF_THE_SUN) != QuestState.FINISHED)
        {
            return 0;
        }

        int added = 0;
        if (!requestNetworkBlacklist.contains(networkKey(EdgeType.PRIMIO, PRIMIO_VARROCK, PRIMIO_CIVITAS)))
        {
            merged.add(buildCustomNetworkTransport(
                EdgeType.PRIMIO, PRIMIO_VARROCK, PRIMIO_CIVITAS, "Civitas illa Fortis",
                "Primio", PRIMIO_VARROCK_NPC_ID, "Travel"));
            added++;
        }
        if (!requestNetworkBlacklist.contains(networkKey(EdgeType.PRIMIO, PRIMIO_CIVITAS, PRIMIO_VARROCK)))
        {
            merged.add(buildCustomNetworkTransport(
                EdgeType.PRIMIO, PRIMIO_CIVITAS, PRIMIO_VARROCK, "Varrock",
                "Primio", PRIMIO_CIVITAS_NPC_ID, "Travel"));
            added++;
        }
        return added;
    }

    private int addQuetzalEdges(List<Object> merged) throws ReflectiveOperationException
    {
        if (Rs2Player.getQuestState(Quest.TWILIGHTS_PROMISE) != QuestState.FINISHED)
        {
            return 0;
        }

        int added = 0;
        for (QuetzalStop origin : QUETZAL_STOPS)
        {
            if (!origin.unlocked())
            {
                continue;
            }
            for (QuetzalStop destination : QUETZAL_STOPS)
            {
                if (origin == destination || !destination.unlocked())
                {
                    continue;
                }
                if (requestNetworkBlacklist.contains(networkKey(EdgeType.QUETZAL, origin.point, destination.point)))
                {
                    continue;
                }
                Object edge = buildCustomNetworkTransport(
                    EdgeType.QUETZAL, origin.point, destination.point, destination.label,
                    "Renu", -1, "Travel");
                merged.add(edge);
                added++;
            }
        }
        return added;
    }

    private Object buildCustomNetworkTransport(EdgeType type, WorldPoint origin, WorldPoint destination,
                                               String label, String npcName, int npcId, String action)
        throws ReflectiveOperationException
    {
        Object edge = transportCtor.newInstance(
            origin, destination, action, npcName, -1,
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
            Collections.emptyList(), Collections.emptyList(), false, new int[0]);
        injectedEdges.put(edge, new NetworkEdge(type, origin, destination, label, npcName, npcId, 0));
        return edge;
    }

    private Object buildMinecartTransport(MinecartStop origin, MinecartStop destination, int fare)
        throws ReflectiveOperationException
    {
        Object edge = transportCtor.newInstance(
            origin.point, destination.point, "Travel", "Minecart", -1,
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
            Collections.emptyList(), Collections.emptyList(), false, new int[0]);
        injectedEdges.put(edge, new NetworkEdge(
            EdgeType.MINECART, origin.point, destination.point, destination.menuLabel,
            "Minecart", KOUREND_MINECART_OBJECT_ID, fare));
        return edge;
    }

    /** Add the four directional Al Kharid gate edges that the v1.11.2 parser misses. */
    private int addAlKharidGateEdges(List<Object> merged) throws ReflectiveOperationException
    {
        boolean questDone = alKharidTollFree();
        int coins = Rs2Inventory.itemQuantity(COINS);
        if (!questDone && coins < AL_KHARID_TOLL)
        {
            return 0;
        }

        String action = questDone ? "Open" : "Pay-toll(10gp)";
        int added = 0;
        for (int i = 0; i < AL_KHARID_GATE_WEST.length; i++)
        {
            Object westToEast = buildNativeGateTransport(
                AL_KHARID_GATE_WEST[i], AL_KHARID_GATE_EAST[i], action, AL_KHARID_GATE_WEST_IDS[i]);
            Object eastToWest = buildNativeGateTransport(
                AL_KHARID_GATE_EAST[i], AL_KHARID_GATE_WEST[i], action, AL_KHARID_GATE_EAST_IDS[i]);
            merged.add(westToEast);
            merged.add(eastToWest);
            added += 2;
        }
        return added;
    }

    private boolean alKharidTollFree()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(
            () -> QuestState.FINISHED.equals(Rs2Player.getQuestState(Quest.PRINCE_ALI_RESCUE)))
            .orElse(false);
    }

    private static boolean alKharidGateSide(WorldPoint point, boolean east)
    {
        return point != null && point.getPlane() == 0
            && point.getY() >= AL_KHARID_MIN_Y && point.getY() <= AL_KHARID_MAX_Y
            && (east ? point.getX() > AL_KHARID_GATE_BORDER_X : point.getX() <= AL_KHARID_GATE_BORDER_X);
    }

    /**
     * Coin reserve for the Al Kharid toll gate when the player is on one side of it and the target
     * on the other, the quest is not complete and the toll is not carried but is banked. This
     * only supplies the coins; the walker still compares the gate against going around.
     */
    int bankBackedAlKharidTollRequirement(WorldPoint target)
    {
        WorldPoint start = playerLocation();
        boolean crossesEast = alKharidGateSide(start, false) && alKharidGateSide(target, true);
        boolean crossesWest = alKharidGateSide(start, true) && alKharidGateSide(target, false);
        if (!crossesEast && !crossesWest)
        {
            return 0;
        }
        if (Rs2Inventory.itemQuantity(COINS) >= AL_KHARID_TOLL
            || Rs2Bank.count(COINS) < AL_KHARID_TOLL - Rs2Inventory.itemQuantity(COINS)
            || alKharidTollFree())
        {
            return 0;
        }
        return AL_KHARID_TOLL;
    }

    boolean hasBankBackedAlKharidTollCandidate(WorldPoint target)
    {
        return bankBackedAlKharidTollRequirement(target) > 0;
    }

    private Object buildNativeGateTransport(WorldPoint origin, WorldPoint destination,
                                            String action, int objectId)
        throws ReflectiveOperationException
    {
        Object edge = transportCtor.newInstance(
            origin, destination, action, "Gate", objectId,
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
            Collections.emptyList(), Collections.emptyList(), false, new int[0]);
        injectedEdges.put(edge, new NetworkEdge(
            EdgeType.NATIVE, origin, destination, "Al Kharid gate", "Gate", objectId, 0));
        return edge;
    }

    /**
     * Advisory check for the Kourend/Lovakengj minecart network. Minecart edges themselves
     * remain inventory-only: until the fare is actually carried they are not offered to the
     * preserved graph. This merely tells the bank coordinator that 20 banked coins can unlock
     * a useful transport network for a Kourend-area target.
     */
    int bankBackedKaramjaFareRequirement(WorldPoint target)
    {
        if (!isKaramjaTravelTarget(target))
        {
            return 0;
        }
        int carried = Rs2Inventory.itemQuantity(COINS);
        int banked = Rs2Bank.count(COINS);
        return (long) carried + banked >= KARAMJA_TRAVEL_COIN_RESERVE
            ? KARAMJA_TRAVEL_COIN_RESERVE : 0;
    }

    private static boolean isKaramjaTravelTarget(WorldPoint target)
    {
        return target != null && target.getPlane() == 0
            && target.getX() >= KARAMJA_MIN_X && target.getX() <= KARAMJA_MAX_X
            && target.getY() >= KARAMJA_MIN_Y && target.getY() <= KARAMJA_MAX_Y;
    }

    boolean hasBankBackedMinecartCandidate(WorldPoint target)
    {
        return bankBackedMinecartFareRequirement(target) > 0;
    }

    int bankBackedMinecartFareRequirement(WorldPoint target)
    {
        if (target == null || kourendMinecartStops.isEmpty() || kourendMinecartsFree())
        {
            return 0;
        }

        int carried = Rs2Inventory.itemQuantity(COINS);
        if (carried >= KOUREND_MINECART_FARE)
        {
            return 0;
        }

        int missing = KOUREND_MINECART_FARE - carried;
        if (Rs2Bank.count(COINS) < missing)
        {
            return 0;
        }

        // Keep the pre-bank trigger geographically conservative. Once the 20 coins are
        // withdrawn, Efficient Walker's actual graph—not this check—decides whether to use a cart.
        boolean targetNearNetwork = kourendMinecartStops.stream()
            .anyMatch(stop -> distance2D(stop.point, target) <= MINECART_TARGET_REGION_RADIUS);
        return targetNearNetwork ? KOUREND_MINECART_FARE : 0;
    }

    private int currentKourendMinecartFare()
    {
        return kourendMinecartsFree() ? 0 : KOUREND_MINECART_FARE;
    }

    private boolean kourendMinecartsFree()
    {
        try
        {
            return Microbot.getClientThread().runOnClientThreadOptional(
                () -> Microbot.getVarbitValue(FORSAKEN_TOWER_MINECART_VARBIT)
                    == FORSAKEN_TOWER_MINECART_FREE_VALUE)
                .orElse(false);
        }
        catch (RuntimeException ex)
        {
            debug("minecart free-state check unavailable: {}", ex.getMessage());
            return false;
        }
    }

    /**
     * Cheap advisory check used by the bank coordinator before it commits a long carried-items-only
     * route. Charter edges themselves remain inventory-only; this merely reports whether the live
     * bank mirror can fund at least one charter that would pass the same account/target admission
     * rules after the coins were withdrawn.
     */
    boolean hasBankBackedCharterCandidate(WorldPoint target)
    {
        return bankBackedCharterFareRequirement(target) > 0;
    }

    /**
     * Return a conservative one-trip coin reserve for the charter candidates admitted for this
     * target. We deliberately use the largest qualifying fare, not a guessed selected edge: after
     * withdrawal Efficient Walker replans normally and may choose any of those candidate routes.
     * Current charter fares are small, so this avoids coupling the bank coordinator to preserved
     * planner internals while guaranteeing the finally selected charter can be paid.
     */
    int bankBackedCharterFareRequirement(WorldPoint target)
    {
        if (!installed || target == null || !config.useCharterShips() || !config.useItemsFromBank())
        {
            return 0;
        }

        try
        {
            // This helper is also queried by the bank worker. Charter account checks include
            // world type, quests, varbits, and native teleport availability, all of which may
            // ultimately touch client-thread-only RuneLite state. Run the small admission pass
            // on the client thread; invoke(Supplier) is inline when the caller is already there.
            return Microbot.getClientThread().invoke(() -> bankBackedCharterFareRequirementOnClient(target));
        }
        catch (RuntimeException ex)
        {
            debug("bank-backed charter admission unavailable: {}", ex.getMessage());
            return 0;
        }
    }

    private int bankBackedCharterFareRequirementOnClient(WorldPoint target)
    {
        if (!client.getWorldType().contains(WorldType.MEMBERS))
        {
            return 0;
        }

        int carriedCoins = Rs2Inventory.itemQuantity(COINS);
        int bankCoins = Rs2Bank.count(COINS);
        if (bankCoins <= 0)
        {
            return 0;
        }

        charterCabinFever = CharterFare.cabinFever();
        charterCharos = CharterFare.charos();
        long availableCoins = (long) carriedCoins + bankCoins;
        WorldPoint start = playerLocation();
        int nativeTeleportScore = bestCarriedNativeTeleportScore(start, target);
        int reserve = 0;

        for (CharterRow row : charterRows)
        {
            if (charterFare(row) > availableCoins)
            {
                continue;
            }
            String edgeKey = charterKey(row.origin, row.destination);
            if (sessionCharterBlacklist.contains(edgeKey) || requestCharterBlacklist.contains(edgeKey))
            {
                continue;
            }
            if (!runtimeCharterRequirementsPass(row))
            {
                continue;
            }
            if (charterWorthConsidering(row, target, nativeTeleportScore))
            {
                reserve = Math.max(reserve, charterFare(row));
            }
        }
        return reserve;
    }

    private boolean charterCabinFever;
    private boolean charterCharos;
    private int charterFare(CharterRow row) {
        return CharterFare.quote(row.origin, row.destination, row.fare, charterCabinFever, charterCharos);
    }

    private Object buildCharterTransport(CharterRow row) throws ReflectiveOperationException
    {
        List<Object> varbits = buildRequirements(row.varbits);
        List<Object> skills = buildSkills(row.skills);

        Object edge = transportCtor.newInstance(
            row.origin,
            row.destination,
            row.action,
            row.npcName,
            -1, // NPC id is retained in NetworkEdge; do not pollute Efficient Walker's object-id set.
            skills,
            Collections.emptyList(),
            varbits,
            Collections.emptyList(),
            row.quests,
            false,
            new int[0]);

        injectedEdges.put(edge, new NetworkEdge(
            EdgeType.CHARTER, row.origin, row.destination, row.displayInfo,
            row.npcName, row.npcId, charterFare(row)));
        return edge;
    }

    private Object buildMinigameTransport(WorldPoint start, MinigameRow row)
        throws ReflectiveOperationException
    {
        List<Object> varbits = buildRequirements(row.varbits);
        List<Object> varplayers = buildRequirements(row.varplayers);
        List<Object> skills = buildSkills(row.skills);

        Object edge = transportCtor.newInstance(
            start,
            row.destination,
            "Minigame-teleport",
            "Grouping",
            -1,
            skills,
            Collections.emptyList(),
            varbits,
            varplayers,
            row.quests,
            false,
            new int[0]);

        injectedEdges.put(edge, new NetworkEdge(
            EdgeType.MINIGAME, start, row.destination, row.displayInfo,
            null, -1, 0));
        return edge;
    }

    private List<Object> buildRequirements(List<NumericRequirement> requirements)
        throws ReflectiveOperationException
    {
        List<Object> result = new ArrayList<>();
        for (NumericRequirement requirement : requirements)
        {
            result.add(requirementCtor.newInstance(
                requirement.id, requirement.value, requirement.comparator));
        }
        return result;
    }

    private List<Object> buildSkills(List<SkillRequirement> requirements)
        throws ReflectiveOperationException
    {
        List<Object> result = new ArrayList<>();
        for (SkillRequirement requirement : requirements)
        {
            result.add(skillCtor.newInstance(requirement.skill, requirement.level));
        }
        return result;
    }

    private boolean charterWorthConsidering(CharterRow row, WorldPoint target, int nativeTeleportScore)
    {
        // Complete paths can include a leg pointing away from the goal. Let the route
        // planner compare elapsed time; straight-line pruning hid direct port connections.
        return target != null;
    }

    /** The selected max-house is an actual route, not merely an advertised setting.
     * Only suppress a Grouping hop when a carried/worn house entry is usable and a
     * configured POH exit lands at least as close as the minigame (with 35-tile slack). */
    private boolean preferCarriedHouseOverMinigame(WorldPoint target, WorldPoint minigameLanding)
    {
        if (target == null || minigameLanding == null || !config.useMaxPoh()) return false;
        return Microbot.getClientThread().invoke((java.util.function.Supplier<Boolean>) () -> MaxPohSupport.canTravelWithCarriedSupplies()
            && MaxPohSupport.routes().stream().filter(MaxPohSupport::usable)
                .map(Transport::getDestination).filter(Objects::nonNull)
                .anyMatch(landing -> distance2D(landing, target) <= distance2D(minigameLanding, target) + 35));
    }

    private boolean minigameWorthConsidering(WorldPoint start, WorldPoint target,
                                              MinigameRow row, int nativeTeleportScore)
    {
        int directRunTiles = distance2D(start, target);
        int runAfterTeleportTiles = distance2D(row.destination, target);
        if (directRunTiles == Integer.MAX_VALUE || runAfterTeleportTiles == Integer.MAX_VALUE)
        {
            return false;
        }

        // Fixed time-cost rule requested for Grouping teleports. A <=60 tile run always wins,
        // and for longer trips the minigame must still beat direct running after charging the
        // full 60-tile equivalent plus whatever remains after the teleport landing.
        if (directRunTiles <= MINIGAME_FIXED_RUN_TILE_COST
            || directRunTiles <= MINIGAME_FIXED_RUN_TILE_COST + runAfterTeleportTiles)
        {
            return false;
        }

        int minigameEstimate = MINIGAME_FIXED_RUN_TILE_COST + approximateWalkScore(row.destination, target);
        return nativeTeleportScore == Integer.MAX_VALUE || nativeTeleportScore > minigameEstimate;
    }

    /** True when the preview currently selected one of our Grouping/minigame edges. */
    boolean activePreviewUsesMaxPoh()
    {
        return MaxPohSupport.isLeg(sharedCatalogEdges.get(activeTransportFieldValue()))
            || nextPlannedHouseLeg() != null;
    }

    boolean activePreviewUsesMinigame()
    {
        NetworkEdge edge = activeInjectedEdge();
        return edge != null && edge.type == EdgeType.MINIGAME;
    }

    /**
     * Suppress Grouping/minigame edges for one target and force the next graph refresh to rebuild.
     * The weighted route advisor uses this when the full multimodal comparison proves a faster
     * teleport/transport/bank chain exists.
     */
    void suppressMinigamesForTarget(WorldPoint target, String reason)
    {
        if (target == null)
        {
            return;
        }
        minigamesSuppressedForTarget = target;
        installedKey = null;
        debug("minigames suppressed for target={} reason={}", target, reason);
    }

    private int bestCarriedNativeTeleportScore(WorldPoint start, WorldPoint target)
    {
        if (start == null || target == null || !config.useTeleportations())
        {
            return Integer.MAX_VALUE;
        }

        try
        {
            // Native teleport availability checks can inspect item definitions, which are
            // client-thread-only on this RuneLite/Dro build. Keep the calculation
            // identical, but marshal it when planning is requested from a script worker.
            return Microbot.getClientThread().invoke(() ->
            {
                int best = Integer.MAX_VALUE;
                for (TeleportProviders.TeleportProvider provider : TeleportProviders.createProviders())
                {
                    WorldPoint landing = provider.getDestination();
                    if (landing == null || !provider.isAvailableForRoute(start, target))
                    {
                        continue;
                    }
                    int score = 10 + approximateWalkScore(landing, target);
                    best = Math.min(best, score);
                }

                // The facade injects the remainder of Dro's TELEPORTATION_ITEM catalogue
                // into the planner. Include those same carried-item landings in this
                // admission comparison so a charter/minigame can never displace a newly-known
                // direct teleport merely because v1.11.2's original Java list was shorter.
                int catalogBest = catalogTeleportSupport.bestCarriedScore(
                    start, target, landing -> approximateWalkScore(landing, target));
                return Math.min(best, catalogBest);
            });
        }
        catch (RuntimeException ex)
        {
            debug("native teleport comparison unavailable: {}", ex.getMessage());
            return Integer.MAX_VALUE;
        }
    }

    private boolean runtimeCharterRequirementsPass(CharterRow row)
    {
        for (SkillRequirement requirement : row.skills)
            if (Rs2Player.getRealSkillLevel(requirement.skill) < requirement.level) return false;
        for (Quest quest : row.quests)
        {
            if (Rs2Player.getQuestState(quest) != QuestState.FINISHED)
            {
                return false;
            }
        }
        for (NumericRequirement requirement : row.varbits)
        {
            if (!numericRequirementMatches(requirement, Microbot.getVarbitValue(requirement.id)))
            {
                return false;
            }
        }
        return true;
    }

    private boolean runtimeMinigameRequirementsPass(MinigameRow row)
    {
        // Re-check every machine-readable requirement against the live account instead of
        // relying only on the engine predicate. This makes minigame availability
        // explicitly per-account before the edge is ever offered to Efficient Walker.
        for (SkillRequirement requirement : row.skills)
        {
            if (Rs2Player.getRealSkillLevel(requirement.skill) < requirement.level)
            {
                return false;
            }
        }
        for (Quest quest : row.quests)
        {
            if (Rs2Player.getQuestState(quest) != QuestState.FINISHED)
            {
                return false;
            }
        }
        for (NumericRequirement requirement : row.varbits)
        {
            if (!numericRequirementMatches(requirement, Microbot.getVarbitValue(requirement.id)))
            {
                return false;
            }
        }
        for (NumericRequirement requirement : row.varplayers)
        {
            if (!numericRequirementMatches(requirement, Microbot.getVarbitPlayerValue(requirement.id)))
            {
                return false;
            }
        }

        String label = row.displayInfo == null ? "" : row.displayInfo.trim();

        // Dro's TSV documents this in a comment rather than a column.
        if ("Pest Control".equalsIgnoreCase(label))
        {
            Player player = client.getLocalPlayer();
            if (player == null || player.getCombatLevel() < 40)
            {
                return false;
            }
        }

        // Current Grouping data does not encode the Sleeping Giants / Smithing gate.
        if ("Giant's Foundry".equalsIgnoreCase(label))
        {
            if (Rs2Player.getRealSkillLevel(Skill.SMITHING) < 15
                || !questFinishedByName("Sleeping Giants"))
            {
                return false;
            }
        }

        // NMZ requires any five qualifying quest-boss unlocks. The public transport TSV does
        // not encode that aggregate rule, so count it directly from the logged-in account.
        if ("Nightmare Zone".equalsIgnoreCase(label) && nightmareZoneQuestCount() < 5)
        {
            return false;
        }

        for (CooldownRequirement requirement : row.cooldowns)
        {
            int actual = Microbot.getVarbitPlayerValue(requirement.id);
            long elapsed = (System.currentTimeMillis() / 60_000L) - (long) actual;
            if (actual > 0 && elapsed <= requirement.minutes)
            {
                return false;
            }
        }
        return true;
    }

    private static boolean numericRequirementMatches(NumericRequirement requirement, int actual)
    {
        return requirement.comparator == '>' ? actual > requirement.value : actual == requirement.value;
    }

    private static boolean questFinishedByName(String name)
    {
        for (Quest quest : Quest.values())
        {
            if (quest.getName().equalsIgnoreCase(name))
            {
                return Rs2Player.getQuestState(quest) == QuestState.FINISHED;
            }
        }
        return false;
    }

    private static int nightmareZoneQuestCount()
    {
        int count = 0;
        for (Quest quest : Quest.values())
        {
            if (NIGHTMARE_ZONE_QUESTS.contains(quest.getName())
                && Rs2Player.getQuestState(quest) == QuestState.FINISHED)
            {
                count++;
            }
        }
        return count;
    }

    private boolean minigameCooldownReady()
    {
        int last = Microbot.getVarbitPlayerValue(LAST_MINIGAME_TELEPORT_VARPLAYER);
        if (last <= 0)
        {
            return true;
        }
        long elapsed = (System.currentTimeMillis() / 60_000L) - (long) last;
        return elapsed > MINIGAME_COOLDOWN_MINUTES;
    }

    private ExecutionResult executeMinigame(NetworkEdge edge)
    {
        ExecutionResult result = executeMinigameAttempt(edge);
        // One transient UI rejection must not strand the Crash Island return for the whole session.
        if (result == ExecutionResult.FAILED && ApeAtollTransports.crashIsland(edge.origin)
            && !Thread.currentThread().isInterrupted()) return executeMinigameAttempt(edge);
        return result;
    }

    private ExecutionResult executeMinigameAttempt(NetworkEdge edge)
    {
        try
        {
            if(!net.runelite.client.plugins.microbot.droslayer.support.TeleportBankGuard.ready())return ExecutionResult.BANK_OPEN;
            if (!minigameCooldownReady())
            {
                return ExecutionResult.COOLDOWN;
            }
            if (Rs2Dialogue.isInDialogue())
            {
                // The Crash Island fallback can inherit the preceding boat's dialogue.
                // Dismiss that stale conversation before opening Grouping; it is not an unlock failure.
                if (!ApeAtollTransports.crashIsland(edge.origin)) return minigameUiFailure(edge, "dialogue-open");
                Rs2Keyboard.keyPress(java.awt.event.KeyEvent.VK_ESCAPE);
                if (!waitUntil(() -> !Rs2Dialogue.isInDialogue(), 2_000L)) return minigameUiFailure(edge, "dialogue-close");
            }

            // Tab switching returns before its child widgets have necessarily loaded.
            if (Rs2Tab.getCurrentTab() != InterfaceTab.CHAT)
            {
                Rs2Tab.switchTo(InterfaceTab.CHAT);
                if (!waitUntil(() -> Rs2Tab.getCurrentTab() == InterfaceTab.CHAT, MINIGAME_UI_WAIT_MS))
                    return minigameUiFailure(edge, "chat-tab");
            }
            if (!waitUntil(() -> minigameWidgetVisible(GROUPING_BUTTON_COMPONENT_ID), MINIGAME_UI_WAIT_MS))
                return minigameUiFailure(edge, "grouping-button");

            // The recorded UI opens 707.5, then renders the actual 76.x panel. An internal
            // onOp listener is not evidence that this panel is ready or even selected.
            if (!minigameWidgetVisible(SELECTED_MINIGAME_COMPONENT_ID))
            {
                if (!Rs2Widget.clickWidget(Rs2Widget.getWidget(GROUPING_BUTTON_COMPONENT_ID)))
                    return minigameUiFailure(edge, "grouping-click");
                if (!waitUntil(() -> minigameWidgetVisible(SELECTED_MINIGAME_COMPONENT_ID), MINIGAME_UI_WAIT_MS))
                    return minigameUiFailure(edge, "grouping-panel");
            }

            boolean multipleDestination = edge.label.contains(":");
            String destination = (multipleDestination ? edge.label.split(":", 2)[0] : edge.label)
                .trim().toLowerCase(Locale.ROOT);

            if (!minigameSelected(destination))
            {
                if (!waitUntil(() -> minigameWidgetVisible(DROPDOWN_BUTTON_COMPONENT_ID), MINIGAME_UI_WAIT_MS))
                    return minigameUiFailure(edge, "destination-dropdown");
                if (!minigameListReady())
                {
                    if (!Rs2Widget.clickWidget(Rs2Widget.getWidget(DROPDOWN_BUTTON_COMPONENT_ID)))
                        return minigameUiFailure(edge, "dropdown-click");
                    if (!waitUntil(this::minigameListReady, MINIGAME_UI_WAIT_MS))
                        return minigameUiFailure(edge, "destination-list");
                }
                if (!waitUntil(() -> minigameDestinationIndex(destination) >= 0, MINIGAME_UI_WAIT_MS))
                    return ExecutionResult.MISSING_DESTINATION;
                int destinationIndex = minigameDestinationIndex(destination);
                if (destinationIndex < 0) return minigameUiFailure(edge, "destination-disappeared");
                NewMenuEntry select = new NewMenuEntry()
                    .option("Select").target("").identifier(1).type(MenuAction.CC_OP)
                    .param0(destinationIndex).param1(MINIGAME_LIST_COMPONENT_ID).forceLeftClick(false);
                Microbot.doInvoke(select, new Rectangle(1, 1));
                if (!waitUntil(() -> minigameSelected(destination), MINIGAME_UI_WAIT_MS))
                    return minigameUiFailure(edge, "destination-selection");
            }

            WorldPoint startLocation = playerLocation();
            int cooldownBefore = Microbot.getVarbitPlayerValue(LAST_MINIGAME_TELEPORT_VARPLAYER);

            if (!waitUntil(() -> minigameWidgetVisible(TELEPORT_BUTTON_COMPONENT_ID), MINIGAME_UI_WAIT_MS))
                return minigameUiFailure(edge, "teleport-button");
            Widget teleportButton = Rs2Widget.getWidget(TELEPORT_BUTTON_COMPONENT_ID);
            if (teleportButton == null || !Rs2Widget.clickWidget(teleportButton))
            {
                return ExecutionResult.FAILED;
            }
            debug("minigame teleport clicked {} from={} cooldownBefore={}",
                edge.label, startLocation, cooldownBefore);

            if (edge.label.toLowerCase(Locale.ROOT).contains("rat pits") && edge.label.contains(":"))
            {
                // Rat Pits activation is not complete until the secondary destination choice is
                // made. Do not start the fixed 15-second channel clock before this UI step.
                if (!waitUntil(Rs2Dialogue::hasSelectAnOption, 8_000L))
                {
                    debug("rat-pits destination dialogue did not appear {}", edge.label);
                    return ExecutionResult.FAILED;
                }

                String option = edge.label.split(":", 2)[1].trim();
                if (!Rs2Dialogue.clickOption(option)
                    && !Rs2Dialogue.clickOption(option.toLowerCase(Locale.ROOT)))
                {
                    debug("rat-pits destination option failed {}", option);
                    return ExecutionResult.FAILED;
                }
            }

            // Do not infer completion from a one-tick animation, a widget disappearing, or the
            // engine dropping its active transport. Once activation is complete, hold
            // this custom minigame transition for the protected full 15 seconds.
            debug("minigame activated {}; fixed channel hold={}ms expected={}",
                edge.label, MINIGAME_FIXED_CHANNEL_MS, edge.destination);
            if (!waitFixed(MINIGAME_FIXED_CHANNEL_MS))
            {
                return ExecutionResult.FAILED;
            }

            if (near(playerLocation(), edge.destination, LANDING_RADIUS))
            {
                return ExecutionResult.SUCCESS;
            }

            // Extra post-channel time for slow ticks, loading and landing-coordinate variance.
            boolean landed = waitUntil(() -> near(playerLocation(), edge.destination, LANDING_RADIUS),
                MINIGAME_LANDING_GRACE_MS);
            if (!landed)
            {
                debug("minigame fixed channel elapsed without landing {} expected={} actual={} cooldownNow={}",
                    edge.label, edge.destination, playerLocation(),
                    Microbot.getVarbitPlayerValue(LAST_MINIGAME_TELEPORT_VARPLAYER));
            }
            return landed ? ExecutionResult.SUCCESS : ExecutionResult.FAILED;
        }
        catch (RuntimeException ex)
        {
            debug("minigame execution exception {}: {}", edge.label, ex.getMessage());
            return ExecutionResult.FAILED;
        }
    }

    private boolean minigameWidgetVisible(int id)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget widget = Rs2Widget.getWidget(id);
            return widget != null && !widget.isHidden();
        }).orElse(false);
    }

    private boolean minigameSelected(String destination)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget selected = Rs2Widget.getWidget(SELECTED_MINIGAME_COMPONENT_ID);
            return selected != null && !selected.isHidden() && selected.getText() != null
                && net.runelite.client.util.Text.removeTags(selected.getText()).trim().equalsIgnoreCase(destination);
        }).orElse(false);
    }

    private int minigameDestinationIndex(String destination)
    {
        return Microbot.getClientThread().invoke(() -> {
            Widget root = Rs2Widget.getWidget(MINIGAME_LIST_COMPONENT_ID);
            if (root == null || root.getDynamicChildren() == null) return -1;
            List<Widget> widgets = Arrays.stream(root.getDynamicChildren())
                .filter(Objects::nonNull).collect(Collectors.toList());
            Widget choice = Rs2Widget.findWidget(destination, widgets);
            return choice == null ? -1 : choice.getIndex();
        });
    }

    private boolean minigameListReady()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget root = Rs2Widget.getWidget(MINIGAME_LIST_COMPONENT_ID);
            return root != null && !root.isHidden() && root.getDynamicChildren() != null
                && root.getDynamicChildren().length > 0;
        }).orElse(false);
    }

    private ExecutionResult minigameUiFailure(NetworkEdge edge, String stage)
    {
        log.info("[EfficientWalker] minigame UI did not become ready destination={} stage={}; teleport not activated",
            edge.label, stage);
        return ExecutionResult.FAILED;
    }

    private ExecutionResult finishMenuExecution(EdgeType type, ExecutionResult result)
    {
        if (result == ExecutionResult.SUCCESS || Thread.currentThread().isInterrupted()
            || Microbot.pauseAllScripts.get()
            || net.runelite.client.plugins.microbot.util.input.InputArbiter.isHuman()) return result;
        BooleanSupplier visible;
        switch (type)
        {
            case MINECART: visible = this::minecartMenuShellVisible; break;
            case MUSHTREE: visible = this::mushtreeMenuVisible; break;
            case QUETZAL: visible = this::isQuetzalMapVisible; break;
            case FOSSIL_ROWBOAT: visible = Rs2Dialogue::hasSelectAnOption; break;
            default: return result;
        }
        if (visible.getAsBoolean())
        {
            Rs2Keyboard.keyPress(java.awt.event.KeyEvent.VK_ESCAPE);
            boolean closed = waitUntil(() -> !visible.getAsBoolean(), 600L);
            log.info("[EfficientWalker] transport menu abandoned type={} result={} closed={}", type, result, closed);
        }
        return result;
    }

    private ExecutionResult executeMinecart(NetworkEdge edge)
    {
        try
        {
            if (edge.fare > 0 && Rs2Inventory.itemQuantity(COINS) < edge.fare)
            {
                return ExecutionResult.INSUFFICIENT_COINS;
            }
            if (near(playerLocation(), edge.destination, LANDING_RADIUS))
            {
                return ExecutionResult.SUCCESS;
            }

            // Kourend uses 947:9; keep support for the older Adventure Log layout.
            if (!minecartMenuShellVisible())
            {
                TileObject cart = Microbot.getClientThread().invoke(() ->
                    SceneObjects.<TileObject>getAll(
                            object -> object != null && object.getId() == KOUREND_MINECART_OBJECT_ID,
                            edge.origin, 5)
                        .stream()
                        .min(Comparator.comparingInt((TileObject object) ->
                            object.getWorldLocation().distanceTo2D(edge.origin)))
                        .orElse(null));
                if (cart == null || !SceneObjects.interact(cart, "Travel"))
                {
                    return ExecutionResult.FAILED;
                }

            }
            // The menu shell can precede its title/options. Never click through that shell.
            if (!waitUntil(this::minecartMenuVisible, MINECART_MENU_WAIT_MS)) return ExecutionResult.FAILED;

            if (!waitUntil(() -> menuWidgetReady(findMinecartDestination(edge.label)), MINECART_MENU_WAIT_MS))
                return ExecutionResult.MISSING_DESTINATION;
            Widget destinationWidget = findMinecartDestination(edge.label);

            if (destinationWidget == null || !Rs2Widget.clickWidget(destinationWidget))
            {
                return ExecutionResult.MISSING_DESTINATION;
            }

            return waitUntil(() -> near(playerLocation(), edge.destination, LANDING_RADIUS),
                MINECART_LANDING_WAIT_MS)
                ? ExecutionResult.SUCCESS : ExecutionResult.FAILED;
        }
        catch (RuntimeException ex)
        {
            debug("minecart execution exception {}: {}", edge.label, ex.getMessage());
            return ExecutionResult.FAILED;
        }
    }

    private Widget findMinecartDestination(String label)
    {
        Widget root = !Rs2Widget.isHidden(MINECART_MENU_GROUP_ID, MINECART_MENU_CHOICES_CHILD_ID)
            ? Rs2Widget.getWidget(MINECART_MENU_GROUP_ID, MINECART_MENU_CHOICES_CHILD_ID)
            : Rs2Widget.getWidget(ADVENTURE_LOG_GROUP_ID, ADVENTURE_LOG_CHOICES_CHILD_ID);
        Widget destinationWidget = root == null
            ? null
            : Rs2Widget.findWidget(label, List.of(root), false);

        // Some revisions omit the "1: "/"A: " prefix in the rendered text. Fall back to the
        // human destination name without changing the authoritative TSV label stored on the edge.
        if (destinationWidget == null && root != null)
        {
            String fallbackLabel = stripMinecartMenuPrefix(label);
            destinationWidget = Rs2Widget.findWidget(fallbackLabel, List.of(root), false);
        }

        return destinationWidget;
    }

    private static boolean menuWidgetReady(Widget widget)
    {
        if (widget == null) return false;
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            java.awt.Rectangle bounds = widget.getBounds();
            return !widget.isHidden() && bounds != null && bounds.width > 0 && bounds.height > 0;
        }).orElse(false);
    }

    private boolean minecartMenuVisible()
    {
        return !Rs2Widget.isHidden(MINECART_MENU_GROUP_ID, MINECART_MENU_CHOICES_CHILD_ID)
            || !Rs2Widget.isHidden(ADVENTURE_LOG_GROUP_ID, ADVENTURE_LOG_CHOICES_CHILD_ID)
                && Rs2Widget.hasWidgetText("Minecart", ADVENTURE_LOG_GROUP_ID, 0, false);
    }

    private boolean minecartMenuShellVisible()
    {
        return !Rs2Widget.isHidden(MINECART_MENU_GROUP_ID, MINECART_MENU_CHOICES_CHILD_ID)
            || !Rs2Widget.isHidden(ADVENTURE_LOG_GROUP_ID, ADVENTURE_LOG_CHOICES_CHILD_ID);
    }

    private static String stripMinecartMenuPrefix(String label)
    {
        if (label == null)
        {
            return "";
        }
        int colon = label.indexOf(':');
        return colon >= 0 && colon + 1 < label.length()
            ? label.substring(colon + 1).trim()
            : label.trim();
    }

    private ExecutionResult executeElkoy(NetworkEdge edge)
    {
        try
        {
            WorldPoint player = playerLocation();
            if (elkoyLandingReached(edge, player))
            {
                return ExecutionResult.SUCCESS;
            }

            // Inbound Tree Gnome Village routing is a two-edge chain: Loose Railing first, then
            // Elkoy. The walker normally owns the railing edge. Keep this as a hard
            // failsafe only for the exact SOUTH->VILLAGE Elkoy edge if that edge becomes active
            // while the player is still one tile north of the railing.
            if (ELKOY_SOUTH_SIDE.equals(edge.origin)
                && player != null
                && player.distanceTo2D(ELKOY_RAIL_SPIRIT_TREE_SIDE) <= 2
                && !hasPassedElkoyRailing(player))
            {
                debug("Elkoy inbound prerequisite: squeezing Loose Railing {} -> {} from {}",
                    ELKOY_RAIL_SPIRIT_TREE_SIDE, ELKOY_RAIL_SOUTH, player);
                boolean squeezed = SceneObjects.interact(
                        ELKOY_RAIL_SPIRIT_TREE_SIDE, "Squeeze-through")
                    || SceneObjects.interact("Loose Railing", "Squeeze-through");
                if (!squeezed
                    || !waitUntil(() -> hasPassedElkoyRailing(playerLocation()), 8_000L))
                {
                    return ExecutionResult.FAILED;
                }
            }

            // Rs2Walker terminal-NPC logic: resolve the NPC, require it to be locally walkable,
            // then resolve the configured action EXACTLY from the live NPC menu before clicking.
            // Do not fall through to object handling for NPC transports.
            waitUntil(() -> elkoyNpc() != null, ELKOY_LOAD_WAIT_MS);
            Rs2NpcModel elkoy = elkoyNpc();
            if (elkoy == null || !Rs2Npc.canWalkTo(elkoy, ELKOY_NPC_WALK_RADIUS))
            {
                debug("Elkoy terminal NPC unavailable/unwalkable at {} for edge {} -> {}",
                    playerLocation(), edge.origin, edge.destination);
                return ExecutionResult.FAILED;
            }

            String action = Rs2Npc.getAvailableAction(elkoy, Collections.singletonList("Follow"));
            if (action == null || action.isBlank())
            {
                debug("Elkoy terminal NPC has no live Follow action at {} for edge {} -> {}",
                    playerLocation(), edge.origin, edge.destination);
                return ExecutionResult.FAILED;
            }

            WorldPoint before = playerLocation();
            log.info("[EfficientWalker] Elkoy terminal NPC click action={} at={} edge={} -> {}",
                action, before, edge.origin, edge.destination);
            if (!Rs2Npc.interact(elkoy, action))
            {
                return ExecutionResult.FAILED;
            }

            // Mirror Rs2Walker terminal travel: let the server perform any final approach, then
            // wait for a real landing rather than treating the click itself as success.
            Rs2Player.waitForWalking();
            boolean landed = waitUntil(
                () -> elkoyLandingReached(edge, playerLocation())
                    && !near(playerLocation(), edge.origin, 1),
                ELKOY_LANDING_WAIT_MS);
            if (landed)
            {
                log.info("[EfficientWalker] Elkoy terminal NPC landed at {} for {} -> {}",
                    playerLocation(), edge.origin, edge.destination);
                return ExecutionResult.SUCCESS;
            }

            debug("Elkoy terminal NPC landing timed out start={} now={} edge={} -> {}",
                before, playerLocation(), edge.origin, edge.destination);
            return ExecutionResult.FAILED;
        }
        catch (RuntimeException ex)
        {
            debug("Elkoy execution exception {} -> {}: {}",
                edge.origin, edge.destination, ex.getMessage());
            return ExecutionResult.FAILED;
        }
    }

    private boolean isOnElkoyDestinationSide(NetworkEdge edge, WorldPoint player)
    {
        if (edge == null || player == null || edge.origin == null || edge.destination == null
            || player.getPlane() != edge.origin.getPlane()
            || player.getPlane() != edge.destination.getPlane())
        {
            return false;
        }
        return player.distanceTo2D(edge.destination) < player.distanceTo2D(edge.origin);
    }

    private Rs2NpcModel elkoyNpc()
    {
        Rs2NpcModel elkoy = Rs2Npc.getNpc(ELKOY_NPC_ID);
        return elkoy != null ? elkoy : Rs2Npc.getNpc("Elkoy");
    }

    private boolean elkoyLandingReached(NetworkEdge edge, WorldPoint point)
    {
        if (edge == null || point == null || point.getPlane() != 0)
        {
            return false;
        }
        if (near(point, edge.destination, LANDING_RADIUS))
        {
            return true;
        }

        if (ELKOY_VILLAGE_SIDE.equals(edge.destination))
        {
            // Actual landings can vary by a few tiles around Elkoy's village-side endpoint.
            return point.getX() <= 2507 && point.getY() >= 3174;
        }
        if (ELKOY_SOUTH_SIDE.equals(edge.destination))
        {
            return hasPassedElkoyRailing(point);
        }
        return false;
    }

    private boolean hasPassedElkoyRailing(WorldPoint point)
    {
        if (point == null || point.getPlane() != ELKOY_RAIL_SOUTH.getPlane())
        {
            return false;
        }
        return point.getY() <= ELKOY_RAIL_SOUTH.getY()
            && point.getX() >= 2509 && point.getX() <= 2522
            && point.getY() >= 3155;
    }


    private MushtreeStop mushtreeStopForRouteOrigin(WorldPoint routeOrigin)
    {
        if (routeOrigin == null)
        {
            return null;
        }
        for (MushtreeStop stop : MUSHTREE_STOPS)
        {
            for (WorldPoint candidate : stop.routeOrigins)
            {
                if (candidate.equals(routeOrigin))
                {
                    return stop;
                }
            }
        }
        return null;
    }

    private boolean mushtreeMenuVisible()
    {
        return Rs2Widget.isWidgetVisible(608, 0) || Rs2Widget.hasWidget("Mycelium");
    }

    private ExecutionResult executeMagicMushtree(NetworkEdge edge)
    {
        try
        {
            // A previous Use may have opened Mycelium after its worker timed out.
            // Keep the existing destination selection; never click the world tree through an open menu.
            if (!mushtreeMenuVisible())
            {
                // The three island-side Mushtree origins are live object tiles captured in game.
                // Keep House on the Hill's already-working wider lookup unchanged, but make the
                // shared 30924 trees resolve tightly around their explicit physical tile so a
                // different nearby mushroom cannot be selected.
                MushtreeStop stop = mushtreeStopForRouteOrigin(edge.origin);
                WorldPoint objectAnchor = stop == null ? edge.origin : stop.objectTile;
                int searchRadius = edge.npcId == 30920 ? 8 : 2;
                TileObject mushtree = nearestObjectById(edge.npcId, objectAnchor, searchRadius);
                if (mushtree == null)
                {
                    debug("Magic Mushtree object {} not found near objectTile={} routeOrigin={}",
                        edge.npcId, objectAnchor, edge.origin);
                    return ExecutionResult.FAILED;
                }

                log.info("[EfficientWalker] Magic Mushtree Use routeOrigin={} objectTile={} -> {}",
                    edge.origin, mushtree.getWorldLocation(), edge.label);
                if (!SceneObjects.interact(mushtree, "Use"))
                {
                    return ExecutionResult.FAILED;
                }

                if (!waitUntil(this::mushtreeMenuVisible, MUSHTREE_WIDGET_WAIT_MS))
                {
                    return ExecutionResult.FAILED;
                }
            }
            if (!waitUntil(() -> menuWidgetReady(Rs2Widget.findWidget(edge.label)), MUSHTREE_WIDGET_WAIT_MS)
                || !Rs2Widget.clickWidget(edge.label))
            {
                log.info("[EfficientWalker] Mycelium destination '{}' is not available; falling back",
                    edge.label);
                return ExecutionResult.MISSING_DESTINATION;
            }

            boolean landed = waitUntil(
                () -> near(playerLocation(), edge.destination, LANDING_RADIUS),
                MUSHTREE_LANDING_WAIT_MS);
            return landed ? ExecutionResult.SUCCESS : ExecutionResult.FAILED;
        }
        catch (RuntimeException ex)
        {
            debug("Magic Mushtree execution exception {}: {}", edge.label, ex.getMessage());
            return ExecutionResult.FAILED;
        }
    }

    private ExecutionResult executeFossilBarge(NetworkEdge edge)
    {
        try
        {
            Rs2NpcModel guard = Rs2Npc.getNpc(edge.npcId);
            if (guard == null)
            {
                guard = Rs2Npc.getNpc(edge.npcName);
            }
            if (guard == null || !Rs2Npc.canWalkTo(guard, 20))
            {
                return ExecutionResult.FAILED;
            }

            String action = Rs2Npc.getAvailableAction(
                guard, Collections.singletonList("Quick-Travel"));
            if (action == null || action.isBlank())
            {
                return ExecutionResult.FAILED;
            }

            log.info("[EfficientWalker] Fossil Island Barge guard {} at={}",
                action, playerLocation());
            // The guard stands at the jetty edge and is often behind the camera on arrival.
            if (!Rs2Camera.isTileOnScreen(guard.getLocalLocation()))
            {
                Rs2Camera.turnTo(guard);
            }
            if (!Rs2Npc.interact(guard, action))
            {
                return ExecutionResult.FAILED;
            }

            boolean landed = waitUntil(
                () -> near(playerLocation(), edge.destination, LANDING_RADIUS),
                FOSSIL_LANDING_WAIT_MS);
            return landed ? ExecutionResult.SUCCESS : ExecutionResult.FAILED;
        }
        catch (RuntimeException ex)
        {
            debug("Fossil barge execution exception: {}", ex.getMessage());
            return ExecutionResult.FAILED;
        }
    }

    private ExecutionResult executeFossilRowboat(NetworkEdge edge)
    {
        try
        {
            if (edge.label == null || edge.label.isBlank()) return ExecutionResult.FAILED;
            String destinationOption = FossilRowboatMenu.destination(edge.label);
            if (!Rs2Dialogue.hasSelectAnOption())
            {
                TileObject rowboat = nearestObjectById(edge.npcId, edge.origin, 8);
                if (rowboat == null)
                {
                    return ExecutionResult.FAILED;
                }

                log.info("[EfficientWalker] Fossil rowboat Travel at={} option='{}'",
                    playerLocation(), edge.label);
                if (!SceneObjects.interact(rowboat, "Travel"))
                {
                    return ExecutionResult.FAILED;
                }

            }

            // This is the same Fossil_Rowboat behavior used by Rs2Walker: the Travel click
            // opens a numbered dialogue; the transport row's display text supplies the key.
            // Require the destination option to remain visible briefly before selecting it.
            // Every origin/destination samples the same 300-500ms option settling delay.
            // North -> Sea retains its shorter menu-appearance timeout because crabs
            // can start combat beside this boat and close/interrupt the option menu.
            if (edge.label == null || edge.label.isBlank())
            {
                return ExecutionResult.FAILED;
            }
            boolean northToSea = nearPoint(edge.origin, FOSSIL_NORTH, 1)
                && nearPoint(edge.destination, FOSSIL_SEA, 1)
                && edge.label.charAt(0) == '3';
            long menuWait = northToSea ? FOSSIL_FAST_ROWBOAT_MENU_WAIT_MS : 5_000L;
            if (!FossilRowboatMenu.awaitOption(destinationOption, menuWait))
            {
                debug("Fossil rowboat menu not ready for destination: {}", destinationOption);
                return ExecutionResult.FAILED;
            }
            Character optionKey = Microbot.getClientThread().runOnClientThreadOptional(() -> {
                var options = Rs2Dialogue.getDialogueOptions();
                for (int i = 0; i < options.size(); i++)
                {
                    var option = options.get(i);
                    if (option.getText() == null || !option.getText().toLowerCase(java.util.Locale.ROOT)
                        .contains(destinationOption.toLowerCase(java.util.Locale.ROOT))) continue;
                    Object[] listener = option.getOnKeyListener();
                    if (listener != null && listener.length > 7 && listener[7] != null
                        && !listener[7].toString().isEmpty()) return listener[7].toString().charAt(0);
                    return i < 9 ? (char) ('1' + i) : null;
                }
                return (Character) null;
            }).orElse(null);
            if (optionKey == null)
            {
                debug("Fossil rowboat destination option unavailable: {}", destinationOption);
                return ExecutionResult.FAILED;
            }
            Rs2Keyboard.keyPress(optionKey);
            debug("Fossil rowboat selected destination option: {}", destinationOption);

            boolean landed = waitUntil(
                () -> near(playerLocation(), edge.destination, LANDING_RADIUS),
                FOSSIL_LANDING_WAIT_MS);
            return landed ? ExecutionResult.SUCCESS : ExecutionResult.FAILED;
        }
        catch (RuntimeException ex)
        {
            debug("Fossil rowboat execution exception {}: {}", edge.label, ex.getMessage());
            return ExecutionResult.FAILED;
        }
    }

    private ExecutionResult executeFossilHole(NetworkEdge edge)
    {
        try
        {
            TileObject hole = nearestObjectById(edge.npcId, edge.origin, 10);
            if (hole == null)
            {
                hole = nearestObjectById(edge.npcId == 31481 ? 31482 : 31481, edge.origin, 10);
            }
            if (hole == null)
            {
                hole = SceneObjects.<TileObject>getAll(object -> {
                    if (object == null) return false;
                    ObjectComposition definition = SceneObjects.convertToObjectComposition(object);
                    return definition != null
                        && "Hole".equalsIgnoreCase(definition.getName())
                        && SceneObjects.hasAction(definition, "Climb through", false);
                }, edge.origin, 10).stream()
                    .min(Comparator.comparingInt(object ->
                        object.getWorldLocation().distanceTo2D(edge.origin)))
                    .orElse(null);
            }
            if (hole == null)
            {
                log.info("[EfficientWalker] Fossil Hole unavailable near {}", edge.origin);
                return ExecutionResult.FAILED;
            }
            log.info("[EfficientWalker] Fossil Hole id={} at {} -> {}",
                hole.getId(), hole.getWorldLocation(), edge.destination);
            if (!SceneObjects.interact(hole, "Climb through"))
            {
                return ExecutionResult.FAILED;
            }
            return waitUntil(() -> near(playerLocation(), edge.destination, 6), 10_000L)
                ? ExecutionResult.SUCCESS : ExecutionResult.FAILED;
        }
        catch (RuntimeException ex)
        {
            debug("Fossil Hole execution exception: {}", ex.getMessage());
            return ExecutionResult.FAILED;
        }
    }

    /** Obelisks send the player to another obelisk in the network; replan from the observed landing. */
    private ExecutionResult executeWildernessObelisk(NetworkEdge edge)
    {
        try
        {
            // Important ordering: activate first while we are still beside the platform, then
            // deliberately wait before issuing the one movement click onto the catalogue's
            // interior/center tile. Clicking the center immediately was racing the activation.
            TileObject obelisk = nearestObjectById(edge.npcId, edge.origin, 6);
            if (obelisk == null || !SceneObjects.interact(obelisk, "Activate"))
            {
                return ExecutionResult.FAILED;
            }

            try
            {
                Thread.sleep(1_800L + (long) (Math.random() * 201L));
            }
            catch (InterruptedException interrupted)
            {
                Thread.currentThread().interrupt();
                return ExecutionResult.FAILED;
            }

            // Exactly one center/interior click after the activation delay.
            net.runelite.client.plugins.microbot.droslayer.walker.support.Rs2Walker.walkFastCanvas(edge.origin);

            // Keep the original 25-second landing-confirmation window. This is only the
            // post-center-click failsafe; there is no 30-second pre-center hold/delay.
            List<WorldPoint> destinations = wildernessObeliskDestinations();
            boolean landed = waitUntil(() -> {
                WorldPoint now = playerLocation();
                return now != null && !near(now, edge.origin, 10)
                    && destinations.stream().anyMatch(point -> near(now, point, 5));
            }, 25_000L);
            return landed ? ExecutionResult.SUCCESS : ExecutionResult.FAILED;
        }
        catch (RuntimeException ex)
        {
            debug("wilderness obelisk execution exception: {}", ex.getMessage());
            return ExecutionResult.FAILED;
        }
    }

    private List<WorldPoint> wildernessObeliskDestinations()
    {
        ArrayList<WorldPoint> points = new ArrayList<>();
        try (InputStream input = net.runelite.client.plugins.microbot.droslayer.walker.support.TransportResources.openResource("wilderness_obelisks.tsv"))
        {
            if (input == null) return points;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8)))
            {
                String line;
                while ((line = reader.readLine()) != null)
                {
                    if (line.isBlank() || line.startsWith("#")) continue;
                    String[] fields = line.split("\\t", -1);
                    if (fields.length >= 7 && fields[0].isBlank() && !fields[1].isBlank())
                    {
                        WorldPoint point = parsePoint(fields[1]);
                        if (point != null) points.add(point);
                    }
                }
            }
        }
        catch (IOException | RuntimeException ex)
        {
            debug("could not load wilderness obelisk destinations: {}", ex.getMessage());
        }
        return points;
    }

    private TileObject nearestObjectById(int objectId, WorldPoint origin, int radius)
    {
        if (objectId <= 0 || origin == null)
        {
            return null;
        }
        return SceneObjects.<TileObject>getAll(
                object -> object != null && object.getId() == objectId,
                origin, radius).stream()
            .min(Comparator.comparingInt(object ->
                object.getWorldLocation().distanceTo2D(origin)))
            .orElse(null);
    }

    private ExecutionResult executePrimio(NetworkEdge edge)
    {
        try
        {
            Rs2NpcModel npc = edge.npcId > 0 ? Rs2Npc.getNpc(edge.npcId) : null;
            if (npc == null && edge.npcName != null)
            {
                npc = Rs2Npc.getNpc(edge.npcName);
            }
            if (npc == null || !Rs2Npc.interact(npc, "Travel"))
            {
                return ExecutionResult.FAILED;
            }
            return waitUntil(() -> near(playerLocation(), edge.destination, LANDING_RADIUS), 15_000L)
                ? ExecutionResult.SUCCESS : ExecutionResult.FAILED;
        }
        catch (RuntimeException ex)
        {
            debug("Primio execution exception {}: {}", edge.label, ex.getMessage());
            return ExecutionResult.FAILED;
        }
    }

    private ExecutionResult executeQuetzal(NetworkEdge edge)
    {
        try
        {
            if (near(playerLocation(), edge.destination, LANDING_RADIUS))
            {
                return ExecutionResult.SUCCESS;
            }

            if (!isQuetzalMapVisible())
            {
                Rs2NpcModel renu = Rs2Npc.getNpc("Renu");
                if (renu == null || !Rs2Npc.interact(renu, "Travel"))
                {
                    return ExecutionResult.FAILED;
                }
                if (!waitUntil(this::isQuetzalMapVisible, QUETZAL_MAP_VISIBLE_WAIT_MS))
                {
                    return ExecutionResult.FAILED;
                }
            }
            Widget destinationWidget = findQuetzalDestinationWidget(edge.label);
            if (destinationWidget == null || !Rs2Widget.clickWidget(destinationWidget))
            {
                return ExecutionResult.MISSING_DESTINATION;
            }
            return waitUntil(() -> near(playerLocation(), edge.destination, LANDING_RADIUS), 12_000L)
                ? ExecutionResult.SUCCESS : ExecutionResult.FAILED;
        }
        catch (RuntimeException ex)
        {
            debug("Quetzal execution exception {}: {}", edge.label, ex.getMessage());
            return ExecutionResult.FAILED;
        }
    }

    private boolean isQuetzalMapVisible()
    {
        return Rs2Widget.isWidgetVisible(InterfaceID.QuetzalMenu.UNIVERSE)
            || Rs2Widget.isWidgetVisible(InterfaceID.QuetzalMenu.MAP)
            || Rs2Widget.isWidgetVisible(InterfaceID.QuetzalMenu.ICONS)
            || Rs2Widget.isWidgetVisible(InterfaceID.QuetzalMenu.CONTENTS)
            || Rs2Widget.isWidgetVisible(InterfaceID.QuetzalwhistleMenu.UNIVERSE)
            || Rs2Widget.isWidgetVisible(InterfaceID.QuetzalwhistleMenu.MAP)
            || Rs2Widget.isWidgetVisible(InterfaceID.QuetzalwhistleMenu.ICONS)
            || Rs2Widget.isWidgetVisible(InterfaceID.QuetzalwhistleMenu.CONTENTS);
    }

    private Widget findQuetzalDestinationWidget(String label)
    {
        if (label == null || label.isBlank())
        {
            return null;
        }
        int[] roots = {
            InterfaceID.QuetzalMenu.ICONS, InterfaceID.QuetzalMenu.MAP,
            InterfaceID.QuetzalMenu.SCROLL, InterfaceID.QuetzalMenu.CONTENTS,
            InterfaceID.QuetzalMenu.UNIVERSE, InterfaceID.QuetzalwhistleMenu.ICONS,
            InterfaceID.QuetzalwhistleMenu.MAP, InterfaceID.QuetzalwhistleMenu.SCROLL,
            InterfaceID.QuetzalwhistleMenu.CONTENTS, InterfaceID.QuetzalwhistleMenu.UNIVERSE
        };

        long deadline = System.currentTimeMillis() + QUETZAL_ICON_READY_WAIT_MS;
        do
        {
            for (int rootId : roots)
            {
                if (Rs2Widget.isHidden(rootId))
                {
                    continue;
                }
                Widget root = Rs2Widget.getWidget(rootId);
                if (root == null)
                {
                    continue;
                }
                Widget found = Rs2Widget.findWidget(label, List.of(root), false);
                if (menuWidgetReady(found))
                {
                    return found;
                }
            }
            try
            {
                Thread.sleep(120L);
            }
            catch (InterruptedException ex)
            {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        while (System.currentTimeMillis() < deadline);
        return null;
    }


    private int interactionRadiusFor(NetworkEdge edge)
    {
        if (edge == null)
        {
            return CHARTER_ORIGIN_RADIUS;
        }
        switch (edge.type)
        {
            case MINIGAME:
                return MINIGAME_ORIGIN_RADIUS;
            case ELKOY:
                return ELKOY_INTERACT_RADIUS;
            case MUSHTREE:
                return 8;
            case FOSSIL_BARGE:
                return 10;
            case FOSSIL_HOLE:
                return 2;
            case WILDERNESS_OBELISK:
                return 4;
            case FOSSIL_ROWBOAT:
                // For the north-coast boat to the bank island, get essentially onto the
                // canonical rowboat approach tile before clicking Travel.  This minimizes the
                // time between the object click and immediate option-3 input while ammonite
                // crabs are nearby. Other Fossil rowboats keep the existing radius.
                if (nearPoint(edge.origin, FOSSIL_NORTH, 1)
                    && nearPoint(edge.destination, FOSSIL_SEA, 1))
                {
                    return 1;
                }
                return 8;
            default:
                return CHARTER_ORIGIN_RADIUS;
        }
    }

    private static boolean requiresRouteHandoff(NetworkEdge edge)
    {
        return edge != null
            && (edge.type == EdgeType.CHARTER || edge.type == EdgeType.CHARTER_EXIT
                || edge.type == EdgeType.ELKOY
                || edge.type == EdgeType.MUSHTREE
                || edge.type == EdgeType.FOSSIL_BARGE
                || edge.type == EdgeType.FOSSIL_ROWBOAT
                || edge.type == EdgeType.FOSSIL_HOLE
                || edge.type == EdgeType.WILDERNESS_OBELISK);
    }

    private boolean isLegacyElkoyTransport(Object transport)
    {
        if (transport == null)
        {
            return false;
        }
        String action = transportString(transport, transportActionField);
        String target = transportString(transport, transportTargetField);
        int objectId = transportInt(transport, transportObjectIdField);
        return "Follow".equalsIgnoreCase(action)
            && ("Elkoy".equalsIgnoreCase(target) || objectId == ELKOY_NPC_ID);
    }

    private WorldPoint transportWorldPoint(Object transport, Field field)
    {
        try
        {
            return field == null || transport == null ? null : (WorldPoint) field.get(transport);
        }
        catch (IllegalAccessException | ClassCastException ex)
        {
            return null;
        }
    }

    private String transportString(Object transport, Field field)
    {
        try
        {
            Object value = field == null || transport == null ? null : field.get(transport);
            return value == null ? "" : value.toString();
        }
        catch (IllegalAccessException ex)
        {
            return "";
        }
    }

    private int transportInt(Object transport, Field field)
    {
        try
        {
            return field == null || transport == null ? -1 : field.getInt(transport);
        }
        catch (IllegalAccessException ex)
        {
            return -1;
        }
    }

    private boolean isRubberCapTransport(Object transport)
    {
        if (transport == null)
        {
            return false;
        }
        return isRubberCapTransport(
            transportInt(transport, transportObjectIdField),
            transportWorldPoint(transport, transportOriginField),
            transportWorldPoint(transport, transportDestinationField));
    }

    private static boolean isRubberCapTransport(int objectId, WorldPoint origin, WorldPoint destination)
    {
        if (objectId == RUBBER_CAP_MUSHROOM_ID)
        {
            return true;
        }
        if (!RUBBER_CAP_DESTINATION.equals(destination) || origin == null)
        {
            return false;
        }
        for (WorldPoint candidate : RUBBER_CAP_ORIGINS)
        {
            if (candidate.equals(origin))
            {
                return true;
            }
        }
        return false;
    }

    private static boolean nearPoint(WorldPoint a, WorldPoint b, int radius)
    {
        return a != null && b != null
            && a.getPlane() == b.getPlane()
            && a.distanceTo2D(b) <= radius;
    }

    private static boolean isFossilLocalRowboatPoint(WorldPoint point)
    {
        return nearPoint(point, FOSSIL_CAMP, 3)
            || nearPoint(point, FOSSIL_NORTH, 3)
            || nearPoint(point, FOSSIL_SEA, 3);
    }

    private static String edgeTypeName(EdgeType type)
    {
        switch (type)
        {
            case MINIGAME:
                return "minigame";
            case WILDERNESS_OBELISK:
                return "wilderness obelisk";
            case CHARTER:
                return "charter";
            case CHARTER_EXIT:
                return "charter disembark";
            case QUETZAL:
                return "quetzal";
            case PRIMIO:
                return "Primio";
            case MINECART:
                return "minecart";
            case ELKOY:
                return "Elkoy";
            case MUSHTREE:
                return "Magic Mushtree";
            case FOSSIL_BARGE:
                return "Fossil barge";
            case FOSSIL_ROWBOAT:
                return "Fossil rowboat";
            case FOSSIL_HOLE:
                return "Fossil Hole";
            default:
                return "native";
        }
    }

    private ExecutionResult executeCharter(NetworkEdge edge)
    {
        try
        {
            CharterRow liveRow = charterRows.stream().filter(row -> row.origin.equals(edge.origin)
                && row.destination.equals(edge.destination)).findFirst().orElse(null);
            if (liveRow == null || !Microbot.getClientThread().invoke((java.util.function.Supplier<Boolean>) () -> runtimeCharterRequirementsPass(liveRow)))
                return ExecutionResult.FAILED;
            int liveFare = Microbot.getClientThread().invoke(() -> CharterFare.quote(liveRow.origin,
                liveRow.destination, liveRow.fare, CharterFare.cabinFever(), CharterFare.charos()));
            if (Rs2Inventory.itemQuantity(COINS) < liveFare)
            {
                return ExecutionResult.INSUFFICIENT_COINS;
            }

            // Reuse an open charter menu. Re-clicking the NPC can reset entries while they load.
            if (!Microbot.getClientThread().runOnClientThreadOptional(() -> CharterMenu.open(client)).orElse(false))
            {
                Rs2NpcModel npc = Microbot.getClientThread().invoke(() -> {
                    var match = Microbot.getRs2NpcCache().query().withName(edge.npcName).within(edge.origin, 10)
                        .toList().stream().min(Comparator.comparingInt(candidate ->
                            playerLocation().distanceTo(candidate.getWorldLocation()))).orElse(null);
                    return match == null ? null : new Rs2NpcModel(match.getNpc());
                });
                if (npc == null || !Microbot.getClientThread().invoke((java.util.function.Supplier<Boolean>)
                    () -> Rs2Npc.canWalkTo(npc, 20)) || !Rs2Npc.interact(npc, "Charter"))
                    return ExecutionResult.FAILED;
            }

            // Wait for the actual destination, not just the interface shell. Its children can
            // appear later than the root; that delay must not blacklist a valid charter.
            if (!waitUntil(() -> findCharterSelection(edge.label) != null, 6_000L))
            {
                if (Thread.currentThread().isInterrupted()) return ExecutionResult.FAILED;
                return Microbot.getClientThread().runOnClientThreadOptional(() ->
                    CharterMenu.missing(client, edge.label)).orElse(false)
                    ? ExecutionResult.MISSING_DESTINATION : ExecutionResult.CHARTER_MENU_UNAVAILABLE;
            }
            CharterMenu.Selection selection = findCharterSelection(edge.label); // Refresh before clicking.
            if (selection == null || Thread.currentThread().isInterrupted())
            {
                return ExecutionResult.CHARTER_MENU_UNAVAILABLE;
            }
            invokeCharterSelection(selection);

            if (waitUntil(Rs2Dialogue::hasSelectAnOption, 2_000L))
            {
                confirmCharterFare();
            }

            if (!waitUntil(() -> near(playerLocation(), edge.destination, LANDING_RADIUS)
                || CharterTransportCatalog.onDeck(playerLocation(), edge.destination), 22_000L))
            {
                debug("charter landing not recognized destination={} player={}", edge.destination, playerLocation());
                return ExecutionResult.FAILED;
            }
            if (CharterTransportCatalog.onDeck(playerLocation(), edge.destination))
                return disembark(edge.destination);
            return ExecutionResult.SUCCESS;
        }
        catch (RuntimeException ex)
        {
            debug("charter execution exception {}: {}", edge.label, ex.getMessage());
            return ExecutionResult.FAILED;
        }
    }

    /** The fare prompt offers "Yes." and "Yes, and don't ask again."; the first confirmation also silences later prompts. */
    private static boolean confirmCharterFare()
    {
        return Rs2Dialogue.clickOption(false, "ask again") || Rs2Dialogue.clickOption(true, "Yes", "Yes.");
    }

    private ExecutionResult disembark(WorldPoint dock)
    {
        if (near(playerLocation(), dock, LANDING_RADIUS)) return ExecutionResult.SUCCESS;
        if (!CharterTransportCatalog.onDeck(playerLocation(), dock)) return ExecutionResult.FAILED;
        if (!waitUntil(() -> findGangplank(dock) != null, 4_000L)) {
            debug("charter disembark missing gangplank dock={} player={}", dock, playerLocation());
            return ExecutionResult.FAILED;
        }
        for (int attempt = 1; attempt <= 3; attempt++)
        {
            if (near(playerLocation(), dock, LANDING_RADIUS)) return ExecutionResult.SUCCESS;
            if (!CharterTransportCatalog.onDeck(playerLocation(), dock)) return ExecutionResult.FAILED;
            // Movement means the previous click was accepted; do not cancel it with another.
            if (Rs2Player.isMoving())
            {
                if (waitUntil(() -> near(playerLocation(), dock, LANDING_RADIUS), 8_000L))
                    return ExecutionResult.SUCCESS;
                if (Rs2Player.isMoving()) return ExecutionResult.FAILED;
            }
            Rs2TileObjectModel plank = findGangplank(dock); // Must re-query after each scene update.
            if (plank == null) return ExecutionResult.FAILED;
            String action = Microbot.getClientThread().invoke(() ->
                SceneObjects.hasAction(plank, "Disembark") ? "Disembark" : "Cross");
            boolean clicked = net.runelite.client.plugins.microbot.droslayer.walker.support.TransportObjectClick.click(plank, action);
            debug("gangplank attempt={} dock={} clicked={} player={}", attempt, dock, clicked, playerLocation());
            if (attempt == 1)
            {
                // Retry a missed interaction 250-500 ms later, but never click after arrival.
                long jitter = java.util.concurrent.ThreadLocalRandom.current().nextLong(250L, 501L);
                if (waitUntil(() -> near(playerLocation(), dock, LANDING_RADIUS), jitter)) return ExecutionResult.SUCCESS;
            }
            else
            {
                // After the second attempt, require >=3 seconds of continuous idle before
                // the third one. Clicking over an ongoing crossing can cancel the action.
                final long[] lastMoving = {System.currentTimeMillis()};
                final boolean waitForIdle = attempt == 2;
                long grace = waitForIdle ? 9_000L : 8_000L;
                if (waitUntil(() -> near(playerLocation(), dock, LANDING_RADIUS)
                    || (waitForIdle && gangplankIdleFor(lastMoving, 3_000L)), grace))
                {
                    if (near(playerLocation(), dock, LANDING_RADIUS)) return ExecutionResult.SUCCESS;
                }
                if (attempt == 2 && !gangplankIdleFor(lastMoving, 3_000L))
                    return ExecutionResult.FAILED; // Never issue third click while still moving.
            }
        }
        return near(playerLocation(), dock, LANDING_RADIUS)
            ? ExecutionResult.SUCCESS : ExecutionResult.FAILED;
    }

    private static boolean gangplankIdleFor(long[] lastMoving, long idleMs)
    {
        if (Rs2Player.isMoving()) lastMoving[0] = System.currentTimeMillis();
        return System.currentTimeMillis() - lastMoving[0] >= idleMs;
    }

    private Rs2TileObjectModel findGangplank(WorldPoint dock)
    {
        return Microbot.getClientThread().invoke(() -> {
            WorldPoint player = playerLocation();
            if (!CharterTransportCatalog.onDeck(player, dock)) return null;
            // Gangplanks can be wall/decorative objects, not just GameObject instances.
            return Microbot.getRs2TileObjectCache().query().withName("Gangplank").within(player, 8)
                .where(object -> object.getWorldLocation().distanceTo2D(dock) <= 8
                    && (SceneObjects.hasAction(object, "Cross") || SceneObjects.hasAction(object, "Disembark")))
                .toList().stream().min(Comparator.comparingInt(object -> player.distanceTo(object.getWorldLocation())))
                .orElse(null);
        });
    }

    private int charterUnlockFingerprint()
    {
        int hash = Objects.hash(CharterFare.cabinFever(), CharterFare.charos(),
            Rs2Player.getRealSkillLevel(Skill.SAILING));
        for (int id : new int[]{18152,19566}) hash = 31 * hash + Microbot.getVarbitValue(id);
        for (Quest quest : new Quest[]{Quest.PANDEMONIUM, Quest.THE_RED_REEF})
            hash = 31 * hash + Objects.hashCode(Rs2Player.getQuestState(quest));
        return hash;
    }

    private CharterMenu.Selection findCharterSelection(String destinationText)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
            CharterMenu.selection(client, destinationText)).orElse(null);
    }

    private void invokeCharterSelection(CharterMenu.Selection selection)
    {
        NewMenuEntry menu = new NewMenuEntry()
            .option(selection.option)
            .target("")
            .identifier(selection.actionIndex)
            .type(MenuAction.CC_OP)
            .param0(selection.index)
            .param1(selection.widgetId)
            .forceLeftClick(false);
        Microbot.doInvoke(menu, selection.bounds);
    }

    private void failEdge(NetworkEdge edge, ExecutionResult result, String reason)
    {
        clearCompleteRoute();
        WorldPoint target = engineDestination();
        boolean retrySwampBank = edge.type == EdgeType.MUSHTREE
            && StickySwampExit.origin(edge.origin) && StickySwampExit.bankTarget(target);
        debug("{} failed result={} edge={} -> {} ({}) target={}",
            reason, result, edge.origin, edge.destination, edge.label, target);

        if (edge.type == EdgeType.CHARTER)
        {
            String key = charterKey(edge.origin, edge.destination);
            if (result == ExecutionResult.CHARTER_MENU_UNAVAILABLE)
            {
                // A broken/loading menu affects this dock, not every destination's unlock.
                // Avoid cycling through other charters here; allow another route for this request.
                suppressChartersFromOrigin(edge.origin);
            }
            else if (result == ExecutionResult.MISSING_DESTINATION)
            {
                sessionCharterBlacklist.add(key);
                log.info("[EfficientWalker] charter destination '{}' is unavailable on this account/session; blacklisting {} -> {}",
                    edge.label, edge.origin, edge.destination);
            }
            else
            {
                requestCharterBlacklist.add(key);
            }
        }
        else if (edge.type == EdgeType.MINIGAME)
        {
            // Rejected channels can leave the advertised unlock varbit set (Mage Training
            // Arena). Keep the failed destination excluded across nearby-target retries.
            if (result == ExecutionResult.MISSING_DESTINATION || result == ExecutionResult.FAILED)
            {
                sessionMinigameBlacklist.add(edge.label.toLowerCase(Locale.ROOT));
                log.info("[EfficientWalker] minigame teleport '{}' is unavailable on this account/session; blacklisting it",
                    edge.label);
            }
            // A real cooldown already removes every minigame edge from the rebuilt graph. Do not
            // permanently suppress this target for the whole session; the route should become
            // eligible again naturally when the 20-minute timer expires.
            if (target != null && result != ExecutionResult.COOLDOWN)
            {
                minigamesSuppressedForTarget = target;
            }
        }
        else if (edge.type == EdgeType.CHARTER_EXIT || edge.type == EdgeType.QUETZAL || edge.type == EdgeType.PRIMIO
            || edge.type == EdgeType.MINECART || edge.type == EdgeType.ELKOY
            || edge.type == EdgeType.MUSHTREE || edge.type == EdgeType.FOSSIL_BARGE
            || edge.type == EdgeType.FOSSIL_ROWBOAT
            || edge.type == EdgeType.FOSSIL_HOLE)
        {
            String key = networkKey(edge.type, edge.origin, edge.destination);
            // A missing Mycelium destination is an account unlock fact, not a transient click
            // failure. Remember it for the session so the planner immediately falls back to the
            // Digsite/Fossil route instead of reopening the same unavailable mushroom destination.
            if (edge.type == EdgeType.MUSHTREE && result == ExecutionResult.MISSING_DESTINATION)
            {
                // Mycelium unlocks are destination-wide, not origin-specific. If "Mushroom
                // Meadow" is absent from the menu at one tree it will be absent from every tree;
                // blacklist every edge into that landing so the immediate replan can choose the
                // Digsite barge / ordinary walking fallback instead of testing the same lock from
                // another mushtree.
                for (MushtreeStop origin : MUSHTREE_STOPS)
                {
                    for (WorldPoint routeOrigin : origin.routeOrigins)
                    {
                        sessionNetworkBlacklist.add(networkKey(
                            EdgeType.MUSHTREE, routeOrigin, edge.destination));
                    }
                }
            }
            else
            {
                requestNetworkBlacklist.add(key);
            }
        }

        cancelFuture();
        resetExecution(false);
        installedKey = null;
        if (target != null)
        {
            try
            {
                rebuildGraph(target);
            }
            catch (ReflectiveOperationException ex)
            {
                log.warn("[EfficientWalker] could not rebuild graph after a failed extra transport", ex);
            }
            replanTarget = target;
            stickySwampBankRetry = retrySwampBank;
        }
    }

    private void suppressChartersFromOrigin(WorldPoint origin)
    {
        for (CharterRow row : charterRows)
            if (origin.equals(row.origin)) requestCharterBlacklist.add(charterKey(row.origin, row.destination));
    }

    private void maybeAdoptExternalGraph() throws ReflectiveOperationException
    {
        @SuppressWarnings("unchecked")
        List<Object> current = (List<Object>) transportListField.get(engine);
        if (current == installedMerged)
        {
            return;
        }

        ArrayList<Object> clean = new ArrayList<>();
        for (Object transport : current)
        {
            if (!injectedEdges.containsKey(transport) && !sharedCatalogEdges.containsKey(transport))
            {
                clean.add(transport);
            }
        }
        baseTransports = Collections.unmodifiableList(clean);
        installedMerged = null;
        injectedEdges.clear();
        installedKey = null;
    }

    private void adoptCurrentGraphAsBase() throws ReflectiveOperationException
    {
        @SuppressWarnings("unchecked")
        List<Object> current = (List<Object>) transportListField.get(engine);
        baseTransports = Collections.unmodifiableList(new ArrayList<>(current));
        installedMerged = null;
        installedKey = null;
    }

    private NetworkEdge activeInjectedEdge()
    {
        Object active = activeTransportFieldValue();
        if (active == null)
        {
            return null;
        }
        NetworkEdge injected = injectedEdges.get(active);
        if (injected != null)
        {
            return injected;
        }
        injected = rebuiltInjectedEdge(active);
        if (injected != null)
        {
            return injected;
        }

        // v8 exposed the real problem with Elkoy: the graph already contains a
        // Follow;Elkoy row (NPC id 4968), and the planner can select that identity instead of
        // our synthetic id=-1 row. Normalize any such legacy row onto our hard-coded NPC
        // executor so the generic object transition path can never win the race.
        if (isLegacyElkoyTransport(active))
        {
            WorldPoint origin = transportWorldPoint(active, transportOriginField);
            WorldPoint destination = transportWorldPoint(active, transportDestinationField);
            boolean outbound = origin != null && origin.getY() >= 3170;
            return outbound
                ? new NetworkEdge(EdgeType.ELKOY, ELKOY_VILLAGE_SIDE, ELKOY_SOUTH_SIDE,
                    "Tree Gnome Village maze exit", "Elkoy", ELKOY_NPC_ID, 0)
                : new NetworkEdge(EdgeType.ELKOY, ELKOY_SOUTH_SIDE, ELKOY_VILLAGE_SIDE,
                    "Tree Gnome Village", "Elkoy", ELKOY_NPC_ID, 0);
        }
        return null;
    }

    /**
     * A committed preview keeps the synthetic id=-1 row from the graph it was planned on. A later
     * rebuild (e.g. a failed complete-route refresh) replaces injectedEdges, orphaning that row so
     * the generic object executor claimed the Barge guard and failed objectFound=false. Re-associate
     * the stale row with its rebuilt identity (same origin, destination, action and target).
     */
    private NetworkEdge rebuiltInjectedEdge(Object active)
    {
        if (active == staleActiveTransport)
        {
            return staleActiveEdge;
        }
        staleActiveTransport = active;
        staleActiveEdge = null;
        if (transportInt(active, transportObjectIdField) != -1)
        {
            return null;
        }
        WorldPoint origin = transportWorldPoint(active, transportOriginField);
        WorldPoint destination = transportWorldPoint(active, transportDestinationField);
        String action = transportString(active, transportActionField);
        String target = transportString(active, transportTargetField);
        for (Map.Entry<Object, NetworkEdge> entry : injectedEdges.entrySet())
        {
            Object candidate = entry.getKey();
            if (Objects.equals(origin, transportWorldPoint(candidate, transportOriginField))
                && Objects.equals(destination, transportWorldPoint(candidate, transportDestinationField))
                && action.equals(transportString(candidate, transportActionField))
                && target.equals(transportString(candidate, transportTargetField)))
            {
                staleActiveEdge = entry.getValue();
                debug("re-associated stale {} edge {} -> {} after graph rebuild",
                    edgeTypeName(staleActiveEdge.type), origin, destination);
                break;
            }
        }
        return staleActiveEdge;
    }

    private Object activeTransportFieldValue()
    {
        try
        {
            return activeTransportField == null ? null : activeTransportField.get(engine);
        }
        catch (IllegalAccessException ex)
        {
            return null;
        }
    }

    private WorldPoint engineDestination()
    {
        try
        {
            Method method = engineClass.getDeclaredMethod("getDestination");
            method.setAccessible(true);
            return (WorldPoint) method.invoke(engine);
        }
        catch (ReflectiveOperationException ex)
        {
            return null;
        }
    }

    private ExecutionResult consumeFutureResult()
    {
        Future<ExecutionResult> future = actionFuture;
        actionFuture = null;
        if (future == null)
        {
            return ExecutionResult.FAILED;
        }
        try
        {
            return future.get();
        }
        catch (Exception ex)
        {
            return ExecutionResult.FAILED;
        }
    }

    private void cancelFuture()
    {
        if (actionFuture != null)
        {
            actionFuture.cancel(true);
            actionFuture = null;
        }
    }

    private void resetExecution(boolean clearReplan)
    {
        currentTransport = null;
        currentEdge = null;
        if (actionFuture == null || actionFuture.isDone()) currentSharedTransport = null;
        actionStartedAt = 0L;
        if (clearReplan)
        {
            replanTarget = null;
        }
    }

    private String contextKey(WorldPoint target)
    {
        WorldPoint start = playerLocation();
        boolean members = client.getWorldType().contains(WorldType.MEMBERS);
        return String.valueOf(target) + "|start=" + routeContextBucket(start)
            + "|caveLight=" + caveLightAvailable
            + "|swampExit=" + stickySwampBankJourney
            + "|maxPoh=" + MaxPohSupport.fingerprint()
            + "|charter=" + config.useCharterShips()
            + "|mini=" + config.useMinigameTeleports()
            + "|tele=" + config.useTeleportations()
            + "|allowWilderness=" + config.allowWildernessTravel()
            + "|wildLevers=" + config.useWildernessLevers()
            + "|canoes=" + config.useCanoes()
            + "|wildObelisks=" + config.useWildernessObelisks() + ":" + wildernessObeliskHops
            + "|wildItems=" + config.useWildernessTeleports()
            + "|ferox=" + config.useFeroxTeleport()
            + "|cooldown=" + minigameCooldownReady()
            + "|carried=" + TravelEquipment.carriedFingerprint()
            + "|wildernessMedium=" + Microbot.getVarbitValue(4467)
            + "|coins=" + Rs2Inventory.itemQuantity(COINS)
            + "|greatConch=" + GreatConchTransports.unlocked(Rs2Player.getQuestState(Quest.TROUBLED_TORTUGANS))
            + "|charterUnlocks=" + charterUnlockFingerprint()
            + "|minecartFree=" + kourendMinecartsFree()
            + "|members=" + members
            + "|miniSuppressed=" + Objects.equals(minigamesSuppressedForTarget, target)
            + "|sessionCharterBlocks=" + sessionCharterBlacklist.hashCode()
            + "|requestCharterBlocks=" + requestCharterBlacklist.hashCode()
            + "|sessionMinigameBlocks=" + sessionMinigameBlacklist.hashCode()
            + "|sessionNetworkBlocks=" + sessionNetworkBlacklist.hashCode()
            + "|requestNetworkBlocks=" + requestNetworkBlacklist.hashCode();
    }

    private static String routeContextBucket(WorldPoint point)
    {
        if (point == null)
        {
            return "null";
        }
        // Rebuild at most once per 8x8 movement bucket instead of once per walked tile.
        return (point.getX() >> 3) + ":" + (point.getY() >> 3) + ":" + point.getPlane();
    }

    private List<MinecartStop> readKourendMinecartStops() throws IOException
    {
        InputStream stream = net.runelite.client.plugins.microbot.droslayer.walker.support.TransportResources.openResource("minecarts.tsv");
        if (stream == null)
        {
            throw new IOException("Dro shortestpath/minecarts.tsv not found on classpath");
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)))
        {
            return parseKourendMinecartStops(reader);
        }
    }

    static List<MinecartStop> parseKourendMinecartStops(BufferedReader reader) throws IOException
    {
        LinkedHashMap<WorldPoint, MinecartStop> stops = new LinkedHashMap<>();
        String line;
        while ((line = reader.readLine()) != null)
        {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] fields = line.split("\\t", -1);
            // Current schema: origin, destination, interaction, skills, fare, quests,
            // varbits, duration, display label. Fare belongs to origin rows, not stops.
            if (fields.length < 9 || !fields[0].isBlank()
                || !("7796<11".equals(fields[6].trim()) || "7796=11".equals(fields[6].trim()))) continue;
            WorldPoint destination = parsePoint(fields[1]);
            String label = fields[8].trim();
            if (destination == null || !label.matches("[1-9ABC]: .+")) continue;
            // Paid and free sections describe the same destinations. Execution selects
            // the current fare independently from the account's quest state.
            stops.putIfAbsent(destination, new MinecartStop(destination, label));
        }
        return List.copyOf(stops.values());
    }

    private List<CharterRow> readCharterRows() throws IOException
    {
        return Collections.unmodifiableList(readCharterRows("charter_ships.tsv"));
    }

    private List<CharterRow> readCharterRows(String resource) throws IOException
    {
        InputStream stream = net.runelite.client.plugins.microbot.droslayer.walker.support.TransportResources.openResource(resource);
        if (stream == null)
        {
            throw new IOException("Charter resource not found on classpath: " + resource);
        }

        List<CharterRow> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                if (line.isBlank() || line.startsWith("#"))
                {
                    continue;
                }
                String[] fields = line.split("\\t", -1);
                if (fields.length < 8)
                {
                    continue;
                }
                try
                {
                    WorldPoint origin = parsePoint(fields[0]);
                    WorldPoint destination = parsePoint(fields[1]);
                    String[] menu = fields[2].split(";", -1);
                    if (origin == null || destination == null || menu.length < 3)
                    {
                        continue;
                    }
                    String action = menu[0].trim();
                    String npcName = menu[1].trim();
                    int npcId = Integer.parseInt(menu[2].trim());
                    int fare = parseCurrency(fields[3]);
                    List<Quest> quests = parseQuests(fields[4], "charter");
                    List<NumericRequirement> varbits = parseNumericRequirements(fields[5]);
                    if (quests == null || varbits == null)
                    {
                        continue;
                    }
                    int duration = parseIntOrDefault(fields[6], 0);
                    String displayInfo = fields[7].trim();
                    // Some resource revisions include an extra trailing display column. Prefer the
                    // final nonblank field when the normal display index is empty.
                    if (displayInfo.isBlank() && fields.length > 8)
                    {
                        displayInfo = fields[8].trim();
                    }
                    if (displayInfo.isBlank())
                    {
                        displayInfo = inferCharterDestination(destination);
                    }
                    rows.add(new CharterRow(origin, destination, action, npcName, npcId, fare,
                        fields.length > 8 ? parseSkills(fields[8]) : Collections.emptyList(), quests, varbits, duration, displayInfo));
                }
                catch (RuntimeException ex)
                {
                    debug("skipped malformed charter row: {}", line);
                }
            }
        }
        return rows;
    }

    private List<MinigameRow> readMinigameRows() throws IOException
    {
        InputStream stream = net.runelite.client.plugins.microbot.droslayer.walker.support.TransportResources.openResource("teleportation_minigames.tsv");
        if (stream == null)
        {
            throw new IOException("Dro shortestpath/teleportation_minigames.tsv not found on classpath");
        }

        List<MinigameRow> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)))
        {
            String line;
            boolean headerSeen = false;
            while ((line = reader.readLine()) != null)
            {
                if (line.isBlank())
                {
                    continue;
                }
                if (!headerSeen)
                {
                    if (line.startsWith("#"))
                    {
                        continue;
                    }
                    headerSeen = true; // Destination / Skills / Quests / ...
                    continue;
                }
                if (line.startsWith("#"))
                {
                    continue;
                }

                String[] fields = line.split("\\t", -1);
                if (fields.length < 9)
                {
                    continue;
                }
                try
                {
                    WorldPoint destination = parsePoint(fields[0]);
                    if (destination == null)
                    {
                        continue;
                    }
                    List<SkillRequirement> skills = parseSkills(fields[1]);
                    List<Quest> quests = parseQuests(fields[2], "minigame");
                    if (quests == null)
                    {
                        continue;
                    }
                    boolean membersOnly = "Y".equalsIgnoreCase(fields[3].trim());
                    List<NumericRequirement> varbits = parseNumericRequirements(fields[4]);
                    ParsedVarplayers parsedVarplayers = parseVarplayers(fields[5]);
                    if (varbits == null || parsedVarplayers == null)
                    {
                        continue;
                    }
                    int wilderness = parseIntOrDefault(fields[6], 0);
                    int duration = parseIntOrDefault(fields[7], 0);
                    String displayInfo = fields[8].trim();
                    if (displayInfo.isBlank())
                    {
                        continue;
                    }
                    rows.add(new MinigameRow(destination, skills, quests, membersOnly, varbits,
                        parsedVarplayers.numeric, parsedVarplayers.cooldowns,
                        wilderness, duration, displayInfo));
                }
                catch (RuntimeException ex)
                {
                    debug("skipped malformed minigame row: {}", line);
                }
            }
        }
        return rows;
    }

    private static int parseCurrency(String text)
    {
        if (text == null || text.isBlank())
        {
            return 0;
        }
        String digits = text.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? 0 : Integer.parseInt(digits);
    }

    private static List<SkillRequirement> parseSkills(String text)
    {
        if (text == null || text.isBlank())
        {
            return Collections.emptyList();
        }
        List<SkillRequirement> result = new ArrayList<>();
        for (String token : text.split(";"))
        {
            String trimmed = token.trim();
            if (trimmed.isEmpty())
            {
                continue;
            }
            int split = trimmed.indexOf(' ');
            if (split <= 0)
            {
                throw new IllegalArgumentException("bad skill requirement: " + trimmed);
            }
            int level = Integer.parseInt(trimmed.substring(0, split));
            String skillName = trimmed.substring(split + 1).trim().toUpperCase(Locale.ROOT).replace(' ', '_');
            result.add(new SkillRequirement(Skill.valueOf(skillName), level));
        }
        return result;
    }

    private static List<Quest> parseQuests(String text, String source)
    {
        if (text == null || text.isBlank())
        {
            return Collections.emptyList();
        }
        List<Quest> result = new ArrayList<>();
        for (String token : text.split(";"))
        {
            String name = token.trim();
            if (name.isEmpty())
            {
                continue;
            }
            Quest found = null;
            for (Quest quest : Quest.values())
            {
                if (quest.getName().equalsIgnoreCase(name))
                {
                    found = quest;
                    break;
                }
            }
            if (found == null)
            {
                log.warn("[EfficientWalker] unknown {} quest requirement '{}'; rejecting that route", source, name);
                return null;
            }
            result.add(found);
        }
        return result;
    }

    private static List<NumericRequirement> parseNumericRequirements(String text)
    {
        if (text == null || text.isBlank())
        {
            return Collections.emptyList();
        }
        List<NumericRequirement> result = new ArrayList<>();
        for (String token : text.split(";"))
        {
            String trimmed = token.trim();
            if (trimmed.isEmpty())
            {
                continue;
            }
            int split = trimmed.indexOf('=');
            char comparator = '=';
            if (split < 0)
            {
                split = trimmed.indexOf('>');
                comparator = '>';
            }
            if (split <= 0 || split >= trimmed.length() - 1)
            {
                return null;
            }
            result.add(new NumericRequirement(
                Integer.parseInt(trimmed.substring(0, split).trim()),
                Integer.parseInt(trimmed.substring(split + 1).trim()),
                comparator));
        }
        return result;
    }

    private static ParsedVarplayers parseVarplayers(String text)
    {
        if (text == null || text.isBlank())
        {
            return new ParsedVarplayers(Collections.emptyList(), Collections.emptyList());
        }
        List<NumericRequirement> numeric = new ArrayList<>();
        List<CooldownRequirement> cooldowns = new ArrayList<>();
        for (String token : text.split(";"))
        {
            String trimmed = token.trim();
            if (trimmed.isEmpty())
            {
                continue;
            }
            int at = trimmed.indexOf('@');
            if (at > 0 && at < trimmed.length() - 1)
            {
                cooldowns.add(new CooldownRequirement(
                    Integer.parseInt(trimmed.substring(0, at).trim()),
                    Integer.parseInt(trimmed.substring(at + 1).trim())));
                continue;
            }
            List<NumericRequirement> one = parseNumericRequirements(trimmed);
            if (one == null)
            {
                return null;
            }
            numeric.addAll(one);
        }
        return new ParsedVarplayers(numeric, cooldowns);
    }

    private static WorldPoint parsePoint(String text)
    {
        if (text == null || text.isBlank())
        {
            return null;
        }
        String[] parts = text.trim().split("\\s+");
        if (parts.length != 3)
        {
            throw new IllegalArgumentException("bad point: " + text);
        }
        return new WorldPoint(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }

    private static int parseIntOrDefault(String text, int fallback)
    {
        return text == null || text.isBlank() ? fallback : Integer.parseInt(text.trim());
    }

    private static String inferCharterDestination(WorldPoint destination)
    {
        return destination == null ? "Charter destination" : destination.toString();
    }

    private static String normalizeWidgetText(String text)
    {
        if (text == null || text.isBlank())
        {
            return "";
        }
        return text.replaceAll("<[^>]+>", " ")
            .trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static int distance2D(WorldPoint a, WorldPoint b)
    {
        if (a == null || b == null)
        {
            return Integer.MAX_VALUE;
        }
        return Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getY() - b.getY()));
    }

    private static int approximateWalkScore(WorldPoint a, WorldPoint b)
    {
        int distance = distance2D(a, b);
        return distance == Integer.MAX_VALUE ? Integer.MAX_VALUE / 4 : (distance + 1) / 2;
    }

    private WorldPoint playerLocation()
    {
        // Player#getWorldLocation() is a client-thread-only API on this RuneLite/Dro build.
        // Network transports execute on their own worker so marshal only this tiny snapshot back
        // to the client thread. ClientThread.invoke(Supplier) executes inline when already on the
        // client thread, so callers from normal Efficient Walker ticks keep the same behavior.
        return Microbot.getClientThread().invoke(() ->
        {
            Player player = client.getLocalPlayer();
            return player == null ? null : player.getWorldLocation();
        });
    }

    private static boolean near(WorldPoint a, WorldPoint b, int radius)
    {
        return a != null && b != null && a.getPlane() == b.getPlane() && a.distanceTo2D(b) <= radius;
    }

    private static String networkKey(EdgeType type, WorldPoint origin, WorldPoint destination)
    {
        return type + ":" + origin + "->" + destination;
    }

    private static String sharedEdgeKey(Transport transport)
    {
        return transport.getType() + ":" + transport.getOrigin() + ">" + transport.getDestination()
            + ":" + transport.getObjectId() + ":" + Objects.toString(transport.getAction(), "")
            + ":" + Objects.toString(transport.getName(), "");
    }

    private Transport currentSharedTransport;
    private int wildernessObeliskHops;

    private boolean canoeAllowed(Object transport)
    {
        if (transport == null || !WildernessTravelPolicy.isCanoe(
            transportString(transport, transportActionField), transportString(transport, transportTargetField))) return true;
        return WildernessTravelPolicy.canoeAllowed(transportWorldPoint(transport, transportOriginField),
            transportWorldPoint(transport, transportDestinationField),
            config.useCanoes(), config.allowWildernessTravel());
    }

    private static boolean sharedCanStartFrom(WorldPoint player, Transport transport)
    {
        return player != null && ((isSharedTeleportSpell(transport) || MaxPohSupport.isLeg(transport)) && transport.getOrigin() == null
            || near(player, transport.getOrigin(), 10));
    }

    private boolean beforeSharedTransportTick(Transport transport)
    {
        boolean pendingAction = currentSharedTransport == transport && actionFuture != null;
        if (!pendingAction && transport.getType() == TransportType.CANOE && !WildernessTravelPolicy.canoeAllowed(
            transport.getOrigin(), transport.getDestination(), config.useCanoes(), config.allowWildernessTravel()))
        {
            WorldPoint goal = engineDestination();
            cancelFuture();
            clearCompleteRoute();
            resetExecution(false);
            installedKey = null;
            if (goal != null) replanTarget = goal;
            return true;
        }
        if (currentSharedTransport != transport)
        {
            cancelFuture();
            resetExecution(false);
            currentSharedTransport = transport;
        }
        WorldPoint player = playerLocation();
        if (sharedTransportLanded(player, transport))
        {
            WorldPoint goal = engineDestination();
            cancelFuture();
            resetExecution(false);
            currentSharedTransport = null;
            installedKey = null;
            if (goal != null) replanTarget = goal;
            return goal != null;
        }
        if (actionFuture == null)
        {
            if (!sharedCanStartFrom(player, transport)) return false;
            // A nearby ladder/portal can be in the next room. Let the native walker
            // open intervening doors and execute earlier obstacles before taking ownership.
            if (transport.getOrigin() != null && !sharedApproachReachable(player, transport)) return false;
            actionStartedAt = System.currentTimeMillis();
            actionFuture = actionExecutor().submit(() -> executeSharedTransport(transport));
            return true;
        }
        if (!actionFuture.isDone())
        {
            if (System.currentTimeMillis() - actionStartedAt < EXECUTION_TIMEOUT_MS) return true;
            cancelFuture();
            clearCompleteRoute();
            failedSharedEdges.add(sharedEdgeKey(transport));
            WorldPoint goal = engineDestination();
            currentSharedTransport = null;
            resetExecution(false);
            installedKey = null;
            stickySwampBankRetry = stickySwampBankJourney && StickySwampExit.zipline(transport)
                && StickySwampExit.bankTarget(goal);
            if (goal != null) replanTarget = goal;
            return true;
        }
        ExecutionResult result = consumeFutureResult();
        WorldPoint goal = engineDestination();
        if(result==ExecutionResult.BANK_OPEN){currentSharedTransport=null;resetExecution(false);return true;}
        if (result != ExecutionResult.SUCCESS)
        {
            stickySwampBankRetry = stickySwampBankJourney && StickySwampExit.zipline(transport)
                && StickySwampExit.bankTarget(goal);
            clearCompleteRoute();
            failedSharedEdges.add(sharedEdgeKey(transport));
            debug("shared transport failed and was suppressed for the session: {} {} -> {}",
                transport.getType(), transport.getOrigin(), transport.getDestination());
        }
        currentSharedTransport = null;
        resetExecution(false);
        installedKey = null;
        if (goal != null) replanTarget = goal;
        return true;
    }

    private boolean sharedApproachReachable(WorldPoint player, Transport transport)
    {
        Set<WorldPoint> reachable = Rs2Tile.getReachableTilesFromTile(player, 24).keySet();
        TileObject object = findCatalogObject(transport);
        if (WebTravel.isWeb(transport))
        {
            // The web itself blocks walking onto its catalog origin. It must be
            // clicked from an adjacent floor tile, before the normal walking handoff.
            return WebTravel.canInteractFrom(player, transport, object == null ? null : object.getWorldLocation());
        }
        if (object != null)
        {
            WorldArea area = object instanceof GameObject
                ? SceneObjects.getWorldArea((GameObject) object)
                : new WorldArea(object.getWorldLocation(), 1, 1);
            return TransportApproach.reachable(area, reachable, client.getTopLevelWorldView());
        }
        // NPC and specialized transports still need a collision-valid approach to
        // their catalog origin; distance alone must never bypass a closed obstacle.
        return reachable.contains(transport.getOrigin());
    }

    private ExecutionResult executeSharedTransport(Transport transport)
    {
        try
        {
            if(transport.getType().name().startsWith("TELEPORTATION")&&!net.runelite.client.plugins.microbot.droslayer.support.TeleportBankGuard.ready())return ExecutionResult.BANK_OPEN;
            if (transport.getType() == TransportType.MAGIC_MUSHTREE)
            {
                MushtreeStop origin = Arrays.stream(MUSHTREE_STOPS)
                    .filter(stop -> near(transport.getOrigin(), stop.landing, 3)
                        || Arrays.stream(stop.routeOrigins).anyMatch(point -> near(point, transport.getOrigin(), 1)))
                    .findFirst().orElse(null);
                MushtreeStop destination = Arrays.stream(MUSHTREE_STOPS)
                    .filter(stop -> near(transport.getDestination(), stop.landing, 3)).findFirst().orElse(null);
                if (origin == null || destination == null) return ExecutionResult.FAILED;
                return finishMenuExecution(EdgeType.MUSHTREE, executeMagicMushtree(new NetworkEdge(
                    EdgeType.MUSHTREE, origin.routeOrigins[0], destination.landing,
                    destination.label, "Magic Mushtree", origin.objectId, 0)));
            }
            if (transport.getType() == TransportType.MINECART && transport.getObjectId() == KOUREND_MINECART_OBJECT_ID)
            {
                return finishMenuExecution(EdgeType.MINECART, executeMinecart(new NetworkEdge(
                    EdgeType.MINECART, transport.getOrigin(), transport.getDestination(), transport.getDisplayInfo(),
                    "Minecart", KOUREND_MINECART_OBJECT_ID, currentKourendMinecartFare())));
            }
            if (MaxPohSupport.isLeg(transport))
                return MaxPohSupport.execute(transport) ? ExecutionResult.SUCCESS : ExecutionResult.FAILED;
            if (WebTravel.isWeb(transport)) return executeWebTransport(transport);
            if(TzhaarAccess.isVent(transport)){
                boolean crossed=TzhaarDoorAttempts.pass(
                    ()->!Thread.currentThread().isInterrupted()&&!Microbot.pauseAllScripts.get()&&!net.runelite.client.plugins.microbot.util.input.InputArbiter.isHuman(),
                    ()->sharedTransportLanded(playerLocation(),transport),
                    ()->{
                        TileObject door=findCatalogObject(transport);
                        return door!=null&&net.runelite.client.plugins.microbot.droslayer.walker.support.TransportObjectClick.click(door,"Pass");
                    },
                    ()->awaitSharedLanding(transport,()->sharedTransportLanded(playerLocation(),transport),8_000L),
                    NetworkTransportSupport::ventActionBusy,
                    ()->waitUntil(()->!ventActionBusy(),15_000L));
                if(!crossed&&!Thread.currentThread().isInterrupted()&&!Microbot.pauseAllScripts.get()&&!net.runelite.client.plugins.microbot.util.input.InputArbiter.isHuman())
                    tzhaarDoorFailure="Hot vent door did not finish passage within the two-attempt limit; skipping Inferno";
                return crossed?ExecutionResult.SUCCESS:ExecutionResult.FAILED;
            }
            // The boulder crossing can reach its tile before the animation finishes.
            // Retain transport ownership and observe the end before using the adjacent crevice.
            if (WildernessGodWarsTransports.isCrevice(transport)
                && !waitUntil(() -> Microbot.getClientThread().runOnClientThreadOptional(() -> {
                    var player = client.getLocalPlayer();
                    return player != null && player.getAnimation() == -1 && !Rs2Player.isMoving();
                }).orElse(false), 5_000L)) return ExecutionResult.FAILED;
            if (transport.getType() == TransportType.CANOE && !WildernessTravelPolicy.canoeAllowed(
                transport.getOrigin(), transport.getDestination(), config.useCanoes(), config.allowWildernessTravel()))
                return ExecutionResult.FAILED;
            if ((isUndergroundObjectTransport(transport) || isSharedTeleportSpell(transport) || MagicAxesHutTransports.isEntry(transport)
                    || transport.getType() == TransportType.TELEPORTATION_LEVER)
                && !Microbot.getClientThread().invoke((java.util.function.Supplier<Boolean>) () -> sharedRequirementsSatisfiedNow(transport)))
                return ExecutionResult.FAILED;
            String method = null;
            switch (transport.getType())
            {
                case CANOE: method = "handleCanoe"; break;
                case MAGIC_CARPET: method = "handleMagicCarpet"; break;
                case GNOME_GLIDER: method = "handleGlider"; break;
                case CHARTER_SHIP: method = "handleCharterShip"; break;
                case SEASONAL_TRANSPORT: method = "handleSeasonalTransport"; break;
                default: break;
            }
            boolean started;
            if (transport instanceof net.runelite.client.plugins.microbot.util.poh.PohTransport)
            {
                started = MaxPohSupport.executeFacility(((net.runelite.client.plugins.microbot.util.poh.PohTransport) transport).getTeleport());
            }
            else if (transport.getType() == TransportType.TELEPORTATION_SPELL)
            {
                Class<?> executor = Class.forName("net.runelite.client.plugins.microbot.droslayer.walker.support.Rs2WalkerTransports");
                Method handle = executor.getDeclaredMethod("handleTeleportSpell", Transport.class);
                handle.setAccessible(true);
                started = Boolean.TRUE.equals(handle.invoke(null, transport));
            }
            else if (transport.getType() == TransportType.HOT_AIR_BALLOON)
            {
                Class<?> edgeClass = Class.forName("net.runelite.client.plugins.microbot.droslayer.walker.support.Rs2TransportEdge");
                Method convert = Class.forName("net.runelite.client.plugins.microbot.droslayer.walker.support.Rs2PathApi")
                    .getDeclaredMethod("toTransportEdge", Transport.class);
                convert.setAccessible(true);
                Object edge = convert.invoke(null, transport);
                Class<?> balloon = Class.forName("net.runelite.client.plugins.microbot.droslayer.walker.support.Rs2HotAirBalloon");
                Method handle = balloon.getDeclaredMethod("handle", edgeClass);
                handle.setAccessible(true);
                started = Boolean.TRUE.equals(handle.invoke(null, edge));
            }
            else if (method != null)
            {
                Class<?> executor = Class.forName("net.runelite.client.plugins.microbot.droslayer.walker.support.Rs2WalkerTransports");
                Method handle = executor.getDeclaredMethod(method, Transport.class);
                handle.setAccessible(true);
                started = Boolean.TRUE.equals(handle.invoke(null, transport));
            }
            else
            {
                started = executeTerminalCatalogTransport(transport);
            }
            if (!started) return ExecutionResult.FAILED;
            return awaitSharedLanding(transport, () -> sharedTransportLanded(playerLocation(), transport),
                sharedLandingTimeout(transport)) ? ExecutionResult.SUCCESS : ExecutionResult.FAILED;
        }
        catch (ReflectiveOperationException | RuntimeException ex)
        {
            debug("shared transport execution error type={} {} -> {}: {}", transport.getType(),
                transport.getOrigin(), transport.getDestination(), ex.getMessage());
            return ExecutionResult.FAILED;
        }
    }

    static boolean awaitSharedLanding(Transport transport, BooleanSupplier landed, long timeoutMs)
    {
        boolean[] confirmationSent = { false };
        return waitUntil(() -> {
            if (landed.getAsBoolean()) return true;
            if (!confirmationSent[0] && transport.getType() == TransportType.TELEPORTATION_SPELL
                && "Ice Plateau Teleport".equalsIgnoreCase(transport.getDisplayInfo())
                && Microbot.getClientThread().runOnClientThreadOptional(
                    () -> Rs2Widget.hasWidget("Go DEEP into the wilderness?")).orElse(false))
            {
                confirmationSent[0] = Rs2Dialogue.clickOption("Yes", true);
            }
            return false;
        }, timeoutMs);
    }

    private static boolean sharedTransportLanded(WorldPoint player, Transport transport)
    {
        if (LumbridgeSwampCaveAccess.isEntrance(transport))
        {
            var thread = Microbot.getClientThread();
            if (thread == null) return false;
            return thread.runOnClientThreadOptional(() -> {
                var current = Microbot.getClient() == null ? null : Microbot.getClient().getLocalPlayer();
                return current != null && Microbot.getClient().getGameState() == GameState.LOGGED_IN
                    && current.getAnimation() == -1 && !Rs2Player.isMoving()
                    && near(current.getWorldLocation(), transport.getDestination(), 2);
            }).orElse(false);
        }
        if (MotherlodeTransports.isUpperLadderDescent(transport))
        {
            var thread = Microbot.getClientThread();
            if (thread == null) return false;
            return thread.runOnClientThreadOptional(() -> {
                var client = Microbot.getClient();
                var current = client == null ? null : client.getLocalPlayer();
                if (current == null || client.getGameState() != GameState.LOGGED_IN
                    || current.getAnimation() != -1 || Rs2Player.isMoving()) return false;
                WorldPoint here = current.getWorldLocation();
                // Nearby upper tiles share the plane. Only live access to the lower bank
                // proves the climb, including landings beside the graph's approach tile.
                return near(here, transport.getDestination(), 2)
                    && Rs2Tile.getReachableTilesFromTile(here, 12)
                        .containsKey(MotherlodeTransports.BANK_APPROACH);
            }).orElse(false);
        }
        if(TzhaarAccess.isVent(transport))return TzhaarAccess.crossedVent(transport,player)&&!ventActionBusy();
        if (MaxPohSupport.isLeg(transport))
            return MaxPohSupport.landed(transport.getDestination(), player, MaxPohSupport.inHouse());
        // Door of Dinh can land at (1631, 3982), three tiles from its catalog endpoint.
        // Scope this tolerance to the known entrance; outside tiles cannot satisfy it.
        if (transport.getObjectId() == 29322 && "Enter".equalsIgnoreCase(transport.getAction())
            && near(transport.getOrigin(), new WorldPoint(1630, 3963, 0), 2)
            && new WorldPoint(1630, 3979, 0).equals(transport.getDestination()))
        {
            return near(player, transport.getDestination(), 3);
        }
        // Nearby endpoints can straddle a wall or a portal. Only the actual landing
        // tile proves that such a short edge was crossed.
        int radius = near(transport.getOrigin(), transport.getDestination(), LANDING_RADIUS * 2)
            ? 0 : (isUndergroundObjectTransport(transport) ? 2 : LANDING_RADIUS);
        return near(player, transport.getDestination(), radius);
    }

    private static boolean ventActionBusy(){
        return Microbot.getClientThread().runOnClientThreadOptional(()->{
            var c=Microbot.getClient();var player=c==null?null:c.getLocalPlayer();
            return player==null||c.getGameState()!=GameState.LOGGED_IN||player.getAnimation()!=-1||Rs2Player.isMoving();
        }).orElse(true);
    }

    private ExecutionResult executeWebTransport(Transport transport)
    {
        // A slashed web transforms/disappears. Absence alone is not proof of passage:
        // require live collision reachability before issuing the crossing click.
        for (int attempt = 0; attempt < 8 && !Thread.currentThread().isInterrupted(); attempt++)
        {
            if (sharedTransportLanded(playerLocation(), transport)) return ExecutionResult.SUCCESS;
            if (webPassageReachable(transport))
            {
                net.runelite.client.plugins.microbot.droslayer.walker.support.Rs2Walker.walkFastCanvas(transport.getDestination());
                return waitUntil(() -> sharedTransportLanded(playerLocation(), transport), 3_000L)
                    ? ExecutionResult.SUCCESS : ExecutionResult.FAILED;
            }
            if (!WebTravel.canCut()) return ExecutionResult.FAILED;
            TileObject web = findCatalogObject(transport);
            if (web == null || !net.runelite.client.plugins.microbot.droslayer.walker.support.TransportObjectClick.click(web, "Slash"))
                return ExecutionResult.FAILED;
            waitUntil(() -> webPassageReachable(transport), 1_800L);
        }
        return ExecutionResult.FAILED;
    }

    private boolean webPassageReachable(Transport transport)
    {
        WorldPoint player = playerLocation();
        return player != null && near(player, transport.getOrigin(), 3)
            && Rs2Tile.getReachableTilesFromTile(player, 8).containsKey(transport.getDestination());
    }

    private static long sharedLandingTimeout(Transport transport)
    {
        // Catalogue durations are in game ticks (600 ms each). Keep only a short
        // interaction grace period; do not hold the walker in a fixed 15-second wait.
        long travelMillis = Math.max(0, transport.getDuration()) * 600L;
        return Math.min(EXECUTION_TIMEOUT_MS,
            Math.max(SHARED_TRANSPORT_LANDING_WAIT_MS, travelMillis + 2_000L));
    }

    private boolean executeTerminalCatalogTransport(Transport transport)
    {
        boolean swampCave = LumbridgeSwampCaveAccess.isEntrance(transport);
        if (swampCave && (!LumbridgeSwampCaveAccess.inputAllowed()
            || !Microbot.getClientThread().runOnClientThreadOptional(CaveLightSource::hasLight).orElse(false))) return false;
        LumbridgeSwampCaveAccess.Warning existingCaveWarning = swampCave
            ? Microbot.getClientThread().runOnClientThreadOptional(() -> LumbridgeSwampCaveAccess.warning(client)).orElse(null) : null;
        if (swampCave && existingCaveWarning == null) return false;
        boolean caveWarningOpen = existingCaveWarning != null && existingCaveWarning.group >= 0;
        if (transport.getObjectId() <= 0 || transport.getAction() == null) return false;
        if (EdgevilleDungeonAccess.isTrapdoor(transport)) {
            TileObject trapdoor = findCatalogObject(transport);
            if (trapdoor == null) return false;
            if (SceneObjects.hasAction(trapdoor, "Open", true)) {
                if (!net.runelite.client.plugins.microbot.droslayer.walker.support.TransportObjectClick.click(trapdoor, "Open")
                    || !waitUntil(() -> {
                        TileObject opened = findCatalogObject(transport);
                        return opened != null && SceneObjects.hasAction(opened, "Climb-down", true);
                    }, 5000L)) return false;
                // Re-query after the object transforms; never click the closed snapshot.
            }
        }
        if (ApeAtollTransports.isGate(transport)
            && Rs2Player.getQuestState(Quest.MONKEY_MADNESS_II) != QuestState.FINISHED)
        {
            int greegree = ApeAtollTransports.GREEGREES.stream().filter(TravelEquipment::isWearing)
                .findFirst().orElseGet(() -> ApeAtollTransports.GREEGREES.stream()
                    .filter(Rs2Inventory::hasItem).findFirst().orElse(-1));
            if (greegree < 0) return false;
            if (!TravelEquipment.isWearing(greegree)
                && (!Rs2Inventory.interact(greegree, "Hold") || !waitUntil(() -> TravelEquipment.isWearing(greegree), 2500L)))
                return false;
        }
        if (transport.getObjectId() == 26419 && Microbot.getVarbitValue(3966) == 0)
        {
            // First visit requires installing the rope before Climb-down becomes available.
            if (!Rs2Inventory.hasItem(954)) return false;
            TileObject hole = findCatalogObject(transport);
            if (hole == null || !SceneObjects.interact(hole, "Tie-rope")
                || !waitUntil(() -> Microbot.getVarbitValue(3966) > 0, 5_000L)) return false;
        }
        // Reuse only the requested destination or this transport NPC's dialogue.
        boolean dialogueOpen = Rs2Dialogue.hasSelectAnOption() || Rs2Dialogue.hasContinue();
        boolean matchingDialogue = dialogueOpen && Microbot.getClientThread().runOnClientThreadOptional(() -> {
            String destination = transport.getDisplayInfo();
            if (destination != null && !destination.isBlank() && Rs2Dialogue.hasDialogueOption(destination, false)) return true;
            Widget speaker = client.getWidget(net.runelite.api.widgets.WidgetInfo.DIALOG_NPC_NAME);
            return speaker != null && !speaker.isHidden() && transport.getName() != null
                && transport.getName().equalsIgnoreCase(net.runelite.client.util.Text.removeTags(speaker.getText()));
        }).orElse(false);
        if (dialogueOpen && !matchingDialogue && !caveWarningOpen)
        {
            if (Microbot.pauseAllScripts.get() || net.runelite.client.plugins.microbot.util.input.InputArbiter.isHuman()) return false;
            Rs2Keyboard.keyPress(java.awt.event.KeyEvent.VK_ESCAPE);
            if (!waitUntil(() -> !Rs2Dialogue.hasSelectAnOption() && !Rs2Dialogue.hasContinue(), 1000L)) return false;
        }
        if (!matchingDialogue && !caveWarningOpen)
        {
            if (transport.getType() == TransportType.NPC || transport.getType() == TransportType.SHIP
                || transport.getType() == TransportType.BOAT)
            {
                TileObject object = transport.getType() == TransportType.NPC ? null : findCatalogObject(transport);
                if (object != null)
                {
                    if (!net.runelite.client.plugins.microbot.droslayer.walker.support.TransportObjectClick.click(object, transport.getAction())) return false;
                }
                else
                {
                    Rs2NpcModel npc = Rs2Npc.getNpc(transport.getObjectId());
                    if (npc == null && transport.getName() != null) npc = Rs2Npc.getNpc(transport.getName());
                    if (npc == null) return false;
                    String action = Rs2Npc.getAvailableAction(npc, Collections.singletonList(transport.getAction()));
                    if (action.isEmpty()) action = Rs2Npc.getAvailableAction(npc, List.of("Travel", "Talk-to", "Quick-Travel"));
                    if (action.isEmpty() || !Rs2Npc.interact(npc, action)) return false;
                }
            }
            else
            {
                TileObject object = findCatalogObject(transport);
                if (object == null || !net.runelite.client.plugins.microbot.droslayer.walker.support.TransportObjectClick.click(object, catalogObjectAction(transport, object))) return false;
            }

        }

        boolean gateCrossingIssued = false;
        boolean waterbirthWarningAccepted = false;
        boolean sophanemEntry = transport.getObjectId() == 20278;
        boolean darknessWarningAccepted = false;
        boolean swampWarningAccepted = false;
        long lastDialogueClickAt = 0;
        TransportMenuAttempts dialogueAttempts = new TransportMenuAttempts();
        boolean retryableObject = transport.getType() == TransportType.TRANSPORT
            && (transport.getOrigin().getPlane() != transport.getDestination().getPlane()
                || transport.getOrigin().distanceTo2D(transport.getDestination()) > 8
                || MotherlodeTransports.isUpperLadderDescent(transport)
                || MagicAxesHutTransports.isEntry(transport));
        int objectAttempts = 1;
        long lastObjectAttemptAt = System.currentTimeMillis();
        long deadline = System.currentTimeMillis() + 20_000L;
        while (System.currentTimeMillis() < deadline && !Thread.currentThread().isInterrupted())
        {
            if (sharedTransportLanded(playerLocation(), transport)
                && (!sophanemEntry || !Rs2Dialogue.hasContinue())) return true;
            if (swampCave)
            {
                if (!LumbridgeSwampCaveAccess.inputAllowed()) return false;
                var warning = Microbot.getClientThread().runOnClientThreadOptional(() ->
                    CaveLightSource.hasLight() ? LumbridgeSwampCaveAccess.warning(client) : null).orElse(null);
                if (warning == null) return false;
                if (warning.group >= 0)
                {
                    if (warning.selection != null && dialogueAttempts.allow("lumbridge-darkness:" + warning.group,
                        System.currentTimeMillis()) && LumbridgeSwampCaveAccess.accept(client, transport, warning))
                        swampWarningAccepted = true;
                    // A visible/loading warning owns this interaction. Never click the world behind it.
                    waitUntil(() -> sharedTransportLanded(playerLocation(), transport), 100L);
                    continue;
                }
            }
            if (Rs2Dialogue.hasSelectAnOption() || Rs2Dialogue.hasContinue())
            {
                String menuState = Microbot.getClientThread().runOnClientThreadOptional(() ->
                    Rs2Dialogue.getQuestion() + ":" + Rs2Dialogue.getDialogueText() + ":"
                        + Rs2Dialogue.getDialogueOptions().stream().map(Widget::getText)
                            .collect(java.util.stream.Collectors.joining("|"))).orElse(null);
                if (menuState == null) return false;
                if (!dialogueAttempts.allow(menuState, System.currentTimeMillis()))
                {
                    // Wait for changed options/arrival without a tight loop or another click.
                    waitUntil(() -> sharedTransportLanded(playerLocation(), transport)
                        || !Rs2Dialogue.hasSelectAnOption() && !Rs2Dialogue.hasContinue(), 100L);
                    continue;
                }
            }
            // Arrival coordinates can update before the previous transport's input lock ends.
            // This also affects consecutive caves on the same plane, not just ladders.
            // Retry at most four times, only idle and still beside the source object.
            if (retryableObject && !swampWarningAccepted && objectAttempts < 4
                && System.currentTimeMillis() - lastObjectAttemptAt >= 2500L
                && !Rs2Dialogue.hasContinue() && !Rs2Dialogue.hasSelectAnOption()
                && Microbot.getClientThread().runOnClientThreadOptional(() -> {
                    var player = client.getLocalPlayer();
                    return player != null && player.getAnimation() == -1
                        && player.getWorldLocation().getPlane() == transport.getOrigin().getPlane()
                        && player.getWorldLocation().distanceTo2D(transport.getOrigin()) <= 10
                        && !Rs2Player.isMoving();
                }).orElse(false))
            {
                TileObject object = findCatalogObject(transport);
                lastObjectAttemptAt = System.currentTimeMillis();
                objectAttempts++;
                if (object != null) {
                    String action = catalogObjectAction(transport, object);
                    if (action != null) net.runelite.client.plugins.microbot.droslayer.walker.support.TransportObjectClick.click(object, action);
                }
                debug("object transport retry id={} attempt={} player={} expected={}",
                    transport.getObjectId(), objectAttempts, playerLocation(), transport.getDestination());
            }
            // Main Waterbirth entrance uses a warning widget, not dialogue options.
            // Accept only this requested entrance; preserve the user's warning preference.
            if (transport.getObjectId() == 8929 && !waterbirthWarningAccepted
                && Rs2Widget.clickWidget("Go in - I don't mind dying", true))
            {
                waterbirthWarningAccepted = true;
            }
            if (sophanemEntry && !darknessWarningAccepted
                && (Rs2Inventory.hasItem("Bruma torch", false)
                    || Rs2Inventory.hasItem("Kandarin headgear", false)
                    || TravelEquipment.isWearing("Bruma torch", false)
                    || TravelEquipment.isWearing("Kandarin headgear", false))
                && Rs2Widget.clickWidget("Yes. I'm not scared of the dark.", true))
            {
                darknessWarningAccepted = true;
                lastDialogueClickAt = System.currentTimeMillis();
            }
            if (sophanemEntry && Rs2Dialogue.hasContinue())
            {
                // The guard's warning is followed by an NPC continuation before descent.
                if (System.currentTimeMillis() - lastDialogueClickAt >= 600L) {
                    Rs2Dialogue.clickContinue();
                    lastDialogueClickAt = System.currentTimeMillis();
                }
                waitUntil(() -> !Rs2Dialogue.hasContinue(), 100L);
                continue;
            }
            if (Rs2Dialogue.hasSelectAnOption())
            {
                // Lokar's confirmation is not named after the catalog destination.
                // Without the seal he offers a separate, valid Pirates' Cove branch.
                if (transport.getObjectId() == 3855
                    && (Rs2Dialogue.clickOption("I'm just going to Pirates' cove", false)
                        || Rs2Dialogue.clickOption("Go now.", true)
                        || Rs2Dialogue.clickOption("Arrr! Yar! Let's be on our way, yar!", true))) continue;
                if (transport.getObjectId() == 6650 && isLunarIsle(transport.getDestination())
                    && Rs2Dialogue.clickOption("Can we sail to Lunar Isle now?", true)) continue;
                String destination = transport.getDisplayInfo();
                if (destination != null && !destination.isBlank()
                    && Rs2Dialogue.clickOption(destination, true)) continue;
                if (Rs2Dialogue.clickOption("Yes", true) || Rs2Dialogue.clickOption("Yes.", true)) continue;
                // Option widgets can be visible before their text/keypress target is ready.
                // Keep this transport active instead of permanently removing its route immediately.
            }
            if (ApeAtollTransports.isGate(transport) && !gateCrossingIssued
                && !Rs2Dialogue.hasContinue() && !Rs2Dialogue.hasSelectAnOption()
                && Microbot.getClientThread().invoke((java.util.function.Supplier<Boolean>) () -> {
                    WorldPoint here = playerLocation();
                    return here != null && Rs2Tile.getReachableTilesFromTile(here, 8)
                        .containsKey(transport.getDestination());
                }))
            {
                // Opening the gate/finishing the archer dialogue is not arrival.
                // Walk across only once live collision confirms the passage is open.
                gateCrossingIssued = net.runelite.client.plugins.microbot.droslayer.walker.support.Rs2Walker
                    .walkMiniMap(transport.getDestination());
            }
            if (Rs2Dialogue.hasContinue()) Rs2Dialogue.clickContinue();
            net.runelite.client.plugins.microbot.util.Global.sleepUntil(
                () -> sharedTransportLanded(playerLocation(), transport)
                    || Rs2Dialogue.hasSelectAnOption() || Rs2Dialogue.hasContinue(),
                100);

        }
        boolean landed = sharedTransportLanded(playerLocation(), transport);
        if (!landed && !Thread.currentThread().isInterrupted() && !Microbot.pauseAllScripts.get()
            && !net.runelite.client.plugins.microbot.util.input.InputArbiter.isHuman()
            && (Rs2Dialogue.hasSelectAnOption() || Rs2Dialogue.hasContinue()))
        {
            Rs2Keyboard.keyPress(java.awt.event.KeyEvent.VK_ESCAPE);
            log.info("[EfficientWalker] transport dialogue timed out id={}; menu dismissed", transport.getObjectId());
        }
        return landed;
    }

    private static String catalogObjectAction(Transport transport, TileObject object)
    {
        if (EdgevilleDungeonAccess.isTrapdoor(transport) && SceneObjects.hasAction(object, "Open", true)) return "Open";
        if (SceneObjects.hasAction(object, transport.getAction(), true)) return transport.getAction();
        if ("Climb".equalsIgnoreCase(transport.getAction())
            && transport.getOrigin().getPlane() != transport.getDestination().getPlane())
        {
            String action = transport.getDestination().getPlane() > transport.getOrigin().getPlane()
                ? "Climb-up" : "Climb-down";
            if (SceneObjects.hasAction(object, action, true)) return action;
        }
        return null;
    }

    private TileObject findCatalogObject(Transport transport)
    {
        return Microbot.getClientThread().invoke(() -> SceneObjects.<TileObject>getAll(object ->
            object != null && (object.getId() == transport.getObjectId()
                || ApeAtollTransports.matchesObject(transport, object.getId())
                || EdgevilleDungeonAccess.isTrapdoor(transport) && (object.getId() == 1579 || object.getId() == 1581)
                || WildernessGodWarsTransports.isBoulder(transport)
                    && SceneObjects.convertToObjectComposition(object) != null
                    && "Boulder".equalsIgnoreCase(SceneObjects.convertToObjectComposition(object).getName()))
                && catalogObjectAction(transport, object) != null,
            transport.getOrigin(), 10).stream().min(Comparator.comparingInt(object ->
                object.getWorldLocation().distanceTo2D(transport.getOrigin()))).orElse(null));
    }

    private static String charterKey(WorldPoint origin, WorldPoint destination)
    {
        return String.valueOf(origin) + "->" + String.valueOf(destination);
    }

    private static boolean waitFixed(long durationMs)
    {
        long deadline = System.currentTimeMillis() + durationMs;
        while (System.currentTimeMillis() < deadline && !Thread.currentThread().isInterrupted())
        {
            long remaining = deadline - System.currentTimeMillis();
            try
            {
                Thread.sleep(Math.min(100L, Math.max(1L, remaining)));
            }
            catch (InterruptedException ex)
            {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return !Thread.currentThread().isInterrupted();
    }

    private static boolean waitUntil(BooleanSupplier condition, long timeoutMs)
    {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline && !Thread.currentThread().isInterrupted())
        {
            if (condition.getAsBoolean())
            {
                return true;
            }
            try
            {
                Thread.sleep(100L);
            }
            catch (InterruptedException ex)
            {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return condition.getAsBoolean();
    }

    private void debug(String format, Object... args)
    {
        if (config.enableDebugging())
        {
            log.info("[EfficientWalker] " + format, args);
        }
    }

    private static boolean invokeBoolean(Method method, Object target, Object arg)
    {
        try
        {
            return (Boolean) method.invoke(target, arg);
        }
        catch (InvocationTargetException ex)
        {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException)
            {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error)
            {
                throw (Error) cause;
            }
            throw new IllegalStateException(cause);
        }
        catch (ReflectiveOperationException ex)
        {
            throw new IllegalStateException(ex);
        }
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException
    {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void setField(Class<?> type, Object target, String name, Object value)
        throws ReflectiveOperationException
    {
        Field field = field(type, name);
        field.set(target, value);
    }

    private enum EdgeType
    {
        MINIGAME,
        CHARTER,
        CHARTER_EXIT,
        QUETZAL,
        PRIMIO,
        MINECART,
        ELKOY,
        MUSHTREE,
        FOSSIL_BARGE,
        FOSSIL_ROWBOAT,
        FOSSIL_HOLE,
        WILDERNESS_OBELISK,
        NATIVE
    }

    private enum ExecutionResult
    {
        BANK_OPEN,
        SUCCESS,
        FAILED,
        CHARTER_MENU_UNAVAILABLE,
        MISSING_DESTINATION,
        COOLDOWN,
        INSUFFICIENT_COINS
    }

    private static final class MushtreeStop
    {
        final String label;
        final WorldPoint[] routeOrigins;
        final WorldPoint objectTile;
        final WorldPoint landing;
        final int objectId;

        MushtreeStop(String label, WorldPoint[] routeOrigins, WorldPoint objectTile,
                     WorldPoint landing, int objectId)
        {
            this.label = label;
            this.routeOrigins = routeOrigins;
            this.objectTile = objectTile;
            this.landing = landing;
            this.objectId = objectId;
        }
    }

    private static final class MinecartStop
    {
        final WorldPoint point;
        final String menuLabel;

        MinecartStop(WorldPoint point, String menuLabel)
        {
            this.point = point;
            this.menuLabel = menuLabel;
        }
    }

    private static final class QuetzalStop
    {
        final WorldPoint point;
        final String label;
        final int unlockVarbit;

        QuetzalStop(WorldPoint point, String label, int unlockVarbit)
        {
            this.point = point;
            this.label = label;
            this.unlockVarbit = unlockVarbit;
        }

        boolean unlocked()
        {
            return unlockVarbit < 0 || Microbot.getVarbitValue(unlockVarbit) == 1;
        }
    }

    private static final class NetworkEdge
    {
        final EdgeType type;
        final WorldPoint origin;
        final WorldPoint destination;
        final String label;
        final String npcName;
        final int npcId;
        final int fare;

        NetworkEdge(EdgeType type, WorldPoint origin, WorldPoint destination, String label,
                    String npcName, int npcId, int fare)
        {
            this.type = type;
            this.origin = origin;
            this.destination = destination;
            this.label = label;
            this.npcName = npcName;
            this.npcId = npcId;
            this.fare = fare;
        }
    }

    private static final class CharterRow
    {
        final WorldPoint origin;
        final WorldPoint destination;
        final String action;
        final String npcName;
        final int npcId;
        final int fare;
        final List<SkillRequirement> skills;
        final List<Quest> quests;
        final List<NumericRequirement> varbits;
        final int duration;
        final String displayInfo;

        CharterRow(WorldPoint origin, WorldPoint destination, String action, String npcName,
                   int npcId, int fare, List<SkillRequirement> skills, List<Quest> quests,
                   List<NumericRequirement> varbits, int duration, String displayInfo)
        {
            this.origin = origin;
            this.destination = destination;
            this.action = action;
            this.npcName = npcName;
            this.npcId = npcId;
            this.fare = fare;
            this.skills = skills;
            this.quests = quests;
            this.varbits = varbits;
            this.duration = duration;
            this.displayInfo = displayInfo;
        }
    }

    private static final class MinigameRow
    {
        final WorldPoint destination;
        final List<SkillRequirement> skills;
        final List<Quest> quests;
        final boolean membersOnly;
        final List<NumericRequirement> varbits;
        final List<NumericRequirement> varplayers;
        final List<CooldownRequirement> cooldowns;
        final int maxWildernessLevel;
        final int duration;
        final String displayInfo;

        MinigameRow(WorldPoint destination, List<SkillRequirement> skills, List<Quest> quests,
                    boolean membersOnly, List<NumericRequirement> varbits,
                    List<NumericRequirement> varplayers, List<CooldownRequirement> cooldowns,
                    int maxWildernessLevel, int duration, String displayInfo)
        {
            this.destination = destination;
            this.skills = skills;
            this.quests = quests;
            this.membersOnly = membersOnly;
            this.varbits = varbits;
            this.varplayers = varplayers;
            this.cooldowns = cooldowns;
            this.maxWildernessLevel = maxWildernessLevel;
            this.duration = duration;
            this.displayInfo = displayInfo;
        }
    }

    private static final class NumericRequirement
    {
        final int id;
        final int value;
        final char comparator;

        NumericRequirement(int id, int value, char comparator)
        {
            this.id = id;
            this.value = value;
            this.comparator = comparator;
        }
    }

    private static final class SkillRequirement
    {
        final Skill skill;
        final int level;

        SkillRequirement(Skill skill, int level)
        {
            this.skill = skill;
            this.level = level;
        }
    }

    private static final class CooldownRequirement
    {
        final int id;
        final int minutes;

        CooldownRequirement(int id, int minutes)
        {
            this.id = id;
            this.minutes = minutes;
        }
    }

    private static final class ParsedVarplayers
    {
        final List<NumericRequirement> numeric;
        final List<CooldownRequirement> cooldowns;

        ParsedVarplayers(List<NumericRequirement> numeric, List<CooldownRequirement> cooldowns)
        {
            this.numeric = numeric;
            this.cooldowns = cooldowns;
        }
    }
}
