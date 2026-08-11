package net.runelite.client.plugins.custom.zulrah.actions;

import lombok.Getter;
import lombok.Setter;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.custom.zulrah.rotationutils.ZulrahPhase;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;

/**
 * Durable state of the current fight, shared across ticks. Actions read/write it via
 * {@link ZulrahState#context()}; the plugin's animation events (phase change, melee swing, jad
 * flick) also write to it. Fields are volatile where they are touched from both the client (event)
 * thread and the script tick thread.
 */
@Getter
@Setter
public class FightContext {

    public static final int DEFAULT_ATTACK_SPEED_TICKS = 4;

    // --- Environment (set once on initialise) ---
    private final ItemManager itemManager;
    private Rs2InventorySetup magicSetup;
    private Rs2InventorySetup rangeSetup;

    // --- Durable fight state ---
    /** Current phase, set by the plugin's phase-transition events. */
    private volatile ZulrahPhase phase;
    /** Set true by an event when a new phase arrives; consumed by SyncPhaseAction. */
    private volatile boolean phaseChanged;
    /** The tile to stand on this phase. */
    private volatile WorldPoint standLocation;
    /** The next phase's stand tile (known once the rotation is identified); used to pre-move out of
     *  end-of-phase venom clouds before the phase actually transitions. Null if unknown. */
    private volatile WorldPoint nextStandLocation;
    /** Set once we've pre-moved toward the next phase because a venom cloud spawned; blocks further
     *  venom pre-moves this phase. Reset on resurface so each phase re-arms exactly one pre-move. */
    private volatile boolean venomPreMoved;
    /** Whether Zulrah is currently surfaced (attackable). False while it is submerged — during the
     *  opening run before the first spawn and between phases while it dives — so we walk to the tile
     *  without wasting clicks on a target we can't hit. Set by the plugin's spawn/dive events. */
    private volatile boolean surfaced;
    /** First phase of a rotation only: hold at the spawn spot and get the opening attack off, moving
     *  to the first stand tile only once that attack has actually fired. Set when the fight starts,
     *  cleared the moment the opening hit lands. Every later phase ignores this and runs/attacks as
     *  normal. */
    private volatile boolean openingHold;
    /** First phase only, set the instant the opening hit fires: walk straight to the first stand tile
     *  WITHOUT attacking on the way. From the spawn spot in the centre Zulrah is in range, so the normal
     *  attack-while-repositioning would keep us pinned there instead of moving. Cleared once we reach the
     *  tile, after which normal safespot attacking resumes. */
    private volatile boolean openingWalk;
    /** Whether this is the NE melee-dodge phase. */
    private volatile boolean meleeDodgePhase;
    /** Set once the jad phase's starting overhead has been anchored; the flick alternates it after. */
    private volatile boolean jadStartPrayerSet;
    /** Which of the two NE dodge tiles is currently targeted; flipped on each tail swing. */
    private volatile boolean meleeDodgeAtNorth = true;

    /** Wall-clock of the last attack, for the attack-on-cooldown-while-moving cadence. */
    private long lastAttackAtMs;
    /** Bounded gear-swap attempts for the current phase. */
    private int gearSwapAttempts;

    /** Weapon-speed cache, refreshed only when the equipped weapon changes. */
    private int cachedWeaponId = -1;
    private int cachedAttackSpeedTicks = DEFAULT_ATTACK_SPEED_TICKS;

    // --- Between-kills restock. A pending flag that outlives reset() like the loot fields (the routine
    //     runs after the on-death reset). The enable/disable decision is read LIVE from the config in
    //     LootAction, not snapshotted here, so toggling it takes effect without a restart. ---
    /** Armed by LootAction once looting finishes (only when the restock toggle is on); consumed by
     *  ReturnToZulrahAction, which runs the whole bank-and-travel trip and clears it. */
    private volatile boolean prepPending;
    /** When the armed prep should SKIP the banking leg and only travel back to Zulrah. Set true only by
     *  the "Travel to Zulrah" start point; the normal between-kills prep leaves it false (full trip).
     *  Like prepPending, it outlives reset(). */
    private volatile boolean prepSkipBank;

    // --- Post-kill looting. Deliberately NOT cleared by reset(): reset() runs on death right after
    //     these are set, and looting happens after the fight state has already been torn down. ---
    /** Set on Zulrah's death animation; while true we're waiting for the corpse to despawn (the drop
     *  only lands once the death animation finishes). Cleared when loot is armed on despawn. Like the
     *  loot fields, deliberately NOT cleared by reset() — it must outlive the on-death reset(). */
    private volatile boolean deathAwaitingDrop;
    /** Set on Zulrah's death; while true (and no fight active) LootAction grabs the kill's drops. */
    private volatile boolean lootPending;
    /** Give-up time for looting, extended on each successful pickup so we stop shortly after the last
     *  one (also covers the short delay before the drop spawns). */
    private volatile long lootDeadlineMs;

    public FightContext(ItemManager itemManager) {
        this.itemManager = itemManager;
    }

    /** Clears per-fight state so a fresh encounter starts idle at the first phase. */
    public void reset() {
        phase = null;
        phaseChanged = false;
        standLocation = null;
        nextStandLocation = null;
        venomPreMoved = false;
        surfaced = false;
        openingHold = false;
        openingWalk = false;
        meleeDodgePhase = false;
        jadStartPrayerSet = false;
        meleeDodgeAtNorth = true;
        lastAttackAtMs = 0;
        gearSwapAttempts = 0;
    }
}
