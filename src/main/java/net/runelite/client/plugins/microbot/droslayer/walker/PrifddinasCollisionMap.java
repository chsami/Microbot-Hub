package net.runelite.client.plugins.microbot.droslayer.walker;

/** Clear northern-forest passages captured on plane 0 in recording 20261023-204411-335. */
final class PrifddinasCollisionMap
{
    // Both endpoint flags were 0 in all three covering scene snapshots (145925/158548/171779 ms).
    // Observations in the eight-tile loading border are excluded, as in LiveCollisionCapture.
    // Only missing edges within two tiles of the recorded walk are included; gates, resource footprints,
    // the city's coordinate mirrors and unvisited cave floor are not inferred from these flags.
    private static final int[][] NORTHERN_PASSAGES = {
        {2129, 3420, 0}, {2129, 3421, 0}, {2129, 3421, 1}, {2129, 3422, 1},
        {2130, 3421, 0}, {2130, 3422, 0}, {2130, 3424, 0}, {2130, 3424, 1},
        {2130, 3425, 1}, {2131, 3423, 0}, {2131, 3424, 0}, {2131, 3424, 1},
        {2131, 3425, 1}, {2132, 3424, 0}, {2132, 3424, 1}, {2132, 3425, 0},
        {2132, 3425, 1}, {2133, 3424, 0}, {2133, 3425, 1}, {2134, 3425, 0},
        {2135, 3426, 1}, {2136, 3426, 1}
    };

    private PrifddinasCollisionMap() { }

    static void patch(String region, byte[] flags)
    {
        if (!"33_53".equals(region)) return;
        for (int[] edge : NORTHERN_PASSAGES)
        {
            int bit = ((edge[1] % 64) * 64 + edge[0] % 64) * 2 + edge[2];
            if (bit / 8 >= flags.length)
                throw new IllegalArgumentException("Missing recorded Prifddinas collision data");
            flags[bit / 8] |= 1 << (bit % 8);
        }
    }
}
