package net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.tasks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntSupplier;
import java.util.stream.Collectors;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.DreamData;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.Loadout;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.TripPhase;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.TripState;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.action.NightmareActions;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.task.FourtifysTask;

/**
 * Wears the loadout for this tick's target, every missing item on the same tick: the melee or magic set, with the weapon
 * swapped for parasites and special attacks, and only the weapon for husks and sleepwalkers. A two-handed swap leaves
 * the shield off. A husk or parasite weapon whose crush button is known is wielded with its style in one request.
 * While the player still attacks her as her husks rise, the husk is attacked first. Husk and parasite weapons
 * then use a crush style, a ranged sleepwalker weapon its rapid style, the magic weapon
 * alone its longrange style, and the melee weapon its starting style.
 */
public final class GearTask implements FourtifysTask
{
    private static final int OBSERVE_TICKS = 2;
    private final NightmareActions actions;
    private final TripState trip;
    private final IntSupplier tick;
    private final Set<Integer> melee;
    private final Set<Integer> magic;
    private final Map<Loadout, Integer> weapons;
    private int meleeStyle = -1;
    /** The crush style button of each husk and parasite weapon, learned while it is worn. */
    private final Map<Integer, Integer> crush = new HashMap<>();
    private int requestedAt = Integer.MIN_VALUE / 2;
    private String status = "Switching gear";

    public GearTask(NightmareActions actions, TripState trip, IntSupplier tick, Set<Integer> melee, Set<Integer> magic,
        Map<Loadout, Integer> weapons)
    {
        this.actions = actions;
        this.trip = trip;
        this.tick = tick;
        this.melee = melee;
        this.magic = magic;
        this.weapons = weapons;
    }

    /** Remembers the combat style chosen for the melee weapon, read once the melee set is worn. */
    public void observe()
    {
        if (meleeStyle < 0 && missing(Loadout.MELEE).isEmpty())
        {
            meleeStyle = actions.styleIndex();
        }
        for (Loadout loadout : new Loadout[] {Loadout.HUSK, Loadout.PARASITE})
        {
            Integer weapon = weapons.get(loadout);
            int button = weapon != null && weapon > 0 && actions.wearing(weapon) ? actions.styleButton("(Crush)") : -1;
            if (button >= 0)
            {
                crush.put(weapon, button);
            }
        }
    }

    /** The items the loadout wears. */
    public Set<Integer> wanted(Loadout loadout)
    {
        Integer weapon = weapons.get(loadout);
        if ((loadout == Loadout.SLEEPWALKER || loadout == Loadout.HUSK) && weapon != null && weapon > 0)
        {
            // Any hit kills a sleepwalker, and a crush hit on a husk is always its max hit, so only the weapon is
            // swapped and the first attack comes sooner.
            return Set.of(weapon);
        }
        if (loadout == Loadout.LONGRANGE)
        {
            Set<Integer> staff = magic.stream().filter(id -> actions.slot(id) == EquipmentInventorySlot.WEAPON.getSlotIdx())
                .collect(Collectors.toSet());
            return staff.isEmpty() ? magic : staff;
        }
        Set<Integer> result = new LinkedHashSet<>(loadout.isMagic() ? magic : melee);
        if (weapon != null && weapon > 0)
        {
            boolean twoHanded = actions.twoHanded(weapon);
            result.removeIf(id -> actions.slot(id) == EquipmentInventorySlot.WEAPON.getSlotIdx()
                || twoHanded && actions.slot(id) == EquipmentInventorySlot.SHIELD.getSlotIdx());
            result.add(weapon);
        }
        return result;
    }

    /** The items of the loadout that are not worn. */
    /** Every item of every set, melee first. */
    private Set<Integer> allGear()
    {
        Set<Integer> all = new LinkedHashSet<>(melee);
        all.addAll(magic);
        weapons.values().stream().filter(id -> id > 0).forEach(all::add);
        return all;
    }

    public List<Integer> missing(Loadout loadout)
    {
        return wanted(loadout).stream().filter(id -> !actions.wearing(id)).collect(Collectors.toList());
    }

    /** The loadout's combat style button, or -1 to leave the style as it is. */
    int style(Loadout loadout)
    {
        Integer weapon = weapons.get(loadout);
        NPC target = trip.getTarget();
        if (loadout == Loadout.HUSK && target != null && target.getId() == DreamData.SLEEPWALKER)
        {
            // Any hit kills a sleepwalker, so the husk weapon hits it on whatever style it has.
            return -1;
        }
        if ((loadout == Loadout.HUSK || loadout == Loadout.PARASITE) && weapon != null && weapon > 0)
        {
            return actions.styleButton("(Crush)");
        }
        if (loadout == Loadout.SLEEPWALKER && weapon != null && weapon > 0)
        {
            // A ranged weapon attacks most often on its rapid style; a weapon without one keeps its style.
            return actions.styleButton("(Rapid)");
        }
        if (loadout == Loadout.LONGRANGE)
        {
            // A held player cannot close in, so the staff casts from its longest range.
            return actions.styleButton("(Longrange)");
        }
        return loadout == Loadout.MELEE ? meleeStyle : -1;
    }

    /**
     * The target can be attacked: the loadout is worn and its style chosen. The sleepwalkers' rapid style only
     * quickens the attacks after the first, so the first does not wait for it.
     */
    public boolean ready()
    {
        Loadout loadout = trip.getLoadout();
        return loadout == Loadout.SLEEPWALKER ? missing(loadout).isEmpty() : done();
    }

    /** The loadout is worn and its style chosen. */
    boolean done()
    {
        Loadout loadout = trip.getLoadout();
        int style = style(loadout);
        return missing(loadout).isEmpty() && (style < 0 || actions.styleIndex() == style);
    }

    @Override
    public boolean isValid()
    {
        boolean place = trip.getPhase() == TripPhase.FIGHTING && actions.inInstance()
            || trip.getPhase() == TripPhase.TRAVELLING && DreamData.inSanctuary(actions.position()) && !actions.inInstance();
        return place && tick.getAsInt() - requestedAt >= OBSERVE_TICKS && !done();
    }

    @Override
    public String getStatus()
    {
        return status;
    }

    @Override
    public void execute()
    {
        int now = tick.getAsInt();
        Runnable sent = () -> requestedAt = now;
        Loadout loadout = trip.getLoadout();
        List<Integer> missing = missing(loadout);
        if (missing.isEmpty())
        {
            status = "Choosing the combat style";
            actions.style(style(loadout), sent);
            return;
        }
        if (missing.stream().anyMatch(id -> actions.count(id) == 0))
        {
            // Every set is checked, so one stop names all that is missing.
            List<Integer> absent = new ArrayList<>();
            for (int id : allGear())
            {
                if (!actions.wearing(id) && actions.count(id) == 0)
                {
                    absent.add(id);
                }
            }
            String items = absent.stream().map(actions::describe).collect(Collectors.joining(", "));
            String them = absent.size() == 1 ? "it" : "them";
            String stop = "Missing " + items + ": carry " + them + " or remove " + them + " from your equipment IDs, then restart the plugin";
            if (trip.getPhase() == TripPhase.FIGHTING)
            {
                // Such as a powered staff that has turned into its uncharged form: stopping here would leave the
                // player in the fight.
                trip.leaveAndStop("Missing " + items, stop);
            }
            else
            {
                actions.fail(stop);
            }
            return;
        }
        NPC target = trip.getTarget();
        Player player = actions.player();
        if (target != null && DreamData.HUSK_IDS.contains(target.getId()) && player != null && player.getInteracting() != null
            && player.getInteracting() == actions.boss())
        {
            // Her husks make her immune, yet a swing at her still spends the attack delay, so the husk is attacked
            // before the swap. The player is held, so the click does not walk.
            status = "Attacking a husk";
            actions.npc(target, "Attack", sent);
            return;
        }
        if (loadout == Loadout.HUSK && missing.size() == 1 && target != null && target.getId() == DreamData.SLEEPWALKER)
        {
            // The last sleepwalker is hit in melee because that lands sooner than a throw, so it is attacked on
            // the swap's tick instead of the next.
            status = "Attacking a sleepwalker";
            actions.attackAndWield(target, missing.get(0), sent);
            return;
        }
        status = "Switching to " + loadout.name().toLowerCase() + " gear";
        Integer button = missing.size() == 1 && (loadout == Loadout.HUSK || loadout == Loadout.PARASITE)
            ? crush.get(missing.get(0)) : null;
        if (button != null && button != actions.styleIndex())
        {
            // Its crush button is known, so the style goes with the weapon instead of a request later.
            actions.wieldWithStyle(missing.get(0), button, sent);
            return;
        }
        actions.equipAll(missing, sent);
    }

    public void reset()
    {
        meleeStyle = -1;
        requestedAt = Integer.MIN_VALUE / 2;
    }
}
