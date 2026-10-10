package net.runelite.client.plugins.microbot.actions.fixtures;

/** Concrete discovery fixture with the lowest order, so it must run first despite its name. */
public class ActionBeta implements TestAction {

    @Override
    public int order() {
        return 10;
    }

    @Override
    public String key() {
        return "beta";
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
