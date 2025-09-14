package net.runelite.client.plugins.microbot.autofishing.enums;

import lombok.Getter;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.game.FishingSpot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Getter
public enum Fish {
    SHRIMP_AND_ANCHOVIES("Shrimp + Anchovies", List.of("Raw shrimps", "Shrimps", "Burnt shrimp", "Raw anchovies", "Anchovies", "Burnt fish"), FishingMethod.NET, FishingSpot.SHRIMP.getIds()),
    TROUT_AND_SALMON("Trout + Salmon", List.of("Raw trout", "Trout", "Raw salmon", "Salmon", "Burnt fish"), FishingMethod.LURE, FishingSpot.SALMON.getIds()),
    TUNA_AND_SWORDFISH("Tuna + Swordfish", List.of("Raw tuna", "Tuna", "Burnt fish", "Raw swordfish", "Swordfish", "Burnt swordfish"), FishingMethod.HARPOON, FishingSpot.LOBSTER.getIds()),
    ANGLERFISH("Anglerfish", List.of("Raw anglerfish", "Anglerfish", "Burnt anglerfish"), FishingMethod.SANDWORMS, FishingSpot.ANGLERFISH.getIds()),
    //BARBARIAN_FISH("Barbarian fish", List.of("Leaping trout", "Leaping salmon", "Leaping sturgeon"), FishingMethod.BARBARIAN_ROD, FishingSpot.BARB_FISH.getIds()),
    BASS("Bass", List.of("Raw bass", "Bass", "Burnt fish"), FishingMethod.BIG_NET, FishingSpot.SHARK.getIds()),
    //CAVE_EEL("Cave eel", List.of("Raw cave eel", "Cave eel", "Burnt eel"), FishingMethod.BAIT, FishingSpot.CAVE_EEL.getIds()),
    SACRED_EEL("Sacred eel", List.of("Sacred eel"), FishingMethod.SACRED_EEL_BAIT, FishingSpot.SACRED_EEL.getIds()),
    INFERNAL_EEL("Infernal eel", List.of("Infernal eel"), FishingMethod.INFERNAL_EEL_BAIT, FishingSpot.INFERNAL_EEL.getIds()),
    COD("Cod", List.of("Raw cod", "Cod", "Burnt fish"), FishingMethod.BIG_NET, FishingSpot.LOBSTER.getIds()),
    DARK_CRAB("Dark crab", List.of("Raw dark crab", "Dark crab", "Burnt dark crab"), FishingMethod.DARK_CRAB_BAIT, FishingSpot.DARK_CRAB.getIds()),
    HERRING("Herring", List.of("Raw herring", "Herring", "Burnt fish"), FishingMethod.BAIT, FishingSpot.SHRIMP.getIds()),
    KARAMBWAN("Karambwan", List.of("Raw karambwan", "Cooked karambwan", "Burnt karambwan"), FishingMethod.KARAMBWAN_VESSEL, FishingSpot.KARAMBWAN.getIds()),
    LAVA_EEL("Lava eel", List.of("Lava eel"), FishingMethod.OILY_ROD, FishingSpot.LAVA_EEL.getIds()),
    LOBSTER("Lobster", List.of("Raw lobster", "Lobster", "Burnt lobster"), FishingMethod.CAGE, FishingSpot.LOBSTER.getIds()),
    MACKEREL("Mackerel", List.of("Raw mackerel", "Mackerel", "Burnt fish"), FishingMethod.BIG_NET, FishingSpot.SHRIMP.getIds()),
    MONKFISH("Monkfish", List.of("Raw monkfish", "Monkfish", "Burnt monkfish"), FishingMethod.NET, FishingSpot.MONKFISH.getIds()),
    PIKE("Pike", List.of("Raw pike", "Pike", "Burnt fish"), FishingMethod.BAIT, FishingSpot.SALMON.getIds()),
    SARDINE("Sardine", List.of("Raw sardine", "Sardine", "Burnt fish"), FishingMethod.BAIT, FishingSpot.SHRIMP.getIds()),
    SHARK("Shark", List.of("Raw shark", "Shark", "Burnt shark"), FishingMethod.HARPOON, FishingSpot.SHARK.getIds());

    private final String name;
    private final List<String> itemNames; // raw, cook and burnt states
    private final FishingMethod method;
    private final int[] fishingSpot;

    Fish(String name, List<String> itemNames, FishingMethod method, int... fishingSpot) {
        this.name = name;
        this.itemNames = itemNames;
        this.method = method;
        this.fishingSpot = fishingSpot;
    }

    public List<String> getActions() {
        return method.getActions();
    }

    public List<String> getRequiredItems() {
        return method.getRequiredItems();
    }

    public List<FishingSpotLocation> getAvailableLocations() {
        FishingSpot spotType = getFishingSpotType();
        if (spotType == null) return new ArrayList<>();

        return Arrays.stream(FishingSpotLocation.values())
                .filter(location -> location.getTooltip().contains(spotType.getWorldMapTooltip()))
                .collect(Collectors.toList());
    }

    public WorldPoint getClosestLocation(WorldPoint playerLocation) {
        List<FishingSpotLocation> availableLocations = getAvailableLocations();
        if (availableLocations.isEmpty()) return null;

        WorldPoint closestPoint = null;
        int minDistance = Integer.MAX_VALUE;

        for (FishingSpotLocation location : availableLocations) {
            for (WorldPoint point : location.getLocations()) {
                int distance = playerLocation.distanceTo(point);
                if (distance < minDistance) {
                    minDistance = distance;
                    closestPoint = point;
                }
            }
        }

        return closestPoint;
    }

    private FishingSpot getFishingSpotType() {
        switch (this) {
            case SHRIMP_AND_ANCHOVIES:
            case HERRING:
            case SARDINE:
            case MACKEREL:
                return FishingSpot.SHRIMP;
            case LOBSTER:
            case TUNA_AND_SWORDFISH:
            case COD:
                return FishingSpot.LOBSTER;
            case SHARK:
            case BASS:
                return FishingSpot.SHARK;
            case TROUT_AND_SALMON:
            case PIKE:
                return FishingSpot.SALMON;
            case MONKFISH:
                return FishingSpot.MONKFISH;
            case KARAMBWAN:
                return FishingSpot.KARAMBWAN;
            case LAVA_EEL:
                return FishingSpot.LAVA_EEL;
            case SACRED_EEL:
                return FishingSpot.SACRED_EEL;
            case INFERNAL_EEL:
                return FishingSpot.INFERNAL_EEL;
            // case CAVE_EEL:
            //     return FishingSpot.CAVE_EEL;
            // case BARBARIAN_FISH:
            //     return FishingSpot.BARB_FISH;
            case ANGLERFISH:
                return FishingSpot.ANGLERFISH;
            case DARK_CRAB:
                return FishingSpot.DARK_CRAB;
            default:
                return null;
        }
    }

    @Override
    public String toString() {
        return name;
    }
}