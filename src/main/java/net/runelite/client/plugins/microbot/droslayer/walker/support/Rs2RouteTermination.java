package net.runelite.client.plugins.microbot.droslayer.walker.support;

/** Stable Microbot-owned reason why route planning stopped. */
public enum Rs2RouteTermination
{
	TARGET_REACHED,
	SEARCH_EXHAUSTED,
	CUTOFF_REACHED,
	CANCELLED,
	FAILED
}
