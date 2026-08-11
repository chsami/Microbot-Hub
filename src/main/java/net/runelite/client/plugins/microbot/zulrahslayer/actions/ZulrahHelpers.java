package net.runelite.client.plugins.custom.zulrah.actions;

import net.runelite.api.Actor;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.game.ItemStats;
import net.runelite.client.plugins.custom.zulrah.constants.StandLocation;
import net.runelite.client.plugins.custom.zulrah.constants.ZulrahType;
import net.runelite.client.plugins.custom.zulrah.rotationutils.ZulrahPhase;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.function.Supplier;

/** Stateless combat/query helpers shared by the actions. */
final class ZulrahHelpers {

    static final int VENOM_THRESHOLD = 1000000;
    static final int VENOM_MAXIMUM_DAMAGE = 20;

    private ZulrahHelpers() {
    }

    /**
     * We attack the magic (tanzanite) form with ranged and everything else with magic. The jad
     * phase is always Zulrah's green form, so it is attacked with magic regardless of the form its
     * rotation entry is encoded with (ROT_C/D encode it as MAGIC only to set the magic start prayer).
     */
    static boolean attackWithMagic(ZulrahPhase phase) {
        return phase.getZulrahNpc().isJad() || phase.getZulrahNpc().getType() != ZulrahType.MAGIC;
    }

    static Rs2InventorySetup desiredSetup(FightContext ctx, ZulrahPhase phase) {
        return attackWithMagic(phase) ? ctx.getMagicSetup() : ctx.getRangeSetup();
    }

    /** The tile we should be standing on this tick: the melee-dodge tile, or the phase's stand tile. */
    static WorldPoint targetTile(FightContext ctx) {
        if (ctx.isMeleeDodgePhase()) {
            return (ctx.isMeleeDodgeAtNorth() ? StandLocation.NORTHEAST_NORTH : StandLocation.NORTHEAST_TOP).toWorldPoint();
        }
        return ctx.getStandLocation();
    }

    static boolean atTargetTile(FightContext ctx) {
        WorldPoint target = targetTile(ctx);
        if (target == null) {
            return false;
        }
        WorldPoint pos = Rs2Player.getWorldLocation();
        // Exact tile, or stopped within one tile of it (can't stand exactly on it) — treat as arrived
        // so gear swap / attacking trigger instead of looping forever trying to reach the exact tile.
        return pos.equals(target) || (!Rs2Player.isMoving() && pos.distanceTo(target) <= 1);
    }

    private static Rs2NpcModel nearestZulrah() {
        return Microbot.getRs2NpcCache().query().withName("zulrah").nearestOnClientThread(20);
    }

    static void clickNearestZulrah() {
        Rs2NpcModel zulrah = nearestZulrah();
        if (zulrah != null) {
            zulrah.click("attack");
        }
    }

    /**
     * Keep Zulrah on screen so our attack clicks land: if it has drifted off-screen, turn the camera
     * back to face it. No-op while it's already visible, so we don't fight the camera every tick (and
     * since {@link Rs2Camera#turnTo} blocks until the turn completes, we only pay that cost when needed).
     */
    static void faceZulrah() {
        Rs2NpcModel zulrah = nearestZulrah();
        if (zulrah == null) {
            return;
        }
        LocalPoint lp = zulrah.getLocalLocation();
        if (lp != null && Rs2Camera.isTileOnScreen(lp)) {
            return;
        }
        Rs2Camera.turnTo(zulrah);
    }

    static boolean isInteractingWithZulrah() {
        Actor interacting = Rs2Player.getInteracting();
        return interacting != null && "zulrah".equalsIgnoreCase(interacting.getName());
    }

    static boolean zulrahInRange() {
        // getAttackRange() reads the weapon definition (client thread) and can report a melee/unknown
        // 1 for powered staves / some ranged weapons; fall back to a ranged/magic reach so the
        // attack-while-moving still fires. Then reuse the NPC-cache distance query (same one
        // clickNearestZulrah uses) so we don't rely on world-point coords lining up in the instance.
        final Supplier<Integer> rangeSupplier = () -> {
            int r = Rs2Combat.getAttackRange();
            return r <= 1 ? 10 : r;
        };
        Integer range = Microbot.getClientThread().invoke(rangeSupplier);
        int r = range != null ? range : 10;
        return Microbot.getRs2NpcCache().query().withName("zulrah").nearestOnClientThread(r) != null;
    }

    /** Equipped weapon's attack speed in ticks, cached in the context and refreshed on weapon change. */
    static int attackSpeedTicks(FightContext ctx) {
        var weapon = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
        int id = weapon != null ? weapon.getId() : -1;
        if (id != ctx.getCachedWeaponId()) {
            ctx.setCachedWeaponId(id);
            ctx.setCachedAttackSpeedTicks(FightContext.DEFAULT_ATTACK_SPEED_TICKS);
            if (id != -1) {
                // getItemStats -> getItemComposition must run on the client thread.
                final Supplier<Integer> lookup = () -> {
                    ItemStats stats = ctx.getItemManager().getItemStats(id);
                    return stats != null && stats.getEquipment() != null ? stats.getEquipment().getAspeed() : 0;
                };
                Integer aspeed = Microbot.getClientThread().invoke(lookup);
                if (aspeed != null && aspeed > 0) {
                    ctx.setCachedAttackSpeedTicks(aspeed);
                }
            }
        }
        return ctx.getCachedAttackSpeedTicks();
    }

    static long attackCooldownMs(FightContext ctx) {
        return attackSpeedTicks(ctx) * 600L;
    }

    /**
     * True once the phase's desired setup is fully equipped — or there's no phase/setup, or we've
     * used up the gear-swap attempts (items missing) and must attack with whatever we have. Attacks
     * are held until this is true so we don't waste hits on a form that resists our current style
     * (e.g. magic against the blue/tanzanite form, which has very high magic defence).
     */
    static boolean gearReady(FightContext ctx) {
        ZulrahPhase phase = ctx.getPhase();
        if (phase == null || ctx.getGearSwapAttempts() >= EquipGearAction.MAX_GEAR_SWAP_ATTEMPTS) {
            return true;
        }
        Rs2InventorySetup setup = desiredSetup(ctx, phase);
        return setup == null || setup.doesEquipmentMatch();
    }

    static int nextPoisonDamage(int poisonValue) {
        int damage;
        if (poisonValue >= VENOM_THRESHOLD) {
            // Venom damage starts at 6 and increments in twos; the VarPlayer increments by 1.
            poisonValue -= VENOM_THRESHOLD - 3;
            damage = poisonValue * 2;
            if (damage > VENOM_MAXIMUM_DAMAGE) {
                damage = VENOM_MAXIMUM_DAMAGE;
            }
        } else {
            damage = (int) Math.ceil(poisonValue / 5.0f);
        }
        return damage;
    }
}
