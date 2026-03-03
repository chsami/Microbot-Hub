package net.runelite.client.plugins.microbot.chatbot;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(ChatbotConfig.configGroup)
public interface ChatbotConfig extends Config {
	String configGroup = "micro-chatbot";

	// ── Sections ───────────────────────────────────────────────────────

	@ConfigSection(
		name = "OpenAI Settings",
		description = "OpenAI API configuration",
		position = 0
	)
	String openAiSection = "openAi";

	@ConfigSection(
		name = "Chat Sources",
		description = "Which chat types to listen to",
		position = 1
	)
	String chatSourceSection = "chatSources";

	@ConfigSection(
		name = "Response Settings",
		description = "How the bot responds",
		position = 2
	)
	String responseSection = "response";

	@ConfigSection(
		name = "Filtering",
		description = "Message filtering options",
		position = 3
	)
	String filterSection = "filtering";

	// ── OpenAI Settings ────────────────────────────────────────────────

	@ConfigItem(
		keyName = "openAiApiKey",
		name = "API Key",
		description = "Your OpenAI API key (sk-...)",
		position = 0,
		section = openAiSection,
		secret = true
	)
	default String openAiApiKey() {
		return "";
	}

	@ConfigItem(
		keyName = "openAiModel",
		name = "Model",
		description = "The OpenAI model to use (e.g. gpt-4o-mini, gpt-4o, gpt-3.5-turbo)",
		position = 1,
		section = openAiSection
	)
	default String openAiModel() {
		return "gpt-4o-mini";
	}

	@ConfigItem(
		keyName = "systemPrompt",
		name = "System Prompt",
		description = "The system prompt that defines the chatbot's personality and behavior.",
		position = 2,
		section = openAiSection
	)
	default String systemPrompt() {
		return "You are a friendly player in Old School RuneScape. "
			+ "Keep responses short (under 80 characters) so they fit in the chat box. "
			+ "Be casual, use OSRS slang when appropriate. Never break character.";
	}

	@ConfigItem(
		keyName = "maxTokens",
		name = "Max Tokens",
		description = "Maximum tokens for each OpenAI response (lower = shorter replies)",
		position = 3,
		section = openAiSection
	)
	default int maxTokens() {
		return 60;
	}

	@ConfigItem(
		keyName = "temperature",
		name = "Temperature",
		description = "Creativity of responses (0.0 = deterministic, 2.0 = very creative). Use values like 0.7",
		position = 4,
		section = openAiSection
	)
	default String temperature() {
		return "0.7";
	}

	// ── Chat Sources ───────────────────────────────────────────────────

	@ConfigItem(
		keyName = "listenPublicChat",
		name = "Public Chat",
		description = "Listen and respond to public chat messages",
		position = 0,
		section = chatSourceSection
	)
	default boolean listenPublicChat() {
		return true;
	}

	@ConfigItem(
		keyName = "listenClanChat",
		name = "Clan Chat",
		description = "Listen and respond to clan chat messages",
		position = 1,
		section = chatSourceSection
	)
	default boolean listenClanChat() {
		return false;
	}

	@ConfigItem(
		keyName = "listenFriendsChat",
		name = "Friends Chat",
		description = "Listen and respond to friends chat messages",
		position = 2,
		section = chatSourceSection
	)
	default boolean listenFriendsChat() {
		return false;
	}

	// ── Response Settings ──────────────────────────────────────────────

	@ConfigItem(
		keyName = "responseDelayMin",
		name = "Min Response Delay (ms)",
		description = "Minimum delay before responding (milliseconds) to appear more human",
		position = 0,
		section = responseSection
	)
	default int responseDelayMin() {
		return 2000;
	}

	@ConfigItem(
		keyName = "responseDelayMax",
		name = "Max Response Delay (ms)",
		description = "Maximum delay before responding (milliseconds)",
		position = 1,
		section = responseSection
	)
	default int responseDelayMax() {
		return 5000;
	}

	@ConfigItem(
		keyName = "respondViaPublic",
		name = "Respond in Public",
		description = "Send responses via public chat (typed in chatbox)",
		position = 2,
		section = responseSection
	)
	default boolean respondViaPublic() {
		return true;
	}

	@ConfigItem(
		keyName = "maxResponseLength",
		name = "Max Response Length",
		description = "Maximum character length for responses (OSRS chat limit is 80)",
		position = 3,
		section = responseSection
	)
	default int maxResponseLength() {
		return 80;
	}

	@ConfigItem(
		keyName = "conversationMemory",
		name = "Conversation Memory",
		description = "Number of recent messages to include as context for the AI",
		position = 4,
		section = responseSection
	)
	default int conversationMemory() {
		return 10;
	}

	// ── Filtering ──────────────────────────────────────────────────────

	@ConfigItem(
		keyName = "onlyRespondToNames",
		name = "Only Respond To (names)",
		description = "Comma-separated list of player names to respond to. Leave empty to respond to everyone.",
		position = 0,
		section = filterSection
	)
	default String onlyRespondToNames() {
		return "";
	}

	@ConfigItem(
		keyName = "ignoreNames",
		name = "Ignore Names",
		description = "Comma-separated list of player names to ignore.",
		position = 1,
		section = filterSection
	)
	default String ignoreNames() {
		return "";
	}

	@ConfigItem(
		keyName = "triggerKeyword",
		name = "Trigger Keyword",
		description = "Only respond when message contains this keyword. Leave empty to respond to all messages.",
		position = 2,
		section = filterSection
	)
	default String triggerKeyword() {
		return "";
	}

	@ConfigItem(
		keyName = "ignoreSelf",
		name = "Ignore Own Messages",
		description = "Don't respond to your own messages",
		position = 3,
		section = filterSection
	)
	default boolean ignoreSelf() {
		return true;
	}

	@ConfigItem(
		keyName = "cooldownSeconds",
		name = "Cooldown (seconds)",
		description = "Minimum seconds between responses to avoid spamming",
		position = 4,
		section = filterSection
	)
	default int cooldownSeconds() {
		return 5;
	}
}
