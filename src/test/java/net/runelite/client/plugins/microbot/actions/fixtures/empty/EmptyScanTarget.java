package net.runelite.client.plugins.microbot.actions.fixtures.empty;

import net.runelite.client.plugins.microbot.actions.Action;
import net.runelite.client.plugins.microbot.actions.fixtures.FixtureState;

/**
 * A scan target that intentionally has NO concrete implementations in its package (only this
 * interface and an unrelated POJO live here). Discovery over it must return an empty list, not throw.
 */
public interface EmptyScanTarget extends Action<FixtureState> {
}
