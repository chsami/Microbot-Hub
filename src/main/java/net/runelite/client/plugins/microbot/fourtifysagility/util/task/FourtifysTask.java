package net.runelite.client.plugins.microbot.fourtifysagility.util.task;

public interface FourtifysTask
{
    boolean isValid();

    String getStatus();

    void execute();
}
