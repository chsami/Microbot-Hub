package net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.task;

public interface FourtifysTask
{
    boolean isValid();

    String getStatus();

    void execute();
}
