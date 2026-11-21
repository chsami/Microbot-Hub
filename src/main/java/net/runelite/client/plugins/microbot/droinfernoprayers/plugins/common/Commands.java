package net.runelite.client.plugins.microbot.droinfernoprayers.plugins.common;

import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.client.plugins.microbot.droinfernoprayers.ArenaInputScope;
import net.runelite.client.plugins.microbot.droinfernoprayers.ArenaProduct;
import net.runelite.client.plugins.microbot.droinfernoprayers.execution.PrayerDiagnostics;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

public class Commands
{
	protected final Client client;
    private final java.util.function.BooleanSupplier allowed;
    private final ArenaProduct product;

	public Commands(Client client, java.util.function.BooleanSupplier allowed)
	{
        this(client, allowed, ArenaProduct.INFERNO);
    }

    public Commands(Client client, java.util.function.BooleanSupplier allowed, ArenaProduct product) {
        this.client = client;
        this.allowed = () -> {
            try {
                net.runelite.client.plugins.microbot.droinfernoprayers.MouseMotionScope.checkpoint();
            }
            catch (net.runelite.client.plugins.microbot.droinfernoprayers.MouseMotionScope.Cancelled stale) {
                return false;
            }
            return allowed.getAsBoolean();
        };
        this.product = product;
	}

    private volatile java.util.Map<Rs2PrayerEnum, Boolean> prayerSnapshot = java.util.Map.of();
    private volatile long prayerSnapshotNanos;

    public void observePrayers(java.util.function.IntPredicate active) {
        var snapshot = new java.util.EnumMap<Rs2PrayerEnum, Boolean>(Rs2PrayerEnum.class);
        for (var prayer : Rs2PrayerEnum.values()) snapshot.put(prayer, active.test(prayer.getVarbit()));
        prayerSnapshot = java.util.Collections.unmodifiableMap(snapshot);
        prayerSnapshotNanos = System.nanoTime();
    }

    public void clearPrayerSnapshot() {
        prayerSnapshot = java.util.Map.of();
        prayerSnapshotNanos = 0;
    }

    public boolean prayerSet(String name, boolean on) {
        if (!allowed.getAsBoolean()) return false;
        var prayer = prayer(name);
        if (prayer == null) return false;

        var trace = PrayerDiagnostics.current();
        return ArenaInputScope.onClientThread(() -> {
            if (!allowed.getAsBoolean()) return false;
            observePrayers(id -> client.getVarbitValue(id) == 1);
            boolean active = prayerSnapshot.get(prayer);
            if (trace != null) trace.before(active);
            return active == on || invokePrayer(prayer, on, trace);
        }).orElse(false);
    }

    public Boolean prayerActive(String name) {
        if (!allowed.getAsBoolean() || System.nanoTime() - prayerSnapshotNanos > 1_200_000_000L) return null;
        var prayer = prayer(name);
        return prayer == null ? null : prayerSnapshot.get(prayer);
    }

    private boolean invokePrayer(Rs2PrayerEnum p, boolean desired, PrayerDiagnostics.Ticket trace) {
        if (!allowed.getAsBoolean()) return false;
        if (trace != null) trace.inputQueued();
        client.menuAction(-1, p.getIndex(), MenuAction.CC_OP, 1, -1, "Activate", "");
        return true;
    }

    public static Rs2PrayerEnum prayerEnum(String name) {
        return prayer(name);
    }

	protected static Rs2PrayerEnum prayer(String name)
	{
		switch (name)
		{
			case "BURST_OF_STRENGTH":
				return Rs2PrayerEnum.BURST_STRENGTH;
			case "CLARITY_OF_THOUGHT":
				return Rs2PrayerEnum.CLARITY_THOUGHT;
			case "PROTECT_FROM_MAGIC":
				return Rs2PrayerEnum.PROTECT_MAGIC;
			case "PROTECT_FROM_MISSILES":
				return Rs2PrayerEnum.PROTECT_RANGE;
			case "PROTECT_FROM_MELEE":
				return Rs2PrayerEnum.PROTECT_MELEE;
			case "DEADEYE":
				return Rs2PrayerEnum.DEAD_EYE;
			default:
				try
				{
					return Rs2PrayerEnum.valueOf(name);
				}
				catch (IllegalArgumentException e)
				{
					return null;
				}
		}
	}
}
