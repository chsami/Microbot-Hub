package net.runelite.client.plugins.microbot.fourtifysbarrows.util.action;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class TunnelMonsterTest
{
    @Test
    public void skipsCloserMonsterBehindAClosedDoor()
    {
        NPC behindDoor = npc("Skeleton", new WorldPoint(3550, 9700, 0));
        NPC inRoom = npc("Bloodworm", new WorldPoint(3540, 9700, 0));
        Map<NPC, Integer> distance = Map.of(behindDoor, 2, inRoom, 6);
        Set<WorldPoint> reachable = Set.of(inRoom.getWorldLocation());

        assertSame(inRoom, BarrowsActions.nearestTunnelMonster(List.of(behindDoor, inRoom), reachable, distance::get, 800, null));
    }

    @Test
    public void noReachableMonsterReturnsNoTarget()
    {
        NPC behindDoor = npc("Skeleton", new WorldPoint(3550, 9700, 0));

        assertNull(BarrowsActions.nearestTunnelMonster(List.of(behindDoor), Set.of(), candidate -> 2, 800, null));
    }

    @Test
    public void fightsTheMonsterAlreadyAttackingInSingleCombat()
    {
        Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
            (proxy, method, args) -> method.getName().equals("hashCode") ? 1 : method.getName().equals("equals") ? proxy == args[0] : null);
        NPC nearer = npc("Skeleton", new WorldPoint(3540, 9700, 0));
        NPC attacker = npc("Bloodworm", new WorldPoint(3542, 9700, 0), player);
        Map<NPC, Integer> distance = Map.of(nearer, 1, attacker, 3);
        Set<WorldPoint> reachable = Set.of(nearer.getWorldLocation(), attacker.getWorldLocation());

        assertSame(attacker, BarrowsActions.nearestTunnelMonster(List.of(nearer, attacker), reachable, distance::get, 800, attacker));
    }

    @Test
    public void attackerOverTheRewardCapBlocksOtherTargets()
    {
        Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
            (proxy, method, args) -> method.getName().equals("hashCode") ? 1 : method.getName().equals("equals") ? proxy == args[0] : null);
        NPC fitsUnderCap = npc("Crypt rat", new WorldPoint(3540, 9700, 0), null, 10);
        NPC attacker = npc("Bloodworm", new WorldPoint(3542, 9700, 0), player);
        Set<WorldPoint> reachable = Set.of(fitsUnderCap.getWorldLocation(), attacker.getWorldLocation());

        assertNull(BarrowsActions.nearestTunnelMonster(List.of(fitsUnderCap, attacker), reachable, candidate -> 1, 865, attacker));
    }

    @Test
    public void rejectedTargetHandsOffToTheOtherSameNamedRatThatIsAttacking()
    {
        Player player = player();
        NPC targeted = npc("Crypt rat", new WorldPoint(3540, 9700, 0), player);
        NPC attacking = npc("Crypt rat", new WorldPoint(3542, 9700, 0), player);
        List<NPC> npcs = List.of(targeted, attacking);

        assertSame(targeted, BarrowsActions.tunnelAttacker(npcs, player, targeted, null, Integer.MIN_VALUE, npc -> Integer.MIN_VALUE), "our own target still counts while the game accepts it");
        assertSame(attacking, BarrowsActions.tunnelAttacker(npcs, player, targeted, targeted, 100, npc -> Integer.MIN_VALUE));
    }

    @Test
    public void theSameNamedRatThatSwingsAtUsIsTheAttackerEvenWhileWeTargetTheOther()
    {
        Player player = player();
        NPC targeted = npc("Crypt rat", new WorldPoint(3540, 9700, 0), player);
        NPC attacking = npc("Crypt rat", new WorldPoint(3542, 9700, 0), player);
        Map<NPC, Integer> swings = new java.util.IdentityHashMap<>();
        swings.put(attacking, 97);

        assertSame(attacking, BarrowsActions.tunnelAttacker(List.of(targeted, attacking), player, targeted, null,
            Integer.MIN_VALUE, npc -> swings.getOrDefault(npc, Integer.MIN_VALUE)));
    }

    @Test
    public void theAttackerStaysTheAttackerBetweenSwingsWhileItStillFacesUs()
    {
        Player player = player();
        NPC targeted = npc("Crypt rat", new WorldPoint(3540, 9700, 0), player);
        NPC attacking = npc("Crypt rat", new WorldPoint(3542, 9700, 0), player);
        Map<NPC, Integer> swings = new java.util.IdentityHashMap<>();
        swings.put(attacking, 97);

        // No tick argument: a slow attacker or a missed animation must not hand the fight back to our own target.
        assertSame(attacking, BarrowsActions.tunnelAttacker(List.of(targeted, attacking), player, targeted, null,
            Integer.MIN_VALUE, npc -> swings.getOrDefault(npc, Integer.MIN_VALUE)));
    }

    @Test
    public void theAttackerIsForgottenOnceItStopsFacingUs()
    {
        Player player = player();
        NPC targeted = npc("Crypt rat", new WorldPoint(3540, 9700, 0), player);
        NPC wandered = npc("Crypt rat", new WorldPoint(3542, 9700, 0), null);
        Map<NPC, Integer> swings = new java.util.IdentityHashMap<>();
        swings.put(wandered, 97);

        assertSame(targeted, BarrowsActions.tunnelAttacker(List.of(targeted, wandered), player, targeted, null,
            Integer.MIN_VALUE, npc -> swings.getOrDefault(npc, Integer.MIN_VALUE)));
    }

    @Test
    public void aRejectedRatThatLaterSwingsAtUsBecomesTheAttacker()
    {
        Player player = player();
        NPC rat = npc("Crypt rat", new WorldPoint(3540, 9700, 0), player);
        Map<NPC, Integer> swings = new java.util.IdentityHashMap<>();
        swings.put(rat, 105);

        assertSame(rat, BarrowsActions.tunnelAttacker(List.of(rat), player, rat, rat, 100,
            npc -> swings.getOrDefault(npc, Integer.MIN_VALUE)));
        assertNull(BarrowsActions.tunnelAttacker(List.of(rat), player, rat, rat, 110, npc -> swings.getOrDefault(npc, Integer.MIN_VALUE)), "a swing from before the rejection does not lift it");
    }

    private static Player player()
    {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
            (proxy, method, args) -> method.getName().equals("hashCode") ? 1 : method.getName().equals("equals") ? proxy == args[0] : null);
    }

    private static NPC npc(String name, WorldPoint location)
    {
        return npc(name, location, null);
    }

    private static NPC npc(String name, WorldPoint location, Player target)
    {
        return npc(name, location, target, 20);
    }

    private static NPC npc(String name, WorldPoint location, Player target, int level)
    {
        return (NPC) Proxy.newProxyInstance(NPC.class.getClassLoader(), new Class<?>[] {NPC.class}, (proxy, method, args) ->
        {
            switch (method.getName())
            {
                case "getName":
                    return name;
                case "getWorldLocation":
                    return location;
                case "getCombatLevel":
                    return level;
                case "isDead":
                    return false;
                case "getInteracting":
                    return target;
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                default:
                    return null;
            }
        });
    }
}
