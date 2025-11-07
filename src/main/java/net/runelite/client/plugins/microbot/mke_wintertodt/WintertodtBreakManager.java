package net.runelite.client.plugins.microbot.mke_wintertodt;

import lombok.Getter;
import lombok.Setter;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.mke_wintertodt.enums.State;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.plugins.microbot.util.security.LoginManager;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.accountselector.AutoLoginPlugin;
import net.runelite.client.ui.ClientUI;

import static net.runelite.client.plugins.microbot.util.Global.sleep;

import java.awt.Point;
import java.time.Duration;
import java.util.Random;

/**
 * Custom break management system for Wintertodt script.
 * Handles both short AFK breaks (mouse offscreen) and longer logout breaks.
 */
public class WintertodtBreakManager {
    
    @Getter
    private static boolean breakActive = false;
    
    @Getter
    private static boolean afkBreakActive = false;
    
    @Getter
    private static boolean logoutBreakActive = false;
    
    private static int breakTimeRemaining = 0; // in seconds
    private static int nextBreakIn = 0; // in seconds
    
    @Getter
    @Setter
    private static boolean lockState = false;
    
    private final MKE_WintertodtConfig config;
    private final Random random = new Random();
    
    // Break timing
    private long nextBreakCheck = 0;
    
    // Title management
    private String originalTitle = "";
    
    @Getter
    private long waitingForSafeSpotSince = 0; // Track when we started waiting for safe spot
    
    @Getter
    private boolean isWalkingToSafeSpot = false;
    
    // AFK break data
    private Point originalMousePosition = null;
    
    // Safe locations for breaks
    public static final WorldPoint BANK_LOCATION = new WorldPoint(1640, 3944, 0);
    public static final WorldPoint BOSS_ROOM = new WorldPoint(1630, 3982, 0);
    public static final int SAFE_SPOT_RADIUS = 3; // Radius around boss room for safe spot
    private static final long MAX_WAIT_FOR_SAFE_SPOT_MS = 600000; // 10 minutes
    
    public enum BreakType {
        AFK_SHORT("AFK Break", "Moving mouse offscreen for 1-6 minutes"),
        LOGOUT_LONG("Logout Break", "Logging out for 5-40 minutes");
        
        private final String name;
        private final String description;
        
        BreakType(String name, String description) {
            this.name = name;
            this.description = description;
        }
        
        public String getName() { return name; }
        public String getDescription() { return description; }
    }
    
    public WintertodtBreakManager(MKE_WintertodtConfig config) {
        this.config = config;
        // Store original window title
        try {
            originalTitle = ClientUI.getFrame().getTitle();
        } catch (Exception e) {
            originalTitle = "RuneLite";
        }
        initializeBreakTimer();
    }
    
    /**
     * Initialize the break timer with random interval
     */
    private void initializeBreakTimer() {
        if (!config.enableCustomBreaks()) {
            return;
        }
        
        // Schedule first break randomly between min and max interval
        int minInterval = config.minBreakInterval();
        int maxInterval = config.maxBreakInterval();
        nextBreakIn = Rs2Random.between(minInterval * 60, maxInterval * 60);
        
        Microbot.log(String.format("Next break scheduled in %d minutes", nextBreakIn / 60));
    }
    
    /**
     * Main update method - call this from the script loop
     */
    public boolean update() {
        try {
            if (!config.enableCustomBreaks()) {
                return false;
            }
            
            // ----------------------------------------------------------------
            // Prevent breaks while the script is in the middle of the reward
            // cart collection flow.  Interrupting that sequence would leave
            // the player stranded outside of the arena with an open bank or
            // reward-cart interface.  We therefore just pause the break
            // countdown until the main script returns to normal operation.
            // ----------------------------------------------------------------
            if (MKE_WintertodtScript.isLootingRewards ||
                isInRewardCollectionState(MKE_WintertodtScript.state)) {
                return false;    // do nothing – countdown remains frozen
            }
            
            long currentTime = System.currentTimeMillis();
            
            // Check if we should evaluate for a break
            if (currentTime >= nextBreakCheck) {
                nextBreakCheck = currentTime + 1000; // Check every second
                
                // Countdown to next break
                if (nextBreakIn > 0 && !breakActive) {
                    nextBreakIn--;
                }
                
                // Countdown active break
                if (breakTimeRemaining > 0 && breakActive) {
                    breakTimeRemaining--;
                    
                    // Update window title with countdown
                    updateTitle();
                    
                    // Log countdown for logout breaks (every 30 seconds to avoid spam)
                    if (logoutBreakActive && breakTimeRemaining % 30 == 0) {
                        Microbot.log("Logout break countdown: " + (breakTimeRemaining / 60) + " minutes remaining");
                    }
                }
                
                // Time to start a break
                if (nextBreakIn <= 0 && !breakActive && !lockState) {
                    if (isInSafeLocationForBreak()) {
                        // Reset safe spot waiting timer since we found a safe spot
                        waitingForSafeSpotSince = 0;
                        isWalkingToSafeSpot = false;
                        startRandomBreak();
                    } else {
                        // Start tracking how long we've been waiting for safe spot
                        if (waitingForSafeSpotSince == 0) {
                            waitingForSafeSpotSince = currentTime;
                            Microbot.log("Started waiting for safe location for break");
                        }
                        
                        // Check if we've been waiting too long (10 minutes) or if less than 3 minutes left and state is waiting
                        long waitingTime = currentTime - waitingForSafeSpotSince;
                        if (waitingTime >= MAX_WAIT_FOR_SAFE_SPOT_MS || (waitingTime >= MAX_WAIT_FOR_SAFE_SPOT_MS - 180000 && MKE_WintertodtScript.state == State.WAITING)) {
                            if (!isWalkingToSafeSpot) {
                                Microbot.log("Waiting for safe spot timed out, requesting walk to boss room safe area");
                                isWalkingToSafeSpot = true;
                                return true; // Signal to walk
                            } else if (isInSafeLocationForBreak()) {
                                // We've arrived at the safe spot
                                Microbot.log("Arrived at safe spot, starting break");
                                waitingForSafeSpotSince = 0;
                                isWalkingToSafeSpot = false;
                                startRandomBreak();
                            }
                        } else {
                            // Delay break check by 1 second if not in safe location and haven't waited too long
                            nextBreakIn = 1;
                            long remainingWait = (MAX_WAIT_FOR_SAFE_SPOT_MS - waitingTime) / 1000;
                            if (remainingWait % 30 == 0) { // Log every 30 seconds
                                Microbot.log("Delaying break - not in safe location (will force walk in " + remainingWait / 60 + " minutes)");
                            }
                        }
                    }
                }
                
                // Time to end a break
                if (breakTimeRemaining <= 0 && breakActive) {
                    if (logoutBreakActive) {
                        Microbot.log("Logout break timer reached zero - ending break now");
                    }
                    endCurrentBreak();
                }
            }
            
        } catch (Exception e) {
            Microbot.log("Error in WintertodtBreakManager: " + e.getMessage());
            e.printStackTrace();
            // Emergency cleanup
            emergencyCleanup();
        }
        return false;
    }
    
    /**
     * Start a random break (either AFK or logout)
     */
    private void startRandomBreak() {
        // Determine break type based on config percentages
        boolean shouldLogout = random.nextInt(100) < config.logoutBreakChance();
        
        if (shouldLogout) {
            startLogoutBreak();
        } else {
            startAfkBreak();
        }
    }
    
    /**
     * Start an AFK break (mouse offscreen)
     */
    private void startAfkBreak() {
        int duration = Rs2Random.between(config.afkBreakMinDuration(), config.afkBreakMaxDuration());
        breakTimeRemaining = duration * 60; // convert to seconds
        
        afkBreakActive = true;
        breakActive = true;
        
        // Store original mouse position
        originalMousePosition = Microbot.getMouse().getMousePosition();
        
        // Move mouse offscreen
        Microbot.naturalMouse.moveOffScreen(90);
        
        // Update window title to show break status
        updateTitle();
        
        Microbot.log(String.format("Started AFK break for %d minutes", duration));
    }
    
    /**
     * Start a logout break
     */
    private void startLogoutBreak() {
        // Check if AutoLoginPlugin is currently enabled and disable it
        disableAutoLoginPlugin();
        
        int duration = Rs2Random.between(config.logoutBreakMinDuration(), config.logoutBreakMaxDuration());
        breakTimeRemaining = duration * 60; // convert to seconds
        
        logoutBreakActive = true;
        breakActive = true;
        
        // Attempt logout with retries
        boolean logoutSuccessful = attemptLogoutWithRetries(3);
        if (!logoutSuccessful) {
            Microbot.log("Failed to logout after 3 attempts - continuing with break anyway");
        }
        
        // Update window title to show break status
        updateTitle();
        
        Microbot.log(String.format("Started logout break for %d minutes", duration));
    }
    
    /**
     * Attempts to logout with retry logic
     */
    private boolean attemptLogoutWithRetries(int maxAttempts) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                Microbot.log("Logout attempt " + attempt + "/" + maxAttempts);
                
                // Attempt logout
                Rs2Player.logout();
                
                // Wait a moment and check if logout was successful
                sleep(2000);
                
                if (!Microbot.isLoggedIn()) {
                    Microbot.log("Logout successful on attempt " + attempt);
                    return true;
                }
                
                Microbot.log("Logout attempt " + attempt + " failed - player still logged in");
                
                // Wait a bit before retrying (except on last attempt)
                if (attempt < maxAttempts) {
                    sleep(3000);
                }
                
            } catch (Exception e) {
                Microbot.log("Error during logout attempt " + attempt + ": " + e.getMessage());
                if (attempt < maxAttempts) {
                    sleep(3000);
                }
            }
        }
        
        return false; // All attempts failed
    }
    
    /**
     * End the current break
     */
    private void endCurrentBreak() {
        if (afkBreakActive) {
            endAfkBreak();
        } else if (logoutBreakActive) {
            endLogoutBreak();
        }
        
        // Schedule next break
        scheduleNextBreak();
    }
    
    /**
     * End AFK break
     */
    private void endAfkBreak() {
        afkBreakActive = false;
        breakActive = false;
        
        // Restore mouse position with some randomization
        if (originalMousePosition != null) {
            int deltaX = random.nextInt(100) - 50;
            int deltaY = random.nextInt(100) - 50;
            Microbot.getMouse().move(
                originalMousePosition.x + deltaX, 
                originalMousePosition.y + deltaY
            );
            originalMousePosition = null;
        }
        
        // Restore window title
        updateTitle();
        
        // Reset action plan since game state may have changed during break
        MKE_WintertodtScript.resetActionPlanning();
        
        Microbot.log("AFK break ended - action plan reset, resuming activities");
    }
    
    /**
     * End logout break
     */
    private void endLogoutBreak() {
        logoutBreakActive = false;
        breakActive = false;
        
        // Restore window title
        updateTitle();
        
        // Reset action plan since inventory was cleared during logout
        MKE_WintertodtScript.resetActionPlanning();
        
        Microbot.log("Logout break ended - action plan reset, attempting to log back in");
        
        // Attempt to log back in
        attemptLogin();
    }
    
    /**
     * Attempts to log the player back in after a logout break
     */
    private void attemptLogin() {
        try {
            // Check if player is already logged in
            if (Microbot.isLoggedIn()) {
                Microbot.log("Player already logged in");
                return;
            }
            
            Microbot.log("Attempting to log back in after logout break...");
            
            // Try AutoLoginPlugin first with retries
            boolean autoLoginSuccessful = attemptAutoLoginWithRetries(3);
            if (autoLoginSuccessful) {
                onSuccessfulLogin();
                return;
            }
            
            // Fallback to manual login with retries
            boolean manualLoginSuccessful = attemptManualLoginWithRetries(3);
            if (manualLoginSuccessful) {
                onSuccessfulLogin();
                return;
            }
            
            // All login attempts failed
            Microbot.log("All login attempts failed - player will need to log in manually to resume script");
            
        } catch (Exception e) {
            Microbot.log("Error during login attempt: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Attempts to login using AutoLoginPlugin with retry logic
     */
    private boolean attemptAutoLoginWithRetries(int maxAttempts) {
        if (!isAutoLoginPluginAvailable()) {
            Microbot.log("AutoLoginPlugin not available - skipping auto login attempts");
            return false;
        }
        
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                Microbot.log("AutoLogin attempt " + attempt + "/" + maxAttempts);
                
                if (Microbot.isLoggedIn()) {
                    Microbot.log("Player already logged in during auto login attempt " + attempt);
                    sleep(20000);
                    return true;
                }
                
                // Enable AutoLoginPlugin
                enableAutoLoginPlugin();
                
                // Wait for login to complete (up to 60 seconds)
                long loginStart = System.currentTimeMillis();
                while (!Microbot.isLoggedIn() && System.currentTimeMillis() - loginStart < 60000) {
                    sleep(1000);
                }
                
                if (Microbot.isLoggedIn()) {
                    Microbot.log("Successfully logged in using AutoLoginPlugin on attempt " + attempt);
                    sleep(20000);
                    return true;
                }
                
                Microbot.log("AutoLogin attempt " + attempt + " timed out");
                
                // Wait before retry (except on last attempt)
                if (attempt < maxAttempts) {
                    sleep(5000);
                }
                
            } catch (Exception e) {
                Microbot.log("Error during AutoLogin attempt " + attempt + ": " + e.getMessage());
                if (attempt < maxAttempts) {
                    sleep(5000);
                }
            }
        }
        
        return false; // All auto login attempts failed
    }
    
    /**
     * Attempts manual login with retry logic
     */
    private boolean attemptManualLoginWithRetries(int maxAttempts) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                Microbot.log("Manual login attempt " + attempt + "/" + maxAttempts);
                
                if (Microbot.isLoggedIn()) {
                    Microbot.log("Player already logged in during manual login attempt " + attempt);
                    sleep(20000);
                    return true;
                }
                
                // Check if we have an active profile configured
                if (LoginManager.getActiveProfile() == null) {
                    Microbot.log("No active profile configured for automatic login");
                    return false;
                }
                
                // Use one of the Wintertodt worlds (307, 309, 311, 389)
                int[] wintertodtWorlds = {307, 309, 311, 389};
                int world = wintertodtWorlds[Rs2Random.between(0, wintertodtWorlds.length - 1)];
                
                Microbot.log("Attempting manual login to world " + world + " (attempt " + attempt + ")");
                new Login(world);
                
                // Wait for login to complete (up to 30 seconds)
                long loginStart = System.currentTimeMillis();
                while (!Microbot.isLoggedIn() && !Rs2Widget.isWidgetVisible(378,0) && System.currentTimeMillis() - loginStart < 30000) {
                    sleep(1000);
                }
                
                if (Microbot.isLoggedIn()) {
                    Microbot.log("Successfully logged in using manual login on attempt " + attempt);
                    sleep(20000);
                    return true;
                }
                
                Microbot.log("Manual login attempt " + attempt + " timed out");
                
                // Wait before retry (except on last attempt)
                if (attempt < maxAttempts) {
                    sleep(5000);
                }
                
            } catch (Exception e) {
                Microbot.log("Error during manual login attempt " + attempt + ": " + e.getMessage());
                if (attempt < maxAttempts) {
                    sleep(5000);
                }
            }
        }
        
        return false; // All manual login attempts failed
    }
    
    /**
     * Checks if the AutoLoginPlugin is available and can be used
     */
    private boolean isAutoLoginPluginAvailable() {
        try {
            return Microbot.getPlugin(AutoLoginPlugin.class.getName()) != null;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Enables the AutoLoginPlugin if available
     */
    private void enableAutoLoginPlugin() {
        try {
            AutoLoginPlugin autoLoginPlugin = (AutoLoginPlugin) Microbot.getPlugin(AutoLoginPlugin.class.getName());
            if (autoLoginPlugin != null && !Microbot.isPluginEnabled(autoLoginPlugin.getClass())) {
                Microbot.getClientThread().runOnSeperateThread(() -> {
                    Microbot.startPlugin(autoLoginPlugin);
                    return true;
                });
                Microbot.log("AutoLoginPlugin enabled for break login");
            }
        } catch (Exception e) {
            Microbot.log("Failed to enable AutoLoginPlugin: " + e.getMessage());
        }
    }
    
    /**
     * Disables the AutoLoginPlugin if available and enabled
     */
    private void disableAutoLoginPlugin() {
        try {
            if (isAutoLoginPluginAvailable()) {
                AutoLoginPlugin autoLoginPlugin = (AutoLoginPlugin) Microbot.getPlugin(AutoLoginPlugin.class.getName());
                if (autoLoginPlugin != null && Microbot.isPluginEnabled(autoLoginPlugin.getClass())) {
                    Microbot.getClientThread().runOnSeperateThread(() -> {
                        Microbot.stopPlugin(autoLoginPlugin);
                        return true;
                    });
                    Microbot.log("AutoLoginPlugin disabled after use");
                }
            }
        } catch (Exception e) {
            Microbot.log("Failed to disable AutoLoginPlugin: " + e.getMessage());
        }
    }
    
    /**
     * Handles successful login after a logout break
     */
    private void onSuccessfulLogin() {
        try {
            Microbot.log("Login detected");

            // Disable AutoLoginPlugin after successful login
            disableAutoLoginPlugin();
            
            Microbot.log("Break system resuming normal operation");

            // Ensure we're on a Wintertodt world after login
            ensureWintertodtWorldAfterLogin();
            
        } catch (Exception e) {
            Microbot.log("Error during post-login cleanup: " + e.getMessage());
        }
    }
    
    /**
     * Ensures we're on a Wintertodt world after login, hops if necessary
     */
    private void ensureWintertodtWorldAfterLogin() {
        try {
            // Wintertodt worlds
            int[] wintertodtWorlds = {307, 309, 311, 389};
            int currentWorld = Rs2Player.getWorld();
            
            // Check if we're already on a Wintertodt world
            for (int world : wintertodtWorlds) {
                if (currentWorld == world) {
                    Microbot.log("Already on Wintertodt world " + currentWorld + " after login");
                    return;
                }
            }
            
            // Not on a Wintertodt world, hop to one
            Microbot.log("Not on Wintertodt world (current: " + currentWorld + ") - hopping after login...");
            
            int targetWorld = wintertodtWorlds[random.nextInt(wintertodtWorlds.length)];
            boolean hopSuccessful = Microbot.hopToWorld(targetWorld);
            
            if (hopSuccessful) {
                // Wait up to 15 seconds for hop to complete
                long startTime = System.currentTimeMillis();
                while (Rs2Player.getWorld() != targetWorld && System.currentTimeMillis() - startTime < 15000) {
                    sleep(1000);
                }
                
                if (Rs2Player.getWorld() == targetWorld) {
                    Microbot.log("Successfully hopped to Wintertodt world " + targetWorld + " after login");
                } else {
                    Microbot.log("World hop timeout after login - continuing on world " + Rs2Player.getWorld());
                }
            } else {
                Microbot.log("Failed to hop to Wintertodt world after login - continuing on world " + currentWorld);
            }
            
        } catch (Exception e) {
            Microbot.log("Error ensuring Wintertodt world after login: " + e.getMessage());
        }
    }
    
    /**
     * Schedule the next break
     */
    private void scheduleNextBreak() {
        int minInterval = config.minBreakInterval();
        int maxInterval = config.maxBreakInterval();
        nextBreakIn = Rs2Random.between(minInterval * 60, maxInterval * 60);
        
        Microbot.log(String.format("Next break scheduled in %d minutes", nextBreakIn / 60));
    }
    
    /**
     * Updates the window title to show break status and remaining time
     */
    private void updateTitle() {
        try {
            if (!breakActive) {
                // No break active - restore original title
                ClientUI.getFrame().setTitle(originalTitle);
                return;
            }
            
            String breakType = afkBreakActive ? "AFK Break" : "Logout Break";
            Duration duration = Duration.ofSeconds(breakTimeRemaining);
            long hours = duration.toHours();
            long minutes = duration.toMinutes() % 60;
            long seconds = duration.getSeconds() % 60;
            
            String timeRemaining = String.format("%02d:%02d:%02d", hours, minutes, seconds);
            String title = String.format("[%s - %s] %s", breakType, timeRemaining, originalTitle);
            
            ClientUI.getFrame().setTitle(title);
            
        } catch (Exception e) {
            // Ignore title update errors
        }
    }
    
    /**
     * Check if player is in a safe location for breaks
     */
    public boolean isInSafeLocationForBreak() {
        try {
            WorldPoint playerLocation = Rs2Player.getWorldLocation();
            if (playerLocation == null) return false;
            
            // Safe at bank
            if (playerLocation.distanceTo(BANK_LOCATION) <= 10) {
                return true;
            }
            
            // Safe at boss room when not actively doing anything (expanded to include safe spot radius)
            if (playerLocation.distanceTo(BOSS_ROOM) <= Math.max(8, SAFE_SPOT_RADIUS) && 
                !Rs2Player.isAnimating() && 
                !Rs2Player.isMoving() &&
                !Rs2Player.isInteracting()) {
                return true;
            }
            
            // Safe when in BANKING or WAITING states (expanded to include safe spot radius)
            State currentState = MKE_WintertodtScript.state;
            if ((currentState == State.BANKING || currentState == State.WAITING) && 
                playerLocation.distanceTo(BOSS_ROOM) <= Math.max(8, SAFE_SPOT_RADIUS)) {
                return true;
            }
            
            // Force safe when we're walking to safe spot and arrived
            if (isWalkingToSafeSpot && playerLocation.distanceTo(BOSS_ROOM) <= SAFE_SPOT_RADIUS &&
                !Rs2Player.isAnimating() && !Rs2Player.isMoving()) {
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            Microbot.log("Error checking safe location: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Emergency cleanup in case of errors
     */
    private void emergencyCleanup() {
        breakActive = false;
        afkBreakActive = false;
        logoutBreakActive = false;
        originalMousePosition = null;
        breakTimeRemaining = 0;
        
        // Restore window title
        updateTitle();
        
        // Reset action plan for safety
        try {
            MKE_WintertodtScript.resetActionPlanning();
        } catch (Exception e) {
            // Ignore if script not initialized
        }
    }
    
    /**
     * Get formatted time remaining in current break
     */
    public static String getBreakTimeRemaining() {
        if (!breakActive) return "No break active";
        
        Duration duration = Duration.ofSeconds(breakTimeRemaining);
        long hours = duration.toHours();
        long minutes = duration.toMinutes() % 60;
        long seconds = duration.getSeconds() % 60;
        
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
    
    /**
     * Get formatted time until next break
     */
    public static String getTimeUntilNextBreak() {
        if (breakActive) return "Break active";
        
        Duration duration = Duration.ofSeconds(nextBreakIn);
        long hours = duration.toHours();
        long minutes = duration.toMinutes() % 60;
        long seconds = duration.getSeconds() % 60;
        
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
    
    /**
     * Force start a break (for manual trigger)
     */
    public void forceBreak(BreakType type) {
        if (breakActive) {
            Microbot.log("Break already active, ignoring force break request");
            return;
        }
        
        if (type == BreakType.AFK_SHORT) {
            startAfkBreak();
        } else {
            startLogoutBreak();
        }
    }
    
    /**
     * Force end current break
     */
    public void forceEndBreak() {
        if (!breakActive) {
            return;
        }
        
        Microbot.log("Manually ending break - resetting action plan");
        breakTimeRemaining = 0;
        endCurrentBreak();
    }
    
    /**
     * Checks if we're currently in a logout break (player should be logged out)
     */
    public static boolean isLogoutBreakActive() {
        return logoutBreakActive;
    }
    
    /**
     * Reset the break manager
     */
    public void reset() {
        emergencyCleanup();
        waitingForSafeSpotSince = 0;
        isWalkingToSafeSpot = false;
        
        // Re-store the original title in case it changed
        try {
            originalTitle = ClientUI.getFrame().getTitle();
        } catch (Exception e) {
            originalTitle = "RuneLite";
        }
        
        initializeBreakTimer();
    }
    
    /**
     * Shutdown the break manager
     */
    public void shutdown() {
        emergencyCleanup();
        nextBreakIn = 0;
        waitingForSafeSpotSince = 0;
        isWalkingToSafeSpot = false;
        
        // Ensure title is restored on shutdown
        try {
            ClientUI.getFrame().setTitle(originalTitle);
        } catch (Exception e) {
            // Ignore title restoration errors during shutdown
        }
        
        Microbot.log("Break manager shutdown");
    }

    // --------------------------------------------------------------------
    // Reward cart recognition
    /**
     * Returns <code>true</code> when the main script is currently in any of
     * the temporary states that are used for the Wintertodt reward-cart
     * collection sequence.  While in these states we never want to begin a
     * custom break because it would interrupt the banking / walking flow and
     * could leave the player stuck outside of the arena.
     */
    private boolean isInRewardCollectionState(State state) {
        switch (state) {
            case EXITING_FOR_REWARDS:
            case WALKING_TO_REWARDS_BANK:
            case BANKING_FOR_REWARDS:
            case WALKING_TO_REWARD_CART:
            case LOOTING_REWARD_CART:
            case RETURNING_FROM_REWARDS:
                return true;
            default:
                return false;
        }
    }
    // --------------------------------------------------------------------
} 