package net.runelite.client.plugins.microbot.valetotems.models;

import net.runelite.client.plugins.microbot.valetotems.enums.GameState;
import net.runelite.client.plugins.microbot.valetotems.enums.TotemLocation;
import net.runelite.client.plugins.microbot.valetotems.enums.TotemLocation.RouteType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Model class to track the overall game session state and statistics
 */
public class GameSession {
    private GameState currentState;
    private TotemLocation currentTotem;
    private RouteType currentRouteType; // Track which route we're using
    private int currentRound;
    private int totalRounds;
    private Map<TotemLocation, TotemProgress> totemProgress;
    private long sessionStartTime;
    private long lastActivityTime;
    private int totalOfferingsCollected;
    private int totalBowsFletched;
    private List<String> errorMessages;
    private boolean isActive;
    private boolean navigationInProgress;
    
    // Log basket tracking for extended route
    private int logBasketLogCount; // Track how many logs are stored in the log basket

    // Performance tracking
    private Map<GameState, Long> stateTimeTracking;
    private Map<GameState, Integer> stateChangeCount;

    public GameSession() {
        this.currentState = GameState.IDLE;
        this.currentTotem = null;
        this.currentRouteType = RouteType.STANDARD; // Default to standard route
        this.currentRound = 0;
        this.totalRounds = 0;
        this.totemProgress = new HashMap<>();
        this.sessionStartTime = System.currentTimeMillis();
        this.lastActivityTime = sessionStartTime;
        this.totalOfferingsCollected = 0;
        this.totalBowsFletched = 0;
        this.errorMessages = new ArrayList<>();
        this.isActive = false;
        this.navigationInProgress = false;
        this.logBasketLogCount = 1; // Initialize basket with 1 log, so it will be emptied at start
        this.stateTimeTracking = new HashMap<>();
        this.stateChangeCount = new HashMap<>();
        resetTotemProgressForAllLocations();
    }

    public void resetTotemProgressForAllLocations() {
        // Clear existing progress
        totemProgress.clear();
        
        // Only initialize progress for totems in the current route
        TotemLocation[] routeLocations = TotemLocation.getRouteLocations(currentRouteType);
        for (TotemLocation location : routeLocations) {
            totemProgress.put(location, new TotemProgress(location));
        }
    }

    /**
     * Set the route type for this session
     * @param routeType the route type to use
     */
    public void setRouteType(RouteType routeType) {
        if (this.currentRouteType != routeType) {
            this.currentRouteType = routeType;
            resetTotemProgressForAllLocations(); // Reset progress when switching routes
            System.out.println("Switched to " + routeType.getDescription() + " (" + routeType.getMaxTotems() + " totems)");
        }
    }

    /**
     * Get the current route type
     * @return the current route type
     */
    public RouteType getCurrentRouteType() {
        return currentRouteType;
    }

    /**
     * Get the maximum number of totems for the current route
     * @return max totems for current route
     */
    public int getMaxTotemsForCurrentRoute() {
        return currentRouteType.getMaxTotems();
    }

    // State management
    public void setState(GameState newState) {
        if (currentState != newState) {
            // Track time spent in previous state
            long currentTime = System.currentTimeMillis();
            if (currentState != null) {
                stateTimeTracking.merge(currentState, currentTime - lastActivityTime, Long::sum);
                stateChangeCount.merge(currentState, 1, Integer::sum);
            }
            
            this.currentState = newState;
            this.lastActivityTime = currentTime;
        }
    }

    public void setCurrentTotem(TotemLocation totem) {
        this.currentTotem = totem;
    }

    public void startNewRound() {
        currentRound++;
        totalRounds++;
        
        // Reset totem progress for new round
        resetTotemProgressForAllLocations();
        
        // Start with first totem for current route
        currentTotem = TotemLocation.getFirst(currentRouteType);
        setState(GameState.BANKING);
        isActive = true;
    }

    public void completeRound() {
        setState(GameState.COMPLETED);
        isActive = false;
    }

    public void addError(String errorMessage) {
        errorMessages.add(String.format("[%d] %s", System.currentTimeMillis(), errorMessage));
        setState(GameState.ERROR);
    }

    // Progress tracking
    public TotemProgress getCurrentTotemProgress() {
        return currentTotem != null ? totemProgress.get(currentTotem) : null;
    }

    public TotemProgress getTotemProgress(TotemLocation location) {
        return totemProgress.get(location);
    }

    public void moveToNextTotem() {
        if (currentTotem != null) {
            TotemLocation next = currentTotem.getNext();
            if (next != null) {
                currentTotem = next;
            } else {
                // All totems completed, return to bank
                setState(GameState.RETURNING_TO_BANK);
            }
        }
    }

    // Statistics
    public int getCompletedTotemsThisRound() {
        int completed = 0;
        for (TotemProgress progress : totemProgress.values()) {
            if (progress.isCompletelyFinished()) {
                completed++;
            }
        }
        return completed;
    }

    public double getAverageTotemCompletionTime() {
        long totalTime = 0;
        int completedCount = 0;
        
        for (TotemProgress progress : totemProgress.values()) {
            if (progress.isCompletelyFinished() && progress.getCompletionTime() > 0) {
                totalTime += progress.getDurationMs();
                completedCount++;
            }
        }
        
        return completedCount > 0 ? (double) totalTime / completedCount : 0;
    }

    public long getTimeInState(GameState state) {
        return stateTimeTracking.getOrDefault(state, 0L);
    }

    public int getStateChangeCount(GameState state) {
        return stateChangeCount.getOrDefault(state, 0);
    }

    public long getSessionDuration() {
        return System.currentTimeMillis() - sessionStartTime;
    }

    public double getCompletionRate() {
        int totalPossibleTotems = totalRounds * currentRouteType.getMaxTotems();
        if (totalPossibleTotems == 0) return 0.0;
        
        int totalCompleted = 0;
        // Count all completed totems from all rounds (only for current route type)
        for (TotemProgress progress : totemProgress.values()) {
            if (progress.isCompletelyFinished()) {
                totalCompleted++;
            }
        }
        
        return (double) totalCompleted / totalPossibleTotems * 100;
    }

    // Getters
    public GameState getCurrentState() {
        return currentState;
    }

    public TotemLocation getCurrentTotem() {
        return currentTotem;
    }

    public int getCurrentRound() {
        return currentRound;
    }

    public int getTotalRounds() {
        return totalRounds;
    }

    public long getSessionStartTime() {
        return sessionStartTime;
    }

    public long getLastActivityTime() {
        return lastActivityTime;
    }

    public int getTotalOfferingsCollected() {
        return totalOfferingsCollected;
    }

    public void incrementOfferingsCollected() {
        this.totalOfferingsCollected++;
    }

    public int getTotalBowsFletched() {
        return totalBowsFletched;
    }

    public List<String> getErrorMessages() {
        return new ArrayList<>(errorMessages);
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        this.isActive = active;
        if (!active) {
            setState(GameState.IDLE);
        }
    }

    public boolean isNavigationInProgress() {
        return navigationInProgress;
    }

    public void setNavigationInProgress(boolean navigationInProgress) {
        this.navigationInProgress = navigationInProgress;
    }

    // Log basket tracking methods
    public int getLogBasketLogCount() {
        return logBasketLogCount;
    }

    public void setLogBasketLogCount(int count) {
        this.logBasketLogCount = Math.max(0, Math.min(count, 28)); // Ensure valid range 0-28
    }

    public void addLogsToBasket(int logsAdded) {
        this.logBasketLogCount = Math.max(0, Math.min(this.logBasketLogCount + logsAdded, 28));
        System.out.println("Added " + logsAdded + " logs to basket. Basket now contains: " + this.logBasketLogCount + "/28 logs");
    }

    public void removeLogsFromBasket(int logsRemoved) {
        int actualRemoved = Math.min(logsRemoved, this.logBasketLogCount);
        this.logBasketLogCount = Math.max(0, this.logBasketLogCount - actualRemoved);
        System.out.println("Removed " + actualRemoved + " logs from basket. Basket now contains: " + this.logBasketLogCount + "/28 logs");
    }

    public void emptyLogBasket() {
        int logsEmptied = this.logBasketLogCount;
        this.logBasketLogCount = 0;
        System.out.println("Emptied log basket. Removed " + logsEmptied + " logs. Basket is now empty.");
    }

    public boolean hasLogBasketSpace() {
        return logBasketLogCount < 28;
    }

    public int getLogBasketAvailableSpace() {
        return 28 - logBasketLogCount;
    }

    @Override
    public String toString() {
        return String.format("Round %d, State: %s, Totem: %s, Completed: %d/%d, Route: %s",
                currentRound,
                currentState.getDescription(),
                currentTotem != null ? currentTotem.name() : "None",
                getCompletedTotemsThisRound(),
                currentRouteType.getMaxTotems(),
                currentRouteType.getDescription());
    }
} 