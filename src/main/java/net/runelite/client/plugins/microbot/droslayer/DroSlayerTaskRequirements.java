package net.runelite.client.plugins.microbot.droslayer;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Small registry for Slayer-specific mandatory equipment/tools and survival supplies that should
 * be layered on top of the user's selected Inventory Setup. The Inventory Setup remains the base
 * loadout; these requirements only add/replace the pieces that the task itself requires.
 *
 * RuneLite's Task weakness item is handled separately in DroSlayerScript so current and future
 * finishing items (rock hammer, salt, ice cooler, fungicide spray, etc.) follow RuneLite's own
 * task data rather than being duplicated here.
 *
 * Survival consumables intentionally accept a family of equivalent potions. The banker prefers
 * the strongest/full-dose option but does not require that exact variant: e.g. a dragon task can
 * use extended super antifire, super antifire, extended antifire, or regular antifire; poison
 * tasks accept antidotes/antipoisons/anti-venoms as fallbacks.
 */
public final class DroSlayerTaskRequirements {
    private DroSlayerTaskRequirements() {}

    @Getter
    @RequiredArgsConstructor
    public static final class Requirement {
        private final String label;
        private final String preferredName;
        private final String[] acceptedNames;
        private final boolean equipped;
        private final boolean slayerHelmetCovers;
        private final int quantity;

        public static Requirement equipped(String label, String preferredName, boolean slayerHelmetCovers, String... acceptedNames) {
            return new Requirement(label, preferredName, acceptedNames, true, slayerHelmetCovers, 1);
        }

        public static Requirement inventory(String label, String preferredName, int quantity, String... acceptedNames) {
            return new Requirement(label, preferredName, acceptedNames, false, false, Math.max(1, quantity));
        }
    }

    public static List<Requirement> forTask(String taskName, String locationName) {
        return forTask(taskName, locationName, false, false, false);
    }

    /**
     * @param forceAntipoison preserves an explicit user/profile antipoison request
     * @param forceAntivenom preserves an explicit user/profile antivenom request
     * @param forceAntifire preserves an explicit user/profile super-antifire request
     */
    public static List<Requirement> forTask(String taskName,
                                            String locationName,
                                            boolean forceAntipoison,
                                            boolean forceAntivenom,
                                            boolean forceAntifire) {
        if (taskName == null || taskName.isBlank()) return Collections.emptyList();

        String task = normalize(taskName);
        String location = normalize(locationName);
        List<Requirement> out = new ArrayList<>();

        if (location.contains("sophanem") || task.contains("scabar")) {
            // These lights cannot go out and their names distinguish them from unlit lamps.
            out.add(Requirement.inventory("dungeon light source", "Bruma torch", 1,
                    "Bruma torch", "Kandarin headgear"));
            out.add(Requirement.inventory("dungeon tinderbox", "Tinderbox", 1, "Tinderbox"));
        }

        switch (task) {
            case "custodian stalkers":
                // No mandatory Slayer tool/equipment. The task itself is gated by
                // Shadows of Custodia + Slayer level; normal combat supplies are enough.
                break;
            case "banshees":
                out.add(Requirement.equipped("ear protection", "Earmuffs", true,
                        "Earmuffs", "Slayer helmet"));
                break;
            case "aberrant spectres":
                out.add(Requirement.equipped("nose protection", "Nose peg", true,
                        "Nose peg", "Slayer helmet"));
                break;
            case "dust devils":
            case "smoke devils":
            case "the thermonuclear smoke devil":
                out.add(Requirement.equipped("face protection", "Facemask", true,
                        "Facemask", "Slayer helmet"));
                break;
            case "wall beasts":
                out.add(Requirement.equipped("wall-beast protection", "Spiny helmet", true,
                        "Spiny helmet", "Slayer helmet"));
                break;
            case "sourhogs":
                out.add(Requirement.equipped("eye protection", "Reinforced goggles", true,
                        "Reinforced goggles", "Slayer helmet"));
                break;
            case "basilisks":
            case "cockatrice":
            case "cockatrices":
                out.add(Requirement.equipped("mirror protection", "Mirror shield", false,
                        "Mirror shield", "V's shield"));
                break;
            case "cave horrors":
                out.add(Requirement.equipped("cave-horror protection", "Witchwood icon", false,
                        "Witchwood icon"));
                break;
            case "killerwatts":
                out.add(Requirement.equipped("electrical protection", "Insulated boots", false,
                        "Insulated boots"));
                break;
            case "fever spiders":
                out.add(Requirement.equipped("fever-spider protection", "Slayer gloves", false,
                        "Slayer gloves"));
                break;
            case "harpie bug swarms":
                out.add(Requirement.equipped("lit bug lantern", "Lit bug lantern", false,
                        "Lit bug lantern"));
                break;
            case "warped creatures":
                out.add(Requirement.inventory("crystal chime", "Crystal chime", 1,
                        "Crystal chime"));
                break;
            case "molanisks":
                out.add(Requirement.inventory("slayer bell", "Slayer bell", 1,
                        "Slayer bell"));
                break;
            case "mogres":
                out.add(Requirement.inventory("fishing explosive", "Fishing explosive", 1,
                        "Fishing explosive", "Super fishing explosive"));
                break;
            case "turoth":
            case "kurask":
                // If a ranged setup already has broad ammunition, or a magic setup has a
                // Slayer's staff, do not replace it. Otherwise leaf-bladed sword is the safe
                // bank fallback layered on top of the selected Inventory Setup.
                out.add(Requirement.equipped("Turoth/Kurask-capable weapon or ammo", "Leaf-bladed sword", false,
                        "Leaf-bladed sword", "Leaf-bladed battleaxe", "Leaf-bladed spear",
                        "Slayer's staff", "Broad bolts", "Broad arrows"));
                break;
            default:
                break;
        }

        if (location.contains("god wars")) {
            out.add(Requirement.inventory("God Wars entrance rope", "Rope", 1, "Rope"));
        }

        // Karuulm floor protection is location-specific rather than monster-specific.
        if ((location.contains("karuulm") || location.contains("alchemical hydra"))
                && out.stream().noneMatch(r -> r.getLabel().contains("Karuulm"))) {
            out.add(Requirement.equipped("Karuulm floor protection", "Boots of brimstone", false,
                    "Boots of stone", "Boots of brimstone", "Granite boots"));
        }

        // Wyvern breath protection. Antifire potions do not protect against skeletal/Fossil
        // Island wyvern icy breath, so these tasks deliberately use shield protection instead.
        if (task.equals("skeletal wyverns") || task.equals("fossil island wyverns")) {
            out.add(Requirement.equipped("wyvern breath protection", "Elemental shield", false,
                    "Elemental shield", "Mind shield", "Dragonfire shield", "Dragonfire ward", "Ancient wyvern shield"));
        }

        boolean venom = forceAntivenom || requiresVenomProtection(task, location);
        boolean poison = forceAntipoison || requiresPoisonProtection(task, location);
        boolean antifire = forceAntifire || requiresDragonfireProtection(task, location);

        // Venom protection already covers poison, so do not spend a second inventory slot on both.
        if (venom) {
            out.add(venomRequirement());
        } else if (poison) {
            out.add(poisonRequirement());
        }

        if (antifire) {
            if (!task.contains("drake") && requiresDragonfireProtection(task, location)) {
                out.add(Requirement.equipped("dragonfire shield protection", "Anti-dragon shield", false,
                        "Anti-dragon shield", "Dragonfire shield", "Dragonfire ward", "Ancient wyvern shield"));
            }
            out.add(antifireRequirement());
        }

        return out;
    }

    /** High-risk task categories for which poison protection is a practical survival supply. */
    public static boolean requiresPoisonProtection(String taskName, String locationName) {
        String task = normalize(taskName);
        String location = normalize(locationName);
        if (task.isEmpty()) return false;

        // Adamant dragons have a poison special; rune dragons do not.
        if (task.contains("adamant dragon")) return true;

        return containsAny(task,
                "hydra",
                "kalphite",
                "cave crawler",
                "cave slime",
                "lizardmen",
                "lizardman",
                "scorpion",
                "spider",
                "sea snake",
                "zygomite")
                // Keep existing curated locations/profile behavior safe when older JSON names differ.
                || location.contains("kalphite");
    }

    /** Araxytes can inflict venom and are the main standard Slayer task that needs venom immunity. */
    public static boolean requiresVenomProtection(String taskName, String locationName) {
        String task = normalize(taskName);
        String location = normalize(locationName);
        return task.contains("araxyte") || task.contains("araxxor")
                || location.contains("araxyte") || location.contains("araxxor");
    }

    /**
     * Adult chromatic/metal/brutal/frost dragons and drakes can deal dangerous dragonfire damage.
     * Wyverns use icy breath (potion ineffective) and wyrms/hydras do not use normal dragonfire.
     */
    public static boolean requiresDragonfireProtection(String taskName, String locationName) {
        String task = normalize(taskName);
        String location = normalize(locationName);
        if (task.isEmpty()) return false;

        if (task.contains("wyvern") || task.contains("wyrm") || task.contains("hydra")) {
            return false;
        }
        if ((task.contains("baby") && task.contains("dragon"))
                || (location.contains("baby") && location.contains("dragon"))) return false;
        if (task.contains("drake")) return true;
        if (task.contains("dragon") && !task.contains("baby dragon")) return true;

        // A known assignment is authoritative: nearby landmarks such as Lava Dragon Isle
        // must not turn Spiders (or another non-dragon task) into a dragon assignment.
        if (net.runelite.client.plugins.slayer.Task.getTask(task) != null) return false;

        // Location fallback for a generic/custom task profile pointed directly at a dragon room.
        return (location.contains("dragon") || location.contains("lithkren vault"))
                && !location.contains("wyvern");
    }

    private static Requirement antifireRequirement() {
        String[] accepted = potionVariants(1,
                "Extended super antifire",
                "Super antifire",
                "Extended antifire",
                "Antifire");
        return Requirement.inventory("dragonfire protection potion", "Extended super antifire(4)", 1, accepted);
    }

    private static Requirement poisonRequirement() {
        String[] accepted = concat(
                potionVariants(1, "Superantipoison", "Antidote++", "Antidote+", "Antipoison"),
                potionVariants(1, "Sanfew serum", "Extended anti-venom+", "Anti-venom+", "Anti-venom"));
        return Requirement.inventory("poison protection/cure", "Superantipoison(4)", 1, accepted);
    }

    private static Requirement venomRequirement() {
        // One anti-venom dose cures venom outright. Ordinary poison cures need two doses to fully
        // clear venom, so only 2+ dose containers are accepted as that last-resort bank fallback.
        String[] accepted = concat(
                potionVariants(1, "Extended anti-venom+", "Anti-venom+", "Anti-venom"),
                potionVariants(2, "Antidote++", "Antidote+", "Superantipoison", "Antipoison", "Sanfew serum"));
        return Requirement.inventory("venom protection/cure", "Extended anti-venom+(4)", 1, accepted);
    }

    private static String[] potionVariants(int minimumDose, String... baseNames) {
        List<String> names = new ArrayList<>();
        int min = Math.max(1, Math.min(4, minimumDose));
        for (String base : baseNames) {
            if (base == null || base.isBlank()) continue;
            for (int dose = 4; dose >= min; dose--) {
                names.add(base + "(" + dose + ")");
            }
        }
        return names.toArray(new String[0]);
    }

    private static String[] concat(String[]... arrays) {
        List<String> out = new ArrayList<>();
        for (String[] array : arrays) {
            if (array == null) continue;
            Collections.addAll(out, array);
        }
        return out.toArray(new String[0]);
    }

    private static boolean containsAny(String value, String... needles) {
        if (value == null) return false;
        for (String needle : needles) {
            if (needle != null && value.contains(needle)) return true;
        }
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }
}
