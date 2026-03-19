package net.runelite.client.plugins.microbot.qualityoflife.scripts;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Player;
import net.runelite.api.WorldType;
import net.runelite.api.kit.KitType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.qualityoflife.QoLConfig;
import net.runelite.client.plugins.microbot.qualityoflife.enums.WeaponAnimation;
import net.runelite.client.plugins.microbot.qualityoflife.enums.WeaponID;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

@Slf4j
public class AutoPrayer extends Script
{
    @Inject
    private ClientThread clientThread;

    private long lastPkAttackTime = 0;
    private String lastPrayedStyle = null;

    private static final long PRAYER_DISABLE_DELAY_MS = 10_000;

    private Player followedPlayer = null;
    private long followEndTime = 0;

    public boolean run(QoLConfig config)
    {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
        {
            clientThread.invokeLater(() ->
            {
                try
                {
                    if (!Microbot.isLoggedIn()) return;
                    if (!super.run()) return;
                    if (!config.autoPrayAgainstPlayers()) return;

                    handleAntiPkPrayers(config);
                }
                catch (Exception ex)
                {
                    log.error("AutoPrayer error", ex);
                }
            });
        }, 0, 300, TimeUnit.MILLISECONDS);

        return true;
    }

    private void handleAntiPkPrayers(QoLConfig config)
    {
        Player local = Microbot.getClient().getLocalPlayer();
        if (local == null) return;

        if (!(local.getInteracting() instanceof Player))
        {
            if (lastPrayedStyle != null &&
                System.currentTimeMillis() - lastPkAttackTime > PRAYER_DISABLE_DELAY_MS)
            {
                Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, false);
                Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC, false);
                Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_RANGE, false);
                Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_ITEM, false);

                lastPrayedStyle = null;
                followedPlayer = null;
                followEndTime = 0;
            }
            return;
        }

        Player attacker = (Player) local.getInteracting();
        if (attacker == null || attacker.getPlayerComposition() == null) return;

        int animationId = attacker.getAnimation();
        int weaponId = attacker.getPlayerComposition().getEquipmentId(KitType.WEAPON);

        String detectedStyle = null;

        if (config.aggressiveAntiPkMode())
        {
            followedPlayer = attacker;
            followEndTime = System.currentTimeMillis() + PRAYER_DISABLE_DELAY_MS;
        }

        WeaponAnimation anim = WeaponAnimation.getByAnimationId(animationId);
        WeaponID weapon = WeaponID.getByObjectId(weaponId);

        if (config.aggressiveAntiPkMode() && followedPlayer != null &&
            System.currentTimeMillis() < followEndTime)
        {
            int followedWeaponId = followedPlayer.getPlayerComposition().getEquipmentId(KitType.WEAPON);
            int followedAnimationId = followedPlayer.getAnimation();

            WeaponID followedWeapon = WeaponID.getByObjectId(followedWeaponId);
            WeaponAnimation followedAnim = WeaponAnimation.getByAnimationId(followedAnimationId);

            if (followedWeapon != null)
            {
                detectedStyle = followedWeapon.getAttackType().toLowerCase();
            }

            if (followedAnim != null)
            {
                detectedStyle = followedAnim.getAttackType().toLowerCase();
            }
        }
        else
        {
            if (weapon != null)
            {
                detectedStyle = weapon.getAttackType().toLowerCase();
            }

            if (anim != null)
            {
                detectedStyle = anim.getAttackType().toLowerCase();
            }
        }

        if (detectedStyle != null)
        {
            prayStyle(detectedStyle, config);
        }
    }

    private void prayStyle(String style, QoLConfig config)
    {
        if (style == null) return;

        boolean shouldChange = !style.equals(lastPrayedStyle);

        if (shouldChange)
        {
            switch (style)
            {
                case "melee":
                    Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, true);
                    break;
                case "ranged":
                    Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_RANGE, true);
                    break;
                case "magic":
                    Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC, true);
                    break;
            }

            lastPrayedStyle = style;
        }

        applyProtectItemIfAllowed(config);

        lastPkAttackTime = System.currentTimeMillis();
    }

    /**
     * Protect Item is unavailable in Last Man Standing; only toggle it elsewhere.
     */
    private static void applyProtectItemIfAllowed(QoLConfig cfg)
    {
        if (isLastManStandingWorld())
        {
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_ITEM, false);
            return;
        }
        Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_ITEM, cfg.enableProtectItemPrayer());
    }

    private static boolean isLastManStandingWorld()
    {
        if (Microbot.getClient() == null)
        {
            return false;
        }
        return Microbot.getClient().getWorldType().contains(WorldType.LAST_MAN_STANDING);
    }

    public boolean isFollowingPlayer(Player player)
    {
        return followedPlayer != null &&
               player != null &&
               player.getName() != null &&
               player.getName().equals(followedPlayer.getName());
    }

    public void handleAggressivePrayerOnGearChange(Player player, QoLConfig config)
    {
        if (player == null || player.getPlayerComposition() == null) return;

        int weaponId = player.getPlayerComposition().getEquipmentId(KitType.WEAPON);
        WeaponID weapon = WeaponID.getByObjectId(weaponId);

        if (weapon != null)
        {
            String detectedStyle = weapon.getAttackType().toLowerCase();
            prayStyle(detectedStyle, config);
        }
    }

    @Override
    public void shutdown()
    {
        super.shutdown();
        log.info("AutoPrayer shutdown complete.");
    }
}