package net.runelite.client.plugins.microbot.droslayer.walker.support;

import net.runelite.api.coords.WorldPoint;

import java.util.*;

/**
 * Immutable Microbot-owned input for a synchronous route calculation.
 *
 * <p>The request deliberately describes policy without exposing {@code PathfinderConfig}. New
 * synchronous planning consumers should use this value through {@link Rs2PathApi#plan(Rs2RouteRequest)}
 * instead of constructing a shortest-path {@code Pathfinder} directly.</p>
 */
public final class Rs2RouteRequest
{
	/** Caller-owned workflow intent used only to classify planner evidence. */
	public enum Purpose
	{
		GENERAL,
		BANK_ROUTE_DIRECT,
		BANK_ROUTE_TO_BANK,
		BANK_ROUTE_FROM_BANK
	}

	public enum RefreshPolicy
	{
		NEVER,
		IF_TRANSPORTS_EMPTY,
		ALWAYS
	}

	private final WorldPoint start;
	private final Set<WorldPoint> targets;
	private final RefreshPolicy refreshPolicy;
	private final WorldPoint refreshTarget;
	private final Boolean useBankItems;
	private final Rs2RoutePolicy policy;
	private final Purpose purpose;

	private Rs2RouteRequest(
		WorldPoint start,
		Set<WorldPoint> targets,
		RefreshPolicy refreshPolicy,
		WorldPoint refreshTarget,
		Boolean useBankItems,
		Rs2RoutePolicy policy,
		Purpose purpose)
	{
		this.start = Objects.requireNonNull(start, "start");
		Objects.requireNonNull(targets, "targets");
		if (targets.isEmpty())
		{
			throw new IllegalArgumentException("targets must not be empty");
		}
		LinkedHashSet<WorldPoint> targetCopy = new LinkedHashSet<>();
		for (WorldPoint target : targets)
		{
			targetCopy.add(Objects.requireNonNull(target, "target"));
		}
		this.targets = Collections.unmodifiableSet(targetCopy);
		this.refreshPolicy = Objects.requireNonNull(refreshPolicy, "refreshPolicy");
		this.refreshTarget = refreshTarget;
		this.useBankItems = useBankItems;
		this.policy = policy;
		this.purpose = Objects.requireNonNull(purpose, "purpose");
	}

	public static Rs2RouteRequest to(WorldPoint start, WorldPoint target)
	{
		return toAny(start, Collections.singleton(target));
	}

	public static Rs2RouteRequest toAny(WorldPoint start, Set<WorldPoint> targets)
	{
		return new Rs2RouteRequest(
			start, targets, RefreshPolicy.IF_TRANSPORTS_EMPTY, null, null, null,
			Purpose.GENERAL);
	}

	public Rs2RouteRequest withRefreshTarget(WorldPoint target)
	{
		return new Rs2RouteRequest(
			start, targets, refreshPolicy, target, useBankItems, policy, purpose);
	}

	/**
	 * Include or exclude bank contents while evaluating transport requirements. Planning with this
	 * temporary policy always refreshes the transport snapshot and restores the previous policy after
	 * the search.
	 */
	public Rs2RouteRequest withBankItems(boolean enabled)
	{
		return new Rs2RouteRequest(
			start, targets, RefreshPolicy.ALWAYS, refreshTarget, enabled,
			policy == null ? null : policy.withUseBankItems(enabled), purpose);
	}

	public WorldPoint getStart()
	{
		return start;
	}

	public Set<WorldPoint> getTargets()
	{
		return targets;
	}

	public RefreshPolicy getRefreshPolicy()
	{
		return refreshPolicy;
	}

	public WorldPoint getRefreshTarget()
	{
		return refreshTarget;
	}

	public Boolean getUseBankItems()
	{
		return useBankItems;
	}

}
