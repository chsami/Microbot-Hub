package net.runelite.client.plugins.microbot.droefficientwalker;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import net.runelite.api.Client;
import net.runelite.api.IndexDataBase;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.worldmap.WorldMap;
import net.runelite.api.worldmap.WorldMapData;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WalkerWorldMapDestinationTest
{
    @Test void translatesSingleRegionAndSourcePlane()
    {
        assertEquals(new WorldPoint(641, 1282, 2), translate(area("Area", region()), 1921, 2562));
    }

    @Test void translatesRegionRectangle()
    {
        byte[] section = section(0, 1, 1, 10, 20, 11, 22, 30, 40, 31, 42);
        assertEquals(new WorldPoint(767, 1471, 1), translate(area("Area", section), 2047, 2751));
    }

    @Test void translatesSingleChunk()
    {
        assertEquals(new WorldPoint(663, 1311, 3), translate(area("Area", chunks(false)), 1967, 2607));
    }

    @Test void translatesChunkRectangle()
    {
        assertEquals(new WorldPoint(671, 1319, 3), translate(area("Area", chunks(true)), 1975, 2615));
    }

    @Test void validatesDisplayBoundsOnEveryEdge()
    {
        byte[] data = area("Area", region());
        assertNull(translate(data, 1919, 2560));
        assertNull(translate(data, 1984, 2560));
        assertNull(translate(data, 1920, 2559));
        assertNull(translate(data, 1920, 2624));
        assertEquals(new WorldPoint(703, 1343, 2), translate(data, 1983, 2623));
    }

    @Test void rejectsUnsupportedSection()
    {
        assertNull(translate(area("Area", new byte[]{4, 0, 1}), 1920, 2560));
    }

    @Test void rejectsTruncationAndTrailingBytes()
    {
        byte[] data = area("Area", region());
        for (int length = 0; length < data.length; length++)
            assertNull(translate(Arrays.copyOf(data, length), 1920, 2560));
        assertNull(translate(Arrays.copyOf(data, data.length + 1), 1920, 2560));
    }

    @Test void rejectsOverlappingSections()
    {
        assertNull(translate(area("Area", region(), region()), 1920, 2560));
    }

    @Test void rejectsInvalidPlaneAndChunk()
    {
        assertNull(translate(area("Area", section(1, 4, 1, 10, 20, 30, 40)), 1920, 2560));
        byte[] chunk = chunks(false); chunk[5] = 8;
        assertNull(translate(area("Area", chunk), 1967, 2607));
    }

    @Test void rejectsMismatchedRectangleSizes()
    {
        assertNull(translate(area("Area", section(0, 1, 1, 10, 20, 11, 22, 30, 40, 32, 42)), 1920, 2560));
    }

    @Test void resolvesOnlySelectedAreaAndIgnoresUnselectedSectionFormat()
    {
        IndexDataBase index = index(area("Other", new byte[]{99}), area("Area", region()));
        assertEquals(new WorldPoint(640, 1280, 2), WalkerWorldMapDestination.resolve(index, "<col=ffffff>Area</col>", 1920, 2560));
    }

    @Test void rejectsDuplicateAreaNames()
    {
        assertNull(WalkerWorldMapDestination.resolve(index(area("Area", region()), area("Area", region())), "Area", 1920, 2560));
    }

    @Test void rejectsMissingOrUnloadedSelection()
    {
        assertNull(WalkerWorldMapDestination.resolve(index(area("Other", region())), "Area", 1920, 2560));
        assertNull(WalkerWorldMapDestination.resolve(index(area("Area", region()), null), "Area", 1920, 2560));
        assertNull(WalkerWorldMapDestination.resolve(index(area("Area", region())), "", 1920, 2560));
    }

    @Test void checksLiveSelectedMapDisplayBounds()
    {
        Client client = mock(Client.class);
        WorldMap map = mock(WorldMap.class);
        WorldMapData data = mock(WorldMapData.class);
        Widget selection = mock(Widget.class);
        when(client.getWorldMap()).thenReturn(map);
        when(map.getWorldMapData()).thenReturn(data);
        when(client.getWidget(InterfaceID.Worldmap.MAPLIST_DISPLAY)).thenReturn(selection);
        when(selection.getText()).thenReturn("Area");
        IndexDataBase cache = index(area("Area", region()));
        when(client.getIndex(19)).thenReturn(cache);
        WorldPoint display = new WorldPoint(1920, 2560, 0);
        assertNull(WalkerWorldMapDestination.resolve(client, display));
        when(data.surfaceContainsPosition(1920, 2560)).thenReturn(true);
        assertEquals(new WorldPoint(640, 1280, 2), WalkerWorldMapDestination.resolve(client, display));
    }

    @Test void rejectedMapClickDoesNotCancelJourney() throws Exception
    {
        EfficientWalkerPlugin plugin = new EfficientWalkerPlugin();
        Client client = mock(Client.class);
        EfficientWalker walker = mock(EfficientWalker.class);
        Widget map = mock(Widget.class);
        set(plugin, "client", client); set(plugin, "walker", walker);
        when(client.getMouseCanvasPosition()).thenReturn(new net.runelite.api.Point(5, 5));
        when(client.getWidget(38993927)).thenReturn(map);
        when(map.getBounds()).thenReturn(new java.awt.Rectangle(0, 0, 100, 100));
        plugin.onMenuOpened(new net.runelite.api.events.MenuOpened());
        verifyNoInteractions(walker);
        verify(client, never()).createMenuEntry(anyInt());
    }

    private static void set(Object target, String name, Object value) throws Exception
    {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true); field.set(target, value);
    }

    private static WorldPoint translate(byte[] data, int x, int y)
    {
        return WalkerWorldMapDestination.translate(data, x, y);
    }

    private static IndexDataBase index(byte[]... data)
    {
        IndexDataBase index = mock(IndexDataBase.class);
        int[] ids = new int[data.length];
        for (int i = 0; i < data.length; i++) { ids[i] = i; when(index.loadData(0, i)).thenReturn(data[i]); }
        when(index.getFileIds(0)).thenReturn(ids);
        return index;
    }

    private static byte[] region() { return section(1, 2, 1, 10, 20, 30, 40); }

    private static byte[] section(int type, int plane, int planes, int... regions)
    {
        return bytes(out -> { out.writeByte(type); out.writeByte(plane); out.writeByte(planes);
            for (int region : regions) out.writeShort(region); });
    }

    private static byte[] chunks(boolean rectangle)
    {
        return bytes(out -> {
            out.writeByte(rectangle ? 2 : 3); out.writeByte(3); out.writeByte(1);
            int[] regions = {10, 20, 30, 40}; int[] chunks = {2, 3, 5, 5};
            for (int i = 0; i < 4; i++) {
                out.writeShort(regions[i]); out.writeByte(chunks[i]);
                if (rectangle) out.writeByte(chunks[i] + 1);
            }
        });
    }

    private static byte[] area(String name, byte[]... sections)
    {
        return bytes(out -> {
            out.writeBytes("internal\0" + name + "\0");
            out.writeInt(0); out.writeInt(0); out.writeInt(0);
            out.writeByte(0); out.writeByte(0); out.writeByte(50); out.writeByte(sections.length);
            for (byte[] section : sections) out.write(section);
        });
    }

    private interface Write { void write(DataOutputStream out) throws IOException; }
    private static byte[] bytes(Write write)
    {
        try { ByteArrayOutputStream bytes = new ByteArrayOutputStream(); write.write(new DataOutputStream(bytes)); return bytes.toByteArray(); }
        catch (IOException ex) { throw new AssertionError(ex); }
    }
}
