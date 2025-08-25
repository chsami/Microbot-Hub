package net.runelite.client.plugins.microbot.valetotems.models;

import net.runelite.api.ItemID;
import net.runelite.client.plugins.microbot.valetotems.enums.SpiritAnimal;
import net.runelite.client.plugins.microbot.valetotems.enums.TotemLocation;
import net.runelite.client.plugins.microbot.valetotems.utils.FletchingItemMapper;
import net.runelite.client.plugins.microbot.valetotems.utils.InventoryUtils;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * Model class to track the progress of a single totem construction
 */
public class TotemProgress {
    private final TotemLocation location;
    private boolean baseBuilt;
    private List<SpiritAnimal> identifiedAnimals;
    private List<SpiritAnimal> carvedAnimals;
    private boolean decorated;
    private boolean offeringsCollected;
    private long startTime;
    private long completionTime;

    public TotemProgress(TotemLocation location) {
        this.location = location;
        this.baseBuilt = false;
        this.identifiedAnimals = new ArrayList<>();
        this.carvedAnimals = new ArrayList<>();
        this.decorated = false;
        this.offeringsCollected = false;
        this.startTime = System.currentTimeMillis();
        this.completionTime = 0;
    }

    // Getters
    public TotemLocation getLocation() {
        return location;
    }

    public boolean isBaseBuilt() {
        return baseBuilt;
    }

    public List<SpiritAnimal> getIdentifiedAnimals() {
        return new ArrayList<>(identifiedAnimals);
    }

    public List<SpiritAnimal> getCarvedAnimals() {
        return new ArrayList<>(carvedAnimals);
    }

    public boolean isDecorated() {
        return decorated;
    }

    public boolean areOfferingsCollected() {
        return offeringsCollected;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getCompletionTime() {
        return completionTime;
    }

    // Progress tracking methods
    public void setBaseBuilt(boolean baseBuilt) {
        this.baseBuilt = baseBuilt;
    }

    public void addIdentifiedAnimal(SpiritAnimal animal) {
        if (!identifiedAnimals.contains(animal)) {
            identifiedAnimals.add(animal);
        }
    }

    public void clearIdentifiedAnimals() {
        identifiedAnimals.clear();
    }

    public void addCarvedAnimal(SpiritAnimal animal) {
        if (!carvedAnimals.contains(animal)) {
            carvedAnimals.add(animal);
        }
    }

    public void setDecorated(boolean decorated) {
        this.decorated = decorated;
    }

    public void setOfferingsCollected(boolean collected) {
        this.offeringsCollected = collected;
    }

    // Status checking methods
    public boolean areAllAnimalsIdentified() {
        return identifiedAnimals.size() >= 3;
    }

    public boolean areAllAnimalsCarved() {
        return carvedAnimals.size() >= 3 && carvedAnimals.containsAll(identifiedAnimals);
    }

    public boolean areEnoughBowsFletched() {
        return InventoryUtils.getBowCount() >= 4;
    }

    public boolean isCompletelyFinished() {
        return baseBuilt && areAllAnimalsCarved() && decorated;
    }

    public boolean needsOfferingsCollection() {
        return isCompletelyFinished() && !offeringsCollected;
    }

    public SpiritAnimal getNextAnimalToCarve() {
        for (SpiritAnimal animal : identifiedAnimals) {
            if (!carvedAnimals.contains(animal)) {
                return animal;
            }
        }
        return null;
    }

    public void markCompleted() {
        this.completionTime = System.currentTimeMillis();
    }

    public long getDurationMs() {
        long endTime = completionTime > 0 ? completionTime : System.currentTimeMillis();
        return endTime - startTime;
    }

    @Override
    public String toString() {
        return String.format("Totem %d: Base=%s, Animals=%d/3 identified, %d/3 carved, Bows=%d/4, Decorated=%s",
                location.getOrder(),
                baseBuilt ? "✓" : "✗",
                identifiedAnimals.size(),
                carvedAnimals.size(),
                InventoryUtils.getBowCount(),
                decorated ? "✓" : "✗");
    }
} 