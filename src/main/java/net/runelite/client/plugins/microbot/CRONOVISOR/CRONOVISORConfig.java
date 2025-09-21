package net.runelite.client.plugins.microbot.CRONOVISOR;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("recruiter")
@ConfigInformation(
        "Be in a clan and have equipped clan vexillum. Start the plugin in a crowded area (e.g., GE 301, GE 302).<br/>" +
                "This plugin will automatically invite nearby players to your clan CRONO-VISOR (if recruiting is enabled),<br/>" +
                "send custom messages in English or Spanish depending on your settings, with individual message toggles.<br/>" +
                "You can also configure a list of worlds to hop automatically between for recruitment."
)
public interface CRONOVISORConfig extends Config {

    enum LanguageOption {
        English,
        Spanish
    }

    enum ChatTypeOption {
        ALL,
        CHANNEL,
        CLAN
    }

    @ConfigItem(
            keyName = "messageInterval",
            name = "Message Interval (seconds)",
            description = "Tiempo mínimo entre mensajes automáticos en segundos",
            position = 0
    )
    default int messageInterval() {
        return 90; // por defecto 90 segundos
    }

    @ConfigItem(
            keyName = "chatType",
            name = "Chat Type",
            description = "Select which chat to send recruitment messages to",
            position = 1
    )
    default ChatTypeOption chatType() {
        return ChatTypeOption.ALL;
    }

    @ConfigItem(
            keyName = "language",
            name = "Language",
            description = "Choose language for messages",
            position = 2
    )
    default LanguageOption language() {
        return LanguageOption.English;
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
            position = 3
    )
    default boolean enableMessage1() { return true; }

    @ConfigItem(
            keyName = "customMessage1_en",
            name = "Custom Message 1 (EN)",
            description = "Message 1 in English",
            position = 4
    )
    default String customMessage1_en() {
        return "Join Clan CRONO-VISOR for PK & events! Accept Aid :)";
    }

    @ConfigItem(
            keyName = "customMessage1_es",
            name = "Custom Message 1 (ES)",
            description = "Mensaje 1 en Español",
            position = 5
    )
    default String customMessage1_es() {
        return "¡Únete al Clan CRONO-VISOR para PK y eventos! Activa Aid :)";
    }

    // --- Message 2 ---
    @ConfigItem(
            keyName = "enableMessage2",
            name = "Enable Message 2",
            description = "Enable or disable custom message 2",
            position = 6
    )
    default boolean enableMessage2() { return true; }

    @ConfigItem(
            keyName = "customMessage2_en",
            name = "Custom Message 2 (EN)",
            description = "Message 2 in English",
            position = 7
    )
    default String customMessage2_en() {
        return "CRONO-VISOR clan is recruiting PKers! Accept Aid for invite!";
    }

    @ConfigItem(
            keyName = "customMessage2_es",
            name = "Custom Message 2 (ES)",
            description = "Mensaje 2 en Español",
            position = 8
    )
    default String customMessage2_es() {
        return "¡El clan CRONO-VISOR está reclutando PKers! Activa Aid para invitación!";
    }

    // --- Message 3 ---
    @ConfigItem(
            keyName = "enableMessage3",
            name = "Enable Message 3",
            description = "Enable or disable custom message 3",
            position = 9
    )
    default boolean enableMessage3() { return true; }

    @ConfigItem(
            keyName = "customMessage3_en",
            name = "Custom Message 3 (EN)",
            description = "Message 3 in English",
            position = 10
    )
    default String customMessage3_en() {
        return "Clan CRONO-VISOR hosting events, join us now!";
    }

    @ConfigItem(
            keyName = "customMessage3_es",
            name = "Custom Message 3 (ES)",
            description = "Mensaje 3 en Español",
            position = 11
    )
    default String customMessage3_es() {
        return "El clan CRONO-VISOR organiza eventos, ¡únete ya!";
    }

    // --- Message 4 ---
    @ConfigItem(
            keyName = "enableMessage4",
            name = "Enable Message 4",
            description = "Enable or disable custom message 4",
            position = 12
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
            position = 14
    )
    default String customMessage4_es() {
        return "¿Listo para PK? ¡El clan CRONO-VISOR te espera!";
    }
}
