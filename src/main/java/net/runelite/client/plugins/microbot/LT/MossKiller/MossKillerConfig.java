package net.runelite.client.plugins.microbot.LT.MossKiller;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.LT.MossKiller.Enums.AttackStyle;
import net.runelite.client.plugins.microbot.LT.MossKiller.Enums.CombatMode;
import net.runelite.client.plugins.microbot.LT.MossKiller.Enums.GearEnums;

@ConfigGroup(MossKillerConfig.configGroup)
public interface MossKillerConfig extends Config {

    String configGroup = "moss-killer";
    String hideOverlay = "hideOverlay";

    @ConfigSection(
            name = "Basic Guide",
            description = "Basic guide settings for MossKiller",
            position = 0,
            closedByDefault = false
    )
    String basicGuideSection = "basicGuideSection";

    @ConfigSection(
            name = "Advanced Guide",
            description = "Detailed guide for advanced users",
            position = 2,
            closedByDefault = true
    )
    String advancedGuideSection = "advancedGuideSection";

    @ConfigSection(
            name = "Wildy Safing",
            description = "Options specific to the safespot mode in wildy",
            position = 3,
            closedByDefault = true
    )
    String saferSection = "saferSection";

    @ConfigItem(
            keyName = "equipmentGuide",
            name = "Equipment Details",
            description = "List of required equipment.",
            position = 2,
            section = advancedGuideSection
    )
    default String equipmentGuide() {
        return "1x Rune Chainbody\n"
                + "1x Rune Scimitar\n"
                + "(opcional) 1x Bryophyta Staff (sin carga)\n"
                + "--\n"
                + "TODO LO QUE APARECE A CONTINUACIÓN SON CANTIDADES MÍNIMAS RECOMENDADAS, YA QUE EL SCRIPT SE DETENDRÁ CUANDO TE QUEDES SIN SUMINISTROS\n"
                + "--\n"
                + "10x Maple shortbow\n"
                + "10x Maple longbow\n"
                + "10x Studded Chaps\n"
                + "10x Blue wizard hat\n"
                + "10x Amulet of power\n"
                + "10x Leather boots\n"
                + "10x Leather vambraces\n"
                + "10x Staff of Fire\n"
                + "50x Strength potion (4)\n"
                + "100x Energy potion (4)\n"
                + "200x Law rune\n"
                + "500x Death rune\n"
                + "1000x Adamant Arrows\n"
                + "2000x Swordfish\n"
                + "10000x Mind rune\n"
                + "20000x Air rune";
    }

    @ConfigItem(
            keyName = "instructionsGuide",
            name = "Instructions",
            description = "Advanced usage instructions for MossKiller.",
            position = 1,
            section = advancedGuideSection
    )
    default String instructionsGuide() {
        return  "Selecciona el modo Wildy.\n"
                + "Para la primera ejecución, inicia el plugin cerca de un banco en f2p sin armadura ni armas y con cada pieza de equipo listada en el banco.\n"
                + "Activa el Breakhandler. Ten habilitado el modo fijo.\n"
                + "Niveles de habilidad mínimos requeridos:\n"
                + "- 30 Range\n"
                + "- 41 Mage\n"
                + "- 40 Attack\n"
                + "- 40 Defense\n"
                + "Niveles de habilidad ideales:\n"
                + "- 70 Strength\n"
                + "- 55 Magic\n"
                + "- 43 Prayer\n"
                + "---------------------------------\n"
                + "Este plugin entrenará tu defensa hasta 50 automáticamente";
    }

    @ConfigItem(
            keyName = "Filler",
            name = "",
            description = "filler text to differentiate the config",
            position = 3,
            section = saferSection
    )
    default String fillerTest() {
        return  "         ↓↓↓ RANGED SETTINGS ↓↓↓";
    }

    @ConfigItem(
            keyName = "instructionsForSafer",
            name = "Instructions",
            description = "Safer Mode instructions for MossKiller.",
            position = 1,
            section = saferSection
    )
    default String instructionsGuideSafer() {
        return  "Selecciona el modo Wildy Safer.\n"
                + "Ten habilitado el modo fijo.\n" +
                "Activa LiteMode en el Monitor de Jugador.\n" +
                "Activa el plugin de AutoLogin.\n" +
                "Selecciona tu capa preferida en el menú desplegable. Debes tener:\n" +
                "2x Leather Boots \n" +
                "2x Leather vambraces \n" +
                "2x cape\n" +
                "LA COMIDA/CONSUMIBLE REQUERIDO ES ***TARTA DE MANZANA***\n"
                + "El nivel mínimo requerido para magia es nivel 13 en Magia.\n"
                + "Equipo mínimo para el modo Magia:\n"
                + "- 2x amulet of magic \n"
                + "- 2x staff of fire \n"
                + "Niveles de habilidad requeridos para Rango:\n"
                + "- 30 Ranged\n"
                + "Equipo mínimo para Rango:\n"
                + "- 2x maple Shortbow \n"
                + "- Mithril Arrows (Editar cantidad abajo) \n"
                + "----------------------\n"
                + "Si usas Rango, el resto del equipo de Rango debes seleccionarlo manualmente desde el menú desplegable (y tener 2x de cada uno).";
    }


    @ConfigItem(
            keyName = "guide",
            name = "How to Use",
            description = "Basic usage instructions for MossKiller",
            position = 1,
            section = basicGuideSection
    )

    default String GUIDE() {
        return "NORMAL: Ten runas para teletransportarte a Varrock, swordfish y un bronze axe en el banco. Comienza en el banco este de Varrock. Desactiva los hechizos de teletransporte en la configuración de Web Walker. Activa el Breakhandler.\n"
                + "AVANZADO: Consulta la Guía Avanzada.\n"
                + "CONSEJOS: Para obtener consejos sobre el plugin visita el Discord de Microbot -> Community Plugins -> Moss Killer Plugin";
    }


    @ConfigItem(
            keyName = "wildySelector",
            name = "Wildy Mode",
            description = "Enable this for killing Moss Giants in the Wilderness.",
            position = 0,
            section = advancedGuideSection
    )
    default boolean wildy() {
        return false;
    }

    @ConfigItem(
            keyName = "wildySaferSelector",
            name = "Wildy Safer Mode",
            description = "Enable this for killing Moss Giants in the Wilderness By Safing.",
            position = 0,
            section = saferSection
    )
    default boolean wildySafer() {
        return false;
    }


    @ConfigItem(
            keyName = "attackStyleSelector",
            name = "Attack style",
            description = "Select which attack style to use for wildy safespot",
            position = 1,
            section = saferSection
    )
    default AttackStyle attackStyle() {
        return AttackStyle.MAGIC;
    }

    @ConfigItem(
            keyName = "mithrilArrowAmount",
            name = "Mithril Arrow Amount",
            description = "Number of mithril arrows to use per trip if using Ranged",
            position = 4,
            section = saferSection
    )
    @Range(min = 1, max = 5000)  // This adds validation
    default int mithrilArrowAmount() {
        return 500;
    }

    @ConfigItem(
            keyName = "rangedAmulet",
            name = "Amulet",
            description = "Select which amulet to use for ranging",
            position = 5,
            section = saferSection
    )
    default GearEnums.RangedAmulet rangedAmulet() {
        return GearEnums.RangedAmulet.POWER;
    }

    @ConfigItem(
            keyName = "rangedTorso",
            name = "Torso",
            description = "Select which body armor to use for ranging",
            position = 6,
            section = saferSection
    )
    default GearEnums.RangedTorso rangedTorso() {
        return GearEnums.RangedTorso.STUDDED;
    }

    @ConfigItem(
            keyName = "rangedChaps",
            name = "Chaps",
            description = "Select which leg armor to use for ranging",
            position = 6,
            section = saferSection
    )
    default GearEnums.RangedChaps rangedChaps() {
        return GearEnums.RangedChaps.STUDDED;
    }

    @ConfigItem(
            keyName = "cape",
            name = "Cape",
            description = "Select which cape to use (for both magic and ranging) - you must have a cape",
            position = 2,
            section = saferSection
    )
    default GearEnums.Cape cape() {
        return GearEnums.Cape.ORANGE_CAPE;
    }

    @ConfigItem(
            keyName = "combatMode",
            name = "Combat Mode",
            description = "Select the combat mode: Flee, Fight, or Lure (Currently only Fight Supported)",
            position = 3,
            section = advancedGuideSection
    )
    default CombatMode combatMode() {
        return CombatMode.FIGHT; // Default option
    }


    @ConfigItem(
            keyName = "hideOverlay",
            name = "Overlay Hider",
            description = "Select this if you want to hide the overlay",
            position = 10
    )
    default boolean isHideOverlay() {
        return false;
    }


    @ConfigItem(
            keyName = "buryBones",
            name = "Bury Bones",
            description = "Select this if you want to bury bones",
            position = 8
    )
    default boolean buryBones() {
        return false;
    }

    @ConfigItem(
            keyName = "alchLoot",
            name = "Alch loot",
            description = "Select this if you want to loot alchables and alch them and loot coins",
            position = 9
    )
    default boolean alchLoot() {
        return false;
    }

    @ConfigItem(
            keyName = "forceDefensive",
            name = "Force Defensive",
            description = "Select this if you want to autocast defensive after 50 Defence.",
            position = 5
    )
    default boolean forceDefensive() {
        return false;
    }

    @ConfigItem(
            keyName = "keyThreshold",
            name = "Key Threshold",
            description = "How many Mossy Keys should be collected before killing the boss.",
            position = 7,
            section = basicGuideSection
    )
    default int keyThreshold() {
        return 30;
    }

    @ConfigItem(
            keyName = "defenceLevel",
            name = "Stop Defence When",
            description = "Stops the script when this defence level is reached.",
            position = 3,
            section = basicGuideSection
    )
    @Range(min = 1, max = 125)
    default int defenseLevel() {
        return 125;
    }

    @ConfigItem(
            keyName = "attackLevel",
            name = "Stop Attack When",
            description = "Stops the script when this attack level is reached.",
            position = 4,
            section = basicGuideSection
    )
    @Range(min = 1, max = 125)
    default int attackLevel() {
        return 125;
    }

    @ConfigItem(
            keyName = "strengthLevel",
            name = "Stop Strength When",
            description = "Stops the script when this strength level is reached.",
            position = 5,
            section = basicGuideSection
    )
    @Range(min = 1, max = 125)
    default int strengthLevel() {
        return 125;
    }

    @ConfigItem(
            keyName = "hopWhenPlayerIsNear",
            name = "Hop Worlds When Players Near",
            description = "Hop worlds when players are near you fighting Moss Giants.",
            position = 6,
            section = basicGuideSection
    )
    default boolean hopWhenPlayerIsNear() {
        return true;
    }

    @ConfigItem(
            keyName = "isSlashWeaponEquipped",
            name = "Slash Weapon Equipped?",
            description = "Do you have a slash weapon equipped for spider's web? If False, have a knife in the bank.",
            position = 6,
            section = basicGuideSection
    )
    default boolean isSlashWeaponEquipped() {
        return true;
    }

    @ConfigSection(
            name = "Scheduler Configurations",
            description = "Configuration options for scheduled behavior",
            position = 4, // Ensure this is below all existing positions
            closedByDefault = true
    )
    String schedulerSection = "schedulerSection";

    @ConfigItem(
            keyName = "selectedOutfit",
            name = "Selected Outfit",
            description = "Choose the default outfit type.",
            position = 1,
            section = schedulerSection
    )
    default OutfitHelper.OutfitType selectedOutfit() {
        return OutfitHelper.OutfitType.FULL_RUNE_CHAIN;
    }

    @ConfigItem(
            keyName = "customWeapon",
            name = "Custom Weapon",
            description = "Optional weapon override (leave empty for default).",
            position = 2,
            section = schedulerSection
    )
    default String customWeapon() {
        return "Rune scimitar";
    }

    @ConfigItem(
            keyName = "includeHelmet",
            name = "Include Helmet?",
            description = "Toggle whether a helmet should be worn.",
            position = 3,
            section = schedulerSection
    )
    default boolean includeHelmet() {
        return true;
    }

    @ConfigItem(
            keyName = "capePreference",
            name = "Cape Preference",
            description = "Select the cape you want your character to wear",
            position = 4,
            section = schedulerSection
    )
    default GearEnums.Cape capePreference() {
        return GearEnums.Cape.TEAM_CAPE_50; // default
    }



}