package net.runelite.client.plugins.microbot.actions.fixtures;

import net.runelite.client.plugins.microbot.actions.Action;

/**
 * Test-only action interface bound to {@link FixtureState}. {@code ActionRegistry.discover} scans the
 * package of the interface it is given, so every concrete implementor that lives in THIS package (and
 * only this package) should be discovered — while this interface itself and any abstract base are
 * skipped. Keep this package limited to discovery fixtures so the discovery assertions stay exact.
 */
public interface TestAction extends Action<FixtureState> {
}
