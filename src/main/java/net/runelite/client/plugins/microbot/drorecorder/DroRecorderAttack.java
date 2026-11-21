package net.runelite.client.plugins.microbot.drorecorder;

import net.runelite.api.gameval.SpotanimID;
import java.util.LinkedHashMap;
import java.util.Map;

/** Conservative attack labels. Raw IDs always remain in the recording. */
final class DroRecorderAttack
{
    private DroRecorderAttack() { }

    static Map<String, Object> projectile(int id)
    {
        String style = "UNKNOWN";
        switch (id)
        {
            case SpotanimID.WINDSTRIKE_TRAVEL:
            case SpotanimID.WATERSTRIKE_TRAVEL:
            case SpotanimID.EARTHSTRIKE_TRAVEL:
            case SpotanimID.FIRESTRIKE_TRAVEL:
            case SpotanimID.WINDBOLT_TRAVEL:
            case SpotanimID.WATERBOLT_TRAVEL:
            case SpotanimID.EARTHBOLT_TRAVEL:
            case SpotanimID.FIREBOLT_TRAVEL:
            case SpotanimID.WINDBLAST_TRAVEL:
            case SpotanimID.WATERBLAST_TRAVEL:
            case SpotanimID.EARTHBLAST_TRAVEL:
            case SpotanimID.FIREBLAST_TRAVEL:
            case SpotanimID.WINDWAVE_TRAVEL:
            case SpotanimID.WATERWAVE_TRAVEL:
            case SpotanimID.EARTHWAVE_TRAVEL:
            case SpotanimID.FIREWAVE_TRAVEL:
            case SpotanimID.SLAYER_MAGICDART_TRAVEL:
            case SpotanimID.SLAYER_INFERNALMAGE_MAGICDART_TRAVEL:
                style = "MAGIC";
                break;
            case SpotanimID.IRON_ARROW_TRAVEL:
            case SpotanimID.BRONZE_ARROW_TRAVEL:
            case SpotanimID.STEEL_ARROW_TRAVEL:
            case SpotanimID.MITHRIL_ARROW_TRAVEL:
            case SpotanimID.ADAMANT_ARROW_TRAVEL:
            case SpotanimID.BLACK_ARROW_TRAVEL:
            case SpotanimID.RUNE_ARROW_TRAVEL:
            case SpotanimID.ICE_ARROW_TRAVEL:
            case SpotanimID.FIRE_ARROW_TRAVEL:
            case SpotanimID.CROSSBOWBOLT_TRAVEL:
            case SpotanimID.BLACK_DART_TRAVEL:
            case SpotanimID.BRONZE_DART_TRAVEL:
            case SpotanimID.IRON_DART_TRAVEL:
            case SpotanimID.STEEL_DART_TRAVEL:
            case SpotanimID.MITHRIL_DART_TRAVEL:
            case SpotanimID.ADAMANT_DART_TRAVEL:
            case SpotanimID.RUNE_DART_TRAVEL:
            case SpotanimID.BRONZE_JAVELIN_TRAVEL:
            case SpotanimID.IRON_JAVELIN_TRAVEL:
            case SpotanimID.STEEL_JAVELIN_TRAVEL:
            case SpotanimID.MITHRIL_JAVELIN_TRAVEL:
            case SpotanimID.ADAMANT_JAVELIN_TRAVEL:
            case SpotanimID.RUNE_JAVELIN_TRAVEL:
            case SpotanimID.DRAGON_JAVELIN_TRAVEL:
            case SpotanimID.OGRE_ARROW_TRAVEL:
            case SpotanimID.SLAYER_BROAD_ARROW_TRAVEL:
            case SpotanimID.DTTD_BONE_CROSSBOWBOLT_TRAVEL:
            case SpotanimID.ACB_CROSSBOWBOLT_TRAVEL:
                style = "RANGED";
                break;
            default:
                break;
        }
        return label(style, "UNKNOWN".equals(style) ? "UNMAPPED_PROJECTILE_ID" : "PROJECTILE_ID_CATALOGUE", id);
    }

    static Map<String, Object> animation(String role, int id)
    {
        String style = "UNKNOWN";
        // Same verified Fight Caves animation catalogue used by DroFirecape AttackClock.
        if ("RANGER".equals(role)) { style = id == 2633 ? "RANGED" : id == 2628 ? "MELEE" : style; }
        if ("MAGER".equals(role)) { style = id == 2647 ? "MAGIC" : id == 2644 ? "MELEE" : style; }
        if ("MELEE".equals(role) && id == 2637) { style = "MELEE"; }
        if ("JAD".equals(role)) { style = id == 2652 ? "RANGED" : id == 2656 ? "MAGIC" : id == 2655 ? "MELEE" : style; }
        return label(style, "UNKNOWN".equals(style) ? "UNMAPPED_ANIMATION" : "FIGHT_CAVES_ROLE_AND_ANIMATION", id);
    }

    private static Map<String, Object> label(String style, String evidence, int id)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("style", style);
        result.put("evidence", evidence);
        result.put("evidenceId", id);
        return result;
    }
}
