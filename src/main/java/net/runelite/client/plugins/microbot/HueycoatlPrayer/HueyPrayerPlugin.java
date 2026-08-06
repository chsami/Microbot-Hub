package net.runelite.client.plugins.microbot.HueycoatlPrayer;

import com.google.inject.Provides;
import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

import net.runelite.api.Client;
import net.runelite.api.HeadIcon;
import net.runelite.api.Player;
import net.runelite.api.Projectile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;

import net.runelite.api.events.ProjectileMoved;

import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

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
    static final String VERSION = "1.0.7";
    @Inject
    private Client client;

    @Inject
    private HueyPrayerConfig config;

    private static final int MAGIC_PROJECTILE_ID = 2975;
    private static final int RANGE_PROJECTILE_ID = 2972;
    private static final int MELEE_PROJECTILE_ID = 2969;

    private Rs2PrayerEnum currentPrayer = null;
    private int lastSwitchTick = -1;

    /** Huey projectiles still in flight toward the player (identity-based). */
    private final Set<Projectile> incomingHueyProjectiles =
            Collections.newSetFromMap(new IdentityHashMap<>());

    @Provides
    HueyPrayerConfig provideConfig(net.runelite.client.config.ConfigManager configManager)
    {
        return configManager.getConfig(HueyPrayerConfig.class);
    }

    @Override
    protected void startUp()
    {
        Microbot.log("Huey Prayer started");
    }

    @Override
    protected void shutDown()
    {
        Rs2Prayer.disableAllPrayers();
        currentPrayer = null;
        incomingHueyProjectiles.clear();
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
            if (!incomingHueyProjectiles.isEmpty())
            {
                incomingHueyProjectiles.clear();
            }
        }

        if (config.disableAfterImpact())
        {
            if (incomingHueyProjectiles.contains(projectile))
            {
                if (projectile.getRemainingCycles() <= 0)
                {
                    incomingHueyProjectiles.remove(projectile);
                    if (incomingHueyProjectiles.isEmpty())
                    {
                        onHueyProjectilePhaseEnded();
                    }
                }
                return;
            }
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
            incomingHueyProjectiles.add(projectile);
        }

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
        if (config.trioMode())
        {
            Rs2PrayerEnum pillar = resolveTrioProtectionPrayer();
            if (pillar != null)
            {
                switchPrayer(pillar);
            }
            return;
        }
        Rs2Prayer.disableAllPrayers();
        currentPrayer = null;
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
        WorldPoint localPoint = local.getWorldLocation();
        WorldView worldView = client.getTopLevelWorldView();
        if (worldView == null)
        {
            return null;
        }

        int melee = 0;
        int ranged = 0;
        int magic = 0;
        int radius = config.trioRadius();
        if (radius < 1)
        {
            radius = 1;
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
            WorldPoint wp = p.getWorldLocation();
            if (wp == null)
            {
                continue;
            }
            if (wp.distanceTo(localPoint) > radius)
            {
                continue;
            }
            HeadIcon overhead = p.getOverheadIcon();
            if (overhead == HeadIcon.MELEE)
            {
                melee++;
            }
            else if (overhead == HeadIcon.RANGED)
            {
                ranged++;
            }
            else if (overhead == HeadIcon.MAGIC)
            {
                magic++;
            }
        }

        if (melee == 0)
        {
            if (ranged == 0)
            {
                if (magic == 0)
                {
                    return protectionMatchingLocalOverhead(local);
                }
            }
        }

        int minCount = melee;
        if (ranged < minCount)
        {
            minCount = ranged;
        }
        if (magic < minCount)
        {
            minCount = magic;
        }

        if (melee == minCount)
        {
            return Rs2PrayerEnum.PROTECT_MELEE;
        }
        if (ranged == minCount)
        {
            return Rs2PrayerEnum.PROTECT_RANGE;
        }
        return Rs2PrayerEnum.PROTECT_MAGIC;
    }

    private static Rs2PrayerEnum protectionMatchingLocalOverhead(Player local)
    {
        HeadIcon overhead = local.getOverheadIcon();
        if (overhead == HeadIcon.MELEE)
        {
            return Rs2PrayerEnum.PROTECT_MELEE;
        }
        if (overhead == HeadIcon.RANGED)
        {
            return Rs2PrayerEnum.PROTECT_RANGE;
        }
        if (overhead == HeadIcon.MAGIC)
        {
            return Rs2PrayerEnum.PROTECT_MAGIC;
        }
        return Rs2PrayerEnum.PROTECT_MELEE;
    }
}