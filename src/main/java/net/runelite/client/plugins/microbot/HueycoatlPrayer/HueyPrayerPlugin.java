package net.runelite.client.plugins.microbot.HueycoatlPrayer;

import com.google.inject.Provides;
import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

import net.runelite.api.Client;
import net.runelite.api.HeadIcon;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Projectile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;

import net.runelite.api.events.GameTick;
import net.runelite.api.events.ProjectileMoved;

import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Slf4j
@PluginDescriptor(
        name = PluginConstants.DEFAULT_PREFIX + "Huey Prayer",
        description = "Auto prayer vs Hueycoatl (3-style projectile)",
        tags = {"microbot", "huey", "prayer"},
        version = HueyPrayerPlugin.VERSION,
        minClientVersion = "2.1.34",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class HueyPrayerPlugin extends Plugin
{
    static final String VERSION = "1.0.9";
    @Inject
    private Client client;

    @Inject
    private HueyPrayerConfig config;

    private static final int MAGIC_PROJECTILE_ID = 2975;
    private static final int RANGE_PROJECTILE_ID = 2972;
    private static final int MELEE_PROJECTILE_ID = 2969;

    private Rs2PrayerEnum currentPrayer = null;
    private int lastSwitchTick = -1;

    /** End cycle snapshotted when each Huey projectile was first seen. */
    private final Map<Projectile, Integer> incomingEndCycles = new IdentityHashMap<>();

    /** After the last tracked projectile lands, trio mode keeps balancing until the next one. */
    private boolean pillarWindow;

    @Provides
    HueyPrayerConfig provideConfig(net.runelite.client.config.ConfigManager configManager)
    {
        return configManager.getConfig(HueyPrayerConfig.class);
    }

    @Override
    protected void startUp()
    {
        currentPrayer = null;
        lastSwitchTick = -1;
        incomingEndCycles.clear();
        pillarWindow = false;
        Microbot.log("Huey Prayer started");
    }

    @Override
    protected void shutDown()
    {
        Rs2Prayer.disableAllPrayers();
        currentPrayer = null;
        incomingEndCycles.clear();
        pillarWindow = false;
    }

    @Subscribe
    public void onProjectileMoved(ProjectileMoved event)
    {
        if (!config.enabled())
        {
            return;
        }

        Projectile projectile = event.getProjectile();
        if (projectile == null)
        {
            return;
        }

        // Only react to projectiles targeting YOU
        if (projectile.getInteracting() != client.getLocalPlayer())
        {
            return;
        }

        int id = projectile.getId();
        boolean isHueyProjectile = false;
        if (id == MAGIC_PROJECTILE_ID)
        {
            isHueyProjectile = true;
        }
        if (id == RANGE_PROJECTILE_ID)
        {
            isHueyProjectile = true;
        }
        if (id == MELEE_PROJECTILE_ID)
        {
            isHueyProjectile = true;
        }
        if (!isHueyProjectile)
        {
            return;
        }

        if (!config.disableAfterImpact())
        {
            incomingEndCycles.clear();
            pillarWindow = false;
        }

        if (incomingEndCycles.containsKey(projectile))
        {
            return;
        }

        if (projectile.getRemainingCycles() <= 0)
        {
            return;
        }

        if (config.debug())
        {
            Microbot.log("Projectile ID: " + id);
        }

        if (config.disableAfterImpact())
        {
            incomingEndCycles.put(projectile, projectile.getEndCycle());
        }
        pillarWindow = false;

        switch (id)
        {
            case MAGIC_PROJECTILE_ID:
                switchPrayer(Rs2PrayerEnum.PROTECT_MAGIC);
                break;

            case RANGE_PROJECTILE_ID:
                // Rs2PrayerEnum uses PROTECT_RANGE for ranged protection
                switchPrayer(Rs2PrayerEnum.PROTECT_RANGE);
                break;

            case MELEE_PROJECTILE_ID:
                switchPrayer(Rs2PrayerEnum.PROTECT_MELEE);
                break;
        }
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (!config.enabled())
        {
            return;
        }
        if (!fightStillNeedsPrayer())
        {
            releaseManagedPrayers();
            return;
        }
        if (!config.disableAfterImpact())
        {
            incomingEndCycles.clear();
            pillarWindow = false;
            return;
        }
        pruneLandedProjectiles();
        if (!pillarWindow)
        {
            return;
        }
        if (!incomingEndCycles.isEmpty())
        {
            return;
        }
        if (!config.trioMode())
        {
            return;
        }
        applyTrioProtection();
    }

    /**
     * ProjectileMoved stops while the projectile is still in the air, so impact is
     * decided from the end cycle captured when it was first seen.
     */
    private void pruneLandedProjectiles()
    {
        int cycle = client.getGameCycle();
        boolean removed = false;
        Iterator<Map.Entry<Projectile, Integer>> it = incomingEndCycles.entrySet().iterator();
        while (it.hasNext())
        {
            Map.Entry<Projectile, Integer> entry = it.next();
            Integer endCycle = entry.getValue();
            if (endCycle == null)
            {
                it.remove();
                removed = true;
                continue;
            }
            if (HueyPrayerBalance.hasLanded(cycle, endCycle))
            {
                it.remove();
                removed = true;
            }
        }
        if (!removed)
        {
            return;
        }
        if (!incomingEndCycles.isEmpty())
        {
            return;
        }
        onHueyProjectilePhaseEnded();
    }

    private void switchPrayer(Rs2PrayerEnum prayer)
    {
        int tick = client.getTickCount();

        if (Rs2Prayer.isPrayerActive(prayer))
        {
            currentPrayer = prayer;
            return;
        }

        if (tick == lastSwitchTick)
        {
            return;
        }

        Rs2Prayer.disableAllPrayers();
        Rs2Prayer.toggle(prayer, true);

        currentPrayer = prayer;
        lastSwitchTick = tick;
    }

    /**
     * Last Huey projectile toward us has landed — either clear prayers or switch to trio pillar protection.
     */
    private void onHueyProjectilePhaseEnded()
    {
        if (!fightStillNeedsPrayer())
        {
            releaseManagedPrayers();
            return;
        }
        if (config.trioMode())
        {
            pillarWindow = true;
            applyTrioProtection();
            return;
        }
        pillarWindow = false;
        Rs2Prayer.disableAllPrayers();
        currentPrayer = null;
    }

    /**
     * Trio and attack prayers are only held in the arena while Huey is alive.
     * A defeated head, a respawn placeholder, or leaving the instance turns them off
     * until the next kill's first projectile.
     */
    private boolean fightStillNeedsPrayer()
    {
        Player local = client.getLocalPlayer();
        if (local == null)
        {
            return false;
        }
        WorldView worldView = local.getWorldView();
        if (worldView == null)
        {
            worldView = client.getTopLevelWorldView();
        }
        if (worldView == null)
        {
            return false;
        }
        boolean inArena = false;
        boolean hueyAlive = false;
        for (NPC npc : worldView.npcs())
        {
            if (npc == null)
            {
                continue;
            }
            int id = npc.getId();
            if (!HueyPrayerBalance.isArenaPresence(id))
            {
                continue;
            }
            inArena = true;
            if (HueyPrayerBalance.isLivingForm(id))
            {
                if (!npc.isDead())
                {
                    hueyAlive = true;
                }
            }
        }
        return HueyPrayerBalance.shouldHoldPrayers(inArena, hueyAlive);
    }

    private void releaseManagedPrayers()
    {
        incomingEndCycles.clear();
        if (!pillarWindow)
        {
            if (currentPrayer == null)
            {
                return;
            }
        }
        pillarWindow = false;
        Rs2Prayer.disableAllPrayers();
        currentPrayer = null;
    }

    private void applyTrioProtection()
    {
        Rs2PrayerEnum pillar = resolveTrioProtectionPrayer();
        if (pillar == null)
        {
            return;
        }
        if (pillar == currentPrayer)
        {
            if (Rs2Prayer.isPrayerActive(pillar))
            {
                return;
            }
        }
        switchPrayer(pillar);
    }

    /**
     * Fixed or autobalance protection for pillar charging (after projectiles, not during).
     */
    private Rs2PrayerEnum resolveTrioProtectionPrayer()
    {
        if (config.trioRoleStyle() == HueyPrayerConfig.TrioRoleStyle.FIXED)
        {
            return config.trioFixedProtection().getPrayer();
        }
        return pickAutobalanceProtectionPrayer();
    }

    private Rs2PrayerEnum pickAutobalanceProtectionPrayer()
    {
        Player local = client.getLocalPlayer();
        if (local == null)
        {
            return null;
        }
        WorldView worldView = local.getWorldView();
        if (worldView == null)
        {
            worldView = client.getTopLevelWorldView();
        }
        if (worldView == null)
        {
            return null;
        }

        int radius = config.trioRadius();
        if (radius < 1)
        {
            radius = 1;
        }
        boolean countWholeArena = worldView.isInstance();
        LocalPoint localTile = local.getLocalLocation();
        String myName = local.getName();

        int melee = 0;
        int ranged = 0;
        int magic = 0;
        List<String> groupNames = new ArrayList<>();
        List<String> namesOnMyStyle = new ArrayList<>();
        if (myName != null)
        {
            groupNames.add(myName);
        }

        for (Player p : worldView.players())
        {
            if (p == null)
            {
                continue;
            }
            if (p == local)
            {
                continue;
            }
            String name = p.getName();
            if (myName != null)
            {
                if (myName.equals(name))
                {
                    continue;
                }
            }
            if (!countWholeArena)
            {
                if (!withinRadius(localTile, p.getLocalLocation(), radius))
                {
                    continue;
                }
            }
            if (name != null)
            {
                groupNames.add(name);
            }
            Rs2PrayerEnum style = overheadToPrayer(p.getOverheadIcon());
            if (style == Rs2PrayerEnum.PROTECT_MELEE)
            {
                melee++;
            }
            else if (style == Rs2PrayerEnum.PROTECT_RANGE)
            {
                ranged++;
            }
            else if (style == Rs2PrayerEnum.PROTECT_MAGIC)
            {
                magic++;
            }
            if (style != null)
            {
                if (style == currentPrayer)
                {
                    if (name != null)
                    {
                        namesOnMyStyle.add(name);
                    }
                }
            }
        }

        Collections.sort(groupNames, String.CASE_INSENSITIVE_ORDER);
        Rs2PrayerEnum chosen = HueyPrayerBalance.chooseAutobalance(
            melee,
            ranged,
            magic,
            currentPrayer,
            namesOnMyStyle,
            groupNames,
            myName);
        if (config.debug())
        {
            if (chosen != currentPrayer)
            {
                Microbot.log("Huey autobalance melee=" + melee + " range=" + ranged + " magic=" + magic + " -> " + chosen);
            }
        }
        return chosen;
    }

    private static boolean withinRadius(LocalPoint localTile, LocalPoint otherTile, int radius)
    {
        if (localTile == null)
        {
            return false;
        }
        if (otherTile == null)
        {
            return false;
        }
        int distance = HueyPrayerBalance.sceneTileDistance(
            localTile.getSceneX(),
            localTile.getSceneY(),
            localTile.getWorldView(),
            otherTile.getSceneX(),
            otherTile.getSceneY(),
            otherTile.getWorldView());
        return distance <= radius;
    }

    private static Rs2PrayerEnum overheadToPrayer(HeadIcon overhead)
    {
        if (overhead == HeadIcon.MELEE)
        {
            return Rs2PrayerEnum.PROTECT_MELEE;
        }
        if (overhead == HeadIcon.DEFLECT_MELEE)
        {
            return Rs2PrayerEnum.PROTECT_MELEE;
        }
        if (overhead == HeadIcon.RANGED)
        {
            return Rs2PrayerEnum.PROTECT_RANGE;
        }
        if (overhead == HeadIcon.DEFLECT_RANGE)
        {
            return Rs2PrayerEnum.PROTECT_RANGE;
        }
        if (overhead == HeadIcon.MAGIC)
        {
            return Rs2PrayerEnum.PROTECT_MAGIC;
        }
        if (overhead == HeadIcon.DEFLECT_MAGE)
        {
            return Rs2PrayerEnum.PROTECT_MAGIC;
        }
        return null;
    }
}