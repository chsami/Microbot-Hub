package net.runelite.client.plugins.microbot.pestcontrol;

import java.util.Collection;
import java.util.Locale;
import java.util.function.Predicate;

final class PestControlLoadout {
    private static final String VOID_MELEE_HELM = "Void melee helm";
    private static final String VOID_RANGER_HELM = "Void ranger helm";
    private static final String VOID_MAGE_HELM = "Void mage helm";

    final PestControlCombatStyle combatStyle;
    final String weapon;
    final String offhand;
    final String helmet;
    final String attackOption;
    final PestControlMeleeStyle meleeStyle;
    final String label;

    private PestControlLoadout(
            PestControlCombatStyle combatStyle,
            String weapon,
            String offhand,
            String attackOption,
            PestControlMeleeStyle meleeStyle,
            String label) {
        this.combatStyle = combatStyle;
        this.weapon = normalizeItemName(weapon);
        this.offhand = normalizeItemName(offhand);
        this.helmet = helmetFor(combatStyle);
        this.attackOption = attackOption;
        this.meleeStyle = meleeStyle;
        this.label = label;
    }

    static PestControlLoadout ranged(String weapon, String offhand) {
        return new PestControlLoadout(
                PestControlCombatStyle.RANGED,
                weapon,
                offhand,
                "Rapid",
                null,
                "Ranged");
    }

    static PestControlLoadout magic(String weapon, String offhand) {
        return new PestControlLoadout(
                PestControlCombatStyle.MAGIC,
                weapon,
                offhand,
                null,
                null,
                "Magic");
    }

    static PestControlLoadout melee(
            PestControlMeleeStyle meleeStyle,
            String weapon,
            String offhand) {
        PestControlMeleeStyle resolvedStyle = meleeStyle == null
                ? PestControlMeleeStyle.SLASH
                : meleeStyle;
        return new PestControlLoadout(
                PestControlCombatStyle.MELEE,
                weapon,
                offhand,
                resolvedStyle.attackOption(),
                resolvedStyle,
                "Melee " + resolvedStyle.name().toLowerCase(Locale.ROOT));
    }

    boolean isConfigured() {
        return !weapon.isEmpty();
    }

    boolean requiresEmptyOffhand() {
        return offhand.isEmpty();
    }

    String key() {
        return combatStyle + ":" + weapon.toLowerCase(Locale.ROOT)
                + ":" + offhand.toLowerCase(Locale.ROOT)
                + ":" + helmet.toLowerCase(Locale.ROOT)
                + ":" + String.valueOf(attackOption).toLowerCase(Locale.ROOT);
    }

    private static String normalizeItemName(String itemName) {
        if (itemName == null
                || itemName.trim().isEmpty()
                || itemName.trim().equalsIgnoreCase("None")) {
            return "";
        }
        return itemName.trim();
    }

    static String helmetFor(PestControlCombatStyle style) {
        switch (style) {
            case MELEE:
                return VOID_MELEE_HELM;
            case MAGIC:
                return VOID_MAGE_HELM;
            case RANGED:
            default:
                return VOID_RANGER_HELM;
        }
    }

    static boolean hasCompleteVoidHelmetSet(
            Collection<PestControlCombatStyle> enabledStyles,
            Predicate<String> isAvailable) {
        return enabledStyles != null
                && !enabledStyles.isEmpty()
                && enabledStyles.stream()
                .map(PestControlLoadout::helmetFor)
                .allMatch(isAvailable);
    }
}
