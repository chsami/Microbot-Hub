package net.runelite.client.plugins.microbot.fourtifysagility.course;

import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;

public enum CourseObstacle
{
    SEERS_ZERO(
        AgilityCourse.SEERS_VILLAGE, bounds(new WorldPoint(2688, 3456, 0), new WorldPoint(2752, 3519, 0)), ObstacleObjectType.DECOR, 14927, 60
    ),
    SEERS_ONE(
        AgilityCourse.SEERS_VILLAGE, bounds(new WorldPoint(2721, 3490, 3), new WorldPoint(2731, 3497, 3)), ObstacleObjectType.GAME, 14928, 60
    ),
    SEERS_TWO(
        AgilityCourse.SEERS_VILLAGE, bounds(new WorldPoint(2705, 3488, 2), new WorldPoint(2714, 3498, 2)), ObstacleObjectType.GROUND, 14932, 60
    ),
    SEERS_THREE(
        AgilityCourse.SEERS_VILLAGE, bounds(new WorldPoint(2710, 3476, 2), new WorldPoint(2719, 3482, 2)), ObstacleObjectType.GAME, 14929, 60
    ),
    SEERS_FOUR(
        AgilityCourse.SEERS_VILLAGE, bounds(new WorldPoint(2700, 3469, 3), new WorldPoint(2716, 3476, 3)), ObstacleObjectType.GAME, 14930, 60
    ),
    SEERS_FIVE(
        AgilityCourse.SEERS_VILLAGE, bounds(new WorldPoint(2692, 3459, 2), new WorldPoint(2704, 3466, 2)), ObstacleObjectType.GAME, 14931, 60
    ),
    DRAYNOR_ZERO(AgilityCourse.DRAYNOR, bounds(new WorldPoint(3072, 3238, 0), new WorldPoint(3116, 3288, 0)), ObstacleObjectType.DECOR, 11404, 10),
    DRAYNOR_ONE(AgilityCourse.DRAYNOR, bounds(new WorldPoint(3098, 3277, 3), new WorldPoint(3103, 3282, 3)), ObstacleObjectType.GROUND, 11405, 10),
    DRAYNOR_TWO(AgilityCourse.DRAYNOR, bounds(new WorldPoint(3087, 3272, 3), new WorldPoint(3094, 3278, 3)), ObstacleObjectType.GROUND, 11406, 10),
    DRAYNOR_THREE(AgilityCourse.DRAYNOR, bounds(new WorldPoint(3089, 3265, 3), new WorldPoint(3095, 3268, 3)), ObstacleObjectType.GAME, 11430, 10),
    DRAYNOR_FOUR(AgilityCourse.DRAYNOR, bounds(new WorldPoint(3087, 3256, 3), new WorldPoint(3089, 3262, 3)), ObstacleObjectType.GAME, 11630, 10),
    DRAYNOR_FIVE(AgilityCourse.DRAYNOR, bounds(new WorldPoint(3087, 3254, 3), new WorldPoint(3095, 3256, 3)), ObstacleObjectType.GAME, 11631, 10),
    DRAYNOR_SIX(AgilityCourse.DRAYNOR, bounds(new WorldPoint(3096, 3256, 3), new WorldPoint(3102, 3262, 3)), ObstacleObjectType.GAME, 11632, 10),
    GNOME_ZERO(AgilityCourse.GNOME, bounds(new WorldPoint(2471, 3435, 0), new WorldPoint(2489, 3440, 0)), ObstacleObjectType.GROUND, 23145, 1),
    GNOME_ONE(AgilityCourse.GNOME, bounds(new WorldPoint(2471, 3425, 0), new WorldPoint(2478, 3430, 0)), ObstacleObjectType.GAME, 23134, 1),
    GNOME_TWO(AgilityCourse.GNOME, bounds(new WorldPoint(2471, 3422, 1), new WorldPoint(2477, 3425, 1)), ObstacleObjectType.GAME, 23559, 1),
    GNOME_THREE(AgilityCourse.GNOME, bounds(new WorldPoint(2472, 3418, 2), new WorldPoint(2478, 3421, 2)), ObstacleObjectType.GROUND, 23557, 1),
    GNOME_FOUR(AgilityCourse.GNOME, bounds(new WorldPoint(2483, 3418, 2), new WorldPoint(2489, 3422, 2)), ObstacleObjectType.GAME, 23560, 1),
    GNOME_FIVE(AgilityCourse.GNOME, bounds(new WorldPoint(2482, 3418, 0), new WorldPoint(2490, 3426, 0)), ObstacleObjectType.GAME, 23135, 1),
    GNOME_SIX(AgilityCourse.GNOME, bounds(new WorldPoint(2483, 3426, 0), new WorldPoint(2489, 3432, 0)), ObstacleObjectType.GAME, 23138, 1),
    VARROCK_ZERO(AgilityCourse.VARROCK, bounds(new WorldPoint(3172, 3384, 0), new WorldPoint(3245, 3430, 0)), ObstacleObjectType.DECOR, 14412, 30),
    VARROCK_ONE(AgilityCourse.VARROCK, bounds(new WorldPoint(3213, 3409, 3), new WorldPoint(3221, 3420, 3)), ObstacleObjectType.GAME, 14413, 30),
    VARROCK_TWO(AgilityCourse.VARROCK, bounds(new WorldPoint(3200, 3410, 3), new WorldPoint(3210, 3420, 3)), ObstacleObjectType.GAME, 14414, 30),
    VARROCK_THREE(AgilityCourse.VARROCK, bounds(new WorldPoint(3192, 3415, 1), new WorldPoint(3198, 3417, 1)), ObstacleObjectType.GAME, 14832, 30),
    VARROCK_FOUR(AgilityCourse.VARROCK, bounds(new WorldPoint(3191, 3401, 3), new WorldPoint(3199, 3407, 3)), ObstacleObjectType.GAME, 14833, 30),
    VARROCK_FIVE(AgilityCourse.VARROCK, bounds(new WorldPoint(3180, 3383, 3), new WorldPoint(3209, 3399, 3)), ObstacleObjectType.GAME, 14834, 30),
    VARROCK_SIX(AgilityCourse.VARROCK, bounds(new WorldPoint(3217, 3392, 3), new WorldPoint(3233, 3403, 3)), ObstacleObjectType.GAME, 14835, 30),
    VARROCK_SEVEN(AgilityCourse.VARROCK, bounds(new WorldPoint(3235, 3402, 3), new WorldPoint(3241, 3409, 3)), ObstacleObjectType.GAME, 14836, 30),
    VARROCK_EIGHT(AgilityCourse.VARROCK, bounds(new WorldPoint(3235, 3409, 3), new WorldPoint(3241, 3416, 3)), ObstacleObjectType.GAME, 14841, 30),
    CANIFIS_ZERO(AgilityCourse.CANIFIS, bounds(new WorldPoint(3457, 3457, 0), new WorldPoint(3518, 3517, 0)), ObstacleObjectType.GAME, 14843, 40),
    CANIFIS_ONE(AgilityCourse.CANIFIS, bounds(new WorldPoint(3503, 3490, 2), new WorldPoint(3512, 3498, 2)), ObstacleObjectType.GAME, 14844, 40),
    CANIFIS_TWO(AgilityCourse.CANIFIS, bounds(new WorldPoint(3496, 3503, 2), new WorldPoint(3505, 3508, 2)), ObstacleObjectType.GAME, 14845, 40),
    CANIFIS_THREE(AgilityCourse.CANIFIS, bounds(new WorldPoint(3485, 3498, 2), new WorldPoint(3494, 3506, 2)), ObstacleObjectType.GAME, 14848, 40),
    CANIFIS_FOUR(AgilityCourse.CANIFIS, bounds(new WorldPoint(3474, 3491, 3), new WorldPoint(3481, 3501, 3)), ObstacleObjectType.GAME, 14846, 40),
    CANIFIS_FIVE(AgilityCourse.CANIFIS, bounds(new WorldPoint(3477, 3481, 2), new WorldPoint(3484, 3487, 2)), ObstacleObjectType.GAME, 14894, 40),
    CANIFIS_SIX(AgilityCourse.CANIFIS, bounds(new WorldPoint(3488, 3468, 3), new WorldPoint(3504, 3478, 3)), ObstacleObjectType.GAME, 14847, 40),
    CANIFIS_SEVEN(AgilityCourse.CANIFIS, bounds(new WorldPoint(3508, 3474, 2), new WorldPoint(3517, 3483, 2)), ObstacleObjectType.GAME, 14897, 40),
    ALKHARID_ZERO(
        AgilityCourse.AL_KHARID, bounds(new WorldPoint(3263, 3155, 0), new WorldPoint(3327, 3202, 0)), ObstacleObjectType.DECOR, 11633, 20
    ),
    ALKHARID_ONE(
        AgilityCourse.AL_KHARID, bounds(new WorldPoint(3272, 3180, 3), new WorldPoint(3279, 3193, 3)), ObstacleObjectType.GROUND, 14398, 20
    ),
    ALKHARID_TWO(AgilityCourse.AL_KHARID, bounds(new WorldPoint(3265, 3161, 3), new WorldPoint(3273, 3174, 3)), ObstacleObjectType.GAME, 14402, 20),
    ALKHARID_THREE(
        AgilityCourse.AL_KHARID, bounds(new WorldPoint(3283, 3160, 3), new WorldPoint(3303, 3180, 3)), ObstacleObjectType.GAME, 14403, 20
    ),
    ALKHARID_FOUR(
        AgilityCourse.AL_KHARID, bounds(new WorldPoint(3313, 3160, 1), new WorldPoint(3319, 3166, 1)), ObstacleObjectType.GAME, 14404, 20
    ),
    ALKHARID_FIVE(
        AgilityCourse.AL_KHARID, bounds(new WorldPoint(3314, 3173, 2), new WorldPoint(3319, 3179, 2)), ObstacleObjectType.DECOR, 11634, 20
    ),
    ALKHARID_SIX(
        AgilityCourse.AL_KHARID, bounds(new WorldPoint(3312, 3180, 3), new WorldPoint(3319, 3187, 3)), ObstacleObjectType.GROUND, 14409, 20
    ),
    ALKHARID_SEVEN(
        AgilityCourse.AL_KHARID, bounds(new WorldPoint(3297, 3185, 3), new WorldPoint(3306, 3194, 3)), ObstacleObjectType.GAME, 14399, 20
    ),
    POLLNIVNEACH_ZERO(
        AgilityCourse.POLLNIVNEACH, bounds(new WorldPoint(3329, 2945, 0), new WorldPoint(3389, 3005, 0)), ObstacleObjectType.GAME, 14935, 70
    ),
    POLLNIVNEACH_ONE(
        AgilityCourse.POLLNIVNEACH, bounds(new WorldPoint(3346, 2963, 1), new WorldPoint(3352, 2969, 1)), ObstacleObjectType.GAME, 14936, 70
    ),
    POLLNIVNEACH_TWO(
        AgilityCourse.POLLNIVNEACH, bounds(new WorldPoint(3352, 2973, 1), new WorldPoint(3356, 2977, 1)), ObstacleObjectType.GAME, 14937, 70
    ),
    POLLNIVNEACH_THREE(
        AgilityCourse.POLLNIVNEACH, bounds(new WorldPoint(3360, 2977, 1), new WorldPoint(3363, 2980, 1)), ObstacleObjectType.GAME, 14938, 70
    ),
    POLLNIVNEACH_FOUR(
        AgilityCourse.POLLNIVNEACH, bounds(new WorldPoint(3366, 2974, 1), new WorldPoint(3371, 2977, 1)), ObstacleObjectType.GAME, 14939, 70
    ),
    POLLNIVNEACH_FIVE(
        AgilityCourse.POLLNIVNEACH, bounds(new WorldPoint(3365, 2982, 1), new WorldPoint(3371, 2986, 1)), ObstacleObjectType.DECOR, 14940, 70
    ),
    POLLNIVNEACH_SIX(
        AgilityCourse.POLLNIVNEACH, bounds(new WorldPoint(3355, 2980, 2), new WorldPoint(3367, 2986, 2)), ObstacleObjectType.GAME, 14941, 70
    ),
    POLLNIVNEACH_SEVEN(
        AgilityCourse.POLLNIVNEACH, bounds(new WorldPoint(3357, 2991, 2), new WorldPoint(3367, 2996, 2)), ObstacleObjectType.GAME, 14944, 70
    ),
    POLLNIVNEACH_EIGHT(
        AgilityCourse.POLLNIVNEACH, bounds(new WorldPoint(3356, 3000, 2), new WorldPoint(3363, 3008, 2)), ObstacleObjectType.GAME, 14945, 70
    ),
    BRIMHAVEN_ENTRY(
        AgilityCourse.BRIMHAVEN, bounds(new WorldPoint(2803, 3187, 0), new WorldPoint(2814, 3198, 0)), ObstacleObjectType.GAME, 3617, 1
    ),
    BRIMHAVEN_ARENA(
        AgilityCourse.BRIMHAVEN, bounds(new WorldPoint(2758, 9543, 3), new WorldPoint(2808, 9593, 3)), ObstacleObjectType.GAME, 3608, 1
    ),
    BRIMHAVEN_FALL(
        AgilityCourse.BRIMHAVEN, bounds(new WorldPoint(2758, 9543, 0), new WorldPoint(2808, 9593, 0)), ObstacleObjectType.GAME, 3610, 1
    ),
    PRIFDDINAS_ZERO(
        AgilityCourse.PRIFDDINAS, bounds(new WorldPoint(3237, 6100, 0), new WorldPoint(3275, 6115, 0)), ObstacleObjectType.GAME, 36221, 75
    ),
    PRIFDDINAS_ONE(
        AgilityCourse.PRIFDDINAS, bounds(new WorldPoint(3255, 6103, 2), new WorldPoint(3262, 6113, 2)), ObstacleObjectType.GAME, 36225, 75
    ),
    PRIFDDINAS_TWO(
        AgilityCourse.PRIFDDINAS, bounds(new WorldPoint(3272, 6102, 2), new WorldPoint(3277, 6108, 2)), ObstacleObjectType.GAME, 36227, 75
    ),
    PRIFDDINAS_THREE(
        AgilityCourse.PRIFDDINAS, bounds(new WorldPoint(3267, 6110, 2), new WorldPoint(3272, 6116, 2)), ObstacleObjectType.GAME, 36228, 75
    ),
    PRIFDDINAS_FOUR(
        AgilityCourse.PRIFDDINAS, bounds(new WorldPoint(3268, 6116, 0), new WorldPoint(3271, 6118, 0)), ObstacleObjectType.GAME, 36229, 75
    ),
    PRIFDDINAS_FIVE(
        AgilityCourse.PRIFDDINAS, bounds(new WorldPoint(2268, 3388, 0), new WorldPoint(2272, 3394, 0)), ObstacleObjectType.GAME, 36231, 75
    ),
    PRIFDDINAS_SIX(
        AgilityCourse.PRIFDDINAS, bounds(new WorldPoint(2264, 3388, 2), new WorldPoint(2270, 3394, 2)), ObstacleObjectType.GAME, 36233, 75
    ),
    PRIFDDINAS_SEVEN(
        AgilityCourse.PRIFDDINAS, bounds(new WorldPoint(2254, 3385, 2), new WorldPoint(2262, 3391, 2)), ObstacleObjectType.GAME, 36234, 75
    ),
    PRIFDDINAS_EIGHT(
        AgilityCourse.PRIFDDINAS, bounds(new WorldPoint(3268, 6141, 0), new WorldPoint(3277, 6148, 0)), ObstacleObjectType.GAME, 36232, 75
    ),
    PRIFDDINAS_EIGHT_RECOVERY(
        AgilityCourse.PRIFDDINAS, bounds(new WorldPoint(2242, 3389, 0), new WorldPoint(2255, 3400, 0)), ObstacleObjectType.GAME, 36232, 75
    ),
    PRIFDDINAS_NINE(
        AgilityCourse.PRIFDDINAS, bounds(new WorldPoint(2243, 3394, 2), new WorldPoint(2248, 3399, 2)), ObstacleObjectType.GAME, 36235, 75
    ),
    PRIFDDINAS_TEN(
        AgilityCourse.PRIFDDINAS, bounds(new WorldPoint(2243, 3404, 2), new WorldPoint(2249, 3410, 2)), ObstacleObjectType.GAME, 36236, 75
    ),
    PRIFDDINAS_ELEVEN(
        AgilityCourse.PRIFDDINAS, bounds(new WorldPoint(2249, 3414, 2), new WorldPoint(2256, 3422, 2)), ObstacleObjectType.GAME, 36237, 75
    ),
    PRIFDDINAS_TWELVE(
        AgilityCourse.PRIFDDINAS, bounds(new WorldPoint(2256, 3424, 0), new WorldPoint(2264, 3433, 0)), ObstacleObjectType.GAME, 36238, 75
    ),
    PYRAMID_ZERO(
        AgilityCourse.AGILITY_PYRAMID, bounds(new WorldPoint(3351, 2826, 0), new WorldPoint(3378, 2854, 0)), ObstacleObjectType.GAME, 10857, 30
    ),
    PYRAMID_ONE(
        AgilityCourse.AGILITY_PYRAMID, bounds(new WorldPoint(3354, 2833, 1), new WorldPoint(3356, 2849, 1)), ObstacleObjectType.GAME, 10865, 30
    ),
    PYRAMID_TWO(
        AgilityCourse.AGILITY_PYRAMID, bounds(new WorldPoint(3354, 2850, 1), new WorldPoint(3364, 2853, 1)), ObstacleObjectType.GAME, 10860, 30
    ),
    PYRAMID_THREE(
        AgilityCourse.AGILITY_PYRAMID, bounds(new WorldPoint(3368, 2845, 1), new WorldPoint(3376, 2853, 1)), ObstacleObjectType.GROUND, 10868, 30
    ),
    PYRAMID_FOUR(
        AgilityCourse.AGILITY_PYRAMID, bounds(new WorldPoint(3372, 2831, 1), new WorldPoint(3376, 2841, 1)), ObstacleObjectType.GAME, 10882, 30
    ),
    PYRAMID_FIVE(
        AgilityCourse.AGILITY_PYRAMID, bounds(new WorldPoint(3364, 2831, 1), new WorldPoint(3368, 2833, 1)), ObstacleObjectType.GAME, 10886, 30
    ),
    PYRAMID_SIX(
        AgilityCourse.AGILITY_PYRAMID, bounds(new WorldPoint(3356, 2831, 1), new WorldPoint(3360, 2833, 1)), ObstacleObjectType.GAME, 10857, 30
    ),
    PYRAMID_SEVEN(
        AgilityCourse.AGILITY_PYRAMID, bounds(new WorldPoint(3356, 2835, 2), new WorldPoint(3358, 2837, 2)), ObstacleObjectType.GAME, 10884, 30
    ),
    PYRAMID_EIGHT(
        AgilityCourse.AGILITY_PYRAMID, bounds(new WorldPoint(3356, 2841, 2), new WorldPoint(3358, 2847, 2)), ObstacleObjectType.GAME, 10859, 30
    ),
    PYRAMID_NINE(
        AgilityCourse.AGILITY_PYRAMID, bounds(new WorldPoint(3356, 2849, 2), new WorldPoint(3360, 2851, 2)), ObstacleObjectType.GAME, 10861, 30
    ),
    PYRAMID_TEN(
        AgilityCourse.AGILITY_PYRAMID, bounds(new WorldPoint(3364, 2841, 2), new WorldPoint(3374, 2851, 2)), ObstacleObjectType.GAME, 10860, 30
    ),
    PYRAMID_ELEVEN(
        AgilityCourse.AGILITY_PYRAMID, bounds(new WorldPoint(3371, 2833, 2), new WorldPoint(3374, 2837, 2)), ObstacleObjectType.GAME, 10865, 30
    ),
    PYRAMID_TWELVE(
        AgilityCourse.AGILITY_PYRAMID, bounds(new WorldPoint(3366, 2833, 2), new WorldPoint(3370, 2836, 2)), ObstacleObjectType.GAME, 10859, 30
    ),
    PYRAMID_THIRTEEN(
        AgilityCourse.AGILITY_PYRAMID, bounds(new WorldPoint(3358, 2833, 2), new WorldPoint(3364, 2836, 2)), ObstacleObjectType.GAME, 10857, 30
    ),
    PYRAMID_FOURTEEN(
        AgilityCourse.AGILITY_PYRAMID, bounds(new WorldPoint(3358, 2837, 3), new WorldPoint(3360, 2839, 3)), ObstacleObjectType.GAME, 10865, 30
    ),
    PYRAMID_FIFTEEN(
        AgilityCourse.AGILITY_PYRAMID, bounds(new WorldPoint(3358, 2840, 3), new WorldPoint(3360, 2844, 3)), ObstacleObjectType.GAME, 10888, 30
    ),
    PYRAMID_SIXTEEN(
        AgilityCourse.AGILITY_PYRAMID, bounds(new WorldPoint(3358, 2843, 3), new WorldPoint(3371, 2849, 3)), ObstacleObjectType.GAME, 10859, 30
    ),
    PYRAMID_SEVENTEEN(
        AgilityCourse.AGILITY_PYRAMID, bounds(new WorldPoint(3370, 2835, 3), new WorldPoint(3372, 2841, 3)), ObstacleObjectType.GROUND, 10868, 30
    ),
    PYRAMID_EIGHTEEN(
        AgilityCourse.AGILITY_PYRAMID, bounds(new WorldPoint(3360, 2835, 3), new WorldPoint(3366, 2837, 3)), ObstacleObjectType.GAME, 10857, 30
    ),
    PYRAMID_NINETEEN(
        AgilityCourse.AGILITY_PYRAMID, bounds(new WorldPoint(3040, 4695, 2), new WorldPoint(3042, 4697, 2)), ObstacleObjectType.GAME, 10859, 30
    ),
    PYRAMID_TWENTY(
        AgilityCourse.AGILITY_PYRAMID, bounds(new WorldPoint(3040, 4699, 2), new WorldPoint(3042, 4703, 2)), ObstacleObjectType.GAME, 10865, 30
    ),
    PYRAMID_TWENTY_ONE(
        AgilityCourse.AGILITY_PYRAMID, bounds(new WorldPoint(3043, 4697, 2), new WorldPoint(3050, 4703, 2)), ObstacleObjectType.GAME, 10859, 30
    ),
    PYRAMID_TWENTY_TWO(
        AgilityCourse.AGILITY_PYRAMID, bounds(new WorldPoint(3048, 4693, 2), new WorldPoint(3050, 4695, 2)), ObstacleObjectType.GAME, 10865, 30
    ),
    PYRAMID_TWENTY_THREE(
        AgilityCourse.AGILITY_PYRAMID, bounds(new WorldPoint(3042, 4693, 2), new WorldPoint(3047, 4695, 2)), ObstacleObjectType.GAME, 10857, 30
    ),
    PYRAMID_TWENTY_FOUR(
        AgilityCourse.AGILITY_PYRAMID, bounds(new WorldPoint(3042, 4697, 3), new WorldPoint(3048, 4701, 3)), ObstacleObjectType.GAME, 10859, 30
    ),
    PYRAMID_TWENTY_FIVE(
        AgilityCourse.AGILITY_PYRAMID, bounds(new WorldPoint(3044, 4695, 3), new WorldPoint(3048, 4697, 3)), ObstacleObjectType.DECOR, 10855, 30
    ),
    FALADOR_ONE(AgilityCourse.FALADOR, bounds(new WorldPoint(3024, 3330, 0), new WorldPoint(3065, 3390, 0)), ObstacleObjectType.DECOR, 14898, 50),
    FALADOR_TWO(AgilityCourse.FALADOR, bounds(new WorldPoint(3036, 3342, 3), new WorldPoint(3041, 3344, 3)), ObstacleObjectType.GROUND, 14899, 50),
    FALADOR_THREE(AgilityCourse.FALADOR, bounds(new WorldPoint(3044, 3342, 3), new WorldPoint(3052, 3350, 3)), ObstacleObjectType.GAME, 14901, 50),
    FALADOR_FOUR(AgilityCourse.FALADOR, bounds(new WorldPoint(3048, 3356, 3), new WorldPoint(3051, 3359, 3)), ObstacleObjectType.GAME, 14903, 50),
    FALADOR_FIVE(AgilityCourse.FALADOR, bounds(new WorldPoint(3045, 3361, 3), new WorldPoint(3049, 3368, 3)), ObstacleObjectType.GAME, 14904, 50),
    FALADOR_SIX(AgilityCourse.FALADOR, bounds(new WorldPoint(3034, 3361, 3), new WorldPoint(3042, 3365, 3)), ObstacleObjectType.GAME, 14905, 50),
    FALADOR_SEVEN(
        AgilityCourse.FALADOR, bounds(new WorldPoint(3026, 3352, 3), new WorldPoint(3030, 3356, 3)), ObstacleObjectType.GROUND, 14911, 50
    ),
    FALADOR_EIGHT(AgilityCourse.FALADOR, bounds(new WorldPoint(3015, 3353, 3), new WorldPoint(3022, 3356, 3)), ObstacleObjectType.GAME, 14919, 50),
    FALADOR_NINE(AgilityCourse.FALADOR, bounds(new WorldPoint(3016, 3343, 3), new WorldPoint(3025, 3350, 3)), ObstacleObjectType.GAME, 14920, 50),
    FALADOR_TEN(AgilityCourse.FALADOR, bounds(new WorldPoint(3011, 3343, 3), new WorldPoint(3015, 3347, 3)), ObstacleObjectType.GAME, 14921, 50),
    FALADOR_ELEVEN(AgilityCourse.FALADOR, bounds(new WorldPoint(3009, 3335, 3), new WorldPoint(3014, 3343, 3)), ObstacleObjectType.GAME, 14923, 50),
    FALADOR_TWELVE(AgilityCourse.FALADOR, bounds(new WorldPoint(3012, 3332, 3), new WorldPoint(3018, 3335, 3)), ObstacleObjectType.GAME, 14924, 50),
    FALADOR_THIRTEEN(
        AgilityCourse.FALADOR, bounds(new WorldPoint(3019, 3332, 3), new WorldPoint(3025, 3336, 3)), ObstacleObjectType.GAME, 14925, 50
    ),
    RELLEKKA_ONE(AgilityCourse.RELLEKKA, bounds(new WorldPoint(2617, 3646, 0), new WorldPoint(2682, 3691, 0)), ObstacleObjectType.DECOR, 14946, 80),
    RELLEKKA_TWO(AgilityCourse.RELLEKKA, bounds(new WorldPoint(2622, 3671, 3), new WorldPoint(2627, 3677, 3)), ObstacleObjectType.GAME, 14947, 75),
    RELLEKKA_THREE(
        AgilityCourse.RELLEKKA, bounds(new WorldPoint(2615, 3658, 3), new WorldPoint(2623, 3669, 3)), ObstacleObjectType.GAME, 14987, 80
    ),
    RELLEKKA_FOUR(AgilityCourse.RELLEKKA, bounds(new WorldPoint(2626, 3651, 3), new WorldPoint(2631, 3656, 3)), ObstacleObjectType.GAME, 14990, 80),
    RELLEKKA_FIVE(AgilityCourse.RELLEKKA, bounds(new WorldPoint(2639, 3649, 3), new WorldPoint(2645, 3654, 3)), ObstacleObjectType.GAME, 14991, 80),
    RELLEKKA_SIX(AgilityCourse.RELLEKKA, bounds(new WorldPoint(2643, 3656, 3), new WorldPoint(2651, 3663, 3)), ObstacleObjectType.GAME, 14992, 80),
    RELLEKKA_SEVEN(
        AgilityCourse.RELLEKKA, bounds(new WorldPoint(2654, 3665, 3), new WorldPoint(2657, 3682, 3)), ObstacleObjectType.GAME, 14994, 80
    ),
    ARDOUGNE_ONE(AgilityCourse.ARDOUGNE, bounds(new WorldPoint(2640, 3285, 0), new WorldPoint(2686, 3325, 0)), ObstacleObjectType.DECOR, 15608, 90),
    ARDOUGNE_TWO(AgilityCourse.ARDOUGNE, bounds(new WorldPoint(2671, 3299, 3), new WorldPoint(2676, 3312, 3)), ObstacleObjectType.GAME, 15609, 90),
    ARDOUGNE_THREE(
        AgilityCourse.ARDOUGNE, bounds(new WorldPoint(2661, 3318, 3), new WorldPoint(2667, 3325, 3)), ObstacleObjectType.GROUND, 26635, 90
    ),
    ARDOUGNE_FOUR(AgilityCourse.ARDOUGNE, bounds(new WorldPoint(2653, 3317, 3), new WorldPoint(2659, 3323, 3)), ObstacleObjectType.GAME, 15610, 90),
    ARDOUGNE_FIVE(AgilityCourse.ARDOUGNE, bounds(new WorldPoint(2649, 3310, 3), new WorldPoint(2655, 3316, 3)), ObstacleObjectType.GAME, 15611, 90),
    ARDOUGNE_SIX(AgilityCourse.ARDOUGNE, bounds(new WorldPoint(2646, 3299, 3), new WorldPoint(2655, 3310, 3)), ObstacleObjectType.GAME, 28912, 90),
    ARDOUGNE_SEVEN(
        AgilityCourse.ARDOUGNE, bounds(new WorldPoint(2653, 3296, 3), new WorldPoint(2658, 3300, 3)), ObstacleObjectType.GAME, 15612, 90
    ),
    APE_ATOLL_ONE(
        AgilityCourse.APE_ATOLL, bounds(new WorldPoint(2750, 2740, 0), new WorldPoint(2777, 2763, 0)), ObstacleObjectType.GROUND, 15412, 48
    ),
    APE_ATOLL_TWO(
        AgilityCourse.APE_ATOLL, bounds(new WorldPoint(2750, 2738, 0), new WorldPoint(2754, 2743, 0)), ObstacleObjectType.GAME, 15414, 48
    ),
    APE_ATOLL_THREE(
        AgilityCourse.APE_ATOLL, bounds(new WorldPoint(2752, 2741, 2), new WorldPoint(2754, 2743, 2)), ObstacleObjectType.GAME, 15417, 48
    ),
    APE_ATOLL_FOUR(
        AgilityCourse.APE_ATOLL, bounds(new WorldPoint(2747, 2741, 0), new WorldPoint(2748, 2742, 0)), ObstacleObjectType.GROUND, 1747, 48
    ),
    APE_ATOLL_FIVE(
        AgilityCourse.APE_ATOLL, bounds(new WorldPoint(2737, 2728, 0), new WorldPoint(2752, 2742, 0)), ObstacleObjectType.GAME, 15487, 48
    ),
    APE_ATOLL_SIX(
        AgilityCourse.APE_ATOLL, bounds(new WorldPoint(2754, 2729, 0), new WorldPoint(2760, 2734, 0)), ObstacleObjectType.GAME, 16062, 48
    ),
    COLOSSAL_WYRM_ONE(
        AgilityCourse.COLOSSAL_WYRM, bounds(new WorldPoint(1611, 2896, 0), new WorldPoint(1668, 2948, 0)), ObstacleObjectType.WALL, 55178, 50
    ),
    COLOSSAL_WYRM_TWO(
        AgilityCourse.COLOSSAL_WYRM, bounds(new WorldPoint(1653, 2926, 1), new WorldPoint(1657, 2933, 1)), ObstacleObjectType.GROUND, 55180, 50
    ),
    COLOSSAL_WYRM_THREE(
        AgilityCourse.COLOSSAL_WYRM, bounds(new WorldPoint(1647, 2909, 1), new WorldPoint(1650, 2912, 1)), ObstacleObjectType.WALL, 55191, 62
    ),
    COLOSSAL_WYRM_FOUR(
        AgilityCourse.COLOSSAL_WYRM, bounds(new WorldPoint(1647, 2906, 2), new WorldPoint(1650, 2909, 2)), ObstacleObjectType.GAME, 55192, 62
    ),
    COLOSSAL_WYRM_FIVE(
        AgilityCourse.COLOSSAL_WYRM, bounds(new WorldPoint(1633, 2906, 2), new WorldPoint(1636, 2911, 2)), ObstacleObjectType.GROUND, 55194, 62
    ),
    COLOSSAL_WYRM_SIX(
        AgilityCourse.COLOSSAL_WYRM, bounds(new WorldPoint(1623, 2931, 2), new WorldPoint(1626, 2934, 2)), ObstacleObjectType.GAME, 55179, 62
    ),
    COLOSSAL_WYRM_SEVEN(
        AgilityCourse.COLOSSAL_WYRM, bounds(new WorldPoint(1647, 2909, 1), new WorldPoint(1650, 2912, 1)), ObstacleObjectType.GROUND, 55184, 50
    ),
    COLOSSAL_WYRM_EIGHT(
        AgilityCourse.COLOSSAL_WYRM, bounds(new WorldPoint(1632, 2909, 1), new WorldPoint(1636, 2912, 1)), ObstacleObjectType.GAME, 55186, 50
    ),
    COLOSSAL_WYRM_NINE(
        AgilityCourse.COLOSSAL_WYRM, bounds(new WorldPoint(1626, 2931, 1), new WorldPoint(1629, 2934, 1)), ObstacleObjectType.WALL, 55190, 50
    ),
    COLOSSAL_WYRM_TEN(
        AgilityCourse.COLOSSAL_WYRM, bounds(new WorldPoint(1623, 2931, 2), new WorldPoint(1626, 2934, 2)), ObstacleObjectType.GAME, 55149, 50
    ),
    WILDERNESS_ONE(
        AgilityCourse.WILDERNESS, bounds(new WorldPoint(2992, 3931, 0), new WorldPoint(3004, 3936, 0)), ObstacleObjectType.GAME, 53224, 52
    ),
    WILDERNESS_TWO(
        AgilityCourse.WILDERNESS, bounds(new WorldPoint(3004, 3936, 0), new WorldPoint(3006, 3939, 0)), ObstacleObjectType.GAME, 23137, 52
    ),
    WILDERNESS_THREE(
        AgilityCourse.WILDERNESS, bounds(new WorldPoint(3003, 3949, 0), new WorldPoint(3009, 3958, 0)), ObstacleObjectType.GAME, 23132, 52
    ),
    WILDERNESS_FOUR(
        AgilityCourse.WILDERNESS, bounds(new WorldPoint(2997, 3958, 0), new WorldPoint(3006, 3961, 0)), ObstacleObjectType.GROUND, 23556, 52
    ),
    WILDERNESS_FIVE(
        AgilityCourse.WILDERNESS, bounds(new WorldPoint(3001, 3960, 0), new WorldPoint(3003, 3964, 0)), ObstacleObjectType.GROUND, 23556, 52
    ),
    WILDERNESS_SIX(
        AgilityCourse.WILDERNESS, bounds(new WorldPoint(2995, 3943, 0), new WorldPoint(3003, 3961, 0)), ObstacleObjectType.GROUND, 23542, 52
    ),
    WILDERNESS_SEVEN(
        AgilityCourse.WILDERNESS, bounds(new WorldPoint(2991, 3935, 0), new WorldPoint(2997, 3946, 0)), ObstacleObjectType.GROUND, 23640, 52
    ),
    WILDERNESS_EIGHT(
        AgilityCourse.WILDERNESS, bounds(new WorldPoint(2992, 10340, 0), new WorldPoint(3009, 10366, 0)), ObstacleObjectType.GAME, 17385, 52
    ),
    // The pit ladder climbs up to 3005, 3964; bounds() leaves out its end row and column.
    WILDERNESS_NINE(
        AgilityCourse.WILDERNESS, bounds(new WorldPoint(3004, 3961, 0), new WorldPoint(3006, 3965, 0)), ObstacleObjectType.GAME, 23132, 52
    );

    public final AgilityCourse area;
    public final WorldArea extendedWorldArea;
    public final ObstacleObjectType type;
    public final int id;
    public final int level;

    CourseObstacle(AgilityCourse area, WorldArea extendedWorldArea, ObstacleObjectType type, int id, int level)
    {
        this.area = area;
        this.extendedWorldArea = extendedWorldArea;
        this.type = type;
        this.id = id;
        this.level = level;
    }

    public static WorldArea bounds(WorldPoint start, WorldPoint end)
    {
        return new WorldArea(start.getX(), start.getY(), end.getX() - start.getX(), end.getY() - start.getY(), start.getPlane());
    }
}
