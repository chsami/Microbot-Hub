package net.runelite.client.plugins.microbot.cgprep;

/**
 * Macro phases of a run. Fine-grained "what to do next" inside PREP is the planner's job
 * (PrepGoal), so this enum stays small and the transitions stay readable.
 */
public enum CgState
{
	/** Not in the Gauntlet; waiting (or auto-lobby if enabled). */
	OUTSIDE,
	/** In the lobby: deposit everything, enter the Corrupted Gauntlet. */
	LOBBY_SETUP,
	/** Inside, timer running: planner + actions. */
	PREP,
	/** Prep complete; bot sends no input. The player takes over. */
	HANDOVER,
	/** Boss fight in progress (player-driven for now; fight module later). */
	BOSS,
	/** Back in the lobby after a run: record result, wait for Continue. */
	POST_RUN,
	/** Paused by hotkey or stopped. */
	PAUSED
}
