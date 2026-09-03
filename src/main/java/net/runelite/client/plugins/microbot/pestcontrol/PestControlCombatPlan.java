package net.runelite.client.plugins.microbot.pestcontrol;

import net.runelite.client.plugins.pestcontrol.Portal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static net.runelite.client.plugins.pestcontrol.Portal.BLUE;
import static net.runelite.client.plugins.pestcontrol.Portal.PURPLE;
import static net.runelite.client.plugins.pestcontrol.Portal.RED;
import static net.runelite.client.plugins.pestcontrol.Portal.YELLOW;

final class PestControlCombatPlan {
    private final PestControlConfig config;
    private final List<PestControlCombatStyle> enabledStyles;
    private final List<String> validationMessages;

    PestControlCombatPlan(PestControlConfig config) {
        this.config = config;
        List<PestControlCombatStyle> styles = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        PestControlCombatStyle primary = config.primaryCombatStyle() == null
                ? PestControlCombatStyle.RANGED
                : config.primaryCombatStyle();
        styles.add(primary);
        addOptionalStyle(styles, messages, config.secondaryCombatStyle(), "Style 2");
        addOptionalStyle(styles, messages, config.tertiaryCombatStyle(), "Style 3");
        this.enabledStyles = Collections.unmodifiableList(styles);

        for (int index = 0; index < styles.size(); index++) {
            PestControlCombatStyle style = styles.get(index);
            if (!loadoutForStyle(style).isConfigured()) {
                messages.add("Style " + (index + 1) + " " + style + " weapon is not configured");
            }
        }
        this.validationMessages = Collections.unmodifiableList(messages);
    }

    List<PestControlCombatStyle> enabledStyles() {
        return enabledStyles;
    }

    List<String> validationMessages() {
        return validationMessages;
    }

    boolean supports(PestControlCombatStyle style) {
        return enabledStyles.contains(style);
    }

    PestControlLoadout primaryLoadout() {
        return loadoutForStyle(enabledStyles.get(0));
    }

    PestControlLoadout magicLoadout() {
        return loadoutForStyle(PestControlCombatStyle.MAGIC);
    }

    PestControlLoadout loadoutForPortal(Portal portal) {
        PestControlCombatStyle desiredStyle = styleForPortal(portal);
        if (!supports(desiredStyle)) {
            return primaryLoadout();
        }

        PestControlLoadout desired;
        if (desiredStyle == PestControlCombatStyle.MELEE) {
            desired = portal == RED ? redMeleeLoadout() : yellowMeleeLoadout();
        } else {
            desired = loadoutForStyle(desiredStyle);
        }
        return desired.isConfigured() ? desired : primaryLoadout();
    }

    Portal openingPortal(int randomValue) {
        PestControlOpeningMode mode = config.openingMode() == null
                ? PestControlOpeningMode.WEIGHTED_RANDOM
                : config.openingMode();
        switch (mode) {
            case MAIN_STYLE:
                return openingPortalForStyle(enabledStyles.get(0));
            case EVEN_RANDOM:
                return openingPortalByIndex(randomValue, 1, 1, 1);
            case WEIGHTED_RANDOM:
            default:
                return openingPortalByIndex(
                        randomValue,
                        clampedWeight(config.rangedOpeningWeight()),
                        clampedWeight(config.magicOpeningWeight()),
                        clampedWeight(config.meleeOpeningWeight()));
        }
    }

    private PestControlLoadout loadoutForStyle(PestControlCombatStyle style) {
        switch (style) {
            case MAGIC:
                return PestControlLoadout.magic(config.magicWeapon(), config.magicOffhand());
            case MELEE:
                return yellowMeleeLoadout();
            case RANGED:
            default:
                return PestControlLoadout.ranged(config.rangedWeapon(), config.rangedOffhand());
        }
    }

    private PestControlLoadout yellowMeleeLoadout() {
        PestControlLoadout yellow = configuredYellowMeleeLoadout();
        return yellow.isConfigured() ? yellow : configuredRedMeleeLoadout();
    }

    private PestControlLoadout configuredYellowMeleeLoadout() {
        PestControlYellowAttackStyle style = config.yellowMeleeStyle() == null
                ? PestControlYellowAttackStyle.SLASH
                : config.yellowMeleeStyle();
        return PestControlLoadout.melee(
                style.meleeStyle(),
                config.slashStabWeapon(),
                config.slashOffhand());
    }

    private PestControlLoadout redMeleeLoadout() {
        PestControlLoadout red = configuredRedMeleeLoadout();
        return red.isConfigured() ? red : configuredYellowMeleeLoadout();
    }

    private PestControlLoadout configuredRedMeleeLoadout() {
        return PestControlLoadout.melee(
                PestControlMeleeStyle.CRUSH,
                config.crushWeapon(),
                config.crushOffhand());
    }

    private static void addOptionalStyle(
            List<PestControlCombatStyle> styles,
            List<String> messages,
            PestControlOptionalCombatStyle optionalStyle,
            String label) {
        if (optionalStyle == null || optionalStyle.combatStyle() == null) {
            return;
        }
        PestControlCombatStyle style = optionalStyle.combatStyle();
        if (styles.contains(style)) {
            messages.add(label + " duplicates " + style + " and will be ignored");
            return;
        }
        styles.add(style);
    }

    private static PestControlCombatStyle styleForPortal(Portal portal) {
        if (portal == PURPLE) {
            return PestControlCombatStyle.RANGED;
        }
        if (portal == BLUE) {
            return PestControlCombatStyle.MAGIC;
        }
        return PestControlCombatStyle.MELEE;
    }

    private static Portal openingPortalForStyle(PestControlCombatStyle style) {
        switch (style) {
            case MAGIC:
                return BLUE;
            case MELEE:
                return YELLOW;
            case RANGED:
            default:
                return PURPLE;
        }
    }

    private static Portal openingPortalByIndex(
            int randomValue,
            int rangedWeight,
            int magicWeight,
            int meleeWeight) {
        int total = rangedWeight + magicWeight + meleeWeight;
        if (total <= 0) {
            rangedWeight = 1;
            magicWeight = 1;
            meleeWeight = 1;
            total = 3;
        }
        int roll = Math.floorMod(randomValue, total);
        if (roll < rangedWeight) {
            return PURPLE;
        }
        if (roll < rangedWeight + magicWeight) {
            return BLUE;
        }
        return YELLOW;
    }

    private static int clampedWeight(int weight) {
        return Math.max(0, Math.min(100, weight));
    }
}
