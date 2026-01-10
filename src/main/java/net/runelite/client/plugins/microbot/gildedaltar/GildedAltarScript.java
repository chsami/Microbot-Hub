package net.runelite.client.plugins.microbot.gildedaltar;

import net.runelite.api.ObjectID;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;package net.runelite.client.plugins.microbot.gildedaltar;

import net.runelite.api.ObjectID;
import net.runelite.api.TileObject;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class GildedAltarScript extends Script {

    // Constants
    private static final int HOUSE_PORTAL_OBJECT = 4525;
    private static final int PHIALS_CHAT_WIDGET = 14352385;
    private static final int ADVERTISEMENT_WIDGET = 3407875;
    private static final int WORLD_330 = 330;

    // Dialog/Sprite Widget IDs (these need to be verified in-game with widget inspector)
    private static final int DIALOG_SPRITE_GROUP = 193; // Common dialog widget group
    private static final int DIALOG_TEXT_CHILD = 2; // Text child in dialog

    // Widget IDs
    private static final int WIDGET_CONTAINER_NAMES = 52;
    private static final int WIDGET_CONTAINER_ENTER = 52;
    private static final int WIDGET_CHILD_NAMES = 9;
    private static final int WIDGET_CHILD_ENTER = 19;
    private static final int WIDGET_ENTER_BUTTON = 3407891;

    // State variables
    public static GildedAltarPlayerState state = GildedAltarPlayerState.IDLE;

    // House tracking
    private HouseData currentHouse;
    private boolean visitedOnce = false;
    private List<String> blacklistNames = new ArrayList<>();
    private long lastDialogCheck = 0;
    private boolean wasLoggedIn = false;

    // Helper class to track house-specific data
    private static class HouseData {
        String ownerName;

        void reset() {
            ownerName = null;
        }
    }

    // State calculation
    private boolean inHouse() {
        return Rs2Npc.getNpc("Phials") == null;
    }

    private boolean hasUnNotedBones() {
        return Rs2Inventory.hasUnNotedItem("bones");
    }

    private boolean hasNotedBones() {
        return Rs2Inventory.hasNotedItem("bones");
    }

    private void calculateState() {
        boolean inHouse = inHouse();
        boolean hasUnNotedBones = hasUnNotedBones();

        if (hasUnNotedBones) {
            state = inHouse ? GildedAltarPlayerState.BONES_ON_ALTAR : GildedAltarPlayerState.ENTER_HOUSE;
        } else {
            state = inHouse ? GildedAltarPlayerState.LEAVE_HOUSE : GildedAltarPlayerState.UNNOTE_BONES;
        }
    }

    private void onLogin() {
        System.out.println("=== Login detected - Performing initial setup ===");

        // Reset all state variables
        visitedOnce = false;
        blacklistNames.clear();
        currentHouse.reset();

        // Check if we're on world 330
        int currentWorld = Microbot.getClient().getWorld();
        if (currentWorld != WORLD_330) {
            System.out.println("Not on world 330 (current: " + currentWorld + "), attempting to hop to world 330...");
            hopToWorld(WORLD_330);

            // Wait and verify hop was successful
            boolean hopSuccessful = sleepUntil(() -> Microbot.getClient().getWorld() == WORLD_330, 10000);

            if (hopSuccessful) {
                System.out.println("Successfully hopped to world 330");
                sleep(2000, 3000); // Wait for world to stabilize
            } else {
                int finalWorld = Microbot.getClient().getWorld();
                System.out.println("Failed to hop to world 330, still on world " + finalWorld);
                Microbot.showMessage("Failed to hop to W330 - world might be full. Please manually hop to W330.");
                // Don't shutdown, let user manually hop
            }
        } else {
            System.out.println("Already on world 330");
        }

        System.out.println("Initial setup complete - will check house advertisements");
    }

    private void checkForDialogs() {
        // Only check every 2 seconds to avoid spam
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastDialogCheck < 2000) {
            return;
        }
        lastDialogCheck = currentTime;

        // Check for sprite dialog (the popup notification)
        Widget spriteDialog = Microbot.getClient().getWidget(DIALOG_SPRITE_GROUP, DIALOG_TEXT_CHILD);
        if (spriteDialog != null && spriteDialog.getText() != null) {
            String dialogText = spriteDialog.getText().toLowerCase();
            System.out.println("Dialog detected: " + dialogText);

            // House unavailable messages
            if (dialogText.contains("offline") ||
                    dialogText.contains("not available") ||
                    dialogText.contains("no longer accessible") ||
                    dialogText.contains("connection") ||
                    dialogText.contains("hasn't visited")) {

                System.out.println("House unavailable dialog detected");
                handleHouseUnavailable();

                // Click through the dialog
                Rs2Keyboard.keyPress(' ');
                sleep(600);
                return;
            }

            // No houses available message
            if (dialogText.contains("no houses") ||
                    dialogText.contains("no advertisers")) {

                System.out.println("No houses available dialog detected");
                handleNoHousesAvailable();

                // Click through the dialog
                Rs2Keyboard.keyPress(' ');
                sleep(600);
                return;
            }
        }

        // Also check chatbox area for these messages
        Widget[] chatboxWidgets = Microbot.getClient().getWidget(162, 44) != null ?
                Microbot.getClient().getWidget(162, 44).getDynamicChildren() : null;

        if (chatboxWidgets != null) {
            for (Widget w : chatboxWidgets) {
                if (w != null && w.getText() != null && !w.getText().isEmpty()) {
                    String text = w.getText().toLowerCase();

                    if ((text.contains("no houses") || text.contains("no advertisers")) &&
                            !text.contains("has")) {
                        System.out.println("No houses message in chatbox");
                        handleNoHousesAvailable();
                        return;
                    }
                }
            }
        }
    }

    public boolean run(GildedAltarConfig config) {
        blacklistNames = new ArrayList<>();
        currentHouse = new HouseData();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) {
                    wasLoggedIn = false;
                    return;
                }

                // Detect fresh login (after break or initial start)
                if (!wasLoggedIn) {
                    onLogin();
                    wasLoggedIn = true;
                }

                if (!super.run()) return;

                // Check for dialogs first
                checkForDialogs();

                // Validate inventory
                if (!Rs2Inventory.hasItem(995)) {
                    Microbot.showMessage("No gp found in your inventory");
                    shutdown();
                    return;
                }

                if (!hasNotedBones() && !hasUnNotedBones()) {
                    Microbot.showMessage("No bones found in your inventory");
                    shutdown();
                    return;
                }

                // Don't interrupt XP gains (animations)
                if (Microbot.isGainingExp) return;

                calculateState();

                switch (state) {
                    case LEAVE_HOUSE:
                        leaveHouse();
                        break;
                    case UNNOTE_BONES:
                        unnoteBones();
                        break;
                    case ENTER_HOUSE:
                        enterHouse();
                        break;
                    case BONES_ON_ALTAR:
                        bonesOnAltar();
                        break;
                }
            } catch (Exception ex) {
                System.out.println("Error in main loop: " + ex.getMessage());
                ex.printStackTrace();
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    private void leaveHouse() {
        System.out.println("Leaving house...");

        TileObject portal = Rs2GameObject.findObjectById(HOUSE_PORTAL_OBJECT);
        if (portal == null) {
            System.out.println("Portal not found, already outside?");
            return;
        }

        if (Rs2GameObject.interact(portal, "Enter")) {
            sleepUntil(() -> !inHouse(), 5000);
        }
    }

    private void unnoteBones() {
        // Check if Phials dialog is open
        if (Microbot.getClient().getWidget(PHIALS_CHAT_WIDGET) != null) {
            Rs2Keyboard.keyPress('3');
            Rs2Inventory.waitForInventoryChanges(2000);
            return;
        }

        // Start interaction with Phials
        if (!Rs2Inventory.isItemSelected()) {
            Rs2Inventory.use("bones");
            sleep(300, 600);
        } else {
            if (Rs2Npc.interact("Phials", "Use")) {
                Rs2Player.waitForWalking();
                sleepUntil(() -> Microbot.getClient().getWidget(PHIALS_CHAT_WIDGET) != null, 3000);
            }
        }
    }

    private void enterHouse() {
        // Use Visit-Last if we've already visited a house this session
        if (visitedOnce) {
            if (Rs2GameObject.interact(ObjectID.HOUSE_ADVERTISEMENT, "Visit-Last")) {
                sleep(600, 1200);
                if (sleepUntil(() -> inHouse(), 5000)) {
                    onHouseEntered();
                }
            }
            return;
        }

        // Open house advertisement board
        if (!Rs2Widget.isWidgetVisible(ADVERTISEMENT_WIDGET)) {
            if (Rs2GameObject.interact(ObjectID.HOUSE_ADVERTISEMENT, "View")) {
                sleepUntil(() -> Rs2Widget.isWidgetVisible(ADVERTISEMENT_WIDGET), 3000);
            }
            return;
        }

        // Select a house to enter (skip sorting, just find one with gilded altar)
        String selectedOwner = selectHouseOwner();
        if (selectedOwner == null) {
            System.out.println("No available houses found on advertisement board");
            handleNoHousesAvailable();
            return;
        }

        // Find and click the Enter button
        Widget enterButton = findEnterButton(selectedOwner);
        if (enterButton != null) {
            currentHouse.ownerName = selectedOwner;
            currentHouse.reset();

            Rs2Widget.clickChildWidget(WIDGET_ENTER_BUTTON, enterButton.getIndex());
            visitedOnce = true;

            sleep(600, 1200);
            if (sleepUntil(() -> inHouse(), 5000)) {
                onHouseEntered();
            }
        }
    }

    private String selectHouseOwner() {
        Widget containerNames = Rs2Widget.getWidget(WIDGET_CONTAINER_NAMES, WIDGET_CHILD_NAMES);
        if (containerNames == null || containerNames.getChildren() == null) {
            return null;
        }

        String selectedOwner = null;
        int smallestY = Integer.MAX_VALUE;

        for (Widget child : containerNames.getChildren()) {
            if (child == null || child.getText() == null || child.getText().isEmpty()) {
                continue;
            }

            String name = child.getText();
            if (child.getOriginalY() < smallestY && !blacklistNames.contains(name)) {
                selectedOwner = name;
                smallestY = child.getOriginalY();
            }
        }

        return selectedOwner;
    }

    private Widget findEnterButton(String ownerName) {
        Widget containerEnter = Rs2Widget.getWidget(WIDGET_CONTAINER_ENTER, WIDGET_CHILD_ENTER);
        if (containerEnter == null || containerEnter.getChildren() == null) {
            return null;
        }

        for (Widget child : containerEnter.getChildren()) {
            if (child == null || child.getOnOpListener() == null) {
                continue;
            }

            Object[] listenerArray = child.getOnOpListener();
            boolean containsOwner = Arrays.stream(listenerArray)
                    .filter(Objects::nonNull)
                    .anyMatch(obj -> obj.toString().replace("\u00A0", " ").contains(ownerName));

            if (containsOwner) {
                return child;
            }
        }

        return null;
    }

    private void onHouseEntered() {
        System.out.println("Entered house owned by: " + currentHouse.ownerName);
    }

    private void bonesOnAltar() {
        // Wait for any ongoing animations
        if (Rs2Player.isAnimating()) {
            return;
        }

        // Find altar directly - Rs2GameObject handles the pathfinding
        TileObject altar = Rs2GameObject.getGameObject("Altar");
        if (altar == null) {
            System.out.println("Altar not found");
            return;
        }

        // Use bones on altar - this handles walking to it automatically
        if (Rs2Inventory.useUnNotedItemOnObject("bones", altar.getId())) {
            Rs2Player.waitForAnimation();
        }
    }

    public void handleHouseUnavailable() {
        System.out.println("House unavailable: " + currentHouse.ownerName);

        // Add current house owner to blacklist
        if (currentHouse.ownerName != null && !blacklistNames.contains(currentHouse.ownerName)) {
            blacklistNames.add(currentHouse.ownerName);
            System.out.println("Added " + currentHouse.ownerName + " to blacklist");
        }

        // Reset to force re-checking house advertisements (don't use Visit-Last)
        visitedOnce = false;
        currentHouse.reset();

        System.out.println("Will select a new house from advertisements");
    }

    public void handleNoHousesAvailable() {
        System.out.println("=== No houses available on advertisement board ===");

        // Close the advertisement board interface before world hopping
        if (Rs2Widget.isWidgetVisible(ADVERTISEMENT_WIDGET)) {
            System.out.println("Closing advertisement board interface...");
            Rs2Keyboard.keyPress(27); // ESC key
            sleepUntil(() -> !Rs2Widget.isWidgetVisible(ADVERTISEMENT_WIDGET), 2000);
        }

        // Check current world and hop to 330 if needed
        int currentWorld = Microbot.getClient().getWorld();
        if (currentWorld != WORLD_330) {
            System.out.println("Not on world 330 (current: " + currentWorld + "), attempting to hop back...");
            hopToWorld(WORLD_330);

            boolean hopSuccessful = sleepUntil(() -> Microbot.getClient().getWorld() == WORLD_330, 10000);
            if (hopSuccessful) {
                System.out.println("Successfully hopped back to world 330");
                sleep(2000, 3000); // Wait for world to stabilize
            } else {
                System.out.println("Failed to hop to world 330");
                Microbot.showMessage("Failed to hop to W330. Please manually hop to W330.");
            }
        } else {
            System.out.println("Already on world 330, but no houses available");
            System.out.println("Waiting 10 seconds for houses to appear...");
            sleep(8000, 12000);
        }

        // Reset everything to start fresh - don't clear blacklist as those houses might still be offline
        visitedOnce = false;
        currentHouse.reset();

        System.out.println("Proceeding with initial setup");
    }

    public void handleHaventVisitedMessage() {
        System.out.println("=== Detected 'haven't visited' message - performing full reset ===");

        // Close any open interfaces
        if (Rs2Widget.isWidgetVisible(ADVERTISEMENT_WIDGET)) {
            System.out.println("Closing advertisement board interface...");
            Rs2Keyboard.keyPress(27); // ESC key
            sleepUntil(() -> !Rs2Widget.isWidgetVisible(ADVERTISEMENT_WIDGET), 2000);
        }

        // Reset all state
        visitedOnce = false;
        blacklistNames.clear();
        currentHouse.reset();

        // Check if we're on world 330
        int currentWorld = Microbot.getClient().getWorld();
        if (currentWorld != WORLD_330) {
            System.out.println("Not on world 330 (current: " + currentWorld + "), attempting to hop...");
            hopToWorld(WORLD_330);

            boolean hopSuccessful = sleepUntil(() -> Microbot.getClient().getWorld() == WORLD_330, 10000);
            if (hopSuccessful) {
                System.out.println("Successfully hopped to world 330");
                sleep(2000, 3000);
            } else {
                System.out.println("Failed to hop to world 330");
                Microbot.showMessage("Failed to hop to W330. Please manually hop to W330.");
            }
        } else {
            System.out.println("Already on world 330");
        }

        System.out.println("Will check house advertisements on next cycle");
    }

    private void hopToWorld(int worldId) {
        net.runelite.api.World rsWorld = Microbot.getClient().createWorld();
        rsWorld.setId(worldId);
        Microbot.getClient().openWorldHopper();
        sleep(600, 1200);
        Microbot.getClient().hopToWorld(rsWorld);
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class GildedAltarScript extends Script {

    private final int HOUSE_PORTAL_OBJECT = 4525;
    private Widget toggleArrow;
    public Widget targetWidget;
    public String houseOwner;

    public WorldPoint portalCoords;
    public WorldPoint altarCoords;
    public Boolean usePortal;
    public boolean visitedOnce;
    List<String> blacklistNames = new ArrayList<>();


    public static GildedAltarPlayerState state = GildedAltarPlayerState.IDLE;

    private boolean inHouse() {
        return Rs2Npc.getNpc("Phials") == null;
    }

    private boolean hasUnNotedBones() {
        return Rs2Inventory.hasUnNotedItem("bones");
    }

    private boolean hasNotedBones() {
        return Rs2Inventory.hasNotedItem("bones");
    }

    private void calculateState() {
        boolean inHouse = inHouse();
        boolean hasUnNotedBones = hasUnNotedBones();

        // If we have unNoted bones:
        // If we're in the house, use bones on altar. Else, enter the portal
        // If we don't have unNoted bones:
        // If we're in the house, leave house. Else, talk to Phials
        if (hasUnNotedBones) {
            state = inHouse ? GildedAltarPlayerState.BONES_ON_ALTAR : GildedAltarPlayerState.ENTER_HOUSE;
        } else {
            state = inHouse ? GildedAltarPlayerState.LEAVE_HOUSE : GildedAltarPlayerState.UNNOTE_BONES;
        }
    }


    public boolean run(GildedAltarConfig config) {
        blacklistNames = new ArrayList<>();
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) {
                    return;
                }
                if (!Rs2Inventory.hasItem(995)) {
                    Microbot.showMessage("No gp found in your inventory");
                    shutdown();
                    return;
                }
                if (!hasNotedBones() && !hasUnNotedBones()) {
                    Microbot.showMessage("No bones found in your inventory");
                    shutdown();
                    return;
                }

                if (Microbot.isGainingExp) return;

                calculateState();

                switch (state) {
                    case LEAVE_HOUSE:
                        leaveHouse();
                        break;
                    case UNNOTE_BONES:
                        unnoteBones();
                        break;
                    case ENTER_HOUSE:
                        enterHouse();
                        break;
                    case BONES_ON_ALTAR:
                        bonesOnAltar();
                        break;
                }
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    public void leaveHouse() {
        System.out.println("Attempting to leave house...");

        // We should only rely on using the settings menu if the portal is several rooms away from the portal. Bringing up 3 different interfaces when we can see the portal on screen is unnecessary.
        if(usePortal) {
            TileObject portalObject = Rs2GameObject.findObjectById(HOUSE_PORTAL_OBJECT);
            if (portalObject == null) {
                System.out.println("Not in house, HOUSE_PORTAL_OBJECT not found.");
                return;
            }
            Rs2GameObject.interact(portalObject);
            Rs2Player.waitForWalking();
            return;
        }

        // Switch to Settings tab
        Rs2Tab.switchToSettingsTab();
        sleep(1200);


        //If the house options button is not visible, player is on Display or Sound settings, need to click Controls.
        String[] actions = Rs2Widget.getWidget(7602235).getActions(); // 116.59
        boolean isControlsInterfaceVisible = actions != null && actions.length == 0;
        if (!isControlsInterfaceVisible) {
            Rs2Widget.clickWidget(7602235);
            sleepUntil(() -> Rs2Widget.isWidgetVisible(7602207));
        }

        // Click House Options
        if (Rs2Widget.clickWidget(7602207)) {
            sleep(1200);
        } else {
            System.out.println("House Options button not found.");
            return;
        }

        // Click Leave House
        if (Rs2Widget.clickWidget(24248341)) {
            sleep(3000);
        } else {
            System.out.println("Leave House button not found.");
        }
    }

    public void unnoteBones() {
        if (Microbot.getClient().getWidget(14352385) == null) {
            if (!Rs2Inventory.isItemSelected()) {
                Rs2Inventory.use("bones");
            } else {
                Rs2Npc.interact("Phials", "Use");
                Rs2Player.waitForWalking();
            }
        } else if (Microbot.getClient().getWidget(14352385) != null) {
            Rs2Keyboard.keyPress('3');
            Rs2Inventory.waitForInventoryChanges(2000);
        }
    }

    private void enterHouse() {
        // If we've already visited a house this session, use 'Visit-Last' on advertisement board
        if (visitedOnce) {
            Rs2GameObject.interact(ObjectID.HOUSE_ADVERTISEMENT, "Visit-Last");
            sleep(2400, 3000);
            return;
        }

        boolean isAdvertisementWidgetOpen = Rs2Widget.isWidgetVisible(3407875);

        if (!isAdvertisementWidgetOpen) {
            Rs2GameObject.interact(ObjectID.HOUSE_ADVERTISEMENT, "View");
            sleep(1200, 1800);
        }

        Widget containerNames = Rs2Widget.getWidget(52, 9);
        Widget containerEnter = Rs2Widget.getWidget(52, 19);
        if (containerNames == null || containerNames.getChildren() == null) return;

        //Sort house advertisements by Gilded Altar availability
        toggleArrow = Rs2Widget.getWidget(3407877);
        if (toggleArrow.getSpriteId() == 1050) {
            Rs2Widget.clickWidget(3407877);
            sleep(600, 1200);
        }

        // Get all names on house board and find the one with the smallest Y value
        if (containerNames.getChildren() != null) {
            int smallestOriginalY = Integer.MAX_VALUE; // Track the smallest OriginalY

            Widget[] children = containerNames.getChildren();

            for (int i = 0; i < children.length; i++) {
                Widget child = children[i];
                if (child.getText() == null || child.getText().isEmpty()|| child.getText() == ""){
                    continue;
                }
                if (child.getText() != null) {
                    if (child.getOriginalY() < smallestOriginalY && !blacklistNames.contains(child.getText())) {
                        houseOwner = child.getText();
                        smallestOriginalY = child.getOriginalY();
                    }
                }
            }

            // Use playername at top of advertisement board as search criteria and find their Enter button
            Widget[] children2 = containerEnter.getChildren();
            for (int i = 0; i < children2.length; i++) {
                Widget child = children2[i];
                if (child == null || child.getOnOpListener() == null) {
                    continue;
                }
                Object[] listenerArray = child.getOnOpListener();
                boolean containsHouseOwner = Arrays.stream(listenerArray)
                        .filter(Objects::nonNull) // Ensure no null elements
                        .anyMatch(obj -> obj.toString().replace("\u00A0", " ").contains(houseOwner)); // Check if houseOwner is part of any listener object
                if (containsHouseOwner) {
                    targetWidget = child;
                    break;
                }
            }
            sleep(600, 1200);
            Rs2Widget.clickChildWidget(3407891, targetWidget.getIndex());
            visitedOnce = true;
            sleep(2400, 3000);
        }
    }

    public void bonesOnAltar() {
        if(portalCoords == null){
            portalCoords = Rs2Player.getWorldLocation();
        }

        if (Rs2Player.isAnimating())  {
            return;
        }


        TileObject altar = Rs2GameObject.getGameObject("Altar", true);
        if (altar != null) {
            Rs2Inventory.useUnNotedItemOnObject("bones", altar.getId());
        Rs2Player.waitForAnimation();
        }



        // Use bones on the altar if it's valid
        if(altarCoords == null){
            altarCoords = Rs2Player.getWorldLocation();
        }
        // If portal is more than 10 tiles from altar, use settings menu to leave. Else, just walk back to portal.
        if(usePortal == null){
            usePortal = altarCoords.distanceTo(portalCoords) <= 10;
        }
    }

    public void addNameToBlackList() {
        blacklistNames.add(houseOwner);
    }
}
