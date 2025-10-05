package net.runelite.client.plugins.microbot.valetotems.handlers;

import net.runelite.api.Actor;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.valetotems.enums.GameObjectId;
import net.runelite.client.plugins.microbot.valetotems.enums.SpiritAnimal;
import net.runelite.client.plugins.microbot.valetotems.enums.TotemLocation;
import net.runelite.client.plugins.microbot.valetotems.models.TotemProgress;
import net.runelite.client.plugins.microbot.valetotems.utils.CoordinateUtils;
import net.runelite.client.plugins.microbot.valetotems.utils.GameObjectUtils;
import net.runelite.client.plugins.microbot.valetotems.utils.InventoryUtils;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepGaussian;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.RandomUtils;

/**
 * Handles all totem construction operations including building, carving, and decoration
 */
public class TotemHandler {

    private static final int TOTEM_INTERACTION_DISTANCE = 10;
    private static final int ANIMAL_SEARCH_RADIUS = 17;
    private static final long INTERACTION_TIMEOUT_MS = 5000; // 5 seconds
    private static final long CARVING_DELAY_MS = 1500; // Delay between key presses for carving

    /**
     * Waits until the current totem is ready for new construction.
     * This prevents starting a new totem before the current one is fully completed.
     * @param currentTotemLocation The location of the totem we intend to build.
     * @return true if the current totem is ready (TOTEM_SITE state), false if timed out.
     */
    private static boolean waitForPreviousTotemToFinish(TotemLocation currentTotemLocation) {
        long timeout = 300000; // 5 minutes
        long endTime = System.currentTimeMillis() + timeout;

        GameObjectId currentState = checkTotemState(currentTotemLocation);
        
        while (currentState != GameObjectId.TOTEM_SITE && currentState != null) {
            System.out.println("Waiting for current totem at " + currentTotemLocation.getDescription() + " to finish. Current state: " + currentState.name());
            sleep(2000); // Wait and re-check

            if (System.currentTimeMillis() > endTime) {
                System.err.println("Timed out waiting for current totem to be completed.");
                return false;
            }

            GameObjectUtils.invalidateCacheAtLocation(currentTotemLocation.getLocation());
            
            currentState = checkTotemState(currentTotemLocation);
        }

        System.out.println("Current totem at " + currentTotemLocation.getDescription() + " is ready for new construction.");
        return true;
    }

    /**
     * Build the totem base at the specified location
     * @param totemLocation the location to build the totem
     * @param progress the totem progress tracker
     * @return true if totem base was successfully built
     */
    public static boolean buildTotemBase(TotemLocation totemLocation, TotemProgress progress) {
        try {
            // Check if already built
            if (progress.isBaseBuilt()) {
                System.out.println("Totem base already built at " + totemLocation.getDescription());
                return true;
            }

            // Wait for any previously decorated totem to finish before starting a new one
            if (!waitForPreviousTotemToFinish(totemLocation)) {
                return false; // Timed out waiting
            }

            // Explicitly check if the current site is ready for building
            if (checkTotemState(totemLocation) != GameObjectId.TOTEM_SITE) {
                System.err.println("Totem site at " + totemLocation.getDescription() + " is not ready. Current state: " + checkTotemState(totemLocation));
                return false;
            }

            WorldPoint location = totemLocation.getLocation();
            

            // Ensure we're close enough to the totem site
            if (!CoordinateUtils.isAtTotemLocation(totemLocation, TOTEM_INTERACTION_DISTANCE)) {
                System.err.println("Not close enough to totem site");
                return false;
            }

            // Retry loop for building totem base
            int maxRetries = 3;
            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                System.out.println("Building totem base attempt " + attempt + "/" + maxRetries + " at " + totemLocation.getDescription());

                // Look for totem site using string search
                GameObject totemSite = GameObjectUtils.findObjectAtLocationByName(
                        GameObjectId.TOTEM_SITE.getSearchTerm(), location);
                
                if (totemSite == null) {
                    System.err.println("Totem site not found at location (attempt " + attempt + ")");
                    if (attempt < maxRetries) {
                        sleep(1000); // Wait before retry
                        continue;
                    }
                    return false;
                }

                // Interact with totem site to build
                boolean built = GameObjectUtils.interactWithObject(totemSite, "Build");
                
                if (built) {
                    // Wait for building animation and state change using action-based detection
                    boolean stateChanged = GameObjectUtils.waitForTotemStateAtLocation(
                            GameObjectId.EMPTY_TOTEM, location, INTERACTION_TIMEOUT_MS);
                    
                    if (stateChanged) {
                        progress.setBaseBuilt(true);
                        System.out.println("Totem base built successfully on attempt " + attempt);
                        return true;
                    } else {
                        System.err.println("Building interaction succeeded but state didn't change (attempt " + attempt + ")");
                    }
                } else {
                    System.err.println("Failed to interact with totem site (attempt " + attempt + ")");
                }

                // Wait before retry if this wasn't the last attempt
                if (attempt < maxRetries) {
                    sleep(2000); // Wait 2 seconds before retry
                }
            }

            System.err.println("Failed to build totem base after " + maxRetries + " attempts");
            return false;

        } catch (Exception e) {
            System.err.println("Error building totem base: " + e.getMessage());
            return false;
        }
    }

    /**
     * Identify spirit animals around the totem location
     * @param totemLocation the totem location to search around
     * @param progress the totem progress tracker
     * @return true if all 3 animals were identified
     */
    public static boolean identifySpiritAnimals(TotemLocation totemLocation, TotemProgress progress) {
        try {
            WorldPoint location = totemLocation.getLocation();
            
            System.out.println("Identifying spirit animals around " + totemLocation.getDescription());
            
            // Retry loop for identifying spirit animals
            boolean allIdentified = false;
            int maxAttempts = 5;
            int attemptCount = 0;
            
            while (attemptCount < maxAttempts && !allIdentified) {
                attemptCount++;
                Microbot.log("Attempting to identify spirit animals (attempt " + attemptCount + "/" + maxAttempts + ")");
                
                // Clear previous identifications for this attempt
                progress.clearIdentifiedAnimals();

                // Search for spirit animals in the area
                List<Rs2NpcModel> nearbyNpcs = Rs2Npc.getNpcs()
                        .filter(npc -> npc.getWorldLocation().distanceTo(location) <= ANIMAL_SEARCH_RADIUS)
                        .filter(npc -> SpiritAnimal.isSpiritAnimal(npc.getId()))
                        .collect(Collectors.toList());

                Microbot.log("Found " + nearbyNpcs.size() + " spirit animals in the area");

                if (nearbyNpcs.size() >= 3) {
                    // Identify the animals
                    for (Rs2NpcModel npc : nearbyNpcs) {
                        SpiritAnimal animal = SpiritAnimal.getByNpcId(npc.getId());
                        if (animal != null) {
                            progress.addIdentifiedAnimal(animal);
                            System.out.println("Identified: " + animal.getDescription());
                        }
                    }

                    allIdentified = progress.areAllAnimalsIdentified();
                    if (allIdentified) {
                        System.out.println("Successfully identified all 3 spirit animals");
                        printIdentifiedAnimals(progress);
                        break;
                    }
                }
                
                if (!allIdentified && attemptCount < maxAttempts) {
                    Microbot.log("Not enough spirit animals found or identified. Retrying in 2 seconds...");
                    sleep(2000);
                }
            }
            
            if (!allIdentified) {
                Microbot.log("Failed to identify all spirit animals after " + maxAttempts + " attempts");
            }

            return allIdentified;

        } catch (Exception e) {
            System.err.println("Error identifying spirit animals: " + e.getMessage());
            return false;
        }
    }

    /**
     * Carve the identified animals into the totem
     * @param totemLocation the totem location
     * @param progress the totem progress tracker
     * @return true if all animals were successfully carved
     */
    public static boolean carveAnimalsIntoTotem(TotemLocation totemLocation, TotemProgress progress) {
        try {
            WorldPoint location = totemLocation.getLocation();
            
            // Check if totem is ready for carving
            if (checkTotemState(totemLocation) != GameObjectId.EMPTY_TOTEM) {
                System.err.println("Totem not ready for carving. Current state: " + checkTotemState(totemLocation));
                return false;
            }
            
            // Check if we have identified animals
            if (!progress.areAllAnimalsIdentified()) {
                System.err.println("Animals not yet identified");
                return false;
            }

            Microbot.log("Carving animals into totem");

            sleepGaussian(400,300);

            // Carve each identified animal
            for (SpiritAnimal animal : progress.getIdentifiedAnimals()) {
                if (!progress.getCarvedAnimals().contains(animal)) {
                    // Add humanlike random hover over on spirit animal
                    hoverOverSpiritAnimal(animal);
                    if (carveAnimalIntoTotem(animal)) {
                        progress.addCarvedAnimal(animal);
                        System.out.println("Carved: " + animal.getDescription() + " (Key " + animal.getWidgetChildId() + ")");
                        
                        // Delay between carvings
                        sleep((int)CARVING_DELAY_MS);
                    } else {
                        System.err.println("Failed to carve: " + animal.getDescription());
                        return false;
                    }
                }
            }

            boolean allCarved = progress.areAllAnimalsCarved();
            if (allCarved) {
                System.out.println("Successfully carved all animals");
                
                // Wait for totem to become ready for decoration
                boolean readyForDecoration = GameObjectUtils.waitForTotemStateAtLocation(
                        GameObjectId.TOTEM_READY_FOR_DECORATION, location, INTERACTION_TIMEOUT_MS);

                if (!readyForDecoration) {
                    System.err.println("Totem did not become ready for decoration");
                    return false;
                }
            }

            return allCarved;

        } catch (Exception e) {
            System.err.println("Error carving animals: " + e.getMessage());
            return false;
        }
    }

    /**
     * Carve a specific animal into the totem using keyboard input
     * @param animal the spirit animal to carve
     * @return true if successfully carved
     */
    private static boolean carveAnimalIntoTotem(SpiritAnimal animal) {
        try {
            if (!sleepUntil(() -> Rs2Widget.hasWidgetText("What animal would you like to carve?",270,5, false), 5000)) {
                Rs2GameObject.interact(GameObjectId.EMPTY_TOTEM.getSearchTerm(), "Carve");
                if (!sleepUntil(() -> Rs2Widget.hasWidgetText("What animal would you like to carve?",270,5, false), 5000)) {
                    System.err.println("Failed to carve animal");
                    return false;
                }
            }

            //Humanlike delay
            sleepGaussian(300,200);

            // Small 2% change to mouse click to avoid detection
            int mouseClickChange = RandomUtils.nextInt(1,100);
            if (mouseClickChange <= 2) {
                // Press the corresponding number key
                Rs2Widget.clickWidget(270,animal.getWidgetChildId());
                return true;
            }

            // Press the corresponding number key
            Rs2Keyboard.keyPress(animal.getKeyNumber());

            return true;
        } catch (Exception e) {
            System.err.println("Error carving animal " + animal.getDescription() + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Decorate the totem with fletched bows
     * @param totemLocation the totem location
     * @param progress the totem progress tracker
     * @return true if totem was successfully decorated
     */
    public static boolean decorateTotem(TotemLocation totemLocation, TotemProgress progress) {
        try {
            WorldPoint location = totemLocation.getLocation();
            // Check if we have enough bows
            if (!InventoryUtils.hasEnoughBowsForOneTotem()) {
                System.err.println("Not enough bows for decoration. Need 4, have: " + InventoryUtils.getBowCount());
                return false;
            }

            // Check if totem is ready for decoration
            if (checkTotemState(totemLocation) != GameObjectId.TOTEM_READY_FOR_DECORATION) {
                System.err.println("Totem not ready for decoration. Current state: " + checkTotemState(totemLocation));
                return false;
            }

            // Find the totem object to interact with.
            GameObject decorationTotem = GameObjectUtils.findObjectAtLocationByName(
                    GameObjectId.TOTEM_READY_FOR_DECORATION.getSearchTerm(), location);

            if (decorationTotem == null) {
                System.err.println("Totem object not found for decoration");
                return false;
            }

            Microbot.log("Decorating totem with fletched bows");

            // Lets track when we have placed 4 bows, so inventory has -4 bows vs at start of decoration
            int startingBows = InventoryUtils.getBowCount();

            // Interact with totem to decorate with retry loop
            boolean decorated = false;
            int maxAttempts = 3;
            int attemptCount = 0;
            
            while (attemptCount < maxAttempts && !decorated) {
                attemptCount++;
                decorated = GameObjectUtils.interactWithObject(decorationTotem, "Decorate");
                if (!decorated && attemptCount < maxAttempts) {
                    Microbot.log("Decoration attempt failed, retrying in 1 second...");
                    sleep(1000);
                }
            }
            
            if (!decorated) {
                Microbot.log("Failed to decorate totem after " + maxAttempts + " attempts");
                return false;
            }
            
            if (decorated) {
                //Sleep until we have 4 less bows in inventory
                sleepUntil(() -> InventoryUtils.getBowCount() == startingBows - 4, 6000);
                int endingBows = InventoryUtils.getBowCount();
                if (endingBows - startingBows == -4) {
                    progress.setDecorated(true);
                    progress.markCompleted();
                    
                    // Clear failed ent trails so they can be retried on next navigation
                    NavigationHandler.clearFailedEntTrails();
                    
                    Microbot.log("Totem decoration completed!");
                    return true;
                }
            }

            return false;

        } catch (Exception e) {
            System.err.println("Error decorating totem: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check the current state of totem construction at a location
     * @param totemLocation the totem location to check
     * @return the current GameObjectId representing the totem state
     */
    public static GameObjectId checkTotemState(TotemLocation totemLocation) {
        return GameObjectUtils.getTotemStateAtLocation(totemLocation.getLocation());
    }

    /**
     * Print identified animals for debugging
     * @param progress the totem progress tracker
     */
    private static void printIdentifiedAnimals(TotemProgress progress) {
        System.out.println("Identified animals:");
        for (SpiritAnimal animal : progress.getIdentifiedAnimals()) {
            System.out.println("  - " + animal.getDescription() + " (Key " + animal.getWidgetChildId() + ")");
        }
    }

    /**
     * Get totem construction status summary
     * @param totemLocation the totem location
     * @param progress the totem progress tracker
     * @return formatted string with construction status
     */
    public static String getTotemStatus(TotemLocation totemLocation, TotemProgress progress) {
        GameObjectId currentState = checkTotemState(totemLocation);
        String stateName = currentState != null ? currentState.name() : "UNKNOWN";
        
        return String.format("%s | State: %s | %s",
                totemLocation.getDescription(),
                stateName,
                progress.toString());
    }

    /**
     * Reset totem construction (for error recovery)
     * @param progress the totem progress to reset
     */
    public static void resetTotemConstruction(TotemProgress progress) {
        progress.setBaseBuilt(false);
        progress.clearIdentifiedAnimals();
        progress.setDecorated(false);
        System.out.println("Totem construction state reset");
    }

    private static void hoverOverSpiritAnimal(SpiritAnimal animal) {
        int randomChange = RandomUtils.nextInt(1,100);
        if (randomChange > 10) {
            return;
        }

        System.out.println("Potential hover over spirit animal: " + animal.getDescription());

        sleepGaussian(300,100);

        // Get the spirit animal's location
        WorldPoint animalLocation = Rs2Npc.getNpcs()
                .filter(npc -> npc.getId() == animal.getNpcId())
                .findFirst()
                .map(npc -> npc.getWorldLocation())
                .orElse(null);

        if (animalLocation == null) {
            System.out.println("Spirit animal not found");
            return;
        }

        int distance = Rs2Player.distanceTo(animalLocation);
        if (distance < 4) {
            System.out.println("Spirit animal too close");
            return;
        }

        // Get the spirit animal's NPC model
        Rs2NpcModel animalNpc = Rs2Npc.getNpcs()
                .filter(npc -> npc.getId() == animal.getNpcId())
                .findFirst()
                .orElse(null);

        if (animalNpc == null) {
            System.out.println("Spirit animal NPC not found");
            return;
        }

        // Check if we have line of sight to the animal
        if (Rs2Npc.hasLineOfSight(animalNpc)) {
            System.out.println("Spirit animal has line of sight");
            return;
        }

        System.out.println("Hovering over spirit animal");

        if (Rs2Npc.hoverOverActor(animalNpc)) {
            System.out.println("Successfully hovered over spirit animal");
        } else {
            System.out.println("Failed to hover over spirit animal");
        }

        sleepGaussian(500,300);
    }
} 