package net.runelite.client.plugins.microbot.droslayer.walker;

import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.droslayer.support.SceneObjects;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.function.Predicate;

/**
 * Adds Dro's maintained spirit-tree network data to the Efficient Walker planner.
 *
 * <p>Dro supplies data only.  Route selection remains entirely inside Efficient Walker:
 * the transport catalogue is extended with spirit-tree edges and its own route planner
 * decides whether those edges beat walking, native teleports, fairy rings, quetzals, etc.</p>
 *
 * <p>The v1.11.2 engine does not know how to choose a destination from the spirit-tree
 * interface, so this class performs only that selected edge's final Travel/menu interaction.  It
 * does not call Dro's Pathfinder or Rs2Walker.</p>
 */
final class SpiritTreeSupport
{
    private static final Logger log = LoggerFactory.getLogger(SpiritTreeSupport.class);

    private static final int SPIRIT_TREE_NETWORK_RADIUS = 5;
    private static final int INTERACT_RADIUS = 5;
    private static final int LANDING_RADIUS = 5;
    private static final long MENU_TIMEOUT_MS = 6_000L;
    private static final long LANDING_TIMEOUT_MS = 15_000L;

    // Player-grown Spirit Tree patches.  These are deliberately gated by the
    // per-account checkboxes because the static spirit-tree table cannot tell
    // whether a particular account has actually planted and grown the tree.
    private static final WorldPoint FARMED_ETCETERIA = new WorldPoint(2613, 3855, 0);
    private static final WorldPoint FARMED_BRIMHAVEN = new WorldPoint(2800, 3203, 0);
    private static final WorldPoint FARMED_PORT_SARIM = new WorldPoint(3058, 3257, 0);
    private static final WorldPoint FARMED_HOSIDIUS = new WorldPoint(1693, 3540, 0);
    private static final WorldPoint FARMED_FARMING_GUILD = new WorldPoint(1251, 3750, 0);

    private final Client client;
    private final EfficientWalkerConfig config;
    private final ClassLoader engineLoader;
    private final Class<?> engineClass;
    private final Object engine;
    private final String enginePackage;

    private final IdentityHashMap<Object, SpiritEdge> injectedEdges = new IdentityHashMap<>();

    // Cached reflection/data used to rebuild only the injected Spirit Tree layer when
    // a checkbox changes.  baseTransports is the untouched v1.11.2 catalogue.
    private List<Object> baseTransports = Collections.emptyList();
    private List<SpiritRow> sourceRows = Collections.emptyList();
    private Class<?> transportClass;
    private Constructor<?> transportCtor;
    private Constructor<?> requirementCtor;
    private Constructor<?> skillCtor;
    private Method allowTransportMethod;
    private Method buildPlannerMethod;
    private Method collectObjectIdsMethod;
    private String installedConfigKey;

    private Field activeTransportField;
    private boolean installed;
    private Object currentTransport;
    private SpiritEdge currentEdge;
    private Phase phase = Phase.IDLE;
    private long phaseStartedAt;
    private final PohTickTiming pohTiming = new PohTickTiming();
    private final Set<String> rejectedDestinations = new HashSet<>();
    private WorldPoint planningTarget;
    private boolean replanRequested;

    SpiritTreeSupport(Client client, EfficientWalkerConfig config, ClassLoader engineLoader,
                      Class<?> engineClass, Object engine, String enginePackage)
    {
        this.client = Objects.requireNonNull(client);
        this.config = Objects.requireNonNull(config);
        this.engineLoader = Objects.requireNonNull(engineLoader);
        this.engineClass = Objects.requireNonNull(engineClass);
        this.engine = Objects.requireNonNull(engine);
        this.enginePackage = Objects.requireNonNull(enginePackage);
    }

    void install()
    {
        try
        {
            sourceRows = readMicrobotSpiritTreeRows();
            if (sourceRows.isEmpty())
            {
                log.warn("[EfficientWalker] Dro spirit_trees.tsv was empty; keeping v1.11.2 graph unchanged");
                return;
            }

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

            Field transportListField = field(engineClass, "transports");
            @SuppressWarnings("unchecked")
            List<Object> original = (List<Object>) transportListField.get(engine);
            baseTransports = Collections.unmodifiableList(new ArrayList<>(original));

            allowTransportMethod = engineClass.getDeclaredMethod("canUseTransport", transportClass);
            allowTransportMethod.setAccessible(true);
            buildPlannerMethod = plannerClass.getDeclaredMethod("create", List.class, Predicate.class);
            buildPlannerMethod.setAccessible(true);
            collectObjectIdsMethod = engineClass.getDeclaredMethod("collectTransportObjectIds", List.class);
            collectObjectIdsMethod.setAccessible(true);
            activeTransportField = field(engineClass, "activeTransport");

            installed = true;
            rebuildGraph(true);
        }
        catch (Throwable ex)
        {
            // This feature is additive.  If Dro changes its resource format or reflection ever
            // stops matching, leave the already-working v1.11.2 engine untouched rather than fail
            // plugin startup.
            injectedEdges.clear();
            installed = false;
            log.warn("[EfficientWalker] spirit-tree extension unavailable; continuing with the unchanged v1.11.2 graph", ex);
        }
    }

    /**
     * Rebuilds only when the Spirit Tree config changed.  This keeps the checkboxes live without
     * rebuilding the planner on every script loop.
     */
    void refreshForConfigIfNeeded()
    {
        if (!installed || hasPendingAction())
        {
            return;
        }
        String key = configKey();
        if (!Objects.equals(key, installedConfigKey))
        {
            try
            {
                boolean activeSpiritRoute = activeInjectedEdge() != null;
                rebuildGraph(false);
                // The old selected edge is no longer in the rebuilt graph.
                replanRequested |= activeSpiritRoute;
            }
            catch (Throwable ex)
            {
                log.warn("[EfficientWalker] failed to refresh Spirit Tree config; retaining the previous graph", ex);
            }
        }
    }

    private void rebuildGraph(boolean initial) throws ReflectiveOperationException
    {
        resetInteraction();
        injectedEdges.clear();

        ArrayList<Object> merged = new ArrayList<>(baseTransports);
        List<SpiritRow> origins = new ArrayList<>();
        List<SpiritRow> destinations = new ArrayList<>();

        if (config.useSpiritTrees() && membersWorld())
        {
            for (SpiritRow row : sourceRows)
            {
                if (row.origin != null && row.destination == null)
                {
                    if (isTreeEndpointEnabled(row.origin, row.displayInfo))
                    {
                        origins.add(row);
                    }
                }
                else if (row.origin == null && row.destination != null)
                {
                    if (isTreeEndpointEnabled(row.destination, row.displayInfo))
                    {
                        destinations.add(row);
                    }
                }
                else if (row.origin != null && row.destination != null
                    && isTreeEndpointEnabled(row.origin, row.displayInfo)
                    && isTreeEndpointEnabled(row.destination, row.displayInfo))
                {
                    Object edge = buildShadowTransport(row, row, transportCtor, requirementCtor, skillCtor);
                    if (edge != null)
                    {
                        merged.add(edge);
                    }
                }
            }

            for (SpiritRow origin : origins)
            {
                for (SpiritRow destination : destinations)
                {
                    if (origin.origin.distanceTo2D(destination.destination) <= SPIRIT_TREE_NETWORK_RADIUS)
                    {
                        continue; // same tree: do not create a pointless self-hop
                    }
                    Object edge = buildShadowTransport(origin, destination, transportCtor, requirementCtor, skillCtor);
                    if (edge != null)
                    {
                        merged.add(edge);
                    }
                }
            }
        }

        List<Object> immutableMerged = Collections.unmodifiableList(merged);
        Predicate<Object> predicate = transport -> invokeBoolean(allowTransportMethod, engine, transport);
        Object planner = buildPlannerMethod.invoke(null, immutableMerged, predicate);
        Object objectIds = collectObjectIdsMethod.invoke(null, immutableMerged);

        setField(engineClass, engine, "transports", immutableMerged);
        setField(engineClass, engine, "planner", planner);
        setField(engineClass, engine, "transportObjectIds", objectIds);
        installedConfigKey = configKey();

        log.info(
            "[EfficientWalker] spirit-tree graph {}: enabled={} origins={} destinations={} edges={} totalTransports={} farmed=[Etceteria={}, Brimhaven={}, PortSarim={}, Hosidius={}, FarmingGuild={}]",
            initial ? "installed" : "refreshed", config.useSpiritTrees(), origins.size(), destinations.size(),
            injectedEdges.size(), immutableMerged.size(),
            config.spiritTreeEtceteria(), config.spiritTreeBrimhaven(),
            config.spiritTreePortSarim(), config.spiritTreeHosidius(),
            config.spiritTreeFarmingGuild());
    }

    private String configKey()
    {
        return membersWorld() + ":" + config.useSpiritTrees() + ":"
            + config.spiritTreeEtceteria() + ":"
            + config.spiritTreeBrimhaven() + ":"
            + config.spiritTreePortSarim() + ":"
            + config.spiritTreeHosidius() + ":"
            + config.spiritTreeFarmingGuild();
    }

    /** The current menu or issued destination still owns a bounded interaction receipt. */
    boolean hasPendingAction()
    {
        return phase != Phase.IDLE;
    }

    /** @return true when the spirit-tree bridge owns this tick and the engine should wait. */
    boolean beforeEngineTick()
    {
        if (!installed)
        {
            return false;
        }

        // A selected destination owns its landing receipt even if preferences or the
        // native active edge change while the game is completing the issued hop.
        boolean awaitingLanding = phase == Phase.WAITING_FOR_LANDING && currentEdge != null;
        SpiritEdge edge = awaitingLanding ? currentEdge : activeInjectedEdge();
        if (edge == null)
        {
            resetInteraction();
            return false;
        }

        if (!membersWorld())
        {
            installedConfigKey = null;
            replanRequested = true;
            resetInteraction();
            return true; // World eligibility is temporary, not a rejected account destination.
        }
        if (!awaitingLanding && !isSpiritEdgeEnabledBySettings(edge))
        {
            // A saved preference is reversible; it is not evidence that the account
            // lacks this destination. Keep genuine menu/landing failures separate.
            installedConfigKey = null;
            return replanInteraction(true);
        }
        if (!awaitingLanding && rejectedDestinations.contains(edge.destinationLabel))
        {
            return reject(edge, "disabled for this account");
        }
        if (!awaitingLanding && currentTransport != activeTransportFieldValue())
        {
            pohTiming.reset();
            currentTransport = activeTransportFieldValue();
            currentEdge = edge;
            phase = Phase.IDLE;
            phaseStartedAt = 0L;
            debug("selected spirit-tree edge {} -> {} ({})",
                edge.origin, edge.destination, edge.destinationLabel);
        }
        else
        {
            currentEdge = edge;
        }

        if (pohTiming.hold(MaxPohSupport.insideHouse(), System.currentTimeMillis())) return true;

        WorldPoint player = playerLocation();
        if (player == null)
        {
            return false;
        }

        if (near(player, edge.destination, LANDING_RADIUS))
        {
            if (phase != Phase.IDLE)
            {
                debug("spirit-tree landed at {} for {}", player, edge.destinationLabel);
            }
            phase = Phase.IDLE;
            phaseStartedAt = 0L;
            return false; // let Efficient Walker consume the completed transport edge
        }

        long now = System.currentTimeMillis();
        if (phase == Phase.WAITING_FOR_LANDING)
        {
            if (now - phaseStartedAt > LANDING_TIMEOUT_MS)
            {
                return reject(edge, "destination click did not produce arrival");
            }
            return true;
        }

        if (spiritTreeMenuVisible())
        {
            Widget root = Rs2Widget.getWidget(187, 3);
            Widget destination = findDestination(root, edge.destinationLabel);
            if (destination != null && disabled(destination))
            {
                return reject(edge, "destination is disabled in the Spirit Tree menu");
            }
            java.awt.Rectangle bounds = destination == null ? null : destination.getBounds();
            if (bounds != null && bounds.width > 0 && bounds.height > 0 && Rs2Widget.clickWidget(destination))
            {
                pohTiming.action(now);
                phase = Phase.WAITING_FOR_LANDING;
                phaseStartedAt = now;
                debug("spirit-tree destination selected: {}", edge.destinationLabel);
                return true;
            }
            // Allow the interface to populate, but do not reopen it or hand the same edge
            // back to the generic executor while its destination is unavailable.
            if (phase != Phase.WAITING_FOR_MENU)
            {
                phase = Phase.WAITING_FOR_MENU;
                phaseStartedAt = now;
            }
        }

        // The shared Adventure Log shell may be visible before the Spirit Tree title/options.
        if (phase == Phase.IDLE && Rs2Widget.isWidgetVisible(ComponentID.ADVENTURE_LOG_CONTAINER))
        {
            phase = Phase.WAITING_FOR_MENU;
            phaseStartedAt = now;
        }
        if (phase == Phase.WAITING_FOR_MENU)
        {
            if (now - phaseStartedAt > MENU_TIMEOUT_MS)
            {
                return reject(edge, "Spirit Tree menu/destination was not selectable");
            }
            return true;
        }

        if (!near(player, edge.origin, INTERACT_RADIUS))
        {
            return false; // Efficient Walker still needs to walk to the tree
        }

        TileObject tree = SceneObjects.findObjectById(edge.objectId);
        if (tree == null)
        {
            return false; // preserve the native engine's fallback/recovery behaviour
        }

        if (SceneObjects.interact(tree, "Travel"))
        {
            pohTiming.action(now);
            phase = Phase.WAITING_FOR_MENU;
            phaseStartedAt = now;
            debug("spirit-tree Travel clicked at {} -> {}", edge.origin, edge.destinationLabel);
            return true;
        }

        return false;
    }

    void planningFor(WorldPoint target)
    {
        if (!Objects.equals(planningTarget, target))
        {
            cancel();
            planningTarget = target;
        }
    }

    boolean consumeReplanRequest()
    {
        boolean requested = replanRequested;
        replanRequested = false;
        return requested;
    }

    private boolean reject(SpiritEdge edge, String reason)
    {
        boolean firstRejection = rejectedDestinations.add(edge.destinationLabel);
        if (firstRejection)
        {
            installedConfigKey = null;
            log.warn("[EfficientWalker] excluding spirit-tree destination {} for this journey: {}", edge.destinationLabel, reason);
        }
        return replanInteraction(firstRejection);
    }

    private boolean replanInteraction(boolean dismissMenu)
    {
        if (dismissMenu && Rs2Widget.isWidgetVisible(ComponentID.ADVENTURE_LOG_CONTAINER)
            && !net.runelite.client.plugins.microbot.Microbot.pauseAllScripts.get()
            && !net.runelite.client.plugins.microbot.util.input.InputArbiter.isHuman())
        {
            if (pohTiming.hold(MaxPohSupport.insideHouse(), System.currentTimeMillis())) return true;
            net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard.keyPress(java.awt.event.KeyEvent.VK_ESCAPE);
            pohTiming.action(System.currentTimeMillis());
        }
        replanRequested = true;
        resetInteraction();
        return true; // Never let the generic executor retry the failed edge on this tick.
    }

    void cancel()
    {
        resetInteraction();
        if (!rejectedDestinations.isEmpty())
        {
            installedConfigKey = null;
        }
        rejectedDestinations.clear();
        planningTarget = null;
        replanRequested = false;
    }

    private Object buildShadowTransport(SpiritRow originRow, SpiritRow destinationRow,
                                        Constructor<?> transportCtor,
                                        Constructor<?> requirementCtor,
                                        Constructor<?> skillCtor) throws ReflectiveOperationException
    {
        if (originRow.origin == null || destinationRow.destination == null
            || originRow.objectId < 0 || originRow.action == null || originRow.target == null)
        {
            return null;
        }

        if (rejectedDestinations.contains(normalizeDestinationLabel(destinationRow.displayInfo)))
        {
            return null;
        }
        List<Object> varbits = new ArrayList<>();
        for (NumericRequirement req : combined(originRow.varbits, destinationRow.varbits))
        {
            varbits.add(requirementCtor.newInstance(req.id, req.value, req.comparator));
        }

        List<Object> skills = new ArrayList<>();
        for (SkillRequirement req : combined(originRow.skills, destinationRow.skills))
        {
            skills.add(skillCtor.newInstance(req.skill, req.level));
        }

        List<Quest> quests = new ArrayList<>(originRow.quests);
        for (Quest quest : destinationRow.quests)
        {
            if (!quests.contains(quest))
            {
                quests.add(quest);
            }
        }

        Object edge = transportCtor.newInstance(
            originRow.origin,
            destinationRow.destination,
            originRow.action,
            originRow.target,
            originRow.objectId,
            skills,
            Collections.emptyList(), // no item requirements in Dro spirit_trees.tsv
            varbits,
            Collections.emptyList(), // no varplayer requirements in this dataset
            quests,
            false,
            new int[0]);

        String originLabel = normalizeDestinationLabel(originRow.displayInfo);
        String destinationLabel = normalizeDestinationLabel(destinationRow.displayInfo);
        injectedEdges.put(edge, new SpiritEdge(
            originRow.origin, destinationRow.destination, originRow.objectId, originLabel, destinationLabel));
        return edge;
    }

    private List<SpiritRow> readMicrobotSpiritTreeRows() throws IOException
    {
        InputStream stream = net.runelite.client.plugins.microbot.droslayer.walker.support.TransportResources.openResource("spirit_trees.tsv");
        if (stream == null)
        {
            throw new IOException("Dro shortestpath/spirit_trees.tsv not found on classpath");
        }

        List<SpiritRow> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)))
        {
            String headerLine = null;
            String line;
            while ((line = reader.readLine()) != null)
            {
                if (line.isBlank())
                {
                    continue;
                }
                if (headerLine == null)
                {
                    if (line.startsWith("#"))
                    {
                        headerLine = line.replaceFirst("^#\\s?", "");
                        break;
                    }
                    throw new IOException("spirit_trees.tsv has no header");
                }
            }

            if (headerLine == null)
            {
                return rows;
            }

            String[] headers = headerLine.split("\\t", -1);
            Map<String, Integer> columns = new LinkedHashMap<>();
            for (int i = 0; i < headers.length; i++)
            {
                columns.put(headers[i].trim().toLowerCase(), i);
            }

            int originCol = column(columns, "origin", 0);
            int destinationCol = column(columns, "destination", 1);
            int menuCol = findColumn(columns, 2, "menuoption menutarget objectid", "menuoption menuTarget objectID");
            int skillsCol = column(columns, "skills", 3);
            int questsCol = column(columns, "quests", 4);
            int varbitsCol = column(columns, "varbits", 5);
            int durationCol = column(columns, "duration", 6);
            int displayCol = findColumn(columns, 7, "display info", "displayinfo");

            while ((line = reader.readLine()) != null)
            {
                if (line.isBlank() || line.startsWith("#"))
                {
                    continue;
                }

                String[] fields = line.split("\\t", -1);
                SpiritRow row = parseRow(fields, originCol, destinationCol, menuCol,
                    skillsCol, questsCol, varbitsCol, durationCol, displayCol);
                if (row != null)
                {
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    private SpiritRow parseRow(String[] fields, int originCol, int destinationCol, int menuCol,
                               int skillsCol, int questsCol, int varbitsCol, int durationCol,
                               int displayCol)
    {
        try
        {
            WorldPoint origin = parsePoint(value(fields, originCol));
            WorldPoint destination = parsePoint(value(fields, destinationCol));
            if (origin == null && destination == null)
            {
                return null;
            }

            String menu = value(fields, menuCol).trim();
            String action = null;
            String target = null;
            int objectId = -1;
            if (!menu.isEmpty())
            {
                String[] menuParts = menu.split(";", -1);
                if (menuParts.length >= 3)
                {
                    action = menuParts[0].trim();
                    target = menuParts[1].trim();
                    objectId = Integer.parseInt(menuParts[2].trim());
                }
            }

            List<SkillRequirement> skills = parseSkills(value(fields, skillsCol));
            List<Quest> quests = parseQuests(value(fields, questsCol));
            if (quests == null)
            {
                return null; // unknown quest => fail closed, never make a locked tree appear available
            }
            List<NumericRequirement> varbits = parseNumericRequirements(value(fields, varbitsCol));
            if (varbits == null)
            {
                return null;
            }

            int duration = parseIntOrDefault(value(fields, durationCol), 0);
            String displayInfo = value(fields, displayCol).trim();

            return new SpiritRow(origin, destination, action, target, objectId,
                skills, quests, varbits, duration, displayInfo);
        }
        catch (RuntimeException ex)
        {
            log.debug("[EfficientWalker] skipped malformed spirit-tree row: {}", String.join(" | ", fields));
            return null;
        }
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
            String skillName = trimmed.substring(split + 1).trim().toUpperCase().replace(' ', '_');
            result.add(new SkillRequirement(Skill.valueOf(skillName), level));
        }
        return result;
    }

    /** @return null when a quest name is unknown, so the associated transport is rejected. */
    private static List<Quest> parseQuests(String text)
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
                log.warn("[EfficientWalker] unknown spirit-tree quest requirement '{}'; rejecting that route", name);
                return null;
            }
            result.add(found);
        }
        return result;
    }

    /** @return null for an unsupported comparator so the route is rejected rather than over-permitted. */
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

    /**
     * Player-grown spirit trees cannot be inferred safely from the static transport table.
     * They are therefore opt-in per account.  Built-in trees remain automatic: their
     * quest/varbit/skill requirements are evaluated by Efficient Walker's preserved
     * availability predicate.
     */
    private boolean isSpiritEdgeEnabledBySettings(SpiritEdge edge)
    {
        return config.useSpiritTrees()
            && isTreeEndpointEnabled(edge.origin, edge.originLabel)
            && isTreeEndpointEnabled(edge.destination, edge.destinationLabel);
    }

    private boolean membersWorld()
    {
        if (!client.isClientThread())
            return net.runelite.client.plugins.microbot.Microbot.getClientThread()
                .runOnClientThreadOptional(this::membersWorld).orElse(false);
        return client.getWorldType() != null
            && client.getWorldType().contains(net.runelite.api.WorldType.MEMBERS);
    }

    /**
     * Farmed origin rows in Dro's TSV intentionally have no display label, so checking only
     * the destination text is insufficient.  Match both by label and by the actual tree location.
     * This is the bug that previously let an unchecked Farming Guild tree remain in the graph.
     */
    private boolean isTreeEndpointEnabled(WorldPoint point, String label)
    {
        String normalized = label == null ? "" : label.trim().toLowerCase(Locale.ROOT);
        if ("etceteria".equals(normalized) || near(point, FARMED_ETCETERIA, SPIRIT_TREE_NETWORK_RADIUS))
        {
            return config.spiritTreeEtceteria();
        }
        if ("brimhaven".equals(normalized) || near(point, FARMED_BRIMHAVEN, SPIRIT_TREE_NETWORK_RADIUS))
        {
            return config.spiritTreeBrimhaven();
        }
        if ("port sarim".equals(normalized) || near(point, FARMED_PORT_SARIM, SPIRIT_TREE_NETWORK_RADIUS))
        {
            return config.spiritTreePortSarim();
        }
        if ("hosidius".equals(normalized) || near(point, FARMED_HOSIDIUS, SPIRIT_TREE_NETWORK_RADIUS))
        {
            return config.spiritTreeHosidius();
        }
        if ("farming guild".equals(normalized) || near(point, FARMED_FARMING_GUILD, SPIRIT_TREE_NETWORK_RADIUS))
        {
            return config.spiritTreeFarmingGuild();
        }
        return true; // built-in tree; original Efficient Walker handles quest/varbit requirements
    }

    private boolean spiritTreeMenuVisible()
    {
        return Rs2Widget.isWidgetVisible(ComponentID.ADVENTURE_LOG_CONTAINER)
            && Rs2Widget.hasWidgetText("Spirit Tree Locations", 187, 0, false);
    }

    private static boolean disabled(Widget widget)
    {
        String text = Objects.toString(widget.getText(), "").toLowerCase(Locale.ROOT);
        return widget.isHidden() || widget.getTextColor() == 0x5f5f5f || text.contains("<col=5f5f5f>");
    }

    private static Widget findDestination(Widget root, String label)
    {
        if (root == null || root.isHidden() || label == null || label.isBlank()) return null;
        Set<Widget> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        java.util.Deque<Widget> pending = new ArrayDeque<>(); pending.add(root);
        while (!pending.isEmpty()) {
            Widget widget = pending.removeFirst();
            if (!visited.add(widget) || widget.isHidden()) continue;
            String text = Objects.toString(widget.getText(), "").replaceAll("<[^>]*>", "");
            if (normalizeDestinationLabel(text).equalsIgnoreCase(label)) return widget;
            for (Widget[] children : new Widget[][]{widget.getChildren(), widget.getDynamicChildren(), widget.getStaticChildren(), widget.getNestedChildren()})
                if (children != null) for (Widget child : children) if (child != null) pending.addLast(child);
        }
        return null;
    }

    private SpiritEdge activeInjectedEdge()
    {
        Object active = activeTransportFieldValue();
        return active == null ? null : injectedEdges.get(active);
    }

    private Object activeTransportFieldValue()
    {
        if (activeTransportField == null)
        {
            return null;
        }
        try
        {
            return activeTransportField.get(engine);
        }
        catch (IllegalAccessException ex)
        {
            return null;
        }
    }

    private WorldPoint playerLocation()
    {
        Player player = client.getLocalPlayer();
        return player == null ? null : player.getWorldLocation();
    }

    private static boolean near(WorldPoint a, WorldPoint b, int radius)
    {
        return a != null && b != null && a.getPlane() == b.getPlane() && a.distanceTo2D(b) <= radius;
    }

    private void resetInteraction()
    {
        pohTiming.reset();
        currentTransport = null;
        currentEdge = null;
        phase = Phase.IDLE;
        phaseStartedAt = 0L;
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

    private static int column(Map<String, Integer> columns, String name, int fallback)
    {
        return columns.getOrDefault(name.toLowerCase(), fallback);
    }

    private static int findColumn(Map<String, Integer> columns, int fallback, String... candidates)
    {
        for (String candidate : candidates)
        {
            Integer index = columns.get(candidate.toLowerCase());
            if (index != null)
            {
                return index;
            }
        }
        return fallback;
    }

    private static String value(String[] values, int index)
    {
        return index >= 0 && index < values.length ? values[index] : "";
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
        return new WorldPoint(
            Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }

    private static int parseIntOrDefault(String text, int fallback)
    {
        if (text == null || text.isBlank())
        {
            return fallback;
        }
        return Integer.parseInt(text.trim());
    }

    private static String normalizeDestinationLabel(String displayInfo)
    {
        if (displayInfo == null)
        {
            return "";
        }
        return displayInfo.trim().replaceFirst("^[0-9A-Za-z]+:\\s*", "");
    }

    private static <T> List<T> combined(List<T> first, List<T> second)
    {
        ArrayList<T> result = new ArrayList<>(first);
        result.addAll(second);
        return result;
    }

    private enum Phase
    {
        IDLE,
        WAITING_FOR_MENU,
        WAITING_FOR_LANDING
    }

    private static final class SpiritEdge
    {
        final WorldPoint origin;
        final WorldPoint destination;
        final int objectId;
        final String originLabel;
        final String destinationLabel;

        SpiritEdge(WorldPoint origin, WorldPoint destination, int objectId,
                   String originLabel, String destinationLabel)
        {
            this.origin = origin;
            this.destination = destination;
            this.objectId = objectId;
            this.originLabel = originLabel;
            this.destinationLabel = destinationLabel;
        }
    }

    private static final class SpiritRow
    {
        final WorldPoint origin;
        final WorldPoint destination;
        final String action;
        final String target;
        final int objectId;
        final List<SkillRequirement> skills;
        final List<Quest> quests;
        final List<NumericRequirement> varbits;
        final int duration;
        final String displayInfo;

        SpiritRow(WorldPoint origin, WorldPoint destination, String action, String target, int objectId,
                  List<SkillRequirement> skills, List<Quest> quests,
                  List<NumericRequirement> varbits, int duration, String displayInfo)
        {
            this.origin = origin;
            this.destination = destination;
            this.action = action;
            this.target = target;
            this.objectId = objectId;
            this.skills = skills;
            this.quests = quests;
            this.varbits = varbits;
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
}
