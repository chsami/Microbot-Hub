package net.runelite.client.plugins.microbot.qualityoflife.scripts;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Player;
import net.runelite.api.kit.KitType;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.qualityoflife.QoLConfig;
import net.runelite.client.plugins.microbot.qualityoflife.enums.WeaponAnimation;
import net.runelite.client.plugins.microbot.qualityoflife.enums.WeaponID;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
public class AutoPrayer extends Script {

    private long lastPkAttackTime = 0;
    private String lastPrayedStyle = null;
    private static final long PRAYER_DISABLE_DELAY_MS = 10_000;
    private Player followedPlayer = null;
    private long followEndTime = 0;
    private String pendingCorrectStyle = null;
    private long pendingCorrectAfter = 0;
    private String reactionForStyle = null;
    private long reactionReadyAt = 0;

    public boolean run(QoLConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if (!config.autoPrayAgainstPlayers()) return;

                Microbot.getClientThread().invoke(() -> handleAntiPkPrayers(config));

            } catch (Exception ex) {
                log.error("Error in AutoPrayer execution: {}", ex.getMessage(), ex);
            }
        }, 0, 300, TimeUnit.MILLISECONDS);
        return true;
    }

    private void handleAntiPkPrayers(QoLConfig config) {
        Player local = Microbot.getClient().getLocalPlayer();
        if (!(local.getInteracting() instanceof Player)) {
            // If we haven't been attacked for 10s, turn off prayers and stop following
            if (lastPrayedStyle != null && System.currentTimeMillis() - lastPkAttackTime > PRAYER_DISABLE_DELAY_MS) {
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
        int animationId = attacker.getAnimation();
        int weaponId = attacker.getPlayerComposition().getEquipmentId(KitType.WEAPON);
        String detectedStyle = null;

        boolean lmsMode = config.lmsAnimationPraying();
        boolean aggressiveMode = config.aggressiveAntiPkMode();

        // Follow behavior: in aggressive mode or LMS mode, remember the attacker for a short window
        if (aggressiveMode || lmsMode) {
            followedPlayer = attacker;
            followEndTime = System.currentTimeMillis() + PRAYER_DISABLE_DELAY_MS;
        }

        // Get weapon and animation info for detection
        WeaponAnimation anim = WeaponAnimation.getByAnimationId(animationId);
        WeaponID weapon = WeaponID.getByObjectId(weaponId);
        String weaponName = weapon != null ? weapon.getItemName() : "Unknown (" + weaponId + ")";
        String animationName = anim != null ? anim.getAnimationName() : "Unknown (" + animationId + ")";

        // If following a player (aggressive or LMS) and timer is active
        if ((aggressiveMode || lmsMode) && followedPlayer != null && System.currentTimeMillis() < followEndTime) {
            int followedWeaponId = followedPlayer.getPlayerComposition().getEquipmentId(KitType.WEAPON);
            WeaponID followedWeapon = WeaponID.getByObjectId(followedWeaponId);
            WeaponAnimation followedAnim = WeaponAnimation.getByAnimationId(animationId);

            // Weapon first, then animation (shared IDs can misclassify e.g. whip vs bow)
            if (followedWeapon != null) {
                detectedStyle = followedWeapon.getAttackType().toLowerCase();
            } else if (followedAnim != null) {
                detectedStyle = followedAnim.getAttackType().toLowerCase();
            }
            
            String followedWeaponName = followedWeapon != null ? followedWeapon.getItemName() : "Unknown (" + followedWeaponId + ")";
            String followedAnimationName = followedAnim != null ? followedAnim.getAnimationName() : "Unknown (" + animationId + ")";
            
            log.info("Aggressive Anti-PK: Following player {} | WeaponID={} ({}) | AnimationID={} ({}) | WeaponType={} | FinalPrayer={}",
                followedPlayer.getName(), followedWeaponId, followedWeaponName, animationId, followedAnimationName, detectedStyle, detectedStyle);
                
            if (detectedStyle != null) {
                prayStyle(detectedStyle, config);
            }
        } else {
            // Not currently following (or follow window expired): weapon first, then animation
            if (weapon != null) {
                detectedStyle = weapon.getAttackType().toLowerCase();
            } else if (anim != null) {
                detectedStyle = anim.getAttackType().toLowerCase();
            }
            
            log.info("[Anti-PK Debug] AnimationID={} ({}) | WeaponID={} ({}) | WeaponType={} | FinalPrayer={}",
                animationId, animationName, weaponId, weaponName, detectedStyle, detectedStyle);
                
            if (detectedStyle != null) {
                prayStyle(detectedStyle, config);
            }
        }
    }

    private void prayStyle(String style, QoLConfig config) {
        if (style == null) {
            return;
        }

        long now = System.currentTimeMillis();

        // LMS antiban logic: reaction delay + wrong-prayer chance
        if (config.lmsAnimationPraying()) {
            // If we previously chose a wrong prayer, correct it after the delay
            if (pendingCorrectStyle != null
                && style.equals(pendingCorrectStyle)
                && now >= pendingCorrectAfter) {

                applyPrayer(style);
                lastPrayedStyle = style;
                pendingCorrectStyle = null;
                pendingCorrectAfter = 0;
                reactionForStyle = null;
                reactionReadyAt = 0;
                lastPkAttackTime = now;
                return;
            }

            // New style seen: set up a reaction delay
            if (!style.equals(lastPrayedStyle)
                && (reactionForStyle == null || !style.equals(reactionForStyle))) {

                int maxDelay = config.lmsMaxReactionDelayMs();
                if (maxDelay < 30) {
                    maxDelay = 30;
                }
                int delayRange = maxDelay - 30;
                int delay = delayRange > 0
                    ? ThreadLocalRandom.current().nextInt(30, maxDelay + 1)
                    : 30;

                reactionForStyle = style;
                reactionReadyAt = now + delay;
                return;
            }

            // Still within reaction window: do nothing yet
            if (reactionForStyle != null
                && style.equals(reactionForStyle)
                && now < reactionReadyAt) {
                return;
            }

            // Reaction window elapsed; decide whether to pray wrong first
            if (!style.equals(lastPrayedStyle)) {
                int wrongChance = config.lmsWrongPrayChance();
                if (wrongChance < 0) {
                    wrongChance = 0;
                }
                if (wrongChance > 100) {
                    wrongChance = 100;
                }

                int roll = ThreadLocalRandom.current().nextInt(0, 100);
                boolean makeMistake = roll < wrongChance;

                if (makeMistake) {
                    String wrongStyle = pickWrongStyle(style);
                    if (wrongStyle != null) {
                        applyPrayer(wrongStyle);
                        lastPrayedStyle = wrongStyle;

                        // Correct to the real style after 1–2 game ticks (~600–1200ms)
                        int correctDelayMs = ThreadLocalRandom.current().nextInt(600, 1201);
                        pendingCorrectStyle = style;
                        pendingCorrectAfter = now + correctDelayMs;
                        lastPkAttackTime = now;
                        reactionForStyle = null;
                        reactionReadyAt = 0;
                        return;
                    }
                }
            }
        }

        // Default / non-LMS behavior: simple immediate switch
        if (!style.equals(lastPrayedStyle)) {
            applyPrayer(style);
            lastPrayedStyle = style;
        }

        lastPkAttackTime = now;
    }

    private static void applyPrayer(String style) {
        switch (style) {
            case "melee":
                Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, true);
                break;
            case "ranged":
                Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_RANGE, true);
                break;
            case "magic":
                Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC, true);
                break;
            default:
                break;
        }
    }

    private static String pickWrongStyle(String correctStyle) {
        String[] options = {"melee", "ranged", "magic"};
        String[] candidates = new String[2];
        int idx = 0;
        for (String opt : options) {
            if (!opt.equals(correctStyle)) {
                candidates[idx] = opt;
                idx++;
            }
        }
        if (idx == 0) {
            return null;
        }
        if (idx == 1) {
            return candidates[0];
        }
        int choice = ThreadLocalRandom.current().nextInt(0, idx);
        return candidates[choice];
    }

    public boolean isFollowingPlayer(Player player) {
        return followedPlayer != null
            && player != null
            && player.getName() != null
            && player.getName().equals(followedPlayer.getName());
    }

    public void handleAggressivePrayerOnGearChange(Player player, QoLConfig config) {
        int weaponId = player.getPlayerComposition().getEquipmentId(KitType.WEAPON);
        WeaponID weapon = WeaponID.getByObjectId(weaponId);
        if (weapon != null) {
            String detectedStyle = weapon.getAttackType().toLowerCase();
            prayStyle(detectedStyle, config);
            log.info("Aggressive Anti-PK (Immediate): Detected gear swap for {} | WeaponID={} | Style={}", player.getName(), weaponId, detectedStyle);
        }
    }

    public Player getFollowedPlayer() {
        return followedPlayer;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        log.info("AutoPrayer shutdown complete.");
    }
}
