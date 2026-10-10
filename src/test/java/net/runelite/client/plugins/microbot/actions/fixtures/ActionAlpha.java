package net.runelite.client.plugins.microbot.actions.fixtures;

/** Concrete discovery fixture. Order is intentionally not its alphabetical position. */
public class ActionAlpha implements TestAction {

    @Override
    public int order() {
        return 30;
    }

    @Override
    public String key() {
        return "alpha";
    }

    @Override
    public boolean needsExecution(FixtureState state) {
        return true;
    }

    @Override
    public Object execute(FixtureState state) {
        state.markExecuted(key());
        return key();
    }
}
