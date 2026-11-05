package net.runelite.client.plugins.microbot.fourtifysgemmining.util.overlay;

import java.time.Duration;
import java.time.Instant;

public final class RuntimeFormat
{
    private RuntimeFormat()
    {
    }

    public static String elapsed(Instant start)
    {
        long seconds = Math.max(0, Duration.between(start, Instant.now()).getSeconds());
        return String.format("%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }
}
