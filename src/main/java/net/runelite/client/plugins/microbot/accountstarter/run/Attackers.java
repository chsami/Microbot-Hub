package net.runelite.client.plugins.microbot.accountstarter.run;

import java.util.Random;
import java.util.Arrays;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.accountstarter.logic.ApPace;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.accountstarter.logic.AttackerRules;

final class Attackers {
    static final int ESCAPE_TILES = 10;
    private static final int ESCAPE_EXTRA = 3;

    private static final Set<Skill> COMBAT = EnumSet.of(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.HITPOINTS,
            Skill.RANGED, Skill.MAGIC);

    private Attackers() {}

    /**
     * A weak attacker follows a walk and dies in a few hits, so it is killed where we stand. False means walk away:
     * it is not weak, the XP would land in a protected skill, Hitpoints are under half, or the click did nothing.
     */
    static boolean fightBack(Rs2NpcModel attacker, Set<Skill> protectedSkills, ApPace pace, Random random) {
        if (!AttackerRules.fightBack(attacker.getName(), attacker.getCombatLevel())
                || !Collections.disjoint(protectedSkills, COMBAT)) return false;
        int max = Rs2Player.getRealSkillLevel(Skill.HITPOINTS);
        if (max <= 0 || Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS) * 2 < max) return false;
        if (Rs2Player.getInteracting() == attacker.getNpc()) return true;
        Global.sleep((int) pace.reactionMs(random));
        return attacker.click("Attack") && Global.sleepUntil(() -> Rs2Player.getInteracting() == attacker.getNpc(), 2400);
    }

    /** Attack-option NPCs only: random-event NPCs also face us, and walking away just drags them along. */
    static Rs2NpcModel onUs(int except) {
        Player me = Microbot.getClient().getLocalPlayer();
        return Microbot.getRs2NpcCache().query()
                .where(n -> n.getIndex() != except && n.getInteracting() == me && !n.isDead() && n.getHealthRatio() != 0
                        && attackable(n))
                .nearestOnClientThread();
    }

    private static boolean attackable(Rs2NpcModel n) {
        NPCComposition c = n.getNpc() == null ? null : n.getNpc().getTransformedComposition();
        return c != null && c.getActions() != null && Arrays.asList(c.getActions()).contains("Attack");
    }

    static boolean escape(Rs2NpcModel from, ApPace pace, Random random) {
        WorldPoint me = Rs2Player.getWorldLocation();
        WorldPoint them = from.getWorldLocation();
        if (me == null) return false;
        int dx = them == null ? 0 : Integer.signum(me.getX() - them.getX());
        int dy = them == null ? 0 : Integer.signum(me.getY() - them.getY());
        if (dx == 0 && dy == 0) dx = random.nextBoolean() ? 1 : -1;
        int[][] dirs = {{dx, dy}, {dx, 0}, {0, dy}, {-dy, dx}, {dy, -dx}};
        int far = ESCAPE_TILES + random.nextInt(ESCAPE_EXTRA + 1);
        for (int[] d : dirs) {
            if (d[0] == 0 && d[1] == 0) continue;
            for (int k = far; k >= ESCAPE_TILES; k--) {
                WorldPoint p = me.dx(d[0] * k).dy(d[1] * k);
                if (!Rs2Tile.isWalkable(p)) continue;
                Global.sleep((int) pace.reactionMs(random));
                return Rs2Walker.walkTo(p, 1);
            }
        }
        return false;
    }
}
