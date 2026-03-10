package net.runelite.client.plugins.microbot.accountbuilder.task;

/**
 * A single step in the account-builder task list.
 * Tasks are executed in order; each skips itself when already complete.
 */
public interface Task {

    /** Display name shown in the overlay. */
    String getName();

    /** Returns true when this task no longer needs to run. */
    boolean isComplete();

    /** Called every script tick while this is the active task. */
    void execute();
}
