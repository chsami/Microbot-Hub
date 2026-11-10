package net.runelite.client.plugins.microbot.droslayer.walker;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;

/** Observe the actual ring landing, then verify the temporary equipment swap is undone. */
final class FairyRingEquipmentRestore
{
    interface EquipmentAction
    {
        void restore(boolean shield, int itemId);
    }

    private WorldPoint origin;
    private WorldPoint destination;
    private int originalWeapon;
    private int originalShield;
    private boolean sawStaff;
    private boolean preparedAtBank;
    private int restoreStarted = -1;
    private int lastAction = -1;
    private boolean failed;
    private WorldPoint previousPosition;
    private int previousWeapon;
    private int previousShield;
    private int previousTick = -2;

    void beforeBankStaffEquip(int weapon, int shield)
    {
        if (preparedAtBank || destination != null || isStaff(weapon)) return;
        originalWeapon = weapon;
        originalShield = shield;
        preparedAtBank = true;
    }

    static boolean isStaff(int id)
    {
        return id == ItemID.DRAMEN_STAFF || id == ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF;
    }

    boolean tick(WorldPoint ringOrigin, WorldPoint ringDestination, WorldPoint player,
                 int weapon, int shield, int tick, EquipmentAction action)
    {
        if (failed) return true;
        int beforeWeapon = weapon;
        int beforeShield = shield;
        boolean justEquippedStaff = isStaff(weapon) && !isStaff(previousWeapon)
            && previousTick >= 0 && tick - previousTick <= 1
            && near(previousPosition, ringOrigin, 8);
        if (justEquippedStaff)
        {
            beforeWeapon = previousWeapon;
            beforeShield = previousShield;
        }
        if (isStaff(weapon) && !isStaff(previousWeapon) && previousTick >= 0
            && tick - previousTick <= 1)
            beforeBankStaffEquip(previousWeapon, previousShield);
        previousPosition = player;
        previousWeapon = weapon;
        previousShield = shield;
        previousTick = tick;
        if (destination == null)
        {
            if (!near(player, ringOrigin, 8) || ringDestination == null
                || (isStaff(weapon) && !justEquippedStaff && !preparedAtBank)
                || near(ringOrigin, ringDestination, 8)) return false;
            origin = ringOrigin;
            destination = ringDestination;
            if (!preparedAtBank)
            {
                originalWeapon = beforeWeapon;
                originalShield = beforeShield;
            }
        }
        sawStaff |= isStaff(weapon);
        if (!near(player, destination, 3) || near(player, origin, 8)) return false;
        if (!sawStaff || (weapon == originalWeapon && shield == originalShield))
        {
            reset();
            return false;
        }
        if (restoreStarted < 0) restoreStarted = tick;
        if (tick - restoreStarted >= 10)
        {
            failed = true;
            return true;
        }
        if (lastAction >= 0 && tick - lastAction < 2) return true;
        lastAction = tick;
        // Restoring a shield first frees space when the original weapon slot was empty.
        if (shield != originalShield && originalShield >= 0
            && (weapon == originalWeapon || originalWeapon < 0))
            action.restore(true, originalShield);
        else if (weapon != originalWeapon)
            action.restore(false, originalWeapon);
        else if (shield != originalShield)
            action.restore(true, originalShield);
        return true;
    }

    boolean isTracking() { return destination != null; }
    boolean failed() { return failed; }

    void reset()
    {
        origin = null;
        destination = null;
        sawStaff = false;
        preparedAtBank = false;
        restoreStarted = -1;
        lastAction = -1;
        failed = false;
        previousPosition = null;
        previousTick = -2;
    }

    private static boolean near(WorldPoint a, WorldPoint b, int radius)
    {
        return a != null && b != null && a.getPlane() == b.getPlane() && a.distanceTo2D(b) <= radius;
    }
}
