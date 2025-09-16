package net.runelite.client.plugins.microbot.LT.Bones;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("bones_burier")
public interface BonesConfig extends Config {
    @ConfigItem(
            keyName = "boneType",
            name = "Tipo de hueso/ashes",
            description = "Selecciona el tipo de hueso o ashes que quieres usar"
    )
    default BoneType boneType() {
        return BoneType.BONES;
    }

    @ConfigItem(
            keyName = "targetPrayerLevel",
            name = "Nivel objetivo Prayer",
            description = "El script se detendrá automáticamente al alcanzar este nivel"
    )
    default int targetPrayerLevel() {
        return 43;
    }

    @ConfigItem(
            keyName = "debugMode",
            name = "Modo Debug",
            description = "Muestra información detallada en consola",
            position = 2
    )
    default boolean debugMode() {
        return false;
    }
}
