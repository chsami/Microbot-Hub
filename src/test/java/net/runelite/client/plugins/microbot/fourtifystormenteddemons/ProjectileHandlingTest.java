package net.runelite.client.plugins.microbot.fourtifystormenteddemons;

import net.runelite.api.Actor;
import net.runelite.api.Projectile;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.CombatStyle;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.DemonFight;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.TempleData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProjectileHandlingTest
{
    private static final WorldPoint PLAYER = new WorldPoint(4133, 4382, 0);

    @Test
    public void aBombLandingOnOrNextToThePlayerIsCountedWithItsLandingTick() throws Exception
    {
        PluginTestFixture fixture = started();
        WorldPoint own = PLAYER;
        WorldPoint beside = new WorldPoint(4134, 4383, 0);
        move(fixture, bomb(own));
        move(fixture, bomb(beside));
        DemonFight fight = fight(fixture);
        assertEquals(2, fight.getBombs().size());
        assertTrue(fight.getBombs().contains(own));
        assertTrue(fight.getBombs().contains(beside));
        assertEquals(fixture.plugin.getTick() + 1, fight.getBindTick());
    }

    @Test
    public void aBombTwoTilesFromThePlayerBelongsToAnotherFightAndIsIgnored() throws Exception
    {
        PluginTestFixture fixture = started();
        move(fixture, bomb(new WorldPoint(4135, 4382, 0)));
        move(fixture, bomb(new WorldPoint(4133, 4380, 0)));
        move(fixture, bomb(new WorldPoint(4135, 4384, 0)));
        assertTrue(fight(fixture).getBombs().isEmpty());
        assertEquals(Integer.MIN_VALUE / 2, fight(fixture).getBindTick());
    }

    @Test
    public void aBombOnAnotherPlaneOrWithoutATargetPointIsIgnored() throws Exception
    {
        PluginTestFixture fixture = started();
        move(fixture, bomb(new WorldPoint(4133, 4382, 1)));
        move(fixture, bomb(null));
        assertTrue(fight(fixture).getBombs().isEmpty());
    }

    @Test
    public void aProjectileIsJudgedOnItsFirstSightingOnly() throws Exception
    {
        PluginTestFixture fixture = started();
        Projectile far = bomb(new WorldPoint(4140, 4382, 0));
        move(fixture, far);
        fixture.standAt(new WorldPoint(4140, 4382, 0));
        move(fixture, far);
        assertTrue(fight(fixture).getBombs().isEmpty());
        Projectile near = bomb(new WorldPoint(4140, 4383, 0));
        move(fixture, near);
        move(fixture, near);
        assertEquals(1, fight(fixture).getBombs().size());
    }

    @Test
    public void bombsAreIgnoredWhileTheSessionIsNotRunning() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        move(fixture, bomb(PLAYER));
        assertTrue(fight(fixture).getBombs().isEmpty());
    }

    @Test
    public void attackAndBindProjectilesCountOnlyWhenAimedAtThePlayer() throws Exception
    {
        PluginTestFixture fixture = started();
        move(fixture, aimed(TempleData.MAGIC_PROJECTILE, null));
        assertNull(fight(fixture).getStyle());
        move(fixture, aimed(TempleData.BIND_PROJECTILE, null));
        assertEquals(Integer.MIN_VALUE / 2, fight(fixture).getBindTick());
        move(fixture, aimed(TempleData.MAGIC_PROJECTILE, fixture.world.player));
        assertEquals(CombatStyle.MAGIC, fight(fixture).getStyle());
        move(fixture, aimed(TempleData.BIND_PROJECTILE, fixture.world.player));
        assertEquals(fixture.plugin.getTick() + 1, fight(fixture).getBindTick());
        assertNull(fight(fixture).getStyle());
    }

    private static PluginTestFixture started() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        fixture.tick();
        return fixture;
    }

    private static DemonFight fight(PluginTestFixture fixture) throws Exception
    {
        return (DemonFight) fixture.read("fight");
    }

    private static void move(PluginTestFixture fixture, Projectile projectile)
    {
        ProjectileMoved event = new ProjectileMoved();
        event.setProjectile(projectile);
        fixture.plugin.onProjectileMoved(event);
    }

    private static Projectile bomb(WorldPoint target)
    {
        return TestWorld.proxy(Projectile.class, (name, args) ->
        {
            switch (name)
            {
                case "getId":
                    return TempleData.BOMB_PROJECTILE;
                case "getTargetPoint":
                    return target;
                default:
                    return null;
            }
        });
    }

    private static Projectile aimed(int id, Actor target)
    {
        return TestWorld.proxy(Projectile.class, (name, args) ->
        {
            switch (name)
            {
                case "getId":
                    return id;
                case "getTargetActor":
                    return target;
                default:
                    return null;
            }
        });
    }
}
