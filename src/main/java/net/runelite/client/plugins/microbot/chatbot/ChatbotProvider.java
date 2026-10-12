package net.runelite.client.plugins.microbot.chatbot;

import java.net.URI;
import java.net.URISyntaxException;

public enum ChatbotProvider {
    OPENAI("OpenAI"),
    GEMINI("Google Gemini"),
    CUSTOM("Custom (OpenAI-compatible)");

    private final String displayName;

    ChatbotProvider(String displayName) {
        this.displayName = displayName;
    }

    public static String getEndpoint(ChatbotConfig config) {
        ChatbotProvider provider = config.provider();
        if (provider == GEMINI) {
            return "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions";
        }
        if (provider != CUSTOM) {
            return "https://api.openai.com/v1/chat/completions";
        }
        String endpoint = config.customEndpoint();
        endpoint = endpoint == null ? "" : endpoint.trim();
        try {
            URI uri = new URI(endpoint);
            String scheme = uri.getScheme();
            if ((!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme))
                    || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("Set a full HTTP(S) Custom Endpoint URL without embedded credentials or a fragment.");
            }
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("Set a valid full Custom Endpoint URL.");
        }
        return endpoint;
    }

    @Override
    public String toString() {
        return displayName;
    }
}