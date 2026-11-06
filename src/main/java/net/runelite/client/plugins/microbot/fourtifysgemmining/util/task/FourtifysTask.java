package net.runelite.client.plugins.microbot.fourtifysgemmining.util.task;

public interface FourtifysTask
{
    boolean isValid();

    String getStatus();

    void execute();
}
