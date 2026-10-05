package net.runelite.client.plugins.microbot.chatbot;

/** Curated Gemini models; custom provider model IDs remain available in configuration. */
public enum ChatbotModel {
    GEMINI_3_5_FLASH_LITE("gemini-3.5-flash-lite", "Gemini 3.5 Flash-Lite"),
    GEMINI_3_8_FLASH("gemini-3.8-flash", "Gemini 3.8 Flash");

    private final String modelId;
    private final String displayName;

    ChatbotModel(String modelId, String displayName) {
        this.modelId = modelId;
        this.displayName = displayName;
    }

    public String getModelId() {
        return modelId;
    }

    public static String getModelName(ChatbotConfig config) {
        String customModel = trim(config.customModel());
        if (!customModel.isEmpty()) {
            return customModel;
        }
        if (config.provider() == ChatbotProvider.CUSTOM) {
            throw new IllegalArgumentException("Set a Custom Model for the Custom provider.");
        }
        if (config.provider() == ChatbotProvider.GEMINI) {
            ChatbotModel selected = config.model();
            return (selected == null ? GEMINI_3_5_FLASH_LITE : selected).modelId;
        }
        String savedOpenAiModel = trim(config.openAiModel());
        return savedOpenAiModel.isEmpty() ? "gpt-4o-mini" : savedOpenAiModel;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    @Override
    public String toString() {
        return displayName;
    }
}