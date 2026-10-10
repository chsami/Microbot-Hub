package net.runelite.client.plugins.microbot.actions.fixtures;

/**
 * Abstract implementor that must be SKIPPED by discovery (it cannot be instantiated). Present so the
 * discovery test proves abstract classes and the interface itself are filtered out.
 */
public abstract class AbstractTestAction implements TestAction {

    @Override
    public boolean needsExecution(FixtureState state) {
        return false;
    }

    @Override
    public Object execute(FixtureState state) {
        return null;
    }
}
