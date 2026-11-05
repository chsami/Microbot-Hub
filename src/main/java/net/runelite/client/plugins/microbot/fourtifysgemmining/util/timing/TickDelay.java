package net.runelite.client.plugins.microbot.fourtifysgemmining.util.timing;

import java.util.Random;
import java.util.function.Consumer;
import net.runelite.api.Player;
import net.runelite.client.plugins.microbot.fourtifysgemmining.util.config.TimingConfig;

public final class TickDelay
{
    private int remaining;

    public void set(int ticks)
    {
        remaining = ticks;
    }

    public boolean waitForDelay(Player player, Consumer<String> status)
    {
        if (remaining <= 0)
        {
            return false;
        }
        // The countdown only runs while the player is idle, so the status shows it only then.
        if (player.getAnimation() == -1)
        {
            status.accept("Short pause (" + remaining + (remaining == 1 ? " tick)" : " ticks)"));
            remaining--;
        }
        return true;
    }

    public static int roll(Random random, TimingConfig config)
    {
        double value = config.tickDelayWeightedDistribution()
                        ? -Math.log(Math.abs(random.nextGaussian())) * config.tickDelayDeviation() + config.tickDelayTarget()
                        : Math.round(random.nextGaussian() * config.tickDelayDeviation() + config.tickDelayTarget());
        return (int) Math.max(config.tickDelayMin(), Math.min(config.tickDelayMax(), value));
    }
}
