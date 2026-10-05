package net.runelite.client.plugins.microbot.construction;

public class ServantRestock {

    public static final int MAX_FAILED_ATTEMPTS = 3;
    public static final long RETURN_TIMEOUT_MS = 20_000;
    public static final int SERVANT_WAGE = 10_000;

    public enum Servant {
        NONE,
        DEMON_BUTLER,
        OTHER
    }

    public enum Problem {
        NONE(""),
        UNSUPPORTED_SERVANT("Only the demon butler is supported. Hire a demon butler and use noted planks on him before starting."),
        NOT_ENOUGH_COINS("The demon butler wants his 10,000 coin wage. Bring at least 10,000 coins in your inventory."),
        NO_SERVANT("No demon butler answered Call Servant. Hire a demon butler (50 Construction, servant's quarters) before starting."),
        NO_NOTED_PLANKS("Out of noted planks for the demon butler to un-note. Bring noted planks in your inventory."),
        SERVANT_UNRESPONSIVE("The demon butler did not bring planks after several attempts. Check his dialogue and your inventory space.");

        private final String message;

        Problem(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    private boolean away;
    private boolean departed;
    private long sentAt;
    private int failedAttempts;
    private int lastPlankCount = -1;
    private boolean needingPlanks;

    public void observe(int plankCount, boolean servantVisible, long now, boolean needsPlanks) {
        if (needsPlanks && !needingPlanks) {
            failedAttempts = 0;
        }
        needingPlanks = needsPlanks;
        if (lastPlankCount >= 0 && plankCount > lastPlankCount) {
            failedAttempts = 0;
            away = false;
            departed = false;
        }
        lastPlankCount = plankCount;

        if (!away) {
            return;
        }
        if (!servantVisible) {
            departed = true;
        }
        if (now - sentAt >= RETURN_TIMEOUT_MS) {
            away = false;
            departed = false;
            failedAttempts++;
        }
    }

    public void onSent(long now) {
        away = true;
        departed = false;
        sentAt = now;
    }

    public void onFailedAttempt() {
        failedAttempts++;
    }

    public boolean isAway() {
        return away;
    }

    public boolean hasReturned(boolean servantVisible, boolean inDialogue) {
        return away && departed && (servantVisible || inDialogue);
    }

    public void onReturned() {
        away = false;
        departed = false;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public void reset() {
        away = false;
        departed = false;
        sentAt = 0;
        failedAttempts = 0;
        lastPlankCount = -1;
        needingPlanks = false;
    }

    public boolean canAttempt() {
        return failedAttempts < MAX_FAILED_ATTEMPTS;
    }

    public Problem problem(Servant servant, int notedPlanks, boolean wageDue, int coins, boolean needsPlanks) {
        if (servant == Servant.OTHER && needsPlanks) {
            return Problem.UNSUPPORTED_SERVANT;
        }
        if (wageDue && coins < SERVANT_WAGE) {
            return Problem.NOT_ENOUGH_COINS;
        }
        if (!needsPlanks || canAttempt()) {
            return Problem.NONE;
        }
        if (servant == Servant.NONE) {
            return Problem.NO_SERVANT;
        }
        if (notedPlanks <= 0) {
            return Problem.NO_NOTED_PLANKS;
        }
        return Problem.SERVANT_UNRESPONSIVE;
    }
}
