package net.runelite.client.plugins.microbot.accountstarter.run;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.accountselector.AutoLoginPlugin;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerPlugin;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.breakhandler.breakhandlerv2.BreakHandlerV2Plugin;
import net.runelite.client.plugins.microbot.breakhandler.breakhandlerv2.BreakHandlerV2State;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.LoginManager;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

/** Script thread only: camera moves on the Swing thread freeze the UI. */
@Slf4j
public final class SessionSetup {
    /** Below this the view is too flat to see past trees and walls; 383 is the client's limit. */
    static final int MIN_PITCH = 345;

    private final Random random;
    public static final int MAX_LOGIN_TRIES = 30;
    private long nextLoginAt;
    private int loginTries;
    private boolean wasLoggedOut;
    private boolean framed;
    private long frameDueAt;
    private boolean fromWelcome;
    private int framedZoom;
    private long pitchFixAt;

    public SessionSetup(Random random) {
        this.random = random;
    }

    public enum Login { WAIT, LOGIN, NO_PROFILE }

    /** Leaves the login to a break handler, the Auto Login plugin, a login already under way, or the player at the screen. */
    static Login login(boolean atLoginScreen, boolean breakActive, boolean autoLogin, boolean attemptActive,
                       boolean playerHasInput, boolean hasProfile, boolean due) {
        if (!atLoginScreen || breakActive || autoLogin || attemptActive || playerHasInput || !due) return Login.WAIT;
        return hasProfile ? Login.LOGIN : Login.NO_PROFILE;
    }

    /** Either break handler is mid-break or about to start one; a disabled handler's state is stale. */
    public static boolean breakActive() {
        return (Microbot.isPluginEnabled(BreakHandlerPlugin.class) && BreakHandlerScript.isBreakActive())
                || (Microbot.isPluginEnabled(BreakHandlerV2Plugin.class) && BreakHandlerV2State.isBreakActive());
    }

    /** Null while all is well; otherwise why the run has to stop. */
    public String whileLoggedOut() {
        if (Microbot.getClient().getGameState() != GameState.LOADING) wasLoggedOut = true;
        framed = false;
        frameDueAt = 0;
        long now = System.currentTimeMillis();
        Login next = login(Microbot.getClient().getGameState() == GameState.LOGIN_SCREEN, breakActive(),
                Microbot.isPluginEnabled(AutoLoginPlugin.class), LoginManager.isLoginAttemptActive(), InputArbiter.isHuman(),
                LoginManager.getActiveProfile() != null, now >= nextLoginAt);
        if (next == Login.NO_PROFILE) return "logged out, and there is no login profile to log back in with";
        if (next == Login.LOGIN) {
            if (++loginTries > MAX_LOGIN_TRIES) return "could not log in after " + MAX_LOGIN_TRIES + " tries";
            nextLoginAt = now + 8000 + random.nextInt(7001);
            log.info("Account Starter: at the login screen; logging back in");
            LoginManager.login();
        }
        return null;
    }

    public boolean busy() {
        // the welcome screen is LOGGED_IN but swallows camera keys
        if (Rs2Widget.isWidgetVisible(InterfaceID.WelcomeScreen.PLAY)) {
            framed = false;
            frameDueAt = 0;
            fromWelcome = true;
            Global.sleep(600 + random.nextInt(901));
            if (Rs2Widget.isWidgetVisible(InterfaceID.WelcomeScreen.PLAY)) {
                Rs2Widget.clickWidget(InterfaceID.WelcomeScreen.PLAY);
                Global.sleepUntil(() -> !Rs2Widget.isWidgetVisible(InterfaceID.WelcomeScreen.PLAY), 3000);
            }
            return true;
        }
        if (framed) return false;
        long now = System.currentTimeMillis();
        if (frameDueAt == 0) {
            frameDueAt = now + (fromWelcome ? 1000 + random.nextInt(2001) : 600 + random.nextInt(901));
            fromWelcome = false;
        }
        if (now < frameDueAt) return true;
        framed = true;
        frameDueAt = 0;
        frame();
        return false;
    }

    public boolean backFromLogout() {
        loginTries = 0;
        if (!wasLoggedOut) return false;
        wasLoggedOut = false;
        Global.sleep(1200 + random.nextInt(1401));
        return true;
    }

    public void hold() {
        if (framedZoom <= 0 || Rs2Player.isMoving() || Rs2Player.isAnimating()) return;
        int zoom = Rs2Camera.getZoom();
        if (zoom > framedZoom + 10) {
            Rs2Camera.setZoom(framedZoom);
            Global.sleep(200 + random.nextInt(201));
        }
        int pitch = Rs2Camera.getPitch();
        if (pitch >= MIN_PITCH) {
            pitchFixAt = 0;
            return;
        }
        long t = System.currentTimeMillis();
        if (pitchFixAt == 0) {
            pitchFixAt = t + 3000 + random.nextInt(57001);
            return;
        }
        if (t < pitchFixAt) return;
        pitchFixAt = 0;
        Rs2Camera.setPitch(355 + random.nextInt(29));
        Global.sleep(200 + random.nextInt(201));
    }

    private void frame() {
        List<Runnable> steps = new ArrayList<>();
        // lower zoom is further out
        if (Rs2Camera.getZoom() > 140) steps.add(() -> Rs2Camera.setZoom(100 + random.nextInt(26)));
        int pitch = Rs2Camera.getPitch();
        if (pitch < MIN_PITCH) steps.add(() -> Rs2Camera.setPitch(355 + random.nextInt(29)));
        if (random.nextDouble() < 0.6) {
            int by = (random.nextBoolean() ? 1 : -1) * (15 + random.nextInt(46));
            steps.add(() -> Rs2Camera.setAngle(Math.floorMod(Rs2Camera.getAngle() + by, 360), 10));
        }
        Collections.shuffle(steps, random);
        for (int i = 0; i < steps.size(); i++) {
            if (i > 0) Global.sleep(180 + random.nextInt(381));
            steps.get(i).run();
        }
        framedZoom = Rs2Camera.getZoom();
        log.info("Account Starter: camera framed (zoom {}, pitch {})", framedZoom, Rs2Camera.getPitch());
    }
}
