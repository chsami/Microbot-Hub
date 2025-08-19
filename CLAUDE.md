# Microbot Plugin Development Guide

# Gathering information about a skilling method or NPC/boss:

- Always use https://oldschool.runescape.wiki

So for example, if I’m needing information on enchanting bolts and the quantity and type of runes needed for each bolt type, I would visit: https://oldschool.runescape.wiki/w/Calculator:Magic/Enchant_bolts

Another example, if I needed information about Vorkath, I would visit: https://oldschool.runescape.wiki/w/Vorkath/Strategies

# Creating a new plugin or feature

When creating a new plugin or feature, start by asking me questions in a Q&A format starting broad idea and getting more detailed, one question at a time - so you ask the question and then I answer. You take that information in and formulate it. You continue to do this and when you’re ready to create the plugin or add the feature you tell me you’re ready and ask me first if I’m ready. When providing multiple choices for me to choose from, number them 1, 2, 3, etc. so I can just say the number(s) I want.

Never create an overlay unless I specifically say to. Just need a config, plugin, and script to start.

## Core Development Rules

- **TRY TO ACHIEVE THE INTENDED RESULT OR MECHANICS USING THE LEAST AMOUNT OF CODE POSSIBLE**
- **NEVER SUGGEST MODIFICATIONS TO OUR API (net/runelite/client/plugins/microbot/util), RATHER MAKE CHANGES TO OUR SCRIPT TO ADAPT TO THE API.**
- **NEVER PRINT "<phrase> claude.md standards" in comments. NEVER MENTION CLAUDE OR ANY OF ITS ASSOCIATED FILES in comments.**
- **NEVER USE @DEPRECATED/DEPRECATED CODE. FIND THE APPROPRIATE EQUIVALENT.**
- **NEVER BUILD OR TEST AFTER WRITING CODE. I WILL BUILD IT MYSELF.**
- **ALWAYS USE SENTENCE CASING IN CONFIG NAMES** (e.g., "Enable feature", "Max quantity")
- **ALWAYS ADD HUMAN-LIKE COMMENTS IN LOWERCASE** explaining what code does
- **NEVER ADD COMMENTS TO sl4j logging lines of code** (i.e., log.info(); // NEVER DO THIS
- **ALWAYS TRY AND ACHIEVE THE EXPECTED RESULT WITH THE FEWEST LINES POSSIBLE.**
- **LEVERAGE EXISTING PLUGINS AND FUNCTIONALITIES** (example: AutoHerbiboar extends Herbiboar plugin)
- **ALWAYS USE @SLF4J ANNOTATION FOR LOGGING** - never use Microbot.log or Microbot.showMessage
- **ALWAYS USE LOG.INFO FOR ALL LOGGING** - never use log.debug, log.warn unless specifically needed

### Code Format Requirements
When solving bugs or problems, only show before/after code blocks:

```
Before:
<old code>

After:
<new code> // <explanation of new line>
```

### Comment Style Requirements
**ALWAYS add human-like lowercase comments** explaining what the code is actually doing:

```java
Rs2Bank.withdrawX("Raw shark", 6); // withdraw 6 raw shark from the bank
sleepUntil(() -> Rs2Inventory.hasItem("Raw shark"), 3000); // wait until sharks appear in our inventory
if (!Rs2Bank.isOpen()) { // if the bank interface is closed
    Rs2Bank.openBank(); // open the bank interface
}
```

---

## Plugin Architecture

### Project Structure
```
bga/plugin-name/
├── PluginNameConfig.java      # @ConfigGroup configuration
├── PluginNamePlugin.java      # @PluginDescriptor main plugin
├── PluginNameScript.java      # Script extends Script class
├── PluginNameOverlay.java     # Optional OverlayPanel
├── PluginNameState.java       # State enum
└── enums/                     # Additional enums
```

### Naming Conventions
- **Plugin Descriptor**: `[bga] Plugin Name` (mandatory prefix)
- **Classes**: `AutoFishingPlugin`, `F2PBuilderPlugin`
- **Tags**: `tags = {"fishing", "skilling"}`
- **Safety**: `enabledByDefault = false`

### Plugin Template
```java
@PluginDescriptor(
    name = "[bga] Plugin Name",
    description = "Description here",
    tags = {"tag1", "tag2"},
    enabledByDefault = false
)
@Slf4j
public class PluginNamePlugin extends Plugin {
    @Inject private PluginNameConfig config;
    @Inject private OverlayManager overlayManager;
    @Inject private PluginNameOverlay overlay;
    @Inject private PluginNameScript script;

    @Provides
    PluginNameConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(PluginNameConfig.class); // give the config to dependency injection system
    }

    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) overlayManager.add(overlay); // add the overlay to the screen
        script.run(config); // start running the main script with our config
    }

    @Override
    protected void shutDown() {
        script.shutdown(); // tell the script to stop running
        if (overlayManager != null) overlayManager.remove(overlay); // remove the overlay from the screen
    }
}
```

### Script Template
```java
@Slf4j
public class PluginNameScript extends Script {
    public static String version = "1.0";
    private PluginNameState state = PluginNameState.INITIALIZING;
    private PluginNameConfig config;
    private long stateStartTime = System.currentTimeMillis(); // remember when we started this state for timeout checking

    public boolean run(PluginNameConfig config) {
        this.config = config; // save the config so we can use it later
        log.info("Starting plugin script version {}", version);

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) {
                    log.info("Super.run() returned false, stopping");
                    return;
                }
                if (!Microbot.isLoggedIn()) {
                    log.info("Not logged in, waiting");
                    return;
                }
                if (Rs2Player.isMoving() || Rs2Player.isAnimating()) {
                    log.info("Player is moving or animating, waiting");
                    return;
                }

                long startTime = System.currentTimeMillis(); // remember when this loop started

                // state timeout protection
                if (System.currentTimeMillis() - stateStartTime > 30000) {
                    log.info("State timeout after 30 seconds, resetting to INITIALIZING");
                    changeState(PluginNameState.INITIALIZING);
                    return;
                }

                switch (state) {
                    case INITIALIZING: handleInitializing(); break; // handle the setup phase
                    case WORKING: handleWorking(); break; // handle the main work phase
                }

                long endTime = System.currentTimeMillis(); // remember when this loop ended
                long totalTime = endTime - startTime; // calculate how long this loop took
                log.info("Total time for loop: {}ms", totalTime);
            } catch (Exception ex) {
                log.error("Error in main script loop: {}", ex.getMessage(), ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    // helper method to change state with timeout reset
    private void changeState(PluginNameState newState) {
        if (newState != state) {
            log.info("State change: {} -> {}", state, newState);
            state = newState;
            stateStartTime = System.currentTimeMillis();
        }
    }

    @Override
    public void shutdown() {
        log.info("Shutting down plugin script");
        super.shutdown(); // clean up the script properly
        log.info("Plugin script shutdown complete");
    }
}
```

---

## Universal Script States

### 1. **INITIALIZING**
- Validates plugin configuration settings
- Checks required items/equipment are available
- Verifies our location and game state
- Transitions to appropriate next state

```java
private void handleInitializing() {
    log.info("State: INITIALIZING");

    if (!validateConfig()) { // if our config settings are invalid
        log.info("Invalid configuration, cannot continue");
        return;
    }

    if (!hasRequiredItems()) { // if we don't have the items we need
        log.info("Missing required items, switching to BANKING");
        changeState(PluginState.BANKING); // switch to banking to get the items
        return;
    }

    log.info("Initialization complete, determining next state");
    changeState(determineNextState()); // figure out what we should do next
}
```

### 2. **BANKING**
- Handles all bank interactions (deposit/withdraw)
- Universal state for inventory management
- Validates bank access and item availability
- Transitions to activity or walking state

```java
private void handleBanking() {
    log.info("State: BANKING");

    if (!Rs2Bank.isNearBank(10)) { // if we are too far from any bank
        log.info("Not near bank, switching to WALKING");
        changeState(PluginState.WALKING); // switch to walking to get to a bank
        return;
    }

    if (!Rs2Bank.isOpen()) { // if the bank interface isn't open yet
        log.info("Bank not open, attempting to open");
        Rs2Bank.openBank(); // click to open the bank
        boolean opened = sleepUntil(() -> Rs2Bank.isOpen(), 3000); // wait until the bank opens
        if (!opened) {
            log.info("Failed to open bank within timeout");
        }
        return;
    }

    log.info("Bank is open, performing banking operations");
    performBankingOperations(); // do whatever banking we need to do
}
```

### 3. **WALKING**
- Handles all movement between locations
- Universal navigation state
- Can be parameterized for different destinations
- Validates arrival at target location

```java
private void handleWalking() {
    log.info("State: WALKING to {}", targetLocation);

    if (hasArrivedAtDestination()) { // if we have reached where we wanted to go
        log.info("Arrived at destination");
        changeState(determineNextStateAfterWalking()); // figure out what to do now that we're here
        return;
    }

    if (!Rs2Player.isMoving()) { // if we have stopped moving
        log.info("Starting walk to {}", targetLocation);
        Rs2Walker.walkTo(targetLocation); // start walking to where we want to go
    }
}
```

### 4. **ERROR_RECOVERY**
- Handles unexpected game states
- Cleans up interfaces (dialogues, unexpected windows)
- Implements timeout protection
- Resets to safe state when issues detected

```java
private void handleErrorRecovery() {
    log.info("State: ERROR_RECOVERY");

    if (Rs2Dialogue.isInDialogue()) { // if there's an unexpected dialogue open
        log.info("Closing unexpected dialogue");
        Rs2Dialogue.clickContinue(); // click to close the dialogue
        return;
    }

    if (System.currentTimeMillis() - stateStartTime > 60000) { // if we've been stuck for more than 60 seconds
        log.info("State timeout in ERROR_RECOVERY, resetting to INITIALIZING");
        changeState(PluginState.INITIALIZING); // go back to the beginning
        return;
    }

    log.info("Error recovery complete, resetting to INITIALIZING");
    changeState(PluginState.INITIALIZING); // go back to the beginning
}
```

---

## Validation Framework

### Critical Loop Checks (Always Required)
```java
// mandatory checks in every script loop
if (!super.run()) { // if the parent script tells us to stop
    log.info("Super.run() returned false, stopping");
    return;
}
if (!Microbot.isLoggedIn()) { // if we aren't logged into the game
    log.info("Not logged in, waiting");
    return;
}

// movement/animation checks
if (Rs2Player.isMoving() || Rs2Player.isAnimating()) { // if we are currently moving or doing an animation
    log.info("Player is moving or animating, waiting");
    return;
}

// extended timeout for longer animations (cooking, smithing)
if (Rs2Player.isMoving() || Rs2Player.isAnimating(1000)) { // if we are doing a slow animation like cooking
    log.info("Player is in extended animation, waiting");
    return;
}

// for very slow activities
if (Rs2Player.isAnimating(3000)) { // if we are doing a very slow animation
    log.info("Player is in very slow animation, waiting");
    return;
}
```

### Pre-Condition Validation Pattern
**NEVER assume any game state exists** - always validate first:

```java
// location validation
if (!Rs2Bank.isNearBank(10)) { // if we are more than 10 tiles away from a bank
    log.info("Not near bank, distance > 10 tiles");
    return false;
}

// interface validation  
if (!Rs2Bank.isOpen()) { // if the bank interface is not currently open
    log.info("Bank interface not open");
    return false;
}

// item validation
if (!Rs2Bank.hasItem("Raw shark", 6)) { // if the bank doesn't have at least 6 raw sharks
    log.info("Bank missing required items: need 6 raw sharks");
    return false;
}

// space validation
if (Rs2Inventory.getEmptySlots() < 6) { // if we have less than 6 empty inventory slots
    log.info("Insufficient inventory space: {} empty slots", Rs2Inventory.getEmptySlots());
    return false;
}
```

### Post-Condition Validation Pattern
**ALWAYS verify actions succeeded** using conditional sleeps:

```java
// capture initial state
int initialCount = Rs2Inventory.count("Raw shark"); // count how many sharks we have before withdrawing
log.info("Current shark count: {}", initialCount);

// perform action
log.info("Attempting to withdraw 6 raw sharks");
Rs2Bank.withdrawX("Raw shark", 6); // tell the bank to withdraw 6 sharks

// validate result with timeout
boolean success = sleepUntil(() -> 
    Rs2Inventory.count("Raw shark") >= initialCount + 6, 5000); // wait until we have 6 more sharks than before

if (!success) { // if the sharks never appeared in our inventory
    log.info("Failed to withdraw items within timeout");
    return false;
}
log.info("Successfully withdrew 6 raw sharks, new count: {}", Rs2Inventory.count("Raw shark"));
```

### Sleep Method Hierarchy
**ALWAYS use conditional sleeping - NEVER use hardcoded sleeps**:

```java
// 1. PREFERRED - conditional waits with timeout (ALWAYS USE THIS)
boolean success = sleepUntil(() -> condition, timeout); // wait until something actually happens
if (!success) { // if we timed out waiting
    log.info("Timeout waiting for condition");
    return false;
}

// 2. ACCEPTABLE - sleepUntilTrue for boolean returns with custom polling
boolean result = sleepUntilTrue(() -> condition, pollInterval, timeout); // wait with custom checking frequency

// 3. FORBIDDEN - ALL forms of hardcoded sleeps
sleep(Rs2Random.between(1000, 2000)); // NEVER DO THIS - still hardcoded!
sleep(1000); // NEVER DO THIS
sleep(600); // NEVER DO THIS

// CORRECT APPROACH - Always wait for actual game state changes:
// Instead of: sleep(600) after withdrawing
// Use: sleepUntil(() -> Rs2Inventory.hasItem("item"), 3000)

// Instead of: sleep(1000) after equipping
// Use: sleepUntil(() -> Rs2Equipment.isWearing("item"), 3000)

// Instead of: sleep(800) after clicking
// Use: sleepUntil(() -> !Rs2Player.isAnimating(), 5000)
```

---

## State Management

### State-Based Architecture
```java
private void handleBanking() {
    System.out.println("State: BANKING");

    if (!Rs2Bank.isNearBank(10)) { // if we are too far from any bank
        System.out.println("Not near bank, transitioning to WALKING");
        state = PluginState.WALKING; // switch to walking mode to get to a bank
        stateStartTime = System.currentTimeMillis(); // reset our timeout timer
        return;
    }

    if (!Rs2Bank.isOpen()) { // if the bank interface isn't open
        System.out.println("Opening bank");
        Rs2Bank.openBank(); // click to open the bank
        sleepUntil(() -> Rs2Bank.isOpen(), 3000); // wait until the bank interface appears
        return;
    }

    if (Rs2Inventory.isEmpty()) { // if our inventory has no items
        System.out.println("Inventory empty, transitioning to WITHDRAWING");
        state = PluginState.WITHDRAWING; // switch to withdrawing items mode
        stateStartTime = System.currentTimeMillis(); // reset our timeout timer
    } else {
        Rs2Bank.depositAll(); // put all our items into the bank
        boolean deposited = sleepUntil(() -> Rs2Inventory.isEmpty(), 3000); // wait until our inventory is empty
        if (deposited) { // if our inventory is now empty
            System.out.println("Items deposited successfully");
            state = PluginState.WITHDRAWING; // switch to withdrawing items mode
            stateStartTime = System.currentTimeMillis(); // reset our timeout timer
        } else {
            System.out.println("Failed to deposit items");
        }
    }
}
```

### State Timeout Protection
**Implement state-level timeouts** to prevent infinite loops:

```java
private long stateStartTime = System.currentTimeMillis(); // remember when we started the current state

// in each state handler - timeout check
if (System.currentTimeMillis() - stateStartTime > 30000) { // if we've been in this state for more than 30 seconds
    System.out.println("State timeout - resetting to INITIALIZING");
    state = PluginState.INITIALIZING; // go back to the beginning
    stateStartTime = System.currentTimeMillis(); // reset our timeout timer
    return;
}

// when changing states
private void changeState(PluginState newState) {
    if (newState != state) { // if we are actually changing to a different state
        state = newState; // update our current state
        stateStartTime = System.currentTimeMillis(); // reset our timeout timer for the new state
    }
}
```

### Error Recovery System
```java
private boolean handleErrors() {
    // dialogue interruption
    if (Rs2Dialogue.isInDialogue() && state != PluginState.TALKING) { // if there's a dialogue open when we don't expect one
        System.out.println("Closing unexpected dialogue");
        Rs2Dialogue.clickContinue(); // close the dialogue
        return true;
    }

    // interface cleanup
    if (Rs2Bank.isOpen() && state != PluginState.BANKING) { // if the bank is open when we're not supposed to be banking
        System.out.println("Closing unexpected bank interface");
        Rs2Bank.closeBank(); // close the bank interface
        return true;
    }

    // combat interruption
    if (Rs2Combat.inCombat() && state != PluginState.COMBAT) { // if we are in combat when we don't expect to be
        System.out.println("Unexpected combat detected");
        return true;
    }

    return false; // no errors were found
}
```

---

## Loop Performance & Monitoring

### Performance Tracking
**Track loop execution time** for debugging:

```java
// at start of main loop
long startTime = System.currentTimeMillis(); // remember when this loop iteration started

// main loop logic here

// at end of main loop
long endTime = System.currentTimeMillis(); // remember when this loop iteration ended
long totalTime = endTime - startTime; // calculate how long this loop took to complete
System.out.println("Total time for loop " + totalTime);
```

### Break Handler Integration
**Check for active breaks** in main loop:

```java
// in main scheduled loop - before any logic
if (BreakHandlerScript.isBreakActive()) return; // if the break handler says we should be taking a break
```

---

## Rs2 Utilities Reference

### Banking Operations
```java
// location checks
Rs2Bank.isNearBank(int distance) // check if we are within a certain distance of any bank
Rs2Bank.isNearBank(BankLocation location, int distance) // check if we are within distance of a specific bank

// state validation
Rs2Bank.isOpen() // check if the bank interface is currently open
Rs2Bank.hasItem(String itemName, int quantity) // check if the bank contains at least this many of an item

// actions with validation
Rs2Bank.openBank() // click to open the nearest bank interface
Rs2Bank.withdrawX(String itemName, int quantity) // withdraw a specific number of items from the bank
Rs2Bank.depositAll() // put all items from our inventory into the bank
```

### Inventory Management  
```java
// state checks
Rs2Inventory.hasItem(String itemName) // check if our inventory contains at least one of this item
Rs2Inventory.count(String itemName) // count how many of this item we have in our inventory
Rs2Inventory.getEmptySlots() // check how many empty slots we have left in our inventory
Rs2Inventory.isEmpty() // check if our inventory has no items at all

// item interactions
Rs2Inventory.interact(String itemName, String action) // right-click on an item and select an action
Rs2Inventory.wield(String itemName) // equip an item from our inventory
```

### Player Operations
```java
// state checks
Rs2Player.isMoving() // check if we are currently walking or running
Rs2Player.isAnimating() // check if we are currently doing any animation
Rs2Player.isAnimating(int timeout) // check if we are doing an animation with a custom timeout

// xp monitoring
Rs2Player.waitForXpDrop(Skill skill) // wait until we gain experience in a specific skill
Rs2Player.waitForXpDrop(Skill skill, int timeout) // wait for xp gain with a timeout
```

### Object Interactions
```java
// finding objects
Rs2GameObject.findObjectById(int id) // find a game object by its numerical id
Rs2GameObject.findNearestObject(String name) // find the closest object with a specific name

// validation before interaction
if (object == null) { // if we couldn't find the object we were looking for
    System.out.println("Object not found");
    return false;
}
if (!object.interact("Action")) { // if we failed to interact with the object
    System.out.println("Failed to interact with object");
    return false;
}
```

### Ground Items
```java
// validation before looting
Rs2GroundItem item = Rs2GroundItem.findGroundItem("Item name", 5); // look for an item on the ground within 5 tiles
if (item == null) { // if we couldn't find the item on the ground
    System.out.println("Ground item not found");
    return false;
}
Rs2GroundItem.loot("Item name", 5); // pick up the item from the ground
```

---

## Configuration Best Practices

### Config Structure
```java
@ConfigGroup("pluginname")
public interface PluginNameConfig extends Config {
    @ConfigSection(
        name = "General settings", // use sentence casing - first word capitalized, rest lowercase
        description = "Basic plugin configuration"
    )
    String generalSection = "general";

    @ConfigItem(
        keyName = "enableFeature",
        name = "Enable feature", // sentence casing - only first word capitalized
        description = "Enable this feature",
        section = generalSection
    )
    default boolean enableFeature() { return true; }

    @Range(min = 1, max = 100)
    @ConfigItem(
        keyName = "quantity",
        name = "Item quantity", // sentence casing
        description = "How many items to process",
        section = generalSection
    )
    default int quantity() { return 10; }
}
```

### Configuration Validation
**Validate config at startup**:

```java
private boolean validateConfig() {
    if (config.itemName().isEmpty()) { // if the user didn't enter an item name
        log.info("Config validation failed: item name not configured");
        return false;
    }

    if (config.quantity() < 1) { // if the user entered an invalid quantity
        log.info("Config validation failed: invalid quantity {}", config.quantity());
        return false;
    }

    log.info("Config validation successful");
    return true; // all config settings are valid
}
```

---

## Logging Requirements

### SLF4J Logging Standards
**ALWAYS use @Slf4j annotation and log.info() for all logging**:

```java
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PluginNameScript extends Script {
    // NEVER use:
    // - Microbot.log()
    // - Microbot.showMessage()
    // - System.out.println()

    // ALWAYS use:
    // - log.info() for general information
    // - log.error() for exceptions only
    // - log.info() with {} placeholders for variables
}
```

### State Evaluation Pattern
**Log state transitions and evaluations comprehensively**:

```java
log.info("=== State Evaluation ===");
log.info("Current state: {}", state);
log.info("In essence mine: {}", isInEssenceMine);
log.info("Inventory full: {}", needsToBank);
log.info("Distance to target: {} tiles", distance);
log.info("Has required items: {}", hasRequiredItems);

// determine next state with logging
if (needsToBank) {
    log.info("Need to bank, switching to BANKING");
    changeState(State.BANKING);
} else if (!hasRequiredItems) {
    log.info("Missing required items, switching to GETTING_GEAR");
    changeState(State.GETTING_GEAR);
}
```

### Comprehensive Debug Output
**Always log condition outcomes** for troubleshooting:

```java
log.info("=== Banking State Check ===");
log.info("Near bank: {}", Rs2Bank.isNearBank(10)); // log whether we are close to a bank
log.info("Bank open: {}", Rs2Bank.isOpen()); // log whether the bank interface is open
log.info("Has items: {}", Rs2Bank.hasItem("Raw shark", 6)); // log whether the bank has enough items
log.info("Empty slots: {}", Rs2Inventory.getEmptySlots()); // log how much inventory space we have
log.info("=== End Check ===");
```

### Enhanced Validation Patterns
**Always validate complex conditions**:

```java
private boolean canStartActivity() {
    log.info("=== Activity Validation ===");
    log.info("Has required items: {}", hasRequiredItems()); // check if we have the items we need
    log.info("At correct location: {}", isAtLocation()); // check if we are in the right place
    log.info("Equipment ready: {}", hasEquipment()); // check if we have the right gear equipped
    log.info("Not in combat: {}", !Rs2Combat.inCombat()); // check if we are not fighting anything

    boolean canStart = hasRequiredItems() && isAtLocation() &&
                      hasEquipment() && !Rs2Combat.inCombat();
    log.info("Can start activity: {}", canStart);
    return canStart;
}
```

### Action Logging Pattern
**Log before and after every significant action**:

```java
// before action
log.info("Attempting to withdraw {} {}", quantity, itemName);
Rs2Bank.withdrawX(itemName, quantity);

// validate with logging
boolean success = sleepUntil(() -> Rs2Inventory.hasItem(itemName), 3000);
if (success) {
    log.info("Successfully withdrew {} {}", quantity, itemName);
} else {
    log.info("Failed to withdraw {} {} within timeout", quantity, itemName);
}
```

### Status Updates
```java
// use Microbot.status to show the user what we are doing
Microbot.status = "Banking items..."; // tell the user we are handling banking
Microbot.status = "Walking to fishing spot..."; // tell the user we are traveling
Microbot.status = "Fishing..."; // tell the user we are doing the main activity
```

---

## Summary: Critical Rules

### **LOGGING RULES - MOST IMPORTANT**
1. **ALWAYS use @Slf4j annotation** - NEVER use Microbot.log, Microbot.showMessage, or System.out.println
2. **ALWAYS use log.info() for all logging** - avoid log.debug, log.warn unless specifically needed
3. **ALWAYS use {} placeholders for variables** in log statements (e.g., log.info("Value: {}", value))
4. **NEVER add comments to logging lines** - the log message should be self-explanatory

### **VALIDATION RULES**
5. **NEVER assume game state** - validate all preconditions with logging
6. **ALWAYS verify postconditions** after actions with sleepUntil and logging
7. **ALWAYS add comprehensive state evaluation logging** before state changes
8. **ALWAYS log before and after significant actions** (withdraw, equip, interact)

### **CODE STRUCTURE RULES**
9. **ALWAYS use state-based architecture** with proper changeState() helper method
10. **ALWAYS implement state timeouts** to prevent infinite loops (30-60 seconds)
11. **ALWAYS track loop performance** for debugging (log start/end times)
12. **ALWAYS check mandatory loop conditions** (super.run, login, movement) with logging

### **SLEEP AND TIMING RULES**
13. **NEVER use hardcoded sleeps** - use conditional waits with timeouts
14. **ALWAYS use Rs2Random.between(min, max)** for any necessary delays
15. **ALWAYS use sleepUntil patterns** instead of hardcoded delays

### **OTHER IMPORTANT RULES**
16. **ALWAYS validate configuration** at startup with logging
17. **ALWAYS use extended animation checks** for slow activities
18. **ALWAYS check for break handler** in main loop
19. **ALWAYS use sentence casing in config names** (e.g., "Enable feature", "Max quantity")
20. **ALWAYS add human-like lowercase comments** explaining what the code is actually doing
21. **ALWAYS leverage existing RuneLite plugins** instead of reimplementing