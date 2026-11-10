package net.runelite.client.plugins.microbot.droslayer.walker;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.droslayer.walker.support.*;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;

/** Read-only comparison. It has no bank, movement, equipment or route-commit operations. */
final class CompleteRouteObserver
{
    private final Function<Rs2RouteRequest, Rs2RouteResult> planner;
    private final BiFunction<List<Rs2TransportEdge>, Map<Integer, Integer>, Rs2TransportLoadout> preparation;
    private final Consumer<String> log;
    private final LongSupplier clock;
    private final Map<List<Object>, Rs2RouteResult> cache = new HashMap<>();
    private BiFunction<WorldPoint, WorldPoint, Integer> priority = WeightedRouteAdvisor::teleportSearchDistance;

    CompleteRouteObserver withPriority(BiFunction<WorldPoint, WorldPoint, Integer> priority)
    {
        this.priority = priority;
        return this;
    }

    private long deadline;
    private int best = Integer.MAX_VALUE;
    private String winner = "none";

    CompleteRouteObserver(Function<Rs2RouteRequest, Rs2RouteResult> planner,
        BiFunction<List<Rs2TransportEdge>, Map<Integer, Integer>, Rs2TransportLoadout> preparation,
        Consumer<String> log, LongSupplier clock)
    {
        this.planner = planner;
        this.preparation = preparation;
        this.log = log;
        this.clock = clock;
    }

    void compare(WorldPoint start, WorldPoint target,
                 List<WeightedRouteAdvisor.BankedTeleportOption> options)
    {
        deadline = clock.getAsLong() + 4_000_000_000L;
        log.accept("start target=" + target + " mode=observe-only candidates=" + options.size());
        evaluate("carried route", start, target, false, Map.of(), 0);
        // Hypothetical bank supplies admit the entire chain, including a staff, fare,
        // or runes. The exact returned edges determine the preparation requirements.
        evaluate("complete bank route", start, target, true, Map.of(), 0);
        List<WeightedRouteAdvisor.BankedTeleportOption> ordered = new ArrayList<>(options);
        Map<WorldPoint, Integer> order = new HashMap<>();
        for (WeightedRouteAdvisor.BankedTeleportOption option : ordered)
            order.computeIfAbsent(option.landing, landing -> priority.apply(landing, target));
        ordered.sort(Comparator.comparingInt(o -> order.get(o.landing)));
        for (WeightedRouteAdvisor.BankedTeleportOption option : ordered)
            evaluate(option.name, option.landing, target, true, option.requirements, 10);
        log.accept("summary target=" + target + " bestEvaluated=" + winner + " score=" + best
            + " searches=" + cache.size() + " executionChanged=false optimalityProven=false");
    }

    private void evaluate(String name, WorldPoint start, WorldPoint target, boolean bank,
                          Map<Integer, Integer> prefixSupplies, int prefixTime)
    {
        if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
        List<Object> key = List.of(start, target, bank);
        Rs2RouteResult route;
        if (cache.containsKey(key)) route = cache.get(key);
        else
        {
            if (cache.size() >= 10 || clock.getAsLong() >= deadline)
            {
                log.accept("candidate=" + name + " status=NOT_EVALUATED_SEARCH_LIMIT");
                return;
            }
            route = planner.apply(Rs2RouteRequest.to(start, target).withBankItems(bank));
            cache.put(key, route);
        }
        if (route == null || route.getTerminationReason() != Rs2RouteTermination.TARGET_REACHED
            || route.getPath().isEmpty() || !target.equals(route.getPath().get(route.getPath().size() - 1)))
        {
            log.accept("candidate=" + name + " status=" + (route == null ? "NO_RESULT" : route.getTerminationReason() == Rs2RouteTermination.TARGET_REACHED
                ? "INCOMPLETE_PATH" : route.getTerminationReason()));
            return;
        }
        List<Rs2TransportEdge> edges = TransportSupplyPlanner.edges(route);
        Rs2TransportLoadout loadout = preparation.apply(edges, prefixSupplies);
        if (!loadout.isSatisfiable() || (!bank && !loadout.getWithdrawals().isEmpty()))
        {
            log.accept("candidate=" + name + " status=REQUIREMENTS_OR_SPACE_UNAVAILABLE");
            return;
        }
        // All candidates share one time scale, including Grouping's activation delay.
        // Include withdraw/deposit allowance and both equipping and restoring gear.
        long total = (long) WeightedRouteAdvisor.routeCost(route.getSteps()) + prefixTime
            + (bank ? 12 + 4L * loadout.getWithdrawals().size() : 0)
            + 4L * loadout.getEquipmentItemIds().size();
        int score = (int) Math.min(Integer.MAX_VALUE, total);
        StringJoiner chain = new StringJoiner(" -> ");
        for (Rs2TransportEdge edge : edges) chain.add(edge.getType() + ":" + edge.getDisplayInfo());
        log.accept("candidate=" + name + " status=SCORED score=" + score
            + " withdrawals=" + loadout.getWithdrawals() + " equipment=" + loadout.getEquipmentItemIds()
            + " chain=" + chain + " completeTarget=" + target);
        if (score < best) { best = score; winner = name; }
    }
}
