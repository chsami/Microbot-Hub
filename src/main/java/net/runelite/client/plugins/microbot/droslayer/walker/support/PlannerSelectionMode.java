package net.runelite.client.plugins.microbot.droslayer.walker.support;

/**
 * Explicit production planner rollout state.
 *
 * <p>A single mode prevents contradictory combinations such as selecting the upstream planner while
 * comparison telemetry is disabled. The canary is deliberately limited to resolved F2P policy; members
 * routes remain local until their own evidence gate is accepted.</p>
 */
public enum PlannerSelectionMode
{
	/** Run only the local Microbot planner. */
	LOCAL,
	/** Keep the local planner authoritative and compare the pinned upstream planner asynchronously. */
	SHADOW,
	/** Select a semantically matching upstream result for F2P routes, with an automatic local fallback. */
	UPSTREAM_F2P_CANARY;

}
