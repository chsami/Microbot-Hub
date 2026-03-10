package net.runelite.client.plugins.microbot.accountbuilder.task;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.accountbuilder.profile.AccountProfile;

import java.util.List;

/**
 * Walks the ordered task list and delegates to the first incomplete task each tick.
 */
@Slf4j
public class TaskExecutor {

    private final List<Task> tasks;
    private final AccountProfile profile;

    public TaskExecutor(List<Task> tasks, AccountProfile profile) {
        this.tasks = tasks;
        this.profile = profile;
    }

    /**
     * Called once per script tick.
     * Finds the first incomplete task and calls {@link Task#execute()}.
     */
    public void tick() {
        for (Task task : tasks) {
            if (!task.isComplete()) {
                task.execute();
                return;
            }
        }
        Microbot.log("AccountBuilder: All tasks complete!");
    }

    /** Returns the currently active task, or {@code null} if all tasks are done. */
    public Task getCurrentTask() {
        return tasks.stream()
                .filter(t -> !t.isComplete())
                .findFirst()
                .orElse(null);
    }

    /** Number of tasks that have been completed. */
    public int getCompletedCount() {
        return (int) tasks.stream().filter(Task::isComplete).count();
    }

    /** Total number of tasks. */
    public int getTotalCount() {
        return tasks.size();
    }
}
