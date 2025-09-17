package net.runelite.client.plugins.microbot.Bones;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("bones_burier")
public interface BonesConfig extends Config {
    @ConfigItem(
            keyName = "boneType",
            name = "Bone/Ash Type",
            description = "Select the type of bone or ashes you want to use"
    )
    default BoneType boneType() {
        return BoneType.BONES;
    }

    @ConfigItem(
            keyName = "targetPrayerLevel",
            name = "Target Prayer Level",
            description = "The script will automatically stop when this level is reached"
    )
    default int targetPrayerLevel() {
        return 43;
    }

    @ConfigItem(
            keyName = "debugMode",
            name = "Debug Mode",
            description = "Displays detailed information in the console",
            position = 2
    )
    default boolean debugMode() {
        return false;
    }
}