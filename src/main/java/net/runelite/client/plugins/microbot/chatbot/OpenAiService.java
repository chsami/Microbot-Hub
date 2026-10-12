package net.runelite.client.plugins.microbot.chatbot;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** One cancellable request to an OpenAI-compatible API. The script owns pacing and retries. */
public class OpenAiService {
    private static final Gson GSON = new Gson();
    private static final int MAX_REQUEST_BYTES = 256 * 1024;
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    private static final int MAX_ERROR_BYTES = 64 * 1024;
    // Saturate arithmetic only. Real provider hints must not be shortened to our backoff cap.
    private static final long MAX_RETRY_AFTER_MS = Long.MAX_VALUE / 2;
    private final ConnectionFactory connectionFactory;
    private final LongSupplier clock;
    private final AtomicReference<RequestState> activeRequest = new AtomicReference<>();

    public OpenAiService() {
        this(url -> (HttpURLConnection) url.openConnection(), System::currentTimeMillis);
    }

    OpenAiService(ConnectionFactory connectionFactory) {
        this(connectionFactory, System::currentTimeMillis);
    }

    OpenAiService(ConnectionFactory connectionFactory, LongSupplier clock) {
        this.connectionFactory = connectionFactory;
        this.clock = clock;
    }

    @FunctionalInterface
    interface ConnectionFactory {
        HttpURLConnection open(URL url) throws IOException;
    }

    public static class ChatMessage {
        private final String role;
        private final String content;

        public ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() { return role; }
        public String getContent() { return content; }
    }

    /** Safe-to-display outcome: provider error bodies are never returned or logged. */
    public static final class CompletionResult {
        private final String content;
        private final int statusCode;
        private final long retryAfterMs;
        private final boolean permanentFailure;
        private final boolean dailyQuota;
        private final String message;
        private final boolean cancelled;

        private CompletionResult(String content, int statusCode, long retryAfterMs,
                                 boolean permanentFailure, boolean dailyQuota,
                                 String message, boolean cancelled) {
            this.content = content;
            this.statusCode = statusCode;
            this.retryAfterMs = Math.max(0, retryAfterMs);
            this.permanentFailure = permanentFailure;
            this.dailyQuota = dailyQuota;
            this.message = message;
            this.cancelled = cancelled;
        }

        public static CompletionResult success(String content) {
            return success(content, 200);
        }

        public static CompletionResult success(String content, int statusCode) {
            return new CompletionResult(content, statusCode, 0, false, false, "Reply ready", false);
        }

        public static CompletionResult failure(int statusCode, long retryAfterMs,
                                               boolean permanentFailure, boolean dailyQuota,
                                               String message) {
            return new CompletionResult(null, statusCode, retryAfterMs,
                    permanentFailure, dailyQuota, message, false);
        }

        public static CompletionResult cancelled() {
            return new CompletionResult(null, 0, 0, false, false, "Request cancelled", true);
        }

        public boolean isSuccess() { return content != null && !content.isEmpty() && !cancelled; }
        public String getContent() { return content; }
        public int getStatusCode() { return statusCode; }
        public long getRetryAfterMs() { return retryAfterMs; }
        public boolean isPermanentFailure() { return permanentFailure; }
        public boolean isDailyQuota() { return dailyQuota; }
        public String getMessage() { return message; }
        public boolean isCancelled() { return cancelled; }
    }

    /** Cancels the current request without poisoning a later request after plugin restart. */
    public void cancel() {
        RequestState state = activeRequest.get();
        if (state != null) {
            state.cancelled.set(true);
            disconnect(state.connection);
        }
    }

    public CompletionResult getChatCompletion(String apiKey, String endpoint, String model,
                                               List<ChatMessage> messages, int maxTokens,
                                               double temperature, BooleanSupplier cancelled) {
        RequestState state = new RequestState(cancelled);
        if (state.isCancelled()) {
            return CompletionResult.cancelled();
        }
        // Never replace another generation's connection while it is being cancelled.
        if (!activeRequest.compareAndSet(null, state)) {
            return CompletionResult.failure(0, 1000, false, false, "A request is already running");
        }
        try {
            URL url;
            byte[] request;
            try {
                url = validateEndpoint(endpoint);
                request = buildRequest(apiKey, model, messages, maxTokens, temperature);
            } catch (IllegalArgumentException | URISyntaxException e) {
                return CompletionResult.failure(0, 0, true, false,
                        "Check the API key, model and chat-completions endpoint");
            }
            checkCancelled(state);
            HttpURLConnection connection = connectionFactory.open(url);
            state.connection = connection;
            checkCancelled(state);
            // Authorization must never be forwarded through an automatic redirect.
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey.trim());
            connection.setDoOutput(true);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setFixedLengthStreamingMode(request.length);
            try (OutputStream output = connection.getOutputStream()) {
                for (int offset = 0; offset < request.length; offset += 4096) {
                    checkCancelled(state);
                    output.write(request, offset, Math.min(4096, request.length - offset));
                }
            }
            checkCancelled(state);
            int status = connection.getResponseCode();
            if (status >= 300 && status < 400) {
                return CompletionResult.failure(status, 0, true, false,
                        "Endpoint redirected; use its direct chat-completions URL");
            }
            if (status < 200 || status >= 300) {
                String retryAfter = connection.getHeaderField("Retry-After");
                // Once error headers arrived, retain their pacing hint even if stopping
                // interrupts the body read. The script checks its session before using it.
                if (state.isCancelled()) {
                    return errorResult(status, retryAfter, "");
                }
                String body;
                try {
                    body = readBody(connection.getErrorStream(), MAX_ERROR_BYTES, state, true);
                } catch (IOException e) {
                    body = ""; // Preserve HTTP status and Retry-After even with an unreadable body.
                }
                return errorResult(status, retryAfter, body);
            }
            checkCancelled(state);
            String body = readBody(connection.getInputStream(), MAX_RESPONSE_BYTES, state, false);
            checkCancelled(state);
            String content = responseContent(parseObject(body));
            if (content == null || content.trim().isEmpty()) {
                return CompletionResult.failure(status, 0, false, false, "Provider returned no usable reply");
            }
            return CompletionResult.success(content.trim(), status);
        } catch (RequestCancelledException e) {
            return CompletionResult.cancelled();
        } catch (SocketTimeoutException e) {
            return state.isCancelled() ? CompletionResult.cancelled()
                    : CompletionResult.failure(0, 0, false, false, "Provider request timed out");
        } catch (IOException | RuntimeException e) {
            return state.isCancelled() ? CompletionResult.cancelled()
                    : CompletionResult.failure(0, 0, false, false, "Could not complete the provider request");
        } finally {
            disconnect(state.connection);
            activeRequest.compareAndSet(state, null);
        }
    }

    private static URL validateEndpoint(String endpoint) throws URISyntaxException, IOException {
        if (endpoint == null || endpoint.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing endpoint");
        }
        URI uri = new URI(endpoint.trim());
        String host = uri.getHost();
        String scheme = uri.getScheme();
        int port = uri.getPort();
        boolean loopback = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)
                || "::1".equals(host) || "[::1]".equals(host);
        if (host == null || port == 0 || port > 65535 || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                || uri.getRawFragment() != null || !("https".equalsIgnoreCase(scheme)
                || (loopback && "http".equalsIgnoreCase(scheme)))) {
            throw new IllegalArgumentException("Invalid endpoint");
        }
        return uri.toURL();
    }

    private static byte[] buildRequest(String apiKey, String model, List<ChatMessage> messages,
                                       int maxTokens, double temperature) {
        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.indexOf('\n') >= 0
                || apiKey.indexOf('\r') >= 0 || model == null || model.trim().isEmpty()
                || messages == null || messages.isEmpty() || messages.size() > 100) {
            throw new IllegalArgumentException("Invalid request settings");
        }
        JsonObject body = new JsonObject();
        body.addProperty("model", model.trim());
        body.addProperty("max_tokens", Math.max(1, Math.min(4096, maxTokens)));
        body.addProperty("temperature", Double.isFinite(temperature)
                ? Math.max(0, Math.min(2, temperature)) : 0.7);
        JsonArray messageArray = new JsonArray();
        for (ChatMessage message : messages) {
            if (message == null || message.getRole() == null || message.getContent() == null) {
                throw new IllegalArgumentException("Invalid message");
            }
            JsonObject object = new JsonObject();
            object.addProperty("role", message.getRole());
            object.addProperty("content", message.getContent());
            messageArray.add(object);
        }
        body.add("messages", messageArray);
        byte[] request = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        if (request.length > MAX_REQUEST_BYTES) {
            throw new IllegalArgumentException("Request too large");
        }
        return request;
    }

    private static String readBody(InputStream stream, int limit, RequestState state,
                                   boolean truncate) throws IOException {
        if (stream == null) {
            return "";
        }
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            while (true) {
                if (truncate && state.isCancelled()) {
                    break; // Keep any complete error JSON already received with its headers.
                }
                if (!truncate) {
                    checkCancelled(state);
                }
                int count;
                try {
                    count = input.read(buffer);
                } catch (IOException e) {
                    if (!truncate) {
                        throw e;
                    }
                    break;
                }
                if (count < 0) {
                    break;
                }
                if (output.size() + count > limit) {
                    if (!truncate) {
                        throw new IOException("Response exceeded the size limit");
                    }
                    output.write(buffer, 0, limit - output.size());
                    break;
                }
                output.write(buffer, 0, count);
            }
            if (!truncate) {
                checkCancelled(state);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private CompletionResult errorResult(int status, String retryAfter, String body) {
        JsonObject errorBody = parseObject(body);
        String errorText = errorBody == null ? "" : errorBody.toString().toLowerCase(Locale.ROOT);
        boolean dailyQuota = status == 429 && (errorText.contains("requestsperday")
                || errorText.contains("tokensperday") || errorText.contains("per_day")
                || errorText.contains("per day") || errorText.contains("daily")
                || errorText.contains("perday"));
        boolean billingQuota = errorText.contains("insufficient_quota")
                || errorText.contains("billing_hard_limit") || errorText.contains("billing_not_active")
                || errorText.contains("billing_limit") || errorText.contains("account_deactivated")
                || errorText.contains("credit_balance_exhausted")
                || errorText.contains("organization_spend_limit_exceeded")
                || errorText.contains("project_spend_limit_exceeded")
                || errorText.contains("organization_usage_limit_exceeded");
        boolean permanent = dailyQuota || billingQuota || status == 400 || status == 401
                || status == 403 || status == 404 || status == 405 || status == 422;
        long retryAfterMs = Math.max(parseRetryAfter(retryAfter), googleRetryAfter(errorBody, 0));
        String message;
        if (dailyQuota) {
            message = "Daily project quota reached";
        } else if (billingQuota) {
            message = "API quota or billing allowance exhausted";
        } else if (status == 429) {
            message = "Provider rate limit reached; waiting before another request";
        } else if (status == 401 || status == 403) {
            message = "Provider rejected the API key or access permissions";
        } else if (permanent) {
            message = "Check the provider endpoint and model settings";
        } else if (status >= 500) {
            message = "Provider is temporarily unavailable";
        } else {
            message = "Provider could not complete the request (HTTP " + status + ")";
        }
        return CompletionResult.failure(status, retryAfterMs, permanent, dailyQuota, message);
    }

    private long parseRetryAfter(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0;
        }
        try {
            double seconds = Double.parseDouble(value.trim());
            if (!Double.isNaN(seconds) && seconds >= 0) {
                return boundedMillis(seconds * 1000);
            }
        } catch (NumberFormatException ignored) {
            // HTTP-date is the other allowed Retry-After representation.
        }
        try {
            long deadline = ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toInstant().toEpochMilli();
            return boundedMillis((double) deadline - clock.getAsLong());
        } catch (DateTimeParseException | ArithmeticException ignored) {
            return 0;
        }
    }

    private static long googleRetryAfter(JsonElement element, int depth) {
        if (element == null || element.isJsonNull() || depth > 32) {
            return 0;
        }
        long retryAfter = 0;
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            String type = stringValue(object.get("@type"));
            String delay = stringValue(object.get("retryDelay"));
            if (type != null && type.endsWith("google.rpc.RetryInfo") && delay != null
                    && delay.endsWith("s")) {
                try {
                    retryAfter = boundedMillis(Double.parseDouble(delay.substring(0, delay.length() - 1)) * 1000);
                } catch (NumberFormatException ignored) {
                    // Malformed hints do not prevent status classification.
                }
            }
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                retryAfter = Math.max(retryAfter, googleRetryAfter(entry.getValue(), depth + 1));
            }
        } else if (element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) {
                retryAfter = Math.max(retryAfter, googleRetryAfter(item, depth + 1));
            }
        }
        return retryAfter;
    }

    private static long boundedMillis(double milliseconds) {
        if (Double.isNaN(milliseconds) || milliseconds <= 0) {
            return 0;
        }
        if (milliseconds >= MAX_RETRY_AFTER_MS) {
            return MAX_RETRY_AFTER_MS;
        }
        return (long) Math.ceil(milliseconds);
    }

    private static JsonObject parseObject(String body) {
        if (body == null || body.isEmpty()) {
            return null;
        }
        try {
            JsonElement element = new JsonParser().parse(body);
            return element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String responseContent(JsonObject response) {
        if (response == null) {
            return null;
        }
        JsonElement choicesElement = response.get("choices");
        if (choicesElement == null || !choicesElement.isJsonArray()) {
            return null;
        }
        JsonArray choices = choicesElement.getAsJsonArray();
        if (choices.size() == 0 || !choices.get(0).isJsonObject()) {
            return null;
        }
        JsonElement messageElement = choices.get(0).getAsJsonObject().get("message");
        if (messageElement == null || !messageElement.isJsonObject()) {
            return null;
        }
        JsonElement content = messageElement.getAsJsonObject().get("content");
        String text = stringValue(content);
        if (text != null) {
            return text;
        }
        if (content != null && content.isJsonArray()) {
            StringBuilder combined = new StringBuilder();
            for (JsonElement part : content.getAsJsonArray()) {
                if (part.isJsonObject()) {
                    String partText = stringValue(part.getAsJsonObject().get("text"));
                    if (partText != null) {
                        combined.append(partText);
                    }
                }
            }
            return combined.toString();
        }
        return null;
    }

    private static String stringValue(JsonElement element) {
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
                ? element.getAsString() : null;
    }

    private static void disconnect(HttpURLConnection connection) {
        if (connection != null) {
            try {
                connection.disconnect();
            } catch (RuntimeException ignored) {
                // A cleanup failure must not mask the result or retain the active request.
            }
        }
    }

    private static void checkCancelled(RequestState state) throws RequestCancelledException {
        if (state.isCancelled()) {
            throw new RequestCancelledException();
        }
    }

    private static final class RequestState {
        private final BooleanSupplier externalCancellation;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private volatile HttpURLConnection connection;

        private RequestState(BooleanSupplier externalCancellation) {
            this.externalCancellation = externalCancellation;
        }

        private boolean isCancelled() {
            return cancelled.get() || Thread.currentThread().isInterrupted()
                    || (externalCancellation != null && externalCancellation.getAsBoolean());
        }
    }

    private static final class RequestCancelledException extends IOException {
        private static final long serialVersionUID = 1L;
    }
}
