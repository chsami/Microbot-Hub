package net.runelite.client.plugins.microbot.zulrahslayer.actions;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

import java.awt.Point;
import java.awt.event.KeyEvent;
import java.util.Random;

import static net.runelite.client.plugins.microbot.util.Global.sleepGaussian;

public class RandomAction implements ZulrahAction {

    private static final float MIN_PITCH = 0.35f;
    private static final float MAX_PITCH = 0.85f;

    private static final int MAX_YAW_DELTA = 45;
    private static final int MIN_CAMERA_PRESS_MS = 60;
    private static final int MAX_CAMERA_PRESS_MS = 250;

    private static final double YAW_MS_PER_DEGREE = 2.0;

    private static final int MOUSE_MAX_DELTA = 50;

    private final Random random = new Random();

    @Override
    public int order() {
        return 1_000_000;
    }

    @Override
    public String key() {
        return "random-input";
    }

    @Override
    public boolean needsExecution(ZulrahState state) {
        return Rs2Random.between(1, 10) > 8;
    }

    @Override
    public Object execute(ZulrahState state) {
        // Choose between a camera adjustment and a mouse movement.
        if (random.nextBoolean()) {
            return performCameraMovement()
                    ? "camera-movement"
                    : null;
        }

        return performMouseMovement()
                ? "mouse-movement"
                : null;
    }

    /**
     * Performs a bounded camera adjustment relative to the current camera
     * position.
     */
    private boolean performCameraMovement() {
        try {
            int currentYaw = Rs2Camera.getAngle();

            // Small relative yaw adjustment.
            int yawDelta = (int) Math.round(random.nextGaussian() * 15);
            yawDelta = clamp(yawDelta, -MAX_YAW_DELTA, MAX_YAW_DELTA);

            if (yawDelta == 0) {
                return false;
            }

            int targetYaw = Math.floorMod(currentYaw + yawDelta, 360);
            int signedDelta = Rs2Camera.getAngleTo(targetYaw);

            int pressTime = calculateCameraPressTime(Math.abs(signedDelta));

            int key = signedDelta > 0
                    ? KeyEvent.VK_LEFT
                    : KeyEvent.VK_RIGHT;

            Rs2Keyboard.keyHold(key);

            try {
                sleepGaussian(
                        pressTime,
                        Math.max(10, pressTime / 6)
                );
            } finally {
                Rs2Keyboard.keyRelease(key);
            }

            // Sometimes adjust pitch after the yaw movement.
            if (random.nextInt(100) < 35) {
                performPitchAdjustment();
            }

            return true;

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Performs a small pitch adjustment relative to the current pitch.
     */
    private boolean performPitchAdjustment() {
        try {
            sleepGaussian(80, 25);

            float currentPitch = Rs2Camera.cameraPitchPercentage();

            float pitchDelta =
                    (float) (random.nextGaussian() * 0.025);

            float targetPitch = clamp(
                    currentPitch + pitchDelta,
                    MIN_PITCH,
                    MAX_PITCH
            );

            if (Math.abs(targetPitch - currentPitch) < 0.005f) {
                return false;
            }

            Rs2Camera.adjustPitch(targetPitch);

            return true;

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Performs a small relative mouse movement.
     */
    private boolean performMouseMovement() {
        try {
            Point start = Microbot.getMouse().getMousePosition();

            if (start == null) {
                return false;
            }

            int dx = (int) Math.round(
                    random.nextGaussian() * 15
            );

            int dy = (int) Math.round(
                    random.nextGaussian() * 15
            );

            dx = clamp(
                    dx,
                    -MOUSE_MAX_DELTA,
                    MOUSE_MAX_DELTA
            );

            dy = clamp(
                    dy,
                    -MOUSE_MAX_DELTA,
                    MOUSE_MAX_DELTA
            );

            int targetX = Math.max(2, start.x + dx);
            int targetY = Math.max(2, start.y + dy);

            moveCursor(targetX, targetY);

            return true;

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Uses Microbot's natural mouse implementation when available.
     */
    private void moveCursor(int x, int y) {
        if (x <= 1 || y <= 1) {
            return;
        }

        if (Microbot.naturalMouse != null) {
            Microbot.naturalMouse.moveTo(x, y);
        } else {
            Microbot.getMouse().move(x, y);
        }
    }

    /**
     * Calculates a bounded camera key-press duration based on the amount
     * of rotation required.
     */
    private int calculateCameraPressTime(int degrees) {
        int duration =
                (int) Math.round(
                        80 + degrees * YAW_MS_PER_DEGREE
                );

        // Add bounded timing variation.
        double multiplier =
                0.90 + random.nextGaussian() * 0.08;

        multiplier = clamp(multiplier, 0.70, 1.10);

        duration = (int) Math.round(
                duration * multiplier
        );

        return clamp(
                duration,
                MIN_CAMERA_PRESS_MS,
                MAX_CAMERA_PRESS_MS
        );
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}