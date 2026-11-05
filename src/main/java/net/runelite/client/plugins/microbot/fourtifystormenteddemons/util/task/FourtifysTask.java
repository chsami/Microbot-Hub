package net.runelite.client.plugins.microbot.fourtifystormenteddemons.util.task;

public interface FourtifysTask
{
    boolean isValid();

    String getStatus();

    void execute();
}
