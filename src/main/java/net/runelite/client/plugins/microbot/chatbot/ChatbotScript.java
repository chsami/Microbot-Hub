package net.runelite.client.plugins.microbot.chatbot;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.ScriptID;
import net.runelite.api.VarClientInt;
import net.runelite.api.VarClientStr;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import java.util.function.BooleanSupplier;

@Slf4j
@Singleton
public class ChatbotScript extends Script {
    private final ChatbotConfig config;
    private final OpenAiService service;
    private final ChatbotPacing pacing;
    private final LongSupplier clock;
    private final RuntimeAccess runtime;
    private final Object lifecycleLock = new Object();
    private final AtomicBoolean requestInProgress = new AtomicBoolean();
    private volatile Session session;
    private volatile Session lastSession;

    @Inject
    public ChatbotScript(ChatbotPlugin plugin, ChatbotConfig config, OpenAiService service) {
        this.config = config;
        this.service = service;
        this.clock = System::currentTimeMillis;
        this.pacing = new ChatbotPacing();
        this.runtime = new ClientRuntime();
    }

    // Regression tests supply fake game state, API, keyboard and time.
    ChatbotScript(ChatbotConfig config, OpenAiService service, RuntimeAccess runtime,
                  LongSupplier clock, ChatbotPacing pacing) {
        this.config = config;
        this.service = service;
        this.runtime = runtime;
        this.clock = clock;
        this.pacing = pacing;
    }

    interface RuntimeAccess {
        boolean isLoggedIn();
        boolean canProcess();
        String localName();
        boolean isNearby(String sender, int distance);
        boolean chatboxReady(String expectedText);
        boolean clearPreview(String expectedText, BooleanSupplier allowed);
        void typeCharacter(char character);
        void enter();
        void delay(int milliseconds) throws InterruptedException;
    }

    public static final class IncomingMessage {
        final String sender;
        final String message;
        final String chatType;
        final long receivedAt;

        public IncomingMessage(String sender, String message, String chatType) {
            this(sender, message, chatType, System.currentTimeMillis());
        }

        IncomingMessage(String sender, String message, String chatType, long receivedAt) {
            this.sender = sender;
            this.message = message;
            this.chatType = chatType;
            this.receivedAt = receivedAt;
        }
    }

    private static final class Session {
        final AtomicReference<IncomingMessage> pending = new AtomicReference<>();
        final AtomicInteger received = new AtomicInteger();
        final AtomicLong loginGeneration = new AtomicLong();
        long workerLoginGeneration;
        // Only this session's worker owns its history. A stopped worker cannot
        // mutate a restarted session, and shutdown does not clear a live list.
        final ArrayDeque<OpenAiService.ChatMessage> history = new ArrayDeque<>();
        volatile int sent;
        volatile int previews;
        volatile int errors;
        volatile String status = "Listening";
        volatile String effectiveModel = "";
        String conversationKey = "";
        volatile String permanentError;
        volatile ConnectionSettings connection;
        volatile boolean configurationInvalid;
        boolean fallback;
    }

    private static final class ConnectionSettings {
        final String apiKey, endpoint, model;
        final ChatbotProvider provider;
        final int maxTokens;
        final double temperature;

        ConnectionSettings(ChatbotConfig config) {
            provider = config.provider();
            apiKey = text(config.openAiApiKey());
            endpoint = ChatbotProvider.getEndpoint(config);
            model = ChatbotModel.getModelName(config);
            maxTokens = Math.max(16, Math.min(512, config.maxTokens()));
            double value;
            try { value = Double.parseDouble(text(config.temperature())); }
            catch (NumberFormatException ex) { value = 0.7; }
            temperature = Double.isFinite(value) ? Math.max(0, Math.min(2, value)) : 0.7;
        }

        @Override public boolean equals(Object other) {
            if (!(other instanceof ConnectionSettings)) return false;
            ConnectionSettings that = (ConnectionSettings) other;
            return provider == that.provider && apiKey.equals(that.apiKey) && endpoint.equals(that.endpoint) && model.equals(that.model)
                && maxTokens == that.maxTokens && Double.compare(temperature, that.temperature) == 0;
        }

        @Override public int hashCode() { return Objects.hash(provider, apiKey, endpoint, model, maxTokens, temperature); }
    }

    public void enqueueMessage(String sender, String message, String chatType) {
        Session target = session;
        if (target == null || !passesFilters(sender, message, chatType)) return;
        String content = message.trim();
        IncomingMessage incoming = new IncomingMessage(sender, content.substring(0, Math.min(1_000, content.length())),
            chatType, clock.getAsLong());
        synchronized (lifecycleLock) {
            if (session != target) return;
            target.received.incrementAndGet();
            target.pending.set(incoming);
        }
    }

    private boolean passesFilters(String sender, String message, String chatType) {
        if (text(sender).isEmpty() || text(message).isEmpty() || !runtime.isLoggedIn()) return false;
        if (!channelEnabled(chatType)) return false;
        String normalized = ChatbotText.normalizeName(sender);
        if (config.ignoreSelf() && normalized.equals(ChatbotText.normalizeName(runtime.localName()))) return false;
        if (!text(config.onlyRespondToNames()).isEmpty() && !ChatbotText.nameInList(sender, config.onlyRespondToNames())) return false;
        if (ChatbotText.nameInList(sender, config.ignoreNames())) return false;
        if (!ChatbotText.matchesKeyword(message, config.triggerKeyword())) return false;
        if ("public".equals(chatType)) {
            if (config.ignoreTradeSpam() && ChatbotText.looksLikeTradeSpam(message)) return false;
            if (!runtime.isNearby(sender, Math.max(1, Math.min(50, config.maxPublicDistance())))) return false;
        }
        return true;
    }

    private boolean channelEnabled(String channel) {
        if ("public".equals(channel)) return config.listenPublicChat() && config.respondViaPublic();
        if ("clan".equals(channel) || "guestclan".equals(channel)) return config.listenClanChat();
        return "friends".equals(channel) && config.listenFriendsChat();
    }

    @Override public boolean run() { return start(true); }

    boolean start(boolean schedule) {
        synchronized (lifecycleLock) {
            if (session != null) return true;
            Session next = new Session();
            session = next;
            lastSession = next;
            if (schedule) {
                mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(
                    () -> process(next), 0, 600, TimeUnit.MILLISECONDS);
            }
            return true;
        }
    }

    void tick() { Session target = session; if (target != null) process(target); }

    private boolean active(Session target) {
        return session == target && target.workerLoginGeneration == target.loginGeneration.get()
            && !Thread.currentThread().isInterrupted() && runtime.isLoggedIn();
    }

    public void onLoginStateChanged(boolean loggedIn) {
        if (loggedIn) return;
        synchronized (lifecycleLock) {
            Session target = session;
            if (target == null) return;
            target.loginGeneration.incrementAndGet();
            target.pending.set(null);
            service.cancel();
        }
    }

    private void process(Session target) {
        if (session != target || !requestInProgress.compareAndSet(false, true)) return;
        try {
            if (!runtime.isLoggedIn()) {
                target.pending.set(null);
                target.history.clear();
                target.conversationKey = "";
                target.status = "Waiting for login";
                return;
            }
            long loginGeneration = target.loginGeneration.get();
            if (target.workerLoginGeneration != loginGeneration) {
                target.history.clear();
                target.conversationKey = "";
                target.workerLoginGeneration = loginGeneration;
            }
            if (!active(target) || !runtime.canProcess()) return;
            if (runtime instanceof ClientRuntime && !super.run()) return;
            ConnectionSettings connection;
            try { connection = new ConnectionSettings(config); }
            catch (IllegalArgumentException ex) {
                target.configurationInvalid = true;
                target.status = "Check provider, endpoint and model";
                return;
            }
            target.configurationInvalid = false;
            if (target.connection != null && !target.connection.equals(connection)) {
                // Editing settings must not bypass a provider's Retry-After.
                target.permanentError = null;
                target.fallback = false;
                target.history.clear();
            }
            target.connection = connection;
            target.effectiveModel = target.fallback ? "gemini-3.5-flash-lite" : connection.model;
            if (connection.apiKey.isEmpty()) { target.status = "Set an API key"; return; }
            if (target.permanentError != null) { target.status = target.permanentError; return; }
            pruneStale(target);
            if (pacing.remainingMs() > 0) return;
            if (!runtime.chatboxReady("")) {
                target.status = "Waiting for an empty chatbox";
                return;
            }
            IncomingMessage incoming = target.pending.getAndSet(null);
            if (incoming == null) { target.status = "Listening"; return; }
            // A queued message must still match current filters before costing quota.
            if (!passesFilters(incoming.sender, incoming.message, incoming.chatType) || expired(incoming)) return;
            String conversationKey = incoming.chatType + ":" + ChatbotText.normalizeName(incoming.sender);
            if (!conversationKey.equals(target.conversationKey)) {
                target.history.clear();
                target.conversationKey = conversationKey;
            }
            int memory = Math.max(0, Math.min(40, config.conversationMemory()));
            trimHistory(target, memory);
            List<OpenAiService.ChatMessage> messages = new ArrayList<>();
            messages.add(new OpenAiService.ChatMessage("system", text(config.systemPrompt())));
            messages.addAll(target.history);
            messages.add(new OpenAiService.ChatMessage("user", incoming.sender + ": " + incoming.message));
            target.status = "Waiting for AI";
            // Reserve a pause before opening the connection so cancellation,
            // logout or a restart cannot immediately spend another request.
            pacing.defer(cooldown());
            OpenAiService.CompletionResult result = service.getChatCompletion(connection.apiKey,
                connection.endpoint, target.effectiveModel, messages, connection.maxTokens,
                connection.temperature, () -> !active(target));
            recordResultPacing(connection, result);
            if (!active(target) || result.isCancelled() || !connection.equals(new ConnectionSettings(config))) return;
            if (!result.isSuccess()) {
                handleFailure(target, connection, incoming, result);
                return;
            }
            int totalLimit = Math.max(20, Math.min(80, config.maxResponseLength()));
            String response = ChatbotText.sanitizeResponse(result.getContent(), totalLimit);
            if (response.isEmpty()) {
                target.errors++;
                pacing.failure(0, 0, cooldown());
                target.status = "AI returned an empty reply";
                return;
            }
            // Every completed API request is paced, even if input changes prevent sending.
            if (!channelEnabled(incoming.chatType) || expired(incoming) || !runtime.canProcess()) return;
            target.status = "Preparing reply";
            runtime.delay(ChatbotText.responseDelayMs(config.responseDelayMin(), config.responseDelayMax()));
            if (!active(target) || expired(incoming) || !channelEnabled(incoming.chatType) || !runtime.canProcess()) return;
            String prefix = channelPrefix(incoming.chatType);
            response = response.substring(0, Math.min(response.length(), totalLimit - prefix.length())).trim();
            if (send(target, incoming, prefix + response)) {
                target.history.addLast(new OpenAiService.ChatMessage("user", incoming.sender + ": " + incoming.message));
                target.history.addLast(new OpenAiService.ChatMessage("assistant", response));
                trimHistory(target, memory);
                pacing.success(cooldown());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            if (session == target) {
                target.errors++;
                pacing.failure(0, 0, cooldown());
                target.status = "Could not prepare reply; waiting";
                log.warn("[Chatbot] Reply interrupted by {}", ex.getClass().getSimpleName());
            }
        } finally {
            requestInProgress.set(false);
        }
    }

    private void handleFailure(Session target, ConnectionSettings connection, IncomingMessage incoming,
                               OpenAiService.CompletionResult result) {
        target.errors++;
        if (result.isDailyQuota() && config.provider() == ChatbotProvider.GEMINI) {
            target.status = "Daily quota used; waiting for reset";
            target.pending.set(null);
        } else if (result.isPermanentFailure()) {
            target.permanentError = result.getMessage();
            target.status = result.getMessage();
            target.pending.set(null);
        } else {
            target.status = result.getMessage();
            if (config.provider() == ChatbotProvider.GEMINI && config.geminiFailover()
                    && text(config.customModel()).isEmpty() && !target.fallback
                    && "gemini-3.8-flash".equals(connection.model)
                    && (result.getStatusCode() == 429 || result.getStatusCode() == 503)) {
                // Switch once while still respecting project throttling and Retry-After.
                target.fallback = true;
                target.effectiveModel = "gemini-3.5-flash-lite";
            }
            if (!expired(incoming)) target.pending.compareAndSet(null, incoming);
        }
        log.debug("[Chatbot] API request stopped (HTTP {}), next attempt in {}s",
            result.getStatusCode(), getCooldownRemainingSeconds());
    }

    private void recordResultPacing(ConnectionSettings connection, OpenAiService.CompletionResult result) {
        if (result.isCancelled()) return;
        if (result.isSuccess()) {
            pacing.success(cooldown());
        } else if (result.isDailyQuota() && connection.provider == ChatbotProvider.GEMINI) {
            pacing.defer(Math.max(result.getRetryAfterMs(), ChatbotPacing.untilGeminiDailyResetMs(clock.getAsLong())));
        } else if (!result.isPermanentFailure()) {
            pacing.failure(result.getStatusCode(), result.getRetryAfterMs(), cooldown());
        } else {
            pacing.defer(Math.max(cooldown(), result.getRetryAfterMs()));
        }
    }

    private boolean canSend(Session target, IncomingMessage incoming) {
        return active(target) && !expired(incoming) && channelEnabled(incoming.chatType) && runtime.canProcess();
    }

    private boolean awaitInput(Session target, IncomingMessage incoming, String expectedText) throws InterruptedException {
        // Canvas key listeners queue input for a subsequent client frame. Allow
        // acknowledgement time without treating slow rendering as user input.
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        for (int attempt = 0; attempt < 40; attempt++) {
            if (!canSend(target, incoming)) return false;
            if (runtime.chatboxReady(expectedText)) return true;
            if (System.nanoTime() >= deadline) break;
            runtime.delay(25);
        }
        if (session == target) target.status = "Typing paused; check the chatbox draft";
        return false;
    }

    private boolean send(Session target, IncomingMessage incoming, String message) throws InterruptedException {
        if (!canSend(target, incoming) || !runtime.chatboxReady("")) {
            target.status = "Reply skipped; chatbox occupied";
            return false;
        }
        target.status = config.pressEnterToSend() ? "Typing reply" : "Typing preview";
        StringBuilder typed = new StringBuilder();
        for (char character : message.toCharArray()) {
            if (!awaitInput(target, incoming, typed.toString())) return false;
            synchronized (lifecycleLock) {
                if (!canSend(target, incoming)) return false;
                runtime.typeCharacter(character);
                typed.append(character);
            }
            runtime.delay(ThreadLocalRandom.current().nextInt(100, 201));
        }
        if (!awaitInput(target, incoming, typed.toString())) return false;
        synchronized (lifecycleLock) {
            if (!canSend(target, incoming)) return false;
            if (config.pressEnterToSend()) {
                runtime.enter();
                target.sent++;
                target.status = "Reply sent";
                return true;
            }
            target.previews++;
            target.status = "Clearing preview";
        }
        // Check and clear in one client frame. Do not hold the lifecycle lock
        // while waiting for that frame: login events also take the same lock.
        boolean cleared = runtime.clearPreview(typed.toString(), () -> canSend(target, incoming));
        if (session == target) target.status = cleared
            ? "Preview cleared; automatic sending is off" : "Preview left; chatbox changed";
        return false;
    }

    private static String channelPrefix(String channel) {
        if ("clan".equals(channel)) return "/c ";
        if ("guestclan".equals(channel)) return "/gc ";
        if ("friends".equals(channel)) return "/ ";
        return "";
    }

    private void pruneStale(Session target) {
        IncomingMessage queued = target.pending.get();
        if (queued != null && expired(queued)) target.pending.compareAndSet(queued, null);
    }

    private boolean expired(IncomingMessage incoming) {
        return clock.getAsLong() - incoming.receivedAt > Math.max(10, Math.min(120, config.messageMaxAgeSeconds())) * 1_000L;
    }

    private static void trimHistory(Session target, int memory) {
        while (target.history.size() > memory) target.history.removeFirst();
    }

    private long cooldown() { return ChatbotPacing.randomCooldownMs(config.cooldownMinSeconds(), config.cooldownMaxSeconds()); }
    private static String text(String value) { return value == null ? "" : value.trim(); }
    private Session visibleSession() { Session current = session; return current == null ? lastSession : current; }
    public int getMessagesReceived() { Session target = visibleSession(); return target == null ? 0 : target.received.get(); }
    public int getResponsesSent() { Session target = visibleSession(); return target == null ? 0 : target.sent; }
    public int getPreviewCount() { Session target = visibleSession(); return target == null ? 0 : target.previews; }
    public int getApiErrors() { Session target = visibleSession(); return target == null ? 0 : target.errors; }
    public long getCooldownRemainingSeconds() { return session == null ? 0 : (pacing.remainingMs() + 999) / 1_000; }
    public boolean isRequestPaused() {
        Session target = session;
        return target == null || target.configurationInvalid || target.permanentError != null
            || target.connection == null || target.connection.apiKey.isEmpty();
    }
    public String getEffectiveModel() { Session target = visibleSession(); return target == null ? "" : target.effectiveModel; }
    public String getLastStatus() { Session target = session; return target == null ? "Stopped" : target.status; }

    @Override public void shutdown() {
        synchronized (lifecycleLock) {
            Session old = session;
            session = null;
            if (old != null) {
                old.pending.set(null);
                // Keep overlay totals, without retaining chat history or keys.
                Session totals = new Session();
                totals.received.set(old.received.get());
                totals.sent = old.sent;
                totals.previews = old.previews;
                totals.errors = old.errors;
                totals.effectiveModel = old.effectiveModel;
                lastSession = totals;
            }
            super.shutdown();
            service.cancel();
        }
    }

    private final class ClientRuntime implements RuntimeAccess {
        @Override public boolean isLoggedIn() { return Microbot.isLoggedIn(); }
        @Override public boolean canProcess() { return !Microbot.pauseAllScripts.get(); }
        @Override public String localName() {
            return Microbot.getClientThread().runOnClientThreadOptional(() -> {
                Player local = Microbot.getClient().getLocalPlayer();
                return local == null ? "" : text(local.getName());
            }).orElse("");
        }
        @Override public boolean isNearby(String sender, int distance) {
            return Microbot.getClientThread().runOnClientThreadOptional(() -> {
                Client client = Microbot.getClient();
                Player local = client.getLocalPlayer();
                if (local == null || local.getWorldLocation() == null) return false;
                for (Player player : client.getPlayers()) {
                    if (player != null && ChatbotText.normalizeName(sender).equals(ChatbotText.normalizeName(player.getName()))
                            && player.getWorldLocation() != null
                            && local.getWorldLocation().getPlane() == player.getWorldLocation().getPlane()
                            && local.getWorldLocation().distanceTo(player.getWorldLocation()) <= distance) return true;
                }
                return false;
            }).orElse(false);
        }
        @Override public boolean chatboxReady(String expectedText) {
            return Microbot.getClientThread().runOnClientThreadOptional(
                () -> chatboxReady(Microbot.getClient(), expectedText)).orElse(false);
        }
        @Override public boolean clearPreview(String expectedText, BooleanSupplier allowed) {
            return Microbot.getClientThread().runOnClientThreadOptional(() -> {
                Client client = Microbot.getClient();
                if (!allowed.getAsBoolean() || !chatboxReady(client, expectedText)) return false;
                client.setVarcStrValue(VarClientStr.CHATBOX_TYPED_TEXT, "");
                client.runScript(ScriptID.CHAT_TEXT_INPUT_REBUILD, "");
                return true;
            }).orElse(false);
        }
        private boolean chatboxReady(Client client, String expectedText) {
            Widget input = client.getWidget(WidgetInfo.CHATBOX_INPUT);
            return Microbot.isLoggedIn() && client.getVarcIntValue(VarClientInt.INPUT_TYPE) == 0
                && input != null && !input.isHidden()
                && expectedText.equals(Objects.toString(client.getVarcStrValue(VarClientStr.CHATBOX_TYPED_TEXT), ""));
        }
        @Override public void typeCharacter(char character) { Rs2Keyboard.keyPress(character); }
        @Override public void enter() { Rs2Keyboard.enter(); }
        @Override public void delay(int milliseconds) throws InterruptedException { Thread.sleep(milliseconds); }
    }
}
