package net.runelite.client.plugins.custom.zulrah.actions;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

/**
 * Swaps to the correct setup for the phase, INLINE on the tick thread. Equipping goes through
 * {@code Rs2Inventory.wield -> invokeMenu} and does NOT stop the player running, so we can equip
 * while gliding to the stand tile. But it still fires a menu action, and walking/attacking fire
 * their own menu action synchronously on this same thread: two menu actions in one tick collide and
 * the walk/attack is dropped. So we equip ONLY on ticks where the movement/combat action will NOT
 * issue a click — i.e. while auto-running to the tile (already moving, no click this tick), or while
 * parked on the tile and already locked onto Zulrah. That gives run-while-equipping without the
 * collision. {@code wield} equips one mismatched piece per call, so a full setup completes one piece
 * per free tick. Bounded so genuinely-missing gear doesn't retry forever.
 */
@Slf4j
public class EquipGearAction implements ZulrahAction {

    static final String KEY = "equip-gear";
    // One piece is equipped per tick, so this must cover a full range<->mage swap plus slack; if a
    // piece is genuinely missing from the inventory we give up after this many ticks and log.
    // Package-private so ZulrahHelpers.gearReady() can release the attack-hold once we give up.
    static final int MAX_GEAR_SWAP_ATTEMPTS = 8;

    @Override
    public int order() {
        return 600;
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public boolean needsExecution(ZulrahState state) {
        FightContext ctx = state.context();
        if (ctx.getPhase() == null || ctx.getGearSwapAttempts() >= MAX_GEAR_SWAP_ATTEMPTS) {
            return false;
        }
        Rs2InventorySetup setup = ZulrahHelpers.desiredSetup(ctx, ctx.getPhase());
        if (setup == null || setup.doesEquipmentMatch()) {
            return false;
        }
        // Equip only on a tick where no walk/attack click is issued, so the two menu actions don't
        // collide (which drops the walk). Attacks are now held until the gear is ready (see
        // ZulrahHelpers.gearReady), so while a swap is in progress the ONLY competing click is the
        // walk to the tile — and that fires only when we're off the tile and stopped. So it's free to
        // equip whenever we're on the tile or already gliding toward it.
        return ZulrahHelpers.atTargetTile(ctx) || Rs2Player.isMoving();
    }

    @Override
    public Object execute(ZulrahState state) {
        FightContext ctx = state.context();
        Rs2InventorySetup setup = ZulrahHelpers.desiredSetup(ctx, ctx.getPhase());
        if (ctx.getGearSwapAttempts() == 0) {
            log.info("Changing to {} setup", setup == ctx.getMagicSetup() ? "magic" : "range");
        }
        ctx.setGearSwapAttempts(ctx.getGearSwapAttempts() + 1);
        setup.wearEquipment();
        boolean ready = setup.doesEquipmentMatch();
        if (ctx.getGearSwapAttempts() >= MAX_GEAR_SWAP_ATTEMPTS && !ready) {
            log.warn("Equipment swap incomplete (missing items?) — continuing without it.");
        }
        return ready;
    }
}
