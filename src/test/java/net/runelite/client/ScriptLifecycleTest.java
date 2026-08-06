package net.runelite.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.runelite.client.plugins.microbot.agentserver.AgentServerPlugin;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Self-contained test that launches the Microbot client with the Agent Server
 * plugin, logs in, then exercises the /scripts HTTP endpoints.
 *
 * Usage:
 *   ./gradlew test --tests ScriptLifecycleTest \
 *       -PmicrobotClientPath=/path/to/microbot-2.2.0.jar
 *
 * Or run main() from your IDE with microbot 2.2.0 on the classpath.
 */
public class ScriptLifecycleTest {

    private static final String BASE = "http://127.0.0.1:8081";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();
    private static final int STARTUP_TIMEOUT_MS = 120_000;
    private static final int LOGIN_TIMEOUT_S = 60;
    private static final int POLL_INTERVAL_MS = 1_000;
    private static final int VERIFY_TIMEOUT_MS = 15_000;
    private static final int LOGIN_INDEX_AUTH_FAILURE = 3;
    private static final int LOGIN_INDEX_INVALID_CREDENTIALS = 4;
    private static final int LOGIN_INDEX_BANNED = 14;
    private static final int LOGIN_INDEX_DISCONNECTED = 24;
    private static final int LOGIN_INDEX_NON_MEMBER = 34;
    private static final int F2P_WORLD = 301;

    private static final Set<Integer> FATAL_LOGIN_ERRORS = Set.of(
            LOGIN_INDEX_AUTH_FAILURE,
            LOGIN_INDEX_INVALID_CREDENTIALS,
            LOGIN_INDEX_BANNED
    );

    public static void main(String[] args) throws Exception {
        startClient(args);
        waitForAgentServer();
        login();

        String pluginClass = "net.runelite.client.plugins.microbot.breakhandler.BreakHandlerPlugin";

        System.out.println("=== 1. List available scripts ===");
        Map<String, Object> list = get("/scripts");
        System.out.println(GSON.toJson(list));

        System.out.println("\n=== 2. Check status before start ===");
        Map<String, Object> statusBefore = get("/scripts/status?className=" + pluginClass);
        System.out.println(GSON.toJson(statusBefore));

        System.out.println("\n=== 3. Start the script ===");
        Map<String, Object> startResult = post("/scripts/start",
                Map.of("className", pluginClass));
        System.out.println(GSON.toJson(startResult));

        System.out.println("\n=== 4. Poll status while running ===");
        for (int i = 0; i < 5; i++) {
            Thread.sleep(2000);
            Map<String, Object> status = get("/scripts/status?className=" + pluginClass);
            System.out.println("  [" + (i * 2) + "s] " + GSON.toJson(status));
        }

        System.out.println("\n=== 5. Submit test results (simulating harness) ===");
        Map<String, Object> resultPayload = Map.of(
                "className", pluginClass,
                "passed", true,
                "loopCount", 10,
                "details", Map.of("avgLoopMs", 45, "errors", 0)
        );
        Map<String, Object> submitResult = post("/scripts/results", resultPayload);
        System.out.println(GSON.toJson(submitResult));

        System.out.println("\n=== 6. Retrieve results ===");
        Map<String, Object> results = get("/scripts/results?className=" + pluginClass);
        System.out.println(GSON.toJson(results));

        System.out.println("\n=== 7. Stop the script ===");
        Map<String, Object> stopResult = post("/scripts/stop",
                Map.of("className", pluginClass));
        System.out.println(GSON.toJson(stopResult));

        System.out.println("\n=== 8. Final status ===");
        Map<String, Object> statusAfter = get("/scripts/status?className=" + pluginClass);
        System.out.println(GSON.toJson(statusAfter));

        System.out.println("\nDone.");
    }

    private static void startClient(String[] args) {
        Class<?>[] debugPlugins = {
                AgentServerPlugin.class
        };

        RuneLiteDebug.pluginsToDebug.addAll(
                Arrays.stream(debugPlugins).collect(Collectors.toList())
        );

        Thread clientThread = new Thread(() -> {
            try {
                RuneLiteDebug.main(args);
            } catch (Exception e) {
                System.err.println("Client failed to start: " + e.getMessage());
                e.printStackTrace();
            }
        }, "microbot-client");
        clientThread.setDaemon(true);
        clientThread.start();
    }

    private static void waitForAgentServer() throws InterruptedException {
        System.out.println("Waiting for Agent Server on port 8081...");
        long deadline = System.currentTimeMillis() + STARTUP_TIMEOUT_MS;

        while (System.currentTimeMillis() < deadline) {
            try (Socket socket = new Socket("127.0.0.1", 8081)) {
                System.out.println("Agent Server is ready.");
                return;
            } catch (IOException ignored) {
            }
            Thread.sleep(POLL_INTERVAL_MS);
        }

        throw new RuntimeException("Agent Server did not start within " + (STARTUP_TIMEOUT_MS / 1000) + "s");
    }

    private static void login() throws Exception {
        System.out.println("Checking login status...");
        Map<String, Object> status = get("/login");
        System.out.println(GSON.toJson(status));

        if (Boolean.TRUE.equals(status.get("loggedIn"))) {
            System.out.println("Already logged in.");
            return;
        }

        Map<String, Object> loginResult = attemptAndVerifyLogin(Map.of("timeout", LOGIN_TIMEOUT_S));

        if (Boolean.TRUE.equals(loginResult.get("success"))) {
            System.out.println("Logged in successfully. World: " + loginResult.get("currentWorld"));
            return;
        }

        int loginIndex = loginResult.containsKey("loginIndex")
                ? ((Number) loginResult.get("loginIndex")).intValue()
                : -1;

        if (FATAL_LOGIN_ERRORS.contains(loginIndex)) {
            String error = (String) loginResult.get("loginError");
            System.err.println("Fatal login error (loginIndex=" + loginIndex + "): " + error);
            System.exit(1);
        }

        if (loginIndex == LOGIN_INDEX_NON_MEMBER) {
            System.out.println("Non-member account on members world, retrying on F2P world " + F2P_WORLD + "...");
            loginResult = attemptAndVerifyLogin(Map.of("timeout", LOGIN_TIMEOUT_S, "world", F2P_WORLD));

            if (Boolean.TRUE.equals(loginResult.get("success"))) {
                System.out.println("Logged in successfully on F2P world. World: " + loginResult.get("currentWorld"));
                return;
            }

            String error = loginResult.containsKey("loginError")
                    ? (String) loginResult.get("loginError")
                    : (String) loginResult.get("message");
            System.err.println("F2P retry also failed: " + error);
            System.exit(1);
        }

        if (loginIndex == LOGIN_INDEX_DISCONNECTED) {
            System.err.println("Disconnected from server, retrying...");
            loginResult = attemptAndVerifyLogin(Map.of("timeout", LOGIN_TIMEOUT_S));

            if (Boolean.TRUE.equals(loginResult.get("success"))) {
                System.out.println("Logged in successfully after retry. World: " + loginResult.get("currentWorld"));
                return;
            }

            System.err.println("Retry after disconnect also failed: " + loginResult.get("message"));
            System.exit(1);
        }

        String error = loginResult.containsKey("loginError")
                ? (String) loginResult.get("loginError")
                : (String) loginResult.get("message");
        System.err.println("Login failed: " + error);
        System.exit(1);
    }

    private static Map<String, Object> attemptAndVerifyLogin(Map<String, Object> params) throws Exception {
        Map<String, Object> loginResult = attemptLogin(params);

        if (!Boolean.TRUE.equals(loginResult.get("success"))) {
            return loginResult;
        }

        System.out.println("Verifying login state...");
        long deadline = System.currentTimeMillis() + VERIFY_TIMEOUT_MS;

        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(POLL_INTERVAL_MS);
            Map<String, Object> verifyStatus = get("/login");

            if (Boolean.TRUE.equals(verifyStatus.get("loggedIn"))) {
                System.out.println("Login verified.");
                Map<String, Object> verified = new LinkedHashMap<>();
                verified.put("success", true);
                verified.put("message", "Login successful");
                verified.put("currentWorld", verifyStatus.get("currentWorld"));
                return verified;
            }

            if ("LOGIN_SCREEN".equals(verifyStatus.get("gameState"))) {
                int idx = verifyStatus.containsKey("loginIndex")
                        ? ((Number) verifyStatus.get("loginIndex")).intValue()
                        : 0;
                if (idx > 0) {
                    System.out.println("Login verification failed (loginIndex=" + idx + ")");
                    System.out.println(GSON.toJson(verifyStatus));
                    Map<String, Object> corrected = new LinkedHashMap<>();
                    corrected.put("success", false);
                    corrected.put("message", "Login failed during verification");
                    corrected.put("loginIndex", idx);
                    if (verifyStatus.containsKey("loginError")) {
                        corrected.put("loginError", verifyStatus.get("loginError"));
                    }
                    return corrected;
                }
            }
        }

        Map<String, Object> finalStatus = get("/login");
        if (Boolean.TRUE.equals(finalStatus.get("loggedIn"))) {
            System.out.println("Login verified (late).");
            Map<String, Object> verified = new LinkedHashMap<>();
            verified.put("success", true);
            verified.put("message", "Login successful");
            verified.put("currentWorld", finalStatus.get("currentWorld"));
            return verified;
        }

        System.out.println("Login verification timed out.");
        System.out.println(GSON.toJson(finalStatus));
        Map<String, Object> corrected = new LinkedHashMap<>();
        corrected.put("success", false);
        corrected.put("message", "Login verification timed out");
        if (finalStatus.containsKey("loginIndex")) corrected.put("loginIndex", finalStatus.get("loginIndex"));
        if (finalStatus.containsKey("loginError")) corrected.put("loginError", finalStatus.get("loginError"));
        return corrected;
    }

    private static Map<String, Object> attemptLogin(Map<String, Object> params) throws IOException {
        int timeout = params.containsKey("timeout") ? ((Number) params.get("timeout")).intValue() : 30;
        System.out.println("Triggering login (blocking, timeout=" + timeout + "s)...");
        Map<String, Object> result = post("/login", params, (timeout + 10) * 1000);
        System.out.println(GSON.toJson(result));
        return result;
    }

    private static Map<String, Object> get(String path) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(BASE + path).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(10000);
        return readResponse(conn);
    }

    private static Map<String, Object> post(String path, Map<String, Object> body) throws IOException {
        return post(path, body, 10_000);
    }

    private static Map<String, Object> post(String path, Map<String, Object> body, int readTimeoutMs) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(BASE + path).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(readTimeoutMs);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(GSON.toJson(body).getBytes(StandardCharsets.UTF_8));
        }

        return readResponse(conn);
    }

    private static Map<String, Object> readResponse(HttpURLConnection conn) throws IOException {
        int code = conn.getResponseCode();
        java.io.InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        String json;
        try (Scanner sc = new Scanner(stream, StandardCharsets.UTF_8.name())) {
            json = sc.useDelimiter("\\A").hasNext() ? sc.next() : "{}";
        }
        if (code >= 400) {
            System.err.println("HTTP " + code + ": " + json);
        }
        return GSON.fromJson(json, MAP_TYPE);
    }
}
