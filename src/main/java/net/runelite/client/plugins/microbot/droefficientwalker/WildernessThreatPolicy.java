package net.runelite.client.plugins.microbot.droefficientwalker;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.WorldType;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Pvp;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Collections;

/** Shared player-alert evaluation. The caller chooses logout, teleport or another response. */
public final class WildernessThreatPolicy
{
    // Bounds only identify Ferox; Rs2Pvp's existing safe-zone polygon decides
    // which tiles inside these bounds are actually protected, including at gates.
    private static final WorldArea FEROX_BOUNDS = new WorldArea(3117, 3609, 45, 39, 0);
    private WildernessThreatPolicy() { }

    /** Reads the same Wilderness combat range and proximity rules used by Efficient Walker.
     * Call on the client thread; the result holds values rather than live player references.
     */
    public static Snapshot evaluate(Client client, int radius, int upperCombatBuffer)
    {
        if (client == null || client.getGameState() != GameState.LOGGED_IN
            || client.getLocalPlayer() == null)
        {
            return Snapshot.NONE;
        }
        Player local = client.getLocalPlayer();
        if (local.getWorldView() == null) return Snapshot.NONE;
        WorldPoint here = local.getWorldLocation();
        List<NearbyPlayer> players = new ArrayList<>();
        Microbot.getRs2PlayerCache().getStream().forEach(model ->
        {
            Player player = model.getPlayer();
            // A cached boat occupant may outlive its world view during a teleport.
            if (player != null && player != local && player.getWorldView() != null)
            {
                players.add(new NearbyPlayer(player.getWorldLocation(), player.getCombatLevel(),
                    player.getName() != null, player.getInteracting() == local,
                    player.getAnimation(), player.getId()));
            }
        });
        return evaluate(here, local.getCombatLevel(), WorldType.isPvpWorld(client.getWorldType()),
            WorldType.isDeadmanWorld(client.getWorldType()), local.getInteracting() instanceof Player,
            players, radius, upperCombatBuffer);
    }

    static Snapshot evaluate(WorldPoint here, int localCombat, boolean pvpWorld, boolean deadmanWorld,
        boolean localInteractingWithPlayer, List<NearbyPlayer> players, int radius, int upperCombatBuffer)
    {
        if (here == null) return Snapshot.NONE;
        int wildernessLevel = Rs2Pvp.getWildernessLevelFrom(here);
        if (wildernessLevel <= 0) return Snapshot.NONE;

        int attackRange = wildernessLevel + (pvpWorld ? 15 : 0);
        int minimum = deadmanWorld ? 3 : Math.max(3, localCombat - attackRange);
        int maximum = deadmanWorld ? 126 : Math.min(126, localCombat + attackRange + upperCombatBuffer);
        boolean pvpCombat = localInteractingWithPlayer;
        boolean nearbyThreat = false;
        Set<Integer> threatPlayers = new HashSet<>();
        Set<Integer> attackingPlayers = new HashSet<>();
        if (localInteractingWithPlayer) threatPlayers.add(Integer.MIN_VALUE);
        if (players != null)
        {
            for (NearbyPlayer player : players)
            {
                if (player == null) continue;
                if (player.location != null && FEROX_BOUNDS.contains(player.location)
                    && Rs2Pvp.getWildernessLevelFrom(player.location) == 0) continue;
                // Walker historically detects an attacker even before its name arrives.
                pvpCombat |= player.interactingWithLocal;
                if (player.interactingWithLocal) threatPlayers.add(player.id);
                if (WildernessPvpActivity.isAttackAction(player.interactingWithLocal, player.animation))
                    attackingPlayers.add(player.id);
                if (player.named && player.location != null && player.combat >= minimum
                    && player.combat <= maximum && here.distanceTo(player.location) <= radius)
                {
                    nearbyThreat = true;
                    threatPlayers.add(player.id);
                }
            }
        }
        return new Snapshot(wildernessLevel, nearbyThreat, pvpCombat, threatPlayers, attackingPlayers);
    }

    static boolean mayAttemptTravelLogout(boolean combatGrace) { return !combatGrace; }

    static final class NearbyPlayer
    {
        final WorldPoint location;
        final int combat;
        final boolean named;
        final boolean interactingWithLocal;
        final int animation;
        final int id;

        NearbyPlayer(WorldPoint location, int combat, boolean named, boolean interactingWithLocal)
        {
            this(location, combat, named, interactingWithLocal, -1, 0);
        }

        NearbyPlayer(WorldPoint location, int combat, boolean named, boolean interactingWithLocal,
            int animation, int id)
        {
            this.location = location;
            this.combat = combat;
            this.named = named;
            this.interactingWithLocal = interactingWithLocal;
            this.animation = animation;
            this.id = id;
        }
    }

    public static final class Snapshot
    {
        private static final Snapshot NONE = new Snapshot(0, false, false, Collections.emptySet(), Collections.emptySet());
        private final int wildernessLevel;
        private final boolean attackCapablePlayerNearby;
        private final boolean pvpCombat;
        private final Set<Integer> threatPlayers;
        private final Set<Integer> attackingPlayers;

        private Snapshot(int wildernessLevel, boolean attackCapablePlayerNearby, boolean pvpCombat,
            Set<Integer> threatPlayers, Set<Integer> attackingPlayers)
        {
            this.wildernessLevel = wildernessLevel;
            this.attackCapablePlayerNearby = attackCapablePlayerNearby;
            this.pvpCombat = pvpCombat;
            this.threatPlayers = Collections.unmodifiableSet(new HashSet<>(threatPlayers));
            this.attackingPlayers = Collections.unmodifiableSet(new HashSet<>(attackingPlayers));
        }

        public static Snapshot empty() { return NONE; }
        public int getWildernessLevel() { return wildernessLevel; }
        public boolean isAttackCapablePlayerNearby() { return attackCapablePlayerNearby; }
        public boolean isPvpCombat() { return pvpCombat; }
        public boolean hasThreat() { return attackCapablePlayerNearby || pvpCombat; }
        public boolean hasAttackAction() { return !attackingPlayers.isEmpty(); }
        Set<Integer> threatPlayers() { return threatPlayers; }
        Set<Integer> attackingPlayers() { return attackingPlayers; }
    }
}
