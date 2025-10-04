package net.runelite.client.plugins.microbot.CRONOVISOR;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("recruiter")
@ConfigInformation(
        "Be in a clan and have the clan cloak and vexillum equipped.<br/>" +
                "The plugin will automatically handle withdrawing or equipping these items from your bank if needed.<br/>" +
                "Start the plugin in a chosen area: Grand Exchange, Ferox Enclave, Lumbridge, Wilderness, Artio, Line 30 Wilderness, or Falador.<br/>" +
                "This plugin will automatically invite nearby players to your clan CRONO-VISOR if recruiting is enabled,<br/>" +
                "and send custom recruitment messages in English, Spanish, or both depending on your settings.<br/>" +
                "You can enable or disable individual messages, add your own text, and include emoji or text variations.<br/>" +
                "Additionally, the plugin ensures the player stays within the selected area and can be configured to hop between worlds automatically for recruitment."
)
public interface CRONOVISORConfig extends Config {

    enum LanguageOption {
        English,
        Spanish,
        Both
    }

    enum ChatTypeOption {
        ALL,
        CHANNEL,
        CLAN
    }

    enum RecruitLocation {
        GrandExchange,
        Ferox,
        Lumbridge,
        Callisto,
        Artio,
        Line30Wilder,
        Falador,
        Rimmington
    }

    @ConfigItem(
            keyName = "location",
            name = "Recruitment Location",
            description = "Choose the location where the script will operate (GE, Ferox, Lumbridge, Wilderness)",
            position = 0
    )
    default RecruitLocation location() {
        return RecruitLocation.GrandExchange;
    }
    default int messageInterval() {
        return 120;
    }

    @ConfigItem(
            keyName = "chatType",
            name = "Chat Type",
            description = "Select which chat to send recruitment messages to",
            position = 2
    )
    default ChatTypeOption chatType() {
        return ChatTypeOption.ALL;
    }

    @ConfigItem(
            keyName = "language",
            name = "Language",
            description = "Choose language for messages (English, Spanish, or Both)",
            position = 3
    )
    default LanguageOption language() {
        return LanguageOption.Both;
    }

    @ConfigItem(
            keyName = "recruit",
            name = "Recruit clan",
            description = "Enable or disable clan recruiting."
    )
    default boolean recruit() { return false; }

    @ConfigItem(
            keyName = "sendMessage",
            name = "Send messages",
            description = "Enable or disable recruitment messages."
    )
    default boolean sendMessage() { return false; }

    // --- Message 1 ---
    @ConfigItem(
            keyName = "enableMessage1",
            name = "Enable Message 1",
            description = "Enable or disable custom message 1",
            position = 4
    )
    default boolean enableMessage1() { return true; }

    @ConfigItem(
            keyName = "customMessage1_en",
            name = "Custom Message 1 (EN)",
            description = "Message 1 in English",
            position = 5
    )
    default String customMessage1_en() {
        return "Join Clan CRONO-VISOR for PK & events! Accept Aid :)";
    }

    @ConfigItem(
            keyName = "customMessage1_es",
            name = "Custom Message 1 (ES)",
            description = "Mensaje 1 en Español",
            position = 6
    )
    default String customMessage1_es() {
        return "¡Únete al Clan CRONO-VISOR para PK y eventos! Activa Aid :)";
    }

    // --- Message 2 ---
    @ConfigItem(
            keyName = "enableMessage2",
            name = "Enable Message 2",
            description = "Enable or disable custom message 2",
            position = 7
    )
    default boolean enableMessage2() { return true; }

    @ConfigItem(
            keyName = "customMessage2_en",
            name = "Custom Message 2 (EN)",
            description = "Message 2 in English",
            position = 8
    )
    default String customMessage2_en() {
        return "CRONO-VISOR clan is recruiting PKers! Accept Aid for invite!";
    }

    @ConfigItem(
            keyName = "customMessage2_es",
            name = "Custom Message 2 (ES)",
            description = "Mensaje 2 en Español",
            position = 9
    )
    default String customMessage2_es() {
        return "¡El clan CRONO-VISOR está reclutando PKers! Activa Aid para invitación!";
    }

    // --- Message 3 ---
    @ConfigItem(
            keyName = "enableMessage3",
            name = "Enable Message 3",
            description = "Enable or disable custom message 3",
            position = 10
    )
    default boolean enableMessage3() { return true; }

    @ConfigItem(
            keyName = "customMessage3_en",
            name = "Custom Message 3 (EN)",
            description = "Message 3 in English",
            position = 11
    )
    default String customMessage3_en() {
        return "Clan CRONO-VISOR hosting events, join us now!";
    }

    @ConfigItem(
            keyName = "customMessage3_es",
            name = "Custom Message 3 (ES)",
            description = "Mensaje 3 en Español",
            position = 12
    )
    default String customMessage3_es() {
        return "El clan CRONO-VISOR organiza eventos, ¡únete ya!";
    }

    // --- Message 4 ---
    @ConfigItem(
            keyName = "enableMessage4",
            name = "Enable Message 4",
            description = "Enable or disable custom message 4",
            position = 13
    )
    default boolean enableMessage4() { return true; }

    @ConfigItem(
            keyName = "customMessage4_en",
            name = "Custom Message 4 (EN)",
            description = "Message 4 in English",
            position = 14
    )
    default String customMessage4_en() {
        return "Ready for PK? Clan CRONO-VISOR wants you!";
    }

    @ConfigItem(
            keyName = "customMessage4_es",
            name = "Custom Message 4 (ES)",
            description = "Mensaje 4 en Español",
            position = 15
    )
    default String customMessage4_es() {
        return "¿Listo para PK? ¡El clan CRONO-VISOR te espera!";
    }

    // --- Message 5 (Discord con reglas) ---
    @ConfigItem(
            keyName = "enableMessage5",
            name = "Enable Message 5",
            description = "Enable or disable custom message 5",
            position = 16
    )
    default boolean enableMessage5() { return false; }

    @ConfigItem(
            keyName = "customMessage5_en",
            name = "Custom Message 5 (EN)",
            description = "Message 5 in English (Discord rules)",
            position = 17
    )
    default String customMessage5_en() {
        return "Clan rules are on our Discord: discord.gg/hFnTbjWyfB";
    }

    @ConfigItem(
            keyName = "customMessage5_es",
            name = "Custom Message 5 (ES)",
            description = "Mensaje 5 en Español (reglas en Discord)",
            position = 18
    )
    default String customMessage5_es() {
        return "Las reglas están en nuestro Discord: discord.gg/hFnTbjWyfB";
    }
}
