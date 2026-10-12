package net.runelite.client.plugins.microbot.actions.fixtures;

/** Concrete discovery fixture with the middle order. */
public class ActionGamma implements TestAction {

    @Override
    public int order() {
        return 20;
    }

    @Override
    public String key() {
        return "gamma";
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
