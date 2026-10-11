package net.runelite.client.plugins.microbot.zulrahslayer.constants;

/**
 * When (if ever) a phase spews venom cloud barrages, relative to its attacks. Drives the venom
 * pre-move: we only leave the current tile early when the clouds land at the END of the phase.
 */
public enum VenomTiming {
    /** No venom cloud barrage this phase. */
    NONE,
    /**
     * Venom is spewed at (or near) the START of the phase. Our stand tile is the safespot for it, so
     * we STAY — pre-moving here just walks us into the clouds while the phase's attacks continue.
     */
    START,
    /**
     * Venom lands toward the END of the phase (after the attacks). Pre-move to the next phase's tile
     * as soon as it appears so we don't linger in it.
     */
    END
}
