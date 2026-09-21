package net.runelite.client.plugins.microbot.prometheuslogin;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import net.runelite.api.GameState;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.security.LoginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Logs the account the user clicked in whenever the client sits on the login screen. Never acts before a click. */
public class PrometheusLoginScript extends Script
{
    private static final Logger log = LoggerFactory.getLogger(PrometheusLoginScript.class);
    private static final int BANNED_LOGIN_INDEX = 14;
    private static final long ATTEMPT_COOLDOWN_MS = 15_000;

    private PrometheusLoginConfig config;
    private volatile LoginStore.Account target;
    private volatile String status = "";
    private int failedAttempts;
    private long lastAttempt;
    private boolean sawLoggedIn;
    private boolean gaveUp;

    public boolean run(PrometheusLoginConfig config)
    {
        this.config = config;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try { tick(); }
            catch (Exception ex) { log.warn("Prometheus Login tick failed", ex); }
        }, 1, 2, TimeUnit.SECONDS);
        return true;
    }

    String status() { return status; }

    /** Makes this the account to log in and resets the failure counter, so a click always gets a fresh set of attempts. */
    synchronized void requestLogin(LoginStore.Account account)
    {
        target = account;
        failedAttempts = 0;
        lastAttempt = 0;
        sawLoggedIn = false;
        gaveUp = false;
        status = "Waiting for the login screen to log in " + account.label;
    }

    private synchronized void tick()
    {
        var client = Microbot.getClient();
        LoginStore.Account account = target;
        if (client == null || account == null) return;
        GameState state = client.getGameState();

        if (state == GameState.LOGGED_IN)
        {
            failedAttempts = 0;
            gaveUp = false;
            sawLoggedIn = true;
            status = "Logged in (" + account.label + ")";
            return;
        }
        // Only act on the idle login screen; LOGGING_IN / LOADING / CONNECTION_LOST are transitions.
        if (state != GameState.LOGIN_SCREEN) return;
        if (gaveUp) return;
        if (sawLoggedIn && !config.relogin()) return;

        int loginIndex = Microbot.getClientThread().runOnClientThreadOptional(client::getLoginIndex).orElse(-1);
        if (loginIndex == BANNED_LOGIN_INDEX)
        {
            log.warn("Account is banned or locked (login index {}); not retrying.", loginIndex);
            status = account.label + " appears banned or locked - not retrying";
            gaveUp = true;
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastAttempt < ATTEMPT_COOLDOWN_MS) return;

        if (failedAttempts >= config.maxAttempts())
        {
            log.warn("{} login attempts did not reach the game; giving up so the account is not locked.", failedAttempts);
            status = "Gave up on " + account.label + " after " + failedAttempts + " attempts - check the password, then click it again";
            gaveUp = true;
            return;
        }

        // LoginManager expects Microbot's AES/Base64 form. Encryption.encrypt() skips values ending in "==", so encrypt directly.
        String encrypted;
        try
        {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec("microbot12345678".getBytes(StandardCharsets.UTF_8), "AES"));
            encrypted = Base64.getEncoder().encodeToString(cipher.doFinal(account.password.getBytes(StandardCharsets.UTF_8)));
        }
        catch (Exception ex)
        {
            log.warn("Could not prepare password for login", ex);
            status = "Could not prepare the password for login";
            gaveUp = true;
            return;
        }

        lastAttempt = now;
        failedAttempts++;
        status = "Logging in " + account.label + " (attempt " + failedAttempts + "/" + config.maxAttempts() + ")";
        log.info("Logging in {} (attempt {}/{})", account.label, failedAttempts, config.maxAttempts());
        LoginManager.login(account.username, encrypted, config.world());
    }
}
