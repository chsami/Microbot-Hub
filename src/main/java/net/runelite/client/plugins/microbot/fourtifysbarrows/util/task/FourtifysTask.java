package net.runelite.client.plugins.microbot.fourtifysbarrows.util.task;

public interface FourtifysTask
{
    boolean isValid();

    String getStatus();

    void execute();
}
