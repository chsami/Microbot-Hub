package net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.tasks;

import java.util.Set;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.config.PhosanisNightmareConfig;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.AttackStyle;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.DreamData;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.NightmareFight;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.Stage;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.TripState;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.action.NightmareActions;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.task.FourtifysTask;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

/**
 * Prays against her standard attacks, through her curse's shuffled buttons, with the offensive prayer for
 * the worn equipment. In her final phase she only uses her claws, so Redemption replaces the protection.
 * Prayers turn off once she sleeps.
 */
public final class PrayerTask implements FourtifysTask
{
    private static final int RESEND_TICKS = 2;
    private static final Rs2PrayerEnum[] PROTECTIONS = {Rs2PrayerEnum.PROTECT_MELEE, Rs2PrayerEnum.PROTECT_RANGE,
        Rs2PrayerEnum.PROTECT_MAGIC};
    private static final Rs2PrayerEnum[] ALL = {Rs2PrayerEnum.PROTECT_MELEE, Rs2PrayerEnum.PROTECT_RANGE,
        Rs2PrayerEnum.PROTECT_MAGIC, Rs2PrayerEnum.PIETY, Rs2PrayerEnum.AUGURY, Rs2PrayerEnum.REDEMPTION};
    private final NightmareActions actions;
    private final PhosanisNightmareConfig config;
    private final NightmareFight fight;
    private final TripState trip;
    private final IntSupplier tick;
    private final Predicate<Rs2PrayerEnum> unlocked;
    private Rs2PrayerEnum requested;
    private int requestedAt = Integer.MIN_VALUE / 2;
    private String status = "Switching prayer";

    public PrayerTask(NightmareActions actions, PhosanisNightmareConfig config, NightmareFight fight, TripState trip,
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
        return next() != null;
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

    /** The protection button for this tick, or null to leave the protection as it is. */
    Rs2PrayerEnum protection(int now)
    {
        if (fight.getStage() == Stage.DESPERATION)
        {
            return null;
        }
        AttackStyle style = fight.pendingAttack(now);
        // A click takes effect at the start of the next tick, before anything attacks on it.
        AttackStyle minion = minionStyle(now);
        if (minion != null)
        {
            // Husks and a risen parasite attack every few ticks, and the game reads the protection as each of them
            // attacks. It reads hers as her missiles or magic launch, a tick before they land, or as her melee lands.
            // So her protection is on for that tick, and the minion's for every other tick.
            boolean hers = false;
            if (style != null)
            {
                int lands = fight.getAttackTick() + style.getHitTicks();
                hers = now + 1 == (style == AttackStyle.MELEE ? lands : lands - 1);
            }
            return (hers ? style : minion).button(fight.isCursed());
        }
        // In her third phase she switches off a protection already on as her attack starts, so it is turned on
        // only once her attack is seen, which still beats the hit.
        if (style == null && fight.getPhase() != 3)
        {
            style = fight.getLastAttack();
        }
        return style == null ? null : style.button(fight.isCursed());
    }

    /**
     * The style to pray against on the next tick for her husks or risen parasite: the missile husk's when it attacks
     * then, otherwise the magic husk's while it lives, then the missile husk's, then the parasite's next attack; none
     * without them. A risen parasite attacks the player when it cannot help her.
     */
    private AttackStyle minionStyle(int now)
    {
        boolean missile = actions.nearest(Set.of(DreamData.RANGED_HUSK)) != null;
        if (actions.nearest(Set.of(DreamData.MAGIC_HUSK)) != null)
        {
            return missile && fight.missileHuskAttacks(now + 1) ? AttackStyle.RANGED : AttackStyle.MAGIC;
        }
        if (missile)
        {
            return AttackStyle.RANGED;
        }
        return actions.nearest(Set.of(DreamData.WEAK_PARASITE, DreamData.STRONG_PARASITE)) != null ? fight.parasiteNext() : null;
    }

    Rs2PrayerEnum offensive()
    {
        if (!config.offensivePrayers())
        {
            return null;
        }
        Rs2PrayerEnum prayer = trip.getLoadout().isMagic() ? Rs2PrayerEnum.AUGURY : Rs2PrayerEnum.PIETY;
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
        if (!actions.inInstance() || !fight.isFighting())
        {
            for (Rs2PrayerEnum prayer : ALL)
            {
                if (actions.prayerActive(prayer) && ready(prayer, now))
                {
                    return prayer;
                }
            }
            return null;
        }
        if (fight.getStage() == Stage.DESPERATION && config.redemption() && !actions.prayerActive(Rs2PrayerEnum.REDEMPTION))
        {
            return ready(Rs2PrayerEnum.REDEMPTION, now) ? Rs2PrayerEnum.REDEMPTION : null;
        }
        Rs2PrayerEnum protect = protection(now);
        if (protect != null && !actions.prayerActive(protect) && ready(protect, now))
        {
            return protect;
        }
        if (protect == null && fight.getPhase() == 3)
        {
            // She switches off a protection that is on as her attack starts, so it goes off once her attack lands.
            for (Rs2PrayerEnum prayer : PROTECTIONS)
            {
                if (actions.prayerActive(prayer) && ready(prayer, now))
                {
                    return prayer;
                }
            }
        }
        Rs2PrayerEnum offensive = offensive();
        if (offensive != null && !actions.prayerActive(offensive) && ready(offensive, now))
        {
            return offensive;
        }
        return null;
    }

    /** The varbit shows a toggle a tick later; resending sooner would switch the prayer back. */
    private boolean ready(Rs2PrayerEnum prayer, int now)
    {
        return prayer != requested || now - requestedAt >= RESEND_TICKS;
    }
}
