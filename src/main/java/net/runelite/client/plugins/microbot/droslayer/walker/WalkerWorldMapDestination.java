package net.runelite.client.plugins.microbot.droslayer.walker;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import net.runelite.api.Client;
import net.runelite.api.IndexDataBase;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

/** Converts map display tiles only; public Walker destinations are already source coordinates. */
final class WalkerWorldMapDestination
{
    private static final Charset CP1252 = Charset.forName("windows-1252");

    private WalkerWorldMapDestination() { }

    static WorldPoint resolve(Client client, WorldPoint display)
    {
        Widget selection = client.getWidget(InterfaceID.Worldmap.MAPLIST_DISPLAY);
        if (display == null || selection == null || client.getWorldMap() == null
            || client.getWorldMap().getWorldMapData() == null
            || !client.getWorldMap().getWorldMapData().surfaceContainsPosition(display.getX(), display.getY()))
        {
            return null;
        }
        return resolve(client.getIndex(19), selection.getText(), display.getX(), display.getY());
    }

    static WorldPoint resolve(IndexDataBase index, String selectedName, int x, int y)
    {
        if (index == null || selectedName == null) return null;
        String name = Text.removeTags(selectedName).trim();
        if (name.isEmpty()) return null;
        try
        {
            int[] files = index.getFileIds(0); // worldmap/details
            if (files == null) return null;
            byte[] selected = null;
            // Index names first. Unselected areas need not use a supported section format.
            for (int file : files)
            {
                byte[] data = index.loadData(0, file);
                if (data == null) return null; // An unloaded name could be an ambiguous match.
                ByteBuffer in = ByteBuffer.wrap(data);
                string(in); // internal name
                String externalName = string(in);
                if (name.equals(externalName))
                {
                    if (selected != null) return null;
                    selected = data;
                }
            }
            return selected == null ? null : translate(selected, x, y);
        }
        catch (RuntimeException malformedOrUnavailable)
        {
            return null;
        }
    }

    /** Layout and section tags follow platform/cache's WorldMapLoader. */
    static WorldPoint translate(byte[] data, int x, int y)
    {
        try
        {
            ByteBuffer in = ByteBuffer.wrap(data);
            string(in);
            string(in);
            in.getInt(); // origin
            in.getInt(); // empty tile colour
            in.getInt(); // background colour
            u8(in); // reserved
            u8(in); // main surface flag
            u8(in); // zoom
            int count = u8(in);
            WorldPoint result = null;
            for (int i = 0; i < count; i++)
            {
                int type = u8(in);
                int plane = u8(in);
                int planes = u8(in);
                if (plane > 3 || planes < 1 || plane + planes > 4) return null;
                int sx, sy, ex, ey, dx, dy, deX, deY;
                switch (type)
                {
                    case 0: // rectangular regions (WorldMapType1)
                        sx = region(in); sy = region(in);
                        ex = region(in) + 63; ey = region(in) + 63;
                        dx = region(in); dy = region(in);
                        deX = region(in) + 63; deY = region(in) + 63;
                        break;
                    case 1: // single region (WorldMapType2)
                        sx = region(in); sy = region(in);
                        dx = region(in); dy = region(in);
                        ex = sx + 63; ey = sy + 63;
                        deX = dx + 63; deY = dy + 63;
                        break;
                    case 2: // rectangular chunks (WorldMapType3)
                        int srX = region(in); sx = srX + chunk(in); ex = srX + chunk(in) + 7;
                        int srY = region(in); sy = srY + chunk(in); ey = srY + chunk(in) + 7;
                        int drX = region(in); dx = drX + chunk(in); deX = drX + chunk(in) + 7;
                        int drY = region(in); dy = drY + chunk(in); deY = drY + chunk(in) + 7;
                        break;
                    case 3: // single chunk (WorldMapType0)
                        sx = region(in) + chunk(in); sy = region(in) + chunk(in);
                        dx = region(in) + chunk(in); dy = region(in) + chunk(in);
                        ex = sx + 7; ey = sy + 7;
                        deX = dx + 7; deY = dy + 7;
                        break;
                    default:
                        return null;
                }
                if (ex < sx || ey < sy || deX < dx || deY < dy
                    || ex - sx != deX - dx || ey - sy != deY - dy) return null;
                if (x >= dx && x <= deX && y >= dy && y <= deY)
                {
                    // Multiple matching sections are ambiguous, even if they share an origin.
                    if (result != null) return null;
                    result = new WorldPoint(sx + x - dx, sy + y - dy, plane);
                }
            }
            return in.hasRemaining() ? null : result;
        }
        catch (RuntimeException malformed)
        {
            return null;
        }
    }

    private static int u8(ByteBuffer in) { return in.get() & 255; }

    private static int region(ByteBuffer in)
    {
        int region = in.getShort() & 65535;
        if (region > 255) throw new IllegalArgumentException("Invalid map region");
        return region * 64;
    }

    private static int chunk(ByteBuffer in)
    {
        int chunk = u8(in);
        if (chunk > 7) throw new IllegalArgumentException("Invalid map chunk");
        return chunk * 8;
    }

    private static String string(ByteBuffer in)
    {
        int start = in.position();
        while (in.get() != 0) { }
        return new String(in.array(), start, in.position() - start - 1, CP1252);
    }
}
