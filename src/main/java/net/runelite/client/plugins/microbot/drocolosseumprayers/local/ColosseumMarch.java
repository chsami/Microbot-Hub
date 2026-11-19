package net.runelite.client.plugins.microbot.drocolosseumprayers.local;

import java.util.*;
import static net.runelite.client.plugins.microbot.drocolosseumprayers.local.ColosseumSnapshot.*;
import net.runelite.client.plugins.microbot.drocolosseumprayers.local.ColosseumCollision.Tile;

final class ColosseumMarch
{
    static final Set<Role> SHOOTERS = EnumSet.of(Role.SHAMAN, Role.JAVELIN, Role.MANTICORE, Role.SHOCKWAVE);
    static String style(Npc n) { return n.role == Role.MANTICORE ? "volley" : n.role == Role.JAVELIN ? "ranged" : "magic"; }

    private ColosseumMarch() {}

    static final class Result
    {
        final Map<Integer, Tile> rest = new HashMap<>();
        final Set<Integer> reach = new HashSet<>();
    }

    static boolean marches(Npc n)
    {
        return n.alive() && n.enemy() && (SHOOTERS.contains(n.role) || n.role == Role.JAGUAR || n.role == Role.MINOTAUR);
    }

    static int range(Npc n) { return n.role == Role.SHAMAN ? 10 : 15; }

    private static boolean stands(Npc n)
    {
        return n.alive() && n.enemy() && n.role != Role.FLARE && n.role != Role.BEES && !marches(n) && (WARBAND_BLOCKS || !n.warband());
    }

    static final boolean WARBAND_BLOCKS = "true".equals(System.getProperty("colo.marchWarband"));

    static Result run(ColosseumSnapshot f, ColosseumCollision collision, Tile start)
    {
        Result r = new Result();
        List<Npc> order = new ArrayList<>();
        Map<Integer, Tile> at = new HashMap<>();
        for (Npc n : f.npcs)
        {
            if (marches(n))
            {
                order.add(n);
                at.put(n.index, new Tile(n.x, n.y));
            }
            else if (stands(n))
            {
                at.put(n.index, new Tile(n.x, n.y));
            }
        }
        order.sort(Comparator.comparingInt(n -> n.index));
        Tile player = start;
        for (Npc n : order)
        {
            Tile here = at.get(n.index);
            if (n.meleeOnly())
            {
                if (collision.meleeFrom(n, here, player))
                {
                    r.rest.put(n.index, here);
                    continue;
                }
            }
            else if (collision.firingFrom(n, here, player, range(n)))
            {
                r.rest.put(n.index, here);
                continue;
            }
            Tile next = collision.marchStep(n, here, player, at, range(n));
            at.put(n.index, next);
            r.rest.put(n.index, next);
        }
        for (Npc n : order)
        {
            Tile here = r.rest.get(n.index);
            if (n.meleeOnly() && here != null && collision.meleeFrom(n, here, player))
            {
                r.reach.add(n.index);
            }
        }
        return r;
    }
}
