package net.runelite.client.plugins.microbot.droefficientwalker;

import java.awt.event.MouseEvent;
import java.util.Deque;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import net.runelite.api.Point;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.input.InputLoop;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.droefficientwalker.compat.MouseMotionScope;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.MouseMotionFactory;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.SystemCalls;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.MouseMotionNature;

/** A voyage ticket is checked after waiting for the shared input lock, next to emission. */
final class SailingInput {
    private SailingInput() { }
    private static void moveWithTrail(InputLoop.Emit emit, int x, int y) {
        emit.move(x, y);
        // Match Walker's existing shared overlay; don't clear another plugin's trail.
        Deque<Point> points = Microbot.getMouse().getPoints();
        if (points == null) return;
        points.addLast(new Point(x, y));
        if (points.size() > 500) points.pollFirst();
    }
    private static void move(Point target, InputLoop.Emit emit, BooleanSupplier permission) {
        MouseMotionFactory source = Microbot.getNaturalMouse() == null ? null : Microbot.getNaturalMouse().getFactory();
        if (source == null) { moveWithTrail(emit, target.getX(), target.getY()); return; }
        // Copy the motion nature as Walker does; never mutate the shared factory.
        MouseMotionNature original = source.getNature(), copy = new MouseMotionNature();
        copy.setTimeToStepsDivider(original.getTimeToStepsDivider());
        copy.setMinSteps(original.getMinSteps());
        copy.setEffectFadeSteps(original.getEffectFadeSteps());
        copy.setReactionTimeBaseMs(original.getReactionTimeBaseMs());
        copy.setReactionTimeVariationMs(original.getReactionTimeVariationMs());
        copy.setMouseInfo(original.getMouseInfo());
        copy.setSpeedManager(original.getSpeedManager());
        copy.setNoiseProvider(original.getNoiseProvider());
        copy.setDeviationProvider(original.getDeviationProvider());
        copy.setOvershootManager(original.getOvershootManager());
        SystemCalls calls = original.getSystemCalls();
        copy.setSystemCalls(new SystemCalls() {
            public long currentTimeMillis() { return calls.currentTimeMillis(); }
            public void sleep(long ms) { calls.sleep(ms); }
            public java.awt.Dimension getScreenSize() { return calls.getScreenSize(); }
            public void setMousePosition(int x, int y) {
                if (!permission.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new CancellationException();
                moveWithTrail(emit, x, y);
            }
        });
        try { new MouseMotionFactory(copy).move(target.getX(), target.getY()); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new CancellationException(); }
    }

    static boolean click(Point point, NewMenuEntry entry, BooleanSupplier permission) {
        if (point == null || permission == null) return false;
        AtomicBoolean sent = new AtomicBoolean();
        InputLoop.Result result;
        try (var ignored = WalkerMouseControl.openProfile()) { result = InputLoop.run(emit -> {
            if (!permission.getAsBoolean()) return;
            move(point, emit, permission);
            MouseMotionScope.beforeClick();
            if (!permission.getAsBoolean()) return;
            if (entry != null) Microbot.targetMenu = entry;
            emit.press(point.getX(), point.getY(), MouseEvent.BUTTON1);
            emit.release(point.getX(), point.getY(), MouseEvent.BUTTON1);
            if (!permission.getAsBoolean()) { Microbot.targetMenu = null; return; }
            emit.click(point.getX(), point.getY(), MouseEvent.BUTTON1);
            sent.set(true);
        }); } catch (CancellationException | MouseMotionScope.Cancelled ex) { return false; }
        return result == InputLoop.Result.COMPLETED && sent.get();
    }
}
