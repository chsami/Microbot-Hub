package net.runelite.client.plugins.microbot.droefficientwalker.support;

import net.runelite.client.plugins.microbot.droefficientwalker.support.Rs2RouteMetrics;
import net.runelite.client.plugins.microbot.droefficientwalker.support.Rs2RouteResult;
import net.runelite.client.plugins.microbot.droefficientwalker.support.Rs2RouteStep;
import net.runelite.client.plugins.microbot.droefficientwalker.support.Rs2TransportEdge;
import net.runelite.client.plugins.microbot.util.walker.Rs2TransportItemRequirement;
import net.runelite.client.plugins.microbot.util.walker.Rs2RouteTermination;
import net.runelite.client.plugins.microbot.util.walker.Rs2TransportType;
import net.runelite.client.plugins.microbot.util.walker.Rs2TransportExecutor;
import net.runelite.client.plugins.microbot.util.walker.Rs2TerminalTravelMode;
import net.runelite.client.plugins.microbot.util.walker.Rs2RouteRequest;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.droefficientwalker.support.pathfinder.PathEdge;
import net.runelite.client.plugins.microbot.droefficientwalker.support.pathfinder.PathTerminationReason;
import net.runelite.client.plugins.microbot.droefficientwalker.support.pathfinder.Pathfinder;
import net.runelite.client.plugins.microbot.droefficientwalker.support.pathfinder.PathfinderConfig;
import net.runelite.client.plugins.microbot.shortestpath.*;

import java.util.*;

/**
 * Efficient Walker's isolated synchronous route planner and immutable route conversion.
 * Its configuration and mutex never belong to the main Rs2Walker or NavigationEngine.
 * Reads preserved route resources and the user's shared travel settings; does not publish routes.
 */
public final class Rs2PathApi
{



	private static Rs2RouteResult snapshot(Pathfinder pathfinder, long elapsed, PathfinderConfig config)
	{
		Pathfinder.PathfinderStats stats = pathfinder.getStats();
		List<WorldPoint> path = pathfinder.getPath() == null
			? Collections.emptyList()
			: pathfinder.getPath();
		List<Rs2RouteStep> steps = new ArrayList<>();
		for (PathEdge edge : pathfinder.getPathEdges())
		{
			if (edge.isTransport())
			{
				steps.add(Rs2RouteStep.transport(
					edge.getFrom(), edge.getTo(), toTransportEdge(edge.getTransport(), config.transportCurrencyAmount(edge.getTransport()))));
			}
			else
			{
				steps.add(Rs2RouteStep.walk(edge.getFrom(), edge.getTo()));
			}
		}
		return new Rs2RouteResult(
			pathfinder.getStart(),
			pathfinder.getTargets(),
			path,
			steps,
			mapTermination(pathfinder.getTerminationReason()),
			new Rs2RouteMetrics(
				elapsed,
				pathfinder.getSelectedPathCost(),
				stats == null ? Rs2RouteMetrics.UNAVAILABLE : stats.getNodesChecked(),
				stats == null ? Rs2RouteMetrics.UNAVAILABLE : stats.getTransportsChecked(),
				stats == null ? Rs2RouteMetrics.UNAVAILABLE
					: stats.getLiveCollisionEdgesChecked()));
	}


	private static Rs2TransportEdge toTransportEdge(Transport transport)
	{
        return toTransportEdge(transport, net.runelite.client.plugins.microbot.droefficientwalker.CharterFare.amount(transport));
    }

    private static Rs2TransportEdge toTransportEdge(Transport transport, int currencyAmount)
    {
		List<Rs2TransportItemRequirement> itemRequirements = new ArrayList<>();
		for (TransportItemRequirement requirement : transport.getItemRequirements())
		{
			itemRequirements.add(new Rs2TransportItemRequirement(
				requirement.getAlternatives(),
				requirement.getStaffAlternatives(),
				requirement.getOffhandAlternatives(),
				requirement.isRuneOnly()));
		}
		return new Rs2TransportEdge(
			transport.getOrigin(),
			transport.getDestination(),
			mapTransportType(transport.getType()),
			mapExecutor(TransportExecutionRegistry.executorFor(transport).orElse(null)),
			mapTerminalTravelMode(transport),
			transport.getDisplayInfo(),
			transport.getAction(),
			transport.getName(),
			transport.getObjectId(),
			transport.getDuration(),
			TransportType.isTeleport(transport.getType(), transport.getOrigin()),
			transport.isConsumable(),
			transport.isMembers(),
			transport.getMaxWildernessLevel(),
			transport.getCurrencyName(),
			currencyAmount,
			itemRequirements,
			Arrays.stream(transport.getSkillLevels()).anyMatch(level -> level > 0),
			transport.isQuestLocked(),
			!transport.getVarbits().isEmpty() || !transport.getVarplayers().isEmpty(),
			transport);
	}


	private static Rs2TerminalTravelMode mapTerminalTravelMode(Transport transport)
	{
		return TransportExecutionRegistry.terminalTravelModeFor(transport)
			.map(mode -> Rs2TerminalTravelMode.valueOf(mode.name()))
			.orElse(Rs2TerminalTravelMode.UNSUPPORTED);
	}


	private static Rs2TransportExecutor mapExecutor(TransportExecutionRegistry.Executor executor)
	{
		if (executor == null)
		{
			return Rs2TransportExecutor.UNSUPPORTED;
		}
		try
		{
			return Rs2TransportExecutor.valueOf(executor.name());
		}
		catch (IllegalArgumentException ignored)
		{
			return Rs2TransportExecutor.UNSUPPORTED;
		}
	}


	private static Rs2TransportType mapTransportType(TransportType type)
	{
		if (type == null)
		{
			return Rs2TransportType.UNKNOWN;
		}
		try
		{
			return Rs2TransportType.valueOf(type.name());
		}
		catch (IllegalArgumentException ignored)
		{
			return Rs2TransportType.UNKNOWN;
		}
	}


	private static Rs2RouteTermination mapTermination(PathTerminationReason reason)
	{
		if (reason == null)
		{
			return Rs2RouteTermination.FAILED;
		}
		switch (reason)
		{
			case TARGET_REACHED:
				return Rs2RouteTermination.TARGET_REACHED;
			case SEARCH_EXHAUSTED:
				return Rs2RouteTermination.SEARCH_EXHAUSTED;
			case CUTOFF_REACHED:
				return Rs2RouteTermination.CUTOFF_REACHED;
			case CANCELLED:
				return Rs2RouteTermination.CANCELLED;
			case FAILED:
			default:
				return Rs2RouteTermination.FAILED;
		}
	}


	private static PathTerminationReason mapTermination(Rs2RouteTermination reason)
	{
		if (reason == null)
		{
			return PathTerminationReason.FAILED;
		}
		switch (reason)
		{
			case TARGET_REACHED:
				return PathTerminationReason.TARGET_REACHED;
			case SEARCH_EXHAUSTED:
				return PathTerminationReason.SEARCH_EXHAUSTED;
			case CUTOFF_REACHED:
				return PathTerminationReason.CUTOFF_REACHED;
			case CANCELLED:
				return PathTerminationReason.CANCELLED;
			case FAILED:
			default:
				return PathTerminationReason.FAILED;
		}
	}


	public static boolean override(String configOverrideKey, boolean defaultValue)
	{
		return ShortestPathPlugin.override(configOverrideKey, defaultValue);
	}


	public static int override(String configOverrideKey, int defaultValue)
	{
		return ShortestPathPlugin.override(configOverrideKey, defaultValue);
	}


	public static TeleportationItem override(String configOverrideKey, TeleportationItem defaultValue)
	{
		return ShortestPathPlugin.override(configOverrideKey, defaultValue);
	}


	/**
	 * Compatibility access to the concrete mutable transport graph.
	 * New code should use the named catalog queries above or exact immutable route steps.
	 */
	@Deprecated
	public static Map<WorldPoint, Set<Transport>> getTransports()
	{
		return getPathfinderConfig().getTransports();
	}

    private static final Object MUTEX = new Object();
    private static PathfinderConfig privateConfig;

    public static PathfinderConfig getPathfinderConfig()
    {
        synchronized (MUTEX)
        {
            if (privateConfig == null)
            {
                privateConfig = new PathfinderConfig(
                    net.runelite.client.plugins.microbot.droefficientwalker.support.pathfinder.SplitFlagMap.fromResources(),
                    TransportResources.loadAllFromResources(),
                    TransportResources.loadRestrictions(),
                    Microbot.getClient(),
                    Microbot.getConfigManager().getConfig(ShortestPathConfig.class),
                    Rs2TransportPlanningPolicy.INSTANCE);
            }
            return privateConfig;
        }
    }

    public static Rs2RouteResult plan(Rs2RouteRequest request)
    {
        if (Microbot.getClientThread() != null && Microbot.getClientThread().isClientThread())
            throw new IllegalStateException("Efficient Walker route planning must not run on the client thread");
        synchronized (MUTEX)
        {
            PathfinderConfig config = getPathfinderConfig();
            boolean previous = config.isUseBankItems();
            try
            {
                if (request.getUseBankItems() != null) config.setUseBankItems(request.getUseBankItems());
                if (request.getUseBankItems() != null
                    || request.getRefreshPolicy() == Rs2RouteRequest.RefreshPolicy.ALWAYS
                    || (request.getRefreshPolicy() == Rs2RouteRequest.RefreshPolicy.IF_TRANSPORTS_EMPTY
                        && config.getTransports().isEmpty())) config.refresh(request.getRefreshTarget());
                return planWithConfig(request, config);
            }
            finally { config.setUseBankItems(previous); }
        }
    }

    static Rs2RouteResult planWithConfig(Rs2RouteRequest request, PathfinderConfig config)
    {
        long started = System.nanoTime();
        // A ship can land the player on any nearby deck tile, including tiles missing from
        // the static collision map. Scope an exit to this request's exact start, not a walk
        // to an assumed deck coordinate. The runtime validates the live gangplank.
        Transport candidate = net.runelite.client.plugins.microbot.droefficientwalker.CharterTransportCatalog.exitFrom(request.getStart());
        Set<Transport> existing = config.getTransports().get(request.getStart());
        Transport exit = candidate != null && (existing == null || existing.stream().noneMatch(row ->
            net.runelite.client.plugins.microbot.droefficientwalker.CharterTransportCatalog.isDisembark(row)
                && row.getDestination().equals(candidate.getDestination()))) ? candidate : null;
        Set<Transport> previous = null, previousPacked = null;
        int packed = WorldPointUtil.packWorldPoint(request.getStart());
        if (exit != null) {
            previous = config.getTransports().get(request.getStart());
            Set<Transport> packedBefore = config.getTransportsPacked().get(packed);
            previousPacked = packedBefore == null ? Set.of() : new HashSet<>(packedBefore);
            Set<Transport> scoped = new HashSet<>(previous == null ? Set.of() : previous);
            scoped.add(exit);
            config.getTransports().put(request.getStart(), scoped);
            // PrimitiveIntHashMap.put merges collections instead of replacing them.
            // Preserve a copy of its old contents for the request cleanup.
            if (packedBefore == null) config.getTransportsPacked().put(packed, new HashSet<>(scoped));
            else packedBefore.add(exit);
        }
        try {
            Pathfinder planner = new Pathfinder(config, request.getStart(), request.getTargets());
            planner.run();
            return snapshot(planner, System.nanoTime() - started, config);
        } finally {
            if (exit != null) {
                if (previous == null) config.getTransports().remove(request.getStart());
                else config.getTransports().put(request.getStart(), previous);
                // PrimitiveIntHashMap has no remove; an empty entry carries no edges.
                Set<Transport> packedAfter = config.getTransportsPacked().get(packed);
                packedAfter.clear();
                packedAfter.addAll(previousPacked);
            }
        }
    }
}
