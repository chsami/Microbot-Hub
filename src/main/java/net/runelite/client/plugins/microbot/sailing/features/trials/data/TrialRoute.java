package net.runelite.client.plugins.microbot.sailing.features.trials.data;

import net.runelite.api.coords.WorldPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class TrialRoute {
        public TrialLocations Location;
        public TrialRanks Rank;
        public List<WorldPoint> Points;
        public List<ToadFlagColors> ToadOrder;
        public List<Integer> WindMoteIndices;
        public List<PortalDirection> PortalDirections;
        private List<WorldPoint> interpolatedPoints;
        private Map<Integer, Integer> originalToInterpolatedIndexMap;
        private boolean interpolationApplied = false;

        private static final double DEFAULT_SPACING = 5.0;

        public TrialRoute(TrialLocations location, TrialRanks rank, List<WorldPoint> points) {
                Location = location;
                Rank = rank;
                Points = points == null ? new ArrayList<>() : new ArrayList<>(points);
                ToadOrder = Collections.emptyList();
                WindMoteIndices = Collections.emptyList();
                PortalDirections = Collections.emptyList();
        }

        public TrialRoute(TrialLocations location, TrialRanks rank, List<WorldPoint> points, List<ToadFlagColors> toadOrder, List<Integer> windMoteIndices, List<PortalDirection> portalDirections) {
                this(location, rank, points);
                ToadOrder = toadOrder == null ? Collections.emptyList() : toadOrder;
                WindMoteIndices = windMoteIndices == null ? Collections.emptyList() : windMoteIndices;
                PortalDirections = portalDirections == null ? Collections.emptyList() : portalDirections;
        }

        public List<WorldPoint> getInterpolatedPoints() {
                if (!interpolationApplied) {
                        originalToInterpolatedIndexMap = new HashMap<>();
                        interpolatedPoints = interpolateRoute(Points, DEFAULT_SPACING, originalToInterpolatedIndexMap);
                        interpolationApplied = true;
                }
                return interpolatedPoints;
        }

        public int getInterpolatedIndex(int originalIndex) {
                if (!interpolationApplied) {
                        getInterpolatedPoints();
                }
                return originalToInterpolatedIndexMap.getOrDefault(originalIndex, originalIndex);
        }

        public static List<WorldPoint> interpolateRoute(List<WorldPoint> originalPoints, double spacing, Map<Integer, Integer> indexMapping) {
                if (originalPoints == null || originalPoints.size() < 2) {
                        if (indexMapping != null && originalPoints != null) {
                                for (int i = 0; i < originalPoints.size(); i++) {
                                        indexMapping.put(i, i);
                                }
                        }
                        return originalPoints == null ? new ArrayList<>() : new ArrayList<>(originalPoints);
                }

                List<WorldPoint> result = new ArrayList<>();
                result.add(originalPoints.get(0));
                if (indexMapping != null) {
                        indexMapping.put(0, 0);
                }

                for (int i = 0; i < originalPoints.size() - 1; i++) {
                        WorldPoint current = originalPoints.get(i);
                        WorldPoint next = originalPoints.get(i + 1);

                        List<WorldPoint> segment = interpolateSegment(current, next, spacing);
                        for (int j = 1; j < segment.size(); j++) {
                                result.add(segment.get(j));
                        }

                        if (indexMapping != null) {
                                indexMapping.put(i + 1, result.size() - 1);
                        }
                }

                return result;
        }

        private static List<WorldPoint> interpolateSegment(WorldPoint from, WorldPoint to, double spacing) {
                List<WorldPoint> points = new ArrayList<>();
                points.add(from);

                double dx = to.getX() - from.getX();
                double dy = to.getY() - from.getY();
                double distance = Math.sqrt(dx * dx + dy * dy);

                if (distance <= spacing) {
                        points.add(to);
                        return points;
                }

                int numSegments = (int) Math.ceil(distance / spacing);
                double stepX = dx / numSegments;
                double stepY = dy / numSegments;

                for (int i = 1; i < numSegments; i++) {
                        int x = from.getX() + (int) Math.round(stepX * i);
                        int y = from.getY() + (int) Math.round(stepY * i);
                        points.add(new WorldPoint(x, y, from.getPlane()));
                }

                points.add(to);
                return points;
        }

        @Override
        public boolean equals(Object o) {
                if (this == o)
                        return true;
                if (o == null || getClass() != o.getClass())
                        return false;
                TrialRoute that = (TrialRoute) o;
                return Location == that.Location && Rank == that.Rank;
        }

        @Override
        public int hashCode() {
                return Objects.hash(Location, Rank);
        }

        private static final List<WorldPoint> TemporTantrumSwordfishBestLine = new ArrayList<>(
                        List.of(
                                        new WorldPoint(3035, 2922, 0), // start
                                        new WorldPoint(3025, 2911, 0),
                                        new WorldPoint(3017, 2900, 0),
                                        new WorldPoint(2996, 2896, 0),
                                        new WorldPoint(2994, 2882, 0),
                                        new WorldPoint(2979, 2866, 0),
                                        new WorldPoint(2983, 2839, 0),
                                        new WorldPoint(2979, 2827, 0),
                                        new WorldPoint(2990, 2809, 0),
                                        new WorldPoint(3001, 2787, 0),
                                        new WorldPoint(3013, 2769, 0),
                                        new WorldPoint(3022, 2762, 0),
                                        new WorldPoint(3039, 2760, 0),
                                        new WorldPoint(3056, 2763, 0),
                                        new WorldPoint(3054, 2763, 0),
                                        new WorldPoint(3057, 2792, 0),
                                        new WorldPoint(3065, 2811, 0),
                                        new WorldPoint(3078, 2827, 0),
                                        new WorldPoint(3078, 2864, 0),
                                        new WorldPoint(3084, 2875, 0),
                                        new WorldPoint(3091, 2887, 0),
                                        new WorldPoint(3072, 2916, 0),
                                        new WorldPoint(3052, 2920, 0),
                                        new WorldPoint(3035, 2922, 0) // end
                        ));

        private static final List<WorldPoint> TemporTantrumSharkBestLine = new ArrayList<>(
                        List.of(
                                        new WorldPoint(3035, 2922, 0), // start
                                        new WorldPoint(3017, 2898, 0),
                                        new WorldPoint(3017, 2889, 0),
                                        new WorldPoint(3001, 2869, 0),
                                        new WorldPoint(3002, 2858, 0),
                                        new WorldPoint(3004, 2827, 0),
                                        new WorldPoint(3009, 2816, 0),
                                        new WorldPoint(3019, 2814, 0),
                                        new WorldPoint(3027, 2798, 0),
                                        new WorldPoint(3039, 2778, 0),
                                        new WorldPoint(3039, 2773, 0), // approach the rum boat
                                        new WorldPoint(3045, 2777, 0),
                                        new WorldPoint(3057, 2792, 0),
                                        new WorldPoint(3069, 2814, 0),
                                        new WorldPoint(3076, 2825, 0),
                                        new WorldPoint(3080, 2864, 0), // pass through the lost supplies
                                        new WorldPoint(3082, 2873, 0),
                                        new WorldPoint(3076, 2883, 0),
                                        new WorldPoint(3077, 2896, 0),
                                        new WorldPoint(3060, 2906, 0),
                                        new WorldPoint(3040, 2921, 0),
                                        new WorldPoint(3027, 2913, 0),
                                        new WorldPoint(3013, 2910, 0),
                                        new WorldPoint(2994, 2896, 0),
                                        new WorldPoint(2994, 2882, 0),
                                        new WorldPoint(2977, 2865, 0),
                                        new WorldPoint(2982, 2847, 0),
                                        new WorldPoint(2979, 2830, 0),
                                        new WorldPoint(2991, 2806, 0),
                                        new WorldPoint(3014, 2763, 0),
                                        new WorldPoint(3038, 2758, 0),
                                        new WorldPoint(3054, 2761, 0),
                                        new WorldPoint(3066, 2768, 0),
                                        new WorldPoint(3075, 2776, 0),
                                        new WorldPoint(3084, 2801, 0),
                                        new WorldPoint(3081, 2813, 0),
                                        new WorldPoint(3094, 2828, 0),
                                        new WorldPoint(3093, 2843, 0),
                                        new WorldPoint(3093, 2864, 0),
                                        new WorldPoint(3100, 2872, 0),
                                        new WorldPoint(3092, 2884, 0),
                                        new WorldPoint(3073, 2916, 0),
                                        new WorldPoint(3053, 2921, 0),
                                        new WorldPoint(3035, 2922, 0) // end
                        ));

        private static final List<WorldPoint> TemporTantrumMarlinBestLine = new ArrayList<>(
                        List.of(
                                        // Trial Route:
                                        /*0*/new WorldPoint(3035, 2922, 0),
                                        /*1*/new WorldPoint(3017, 2898, 0),
                                        /*2*/new WorldPoint(3017, 2889, 0),
                                        /*3*/new WorldPoint(3001, 2869, 0),
                                        /*4*/new WorldPoint(3002, 2858, 0),
                                        /*5*/new WorldPoint(3004, 2827, 0),
                                        /*6*/new WorldPoint(3009, 2816, 0),
                                        /*7*/new WorldPoint(3019, 2814, 0),
                                        /*8*/new WorldPoint(3030, 2815, 0),
                                        /*9*/new WorldPoint(3027, 2798, 0),
                                        /*10*/new WorldPoint(3039, 2778, 0),
                                        /*11*/new WorldPoint(3045, 2777, 0),
                                        /*12*/new WorldPoint(3057, 2792, 0),
                                        /*13*/new WorldPoint(3069, 2814, 0),
                                        /*14*/new WorldPoint(3076, 2825, 0),
                                        /*15*/new WorldPoint(3078, 2863, 0),
                                        /*16*/new WorldPoint(3082, 2873, 0),
                                        /*17*/new WorldPoint(3073, 2875, 0),
                                        /*18*/new WorldPoint(3060, 2882, 0),
                                        /*19*/new WorldPoint(3060, 2906, 0),
                                        /*20*/new WorldPoint(3035, 2917, 0),
                                        /*21*/new WorldPoint(3027, 2913, 0),
                                        /*22*/new WorldPoint(3013, 2910, 0),
                                        /*23*/new WorldPoint(2994, 2896, 0),
                                        /*24*/new WorldPoint(2994, 2882, 0),
                                        /*25*/new WorldPoint(2977, 2865, 0),
                                        /*26*/new WorldPoint(2982, 2847, 0),
                                        /*27*/new WorldPoint(2979, 2830, 0),
                                        /*28*/new WorldPoint(2991, 2806, 0),
                                        /*29*/new WorldPoint(3016, 2776, 0),
                                        /*30*/new WorldPoint(3038, 2771, 0),
                                        /*31*/new WorldPoint(3045, 2776, 0),
                                        /*32*/new WorldPoint(3066, 2768, 0),
                                        /*33*/new WorldPoint(3075, 2776, 0),
                                        /*34*/new WorldPoint(3084, 2801, 0),
                                        /*35*/new WorldPoint(3081, 2813, 0),
                                        /*36*/new WorldPoint(3094, 2828, 0),
                                        /*37*/new WorldPoint(3093, 2843, 0),
                                        /*38*/new WorldPoint(3093, 2864, 0),
                                        /*39*/new WorldPoint(3100, 2872, 0),
                                        /*40*/new WorldPoint(3092, 2884, 0),
                                        /*41*/new WorldPoint(3073, 2916, 0),
                                        /*42*/new WorldPoint(3053, 2921, 0),
                                        /*43*/new WorldPoint(3035, 2922, 0),
                                        /*44*/new WorldPoint(3012, 2910, 0),
                                        /*45*/new WorldPoint(2993, 2895, 0),
                                        /*46*/new WorldPoint(2979, 2883, 0),
                                        /*47*/new WorldPoint(2962, 2882, 0),
                                        /*48*/new WorldPoint(2956, 2872, 0),
                                        /*49*/new WorldPoint(2965, 2865, 0),
                                        /*50*/new WorldPoint(2966, 2851, 0),
                                        /*51*/new WorldPoint(2958, 2840, 0),
                                        /*52*/new WorldPoint(2953, 2810, 0),
                                        /*53*/new WorldPoint(2968, 2794, 0),
                                        /*54*/new WorldPoint(2983, 2787, 0),
                                        /*55*/new WorldPoint(2987, 2777, 0),
                                        /*56*/new WorldPoint(3004, 2768, 0),
                                        /*57*/new WorldPoint(3036, 2761, 0), // final lap supplies
                                        new WorldPoint(3046, 2773, 0), // final lap approach
                                        /*58*/new WorldPoint(3056, 2761, 0),
                                        /*59*/new WorldPoint(3068, 2766, 0),
                                        /*60*/new WorldPoint(3090, 2764, 0),
                                        /*61*/new WorldPoint(3098, 2774, 0),
                                        /*62*/new WorldPoint(3103, 2797, 0),
                                        /*63*/new WorldPoint(3110, 2825, 0),
                                        /*64*/new WorldPoint(3118, 2836, 0),
                                        /*65*/new WorldPoint(3117, 2850, 0),
                                        /*66*/new WorldPoint(3121, 2864, 0),
                                        /*67*/new WorldPoint(3103, 2878, 0),
                                        /*68*/new WorldPoint(3082, 2900, 0),
                                        /*69*/new WorldPoint(3072, 2917, 0),
                                        /*70*/new WorldPoint(3059, 2921, 0),
                                        /*71*/new WorldPoint(3035, 2922, 0)
                        // End of trial route
                        ));

        private static final List<WorldPoint> JubblySwordfishBestLine = new ArrayList<>(
                        List.of(
                                        new WorldPoint(2436, 3018, 0),
                                        new WorldPoint(2423, 3012, 0),
                                        new WorldPoint(2413, 3015, 0),
                                        new WorldPoint(2396, 3010, 0),
                                        new WorldPoint(2373, 3008, 0),
                                        new WorldPoint(2357, 2991, 0),
                                        new WorldPoint(2353, 2979, 0),
                                        new WorldPoint(2342, 2974, 0),
                                        new WorldPoint(2323, 2976, 0),
                                        new WorldPoint(2309, 2974, 0),
                                        new WorldPoint(2285, 2980, 0),
                                        new WorldPoint(2267, 2990, 0),
                                        new WorldPoint(2251, 2995, 0),
                                        new WorldPoint(2239, 3005, 0),
                                        new WorldPoint(2239, 3016, 0),
                                        new WorldPoint(2252, 3025, 0),
                                        new WorldPoint(2261, 3021, 0),
                                        new WorldPoint(2281, 2999, 0),
                                        new WorldPoint(2298, 3002, 0),
                                        new WorldPoint(2300, 3014, 0),
                                        new WorldPoint(2311, 3021, 0),
                                        new WorldPoint(2352, 3004, 0),
                                        new WorldPoint(2360, 2999, 0),
                                        new WorldPoint(2358, 2969, 0),
                                        new WorldPoint(2358, 2960, 0),
                                        new WorldPoint(2374, 2940, 0),
                                        new WorldPoint(2428, 2939, 0),
                                        new WorldPoint(2435, 2949, 0),
                                        new WorldPoint(2436, 2985, 0),
                                        new WorldPoint(2437, 2990, 0),
                                        new WorldPoint(2433, 3005, 0),
                                        new WorldPoint(2436, 3018, 0)//end
                        ));

        private static final List<ToadFlagColors> JubblySwordfishToadOrder = List.of(
                        ToadFlagColors.Orange,
                        ToadFlagColors.Teal,
                        ToadFlagColors.Pink,
                        ToadFlagColors.White//end
        );

        private static final List<WorldPoint> JubblySharkBestLine = new ArrayList<>(
                        List.of(
                                        new WorldPoint(2436, 3018, 0),
                                        new WorldPoint(2422, 3012, 0),
                                        new WorldPoint(2413, 3016, 0),
                                        new WorldPoint(2402, 3017, 0),
                                        new WorldPoint(2395, 3010, 0),
                                        new WorldPoint(2378, 3008, 0),
                                        new WorldPoint(2362, 2998, 0),
                                        new WorldPoint(2351, 2979, 0),
                                        new WorldPoint(2340, 2973, 0),
                                        new WorldPoint(2330, 2974, 0),
                                        new WorldPoint(2299, 2975, 0),
                                        new WorldPoint(2276, 2984, 0),
                                        new WorldPoint(2263, 2992, 0),
                                        new WorldPoint(2250, 2993, 0),
                                        new WorldPoint(2239, 3007, 0), // collect toad
                                        new WorldPoint(2240, 3016, 0),
                                        new WorldPoint(2250, 3023, 0),
                                        new WorldPoint(2253, 3025, 0),
                                        new WorldPoint(2261, 3021, 0),
                                        new WorldPoint(2278, 3001, 0),
                                        new WorldPoint(2295, 3000, 0), // click yellow outcrop
                                        new WorldPoint(2299, 3007, 0),
                                        new WorldPoint(2302, 3017, 0), // click red outcrop
                                        new WorldPoint(2310, 3021, 0),
                                        new WorldPoint(2329, 3016, 0),
                                        new WorldPoint(2339, 3004, 0),
                                        new WorldPoint(2345, 2990, 0),
                                        new WorldPoint(2359, 2974, 0),
                                        new WorldPoint(2358, 2965, 0),
                                        new WorldPoint(2365, 2948, 0), // click yellow outcrop
                                        new WorldPoint(2373, 2939, 0),
                                        new WorldPoint(2386, 2940, 0),
                                        new WorldPoint(2399, 2939, 0),
                                        new WorldPoint(2420, 2938, 0), // click green outcrop
                                        new WorldPoint(2426, 2936, 0),
                                        new WorldPoint(2434, 2949, 0),
                                        new WorldPoint(2434, 2969, 0),
                                        new WorldPoint(2438, 2989, 0),
                                        new WorldPoint(2438, 2989, 0), // click pink outcrop
                                        new WorldPoint(2434, 2998, 0),
                                        new WorldPoint(2432, 3021, 0),
                                        new WorldPoint(2413, 3026, 0), // click white outcrop
                                        new WorldPoint(2402, 3021, 0),
                                        new WorldPoint(2394, 3020, 0),
                                        new WorldPoint(2382, 3025, 0),
                                        new WorldPoint(2370, 3022, 0),
                                        new WorldPoint(2357, 3025, 0),
                                        new WorldPoint(2340, 3031, 0),
                                        new WorldPoint(2333, 3028, 0),
                                        new WorldPoint(2327, 3016, 0),
                                        new WorldPoint(2339, 3006, 0),
                                        new WorldPoint(2353, 3005, 0), // click blue outcrop
                                        new WorldPoint(2379, 2993, 0),
                                        new WorldPoint(2384, 2985, 0),
                                        new WorldPoint(2379, 2974, 0),
                                        new WorldPoint(2388, 2959, 0), // click orange outcrop
                                        new WorldPoint(2403, 2951, 0),
                                        new WorldPoint(2413, 2955, 0),
                                        new WorldPoint(2420, 2959, 0), // click teal outcrop
                                        new WorldPoint(2424, 2974, 0),
                                        new WorldPoint(2418, 2988, 0), // click pink outcrop
                                        new WorldPoint(2414, 2993, 0),
                                        new WorldPoint(2417, 3003, 0), //click white outcrop
                                        new WorldPoint(2436, 3023, 0) // end
                        ));

        private static final List<ToadFlagColors> JubblySharkToadOrder = List.of(
                        ToadFlagColors.Yellow,
                        ToadFlagColors.Red,
                        ToadFlagColors.Orange,
                        ToadFlagColors.Teal,
                        ToadFlagColors.Pink,
                        ToadFlagColors.White,
                        ToadFlagColors.Blue,
                        ToadFlagColors.Orange,
                        ToadFlagColors.Teal,
                        ToadFlagColors.Pink,
                        ToadFlagColors.White //fin
        );

        private static final List<WorldPoint> JubblyMarlinBestLine = new ArrayList<>(
                        List.of(
                                        /*0*/new WorldPoint(2436, 3018, 0),
                                        /*1*/new WorldPoint(2424, 3025, 0),
                                        /*2*/new WorldPoint(2412, 3026, 0),
                                        /*3*/new WorldPoint(2405, 3023, 0),
                                        /*4*/new WorldPoint(2400, 3011, 0),
                                        /*5*/new WorldPoint(2396, 3009, 0),
                                        /*6*/new WorldPoint(2373, 3009, 0),
                                        /*7*/new WorldPoint(2350, 2977, 0),
                                        /*8*/new WorldPoint(2332, 2974, 0),
                                        /*9*/new WorldPoint(2303, 2975, 0),
                                        /*10*/new WorldPoint(2281, 2980, 0),
                                        /*11*/new WorldPoint(2265, 2991, 0),
                                        /*12*/new WorldPoint(2251, 2994, 0),
                                        /*13*/new WorldPoint(2248, 3000, 0),
                                        /*14*/new WorldPoint(2268, 3013, 0),
                                        /*15*/new WorldPoint(2280, 3000, 0),
                                        /*16*/new WorldPoint(2298, 3001, 0),
                                        /*17*/new WorldPoint(2302, 3017, 0),
                                        /*18*/new WorldPoint(2316, 3023, 0),
                                        /*19*/new WorldPoint(2350, 2981, 0),
                                        /*20*/new WorldPoint(2359, 2958, 0),
                                        /*21*/new WorldPoint(2375, 2936, 0),
                                        /*22*/new WorldPoint(2387, 2940, 0),
                                        /*23*/new WorldPoint(2420, 2939, 0),
                                        /*24*/new WorldPoint(2434, 2942, 0),
                                        /*25*/new WorldPoint(2435, 2967, 0),
                                        /*26*/new WorldPoint(2435, 2986, 0),
                                        /*27*/new WorldPoint(2437, 2991, 0),
                                        /*28*/new WorldPoint(2433, 3004, 0),
                                        /*29*/new WorldPoint(2435, 3010, 0),
                                        /*30*/new WorldPoint(2422, 3012, 0),
                                        /*31*/new WorldPoint(2415, 3000, 0),
                                        /*32*/new WorldPoint(2414, 2990, 0),
                                        /*33*/new WorldPoint(2423, 2978, 0),
                                        /*34*/new WorldPoint(2421, 2964, 0),
                                        /*35*/new WorldPoint(2417, 2957, 0),
                                        /*36*/new WorldPoint(2405, 2950, 0),
                                        /*37*/new WorldPoint(2390, 2957, 0),
                                        /*38*/new WorldPoint(2380, 2974, 0),
                                        /*39*/new WorldPoint(2384, 2985, 0),
                                        /*40*/new WorldPoint(2384, 2989, 0),
                                        /*41*/new WorldPoint(2369, 2997, 0),
                                        /*42*/new WorldPoint(2359, 2991, 0),
                                        /*43*/new WorldPoint(2350, 2977, 0),
                                        /*44*/new WorldPoint(2340, 2974, 0),
                                        /*45*/new WorldPoint(2305, 2974, 0),
                                        /*46*/new WorldPoint(2288, 2980, 0),
                                        /*47*/new WorldPoint(2278, 2981, 0),
                                        /*48*/new WorldPoint(2268, 2990, 0),
                                        /*49*/new WorldPoint(2256, 2992, 0),
                                        /*50*/new WorldPoint(2238, 3006, 0),
                                        /*51*/new WorldPoint(2242, 3020, 0),
                                        /*52*/new WorldPoint(2251, 3025, 0),
                                        /*53*/new WorldPoint(2257, 3024, 0),
                                        /*54*/new WorldPoint(2280, 2999, 0),
                                        /*55*/new WorldPoint(2292, 2997, 0), //wind mote HERE
                                        /*56*/new WorldPoint(2312, 2987, 0),
                                        /*57*/new WorldPoint(2324, 2984, 0),
                                        /*58*/new WorldPoint(2333, 2977, 0),
                                        /*59*/new WorldPoint(2335, 2954, 0),
                                        /*60*/new WorldPoint(2345, 2931, 0),
                                        /*61*/new WorldPoint(2365, 2928, 0),
                                        /*62*/new WorldPoint(2378, 2942, 0),
                                        /*63*/new WorldPoint(2395, 2939, 0),
                                        /*64*/new WorldPoint(2400, 2927, 0),
                                        /*65*/new WorldPoint(2417, 2924, 0),
                                        /*66*/new WorldPoint(2427, 2921, 0),
                                        /*67*/new WorldPoint(2442, 2927, 0),
                                        /*68*/new WorldPoint(2454, 2930, 0),
                                        /*69*/new WorldPoint(2469, 2953, 0),
                                        /*70*/new WorldPoint(2447, 2974, 0),
                                        /*71*/new WorldPoint(2447, 2986, 0), //wind mote HERE
                                        /*72*/new WorldPoint(2445, 3009, 0),
                                        /*73*/new WorldPoint(2437, 3010, 0),
                                        /*74*/new WorldPoint(2434, 3007, 0),
                                        /*75*/new WorldPoint(2403, 3017, 0),
                                        /*76*/new WorldPoint(2395, 3020, 0),
                                        /*77*/new WorldPoint(2387, 3020, 0),
                                        /*78*/new WorldPoint(2378, 3026, 0),
                                        /*79*/new WorldPoint(2371, 3022, 0),
                                        /*80*/new WorldPoint(2355, 3023, 0),
                                        /*81*/new WorldPoint(2343, 3031, 0),
                                        /*82*/new WorldPoint(2329, 3030, 0),
                                        /*83*/new WorldPoint(2313, 3045, 0),
                                        /*84*/new WorldPoint(2304, 3038, 0),
                                        /*85*/new WorldPoint(2313, 3025, 0),
                                        /*86*/new WorldPoint(2341, 3007, 0),
                                        /*87*/new WorldPoint(2355, 3005, 0),
                                        /*88*/new WorldPoint(2361, 3000, 0),
                                        /*89*/new WorldPoint(2390, 2986, 0),
                                        /*90*/new WorldPoint(2418, 2961, 0),
                                        /*91*/new WorldPoint(2430, 2953, 0), //shoot teal
                                        /*92*/new WorldPoint(2435, 2965, 0),
                                        /*93*/new WorldPoint(2433, 3000, 0),
                                        new WorldPoint(2436, 3023, 0) // end
                        ));

        private static final List<ToadFlagColors> JubblyMarlinToadOrder = List.of(
                        ToadFlagColors.Yellow,
                        ToadFlagColors.Red,
                        ToadFlagColors.Orange,
                        ToadFlagColors.Teal,
                        ToadFlagColors.Pink,
                        ToadFlagColors.White,
                        ToadFlagColors.Teal,
                        ToadFlagColors.Orange,
                        ToadFlagColors.Blue,
                        ToadFlagColors.Yellow,
                        ToadFlagColors.Orange,
                        ToadFlagColors.White,
                        ToadFlagColors.Pink,
                        ToadFlagColors.Red,
                        ToadFlagColors.Blue,
                        ToadFlagColors.Teal,
                        ToadFlagColors.Pink,
                        ToadFlagColors.White//end    
        );

        private static final List<Integer> JubblyMarlinWindMoteIndices = List.of(13, 55, 71, 89, 90);

        private static final List<WorldPoint> GwenithGlideSwordfishBestLine = new ArrayList<>(
                        List.of(
                                        new WorldPoint(2257, 3459, 0),
                                        new WorldPoint(2255, 3469, 0),
                                        new WorldPoint(2260, 3474, 0),
                                        new WorldPoint(2271, 3477, 0),
                                        new WorldPoint(2274, 3487, 0),
                                        new WorldPoint(2260, 3494, 0), //white portal
                                        new WorldPoint(2093, 3233, 0), //after portal
                                        new WorldPoint(2103, 3230, 0),
                                        new WorldPoint(2111, 3234, 0),
                                        new WorldPoint(2118, 3231, 0),
                                        new WorldPoint(2128, 3233, 0),
                                        new WorldPoint(2130, 3253, 0),
                                        new WorldPoint(2133, 3263, 0),
                                        new WorldPoint(2127, 3275, 0),
                                        new WorldPoint(2121, 3278, 0),
                                        new WorldPoint(2121, 3289, 0),
                                        new WorldPoint(2131, 3297, 0),
                                        new WorldPoint(2148, 3297, 0),
                                        new WorldPoint(2157, 3293, 0), //white portal
                                        new WorldPoint(2260, 3509, 0),
                                        new WorldPoint(2266, 3518, 0),
                                        new WorldPoint(2263, 3531, 0),
                                        new WorldPoint(2250, 3542, 0),
                                        new WorldPoint(2252, 3558, 0),
                                        new WorldPoint(2254, 3571, 0),
                                        new WorldPoint(2242, 3574, 0), //blue portal
                                        new WorldPoint(2088, 3215, 0),
                                        new WorldPoint(2110, 3214, 0),
                                        new WorldPoint(2115, 3206, 0),
                                        new WorldPoint(2132, 3193, 0),
                                        new WorldPoint(2141, 3220, 0),
                                        new WorldPoint(2139, 3230, 0),
                                        new WorldPoint(2141, 3243, 0),
                                        new WorldPoint(2153, 3246, 0), //blue portal
                                        new WorldPoint(2203, 3574, 0),
                                        new WorldPoint(2191, 3567, 0),
                                        new WorldPoint(2194, 3547, 0),
                                        new WorldPoint(2201, 3535, 0),
                                        new WorldPoint(2198, 3514, 0), //green portal
                                        new WorldPoint(2105, 3140, 0),
                                        new WorldPoint(2092, 3145, 0),
                                        new WorldPoint(2078, 3158, 0),
                                        new WorldPoint(2070, 3161, 0),
                                        new WorldPoint(2069, 3175, 0),
                                        new WorldPoint(2058, 3185, 0),
                                        new WorldPoint(2073, 3210, 0),
                                        new WorldPoint(2100, 3205, 0),
                                        new WorldPoint(2128, 3172, 0)//end
                        ));

        private static final List<PortalDirection> GwenithGlideSwordfishPortalDirections = List.of(
                        new PortalDirection(5, PortalColors.White, Directions.East, Directions.SouthEast),
                        new PortalDirection(18, PortalColors.White, Directions.North, Directions.NorthEast),
                        new PortalDirection(25, PortalColors.Blue, Directions.East, Directions.East),
                        new PortalDirection(33, PortalColors.Blue, Directions.West, Directions.SouthWest),
                        new PortalDirection(38, PortalColors.Green, Directions.West, Directions.NorthWest),
                        new PortalDirection(47, PortalColors.Green, Directions.South, Directions.SouthWest),
                        new PortalDirection(52, PortalColors.Yellow, Directions.West, Directions.West),
                        new PortalDirection(62, PortalColors.Yellow, Directions.West, Directions.West),
                        new PortalDirection(71, PortalColors.Red, Directions.West, Directions.SouthWest)//
        //
        );

        private static final List<WorldPoint> GwenithGlideSharkBestLine = new ArrayList<>(
                        List.of(
                                        new WorldPoint(2257, 3459, 0),
                                        new WorldPoint(2255, 3469, 0),
                                        new WorldPoint(2260, 3474, 0),
                                        new WorldPoint(2271, 3477, 0),
                                        new WorldPoint(2274, 3487, 0),
                                        new WorldPoint(2260, 3494, 0), //white portal
                                        new WorldPoint(2093, 3233, 0), //after portal
                                        new WorldPoint(2103, 3230, 0),
                                        new WorldPoint(2111, 3234, 0),
                                        new WorldPoint(2118, 3231, 0),
                                        new WorldPoint(2128, 3233, 0),
                                        new WorldPoint(2130, 3253, 0),
                                        new WorldPoint(2133, 3263, 0),
                                        new WorldPoint(2127, 3275, 0),
                                        new WorldPoint(2121, 3278, 0),
                                        new WorldPoint(2121, 3289, 0),
                                        new WorldPoint(2131, 3297, 0),
                                        new WorldPoint(2148, 3297, 0),
                                        new WorldPoint(2157, 3293, 0), //white portal
                                        new WorldPoint(2260, 3509, 0),
                                        new WorldPoint(2266, 3518, 0),
                                        new WorldPoint(2263, 3531, 0),
                                        new WorldPoint(2250, 3542, 0),
                                        new WorldPoint(2252, 3558, 0),
                                        new WorldPoint(2254, 3571, 0),
                                        new WorldPoint(2242, 3574, 0), //blue portal
                                        new WorldPoint(2088, 3215, 0),
                                        new WorldPoint(2110, 3214, 0),
                                        new WorldPoint(2115, 3206, 0),
                                        new WorldPoint(2132, 3193, 0),
                                        new WorldPoint(2141, 3220, 0),
                                        new WorldPoint(2139, 3230, 0),
                                        new WorldPoint(2141, 3243, 0),
                                        new WorldPoint(2153, 3246, 0), //blue portal
                                        new WorldPoint(2203, 3574, 0),
                                        new WorldPoint(2191, 3567, 0),
                                        new WorldPoint(2194, 3547, 0),
                                        new WorldPoint(2201, 3535, 0),
                                        new WorldPoint(2198, 3514, 0), //green portal
                                        new WorldPoint(2105, 3140, 0),
                                        new WorldPoint(2092, 3145, 0),
                                        new WorldPoint(2078, 3158, 0),
                                        new WorldPoint(2070, 3161, 0),
                                        new WorldPoint(2069, 3175, 0),
                                        new WorldPoint(2058, 3185, 0),
                                        new WorldPoint(2073, 3210, 0),
                                        new WorldPoint(2100, 3205, 0),
                                        new WorldPoint(2128, 3172, 0), //green portal, end swordfish
                                        new WorldPoint(2198, 3497, 0),
                                        new WorldPoint(2192, 3480, 0),
                                        new WorldPoint(2177, 3474, 0),
                                        new WorldPoint(2171, 3465, 0),
                                        new WorldPoint(2158, 3464, 0), //yellow portal
                                        new WorldPoint(2115, 3373, 0),
                                        new WorldPoint(2100, 3372, 0),
                                        new WorldPoint(2087, 3377, 0),
                                        new WorldPoint(2079, 3389, 0),
                                        new WorldPoint(2094, 3397, 0),
                                        new WorldPoint(2104, 3406, 0),
                                        new WorldPoint(2085, 3413, 0),
                                        new WorldPoint(2078, 3423, 0),
                                        new WorldPoint(2098, 3437, 0),
                                        new WorldPoint(2116, 3439, 0), //yellow portal
                                        new WorldPoint(2143, 3464, 0),
                                        new WorldPoint(2110, 3464, 0),
                                        new WorldPoint(2105, 3481, 0),
                                        new WorldPoint(2106, 3493, 0),
                                        new WorldPoint(2125, 3495, 0),
                                        new WorldPoint(2135, 3480, 0), //secret crate!
                                        new WorldPoint(2151, 3490, 0),
                                        new WorldPoint(2149, 3503, 0),
                                        new WorldPoint(2160, 3508, 0), //red portal
                                        new WorldPoint(2248, 3634, 0),
                                        new WorldPoint(2240, 3628, 0),
                                        new WorldPoint(2231, 3617, 0),
                                        new WorldPoint(2229, 3608, 0),
                                        new WorldPoint(2229, 3599, 0),
                                        new WorldPoint(2216, 3593, 0),
                                        new WorldPoint(2190, 3597, 0),
                                        new WorldPoint(2167, 3589, 0),
                                        new WorldPoint(2141, 3597, 0),
                                        new WorldPoint(2123, 3598, 0),
                                        new WorldPoint(2100, 3583, 0),
                                        new WorldPoint(2100, 3583, 0),
                                        new WorldPoint(2104, 3574, 0),

                                        new WorldPoint(0, 0, 0)//end swordfish
                        ));

        private static final List<PortalDirection> GwenithGlideSharkPortalDirections = List.of(
                        new PortalDirection(5, PortalColors.White, Directions.East, Directions.SouthEast),
                        new PortalDirection(18, PortalColors.White, Directions.North, Directions.NorthEast),
                        new PortalDirection(25, PortalColors.Blue, Directions.East, Directions.East),
                        new PortalDirection(33, PortalColors.Blue, Directions.West, Directions.SouthWest),
                        new PortalDirection(38, PortalColors.Green, Directions.West, Directions.NorthWest),
                        new PortalDirection(47, PortalColors.Green, Directions.South, Directions.SouthWest),
                        new PortalDirection(52, PortalColors.Yellow, Directions.West, Directions.West),
                        new PortalDirection(62, PortalColors.Yellow, Directions.West, Directions.West),
                        new PortalDirection(71, PortalColors.Red, Directions.West, Directions.SouthWest)//
        );

        private static final List<WorldPoint> GwenithGlideMarlinBestLine = new ArrayList<>(
                        List.of(
                                        // Trial Route:
                                        /*0*/new WorldPoint(2257, 3459, 0),
                                        /*1*/new WorldPoint(2259, 3472, 0),
                                        /*2*/new WorldPoint(2269, 3476, 0),
                                        /*3*/new WorldPoint(2272, 3487, 0),
                                        /*4*/new WorldPoint(2260, 3496, 0),
                                        /*5*/new WorldPoint(2093, 3233, 0),
                                        /*6*/new WorldPoint(2097, 3231, 0),
                                        /*7*/new WorldPoint(2104, 3230, 0),
                                        /*8*/new WorldPoint(2117, 3232, 0),
                                        /*9*/new WorldPoint(2130, 3254, 0),
                                        /*10*/new WorldPoint(2132, 3269, 0),
                                        /*11*/new WorldPoint(2143, 3291, 0),
                                        /*12*/new WorldPoint(2157, 3293, 0),
                                        /*13*/new WorldPoint(2260, 3509, 0),
                                        /*14*/new WorldPoint(2267, 3519, 0),
                                        /*15*/new WorldPoint(2263, 3531, 0),
                                        /*16*/new WorldPoint(2250, 3542, 0),
                                        /*17*/new WorldPoint(2251, 3559, 0),
                                        /*19*/new WorldPoint(2242, 3574, 0),
                                        /*20*/new WorldPoint(2084, 3215, 0),
                                        /*21*/new WorldPoint(2107, 3214, 0),
                                        /*22*/new WorldPoint(2110, 3212, 0),
                                        /*23*/new WorldPoint(2136, 3212, 0),
                                        /*24*/new WorldPoint(2140, 3214, 0),
                                        /*25*/new WorldPoint(2138, 3228, 0),
                                        /*26*/new WorldPoint(2133, 3232, 0),
                                        /*27*/new WorldPoint(2138, 3237, 0),
                                        /*28*/new WorldPoint(2143, 3243, 0),
                                        /*29*/new WorldPoint(2153, 3247, 0),
                                        /*30*/new WorldPoint(2203, 3574, 0),
                                        /*31*/new WorldPoint(2192, 3568, 0),
                                        /*32*/new WorldPoint(2192, 3548, 0),
                                        /*33*/new WorldPoint(2202, 3534, 0),
                                        /*34*/new WorldPoint(2198, 3513, 0),
                                        /*35*/new WorldPoint(2105, 3140, 0),
                                        /*36*/new WorldPoint(2092, 3145, 0),
                                        /*37*/new WorldPoint(2078, 3158, 0),
                                        /*38*/new WorldPoint(2070, 3161, 0),
                                        /*39*/new WorldPoint(2069, 3175, 0),
                                        /*40*/new WorldPoint(2058, 3185, 0),
                                        /*41*/new WorldPoint(2073, 3210, 0),
                                        /*42*/new WorldPoint(2100, 3205, 0),
                                        /*44*/new WorldPoint(2115, 3189, 0),
                                        /*45*/new WorldPoint(2133, 3191, 0),
                                        /*43*/new WorldPoint(2128, 3172, 0),
                                        /*46*/new WorldPoint(2197, 3490, 0),
                                        /*47*/new WorldPoint(2192, 3480, 0),
                                        /*48*/new WorldPoint(2176, 3475, 0),
                                        /*49*/new WorldPoint(2170, 3465, 0),
                                        /*50*/new WorldPoint(2158, 3464, 0),
                                        /*51*/new WorldPoint(2117, 3372, 0),
                                        /*52*/new WorldPoint(2089, 3374, 0),
                                        /*53*/new WorldPoint(2079, 3388, 0),
                                        /*54*/new WorldPoint(2083, 3394, 0),
                                        /*55*/new WorldPoint(2097, 3396, 0),
                                        /*56*/new WorldPoint(2105, 3407, 0),
                                        /*57*/new WorldPoint(2108, 3410, 0),
                                        /*58*/new WorldPoint(2113, 3414, 0),
                                        /*59*/new WorldPoint(2115, 3423, 0),
                                        /*60*/new WorldPoint(2110, 3439, 0),
                                        /*61*/new WorldPoint(2117, 3439, 0),
                                        /*62*/new WorldPoint(2146, 3464, 0),
                                        /*63*/new WorldPoint(2111, 3464, 0),
                                        /*64*/new WorldPoint(2105, 3471, 0),
                                        /*65*/new WorldPoint(2107, 3492, 0),
                                        /*66*/new WorldPoint(2115, 3496, 0),
                                        /*67*/new WorldPoint(2126, 3495, 0),
                                        /*68*/new WorldPoint(2134, 3483, 0),
                                        /*69*/new WorldPoint(2144, 3483, 0),
                                        /*70*/new WorldPoint(2149, 3493, 0),
                                        /*71*/new WorldPoint(2149, 3502, 0),
                                        /*72*/new WorldPoint(2160, 3508, 0),
                                        /*73*/new WorldPoint(2250, 3633, 0),
                                        /*74*/new WorldPoint(2241, 3629, 0),
                                        /*75*/new WorldPoint(2232, 3617, 0),
                                        /*76*/new WorldPoint(2230, 3609, 0),
                                        /*77*/new WorldPoint(2228, 3600, 0),
                                        /*78*/new WorldPoint(2220, 3593, 0),
                                        /*79*/new WorldPoint(2192, 3599, 0),
                                        /*80*/new WorldPoint(2166, 3589, 0),
                                        /*81*/new WorldPoint(2151, 3597, 0),
                                        /*82*/new WorldPoint(2125, 3598, 0),
                                        /*83*/new WorldPoint(2109, 3592, 0),
                                        /*84*/new WorldPoint(2099, 3582, 0),
                                        /*85*/new WorldPoint(2104, 3574, 0),
                                        /*86*/new WorldPoint(2174, 3508, 0),
                                        /*87*/new WorldPoint(2189, 3508, 0),
                                        /*88*/new WorldPoint(2208, 3508, 0),
                                        /*89*/new WorldPoint(2220, 3516, 0),
                                        /*90*/new WorldPoint(2220, 3526, 0),
                                        /*91*/new WorldPoint(2216, 3538, 0),
                                        /*92*/new WorldPoint(2222, 3547, 0),
                                        /*93*/new WorldPoint(2224, 3570, 0),
                                        /*94*/new WorldPoint(2218, 3580, 0),
                                        /*95*/new WorldPoint(2208, 3584, 0),
                                        /*96*/new WorldPoint(2108, 3560, 0),
                                        /*97*/new WorldPoint(2097, 3558, 0),
                                        /*98*/new WorldPoint(2080, 3552, 0),
                                        /*99*/new WorldPoint(2072, 3540, 0),
                                        /*100*/new WorldPoint(2083, 3529, 0),
                                        /*101*/new WorldPoint(2083, 3504, 0),
                                        /*102*/new WorldPoint(2085, 3472, 0),
                                        /*103*/new WorldPoint(2095, 3445, 0),
                                        /*104*/new WorldPoint(2095, 3438, 0),
                                        /*105*/new WorldPoint(2103, 3433, 0),
                                        /*106*/new WorldPoint(2105, 3425, 0),
                                        /*107*/new WorldPoint(2193, 3584, 0),
                                        /*108*/new WorldPoint(2177, 3579, 0),
                                        /*109*/new WorldPoint(2175, 3562, 0),
                                        /*110*/new WorldPoint(2179, 3544, 0),
                                        /*111*/new WorldPoint(2174, 3537, 0),
                                        /*112*/new WorldPoint(2162, 3545, 0),
                                        /*113*/new WorldPoint(2153, 3574, 0),
                                        /*114*/new WorldPoint(2143, 3582, 0),
                                        /*115*/new WorldPoint(2137, 3256, 0),
                                        /*116*/new WorldPoint(2130, 3276, 0),
                                        /*117*/new WorldPoint(2130, 3283, 0),
                                        /*118*/new WorldPoint(2124, 3289, 0),
                                        /*119*/new WorldPoint(2127, 3296, 0),
                                        /*120*/new WorldPoint(2131, 3318, 0),
                                        /*121*/new WorldPoint(2141, 3336, 0),
                                        /*122*/new WorldPoint(2151, 3346, 0),
                                        /*123*/new WorldPoint(2146, 3367, 0),
                                        /*124*/new WorldPoint(2128, 3380, 0),
                                        /*125*/new WorldPoint(2121, 3364, 0),
                                        /*126*/new WorldPoint(2126, 3356, 0),
                                        /*127*/new WorldPoint(2129, 3582, 0),
                                        /*128*/new WorldPoint(2122, 3582, 0),
                                        /*129*/new WorldPoint(2119, 3569, 0),
                                        /*130*/new WorldPoint(2124, 3539, 0),
                                        /*131*/new WorldPoint(2121, 3527, 0),
                                        /*132*/new WorldPoint(2119, 3519, 0),
                                        /*133*/new WorldPoint(2130, 3512, 0),
                                        /*134*/new WorldPoint(2140, 3517, 0),
                                        /*135*/new WorldPoint(2162, 3519, 0),
                                        /*136*/new WorldPoint(2171, 3523, 0),
                                        /*137*/new WorldPoint(2104, 3413, 0),
                                        /*138*/new WorldPoint(2094, 3417, 0),
                                        /*139*/new WorldPoint(2085, 3414, 0),
                                        /*140*/new WorldPoint(2080, 3426, 0),
                                        /*141*/new WorldPoint(2081, 3439, 0),
                                        /*142*/new WorldPoint(2083, 3446, 0),
                                        /*143*/new WorldPoint(2082, 3454, 0),
                                        /*144*/new WorldPoint(2082, 3492, 0),
                                        /*145*/new WorldPoint(2095, 3505, 0),
                                        /*146*/new WorldPoint(2105, 3520, 0),
                                        /*147*/new WorldPoint(2095, 3529, 0),
                                        /*148*/new WorldPoint(2091, 3541, 0),
                                        /*149*/new WorldPoint(2106, 3543, 0)
                        // End of trial route

                        ));

        private static final List<PortalDirection> GwenithGlideMarlinPortalDirections = List.of(
                        new PortalDirection(4, PortalColors.White, Directions.East, Directions.EastSouthEast),
                        new PortalDirection(12, PortalColors.White, Directions.North, Directions.NorthNorthEast),
                        new PortalDirection(18, PortalColors.Blue, Directions.East, Directions.East),
                        new PortalDirection(28, PortalColors.Blue, Directions.West, Directions.WestSouthWest),
                        new PortalDirection(33, PortalColors.Green, Directions.West, Directions.WestNorthWest),
                        new PortalDirection(44, PortalColors.Green, Directions.South, Directions.SouthSouthWest),
                        new PortalDirection(49, PortalColors.Yellow, Directions.West, Directions.West),
                        new PortalDirection(60, PortalColors.Yellow, Directions.West, Directions.West),
                        new PortalDirection(71, PortalColors.Red, Directions.West, Directions.SouthWest),
                        new PortalDirection(84, PortalColors.Red, Directions.East, Directions.East),
                        new PortalDirection(86, PortalColors.Green, Directions.East, Directions.NorthEast),
                        new PortalDirection(94, PortalColors.Black, Directions.West, Directions.West),
                        new PortalDirection(105, PortalColors.Black, Directions.West, Directions.West),
                        new PortalDirection(113, PortalColors.Cyan, Directions.North, Directions.NorthNorthWest),
                        new PortalDirection(125, PortalColors.Cyan, Directions.West, Directions.South),
                        new PortalDirection(135, PortalColors.Pink, Directions.West, Directions.WestNorthWest)//
        );

        public static final List<TrialRoute> AllTrialRoutes = new ArrayList<TrialRoute>(
                        List.of(
                                        new TrialRoute(TrialLocations.TemporTantrum, TrialRanks.Swordfish, TemporTantrumSwordfishBestLine),
                                        new TrialRoute(TrialLocations.TemporTantrum, TrialRanks.Shark, TemporTantrumSharkBestLine),
                                        new TrialRoute(TrialLocations.TemporTantrum, TrialRanks.Marlin, TemporTantrumMarlinBestLine),
                                        new TrialRoute(TrialLocations.JubblyJive, TrialRanks.Swordfish, JubblySwordfishBestLine, JubblySwordfishToadOrder, Collections.emptyList(), null),
                                        new TrialRoute(TrialLocations.JubblyJive, TrialRanks.Shark, JubblySharkBestLine, JubblySharkToadOrder, Collections.emptyList(), null),
                                        new TrialRoute(TrialLocations.JubblyJive, TrialRanks.Marlin, JubblyMarlinBestLine, JubblyMarlinToadOrder, JubblyMarlinWindMoteIndices, null),
                                        new TrialRoute(TrialLocations.GwenithGlide, TrialRanks.Swordfish, GwenithGlideSwordfishBestLine, null, null, GwenithGlideSwordfishPortalDirections),
                                        new TrialRoute(TrialLocations.GwenithGlide, TrialRanks.Shark, GwenithGlideSharkBestLine, null, null, GwenithGlideSharkPortalDirections),
                                        new TrialRoute(TrialLocations.GwenithGlide, TrialRanks.Marlin, GwenithGlideMarlinBestLine, null, null, GwenithGlideMarlinPortalDirections)//
                        ));

        public static final void AddGwenithGlideRoutes() {
                AllTrialRoutes.add(new TrialRoute(TrialLocations.GwenithGlide, TrialRanks.Swordfish, GwenithGlideSwordfishBestLine, null, null, GwenithGlideSwordfishPortalDirections));
                AllTrialRoutes.add(new TrialRoute(TrialLocations.GwenithGlide, TrialRanks.Shark, GwenithGlideSharkBestLine, null, null, GwenithGlideSharkPortalDirections));
                AllTrialRoutes.add(new TrialRoute(TrialLocations.GwenithGlide, TrialRanks.Marlin, GwenithGlideMarlinBestLine, null, null, GwenithGlideMarlinPortalDirections));
        }
}
