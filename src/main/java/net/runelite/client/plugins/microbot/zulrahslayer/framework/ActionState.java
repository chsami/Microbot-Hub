package net.runelite.client.plugins.custom.actions;

/** The outcome of one action on one tick: whether it ran and what it returned. */
public class ActionState {

    private final String key;
    private final boolean executed;
    private final Object result;

    public ActionState(String key, boolean executed, Object result) {
        this.key = key;
        this.executed = executed;
        this.result = result;
    }

    public String getKey() {
        return key;
    }

    public boolean isExecuted() {
        return executed;
    }

    public Object getResult() {
        return result;
    }
}
