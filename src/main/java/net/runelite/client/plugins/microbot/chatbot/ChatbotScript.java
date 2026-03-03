package net.runelite.client.plugins.microbot.chatbot;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class ChatbotScript extends Script {

    private final ChatbotPlugin plugin;
    private final ChatbotConfig config;

    /**
     * Queue of incoming chat messages that need a response.
     */
    private final ConcurrentLinkedQueue<IncomingMessage> messageQueue = new ConcurrentLinkedQueue<>();

    /**
     * Conversation history for context.
     */
    private final LinkedList<OpenAiService.ChatMessage> conversationHistory = new LinkedList<>();

    /**
     * Timestamp of last response sent (for cooldown).
     */
    private volatile long lastResponseTime = 0;

    /**
     * Counters for the overlay.
     */
    private volatile int messagesReceived = 0;
    private volatile int responsesSent = 0;
    private volatile String lastStatus = "Idle";

    @Inject
    public ChatbotScript(ChatbotPlugin plugin, ChatbotConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    /**
     * Simple container for an incoming message.
     */
    public static class IncomingMessage {
        final String sender;
        final String message;
        final String chatType;

        public IncomingMessage(String sender, String message, String chatType) {
            this.sender = sender;
            this.message = message;
            this.chatType = chatType;
        }
    }

    /**
     * Enqueue a message to be processed.
     */
    public void enqueueMessage(String sender, String message, String chatType) {
        // Apply filters before queueing
        if (!passesFilters(sender, message)) {
            return;
        }
        messagesReceived++;
        messageQueue.offer(new IncomingMessage(sender, message, chatType));
        log.info("[Chatbot] Queued message from {} ({}): {}", sender, chatType, message);
    }

    private boolean passesFilters(String sender, String message) {
        // Ignore self
        if (config.ignoreSelf() && Microbot.isLoggedIn()) {
            String localName = Microbot.getClient().getLocalPlayer().getName();
            if (localName != null && localName.equalsIgnoreCase(sender)) {
                return false;
            }
        }

        // Only respond to specific names
        String onlyNames = config.onlyRespondToNames().trim();
        if (!onlyNames.isEmpty()) {
            Set<String> allowed = Arrays.stream(onlyNames.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
            if (!allowed.contains(sender.toLowerCase())) {
                return false;
            }
        }

        // Ignore specific names
        String ignoreList = config.ignoreNames().trim();
        if (!ignoreList.isEmpty()) {
            Set<String> ignored = Arrays.stream(ignoreList.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
            if (ignored.contains(sender.toLowerCase())) {
                return false;
            }
        }

        // Trigger keyword
        String keyword = config.triggerKeyword().trim();
        if (!keyword.isEmpty()) {
            if (!message.toLowerCase().contains(keyword.toLowerCase())) {
                return false;
            }
        }

        return true;
    }

    public boolean run() {
        Microbot.enableAutoRunOn = false;
        lastStatus = "Running";
        messagesReceived = 0;
        responsesSent = 0;
        conversationHistory.clear();
        messageQueue.clear();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                // Check API key is configured
                String apiKey = config.openAiApiKey().trim();
                if (apiKey.isEmpty()) {
                    lastStatus = "No API Key set!";
                    return;
                }

                // Process next message from queue
                IncomingMessage incoming = messageQueue.poll();
                if (incoming == null) {
                    lastStatus = "Listening...";
                    return;
                }

                // Cooldown check
                long now = System.currentTimeMillis();
                long cooldownMs = config.cooldownSeconds() * 1000L;
                if (now - lastResponseTime < cooldownMs) {
                    log.info("[Chatbot] Cooldown active, re-queueing message");
                    messageQueue.offer(incoming);
                    lastStatus = "Cooldown...";
                    return;
                }

                lastStatus = "Thinking...";
                log.info("[Chatbot] Processing message from {}: {}", incoming.sender, incoming.message);

                // Add user message to conversation history
                String userContent = incoming.sender + ": " + incoming.message;
                conversationHistory.add(new OpenAiService.ChatMessage("user", userContent));

                // Trim conversation history
                while (conversationHistory.size() > config.conversationMemory()) {
                    conversationHistory.removeFirst();
                }

                // Build messages list for OpenAI
                List<OpenAiService.ChatMessage> apiMessages = new ArrayList<>();
                apiMessages.add(new OpenAiService.ChatMessage("system", config.systemPrompt()));
                apiMessages.addAll(conversationHistory);

                // Parse temperature
                double temp;
                try {
                    temp = Double.parseDouble(config.temperature());
                } catch (NumberFormatException e) {
                    temp = 0.7;
                }

                // Call OpenAI
                String response = OpenAiService.getChatCompletion(
                    apiKey,
                    config.openAiModel(),
                    apiMessages,
                    config.maxTokens(),
                    temp
                );

                if (response == null || response.isEmpty()) {
                    lastStatus = "API Error";
                    log.warn("[Chatbot] Empty response from OpenAI");
                    return;
                }

                // Truncate response to max length
                int maxLen = config.maxResponseLength();
                if (response.length() > maxLen) {
                    response = response.substring(0, maxLen);
                }

                // Remove any newlines (OSRS chat is single-line)
                response = response.replace("\n", " ").replace("\r", "").trim();

                // Add assistant response to history
                conversationHistory.add(new OpenAiService.ChatMessage("assistant", response));

                // Human-like response delay
                int delayMin = config.responseDelayMin();
                int delayMax = config.responseDelayMax();
                int delay = delayMin + (int) (Math.random() * (delayMax - delayMin));
                lastStatus = "Typing...";
                sleep(delay);

                // Send the response in-game
                if ("clan".equalsIgnoreCase(incoming.chatType)) {
                    sendClanMessage(response);
                } else if (config.respondViaPublic()) {
                    sendPublicMessage(response);
                }

                lastResponseTime = System.currentTimeMillis();
                responsesSent++;
                lastStatus = "Sent response";
                log.info("[Chatbot] Responded: {}", response);

            } catch (Exception ex) {
                log.error("[Chatbot] Exception in main loop: ", ex);
                lastStatus = "Error: " + ex.getMessage();
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    /**
     * Types and sends a message in the public chatbox.
     */
    private void sendPublicMessage(String message) {
        Rs2Keyboard.typeString(message);
        sleep(200, 400);
        Rs2Keyboard.enter();
    }

    /**
     * Sends a clan chat message.
     */
    private void sendClanMessage(String message) {
        String clanCommand = "/c " + message;
        Rs2Keyboard.typeString(clanCommand);
        sleep(200, 400);
        Rs2Keyboard.enter();
    }

    public int getMessagesReceived() {
        return messagesReceived;
    }

    public int getResponsesSent() {
        return responsesSent;
    }

    public String getLastStatus() {
        return lastStatus;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        messageQueue.clear();
        conversationHistory.clear();
        lastStatus = "Stopped";
    }
}