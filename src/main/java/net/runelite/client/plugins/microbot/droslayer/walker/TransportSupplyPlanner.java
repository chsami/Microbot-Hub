package net.runelite.client.plugins.microbot.droslayer.walker;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.droslayer.walker.support.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;

/** Preparation-only search. Banked supplies may unlock edges here, never during execution. */
final class TransportSupplyPlanner
{
    static List<Rs2TransportEdge> plan(WorldPoint start, WorldPoint target)
    {
        return plan(start, target, Rs2PathApi::plan);
    }

    static List<Rs2TransportEdge> plan(WorldPoint start, WorldPoint target,
                                     Function<Rs2RouteRequest, Rs2RouteResult> planner)
    {
        if (start == null || target == null || Thread.currentThread().isInterrupted()) return List.of();
        return edges(planner.apply(Rs2RouteRequest.to(start, target).withBankItems(true)));
    }

    static Rs2TransportLoadout supplies(List<Rs2TransportEdge> edges,
        Function<List<Rs2TransportEdge>, Rs2TransportLoadout> itemResolver,
        IntUnaryOperator bank, IntUnaryOperator carried)
    {
        List<Rs2TransportEdge> itemEdges = new ArrayList<>();
        long fare = 0;
        for (Rs2TransportEdge edge : edges)
        {
            boolean coinFare = edge.getCurrencyAmount() > 0
                && "Coins".equalsIgnoreCase(edge.getCurrencyName());
            if (coinFare) fare += edge.getCurrencyAmount();
            // An edge can require both a fare and equipment/items. Resolve both, while
            // keeping currency-only edges out of the per-edge item resolver.
            if (!coinFare || !edge.getItemRequirements().isEmpty()) itemEdges.add(edge);
        }
        Rs2TransportLoadout items = itemResolver.apply(itemEdges);
        if (!items.isSatisfiable()) return items;
        long missingCoins = Math.max(0L, fare - Math.max(0, carried.applyAsInt(995)));
        if (missingCoins > bank.applyAsInt(995) || missingCoins > Integer.MAX_VALUE)
            return Rs2TransportLoadout.unavailable();
        Map<Integer, Integer> withdrawals = new LinkedHashMap<>(items.getWithdrawals());
        if (missingCoins > 0) withdrawals.merge(995, (int) missingCoins, Math::max);
        return new Rs2TransportLoadout(withdrawals, items.getEquipmentItemIds(), true);
    }

    static List<Rs2TransportEdge> nonTeleportSupplies(List<Rs2TransportEdge> edges)
    {
        List<Rs2TransportEdge> supplies = new ArrayList<>();
        for (Rs2TransportEdge edge : edges)
            if (!edge.getType().name().startsWith("TELEPORTATION_")) supplies.add(edge);
        return List.copyOf(supplies);
    }

    static List<Rs2TransportEdge> edges(Rs2RouteResult route)
    {
        if (route == null || route.getTerminationReason() != Rs2RouteTermination.TARGET_REACHED)
            return List.of();
        List<Rs2TransportEdge> result = new ArrayList<>();
        for (Rs2RouteStep step : route.getSteps())
            step.getTransport().ifPresent(result::add);
        return List.copyOf(result);
    }
}
