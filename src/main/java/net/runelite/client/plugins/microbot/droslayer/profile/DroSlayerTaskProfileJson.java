package net.runelite.client.plugins.microbot.droslayer.profile;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import net.runelite.client.plugins.microbot.droslayer.DroSlayerCombatStyle;
import net.runelite.client.plugins.microbot.droslayer.DroSlayerPrayer;

/**
 * JSON model for a slayer task profile.
 * Defines task-specific settings like gear setup, prayer, cannon, and potions.
 */
@Data
public class DroSlayerTaskProfileJson {

    /**
     * Inventory setup name (e.g., "melee", "ranged", "magic", "burst").
     * When cannon is enabled, will look for "{setup}-cannon" variant.
     */
    @SerializedName("setup")
    private String setup;

    /**
     * Combat style to use: melee, ranged, magic, burst, barrage.
     * Burst/barrage styles use AoE spells and goading potions.
     */
    @SerializedName("style")
    private String style;

    /**
     * Prayer to use. Supports shortcuts: pmelee, pm, pmage, pmagic, prange, pr, qp
     */
    @SerializedName("prayer")
    private String prayer;

    @SerializedName("offensivePrayer")
    private String offensivePrayer;

    /** Optional bracelet selections are independent for normal and Wilderness assignments. */
    @SerializedName("slaughterBracelet")
    private boolean slaughterBracelet = false;
    @SerializedName("expeditiousBracelet")
    private boolean expeditiousBracelet = false;
    @SerializedName("wildernessSlaughterBracelet")
    private boolean wildernessSlaughterBracelet = false;
    @SerializedName("wildernessExpeditiousBracelet")
    private boolean wildernessExpeditiousBracelet = false;

    public void setSlaughterBracelet(boolean selected) {
        slaughterBracelet = selected;
        if (selected) expeditiousBracelet = false;
    }
    public void setExpeditiousBracelet(boolean selected) {
        expeditiousBracelet = selected;
        if (selected) slaughterBracelet = false;
    }
    public void setWildernessSlaughterBracelet(boolean selected) {
        wildernessSlaughterBracelet = selected;
        if (selected) wildernessExpeditiousBracelet = false;
    }
    public void setWildernessExpeditiousBracelet(boolean selected) {
        wildernessExpeditiousBracelet = selected;
        if (selected) wildernessSlaughterBracelet = false;
    }


    public net.runelite.client.plugins.microbot.droslayer.DroSlayerOffensivePrayer getParsedOffensivePrayer() {
        return net.runelite.client.plugins.microbot.droslayer.DroSlayerOffensivePrayer.fromString(offensivePrayer);
    }

    /**
     * Whether to use cannon for this task.
     */
    @SerializedName("cannon")
    private boolean cannon = false;

    /**
     * Whether to use goading potions to attract monsters.
     * Automatically enabled for burst/barrage styles.
     */
    @SerializedName("goading")
    private boolean goading = false;

    /**
     * Whether this task requires antipoison potions.
     */
    @SerializedName("antipoison")
    private boolean antipoison = false;

    /**
     * Whether this task requires antivenom potions.
     */
    @SerializedName("antivenom")
    private boolean antivenom = false;

    /**
     * Whether this task requires super antifire potions.
     * Used for dragon tasks to provide full dragonfire protection.
     */
    @SerializedName("superAntifire")
    private boolean superAntifire = false;

    /**
     * Specific monster variant to target (optional).
     * Used when task name is generic but you want a specific monster.
     * Examples: "metal dragons" -> "rune dragons", "kalphite" -> "kalphite soldiers"
     */
    @SerializedName("variant")
    private String variant;

    /**
     * Preferred location for this task (optional).
     * Use "auto" or leave null for automatic selection.
     * This is used when NOT using a cannon.
     */
    @SerializedName("location")
    private String location;

    /**
     * Preferred location when using cannon for this task (optional).
     * If cannon is enabled and this is set, it will be used instead of location.
     * The cannon will also be placed at this location.
     */
    @SerializedName("cannonLocation")
    private String cannonLocation;

    /**
     * Whether to use special attack for this task.
     */
    @SerializedName("useSpecial")
    private boolean useSpecial = false;

    /**
     * Minimum special attack energy before using special (0-100).
     */
    @SerializedName("specialThreshold")
    private int specialThreshold = 50;

    /**
     * Minimum number of monsters to stack before casting burst/barrage.
     * Default is 3 for efficient AoE.
     */
    @SerializedName("minStackSize")
    private int minStackSize = 3;

    /**
     * Gets the parsed DroSlayerPrayer enum from the prayer string.
     */
    public DroSlayerPrayer getParsedPrayer() {
        return DroSlayerPrayer.fromString(prayer);
    }

    /**
     * Gets the parsed DroSlayerCombatStyle enum from the style string.
     */
    public DroSlayerCombatStyle getParsedStyle() {
        return DroSlayerCombatStyle.fromString(style);
    }

    /**
     * Checks if this profile has a specific setup defined.
     */
    public boolean hasSetup() {
        return setup != null && !setup.isEmpty();
    }

    /**
     * Checks if this profile has a specific combat style defined.
     */
    public boolean hasStyle() {
        return style != null && !style.isEmpty();
    }

    /**
     * Checks if this profile has a specific prayer defined.
     */
    public boolean hasPrayer() {
        return prayer != null && !prayer.isEmpty() && getParsedPrayer() != DroSlayerPrayer.NONE;
    }

    /**
     * Checks if this profile has a specific monster variant defined.
     */
    public boolean hasVariant() {
        return variant != null && !variant.isEmpty();
    }

    /**
     * Checks if this profile has a preferred location (non-cannon).
     */
    public boolean hasLocation() {
        return location != null && !location.isEmpty() && !location.equalsIgnoreCase("auto");
    }

    /**
     * Checks if this profile has a preferred cannon location.
     */
    public boolean hasCannonLocation() {
        return cannonLocation != null && !cannonLocation.isEmpty() && !cannonLocation.equalsIgnoreCase("auto");
    }

    /**
     * Gets the cannon location string.
     */
    public String getCannonLocation() {
        return cannonLocation;
    }

    /**
     * Checks if goading should be used (explicit or implicit via burst/barrage style).
     */
    public boolean shouldUseGoading() {
        if (goading) {
            return true;
        }
        // Automatically use goading for burst/barrage styles
        DroSlayerCombatStyle combatStyle = getParsedStyle();
        return combatStyle != null && combatStyle.isAoeStyle();
    }

    /**
     * Checks if this is a burst/barrage AoE combat style.
     */
    public boolean isAoeStyle() {
        DroSlayerCombatStyle combatStyle = getParsedStyle();
        return combatStyle != null && combatStyle.isAoeStyle();
    }
}
