package net.runelite.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Scanner;

/**
 * Standalone login test — run against a running client with Agent Server enabled.
 *
 * Tests the full login flow including non-member detection and F2P fallback.
 * Does NOT launch the client itself — start it separately first.
 *
 * Usage:
 *   1. Launch client with Agent Server enabled on port 8081
 *   2. Make sure the client is on the login screen (not logged in)
 *   3. Run:  java LoginTest [members-world]
 *
 * Examples:
 *   java LoginTest          # login with profile defaults
 *   java LoginTest 360      # force members world 360 (triggers non-member error for F2P accounts)
 *   java LoginTest 301      # force F2P world 301
 */
public class LoginTest {

    private static final String BASE = "http://127.0.0.1:8081";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

    private static final int LOGIN_INDEX_NON_MEMBER = 34;
    private static final int LOGIN_INDEX_BANNED = 14;
    private static final int LOGIN_INDEX_AUTH_FAILURE = 3;
    private static final int LOGIN_INDEX_INVALID_CREDS = 4;
    private static final int F2P_FALLBACK_WORLD = 301;

    public static void main(String[] args) throws Exception {
        int targetWorld = args.length > 0 ? Integer.parseInt(args[0]) : -1;

        step("1. Check current login status");
        Map<String, Object> status = get("/login");
        print(status);

        if (Boolean.TRUE.equals(status.get("loggedIn"))) {
            System.out.println("Already logged in on world " + status.get("currentWorld"));
            System.out.println("Log out first to test login flow.");
            return;
        }

        if (status.containsKey("loginError")) {
            System.out.println("Login screen shows error: " + status.get("loginError"));
            System.out.println("loginIndex: " + status.get("loginIndex"));
        }

        step("2. Attempt login" + (targetWorld > 0 ? " on world " + targetWorld : " with profile defaults"));
        Map<String, Object> loginParams = targetWorld > 0
                ? Map.of("world", targetWorld, "timeout", 30)
                : Map.of("timeout", 30);

        Map<String, Object> result = post("/login", loginParams, 40_000);
        print(result);

        if (Boolean.TRUE.equals(result.get("success"))) {
            step("LOGIN SUCCESSFUL");
            System.out.println("World: " + result.get("currentWorld"));
            verifyLoggedIn();
            return;
        }

        int loginIndex = result.containsKey("loginIndex")
                ? ((Number) result.get("loginIndex")).intValue()
                : -1;
        String loginError = result.containsKey("loginError")
                ? (String) result.get("loginError")
                : (String) result.get("message");

        step("3. Login failed — diagnosing");
        System.out.println("loginIndex: " + loginIndex);
        System.out.println("loginError: " + loginError);

        switch (loginIndex) {
            case LOGIN_INDEX_NON_MEMBER:
                step("4. NON-MEMBER DETECTED — retrying on F2P world " + F2P_FALLBACK_WORLD);
                Map<String, Object> retryResult = post("/login",
                        Map.of("world", F2P_FALLBACK_WORLD, "timeout", 30), 40_000);
                print(retryResult);

                if (Boolean.TRUE.equals(retryResult.get("success"))) {
                    step("LOGIN SUCCESSFUL (F2P fallback)");
                    System.out.println("World: " + retryResult.get("currentWorld"));
                    verifyLoggedIn();
                } else {
                    step("F2P FALLBACK ALSO FAILED");
                    System.out.println("Error: " + retryResult.get("message"));
                }
                break;

            case LOGIN_INDEX_BANNED:
                step("ACCOUNT BANNED — cannot recover");
                break;

            case LOGIN_INDEX_AUTH_FAILURE:
            case LOGIN_INDEX_INVALID_CREDS:
                step("INVALID CREDENTIALS — check profile username/password");
                break;

            default:
                step("UNKNOWN ERROR (loginIndex=" + loginIndex + ")");
                System.out.println("Message: " + loginError);
                break;
        }

        step("5. Final status");
        Map<String, Object> finalStatus = get("/login");
        print(finalStatus);
    }

    private static void verifyLoggedIn() throws IOException {
        System.out.println("\nVerifying with GET /login...");
        Map<String, Object> check = get("/login");
        boolean loggedIn = Boolean.TRUE.equals(check.get("loggedIn"));
        System.out.println("loggedIn: " + loggedIn);
        System.out.println("gameState: " + check.get("gameState"));
        if (check.containsKey("loginDurationMs")) {
            System.out.println("loginDurationMs: " + check.get("loginDurationMs"));
        }
        if (!loggedIn) {
            System.out.println("WARNING: POST said success but GET says not logged in!");
            System.out.println("loginIndex: " + check.get("loginIndex"));
            System.out.println("loginError: " + check.get("loginError"));
        }

        System.out.println("\nVerifying with GET /state...");
        Map<String, Object> state = get("/state");
        print(state);
    }

    private static void step(String label) {
        System.out.println("\n=== " + label + " ===");
    }

    private static void print(Map<String, Object> data) {
        System.out.println(GSON.toJson(data));
    }

    private static Map<String, Object> get(String path) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(BASE + path).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(10000);
        return readResponse(conn);
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
