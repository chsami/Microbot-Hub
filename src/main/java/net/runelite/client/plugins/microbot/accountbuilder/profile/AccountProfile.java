package net.runelite.client.plugins.microbot.accountbuilder.profile;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Per-account behavioral timing profile.
 * Values are randomized on first creation and persisted as JSON so the same
 * account always behaves consistently across sessions.
 */
public class AccountProfile {

    @JsonProperty("accountName")
    private String accountName;

    /** Base loop delay in milliseconds (e.g. 600–1100 ms). */
    @JsonProperty("tickDelayMs")
    private long tickDelayMs;

    /** Multiplier applied to reaction sleeps (0.85–1.20). */
    @JsonProperty("reactionVariance")
    private double reactionVariance;

    /** Probability of a random idle pause per tick (0.01–0.04). */
    @JsonProperty("idleChance")
    private double idleChance;

    /** Maximum duration of idle pauses in ms (1500–4000). */
    @JsonProperty("idleMaxMs")
    private long idleMaxMs;

    /** How often to take a short AFK break (15–40 minutes in ms). */
    @JsonProperty("breakIntervalMs")
    private long breakIntervalMs;

    /** How long a break lasts (30–120 seconds in ms). */
    @JsonProperty("breakDurationMs")
    private long breakDurationMs;

    // Default constructor required by Jackson
    public AccountProfile() {}

    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }

    public long getTickDelayMs() { return tickDelayMs; }
    public void setTickDelayMs(long tickDelayMs) { this.tickDelayMs = tickDelayMs; }

    public double getReactionVariance() { return reactionVariance; }
    public void setReactionVariance(double reactionVariance) { this.reactionVariance = reactionVariance; }

    public double getIdleChance() { return idleChance; }
    public void setIdleChance(double idleChance) { this.idleChance = idleChance; }

    public long getIdleMaxMs() { return idleMaxMs; }
    public void setIdleMaxMs(long idleMaxMs) { this.idleMaxMs = idleMaxMs; }

    public long getBreakIntervalMs() { return breakIntervalMs; }
    public void setBreakIntervalMs(long breakIntervalMs) { this.breakIntervalMs = breakIntervalMs; }

    public long getBreakDurationMs() { return breakDurationMs; }
    public void setBreakDurationMs(long breakDurationMs) { this.breakDurationMs = breakDurationMs; }
}
