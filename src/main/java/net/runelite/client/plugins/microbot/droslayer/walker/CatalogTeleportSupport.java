package net.runelite.client.plugins.microbot.droslayer.walker;

import net.runelite.api.Client;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.microbot.util.magic.Spell;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.Rs2Pvp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/**
 * Adds Dro's maintained TELEPORTATION_ITEM catalogue to the v1.11.2
 * Efficient Walker teleport-provider list.
 *
 * <p>Dro is used only as the source of teleport metadata (item ids, destination,
 * quest/varbit/skill requirements). The Efficient Walker remains responsible
 * for route scoring, deciding whether a bank detour is worthwhile, selecting the winning
 * provider, withdrawing its item, and committing/replanning the route.</p>
 */
final class CatalogTeleportSupport
{
    private static final Logger LOG = LoggerFactory.getLogger(CatalogTeleportSupport.class);

    private static final List<String> GENERIC_ITEM_ACTIONS = List.of(
        "invoke", "empty", "consume", "open", "teleport", "rub", "break",
        "reminisce", "signal", "play", "commune", "squash", "blow");

    private final Client client;
    private final EfficientWalkerConfig config;
    private final Map<Object, CatalogEntry> providers = new IdentityHashMap<>();
    private final ClassLoader engineLoader;
    private final Class<?> engineClass;
    private final Object engine;
    private final String enginePackage;

    private final List<CatalogEntry> entries = new ArrayList<>();
    private boolean installed;

    CatalogTeleportSupport(Client client, EfficientWalkerConfig config, ClassLoader engineLoader, Class<?> engineClass,
                           Object engine, String enginePackage)
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
            Class<?> providerClass = Class.forName(enginePackage + ".TeleportProviders$TeleportProvider", true, engineLoader);
            Constructor<?> providerCtor = providerClass.getDeclaredConstructor(
                String.class,
                Supplier.class,
                Spell.class,
                int.class,
                String.class,
                IntSupplier.class,
                int.class,
                int[].class,
                Supplier.class,
                BooleanSupplier.class,
                boolean.class);
            providerCtor.setAccessible(true);

            Field providerListField = field(engineClass, "teleportProviders");
            @SuppressWarnings("unchecked")
            List<Object> originalProviders = (List<Object>) providerListField.get(engine);

            Field itemIdsField = field(providerClass, "itemIds");
            Field providerNameField = field(providerClass, "name");

            // Do not invoke the providers' landing suppliers during plugin construction:
            // several of them read varbits/house state and startup is not necessarily on the client
            // thread. Names + item ids are enough to keep the known-good providers authoritative.
            List<ExistingProvider> existing = new ArrayList<>();
            for (Object provider : originalProviders)
            {
                String name = String.valueOf(providerNameField.get(provider));
                int[] ids = ((int[]) itemIdsField.get(provider)).clone();
                existing.add(new ExistingProvider(name, ids));
            }

            Map<WorldPoint, Set<Transport>> transportGraph = net.runelite.client.plugins.microbot.droslayer.walker.support.TransportResources.loadAllFromResources();
            ArrayList<Object> mergedProviders = new ArrayList<>(originalProviders);
            // Replace preserved amulet providers so a charge/marker failure cannot fall
            // through to an older provider with weaker requirements.
            for (Object provider : originalProviders)
                if (Arrays.stream((int[]) itemIdsField.get(provider)).anyMatch(id -> id == 32399))
                    mergedProviders.remove(provider);
            int added = 0;
            int skippedExisting = 0;
            int skippedComplex = 0;

            for (Set<Transport> transports : transportGraph.values())
            {
                if (transports == null)
                {
                    continue;
                }
                for (Transport transport : transports)
                {
                    if (transport == null || transport.getType() != TransportType.TELEPORTATION_ITEM
                        || transport.getDestination() == null)
                    {
                        continue;
                    }

                    // The typed requirements distinguish OR-ed charges from conjunctive
                    // supplies. The legacy view has one set per charge for jewellery.
                    var requirements = transport.getItemRequirements();
                    if (requirements == null || requirements.size() != 1
                        || requirements.get(0).getAlternatives().values().stream().anyMatch(count -> count != 1))
                    {
                        skippedComplex++;
                        continue;
                    }
                    Set<Integer> alternatives = requirements.get(0).getAlternatives().keySet();
                    if (alternatives.isEmpty())
                    {
                        skippedComplex++;
                        continue;
                    }

                    int[] itemIds = alternatives.stream()
                        .filter(Objects::nonNull)
                        .mapToInt(Integer::intValue)
                        .filter(id -> id > 0)
                        .distinct()
                        .sorted()
                        .toArray();
                    if (itemIds.length == 0)
                    {
                        skippedComplex++;
                        continue;
                    }

                    String display = transport.getDisplayInfo() == null
                        ? "Teleport item"
                        : transport.getDisplayInfo().trim();
                    String lowerDisplay = display.toLowerCase(Locale.ROOT);

                    // These two require a second custom interface/landing protocol. Keep them out
                    // of this first broad item-provider pass rather than risk a click/replan loop.
                    if (Arrays.stream(itemIds).anyMatch(id -> id == ItemID.BOOKOFSCROLLS_CHARGED)
                        || lowerDisplay.contains("master scroll book")
                        || lowerDisplay.contains("quetzal whistle"))
                    {
                        skippedComplex++;
                        continue;
                    }

                    if (alreadyCovered(existing, display, itemIds)
                        && Arrays.stream(itemIds).noneMatch(id -> id == 32399))
                    {
                        // Keep catalogue metadata for the providers too.  The shadow
                        // engine remains authoritative for execution, while Dro's route advisor
                        // layer must still see an equipped copy of an existing teleport item.
                        entries.add(new CatalogEntry(transport, itemIds, display));
                        skippedExisting++;
                        continue;
                    }

                    CatalogEntry entry = new CatalogEntry(transport, itemIds, display);
                    entries.add(entry);

                    Supplier<WorldPoint> landing = transport::getDestination;
                    IntSupplier widgetOption = () -> 1;
                    Supplier<String> itemAction = () -> resolveAction(entry);
                    BooleanSupplier available = () -> entry.eligible && allowed(entry.transport);

                    Spell spell = ancientSpell(display);
                    Object provider = providerCtor.newInstance(
                        display,
                        landing,
                        spell,
                        ancientWidget(display),
                        spell == null ? null : "Cast",
                        widgetOption,
                        -1,
                        itemIds,
                        itemAction,
                        available,
                        true); // Also allow an already-equipped travel item; never equip a withdrawal.
                    mergedProviders.add(provider);
                    providers.put(provider, entry);
                    added++;
                }
            }

            // Preserve the original provider list's ordinary mutable-list semantics; the engine
            // currently reads this list, but wrapping it as unmodifiable would be an unnecessary
            // behavioral change if a future lifecycle path appends/rebuilds providers.
            for (Object provider : originalProviders)
            {
                String name = String.valueOf(providerNameField.get(provider));
                if (name.startsWith("Necklace of passage:")) {
                    for (CatalogEntry entry : entries) {
                        if (!normalizeProviderName(name).equals(normalizeProviderName(entry.displayInfo))) continue;
                        // Carried necklaces may expose Rub rather than a direct destination.
                        // Preserve the selected provider/landing, but use its real item action
                        // and advance only that provider's destination dialogue on later ticks.
                        field(providerClass, "itemActionSupplier").set(provider, (Supplier<String>) () -> resolveAction(entry));
                        providers.put(provider, entry);
                        break;
                    }
                }
                for (String gateName : List.of("availability", "executionAvailability"))
                {
                    Field gate = field(providerClass, gateName);
                    BooleanSupplier original = (BooleanSupplier) gate.get(provider);
                    gate.set(provider, (BooleanSupplier) () -> original.getAsBoolean()
                        && originalProviderAllowed(name));
                }
            }
            providerListField.set(engine, mergedProviders);
            installed = true;
            LOG.info("[EfficientWalker] Dro teleport-item catalogue integrated: added={} preserved={} skippedExisting={} skippedComplex={}",
                added, originalProviders.size(), skippedExisting, skippedComplex);
        }
        catch (Throwable ex)
        {
            installed = false;
            entries.clear();
            LOG.warn("[EfficientWalker] full teleport-item catalogue integration unavailable; preserving v1.11.2 providers", ex);
        }
    }

    void refreshForPlanning()
    {
        if (!installed || entries.isEmpty())
        {
            return;
        }

        try
        {
            Microbot.getClientThread().runOnClientThreadOptional(() ->
            {
                for (CatalogEntry entry : entries)
                {
                    entry.eligible = requirementsPass(entry.transport) && allowed(entry.transport);
                }
                return Boolean.TRUE;
            });
        }
        catch (RuntimeException ex)
        {
            LOG.debug("[EfficientWalker] teleport catalogue requirement refresh unavailable: {}", ex.getMessage());
        }
    }

    List<WorldPoint> carriedLandings()
    {
        try (TravelEquipment.Scope ignored = TravelEquipment.planningScope()) {
            return carriedLandingsSnapshot();
        }
    }

    private List<WorldPoint> carriedLandingsSnapshot()
    {
        if (!installed || entries.isEmpty() || Rs2Player.isTeleBlocked())
        {
            return List.of();
        }
        LinkedHashSet<WorldPoint> landings = new LinkedHashSet<>();
        for (CatalogEntry entry : entries)
        {
            if (entry.eligible && hasCarriedItem(entry.itemIds)
                && entry.transport.getDestination() != null)
            {
                landings.add(entry.transport.getDestination());
            }
        }
        return List.copyOf(landings);
    }

    List<WorldPoint> availableLandings(WorldPoint start, WorldPoint target)
    {
        if (start == null || target == null) return List.of();
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            LinkedHashSet<WorldPoint> result = new LinkedHashSet<>();
            try
            {
                for (Object provider : (List<?>) field(engineClass, "teleportProviders").get(engine))
                {
                    java.lang.reflect.Method eligible = provider.getClass().getDeclaredMethod(
                        "isAvailableForRoute", WorldPoint.class, WorldPoint.class);
                    eligible.setAccessible(true);
                    if (!Boolean.TRUE.equals(eligible.invoke(provider, start, target))) continue;
                    java.lang.reflect.Method landing = provider.getClass().getDeclaredMethod("getDestination");
                    landing.setAccessible(true);
                    WorldPoint point = (WorldPoint) landing.invoke(provider);
                    if (point != null) result.add(point);
                }
            }
            catch (ReflectiveOperationException ex)
            {
                LOG.debug("[EfficientWalker] carried teleport snapshot unavailable", ex);
            }

            // The maintained catalogue also covers preserved providers (glories, dueling rings,
            // games necklaces, etc.).  Add their landing whenever the teleport item is either in
            // inventory OR worn. This is deliberately only a visibility fallback; execution still
            // belongs to the Efficient Walker provider.
            for (CatalogEntry entry : entries)
            {
                if (entry.eligible && hasCarriedItem(entry.itemIds)
                    && allowed(entry.transport) && entry.transport.getDestination() != null)
                {
                    result.add(entry.transport.getDestination());
                }
            }
            return List.copyOf(result);
        }).orElse(List.of());
    }

    int bestCarriedScore(WorldPoint start, WorldPoint target, ToIntFunction<WorldPoint> walkScore)
    {
        try (TravelEquipment.Scope ignored = TravelEquipment.planningScope()) {
            return bestCarriedScoreSnapshot(start, target, walkScore);
        }
    }

    private int bestCarriedScoreSnapshot(WorldPoint start, WorldPoint target, ToIntFunction<WorldPoint> walkScore)
    {
        if (!installed || start == null || target == null || walkScore == null
            || Rs2Player.isTeleBlocked())
        {
            return Integer.MAX_VALUE;
        }

        int best = Integer.MAX_VALUE;
        for (CatalogEntry entry : entries)
        {
            if (!entry.eligible || !hasCarriedItem(entry.itemIds))
            {
                continue;
            }
            int walk = walkScore.applyAsInt(entry.transport.getDestination());
            if (walk != Integer.MAX_VALUE)
            {
                best = Math.min(best, 10 + walk);
            }
        }
        return best;
    }

    static Spell ancientSpell(String name)
    {
        switch (name.toLowerCase(Locale.ROOT))
        {
            case "dareeyak tablet": return Rs2Spells.DAREEYAK_TELEPORT;
            case "carrallanger tablet": return Rs2Spells.CARRALLANGER_TELEPORT;
            case "annakarl tablet": return Rs2Spells.ANNAKARL_TELEPORT;
            case "ghorrock tablet": return Rs2Spells.GHORROCK_TELEPORT;
            default: return null;
        }
    }

    private static int ancientWidget(String name)
    {
        switch (name.toLowerCase(Locale.ROOT))
        {
            case "dareeyak tablet": return InterfaceID.MagicSpellbook.ZAROSTELEPORT5;
            case "carrallanger tablet": return InterfaceID.MagicSpellbook.ZAROSTELEPORT6;
            case "annakarl tablet": return InterfaceID.MagicSpellbook.ZAROSTELEPORT7;
            case "ghorrock tablet": return InterfaceID.MagicSpellbook.ZAROSTELEPORT8;
            default: return -1;
        }
    }

    private boolean originalProviderAllowed(String name)
    {
        // The planner's wilderness exclusion was removed, so enforce actual
        // departure limits here rather than letting it attempt an impossible teleport.
        String lower = name.toLowerCase(Locale.ROOT);
        int maximum = lower.contains("glory") || lower.contains("skills necklace")
            || lower.contains("combat bracelet") || lower.contains("wealth") ? 30 : 20;
        if (lower.contains("ferox") && !config.useFeroxTeleport()) return false;
        return departureAllowed(maximum);
    }

    private boolean allowed(Transport transport)
    {
        String name = Objects.toString(transport.getDisplayInfo(), "").toLowerCase(Locale.ROOT);
        if (!wildernessDestinationAllowed(name, transport.getDestination(), config)) return false;
        return departureAllowed(transport.getMaxWildernessLevel());
    }

    static boolean wildernessDestinationAllowed(String name, WorldPoint landing, EfficientWalkerConfig config)
    {
        if (name.toLowerCase(Locale.ROOT).contains("ferox")) return config.useFeroxTeleport();
        return landing == null || Rs2Pvp.getWildernessLevelFrom(landing) <= 0
            || (config.allowWildernessTravel() && config.useWildernessTeleports());
    }

    private boolean departureAllowed(int maximum)
    {
        WorldPoint location = Rs2Player.getWorldLocation();
        return location != null && !Rs2Player.isTeleBlocked()
            && (Rs2Pvp.getWildernessLevelFrom(location) <= 0
                || Rs2Pvp.getWildernessLevelFrom(location) <= maximum);
    }

    /** Advance only the selected teleport's dialogue, one action per game tick. */
    boolean handleDialogue()
    {
        try
        {
            CatalogEntry entry = providers.get(field(engineClass, "activeTeleportProvider").get(engine));
            if (entry == null || !allowed(entry.transport)) return false;
            if (Rs2Dialogue.hasSelectAnOption())
            {
                String destination = destinationAction(entry.displayInfo);
                if (destination != null && Rs2Dialogue.clickOption(destination, false)) return true;
                if (Rs2Pvp.getWildernessLevelFrom(entry.transport.getDestination()) > 0)
                {
                    return Rs2Dialogue.clickOption("Okay, teleport to level", false)
                        || Rs2Dialogue.clickOption("Yes", false)
                        || Rs2Dialogue.clickOption("Okay", false);
                }
            }
            if (Rs2Dialogue.hasContinue()) { Rs2Dialogue.clickContinue(); return true; }
        }
        catch (ReflectiveOperationException ex)
        {
            LOG.debug("[EfficientWalker] selected teleport dialogue unavailable", ex);
        }
        return false;
    }

    private Object activationProvider;
    private long activationUntil, nextActivationTab;

    void resetActivationReadiness(){activationProvider=null;}

    /** Every carried item teleport uses the validated inventory dispatcher. Open its
     * tab without a client-thread wait before the engine attempts activation. */
    boolean prepareItemActivation(long now) {
        try {
            Object provider=field(engineClass,"activeTeleportProvider").get(engine);
            if(provider==null){resetActivationReadiness();return false;}
            // Preserved providers (wealth, crystals, tablets, etc.) are not entries
            // in the added catalogue map. Read the engine's actual selected provider.
            if(field(provider.getClass(),"spell").get(provider)!=null)return false; // Spell path owns its UI.
            int[] itemIds=(int[])field(provider.getClass(),"itemIds").get(provider);
            Rs2ItemModel carried=null;
            for(int id:itemIds){carried=Rs2Inventory.get(id);if(carried!=null)break;}
            if(carried==null){resetActivationReadiness();return false;} // Worn-item path owns its UI.
            var inventory=client.getWidget(InterfaceID.Inventory.ITEMS);
            boolean visible=inventory!=null&&!inventory.isHidden();
            if(visible&&inventory.getChildren()!=null){
                for(var slot:inventory.getChildren())if(slot!=null&&!slot.isHidden()
                        &&slot.getItemId()==carried.getId()&&slot.getIndex()==carried.getSlot()){
                    java.awt.Rectangle bounds=slot.getBounds();
                    String[] actions=slot.getActions();
                    if(bounds==null||bounds.width<=0||bounds.height<=0
                            ||actions==null||Arrays.stream(actions).allMatch(Objects::isNull))break;
                    activationProvider=null;return false;
                }
            }
            if(activationProvider!=provider){activationProvider=provider;activationUntil=now+8000;nextActivationTab=0;}
            if(now>=activationUntil)return false; // Bounded readiness; never suppress a genuine failure forever.
            if(!visible&&now>=nextActivationTab){
                client.runScript(915,net.runelite.client.plugins.microbot.globval.enums.InterfaceTab.INVENTORY.getVarcIntIndex());
                nextActivationTab=now+1200;
            }
            return true;
        } catch(ReflectiveOperationException ex){return false;}
    }

    private final BurningAmuletLanding observedLanding = new BurningAmuletLanding();

    private Object preDispatchRequest;
    private WorldPoint preDispatchDeparture;
    private int preDispatchWorld, preDispatchTick;
    private long preDispatchEpoch;
    private Set<Integer> preDispatchItems = Set.of();

    void resetObservedLanding() {
        observedLanding.reset();
        preDispatchRequest = null;
        preDispatchDeparture = null;
        preDispatchItems = Set.of();
    }

    /** Called before the engine can dispatch; never re-arm from a post-teleport location. */
    void armObservedLanding(long requestEpoch)
    {
        try {
            preDispatchRequest = null;
            if (field(engineClass, "teleportAttempt").get(engine) != null) return;
            var player = client.getLocalPlayer();
            if (player == null || client.getGameState() != net.runelite.api.GameState.LOGGED_IN) {
                resetObservedLanding(); return;
            }
            preDispatchRequest = field(engineClass, "activeRequest").get(engine);
            preDispatchDeparture = player.getWorldLocation();
            preDispatchWorld = client.getWorld(); preDispatchTick = client.getTickCount();
            preDispatchEpoch = requestEpoch;
            preDispatchItems = new HashSet<>();
            for (int id : new int[]{21166, 21169, 21171, 21173, 21175})
                if (Rs2Inventory.hasItem(id) || TravelEquipment.isWearing(id)) preDispatchItems.add(id);
            Object provider = field(engineClass, "activeTeleportProvider").get(engine);
            if (provider == null) { observedLanding.reset(); return; }
            int[] ids = (int[]) field(provider.getClass(), "itemIds").get(provider);
            boolean charged = Arrays.stream(ids).anyMatch(id -> BurningAmuletLanding.charged(id)
                && (Rs2Inventory.hasItem(id) || TravelEquipment.isWearing(id)));
            observedLanding.arm(requestEpoch, field(engineClass, "activeRequest").get(engine), provider,
                client.getWorld(), client.getTickCount(), player.getWorldLocation(),
                (WorldPoint) field(engineClass, "expectedTeleportDestination").get(engine), charged);
        } catch (ReflectiveOperationException ex) { observedLanding.reset(); }
    }

    /** Bind a provider selected and dispatched within the tick to its earlier departure snapshot. */
    void recordObservedDispatch(long requestEpoch)
    {
        try {
            if (preDispatchRequest == null || preDispatchEpoch != requestEpoch || preDispatchWorld != client.getWorld()
                || preDispatchRequest != field(engineClass, "activeRequest").get(engine)
                || field(engineClass, "teleportAttempt").get(engine) == null) return;
            Object provider = field(engineClass, "activeTeleportProvider").get(engine);
            if (provider == null) return;
            int[] ids = (int[]) field(provider.getClass(), "itemIds").get(provider);
            observedLanding.arm(requestEpoch, preDispatchRequest, provider, preDispatchWorld, preDispatchTick,
                preDispatchDeparture, (WorldPoint) field(engineClass, "expectedTeleportDestination").get(engine),
                Arrays.stream(ids).anyMatch(preDispatchItems::contains));
        } catch (ReflectiveOperationException ex) { observedLanding.reset(); }
        finally { preDispatchRequest = null; preDispatchItems = Set.of(); }
    }

    boolean confirmObservedLanding(long requestEpoch)
    {
        try {
            Object request = field(engineClass, "activeRequest").get(engine);
            var player = client.getLocalPlayer();
            if (!observedLanding.observe(requestEpoch, request,
                field(engineClass, "activeTeleportProvider").get(engine),
                field(engineClass, "teleportAttempt").get(engine), client.getWorld(), client.getTickCount(),
                (WorldPoint) field(engineClass, "expectedTeleportDestination").get(engine),
                player == null ? null : player.getWorldLocation(),
                client.getGameState() == net.runelite.api.GameState.LOGGED_IN,
                "WALKING".equals(String.valueOf(field(engineClass, "status").get(engine))),
                player != null && player.getAnimation() == -1 && player.getPoseAnimation() == player.getIdlePoseAnimation()))
                return false;
            var finish = engineClass.getDeclaredMethod("finishTeleportAndReplan", request.getClass(), net.runelite.api.Player.class);
            finish.setAccessible(true);
            finish.invoke(engine, request, player);
            observedLanding.reset();
            return true;
        } catch (ReflectiveOperationException ex) {
            observedLanding.reset();
            LOG.debug("[EfficientWalker] burning amulet landing confirmation unavailable", ex);
            return false;
        }
    }

    boolean supportsSelectedProvider(Object provider, net.runelite.client.plugins.microbot.droslayer.walker.support.Rs2TransportEdge selected)
    {
        CatalogEntry entry = providers.get(provider);
        if (entry != null) return NetworkTransportSupport.sameTransition(selected, entry.transport);
        try {
            String name = String.valueOf(field(provider.getClass(), "name").get(provider));
            int[] ids = (int[]) field(provider.getClass(), "itemIds").get(provider);
            return entries.stream().anyMatch(candidate -> NetworkTransportSupport.sameTransition(selected, candidate.transport)
                && normalizeProviderName(name).equals(normalizeProviderName(candidate.displayInfo))
                && Arrays.stream(ids).anyMatch(id -> Arrays.stream(candidate.itemIds).anyMatch(candidateId -> candidateId == id)));
        } catch (ReflectiveOperationException ex) { return false; }
    }

    boolean supportsSelectedEdge(net.runelite.client.plugins.microbot.droslayer.walker.support.Rs2TransportEdge selected)
    {
        return entries.stream().anyMatch(entry -> NetworkTransportSupport.sameTransition(selected, entry.transport));
    }

    /** Explains bank-provider rejection using the same snapshot that gates selection. */
    String requirementDiagnostic(Object provider)
    {
        CatalogEntry entry = providers.get(provider);
        if (entry == null) return "preserved";
        StringBuilder detail = new StringBuilder("eligible=" + entry.eligible);
        for (var gate : entry.transport.getVarbits())
            detail.append(" varbit=").append(gate.getVarbitId())
                .append(" value=").append(Microbot.getVarbitValue(gate.getVarbitId()))
                .append(" passes=").append(gate.matches(Microbot.getVarbitValue(gate.getVarbitId())));
        return detail.toString();
    }

    private boolean requirementsPass(Transport transport)
    {
        if (transport.isMembers() && !client.getWorldType().contains(WorldType.MEMBERS))
        {
            return false;
        }

        int[] levels = transport.getSkillLevels();
        Skill[] skills = Skill.values();
        for (int i = 0; levels != null && i < levels.length && i < skills.length; i++)
        {
            if (levels[i] > 0 && client.getBoostedSkillLevel(skills[i]) < levels[i])
            {
                return false;
            }
        }

        for (Map.Entry<net.runelite.api.Quest, QuestState> requirement : transport.getQuests().entrySet())
        {
            if (!questStateAtLeast(Rs2Player.getQuestState(requirement.getKey()), requirement.getValue()))
            {
                return false;
            }
        }

        for (var requirement : transport.getVarbits())
        {
            if (!requirement.matches(Microbot.getVarbitValue(requirement.getVarbitId())))
            {
                return false;
            }
        }

        for (var requirement : transport.getVarplayers())
        {
            if (!requirement.matches(Microbot.getVarbitPlayerValue(requirement.getVarplayerId())))
            {
                return false;
            }
        }
        return true;
    }

    private static boolean questStateAtLeast(QuestState actual, QuestState required)
    {
        if (required == null)
        {
            return true;
        }
        return questRank(actual) >= questRank(required);
    }

    private static int questRank(QuestState state)
    {
        if (state == QuestState.FINISHED)
        {
            return 2;
        }
        if (state == QuestState.IN_PROGRESS)
        {
            return 1;
        }
        return 0;
    }

    private boolean hasCarriedItem(int[] itemIds)
    {
        for (int itemId : itemIds)
        {
            if (Rs2Inventory.hasItem(itemId) || TravelEquipment.isWearing(itemId))
            {
                return true;
            }
        }
        return false;
    }

    private String resolveAction(CatalogEntry entry)
    {
        if (!client.isClientThread())
        {
            return Microbot.getClientThread().runOnClientThreadOptional(() -> resolveActionOnClient(entry))
                .orElse(null);
        }
        return resolveActionOnClient(entry);
    }

    private String resolveActionOnClient(CatalogEntry entry)
    {
        String destinationAction = destinationAction(entry.displayInfo);
        for (int itemId : entry.itemIds)
        {
            Rs2ItemModel item = Rs2Inventory.get(itemId);
            if (item == null)
            {
                if (TravelEquipment.isWearing(itemId) && destinationAction != null) return destinationAction;
                continue;
            }

            if (destinationAction != null)
            {
                String direct = item.getAction(destinationAction.toLowerCase(Locale.ROOT));
                if (direct != null)
                {
                    return direct;
                }
                Map.Entry<String, Integer> subAction = item.getIndexOfSubAction(destinationAction);
                if (subAction != null && subAction.getKey() != null)
                {
                    return destinationAction;
                }
            }

            String generic = item.getActionFromList(GENERIC_ITEM_ACTIONS);
            if (generic != null)
            {
                return generic;
            }
        }
        return null;
    }

    private static String destinationAction(String displayInfo)
    {
        if (displayInfo == null || displayInfo.isBlank())
        {
            return null;
        }
        int colon = displayInfo.lastIndexOf(':');
        if (colon < 0 || colon >= displayInfo.length() - 1)
        {
            return null;
        }
        return displayInfo.substring(colon + 1).trim().toLowerCase(Locale.ROOT);
    }

    private static boolean alreadyCovered(List<ExistingProvider> existing, String displayInfo, int[] itemIds)
    {
        String normalized = normalizeProviderName(displayInfo);
        for (ExistingProvider provider : existing)
        {
            if (!normalized.equals(normalizeProviderName(provider.name)))
            {
                continue;
            }
            for (int id : itemIds)
            {
                for (int existingId : provider.itemIds)
                {
                    if (id == existingId)
                    {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static String normalizeProviderName(String value)
    {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace("'", "");
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException
    {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static final class CatalogEntry
    {
        private final Transport transport;
        private final int[] itemIds;
        private final String displayInfo;
        private volatile boolean eligible;

        private CatalogEntry(Transport transport, int[] itemIds, String displayInfo)
        {
            this.transport = transport;
            this.itemIds = itemIds.clone();
            this.displayInfo = displayInfo;
        }
    }

    private static final class ExistingProvider
    {
        private final String name;
        private final int[] itemIds;

        private ExistingProvider(String name, int[] itemIds)
        {
            this.name = name;
            this.itemIds = itemIds == null ? new int[0] : itemIds.clone();
        }
    }
}
