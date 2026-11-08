package net.runelite.client.plugins.microbot.fourtifystormenteddemons.tasks;

import java.util.function.IntSupplier;
import java.util.function.Predicate;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.config.TormentedDemonsConfig;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.util.task.FourtifysTask;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.CombatStyle;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.DemonFight;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.TripPhase;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.TripState;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.util.action.DemonActions;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

/**
 * Holds the protection prayer for the demon's style, and the offensive prayer for the worn style when
 * enabled. Prayers turn off once no demon has been engaged for a while.
 */
public final class PrayerTask implements FourtifysTask
{
    static final int IDLE_TICKS = 8;
    private static final int RESEND_TICKS = 2;
    private static final Rs2PrayerEnum[] PROTECTIONS = {Rs2PrayerEnum.PROTECT_MELEE, Rs2PrayerEnum.PROTECT_RANGE, Rs2PrayerEnum.PROTECT_MAGIC};
    private final DemonActions actions;
    private final TormentedDemonsConfig config;
    private final DemonFight fight;
    private final TripState trip;
    private final IntSupplier tick;
    private final Predicate<Rs2PrayerEnum> unlocked;
    private Rs2PrayerEnum requested;
    private int requestedAt = Integer.MIN_VALUE / 2;
    private String status = "Switching prayer";

    public PrayerTask(DemonActions actions, TormentedDemonsConfig config, DemonFight fight, TripState trip,
        IntSupplier tick, Predicate<Rs2PrayerEnum> unlocked)
    {
        this.actions = actions;
        this.config = config;
        this.fight = fight;
        this.trip = trip;
        this.tick = tick;
        this.unlocked = unlocked;
    }

    @Override
    public boolean isValid()
    {
        return trip.getPhase() == TripPhase.FIGHTING && next() != null;
    }

    @Override
    public String getStatus()
    {
        return status;
    }

    @Override
    public void execute()
    {
        Rs2PrayerEnum prayer = next();
        if (prayer == null)
        {
            return;
        }
        boolean on = !actions.prayerActive(prayer);
        status = (on ? "Praying " : "Turning off ") + prayer.getName();
        int now = tick.getAsInt();
        actions.prayer(prayer, on, () ->
        {
            requested = prayer;
            requestedAt = now;
        });
    }

    /** The protection to hold against the engaged demon, or null when no demon is engaged. */
    public CombatStyle wantedProtection()
    {
        NPC target = trip.getTarget();
        Player player = actions.player();
        if (target == null || player == null || target.getWorldArea() == null)
        {
            return null;
        }
        boolean adjacent = target.getWorldArea().distanceTo(player.getWorldLocation()) <= 1;
        return fight.protectFrom(trip.getStyle(), adjacent);
    }

    Rs2PrayerEnum offensive()
    {
        if (!config.offensivePrayer() || trip.getStyle() == null)
        {
            return null;
        }
        Rs2PrayerEnum prayer;
        switch (trip.getStyle())
        {
            case MELEE:
                prayer = Rs2PrayerEnum.PIETY;
                break;
            case RANGED:
                prayer = Rs2PrayerEnum.RIGOUR;
                break;
            default:
                prayer = Rs2PrayerEnum.AUGURY;
                break;
        }
        return unlocked.test(prayer) ? prayer : null;
    }

    /** The next prayer to toggle, or null when every prayer is already as wanted. */
    Rs2PrayerEnum next()
    {
        int now = tick.getAsInt();
        if (actions.prayerPoints() <= 0)
        {
            return null;
        }
        CombatStyle protect = wantedProtection();
        Rs2PrayerEnum offensive = offensive();
        if (protect != null)
        {
            if (!actions.prayerActive(protect.getProtection()) && ready(protect.getProtection(), now))
            {
                return protect.getProtection();
            }
            if (offensive != null && !actions.prayerActive(offensive) && ready(offensive, now))
            {
                return offensive;
            }
            return null;
        }
        if (trip.ticksSinceEngaged(now) < IDLE_TICKS)
        {
            return null;
        }
        for (Rs2PrayerEnum prayer : PROTECTIONS)
        {
            if (actions.prayerActive(prayer) && ready(prayer, now))
            {
                return prayer;
            }
        }
        return offensive != null && actions.prayerActive(offensive) && ready(offensive, now) ? offensive : null;
    }

    /** The varbit shows a toggle a tick later; resending sooner would switch the prayer back. */
    private boolean ready(Rs2PrayerEnum prayer, int now)
    {
        return prayer != requested || now - requestedAt >= RESEND_TICKS;
    }
}
