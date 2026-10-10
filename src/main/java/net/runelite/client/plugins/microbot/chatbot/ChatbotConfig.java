package net.runelite.client.plugins.microbot.chatbot;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(ChatbotConfig.configGroup)
public interface ChatbotConfig extends Config {
    String configGroup = "micro-chatbot";

    @ConfigSection(name = "AI Connection", description = "Provider, API key and model", position = 0)
    String openAiSection = "openAi";

    @ConfigSection(name = "Chat Sources", description = "Channels that can trigger a reply", position = 1)
    String chatSourceSection = "chatSources";

    @ConfigSection(name = "Replies", description = "Reply style and safe chatbox behavior", position = 2)
    String responseSection = "response";

    @ConfigSection(name = "Pacing", description = "Request spacing and message freshness", position = 3)
    String pacingSection = "pacing";

    @ConfigSection(name = "Filters", description = "Choose which messages deserve a reply", position = 4)
    String filterSection = "filtering";

    @ConfigItem(keyName = "provider", name = "Provider", description = "Select the service that issued your API key.",
            position = 0, section = openAiSection)
    default ChatbotProvider provider() {
        return ChatbotProvider.OPENAI;
    }

    // Keep the existing key name so upgrading does not discard a saved API key.
    @ConfigItem(keyName = "openAiApiKey", name = "API Key", description = "API key for the selected provider.",
            position = 1, section = openAiSection, secret = true)
    default String openAiApiKey() {
        return "";
    }

    @ConfigItem(keyName = "geminiModel", name = "Gemini Model", description = "Used with Gemini when Custom Model is empty. Availability and quota depend on your account.",
            position = 2, section = openAiSection)
    default ChatbotModel model() {
        return ChatbotModel.GEMINI_3_5_FLASH_LITE;
    }

    @ConfigItem(keyName = "customModel", name = "Custom Model", description = "Optional exact model ID for OpenAI or Gemini; required for Custom. Empty keeps the default or saved model.",
            position = 3, section = openAiSection)
    default String customModel() {
        return "";
    }

    @ConfigItem(keyName = "customEndpoint", name = "Custom Endpoint", description = "For Custom only: full OpenAI-compatible chat-completions URL, including its path. Your API key is sent to this URL.",
            position = 4, section = openAiSection)
    default String customEndpoint() {
        return "";
    }

    @ConfigItem(keyName = "geminiFailover", name = "Gemini Lite Fallback", description = "If the selected Gemini model is limited or unavailable, try Flash-Lite after the request pause. Does not apply to Custom Model.",
            position = 5, section = openAiSection)
    default boolean geminiFailover() {
        return true;
    }

    @ConfigItem(keyName = "openAiModel", name = "Saved OpenAI Model", description = "Legacy saved OpenAI model. Set Custom Model to override it.",
            hidden = true, position = 6, section = openAiSection)
    default String openAiModel() {
        return "gpt-4o-mini";
    }

    @ConfigItem(keyName = "listenPublicChat", name = "Public Chat", description = "Listen to public chat when Public Replies is also enabled.",
            position = 0, section = chatSourceSection)
    default boolean listenPublicChat() {
        return true;
    }

    @ConfigItem(keyName = "listenClanChat", name = "Clan Chat", description = "Listen to clan, group ironman clan and guest clan chat; reply in the matching channel.",
            position = 1, section = chatSourceSection)
    default boolean listenClanChat() {
        return false;
    }

    @ConfigItem(keyName = "listenFriendsChat", name = "Friends Chat", description = "Listen and reply in friends chat.",
            position = 2, section = chatSourceSection)
    default boolean listenFriendsChat() {
        return false;
    }

    @ConfigItem(keyName = "systemPrompt", name = "Personality", description = "Instructions for the AI's reply style. Replies are always limited to the configured length.",
            position = 0, section = responseSection)
    default String systemPrompt() {
        return "You are a friendly player in Old School RuneScape. "
                + "Keep responses short (under 80 characters) so they fit in the chat box. "
                + "Be casual, use OSRS slang when appropriate. Never break character.";
    }

    @ConfigItem(keyName = "respondViaPublic", name = "Public Replies", description = "Allow replies to public chat. When disabled, public messages do not use API requests.",
            position = 1, section = responseSection)
    default boolean respondViaPublic() {
        return true;
    }

    @ConfigItem(keyName = "pressEnterToSend", name = "Send Replies Automatically", description = "Enabled: type and send. Disabled: type a preview, then clear it without pressing Enter. Your own input and edited previews are preserved.",
            position = 2, section = responseSection)
    default boolean pressEnterToSend() {
        return true;
    }

    @Range(min = 20, max = 80)
    @ConfigItem(keyName = "maxResponseLength", name = "Reply Length", description = "Maximum reply characters, including any chat-channel prefix (20–80).",
            position = 3, section = responseSection)
    default int maxResponseLength() {
        return 80;
    }

    @Range(min = 0, max = 40)
    @ConfigItem(keyName = "conversationMemory", name = "Conversation Memory", description = "Maximum recent user/assistant messages for the current speaker and channel (0 disables memory).",
            position = 4, section = responseSection)
    default int conversationMemory() {
        return 10;
    }

    @Range(min = 16, max = 512)
    @ConfigItem(keyName = "maxTokens", name = "Reply Tokens", description = "Maximum generated tokens per reply (16–512). A smaller value reduces usage.",
            position = 5, section = responseSection)
    default int maxTokens() {
        return 60;
    }

    @ConfigItem(keyName = "temperature", name = "Creativity", description = "Number from 0.0 to 2.0. Lower values produce more predictable replies; default 0.7.",
            position = 6, section = responseSection)
    default String temperature() {
        return "0.7";
    }

    @Range(min = 0, max = 15000)
    @ConfigItem(keyName = "responseDelayMin", name = "Min Typing Delay (ms)", description = "Minimum wait after generating a reply before typing (0–15000 ms).",
            position = 7, section = responseSection)
    default int responseDelayMin() {
        return 2000;
    }

    @Range(min = 0, max = 15000)
    @ConfigItem(keyName = "responseDelayMax", name = "Max Typing Delay (ms)", description = "Maximum wait before typing. Reversed minimum and maximum values are normalized.",
            position = 8, section = responseSection)
    default int responseDelayMax() {
        return 5000;
    }

    @Range(min = 5, max = 120)
    @ConfigItem(keyName = "cooldownMinSeconds", name = "Min Request Pause (seconds)", description = "Minimum pause between API attempts, including failed requests. Error recovery can wait longer.",
            position = 0, section = pacingSection)
    default int cooldownMinSeconds() {
        return 5;
    }

    @Range(min = 5, max = 300)
    @ConfigItem(keyName = "cooldownMaxSeconds", name = "Max Request Pause (seconds)", description = "Maximum normal pause. A random pause is chosen between the minimum and maximum after each attempt.",
            position = 1, section = pacingSection)
    default int cooldownMaxSeconds() {
        return 15;
    }

    @Range(min = 10, max = 120)
    @ConfigItem(keyName = "messageMaxAgeSeconds", name = "Message Expiry (seconds)", description = "Only the newest eligible message waits for a reply. Discard it after this age to avoid late responses.",
            position = 2, section = pacingSection)
    default int messageMaxAgeSeconds() {
        return 60;
    }

    @ConfigItem(keyName = "cooldownSeconds", name = "Saved Cooldown", description = "Legacy cooldown retained when migrating saved settings.",
            hidden = true, position = 3, section = pacingSection)
    default int cooldownSeconds() {
        return 5;
    }

    @ConfigItem(keyName = "onlyRespondToNames", name = "Only Respond To (names)", description = "Comma-separated player names. Empty allows everyone who passes the other filters.",
            position = 0, section = filterSection)
    default String onlyRespondToNames() {
        return "";
    }

    @ConfigItem(keyName = "ignoreNames", name = "Ignore Names", description = "Comma-separated player names to ignore.",
            position = 1, section = filterSection)
    default String ignoreNames() {
        return "";
    }

    @ConfigItem(keyName = "triggerKeyword", name = "Trigger Keywords", description = "Comma-separated whole words or phrases, e.g. hi, hello, good luck. Match any one (case-insensitive). Empty disables the filter.",
            position = 2, section = filterSection)
    default String triggerKeyword() {
        return "";
    }

    @Range(min = 1, max = 50)
    @ConfigItem(keyName = "maxPublicDistance", name = "Public Chat Distance", description = "Maximum distance in tiles to a visible public-chat sender. Clan and friends chat are unaffected.",
            position = 3, section = filterSection)
    default int maxPublicDistance() {
        return 10;
    }

    @ConfigItem(keyName = "ignoreTradeSpam", name = "Ignore Trade Spam", description = "Ignore common trade advertisements, gambling and website spam before making API requests.",
            position = 4, section = filterSection)
    default boolean ignoreTradeSpam() {
        return true;
    }

    @ConfigItem(keyName = "ignoreSelf", name = "Ignore Own Messages", description = "Skip your own character's messages to prevent a reply loop.",
            position = 5, section = filterSection)
    default boolean ignoreSelf() {
        return true;
    }
}
