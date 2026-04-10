package net.runelite.client.plugins.microbot.qualityoflife.scripts;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
import net.runelite.api.kit.KitType;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.qualityoflife.QoLConfig;
import net.runelite.client.plugins.microbot.qualityoflife.enums.WeaponAnimation;
import net.runelite.client.plugins.microbot.qualityoflife.enums.WeaponID;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
public class AutoPrayer extends Script {

    private static final int RIGOUR_UNLOCKED_VARBIT = 5451;
    private static final int AUGURY_UNLOCKED_VARBIT = 5452;

    private long lastPkAttackTime = 0;
    private String lastPrayedStyle = null;
    private static final long PRAYER_DISABLE_DELAY_MS = 10_000;
    private Player followedPlayer = null;
    private long followEndTime = 0;
    private String pendingCorrectStyle = null;
    private long pendingCorrectAfter = 0;
    private String reactionForStyle = null;
    private long reactionReadyAt = 0;
    /** True after we have applied offensive prayers this session; used to clear on toggle-off. */
    private boolean offensivePrayersManaged = false;
    /**
     * Last weapon item id we used for offensive prayer selection while in combat.
     * Equipment is still read every cycle; this is for swap detection and reset on leaving combat.
     * We do not mass-deactivate prayers on change—turning on the right offensive for the new style replaces via normal game rules.
     */
    private int lastOffensiveWeaponId = -1;
    /**
     * LMS presets can show high Prayer level while still restricting Piety/Chivalry; after failed activation we use
     * Ultimate Strength + Incredible Reflexes only until combat ends.
     */
    private int lmsMeleeTopTierFailedAttempts = 0;
    private boolean lmsMeleeUseStatPrayersOnly = false;

    public boolean run(QoLConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if (!config.autoPrayAgainstPlayers() && !config.offensivePrayers()) return;

                Microbot.getClientThread().invoke(() -> {
                    if (config.autoPrayAgainstPlayers()) {
                        handleAntiPkPrayers(config);
                    }
                    if (config.offensivePrayers()) {
                        if (Rs2Player.isInCombat()) {
                            updateOffensivePrayers(config);
                        } else {
                            clearOffensivePrayersIfNeeded();
                        }
                    } else {
                        clearOffensivePrayersIfNeeded();
                    }
                });

            } catch (Exception ex) {
                log.error("Error in AutoPrayer execution: {}", ex.getMessage(), ex);
            }
        }, 0, 300, TimeUnit.MILLISECONDS);
        return true;
    }

    private void updateOffensivePrayers(QoLConfig config) {
        Player local = Microbot.getClient().getLocalPlayer();
        if (local == null || local.getPlayerComposition() == null) {
            return;
        }
        int weaponId = local.getPlayerComposition().getEquipmentId(KitType.WEAPON);
        WeaponID weapon = WeaponID.getByObjectId(weaponId);
        String category;
        if (weapon != null) {
            category = weapon.getAttackType().toLowerCase();
        } else {
            category = "melee";
        }

        int prayerLevel = getOffensivePrayerSkillLevel();
        switch (category) {
            case "ranged":
                activateBestRangedOffensivePrayer(prayerLevel);
                break;
            case "magic":
                activateBestMagicOffensivePrayer(prayerLevel);
                break;
            case "melee":
            default:
                activateBestMeleeOffensivePrayer(prayerLevel);
                break;
        }
        lastOffensiveWeaponId = weaponId;
        offensivePrayersManaged = true;
    }

    private void clearOffensivePrayersIfNeeded() {
        if (!offensivePrayersManaged) {
            return;
        }
        deactivateAllOffensivePrayers();
        offensivePrayersManaged = false;
        lastOffensiveWeaponId = -1;
        lmsMeleeTopTierFailedAttempts = 0;
        lmsMeleeUseStatPrayersOnly = false;
    }

    /**
     * Offensive tier thresholds use base Prayer level ({@code getRealSkillLevel}), not boosted.
     * In LMS, boosted level can exceed your loadout’s real Prayer (e.g. sips) and incorrectly select
     * Rigour/Augury for 1-def pures and other low-Prayer presets; the game still keys tier choice on base level.
     */
    private static int getOffensivePrayerSkillLevel() {
        if (Microbot.getClient() == null) {
            return 1;
        }
        return Microbot.getClient().getRealSkillLevel(Skill.PRAYER);
    }

    private static boolean isLastManStandingWorld() {
        if (Microbot.getClient() == null) {
            return false;
        }
        return Microbot.getClient().getWorldType().contains(WorldType.LAST_MAN_STANDING);
    }

    /**
     * LMS ranged: highest prayer allowed by level only — never fall through to lower tiers when a higher tier is
     * eligible but did not register on one tick (avoids cycling Sharp/Hawk/Eagle/Rigour).
     */
    private static Rs2PrayerEnum bestRangedOffensiveForLmsLevel(int prayerLevel) {
        if (prayerLevel >= 74) {
            return Rs2PrayerEnum.RIGOUR;
        }
        if (prayerLevel >= 44) {
            return Rs2PrayerEnum.EAGLE_EYE;
        }
        if (prayerLevel >= 26) {
            return Rs2PrayerEnum.HAWK_EYE;
        }
        if (prayerLevel >= 8) {
            return Rs2PrayerEnum.SHARP_EYE;
        }
        return null;
    }

    /** LMS magic: same top-down selection as {@link #bestRangedOffensiveForLmsLevel}. */
    private static Rs2PrayerEnum bestMagicOffensiveForLmsLevel(int prayerLevel) {
        if (prayerLevel >= 77) {
            return Rs2PrayerEnum.AUGURY;
        }
        if (prayerLevel >= 45) {
            return Rs2PrayerEnum.MYSTIC_MIGHT;
        }
        if (prayerLevel >= 27) {
            return Rs2PrayerEnum.MYSTIC_LORE;
        }
        if (prayerLevel >= 9) {
            return Rs2PrayerEnum.MYSTIC_WILL;
        }
        return null;
    }

    /**
     * Piety and Chivalry are mutually exclusive. Do not fall through from Piety to Chivalry when
     * {@code isPrayerActive(Piety)} is still false one tick after toggle — only one top-tier target per level band.
     */
    private static Rs2PrayerEnum bestMeleeOffensivePrayerForLevel(int prayerLevel) {
        if (prayerLevel >= 70) {
            return Rs2PrayerEnum.PIETY;
        }
        if (prayerLevel >= 60) {
            return Rs2PrayerEnum.CHIVALRY;
        }
        return null;
    }

    /**
     * Piety/Chivalry: single target from level (see {@link #bestMeleeOffensivePrayerForLevel}).
     * Below 60: Ultimate Strength + Incredible Reflexes as before.
     * LMS: {@link #activateBestMeleeOffensivePrayerLms} — level can read 99 while the loadout still blocks Piety/Chivalry.
     */
    private void activateBestMeleeOffensivePrayer(int prayerLevel) {
        if (isLastManStandingWorld()) {
            activateBestMeleeOffensivePrayerLms(prayerLevel);
            return;
        }
        Rs2PrayerEnum topTier = bestMeleeOffensivePrayerForLevel(prayerLevel);
        if (topTier != null) {
            if (!Rs2Prayer.isPrayerActive(topTier)) {
                Rs2Prayer.toggle(topTier, true);
            }
            return;
        }
        activateMeleeStatPrayerPair(prayerLevel);
    }

    private void activateBestMeleeOffensivePrayerLms(int prayerLevel) {
        if (!lmsMeleeUseStatPrayersOnly) {
            Rs2PrayerEnum topTier = bestMeleeOffensivePrayerForLevel(prayerLevel);
            if (topTier != null) {
                if (Rs2Prayer.isPrayerActive(topTier)) {
                    lmsMeleeTopTierFailedAttempts = 0;
                    return;
                }
                Rs2Prayer.toggle(topTier, true);
                if (Rs2Prayer.isPrayerActive(topTier)) {
                    lmsMeleeTopTierFailedAttempts = 0;
                    return;
                }
                lmsMeleeTopTierFailedAttempts++;
                if (lmsMeleeTopTierFailedAttempts < 2) {
                    return;
                }
                lmsMeleeUseStatPrayersOnly = true;
                lmsMeleeTopTierFailedAttempts = 0;
            }
        }
        activateMeleeStatPrayerPair(prayerLevel);
    }

    private void activateMeleeStatPrayerPair(int prayerLevel) {
        if (prayerLevel >= 31 && !Rs2Prayer.isPrayerActive(Rs2PrayerEnum.ULTIMATE_STRENGTH)) {
            Rs2Prayer.toggle(Rs2PrayerEnum.ULTIMATE_STRENGTH, true);
        }
        if (prayerLevel >= 34 && !Rs2Prayer.isPrayerActive(Rs2PrayerEnum.INCREDIBLE_REFLEXES)) {
            Rs2Prayer.toggle(Rs2PrayerEnum.INCREDIBLE_REFLEXES, true);
        }
    }

    private void activateBestRangedOffensivePrayer(int prayerLevel) {
        if (isLastManStandingWorld()) {
            activateBestRangedOffensivePrayerLms(prayerLevel);
            return;
        }
        if (prayerLevel >= 74) {
            boolean rigourUnlocked = Microbot.getVarbitValue(RIGOUR_UNLOCKED_VARBIT) == 1;
            if (rigourUnlocked) {
                if (!Rs2Prayer.isPrayerActive(Rs2PrayerEnum.RIGOUR)) {
                    Rs2Prayer.toggle(Rs2PrayerEnum.RIGOUR, true);
                }
                return;
            }
        }
        if (prayerLevel >= 44) {
            if (!Rs2Prayer.isPrayerActive(Rs2PrayerEnum.EAGLE_EYE)) {
                Rs2Prayer.toggle(Rs2PrayerEnum.EAGLE_EYE, true);
            }
            return;
        }
        if (prayerLevel >= 26) {
            if (!Rs2Prayer.isPrayerActive(Rs2PrayerEnum.HAWK_EYE)) {
                Rs2Prayer.toggle(Rs2PrayerEnum.HAWK_EYE, true);
            }
            return;
        }
        if (prayerLevel >= 8 && !Rs2Prayer.isPrayerActive(Rs2PrayerEnum.SHARP_EYE)) {
            Rs2Prayer.toggle(Rs2PrayerEnum.SHARP_EYE, true);
        }
    }

    /** LMS: one target prayer from the top of the range for your level; only that prayer is toggled. */
    private void activateBestRangedOffensivePrayerLms(int prayerLevel) {
        Rs2PrayerEnum target = bestRangedOffensiveForLmsLevel(prayerLevel);
        if (target == null) {
            return;
        }
        if (!Rs2Prayer.isPrayerActive(target)) {
            Rs2Prayer.toggle(target, true);
        }
    }

    private void activateBestMagicOffensivePrayer(int prayerLevel) {
        if (isLastManStandingWorld()) {
            activateBestMagicOffensivePrayerLms(prayerLevel);
            return;
        }
        if (prayerLevel >= 77) {
            boolean auguryUnlocked = Microbot.getVarbitValue(AUGURY_UNLOCKED_VARBIT) == 1;
            if (auguryUnlocked) {
                if (!Rs2Prayer.isPrayerActive(Rs2PrayerEnum.AUGURY)) {
                    Rs2Prayer.toggle(Rs2PrayerEnum.AUGURY, true);
                }
                return;
            }
        }
        if (prayerLevel >= 45) {
            if (!Rs2Prayer.isPrayerActive(Rs2PrayerEnum.MYSTIC_MIGHT)) {
                Rs2Prayer.toggle(Rs2PrayerEnum.MYSTIC_MIGHT, true);
            }
            return;
        }
        if (prayerLevel >= 27) {
            if (!Rs2Prayer.isPrayerActive(Rs2PrayerEnum.MYSTIC_LORE)) {
                Rs2Prayer.toggle(Rs2PrayerEnum.MYSTIC_LORE, true);
            }
            return;
        }
        if (prayerLevel >= 9 && !Rs2Prayer.isPrayerActive(Rs2PrayerEnum.MYSTIC_WILL)) {
            Rs2Prayer.toggle(Rs2PrayerEnum.MYSTIC_WILL, true);
        }
    }

    /** LMS: same as ranged — top-down tier for level, single toggle target. */
    private void activateBestMagicOffensivePrayerLms(int prayerLevel) {
        Rs2PrayerEnum target = bestMagicOffensiveForLmsLevel(prayerLevel);
        if (target == null) {
            return;
        }
        if (!Rs2Prayer.isPrayerActive(target)) {
            Rs2Prayer.toggle(target, true);
        }
    }

    private static void deactivateAllOffensivePrayers() {
        if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PIETY)) {
            Rs2Prayer.toggle(Rs2PrayerEnum.PIETY, false);
        }
        if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.CHIVALRY)) {
            Rs2Prayer.toggle(Rs2PrayerEnum.CHIVALRY, false);
        }
        if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.ULTIMATE_STRENGTH)) {
            Rs2Prayer.toggle(Rs2PrayerEnum.ULTIMATE_STRENGTH, false);
        }
        if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.INCREDIBLE_REFLEXES)) {
            Rs2Prayer.toggle(Rs2PrayerEnum.INCREDIBLE_REFLEXES, false);
        }
        if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.RIGOUR)) {
            Rs2Prayer.toggle(Rs2PrayerEnum.RIGOUR, false);
        }
        if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.EAGLE_EYE)) {
            Rs2Prayer.toggle(Rs2PrayerEnum.EAGLE_EYE, false);
        }
        if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.HAWK_EYE)) {
            Rs2Prayer.toggle(Rs2PrayerEnum.HAWK_EYE, false);
        }
        if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.SHARP_EYE)) {
            Rs2Prayer.toggle(Rs2PrayerEnum.SHARP_EYE, false);
        }
        if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.AUGURY)) {
            Rs2Prayer.toggle(Rs2PrayerEnum.AUGURY, false);
        }
        if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.MYSTIC_MIGHT)) {
            Rs2Prayer.toggle(Rs2PrayerEnum.MYSTIC_MIGHT, false);
        }
        if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.MYSTIC_LORE)) {
            Rs2Prayer.toggle(Rs2PrayerEnum.MYSTIC_LORE, false);
        }
        if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.MYSTIC_WILL)) {
            Rs2Prayer.toggle(Rs2PrayerEnum.MYSTIC_WILL, false);
        }
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
        clearOffensivePrayersIfNeeded();
        super.shutdown();
        log.info("AutoPrayer shutdown complete.");
    }
}
