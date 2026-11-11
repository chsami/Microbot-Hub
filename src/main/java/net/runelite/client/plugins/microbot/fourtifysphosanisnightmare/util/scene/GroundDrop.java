package net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.scene;

import net.runelite.api.Tile;
import net.runelite.api.TileItem;

/** One ground item and the tile it lies on. */
public final class GroundDrop
{
    private final Tile tile;
    private final TileItem item;

    public GroundDrop(Tile tile, TileItem item)
    {
        this.tile = tile;
        this.item = item;
    }

    public Tile getTile()
    {
        return tile;
    }

    public TileItem getItem()
    {
        return item;
    }
}
