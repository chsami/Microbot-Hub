package net.runelite.client.plugins.microbot.attackrangesplus;

import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.WorldType;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.kit.KitType;
import net.runelite.client.plugins.microbot.util.player.Rs2Pvp;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.GeneralPath;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * Draws the tiles you can actually attack: the Chebyshev attack square clipped to the tiles in line
 * of sight, outlined along its boundary so it "molds" around walls. Optionally also draws the same
 * for the player you are fighting (their weapon's reach), clipped to their line of sight.
 *
 * <p>Performance: the line-of-sight set and the projected fill/outline paths are cached per region.
 * The LOS set is rebuilt when the region's origin/radius changes or once per game tick (so dynamic
 * obstacles that change reachability are picked up); the projected paths are rebuilt on those changes
 * or when the camera moves. A static scene therefore paints one fill plus one stroke per region per
 * frame instead of doing work per tile every frame.</p>
 */
class AttackRangesPlusOverlay extends Overlay
{
    private static final int HALF = Perspective.LOCAL_HALF_TILE_SIZE;

    private final Client client;
    private final AttackRangesPlusCalc rangesCalc;
    private final AttackRangesPlusConfig config;

    private final Region playerRegion = new Region();
    private final Region opponentRegion = new Region();

    // Shared camera state; a change invalidates every region's projected paths.
    private boolean haveCamera = false;
    private int camX, camY, camZ, camPitch, camYaw;

    // Radius derivation (and the line-of-sight set) are refreshed at most once per game tick rather
    // than per render frame: the resolved radius depends on an enum/struct varbit chain that does not
    // change between ticks, and recomputing the LOS set each tick picks up dynamic obstacles (doors,
    // gates, walls) that change reachability while the player stands still.
    private int lastTick = -1;
    private int cachedPlayerRadius;
    private int cachedOpponentWeaponId = Integer.MIN_VALUE;
    private int cachedOpponentRadius;

    @Inject
    private AttackRangesPlusOverlay(Client client, AttackRangesPlusCalc rangesCalc, AttackRangesPlusConfig config)
    {
        this.client = client;
        this.rangesCalc = rangesCalc;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
        setPriority(OverlayPriority.MED);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!shouldDisplay())
        {
            return null;
        }

        final Player local = client.getLocalPlayer();
        if (local == null)
        {
            return null;
        }
        final WorldView wv = local.getWorldView();
        if (wv == null)
        {
            return null;
        }

        final int ncamX = client.getCameraX();
        final int ncamY = client.getCameraY();
        final int ncamZ = client.getCameraZ();
        final int ncamPitch = client.getCameraPitch();
        final int ncamYaw = client.getCameraYaw();
        final boolean cameraChanged = !haveCamera
                || ncamX != camX || ncamY != camY || ncamZ != camZ
                || ncamPitch != camPitch || ncamYaw != camYaw;

        final int tick = client.getTickCount();
        final boolean tickChanged = tick != lastTick;
        if (tickChanged)
        {
            cachedPlayerRadius = rangesCalc.getPlayerRangeRadius();
            lastTick = tick;
        }

        final boolean havePlayer = playerRegion.update(
                local.getWorldArea(), wv, local.getWorldLocation(), cachedPlayerRadius, cameraChanged, tickChanged);

        boolean haveOpponent = false;
        if (config.showOpponent())
        {
            final Actor target = local.getInteracting();
            if (target instanceof Player && target != local)
            {
                final Player opp = (Player) target;
                final int weaponId = opp.getPlayerComposition() != null
                        ? opp.getPlayerComposition().getEquipmentId(KitType.WEAPON)
                        : -1;
                if (weaponId != cachedOpponentWeaponId)
                {
                    cachedOpponentRadius = rangesCalc.getWeaponRadius(weaponId);
                    cachedOpponentWeaponId = weaponId;
                }
                haveOpponent = opponentRegion.update(
                        opp.getWorldArea(), wv, opp.getWorldLocation(), cachedOpponentRadius, cameraChanged, tickChanged);
            }
            else
            {
                opponentRegion.clear();
                cachedOpponentWeaponId = Integer.MIN_VALUE;
            }
        }
        else
        {
            opponentRegion.clear();
            cachedOpponentWeaponId = Integer.MIN_VALUE;
        }

        camX = ncamX;
        camY = ncamY;
        camZ = ncamZ;
        camPitch = ncamPitch;
        camYaw = ncamYaw;
        haveCamera = true;

        if (havePlayer)
        {
            paint(graphics, playerRegion, config.borderColor(), config.fillColor());
        }
        if (haveOpponent)
        {
            paint(graphics, opponentRegion, config.opponentColor(), faint(config.opponentColor()));
        }
        return null;
    }

    private void paint(Graphics2D graphics, Region region, Color border, Color fill)
    {
        if (config.showFill() && region.fill != null)
        {
            // The fill needs no edge antialiasing (the outline covers its border); turning AA off
            // here makes the per-frame area fill a bit cheaper.
            final Object aa = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            graphics.setColor(fill);
            graphics.fill(region.fill);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    aa != null ? aa : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
        }
        if (region.outline != null)
        {
            graphics.setColor(border);
            graphics.draw(region.outline);
        }
    }

    private static Color faint(Color c)
    {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), 40);
    }

    private boolean shouldDisplay()
    {
        switch (config.displayMode())
        {
            case WILDERNESS_ONLY:
                return Rs2Pvp.isInWilderness();
            case IN_PVP_AREAS:
                return inPvpSituation();
            case ALWAYS:
            default:
                return true;
        }
    }

    private boolean inPvpSituation()
    {
        if (Rs2Pvp.isInWilderness())
        {
            return true;
        }
        final EnumSet<WorldType> worldTypes = client.getWorldType();
        if (worldTypes != null && (worldTypes.contains(WorldType.PVP) || worldTypes.contains(WorldType.DEADMAN)))
        {
            return true;
        }
        return client.getVarbitValue(VarbitID.PVP_AREA_CLIENT) == 1;
    }

    private Set<WorldPoint> computeAttackable(WorldArea area, WorldView wv, WorldPoint origin, int radius)
    {
        final int plane = origin.getPlane();
        final Set<WorldPoint> tiles = new HashSet<>();
        for (int dx = -radius; dx <= radius; dx++)
        {
            for (int dy = -radius; dy <= radius; dy++)
            {
                WorldPoint tile = new WorldPoint(origin.getX() + dx, origin.getY() + dy, plane);
                if (area.hasLineOfSightTo(wv, tile))
                {
                    tiles.add(tile);
                }
            }
        }
        return tiles;
    }

    private Point project(int localX, int localY, WorldView wv, int plane)
    {
        return Perspective.localToCanvas(client, new LocalPoint(localX, localY, wv), plane);
    }

    private static void segment(GeneralPath path, Point a, Point b)
    {
        path.moveTo(a.getX(), a.getY());
        path.lineTo(b.getX(), b.getY());
    }

    /**
     * One attackable area (the player's, or the opponent's) with its own caches.
     */
    private final class Region
    {
        private WorldPoint origin;
        private int radius = -1;
        private Set<WorldPoint> set = new HashSet<>();
        private GeneralPath fill;
        private GeneralPath outline;

        boolean update(WorldArea area, WorldView wv, WorldPoint o, int r, boolean cameraChanged, boolean tickChanged)
        {
            if (area == null || o == null || r <= 0)
            {
                clear();
                return false;
            }

            // A new tick forces an LOS recompute even when the origin and radius are unchanged, so a
            // door/gate/wall toggling reachability under a stationary player is picked up.
            final boolean setChanged = !o.equals(origin) || r != radius || tickChanged;
            if (setChanged)
            {
                set = computeAttackable(area, wv, o, r);
                origin = o;
                radius = r;
            }
            if (setChanged || cameraChanged || fill == null)
            {
                buildPaths(wv, o.getPlane());
            }
            return outline != null;
        }

        void clear()
        {
            origin = null;
            radius = -1;
            fill = null;
            outline = null;
            if (!set.isEmpty())
            {
                set = new HashSet<>();
            }
        }

        private void buildPaths(WorldView wv, int plane)
        {
            final GeneralPath f = new GeneralPath(GeneralPath.WIND_NON_ZERO);
            final GeneralPath o = new GeneralPath();

            for (WorldPoint tile : set)
            {
                final LocalPoint lp = LocalPoint.fromWorld(client, tile);
                if (lp == null)
                {
                    continue; // off-scene
                }
                final int cx = lp.getX();
                final int cy = lp.getY();

                final Point sw = project(cx - HALF, cy - HALF, wv, plane);
                final Point se = project(cx + HALF, cy - HALF, wv, plane);
                final Point ne = project(cx + HALF, cy + HALF, wv, plane);
                final Point nw = project(cx - HALF, cy + HALF, wv, plane);

                if (sw != null && se != null && ne != null && nw != null)
                {
                    f.moveTo(sw.getX(), sw.getY());
                    f.lineTo(se.getX(), se.getY());
                    f.lineTo(ne.getX(), ne.getY());
                    f.lineTo(nw.getX(), nw.getY());
                    f.closePath();
                }

                if (!set.contains(tile.dy(1)) && nw != null && ne != null)   // north
                {
                    segment(o, nw, ne);
                }
                if (!set.contains(tile.dy(-1)) && sw != null && se != null)  // south
                {
                    segment(o, sw, se);
                }
                if (!set.contains(tile.dx(1)) && se != null && ne != null)   // east
                {
                    segment(o, se, ne);
                }
                if (!set.contains(tile.dx(-1)) && sw != null && nw != null)  // west
                {
                    segment(o, sw, nw);
                }
            }

            fill = f;
            outline = o;
        }
    }
}
