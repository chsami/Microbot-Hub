package net.runelite.client.plugins.microbot.vorkathhelper;
import lombok.Getter;
import lombok.Setter;
import net.runelite.api.Projectile;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
public class VorkathVars
{
    private Rs2NpcModel vorkath;
    private Rs2NpcModel zombieSpawn;
    private boolean acidPhase;
    private int attackCount;
    private int lastVorkathAnimation = -1;
    private Projectile fireballProjectile;
    private VorkathHelperScript.VorkathSpecial nextSpecial = VorkathHelperScript.VorkathSpecial.SPAWN_SPEC;
    private final Set<WorldPoint> acidTiles = new HashSet<>();
    private final Set<Projectile> trackedAcidProjectiles = new HashSet<>();

    public void reset()
    {
        vorkath = null;
        zombieSpawn = null;
        fireballProjectile = null;
        acidPhase = false;
        attackCount = 0;
        lastVorkathAnimation = -1;
        nextSpecial = VorkathHelperScript.VorkathSpecial.SPAWN_SPEC;
        acidTiles.clear();
        trackedAcidProjectiles.clear();
    }
}
