package net.runelite.client.plugins.microbot.chatbot;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Isolated regression harness: fake HTTP connections and clocks, no provider or game access.
 * Run scripts/test_chatbot.ps1 after compiling the chatbot source set.
 */
public final class ChatbotRegressionTest
{
    private static final String ENDPOINT = "https://example.invalid/v1/chat/completions";
    private static int cases;
    private static int checks;

    private ChatbotRegressionTest()
    {
    }

    public static void main(String[] args) throws Exception
    {
        testPacing();
        testText();
        testHttp();
        testModels();
        testScript();
        testInjection();
        System.out.println("PASS: " + cases + " chatbot regression cases, " + checks
            + " checks; no live API requests or game actions.");
    }

    private static void testPacing()
    {
        test("429 attempts back off exponentially", () -> {
            AtomicLong now = new AtomicLong(1_000L);
            ChatbotPacing pacing = new ChatbotPacing(now::get, (min, max) -> min);
            long first = pacing.failure(429, 0L, 5_000L);
            check(first >= 30_000L, "429 must wait at least 30 seconds");
            check(pacing.remainingMs() == first, "first failure installs deadline");
            now.addAndGet(first);
            check(pacing.remainingMs() == 0L, "deadline expires without sleeping");
            long second = pacing.failure(429, 0L, 5_000L);
            check(second >= first * 2L, "repeated failures must increase backoff");
            check(pacing.getConsecutiveFailures() == 2, "failure count retained");
        });
        test("503 and unexpected failures install cooldowns", () -> {
            AtomicLong now = new AtomicLong(1_000L);
            ChatbotPacing pacing = new ChatbotPacing(now::get, (min, max) -> min);
            check(pacing.failure(503, 0L, 15_000L) >= 30_000L, "503 waits at least 30 seconds");
            pacing.reset();
            check(pacing.failure(0, 0L, 15_000L) >= 15_000L,
                "network/unhandled failure cannot bypass configured cooldown");
            check(pacing.remainingMs() >= 15_000L, "failure records next deadline");
        });
        test("long Retry-After survives exponential backoff cap", () -> {
            ChatbotPacing pacing = new ChatbotPacing(() -> 1_000L, (min, max) -> min);
            long longRetry = 7_200_000L;
            check(pacing.failure(429, longRetry, 5_000L) >= longRetry,
                "provider asks for two hours; local cap must not shorten it");
            check(pacing.remainingMs() >= longRetry, "long provider deadline preserved");
        });
        test("success clears failure streak and preserves configured pacing", () -> {
            AtomicLong now = new AtomicLong(1_000L);
            ChatbotPacing pacing = new ChatbotPacing(now::get, (min, max) -> min);
            now.addAndGet(pacing.failure(429, 0L, 5_000L));
            pacing.success(15_000L);
            check(pacing.getConsecutiveFailures() == 0, "success resets exponential streak");
            check(pacing.remainingMs() == 15_000L, "success installs normal cooldown");
            pacing.reset();
            check(pacing.remainingMs() == 0L, "restart resets pacing deadline");
        });
        test("deferral cannot shorten an existing provider deadline", () -> {
            ChatbotPacing pacing = new ChatbotPacing(() -> 1_000L, (min, max) -> min);
            pacing.defer(60_000L);
            pacing.defer(5_000L);
            check(pacing.remainingMs() == 60_000L, "short local defer must not erase long wait");
        });
        test("cooldown settings clamp and normalize inverted values", () -> {
            for (int i = 0; i < 100; i++)
            {
                long normal = ChatbotPacing.randomCooldownMs(5, 15);
                check(normal >= 5_000L && normal <= 15_000L, "default cooldown bounds");
                long negative = ChatbotPacing.randomCooldownMs(-1, -50);
                check(negative >= 5_000L, "negative values keep 5-second floor");
                long inverted = ChatbotPacing.randomCooldownMs(120, 5);
                check(inverted >= 5_000L && inverted <= 300_000L, "inverted bounds normalize");
            }
        });
        test("Gemini daily quota waits until Pacific midnight", () -> {
            long late = Instant.parse("2026-10-06T06:59:00Z").toEpochMilli();
            check(ChatbotPacing.untilGeminiDailyResetMs(late) == 120_000L,
                "summer Pacific midnight is 07:00 UTC, plus one minute reset margin");
            long winter = Instant.parse("2026-12-06T07:59:00Z").toEpochMilli();
            check(ChatbotPacing.untilGeminiDailyResetMs(winter) == 120_000L,
                "winter Pacific midnight is 08:00 UTC, plus one minute reset margin");
        });
    }

    private static void testText()
    {
        test("whole-word trigger rejects substring false positives", () -> {
            for (String message : new String[]{"feathers", "ashes", "heaps"})
            {
                check(!ChatbotText.matchesKeyword(message, "he"), "substring rejected: " + message);
            }
            check(ChatbotText.matchesKeyword("HE is here", "he"), "case-insensitive full word");
            check(ChatbotText.matchesKeyword("where is he?", "he"), "punctuation boundary");
            check(ChatbotText.matchesKeyword("anything", ""), "empty trigger permits all");
        });
        test("literal keywords cannot inject regex", () -> {
            check(ChatbotText.matchesKeyword("need a c++ guide", "c++"), "literal punctuation trigger");
            check(!ChatbotText.matchesKeyword("need a c guide", "c++"), "regex metacharacters literal");
            check(!ChatbotText.matchesKeyword("hello", ".*"), "wildcards are literal");
        });
        test("comma-separated trigger alternatives each match independently", () -> {
            String triggers = "hello, hey, thanks, good luck, blue cape";
            for (String message : new String[]{"hello traveler!", "HEY there", "thanks!", "good luck today", "a blue cape looks nice"})
            {
                check(ChatbotText.matchesKeyword(message, triggers), "CSV alternative matches: " + message);
            }
            check(!ChatbotText.matchesKeyword("where is the bank?", triggers), "unrelated conversation rejected");
        });
        test("comma-separated alternatives keep whole-word boundaries", () -> {
            String triggers = "hello, hey, thanks, good luck, blue cape";
            for (String message : new String[]{"helloworld", "heyday", "thanksgiving", "good lucky", "blue capes"})
            {
                check(!ChatbotText.matchesKeyword(message, triggers), "CSV substring rejected: " + message);
            }
            check(ChatbotText.matchesKeyword("(hey), thanks?", triggers), "punctuated alternatives match");
            check(!ChatbotText.matchesKeyword("hey_old", triggers), "underscore remains part of word");
            check(!ChatbotText.matchesKeyword("thanks2", triggers), "numeric suffix remains part of word");
        });
        test("CSV trigger phrases are trimmed and retain phrase boundaries", () -> {
            String triggers = "  good luck  ,  blue cape , hello  ";
            check(ChatbotText.matchesKeyword("Good Luck traveler", triggers), "trimmed phrase case-insensitive");
            check(ChatbotText.matchesKeyword("a blue cape!", triggers), "second phrase matches independently");
            check(!ChatbotText.matchesKeyword("good lucky", triggers), "phrase cannot match a longer trailing word");
            check(!ChatbotText.matchesKeyword("notblue cape", triggers), "phrase cannot match a longer leading word");
            check(!ChatbotText.matchesKeyword("good very luck", triggers), "phrase order stays literal");
        });
        test("empty CSV alternatives do not turn a nonempty filter into match-all", () -> {
            check(ChatbotText.matchesKeyword("hey traveler", ", , hey, ,"), "nonempty alternative retained");
            check(!ChatbotText.matchesKeyword("greetings", ", , hey, ,"), "empty entries are skipped");
            for (String triggers : new String[]{"", "   ", ",", ", , ,", " ,\t,  , "})
            {
                check(ChatbotText.matchesKeyword("hello", triggers), "blank list disables trigger filter");
            }
            check(ChatbotText.matchesKeyword("hello", null), "null list disables trigger filter");
            check(!ChatbotText.matchesKeyword(null, "hello, thanks"), "null message cannot match active alternatives");
        });
        test("regex metacharacters in CSV alternatives remain literal", () -> {
            String triggers = "c++, a.b, [loot], .*";
            for (String message : new String[]{"need c++ help", "literal a.b here", "found [loot] today", "literal .* here"})
            {
                check(ChatbotText.matchesKeyword(message, triggers), "literal CSV alternative matches: " + message);
            }
            for (String message : new String[]{"need c help", "an axb word", "found loot", "anything at all"})
            {
                check(!ChatbotText.matchesKeyword(message, triggers), "CSV regex expansion rejected: " + message);
            }
        });
        test("name filtering is stable under Turkish locale", () -> {
            Locale original = Locale.getDefault();
            try
            {
                Locale.setDefault(new Locale("tr", "TR"));
                check(ChatbotText.nameInList("IRON MAN", "alice, iron man, bob"),
                    "case conversion must use ROOT locale");
                check(!ChatbotText.nameInList("iron", "iron man"), "names match whole entry");
            }
            finally
            {
                Locale.setDefault(original);
            }
        });
        test("response sanitizing handles CRLF and configured length bounds", () -> {
            String longText = "a".repeat(150);
            check(ChatbotText.sanitizeResponse(longText, 200).length() <= 80,
                "runtime maximum cannot exceed OSRS chat line");
            check(ChatbotText.sanitizeResponse(longText, -20).length() == 20,
                "runtime minimum response length clamps to 20");
            String singleLine = ChatbotText.sanitizeResponse("hello\r\nworld\nfriend", 80);
            check(!singleLine.contains("\n") && !singleLine.contains("\r"), "one chat line only");
            check(singleLine.contains("hello") && singleLine.contains("world"), "words preserved");
            check(ChatbotText.sanitizeResponse(null, 80).isEmpty(), "null response safe");
        });
        test("assistant output cannot inject chat commands or markup", () -> {
            check(!ChatbotText.sanitizeResponse("/c hello", 80).startsWith("/"),
                "model cannot choose a chat channel prefix");
            check(!ChatbotText.sanitizeResponse("::bank", 80).startsWith("::"),
                "model cannot choose client command");
            check(!ChatbotText.sanitizeResponse("<col=ffffff>hello</col>", 80).contains("<"),
                "chat markup removed");
        });
        test("trade and gambling spam filtering keeps ordinary conversation", () -> {
            check(ChatbotText.looksLikeTradeSpam("selling feathers 2k"), "GE trade spam rejected");
            check(ChatbotText.looksLikeTradeSpam("doubling money now"), "gambling spam rejected");
            check(!ChatbotText.looksLikeTradeSpam("how much are feathers these days?"),
                "ordinary OSRS question retained");
        });
        test("typing delays allow zero and clamp unsafe settings", () -> {
            check(ChatbotText.responseDelayMs(0, 0) == 0L, "instant responses allowed");
            check(ChatbotText.responseDelayMs(-100, -1) == 0L, "negative delays become zero");
            for (int i = 0; i < 20; i++)
            {
                long inverted = ChatbotText.responseDelayMs(5_000, 2_000);
                check(inverted >= 0L && inverted <= 15_000L, "inverted delay is safe");
                check(ChatbotText.responseDelayMs(Integer.MAX_VALUE, Integer.MAX_VALUE) <= 15_000L,
                    "huge imported delay is bounded");
            }
        });
    }

    private static void testHttp() throws Exception
    {
        test("valid HTTP completion parses and closes both streams", () -> {
            FakeConnection connection = new FakeConnection(200,
                "{\"choices\":[{\"message\":{\"content\":\"  nice drop mate  \"}}]}");
            OpenAiService.CompletionResult result = completion(connection);
            check(result.isSuccess(), "200 valid completion succeeds");
            check("nice drop mate".equals(result.getContent()), "content trimmed");
            check("POST".equals(connection.getRequestMethod()), "POST request");
            check("Bearer test-secret".equals(connection.getRequestProperty("Authorization")),
                "API credentials attached to header");
            check(connection.getConnectTimeout() > 0 && connection.getReadTimeout() > 0,
                "bounded network timeouts");
            check(!connection.getInstanceFollowRedirects(), "credentials cannot follow redirect");
            check(connection.request.toString(StandardCharsets.UTF_8.name()).contains("test-model"),
                "configured model sent");
            connection.assertClosed();
        });
        test("429 Retry-After seconds preserves two-hour provider wait", () -> {
            FakeConnection connection = new FakeConnection(429,
                "{\"error\":{\"code\":\"rate_limit_exceeded\",\"message\":\"Too many requests\"}}");
            connection.retryAfter = "7200";
            OpenAiService.CompletionResult result = completion(connection);
            check(!result.isSuccess(), "429 unsuccessful");
            check(result.getStatusCode() == 429, "429 status retained");
            check(result.getRetryAfterMs() == 7_200_000L, "Retry-After not clipped to 15 minutes");
            check(!result.isPermanentFailure() && !result.isDailyQuota(), "ordinary throttle transient");
            connection.assertClosed();
        });
        test("429 Retry-After HTTP date uses injectable clock", () -> {
            long now = Instant.parse("2026-10-05T12:00:00Z").toEpochMilli();
            FakeConnection connection = new FakeConnection(429, "{}");
            connection.retryAfter = DateTimeFormatter.RFC_1123_DATE_TIME.format(
                Instant.ofEpochMilli(now + 7_200_000L).atZone(ZoneOffset.UTC));
            OpenAiService service = new OpenAiService(url -> connection, () -> now);
            OpenAiService.CompletionResult result = completion(service, () -> false);
            check(result.getRetryAfterMs() == 7_200_000L, "HTTP-date preserves two-hour wait");
            connection.assertClosed();
        });
        test("provider Retry-After beyond a week is not shortened", () -> {
            FakeConnection connection = new FakeConnection(429, "{}");
            connection.retryAfter = "1728000";
            OpenAiService.CompletionResult result = completion(connection);
            check(result.getRetryAfterMs() >= 20L * 24 * 60 * 60 * 1_000L,
                "twenty-day provider delay honored");
            connection.assertClosed();
        });
        test("503 overload is transient and keeps Retry-After", () -> {
            FakeConnection connection = new FakeConnection(503,
                "{\"error\":{\"message\":\"Server overloaded, try later\"}}");
            connection.retryAfter = "120";
            OpenAiService.CompletionResult result = completion(connection);
            check(result.getStatusCode() == 503, "503 status preserved");
            check(!result.isPermanentFailure(), "server overload transient");
            check(result.getRetryAfterMs() == 120_000L, "server wait preserved");
            connection.assertClosed();
        });
        test("Google structured daily quota differs from minute rate limit", () -> {
            String dailyBody = "{\"error\":{\"code\":429,\"status\":\"RESOURCE_EXHAUSTED\","
                + "\"details\":[{\"@type\":\"type.googleapis.com/google.rpc.QuotaFailure\","
                + "\"violations\":[{\"quotaMetric\":\"generativelanguage.googleapis.com/"
                + "generate_content_free_tier_requests\",\"quotaId\":\"GenerateRequestsPerDayPerProjectPerModel-FreeTier\","
                + "\"quotaDimensions\":{\"model\":\"gemini-3.8-flash\"}}]}]}}";
            FakeConnection daily = new FakeConnection(429, dailyBody);
            OpenAiService.CompletionResult dailyResult = completion(daily);
            check(dailyResult.isDailyQuota(), "structured daily violation recognized");
            daily.assertClosed();
            FakeConnection minute = new FakeConnection(429, dailyBody.replace("PerDay", "PerMinute"));
            OpenAiService.CompletionResult minuteResult = completion(minute);
            check(!minuteResult.isDailyQuota(), "minute limit not treated as exhausted day");
            check(!minuteResult.isPermanentFailure(), "minute quota transient");
            minute.assertClosed();
        });
        test("Google RetryInfo delay is honored", () -> {
            FakeConnection connection = new FakeConnection(429,
                "{\"error\":{\"code\":429,\"status\":\"RESOURCE_EXHAUSTED\","
                + "\"details\":[{\"@type\":\"type.googleapis.com/google.rpc.RetryInfo\","
                + "\"retryDelay\":\"90.5s\"}]}}");
            OpenAiService.CompletionResult result = completion(connection);
            check(result.getRetryAfterMs() >= 90_500L, "structured retry delay preserved");
            connection.assertClosed();
        });
        test("exhausted billing quota stops further automatic attempts", () -> {
            FakeConnection connection = new FakeConnection(429,
                "{\"error\":{\"code\":\"insufficient_quota\",\"type\":\"insufficient_quota\","
                + "\"message\":\"Check your plan and billing details\"}}");
            OpenAiService.CompletionResult result = completion(connection);
            check(result.isPermanentFailure(), "billing quota requires user correction");
            check(!result.isDailyQuota(), "billing quota not next-day Gemini limit");
            connection.assertClosed();
            for (String errorCode : new String[]{"credit_balance_exhausted", "organization_spend_limit_exceeded",
                "project_spend_limit_exceeded", "organization_usage_limit_exceeded"})
            {
                FakeConnection spendLimit = new FakeConnection(429,
                    "{\"error\":{\"code\":\"" + errorCode + "\"}}");
                check(completion(spendLimit).isPermanentFailure(), "billing stop code: " + errorCode);
                spendLimit.assertClosed();
            }
        });
        test("invalid credentials and invalid model are actionable permanent errors", () -> {
            for (int code : new int[]{400, 401, 403, 404})
            {
                FakeConnection connection = new FakeConnection(code,
                    "{\"error\":{\"message\":\"test-secret invalid model or key\"}}");
                OpenAiService.CompletionResult result = completion(connection);
                check(result.isPermanentFailure(), "config failure pauses requests: " + code);
                check(result.getMessage() != null && !result.getMessage().contains("test-secret"),
                    "status never discloses secret or raw error body");
                connection.assertClosed();
            }
        });
        test("malformed and empty 200 responses fail without stream leaks", () -> {
            for (String body : new String[]{"not-json", "{}", "{\"choices\":[]}",
                "{\"choices\":[{\"message\":{\"content\":null}}]}",
                "{\"choices\":[{\"message\":{\"content\":\"   \"}}]}"})
            {
                FakeConnection connection = new FakeConnection(200, body);
                OpenAiService.CompletionResult result = completion(connection);
                check(!result.isSuccess(), "malformed or empty assistant content rejected");
                check(!result.isPermanentFailure(), "provider payload problem is retryable");
                connection.assertClosed();
            }
        });
        test("HTTP error without body still preserves status and cleanup", () -> {
            FakeConnection connection = new FakeConnection(503, null);
            connection.nullErrorStream = true;
            OpenAiService.CompletionResult result = completion(connection);
            check(result.getStatusCode() == 503, "null error stream preserves HTTP status");
            check(!result.isSuccess() && !result.isPermanentFailure(), "no-body overload retryable");
            connection.assertClosed();
        });
        test("network timeout is retryable and disconnects", () -> {
            FakeConnection connection = new FakeConnection(0, null);
            connection.responseFailure = new SocketTimeoutException("read timed out");
            OpenAiService.CompletionResult result = completion(connection);
            check(!result.isSuccess() && !result.isPermanentFailure(), "timeout is transient");
            check(!result.isCancelled(), "timeout differs from cancellation");
            connection.assertClosed();
        });
        test("cancelled request opens no connection", () -> {
            AtomicInteger opened = new AtomicInteger();
            OpenAiService service = new OpenAiService(url -> {
                opened.incrementAndGet();
                throw new IOException("should not open");
            });
            OpenAiService.CompletionResult result = completion(service, () -> true);
            check(result.isCancelled(), "already-cancelled lifecycle retained");
            check(opened.get() == 0, "cancel before HTTP prevents request");
        });
        test("validation failures never open an HTTP connection", () -> {
            AtomicInteger opened = new AtomicInteger();
            OpenAiService service = new OpenAiService(url -> {
                opened.incrementAndGet();
                throw new IOException("should not open");
            });
            OpenAiService.CompletionResult noKey = service.getChatCompletion("", ENDPOINT, "model",
                Collections.singletonList(new OpenAiService.ChatMessage("user", "hello")),
                60, 0.7, () -> false);
            check(noKey.isPermanentFailure(), "missing API key stops attempts");
            OpenAiService.CompletionResult insecure = service.getChatCompletion("test-secret",
                "http://example.invalid/v1/chat/completions", "model",
                Collections.singletonList(new OpenAiService.ChatMessage("user", "hello")),
                60, 0.7, () -> false);
            check(insecure.isPermanentFailure(), "remote plain HTTP rejected");
            check(opened.get() == 0, "invalid settings open no connection");
        });
        test("cancel disconnects an in-flight HTTP request", () -> {
            FakeConnection connection = new FakeConnection(200,
                "{\"choices\":[{\"message\":{\"content\":\"late reply\"}}]}");
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            AtomicBoolean cancelled = new AtomicBoolean();
            AtomicReference<OpenAiService.CompletionResult> completed = new AtomicReference<>();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            connection.responseHook = () -> {
                entered.countDown();
                try
                {
                    if (!release.await(3, TimeUnit.SECONDS))
                    {
                        throw new AssertionError("cancel regression worker not released");
                    }
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                }
            };
            OpenAiService service = new OpenAiService(url -> connection);
            Thread worker = new Thread(() -> {
                try
                {
                    completed.set(completion(service, cancelled::get));
                }
                catch (Throwable t)
                {
                    failure.set(t);
                }
            }, "chatbot-cancel-regression");
            worker.setDaemon(true);
            worker.start();
            try
            {
                check(entered.await(3, TimeUnit.SECONDS), "request entered fake network read");
                cancelled.set(true);
                service.cancel();
                check(connection.disconnected.get(), "cancel disconnects immediately");
            }
            finally
            {
                release.countDown();
                worker.join(3_000L);
            }
            check(!worker.isAlive(), "cancelled worker terminates");
            check(failure.get() == null, "cancel handles connection errors");
            check(completed.get() != null && completed.get().isCancelled(),
                "late success cannot survive cancelled session");
            check(connection.requestClosed.get(), "cancelled request output closed");
            connection.responseHook = null;
            check(completion(service, () -> false).isSuccess(),
                "cancelled request cannot poison a later session request");
        });
        test("late cancellation preserves an already-received 429 Retry-After", () -> {
            FakeConnection connection = new FakeConnection(429,
                "{\"error\":{\"code\":\"rate_limit_exceeded\"}}");
            connection.retryAfter = "7200";
            OpenAiService service = new OpenAiService(url -> connection);
            connection.response.readHook = service::cancel;
            OpenAiService.CompletionResult result = completion(service, () -> false);
            check(!result.isSuccess() && !result.isCancelled(),
                "known HTTP failure remains available for project pacing");
            check(result.getStatusCode() == 429 && result.getRetryAfterMs() == 7_200_000L,
                "stop cannot erase a two-hour provider hint already received");
            connection.assertClosed();
        });
    }

    private static OpenAiService.CompletionResult completion(FakeConnection connection)
    {
        return completion(new OpenAiService(url -> connection), () -> false);
    }

    private static OpenAiService.CompletionResult completion(OpenAiService service,
        java.util.function.BooleanSupplier cancelled)
    {
        return service.getChatCompletion("test-secret", ENDPOINT, "test-model",
            Collections.singletonList(new OpenAiService.ChatMessage("user", "hello")),
            60, 0.7, cancelled);
    }

    private static void testModels()
    {
        test("Gemini dropdown contains exactly the two supported models", () -> {
            check(ChatbotModel.class.getEnumConstants().length == 2,
                "RuneLite dropdown must contain exactly two constants");
            check("gemini-3.5-flash-lite".equals(ChatbotModel.GEMINI_3_5_FLASH_LITE.getModelId()),
                "Lite model id");
            check("gemini-3.8-flash".equals(ChatbotModel.GEMINI_3_8_FLASH.getModelId()),
                "Flash model id");
        });
        test("existing OpenAI config keeps endpoint and saved model", () -> {
            ChatbotConfig config = new ChatbotConfig()
            {
                @Override public String openAiModel() { return "  saved-legacy-model  "; }
            };
            check(config.provider() == ChatbotProvider.OPENAI, "upgrade preserves OpenAI provider");
            check("saved-legacy-model".equals(ChatbotModel.getModelName(config)),
                "legacy model preserved and trimmed");
            check("https://api.openai.com/v1/chat/completions".equals(ChatbotProvider.getEndpoint(config)),
                "OpenAI endpoint preserved");
        });
        test("Gemini provider resolves model and OpenAI-compatible endpoint", () -> {
            ChatbotConfig config = new ChatbotConfig()
            {
                @Override public ChatbotProvider provider() { return ChatbotProvider.GEMINI; }
                @Override public ChatbotModel model() { return ChatbotModel.GEMINI_3_8_FLASH; }
            };
            check("gemini-3.8-flash".equals(ChatbotModel.getModelName(config)), "selected Gemini id");
            check("https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"
                .equals(ChatbotProvider.getEndpoint(config)), "Google compatible endpoint");
        });
        test("custom model overrides provider defaults", () -> {
            ChatbotConfig config = new ChatbotConfig()
            {
                @Override public ChatbotProvider provider() { return ChatbotProvider.GEMINI; }
                @Override public String customModel() { return "  chosen-model  "; }
            };
            check("chosen-model".equals(ChatbotModel.getModelName(config)), "explicit model id wins");
        });
        test("null legacy settings resolve safe defaults", () -> {
            ChatbotConfig old = new ChatbotConfig()
            {
                @Override public ChatbotProvider provider() { return null; }
                @Override public String customModel() { return null; }
                @Override public String openAiModel() { return null; }
            };
            check("gpt-4o-mini".equals(ChatbotModel.getModelName(old)), "empty legacy model default");
            check("https://api.openai.com/v1/chat/completions".equals(ChatbotProvider.getEndpoint(old)),
                "null provider retains old endpoint");
            ChatbotConfig gemini = new ChatbotConfig()
            {
                @Override public ChatbotProvider provider() { return ChatbotProvider.GEMINI; }
                @Override public ChatbotModel model() { return null; }
            };
            check("gemini-3.5-flash-lite".equals(ChatbotModel.getModelName(gemini)),
                "null Gemini selection uses Lite");
        });
        test("custom provider needs explicit model and valid full endpoint", () -> {
            ChatbotConfig valid = customConfig("https://example.invalid/api/completions", "custom-id");
            check("custom-id".equals(ChatbotModel.getModelName(valid)), "custom model retained");
            check("https://example.invalid/api/completions".equals(ChatbotProvider.getEndpoint(valid)),
                "custom endpoint exact");
            expectIllegalArgument(() -> ChatbotModel.getModelName(customConfig(
                "https://example.invalid/api/completions", "")), "custom model required");
            for (String endpoint : new String[]{"", "relative/path", "file:///etc/passwd",
                "https://test-secret@example.invalid/completions", "https://example.invalid/#test-secret"})
            {
                expectIllegalArgument(() -> ChatbotProvider.getEndpoint(customConfig(endpoint, "id")),
                    "invalid endpoint must pause configuration");
            }
        });
    }

    private static ChatbotConfig customConfig(String endpoint, String model)
    {
        return new ChatbotConfig()
        {
            @Override public ChatbotProvider provider() { return ChatbotProvider.CUSTOM; }
            @Override public String customEndpoint() { return endpoint; }
            @Override public String customModel() { return model; }
        };
    }

    private static void expectIllegalArgument(Runnable operation, String message)
    {
        try
        {
            operation.run();
            throw new AssertionError(message);
        }
        catch (IllegalArgumentException expected)
        {
            check(expected.getMessage() != null && !expected.getMessage().contains("test-secret"),
                "configuration error is actionable and hides credentials");
        }
    }

    private static void testScript()
    {
        test("busy chat retains only the latest of 1000 messages", () -> {
            try (Fixture f = new Fixture())
            {
                for (int i = 0; i < 1000; i++)
                {
                    f.script.enqueueMessage("Bob", "hello " + i, "public");
                }
                f.script.tick();
                check(f.service.calls == 1, "busy chat produces one API request");
                check(f.service.requests.get(0).contains("hello 999"), "freshest message wins");
                f.now.addAndGet(5_000L);
                f.runtime.typed.setLength(0);
                f.script.tick();
                check(f.service.calls == 1, "old queue does not flush after cooldown");
            }
        });
        test("429 retry pauses even when fresh messages arrive", () -> {
            try (Fixture f = new Fixture())
            {
                f.service.results.add(OpenAiService.CompletionResult.failure(429, 0, false, false,
                    "Provider rate limit reached"));
                f.script.enqueueMessage("Bob", "first", "public");
                f.script.tick();
                check(f.service.calls == 1, "first failed attempt counted");
                f.script.enqueueMessage("Alice", "newer", "public");
                for (int i = 0; i < 20; i++) f.script.tick();
                check(f.service.calls == 1, "600ms loop cannot retry rate limit immediately");
                f.now.addAndGet(29_999L);
                f.script.tick();
                check(f.service.calls == 1, "minimum 30s throttle pause enforced");
                f.now.incrementAndGet();
                f.script.tick();
                check(f.service.calls == 2, "one retry after deadline");
                check(f.service.requests.get(1).contains("newer"), "fresh message replaces failed one");
            }
        });
        test("editing key tokens or temperature cannot bypass Retry-After", () -> {
            try (Fixture f = new Fixture())
            {
                f.service.results.add(OpenAiService.CompletionResult.failure(429, 120_000L, false, false,
                    "Rate limit"));
                f.script.enqueueMessage("Bob", "first", "public");
                f.script.tick();
                f.config.key = "changed-test-key";
                f.script.enqueueMessage("Bob", "after key edit", "public");
                f.script.tick();
                f.config.tokens = 120;
                f.script.enqueueMessage("Bob", "after tokens edit", "public");
                f.script.tick();
                f.config.creativity = "1.0";
                f.script.enqueueMessage("Bob", "after creativity edit", "public");
                f.script.tick();
                check(f.service.calls == 1, "connection edits cannot spend another throttled attempt");
                check(f.pacing.remainingMs() >= 120_000L, "provider deadline retained across edits");
                f.now.addAndGet(120_000L);
                f.script.enqueueMessage("Bob", "fresh when ready", "public");
                f.script.tick();
                check(f.service.calls == 2, "edited settings used only after provider wait");
            }
        });
        test("429 arriving after a key edit still installs provider wait", () -> {
            try (Fixture f = new Fixture())
            {
                f.service.results.add(OpenAiService.CompletionResult.failure(429, 120_000L, false, false,
                    "Rate limit"));
                f.service.requestHook = () -> {
                    f.config.key = "changed-during-request";
                    f.service.requestHook = null;
                };
                f.script.enqueueMessage("Bob", "first", "public");
                f.script.tick();
                f.script.enqueueMessage("Bob", "after completed key edit", "public");
                f.script.tick();
                check(f.service.calls == 1, "stale config result still protects project request quota");
                check(f.pacing.remainingMs() >= 120_000L, "429 result hint preserved before config discard");
            }
        });
        test("stopped generation's 429 retains Retry-After after restart", () -> {
            try (Fixture f = new Fixture())
            {
                f.service.results.add(OpenAiService.CompletionResult.failure(429, 120_000L, false, false,
                    "Rate limit"));
                f.service.requestHook = () -> {
                    f.script.shutdown();
                    f.script.start(false);
                    f.service.requestHook = null;
                };
                f.script.enqueueMessage("Bob", "old generation", "public");
                f.script.tick();
                f.script.enqueueMessage("Bob", "restarted generation", "public");
                f.script.tick();
                check(f.service.calls == 1, "restart cannot bypass completed rate-limit result");
                check(f.pacing.remainingMs() >= 120_000L, "old response provider wait preserved");
                f.now.addAndGet(120_000L);
                f.script.enqueueMessage("Bob", "fresh after provider wait", "public");
                f.script.tick();
                check(f.service.calls == 2, "new session recovers only after wait expires");
            }
        });
        test("Gemini daily project quota waits for reset without rapid model retries", () -> {
            try (Fixture f = new Fixture())
            {
                f.config.selectedProvider = ChatbotProvider.GEMINI;
                f.config.selectedModel = ChatbotModel.GEMINI_3_8_FLASH;
                f.service.results.add(OpenAiService.CompletionResult.failure(429, 0, true, true,
                    "Daily project quota reached"));
                f.script.enqueueMessage("Bob", "first", "public");
                f.script.tick();
                long wait = f.pacing.remainingMs();
                check(wait >= 30_000L, "daily quota installs reset deadline");
                f.now.addAndGet(wait - 1L);
                f.script.enqueueMessage("Bob", "fresh before reset", "public");
                f.script.tick();
                check(f.service.calls == 1, "fresh input cannot trigger rapid daily-quota retry");
                f.now.incrementAndGet();
                f.script.tick();
                check(f.service.calls == 2, "daily quota can recover at reset");
            }
        });
        test("stale queued input expires instead of causing a late response", () -> {
            try (Fixture f = new Fixture())
            {
                f.config.maxAge = 10;
                f.script.enqueueMessage("Bob", "old message", "public");
                f.now.addAndGet(10_001L);
                f.script.tick();
                check(f.service.calls == 0, "expired message uses no quota");
                check(f.runtime.enterCount == 0, "expired message sends no reply");
            }
        });
        test("input that expires during response delay is not sent", () -> {
            try (Fixture f = new Fixture())
            {
                f.config.maxAge = 10;
                f.config.delay = 15_000;
                f.runtime.delayHook = () -> f.now.addAndGet(15_000L);
                f.script.enqueueMessage("Bob", "hello", "public");
                f.script.tick();
                check(f.service.calls == 1, "completion began while input fresh");
                check(f.runtime.typed.length() == 0 && f.runtime.enterCount == 0,
                    "expired delayed reply cannot reach keyboard");
            }
        });
        test("in-flight API failure cannot overwrite a fresher pending message", () -> {
            try (Fixture f = new Fixture())
            {
                f.service.results.add(OpenAiService.CompletionResult.failure(429, 0, false, false,
                    "Rate limit"));
                f.service.requestHook = () -> {
                    f.script.enqueueMessage("Alice", "new during HTTP", "public");
                    f.service.requestHook = null;
                };
                f.script.enqueueMessage("Bob", "older attempted input", "public");
                f.script.tick();
                f.now.addAndGet(30_000L);
                f.script.tick();
                check(f.service.calls == 2, "one subsequent retry");
                check(f.service.requests.get(1).contains("new during HTTP"),
                    "failed input never replaces fresh event");
            }
        });
        test("unexpected worker exception still enforces request pause", () -> {
            try (Fixture f = new Fixture())
            {
                f.service.completionFailure = new IllegalStateException("simulated failure");
                f.script.enqueueMessage("Bob", "hello", "public");
                f.script.tick();
                f.service.completionFailure = null;
                f.script.enqueueMessage("Alice", "fresh input", "public");
                f.script.tick();
                check(f.service.calls == 1, "exception cannot bypass request pacing");
                check(f.pacing.remainingMs() >= 5_000L, "exception installs minimum cooldown");
                f.now.addAndGet(5_000L);
                f.script.tick();
                check(f.service.calls == 2, "exception recovers after deadline");
            }
        });
        test("disabled public replies reject input before API work", () -> {
            try (Fixture f = new Fixture())
            {
                f.config.publicReplies = false;
                f.script.enqueueMessage("Bob", "hello", "public");
                f.script.tick();
                check(f.service.calls == 0, "disabled public chat uses no API request");
                check(f.script.getResponsesSent() == 0, "disabled channel is not counted as sent");
            }
        });
        test("configured CSV trigger filters incoming chat before API work", () -> {
            try (Fixture f = new Fixture())
            {
                f.config.trigger = "hello, hey, thanks, good luck, blue cape";
                f.script.enqueueMessage("Bob", "heyday with blue capes", "public");
                f.script.tick();
                check(f.service.calls == 0, "substring-only incoming chat spends no API request");
                f.script.enqueueMessage("Bob", "thanks traveler!", "public");
                f.script.tick();
                check(f.service.calls == 1, "one configured alternative triggers a completion");
                check(f.script.getMessagesReceived() == 1, "only matching chat reaches pending input");
            }
        });
        test("preview mode clears its completed draft without Enter or sent count", () -> {
            try (Fixture f = new Fixture())
            {
                f.config.send = false;
                f.script.enqueueMessage("Bob", "hello", "public");
                f.script.tick();
                check(f.service.calls == 1, "preview can generate one reply");
                check(f.runtime.typed.length() == 0, "completed owned preview is cleared");
                check(f.runtime.clearCalls == 1 && f.runtime.clearCount == 1, "preview cleared once");
                check("nice drop mate".equals(f.runtime.lastPreviewText), "public preview contains plain reply text");
                check(f.runtime.enterCount == 0, "preview never presses Enter");
                check(f.script.getResponsesSent() == 0, "preview not counted as sent");
                check(f.script.getPreviewCount() == 1, "completed preview counted before cleanup");
            }
        });
        test("cleared preview allows the next preview after request cooldown", () -> {
            try (Fixture f = new Fixture())
            {
                f.config.send = false;
                f.script.enqueueMessage("Bob", "first preview", "public");
                f.script.tick();
                f.script.enqueueMessage("Bob", "second preview", "public");
                f.now.addAndGet(4_999L);
                f.script.tick();
                check(f.service.calls == 1 && f.runtime.clearCount == 1, "cleanup does not bypass pacing");
                f.now.incrementAndGet();
                f.script.tick();
                check(f.service.calls == 2, "empty input allows another completion when due");
                check(f.runtime.typed.length() == 0 && f.runtime.clearCount == 2, "both owned previews cleared");
                check(f.script.getPreviewCount() == 2, "two completed previews counted");
                check(f.runtime.enterCount == 0 && f.script.getResponsesSent() == 0,
                    "successive preview cleanup never sends chat");
            }
        });
        test("player edits immediately before preview cleanup are preserved", () -> {
            try (Fixture f = new Fixture())
            {
                f.config.send = false;
                f.runtime.beforeClear = () -> f.runtime.typed.append(" player edit");
                f.script.enqueueMessage("Bob", "hello", "public");
                f.script.tick();
                check(f.runtime.clearCalls == 1 && f.runtime.clearCount == 0, "changed text vetoes cleanup");
                check(f.runtime.typed.toString().endsWith(" player edit"), "player's changed draft remains");
                check(f.runtime.enterCount == 0, "changed draft is never submitted");
                check(f.script.getPreviewCount() == 1 && f.script.getResponsesSent() == 0,
                    "completed preview counted even when cleanup was vetoed");
            }
        });
        test("shutdown immediately before preview cleanup prevents clearing", () -> {
            try (Fixture f = new Fixture())
            {
                f.config.send = false;
                f.runtime.beforeClear = f.script::shutdown;
                f.script.enqueueMessage("Bob", "hello", "public");
                f.script.tick();
                check(f.runtime.clearCalls == 1 && f.runtime.clearCount == 0, "stop vetoes cleanup");
                check(f.runtime.typed.length() > 0, "stopped session cannot change the chatbox");
                check(f.runtime.enterCount == 0 && f.script.getResponsesSent() == 0,
                    "stop before cleanup never sends chat");
                check("Stopped".equals(f.script.getLastStatus()), "cleanup cannot overwrite stopped status");
            }
        });
        test("logout immediately before preview cleanup prevents clearing", () -> {
            try (Fixture f = new Fixture())
            {
                f.config.send = false;
                f.runtime.beforeClear = () -> {
                    f.runtime.loggedIn = false;
                    f.script.onLoginStateChanged(false);
                };
                f.script.enqueueMessage("Bob", "hello", "public");
                f.script.tick();
                check(f.runtime.clearCalls == 1 && f.runtime.clearCount == 0, "logout vetoes cleanup");
                check(f.runtime.typed.length() > 0, "previous login cannot clear chat input");
                check(f.runtime.enterCount == 0 && f.script.getResponsesSent() == 0,
                    "logout before cleanup never sends chat");
            }
        });
        test("existing player input is preserved and consumes no API quota", () -> {
            try (Fixture f = new Fixture())
            {
                f.config.send = false;
                f.runtime.typed.append("my unsent draft");
                f.script.enqueueMessage("Bob", "hello", "public");
                f.script.tick();
                check("my unsent draft".contentEquals(f.runtime.typed), "player draft preserved");
                check(f.runtime.enterCount == 0, "never submits player's draft");
                check(f.service.calls == 0, "busy chatbox checked before paid completion");
                check(f.runtime.clearCalls == 0, "preview cleanup never touches preexisting player input");
            }
        });
        test("shutdown during completion cannot send a late reply", () -> {
            try (Fixture f = new Fixture())
            {
                f.service.requestHook = f.script::shutdown;
                f.script.enqueueMessage("Bob", "hello", "public");
                f.script.tick();
                check(f.runtime.typed.length() == 0 && f.runtime.enterCount == 0,
                    "no typing or Enter after stop");
                check(f.script.getResponsesSent() == 0, "late result is not counted");
                check("Stopped".equals(f.script.getLastStatus()), "late result cannot overwrite stopped status");
            }
        });
        test("restart cannot inherit a stopped completion or conversation", () -> {
            try (Fixture f = new Fixture())
            {
                f.service.requestHook = () -> {
                    f.script.shutdown();
                    f.script.start(false);
                    f.script.enqueueMessage("Bob", "new session input", "public");
                    f.service.requestHook = null;
                };
                f.script.enqueueMessage("Bob", "old session input", "public");
                f.script.tick();
                check(f.runtime.typed.length() == 0 && f.runtime.enterCount == 0,
                    "stopped generation never reaches new session");
                f.now.addAndGet(5_000L);
                f.script.tick();
                check(f.service.calls == 2, "new session can recover after reserved pause");
                check(f.service.requests.get(1).contains("new session input"), "new session input selected");
                check(f.service.histories.get(1).size() == 2, "stopped conversation does not repopulate new history");
                check(f.script.getResponsesSent() == 1, "new session counts only actual sends");
            }
        });
        test("shutdown during response delay prevents typing", () -> {
            try (Fixture f = new Fixture())
            {
                f.config.delay = 50;
                f.runtime.delayHook = f.script::shutdown;
                f.script.enqueueMessage("Bob", "hello", "public");
                f.script.tick();
                check(f.runtime.typed.length() == 0 && f.runtime.enterCount == 0,
                    "stopped delay cannot type or send");
                check("Stopped".equals(f.script.getLastStatus()), "stopped delay retains stopped status");
            }
        });
        test("shutdown during individual character typing prevents Enter", () -> {
            try (Fixture f = new Fixture())
            {
                f.runtime.characterHook = f.script::shutdown;
                f.script.enqueueMessage("Bob", "hello", "public");
                f.script.tick();
                check(f.runtime.typed.length() <= 1, "typing stops at first cancelled character");
                check(f.runtime.enterCount == 0, "partial reply never submitted after stop");
                check(f.script.getResponsesSent() == 0, "partial typing not counted as sent");
            }
        });
        test("typing waits for delayed client input acknowledgement", () -> {
            try (Fixture f = new Fixture())
            {
                f.runtime.ackLagChecks = 2;
                f.script.enqueueMessage("Bob", "hello", "public");
                f.script.tick();
                check(f.runtime.enterCount == 1, "slow client frame does not abort valid typing");
                check(f.script.getResponsesSent() == 1, "acknowledged reply sent once");
            }
        });
        test("player input interleaved while typing prevents Enter", () -> {
            try (Fixture f = new Fixture())
            {
                f.runtime.characterHook = () -> {
                    f.runtime.typed.append("player draft");
                    f.runtime.characterHook = null;
                };
                f.script.enqueueMessage("Bob", "hello", "public");
                f.script.tick();
                check(f.runtime.enterCount == 0, "mismatched player input never submitted");
                check(f.runtime.typed.toString().contains("player draft"), "player input preserved");
                check(f.script.getResponsesSent() == 0, "interrupted draft not counted as sent");
            }
        });
        test("message expiry while typing prevents Enter", () -> {
            try (Fixture f = new Fixture())
            {
                f.config.maxAge = 10;
                f.runtime.characterHook = () -> f.now.addAndGet(10_001L);
                f.script.enqueueMessage("Bob", "hello", "public");
                f.script.tick();
                check(f.runtime.typed.length() <= 1, "expired message stops at next character check");
                check(f.runtime.enterCount == 0, "expired partial reply never submitted");
            }
        });
        test("logout during completion invalidates the reply", () -> {
            try (Fixture f = new Fixture())
            {
                f.service.requestHook = () -> f.runtime.loggedIn = false;
                f.script.enqueueMessage("Bob", "hello", "public");
                f.script.tick();
                check(f.runtime.enterCount == 0 && f.runtime.typed.length() == 0,
                    "logged-out completion never reaches keyboard");
            }
        });
        test("logout and login during completion invalidates the previous login's reply", () -> {
            try (Fixture f = new Fixture())
            {
                f.service.requestHook = () -> {
                    f.runtime.loggedIn = false;
                    f.script.onLoginStateChanged(false);
                    f.runtime.loggedIn = true;
                    f.script.onLoginStateChanged(true);
                    f.service.requestHook = null;
                };
                f.script.enqueueMessage("Bob", "before logout", "public");
                f.script.tick();
                check(f.runtime.enterCount == 0 && f.runtime.typed.length() == 0,
                    "relogin cannot reactivate old paid completion");
                f.now.addAndGet(5_000L);
                f.script.enqueueMessage("Bob", "after new login", "public");
                f.script.tick();
                check(f.service.calls == 2 && f.runtime.enterCount == 1,
                    "new login can reply to fresh chat after request pause");
                check(f.service.histories.get(1).size() == 2,
                    "previous login conversation cannot leak into new login");
            }
        });
        test("permanent API failure pauses until connection settings change", () -> {
            try (Fixture f = new Fixture())
            {
                f.service.results.add(OpenAiService.CompletionResult.failure(401, 0, true, false,
                    "Provider rejected API key"));
                f.script.enqueueMessage("Bob", "hello", "public");
                f.script.tick();
                f.now.addAndGet(60_000L);
                f.script.enqueueMessage("Alice", "fresh after failure", "public");
                f.script.tick();
                check(f.service.calls == 1, "bad credentials must not repeatedly consume attempts");
                f.config.key = "corrected-test-key";
                f.script.enqueueMessage("Carol", "after setting change", "public");
                f.script.tick();
                check(f.service.calls == 2, "corrected credentials allow recovery");
            }
        });
        test("Gemini fallback waits before trying Lite", () -> {
            try (Fixture f = new Fixture())
            {
                f.config.selectedProvider = ChatbotProvider.GEMINI;
                f.config.selectedModel = ChatbotModel.GEMINI_3_8_FLASH;
                f.service.results.add(OpenAiService.CompletionResult.failure(503, 0, false, false,
                    "Provider overloaded"));
                f.script.enqueueMessage("Bob", "hello", "public");
                f.script.tick();
                check(f.service.calls == 1, "fallback not called in same failing tick");
                f.script.tick();
                check(f.service.calls == 1, "fallback respects overload cooldown");
                f.now.addAndGet(30_000L);
                f.script.tick();
                check(f.service.calls == 2, "fallback attempts after pause");
                check("gemini-3.5-flash-lite".equals(f.service.models.get(1)), "Lite fallback selected");
            }
        });
        test("public replies type plain text without a channel command", () -> {
            try (Fixture f = new Fixture())
            {
                f.script.enqueueMessage("Bob", "hello", "public");
                f.script.tick();
                check("nice drop mate".contentEquals(f.runtime.typed), "public reply has no /p prefix");
                check(f.runtime.enterCount == 1 && f.script.getResponsesSent() == 1,
                    "plain public reply sent once");
            }
        });
        test("public replies use the full eighty-character chat limit", () -> {
            try (Fixture f = new Fixture())
            {
                f.service.defaultResult = OpenAiService.CompletionResult.success("a".repeat(120));
                f.script.enqueueMessage("Bob", "hello", "public");
                f.script.tick();
                check("a".repeat(80).contentEquals(f.runtime.typed),
                    "all eighty characters are reply text without public routing prefix");
                check(f.runtime.enterCount == 1, "full-length public reply sent once");
            }
        });
        test("clan and guest clan replies retain their channel commands", () -> {
            String[] channels = {"clan", "guestclan"};
            String[] prefixes = {"/c ", "/gc "};
            for (int i = 0; i < channels.length; i++)
            {
                try (Fixture f = new Fixture())
                {
                    f.config.clan = true;
                    f.service.defaultResult = OpenAiService.CompletionResult.success("a".repeat(120));
                    f.script.enqueueMessage("Bob", "hello", channels[i]);
                    f.script.tick();
                    check((prefixes[i] + "a".repeat(80 - prefixes[i].length())).contentEquals(f.runtime.typed),
                        "matching clan prefix remains within eighty-character input limit");
                    check(f.runtime.enterCount == 1 && f.script.getResponsesSent() == 1,
                        "channel reply sent once: " + channels[i]);
                }
            }
        });
        test("friends chat replies route to friends chat", () -> {
            try (Fixture f = new Fixture())
            {
                f.config.friends = true;
                f.script.enqueueMessage("Bob", "hello", "friends");
                f.script.tick();
                check("/ nice drop mate".contentEquals(f.runtime.typed), "friends chat prefix and reply retained");
                check(f.runtime.enterCount == 1 && f.script.getResponsesSent() == 1,
                    "only actual send is counted");
            }
        });
        test("unsafe imported numeric settings normalize before use", () -> {
            try (Fixture f = new Fixture())
            {
                f.config.delay = -10;
                f.config.memory = -10;
                f.config.length = -100;
                f.service.defaultResult = OpenAiService.CompletionResult.success("a".repeat(200));
                f.script.enqueueMessage("Bob", "hello", "public");
                f.script.tick();
                check(f.runtime.typed.length() == 20, "minimum response length normalized");
                check(f.runtime.enterCount == 1, "negative settings cause no runtime exception");
            }
        });
    }

    private static void testInjection()
    {
        test("plugin injector and overlay share one chatbot worker", () -> {
            ChatbotPlugin plugin = new ChatbotPlugin();
            Injector injector = Guice.createInjector(new AbstractModule()
            {
                @Override
                protected void configure()
                {
                    // A provider supplies the plugin without injecting the live client's
                    // ConfigManager and OverlayManager graph. Script scope remains real.
                    bind(ChatbotPlugin.class).toProvider(() -> plugin);
                    bind(ChatbotConfig.class).toInstance(new TestConfig());
                    bind(OpenAiService.class).toInstance(new FakeService());
                }
            });
            plugin.setInjector(injector);
            ChatbotScript worker = plugin.getInjector().getInstance(ChatbotScript.class);
            ChatbotOverlay overlay = injector.getInstance(ChatbotOverlay.class);
            Field overlayWorkerField = ChatbotOverlay.class.getDeclaredField("chatbotScript");
            overlayWorkerField.setAccessible(true);
            ChatbotScript overlayWorker = (ChatbotScript) overlayWorkerField.get(overlay);
            check(worker == injector.getInstance(ChatbotScript.class), "Guice worker scope is shared");
            check(worker == overlayWorker, "overlay observes the plugin injector's actual worker");
            try
            {
                check(worker.start(false), "shared worker starts without scheduler or game access");
                check("Listening".equals(overlayWorker.getLastStatus()),
                    "overlay immediately observes the running worker's status");
            }
            finally
            {
                worker.shutdown();
            }
            check("Stopped".equals(overlayWorker.getLastStatus()), "overlay observes worker shutdown");
        });
    }

    private static final class Fixture implements AutoCloseable
    {
        final AtomicLong now = new AtomicLong(1_000L);
        final TestConfig config = new TestConfig();
        final FakeRuntime runtime = new FakeRuntime();
        final FakeService service = new FakeService();
        final ChatbotPacing pacing = new ChatbotPacing(now::get, (min, max) -> min);
        final ChatbotScript script = new ChatbotScript(config, service, runtime, now::get, pacing);

        Fixture()
        {
            check(script.start(false), "manual script start");
        }

        @Override
        public void close()
        {
            script.shutdown();
        }
    }

    private static final class TestConfig implements ChatbotConfig
    {
        String key = "test-secret";
        boolean publicReplies = true;
        boolean send = true;
        boolean friends;
        boolean clan;
        int maxAge = 60;
        int delay;
        int memory = 10;
        int length = 80;
        int tokens = 60;
        String creativity = "0.7";
        String trigger = "";
        ChatbotProvider selectedProvider = ChatbotProvider.OPENAI;
        ChatbotModel selectedModel = ChatbotModel.GEMINI_3_5_FLASH_LITE;

        @Override public String openAiApiKey() { return key; }
        @Override public boolean respondViaPublic() { return publicReplies; }
        @Override public boolean pressEnterToSend() { return send; }
        @Override public boolean listenFriendsChat() { return friends; }
        @Override public boolean listenClanChat() { return clan; }
        @Override public int messageMaxAgeSeconds() { return maxAge; }
        @Override public int responseDelayMin() { return delay; }
        @Override public int responseDelayMax() { return delay; }
        @Override public int conversationMemory() { return memory; }
        @Override public int maxResponseLength() { return length; }
        @Override public int maxTokens() { return tokens; }
        @Override public String temperature() { return creativity; }
        @Override public String triggerKeyword() { return trigger; }
        @Override public int cooldownMinSeconds() { return 5; }
        @Override public int cooldownMaxSeconds() { return 5; }
        @Override public ChatbotProvider provider() { return selectedProvider; }
        @Override public ChatbotModel model() { return selectedModel; }
    }

    private static final class FakeService extends OpenAiService
    {
        int calls;
        Runnable requestHook;
        RuntimeException completionFailure;
        final Deque<CompletionResult> results = new ArrayDeque<>();
        final List<String> requests = new ArrayList<>();
        final List<String> models = new ArrayList<>();
        final List<List<ChatMessage>> histories = new ArrayList<>();
        CompletionResult defaultResult = CompletionResult.success("nice drop mate");

        @Override
        public CompletionResult getChatCompletion(String apiKey, String endpoint, String model,
            List<ChatMessage> messages, int maxTokens, double temperature,
            java.util.function.BooleanSupplier cancelled)
        {
            calls++;
            models.add(model);
            requests.add(messages.get(messages.size() - 1).getContent());
            histories.add(new ArrayList<>(messages));
            if (completionFailure != null) throw completionFailure;
            if (requestHook != null) requestHook.run();
            return results.isEmpty() ? defaultResult : results.removeFirst();
        }
    }

    private static final class FakeRuntime implements ChatbotScript.RuntimeAccess
    {
        boolean loggedIn = true;
        final StringBuilder typed = new StringBuilder();
        int enterCount;
        int ackLagChecks;
        int ackRemaining;
        int clearCalls;
        int clearCount;
        String lastPreviewText;
        Runnable delayHook;
        Runnable characterHook;
        Runnable beforeClear;

        @Override public boolean isLoggedIn() { return loggedIn; }
        @Override public boolean canProcess() { return true; }
        @Override public String localName() { return "My Player"; }
        @Override public boolean isNearby(String name, int distance) { return true; }
        @Override
        public boolean chatboxReady(String expectedText)
        {
            if (ackRemaining > 0 && expectedText.contentEquals(typed))
            {
                ackRemaining--;
                return false;
            }
            return expectedText.contentEquals(typed);
        }

        @Override
        public void typeCharacter(char character)
        {
            typed.append(character);
            ackRemaining = ackLagChecks;
            if (characterHook != null) characterHook.run();
        }

        @Override public void enter() { enterCount++; }
        @Override public void delay(int delayMs) { if (delayHook != null) delayHook.run(); }

        @Override
        public boolean clearPreview(String expectedText, java.util.function.BooleanSupplier allowed)
        {
            clearCalls++;
            lastPreviewText = expectedText;
            if (beforeClear != null) beforeClear.run();
            if (!allowed.getAsBoolean() || !expectedText.contentEquals(typed)) return false;
            typed.setLength(0);
            clearCount++;
            return true;
        }
    }

    private static void test(String name, CheckedRunnable operation)
    {
        try
        {
            operation.run();
            cases++;
            System.out.println("PASS " + name);
        }
        catch (Exception | AssertionError e)
        {
            throw new AssertionError("FAIL " + name, e);
        }
    }

    private static void check(boolean condition, String message)
    {
        checks++;
        if (!condition)
        {
            throw new AssertionError(message);
        }
    }

    @FunctionalInterface
    private interface CheckedRunnable
    {
        void run() throws Exception;
    }

    private static final class FakeConnection extends HttpURLConnection
    {
        final TrackedInputStream response;
        final ByteArrayOutputStream request = new ByteArrayOutputStream();
        final AtomicBoolean disconnected = new AtomicBoolean();
        final AtomicBoolean requestClosed = new AtomicBoolean();
        String retryAfter;
        IOException responseFailure;
        boolean nullErrorStream;
        Runnable responseHook;

        FakeConnection(int status, String body) throws Exception
        {
            super(new URL(ENDPOINT));
            responseCode = status;
            response = new TrackedInputStream(body == null ? "" : body);
        }

        @Override
        public void connect()
        {
            connected = true;
        }

        @Override
        public void disconnect()
        {
            disconnected.set(true);
            connected = false;
        }

        @Override
        public boolean usingProxy()
        {
            return false;
        }

        @Override
        public OutputStream getOutputStream()
        {
            return new OutputStream()
            {
                @Override
                public void write(int value)
                {
                    request.write(value);
                }

                @Override
                public void close()
                {
                    requestClosed.set(true);
                }
            };
        }

        @Override
        public int getResponseCode() throws IOException
        {
            if (responseHook != null)
            {
                responseHook.run();
            }
            if (responseFailure != null)
            {
                throw responseFailure;
            }
            return responseCode;
        }

        @Override
        public String getHeaderField(String name)
        {
            return "Retry-After".equalsIgnoreCase(name) ? retryAfter : null;
        }

        @Override
        public InputStream getInputStream()
        {
            return response;
        }

        @Override
        public InputStream getErrorStream()
        {
            return nullErrorStream ? null : response;
        }

        void assertClosed()
        {
            check(disconnected.get(), "connection disconnected on every result path");
            check(requestClosed.get(), "request output stream closed");
            if (responseFailure == null && !nullErrorStream)
            {
                check(response.closed.get(), "response input stream closed");
            }
        }
    }

    private static final class TrackedInputStream extends ByteArrayInputStream
    {
        final AtomicBoolean closed = new AtomicBoolean();
        Runnable readHook;

        TrackedInputStream(String text)
        {
            super(text.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public synchronized int read(byte[] buffer, int offset, int length)
        {
            if (readHook != null)
            {
                Runnable callback = readHook;
                readHook = null;
                callback.run();
            }
            return super.read(buffer, offset, length);
        }

        @Override
        public void close() throws IOException
        {
            closed.set(true);
            super.close();
        }
    }
}
