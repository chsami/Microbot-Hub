package net.runelite.client.plugins.microbot.cgprep.map;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.cgprep.data.CgIds;

/**
 * Maps RAW instance coordinates to Gauntlet room-grid cells (0..6, 0..6).
 *
 * Confirmed by the recon run (2026-10-26):
 *  - rooms are 16x16 tiles, room X grows with +x, room Z grows with +y (north);
 *  - room boundaries sit on multiples of 16 in RAW coordinates (origin was 13568,13120);
 *  - raw coordinates stay stable for the whole run even though the scene is rebuilt (base moves)
 *    every time a room is lit;
 *  - varbits 9289/9290 hold the START room (not the current room), set once on entry.
 *
 * So calibration is a single step: the player's first raw tile inside the run lies in the start
 * room, which gives the 16-aligned origin directly.
 *
 * Only feed RAW coordinates (client.getLocalPlayer().getWorldLocation(), entity getWorldLocation()).
 * Rs2Player.getWorldLocation() returns TEMPLATE coords in instances (knowledge/08).
 */
public final class RoomGrid
{
	private static final int S = CgIds.ROOM_SIZE_TILES;

	private volatile boolean calibrated;
	private volatile int ox;
	private volatile int oy;

	public synchronized void reset()
	{
		calibrated = false;
	}

	/** Calibrate from the start room (from varbits) and a raw tile known to be inside it. */
	public synchronized void calibrate(int startRoomX, int startRoomZ, WorldPoint rawInStartRoom)
	{
		if (rawInStartRoom == null || startRoomX < 0 || startRoomZ < 0)
		{
			return;
		}
		ox = Math.floorDiv(rawInStartRoom.getX(), S) * S - startRoomX * S;
		oy = Math.floorDiv(rawInStartRoom.getY(), S) * S - startRoomZ * S;
		calibrated = true;
	}

	public boolean isCalibrated()
	{
		return calibrated;
	}

	/** Room cell {x, z} for a raw point, or null if not calibrated / outside the grid. */
	public synchronized int[] roomOf(WorldPoint raw)
	{
		if (!calibrated || raw == null)
		{
			return null;
		}
		int x = Math.floorDiv(raw.getX() - ox, S);
		int z = Math.floorDiv(raw.getY() - oy, S);
		if (x < 0 || z < 0 || x >= CgIds.ROOM_GRID || z >= CgIds.ROOM_GRID)
		{
			return null;
		}
		return new int[]{x, z};
	}

	/** Raw centre tile of a room cell, or null if not calibrated. */
	public synchronized WorldPoint centreOf(int roomX, int roomZ, int plane)
	{
		if (!calibrated)
		{
			return null;
		}
		return new WorldPoint(ox + roomX * S + S / 2, oy + roomZ * S + S / 2, plane);
	}

	public int originX()
	{
		return ox;
	}

	public int originY()
	{
		return oy;
	}

	public synchronized String describe()
	{
		return calibrated ? "origin " + ox + "," + oy + " OK" : "not calibrated";
	}
}
