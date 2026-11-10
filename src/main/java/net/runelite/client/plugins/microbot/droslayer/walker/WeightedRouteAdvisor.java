package net.runelite.client.plugins.microbot.droslayer.walker;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.droslayer.walker.support.*;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;

import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * Read-only route-cost advisor for the Efficient Walker engine.
 *
 * <p>Execution always remains with Efficient Walker. This class deliberately does not call
 * {@code Rs2Walker.walkTo}, {@code Rs2Walker.compareRoutes}, or any Rs2Walker transport executor.
 * It uses only Microbot's typed {@link Rs2PathApi} as a route-graph source, then independently
 * scores the exact selected steps. That gives Efficient Walker visibility of complete multimodal
 * chains such as item teleport -> Spirit Tree -> charter -> walk, without delegating execution to
 * the walker this plugin exists to replace.</p>
 *
 * <p>The maintained shortest-path data already assigns durations to transports. Grouping/minigame
 * teleports are the deliberate exception here: the resource currently prices them at 23, while the
 * activation/completion allowance is expressed in the same half-tick units as running. We therefore
 * floor every minigame edge at {@link #MINIGAME_FIXED_TILE_COST} for comparisons.</p>
 */
final class WeightedRouteAdvisor
{
    static final int MINIGAME_FIXED_TILE_COST = RouteTimeCost.MINIGAME;

    // Bank detours have real interface time even when the bank is already nearby. This is a
    // TIME penalty only. Item value, GE price, charge value and GP cost are deliberately absent
    // from route scoring: EfficientWalker optimizes travel time/distance, not economics.
    private static final int BANK_FIXED_TILE_COST = 12;
    private static final int NATIVE_TELEPORT_TILE_COST = 10;
    private static final int INF = Integer.MAX_VALUE / 4;

    private final CatalogTeleportSupport catalogTeleportSupport;
    private final Function<Rs2RouteRequest, Rs2RouteResult> planner;
    private final Function<WorldPoint, WorldPoint> bankResolver;
    private java.util.function.Consumer<String> diagnostic = message -> { };

    WeightedRouteAdvisor withDiagnostics(java.util.function.Consumer<String> diagnostic)
    {
        this.diagnostic = diagnostic;
        return this;
    }


    WeightedRouteAdvisor(CatalogTeleportSupport catalogTeleportSupport)
    {
        this(catalogTeleportSupport, Rs2PathApi::plan);
    }

    WeightedRouteAdvisor(CatalogTeleportSupport catalogTeleportSupport,
                         Function<Rs2RouteRequest, Rs2RouteResult> planner)
    {
        this(catalogTeleportSupport, planner, null);
    }

    WeightedRouteAdvisor(CatalogTeleportSupport catalogTeleportSupport,
                         Function<Rs2RouteRequest, Rs2RouteResult> planner,
                         Function<WorldPoint, WorldPoint> bankResolver)
    {
        this.catalogTeleportSupport = catalogTeleportSupport;
        this.planner = planner;
        this.bankResolver = bankResolver;
    }

    private WorldPoint nearbyBank(SearchSession session, WorldPoint start)
    {
        // Resolve only a small local shortlist under the same search budget as the
        // comparison. Rs2Bank.getNearestBank performs a separate global search and
        // can select a teleport/boat bank before considering a nearby walkable one.
        Map<WorldPoint, java.util.function.BooleanSupplier> banks = new LinkedHashMap<>();
        for (BankLocation bank : BankLocation.values()) banks.put(bank.getWorldPoint(), bank::hasRequirements);
        // Live-confirmed standing tiles, pending inclusion in the shared bank catalogue.
        banks.put(new WorldPoint(3039,2999,0), () -> net.runelite.api.QuestState.FINISHED.equals(
            net.runelite.client.plugins.microbot.util.player.Rs2Player.getQuestState(net.runelite.api.Quest.PANDEMONIUM)));
        banks.put(new WorldPoint(3181,2419,0), () -> net.runelite.api.QuestState.FINISHED.equals(
            net.runelite.client.plugins.microbot.util.player.Rs2Player.getQuestState(net.runelite.api.Quest.TROUBLED_TORTUGANS)));
        List<WorldPoint> candidates = new ArrayList<>();
        for (WorldPoint point : banks.keySet())
            if (point.getPlane() == start.getPlane() && point.distanceTo2D(start) <= 512) candidates.add(point);
        candidates.sort(Comparator.comparingInt(point -> point.distanceTo2D(start)));
        WorldPoint best = null;
        int bestCost = INF;
        int attempts = 0;
        for (WorldPoint bank : candidates)
        {
            checkCancellation();
            if (!Microbot.getClientThread().invoke((java.util.function.Supplier<Boolean>) banks.get(bank)::getAsBoolean)) continue;
            if (attempts++ >= 4) break;
            int cost = completedCost(plan(session, start, bank, false), bank);
            if (cost < bestCost)
            {
                bestCost = cost;
                best = bank;
            }
        }
        return best;
    }

    Advice analyze(WorldPoint start, WorldPoint target, boolean allowBank)
    {
        return analyze(start, target, allowBank, List.of());
    }

    Advice analyze(WorldPoint start, WorldPoint target, boolean allowBank,
                   List<BankedTeleportOption> bankedTeleportOptions)
    {
        return analyze(start, target, allowBank, bankedTeleportOptions, false);
    }

    Advice analyze(WorldPoint start, WorldPoint target, boolean allowBank,
                   List<BankedTeleportOption> bankedTeleportOptions, boolean directUnavailable)
    {
        return analyze(start, target, allowBank, bankedTeleportOptions, directUnavailable, null);
    }

    Advice analyze(WorldPoint start, WorldPoint target, boolean allowBank,
                   List<BankedTeleportOption> bankedTeleportOptions, boolean directUnavailable,
                   WorldPoint nearbyBank)
    {
        if (start == null || target == null)
        {
            return Advice.unavailable(target);
        }

        SearchSession session = new SearchSession(planner, System::nanoTime);
        try
        {
            diagnostic.accept("comparison-start target=" + target + " bankCandidates="
                + (bankedTeleportOptions == null ? 0 : bankedTeleportOptions.size()));
            Rs2RouteResult directRoute = directUnavailable ? null : plan(session, start, target, false);
            int plannerDirectCost = completedCost(directRoute, target);
            int directCost = plannerDirectCost;
            CompleteRouteDiscovery.Choice directChoice = plannerDirectCost < INF
                ? new CompleteRouteDiscovery.Choice("carried complete route", directRoute, null,
                    Rs2TransportLoadout.empty(), plannerDirectCost)
                : null;
            boolean plannerDirectUsesMinigame = usesType(
                directRoute == null ? null : directRoute.getSteps(),
                Rs2TransportType.TELEPORTATION_MINIGAME);
            boolean directUsesMinigame = plannerDirectUsesMinigame;
            String directChain = directRoute == null ? "" : chain(directRoute.getSteps());

            // If the typed planner selected a 23-cost Grouping teleport, explicitly give every
            // carried EfficientWalker teleport a second chance. The suffix is then planned with
            // the full public transport graph, so GE -> Spirit Tree, Port Sarim -> ship, etc. are
            // scored as one route instead of measuring only the teleport landing -> target walk.
            if (!directUnavailable && (plannerDirectUsesMinigame || directCost >= INF))
            {
                CarriedCandidate carried = bestCarriedTeleportChain(session, start, target);
                if (carried.cost < directCost)
                {
                    directCost = carried.cost;
                    directUsesMinigame = carried.usesMinigame;
                    directChain = carried.chain;
                    directChoice = carried.choice;
                }
            }

            int bankCost = INF;
            String bankChain = "";
            boolean bankUsesMinigame = false;
            boolean bankUsesFairyRing = false;
            BankedTeleportOption bestExplicitBankedTeleport = null;
            int explicitBankedTeleportCost = INF;
            WorldPoint bank = null;
            List<Rs2TransportEdge> bankTransportEdges = List.of();

            if (allowBank)
            {
                checkCancellation();
                bank = nearbyBank != null ? nearbyBank
                    : bankResolver == null ? nearbyBank(session, start) : bankResolver.apply(start);
                if (bank != null)
                {
                    Rs2RouteResult toBank = start.equals(bank) ? null : plan(session, start, bank, false);
                    int toBankCost = start.equals(bank) ? 0 : completedCost(toBank, bank);
                    // Missing carried fare must not suppress the bank-funded leg. First prove
                    // the bank is reachable with current supplies, then plan using bank supplies.
                    Rs2RouteResult fromBank = toBankCost < INF ? plan(session, bank, target, true) : null;
                    int fromBankCost = completedCost(fromBank, target);
                    if (toBankCost < INF && fromBankCost < INF)
                    {
                        bankTransportEdges = TransportSupplyPlanner.edges(fromBank);
                        bankUsesMinigame = usesType(toBank == null ? null : toBank.getSteps(), Rs2TransportType.TELEPORTATION_MINIGAME)
                            || usesType(fromBank.getSteps(), Rs2TransportType.TELEPORTATION_MINIGAME);
                        bankUsesFairyRing = usesType(fromBank.getSteps(), Rs2TransportType.FAIRY_RING);
                        bankCost = clamp((long) toBankCost + fromBankCost + BANK_FIXED_TILE_COST);
                        bankChain = "bank " + bank.toString()
                            + " -> " + chain(fromBank.getSteps());
                    }

                    // Independently score every provider the engine says can be supplied
                    // by the validated bank mirror. This bypasses the old v1.11.2 "must save 25
                    // tiles and 20%" admission threshold without replacing its provider/executor
                    // semantics. Economics are intentionally ignored: only bank interaction time,
                    // teleport time, transport durations and walking distance count.
                    if (toBankCost < INF && bankedTeleportOptions != null)
                    {
                        List<BankedTeleportOption> orderedOptions = new ArrayList<>(bankedTeleportOptions);
                        orderedOptions.removeIf(option -> option == null || option.landing == null);
                        // Prioritize promising landings; this is ordering only, never a reachability test.
                        orderedOptions.sort(Comparator.comparingInt(option -> teleportSearchDistance(option.landing, target)));
                        for (BankedTeleportOption option : orderedOptions)
                        {
                            if (option == null || option.landing == null || option.requirements.isEmpty())
                            {
                                continue;
                            }
                            diagnostic.accept("candidate-start name=" + option.name + " landing=" + option.landing);
                            Rs2RouteResult suffix = plan(session, option.landing, target, true);
                            int suffixCost = completedCost(suffix, target);
                            if (suffixCost >= INF)
                            {
                                diagnostic.accept("candidate-unscored name=" + option.name + " reason="
                                    + (suffix != null ? suffix.getTerminationReason()
                                        : session.routes.containsKey(List.of(option.landing, target, true))
                                            ? "planner-returned-no-result" : "search-budget-exhausted"));
                                continue;
                            }

                            int candidateCost = clamp((long) toBankCost
                                + BANK_FIXED_TILE_COST
                                + 4L * option.requirements.size()
                                + NATIVE_TELEPORT_TILE_COST
                                + suffixCost);
                            diagnostic.accept("candidate-scored name=" + option.name + " total=" + candidateCost
                                + " bankApproach=" + toBankCost + " suffix=" + suffixCost
                                + " direct=" + directCost + " previousBestBank=" + bankCost);
                            boolean suffixUsesMinigame = usesType(
                                suffix.getSteps(), Rs2TransportType.TELEPORTATION_MINIGAME);
                            boolean suffixUsesFairyRing = usesType(
                                suffix.getSteps(), Rs2TransportType.FAIRY_RING);
                            option = option.withScoredRoute(candidateCost, suffixUsesMinigame,
                                suffixUsesFairyRing, chain(suffix.getSteps()));

                            if (candidateCost < explicitBankedTeleportCost)
                            {
                                explicitBankedTeleportCost = candidateCost;
                                bestExplicitBankedTeleport = option;
                            }
                            if (candidateCost < bankCost)
                            {
                                bankCost = candidateCost;
                                bankUsesMinigame = suffixUsesMinigame;
                                bankUsesFairyRing = suffixUsesFairyRing;
                                bankChain = "bank " + bank.toString()
                                    + " -> " + option.name + " -> " + chain(suffix.getSteps());
                            }
                            // Recovery needs a verified executable route, not an exhaustive optimum.
                            if (directUnavailable && bestExplicitBankedTeleport != null) break;
                        }
                    }
                }
            }

            boolean directComparisonKnown = directUnavailable || directCost < INF
                || (directRoute != null && directRoute.getTerminationReason()
                    == Rs2RouteTermination.SEARCH_EXHAUSTED);
            boolean bankPreferred = directComparisonKnown && bankCost < directCost;
            int bestCost = Math.min(directCost, bankCost);
            String bestChain = bankPreferred ? bankChain : directChain;
            // Only expose an explicit banked teleport when it is the actual bank winner.
            // A Fairy Ring/charter/etc. may legitimately be even faster; in that case do not
            // force a slower item teleport merely because it still beats the direct route.
            BankedTeleportOption explicitWinner = directComparisonKnown && bestExplicitBankedTeleport != null
                && explicitBankedTeleportCost <= bankCost
                && explicitBankedTeleportCost < directCost
                ? bestExplicitBankedTeleport : null;
            diagnostic.accept("comparison-end target=" + target + " direct=" + directCost + " bank=" + bankCost
                + " preferred=" + (bankPreferred ? "bank" : "direct") + " chain=" + bestChain);
            return new Advice(target, directCost, bankCost, bestCost, bankPreferred,
                directUsesMinigame, bankUsesMinigame, bankUsesFairyRing,
                directChain, bankChain, bestChain, explicitWinner, explicitBankedTeleportCost,
                bank, explicitWinner == null ? bankTransportEdges : List.of()).withDirectChoice(directChoice);
        }
        catch (RuntimeException ex)
        {
            diagnostic.accept("comparison-aborted target=" + target + " exception=" + ex.getClass().getSimpleName());
            return Advice.unavailable(target);
        }
    }

    static int teleportSearchDistance(WorldPoint landing, WorldPoint target)
    {
        int distance = landing.distanceTo2D(target);
        // Most mainland caves use a +6400 Y map offset. This only orders candidates:
        // every candidate must still pass a complete route search with real requirements.
        if (target.getY() >= 9000 && landing.getY() < 6400)
            distance = Math.min(distance, landing.distanceTo2D(
                    new WorldPoint(target.getX(), target.getY() - 6400, landing.getPlane())));
        return distance;
    }

    private Rs2RouteResult plan(SearchSession session, WorldPoint start, WorldPoint target,
                               boolean useBankItems)
    {
        return session.plan(start, target, useBankItems);
    }

    /** Request-local memoization: never reuse routes across inventory, account or target changes. */
    static final class SearchSession
    {
        static final long BUDGET_NANOS = 10_000_000_000L;
        static final int MAX_SEARCHES = 16;
        private final Function<Rs2RouteRequest, Rs2RouteResult> planner;
        private final LongSupplier clock;
        private final long started;
        private final Map<List<Object>, Rs2RouteResult> routes = new HashMap<>();

        SearchSession(Function<Rs2RouteRequest, Rs2RouteResult> planner, LongSupplier clock)
        {
            this.planner = planner;
            this.clock = clock;
            this.started = clock.getAsLong();
        }

        Rs2RouteResult plan(WorldPoint start, WorldPoint target, boolean useBankItems)
        {
            checkCancellation();
            List<Object> key = List.of(start, target, useBankItems);
            if (routes.containsKey(key)) return routes.get(key);
            // A single in-flight search is canceled by the coordinator's tick deadline.
            // This deadline stops the multiplicative cost of dozens of further searches.
            if (routes.size() >= MAX_SEARCHES || clock.getAsLong() - started >= BUDGET_NANOS)
                return null;
            Rs2RouteResult result;
            try
            {
                result = planner.apply(Rs2RouteRequest.to(start, target)
                    .withRefreshTarget(target).withBankItems(useBankItems));
            }
            catch (CancellationException ex)
            {
                throw ex;
            }
            catch (RuntimeException ex)
            {
                result = null;
            }
            checkCancellation();
            routes.put(key, result);
            return result;
        }
    }

    private static void checkCancellation()
    {
        if (Thread.currentThread().isInterrupted())
            throw new CancellationException("Route comparison canceled");
    }

    private CarriedCandidate bestCarriedTeleportChain(SearchSession session, WorldPoint start, WorldPoint target)
    {
        checkCancellation();
        CarriedCandidate best = CarriedCandidate.unavailable();

        try
        {
            List<WorldPoint> landings = Microbot.getClientThread().invoke(() ->
            {
                List<WorldPoint> result = new ArrayList<>();
                for (TeleportProviders.TeleportProvider provider : TeleportProviders.createProviders())
                {
                    WorldPoint landing = provider.getDestination();
                    if (landing != null && provider.isAvailableForRoute(start, target))
                    {
                        result.add(landing);
                    }
                }
                return result;
            });

            for (WorldPoint landing : landings)
            {
                Rs2RouteResult suffix = plan(session, landing, target, false);
                int suffixCost = completedCost(suffix, target);
                if (suffixCost >= INF)
                {
                    continue;
                }
                int cost = clamp((long) NATIVE_TELEPORT_TILE_COST + suffixCost);
                if (cost < best.cost)
                {
                    boolean suffixUsesMinigame = usesType(
                        suffix.getSteps(), Rs2TransportType.TELEPORTATION_MINIGAME);
                    best = new CarriedCandidate(cost, suffixUsesMinigame,
                        "carried teleport -> " + chain(suffix.getSteps()), suffix, landing);
                }
            }
        }
        catch (RuntimeException ex)
        {
            // Fail open. The planner remains authoritative if this optional advisor
            // cannot snapshot native teleport availability on this client build.
        }

        if (catalogTeleportSupport != null)
        {
            for (WorldPoint landing : catalogTeleportSupport.carriedLandings())
            {
                Rs2RouteResult suffix = plan(session, landing, target, false);
                int suffixCost = completedCost(suffix, target);
                if (suffixCost >= INF)
                {
                    continue;
                }
                int cost = clamp((long) NATIVE_TELEPORT_TILE_COST + suffixCost);
                if (cost < best.cost)
                {
                    boolean suffixUsesMinigame = usesType(
                        suffix.getSteps(), Rs2TransportType.TELEPORTATION_MINIGAME);
                    best = new CarriedCandidate(cost, suffixUsesMinigame,
                        "catalogue item teleport -> " + chain(suffix.getSteps()), suffix, landing);
                }
            }
        }
        return best;
    }

    private static int completedCost(Rs2RouteResult route, WorldPoint target)
    {
        if (route == null || target == null || !route.isTargetReached(0))
        {
            return INF;
        }
        return routeCost(route.getSteps());
    }

    static int routeCost(List<Rs2RouteStep> steps)
    {
        if (steps == null)
        {
            return INF;
        }

        long total = 0L;
        for (Rs2RouteStep step : steps)
        {
            if (step == null)
            {
                continue;
            }
            if (!step.isTransport())
            {
                WorldPoint from = step.getFrom();
                WorldPoint to = step.getTo();
                if (from == null || to == null || from.getPlane() != to.getPlane())
                {
                    total += 1;
                }
                else
                {
                    total += Math.max(
                        Math.abs(from.getX() - to.getX()),
                        Math.abs(from.getY() - to.getY()));
                }
                continue;
            }

            Rs2TransportEdge transport = step.getTransport().orElse(null);
            if (transport == null)
            {
                return INF;
            }
            int duration = RouteTimeCost.transport(transport.getType().name(), transport.getDuration());
            if (transport.getType() == Rs2TransportType.TELEPORTATION_MINIGAME)
            {
                duration = Math.max(duration, MINIGAME_FIXED_TILE_COST);
            }
            total += duration;
        }
        return clamp(total);
    }

    private static boolean usesType(List<Rs2RouteStep> steps, Rs2TransportType type)
    {
        if (steps == null)
        {
            return false;
        }
        for (Rs2RouteStep step : steps)
        {
            if (step == null || !step.isTransport())
            {
                continue;
            }
            Rs2TransportEdge edge = step.getTransport().orElse(null);
            if (edge != null && edge.getType() == type)
            {
                return true;
            }
        }
        return false;
    }

    private static String chain(List<Rs2RouteStep> steps)
    {
        if (steps == null || steps.isEmpty())
        {
            return "walk";
        }

        List<String> parts = new ArrayList<>();
        int walking = 0;
        for (Rs2RouteStep step : steps)
        {
            if (step == null)
            {
                continue;
            }
            if (!step.isTransport())
            {
                walking++;
                continue;
            }
            if (walking > 0)
            {
                parts.add("walk " + walking);
                walking = 0;
            }
            Rs2TransportEdge edge = step.getTransport().orElse(null);
            if (edge != null)
            {
                String label = edge.getDisplayInfo();
                if (label == null || label.isBlank())
                {
                    label = edge.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
                }
                parts.add(label);
            }
        }
        if (walking > 0)
        {
            parts.add("walk " + walking);
        }
        if (parts.isEmpty())
        {
            return "walk";
        }
        StringJoiner joiner = new StringJoiner(" -> ");
        parts.forEach(joiner::add);
        return joiner.toString();
    }

    private static int clamp(long value)
    {
        if (value < 0 || value >= INF)
        {
            return INF;
        }
        return (int) value;
    }

    static final class BankedTeleportOption
    {
        final String name;
        final WorldPoint landing;
        final Map<Integer, Integer> requirements;
        final int withdrawalTimeCost;
        final int scoredCost;
        final boolean suffixUsesMinigame;
        final boolean suffixUsesFairyRing;
        final String suffixChain;

        BankedTeleportOption(String name, WorldPoint landing,
                             Map<Integer, Integer> requirements, int withdrawalTimeCost)
        {
            this(name, landing, requirements, withdrawalTimeCost, INF, false, false, "");
        }

        private BankedTeleportOption(String name, WorldPoint landing,
                                     Map<Integer, Integer> requirements, int withdrawalTimeCost,
                                     int scoredCost, boolean suffixUsesMinigame,
                                     boolean suffixUsesFairyRing, String suffixChain)
        {
            this.name = name == null || name.isBlank() ? "banked teleport" : name;
            this.landing = landing;
            this.requirements = requirements == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(requirements));
            this.withdrawalTimeCost = Math.max(1, withdrawalTimeCost);
            this.scoredCost = scoredCost;
            this.suffixUsesMinigame = suffixUsesMinigame;
            this.suffixUsesFairyRing = suffixUsesFairyRing;
            this.suffixChain = suffixChain == null ? "" : suffixChain;
        }

        private BankedTeleportOption withScoredRoute(int cost, boolean usesMinigame,
                                                      boolean usesFairyRing, String chain)
        {
            return new BankedTeleportOption(name, landing, requirements, withdrawalTimeCost,
                cost, usesMinigame, usesFairyRing, chain);
        }
    }

    private static final class CarriedCandidate
    {
        final int cost;
        final boolean usesMinigame;
        final String chain;

        final CompleteRouteDiscovery.Choice choice;
        private CarriedCandidate(int cost, boolean usesMinigame, String chain, Rs2RouteResult route, WorldPoint landing)
        {
            this.cost = cost;
            this.usesMinigame = usesMinigame;
            this.chain = chain == null ? "" : chain;
            this.choice = route == null ? null : new CompleteRouteDiscovery.Choice(this.chain, route,
                new BankedTeleportOption("carried teleport", landing, Map.of(), 0),
                Rs2TransportLoadout.empty(), cost);
        }

        static CarriedCandidate unavailable()
        {
            return new CarriedCandidate(INF, false, "", null, null);
        }
    }

    static final class Advice
    {
        CompleteRouteDiscovery.Choice directChoice;
        Advice withDirectChoice(CompleteRouteDiscovery.Choice choice) { this.directChoice=choice; return this; }
        final WorldPoint target;
        final WorldPoint bankPoint;
        final List<Rs2TransportEdge> bankTransportEdges;
        final int directCost;
        final int bankCost;
        final int bestCost;
        final boolean bankPreferred;
        final boolean directUsesMinigame;
        final boolean bankUsesMinigame;
        final boolean bankUsesFairyRing;
        final String directChain;
        final String bankChain;
        final String bestChain;
        final BankedTeleportOption explicitBankedTeleport;
        final int explicitBankedTeleportCost;

        private Advice(WorldPoint target, int directCost, int bankCost, int bestCost,
                       boolean bankPreferred, boolean directUsesMinigame,
                       boolean bankUsesMinigame, boolean bankUsesFairyRing,
                       String directChain, String bankChain, String bestChain,
                       BankedTeleportOption explicitBankedTeleport, int explicitBankedTeleportCost,
                       WorldPoint bankPoint, List<Rs2TransportEdge> bankTransportEdges)
        {
            this.target = target;
            this.bankPoint = bankPoint;
            this.bankTransportEdges = List.copyOf(bankTransportEdges);
            this.directCost = directCost;
            this.bankCost = bankCost;
            this.bestCost = bestCost;
            this.bankPreferred = bankPreferred;
            this.directUsesMinigame = directUsesMinigame;
            this.bankUsesMinigame = bankUsesMinigame;
            this.bankUsesFairyRing = bankUsesFairyRing;
            this.directChain = directChain == null ? "" : directChain;
            this.bankChain = bankChain == null ? "" : bankChain;
            this.bestChain = bestChain == null ? "" : bestChain;
            this.explicitBankedTeleport = explicitBankedTeleport;
            this.explicitBankedTeleportCost = explicitBankedTeleportCost;
        }

        static Advice unavailable(WorldPoint target)
        {
            return new Advice(target, INF, INF, INF, false,
                false, false, false, "", "", "", null, INF, null, List.of());
        }

        boolean available()
        {
            return bestCost < INF;
        }
    }
}
