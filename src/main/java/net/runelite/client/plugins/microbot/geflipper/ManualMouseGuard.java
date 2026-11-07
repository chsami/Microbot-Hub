package net.runelite.client.plugins.microbot.geflipper;

import java.awt.Canvas;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseManager;
import net.runelite.client.input.MouseWheelListener;
import net.runelite.client.plugins.microbot.util.mouse.BotEventGuard;

/** Blocks physical canvas input without changing the client's shared input switch or preferences. */
final class ManualMouseGuard extends MouseAdapter implements MouseWheelListener, AutoCloseable {
    private final MouseManager mouseManager;
    private final Supplier<Canvas> canvas;
    private final BooleanSupplier block;
    private volatile boolean registered;

    ManualMouseGuard(MouseManager mouseManager, Supplier<Canvas> canvas, BooleanSupplier block) {
        this.mouseManager = Objects.requireNonNull(mouseManager);
        this.canvas = Objects.requireNonNull(canvas);
        this.block = Objects.requireNonNull(block);
    }

    synchronized void start() {
        if (registered) return;
        // Run before other canvas mouse handlers; synthetic SDK input still passes through normally.
        mouseManager.registerMouseListener(0, this);
        try {
            mouseManager.registerMouseWheelListener(0, this);
            registered = true;
        } catch (RuntimeException failure) {
            mouseManager.unregisterMouseListener(this);
            throw failure;
        }
    }

    @Override
    public synchronized void close() {
        registered = false;
        mouseManager.unregisterMouseListener(this);
        mouseManager.unregisterMouseWheelListener(this);
    }

    private <T extends MouseEvent> T filter(T event) {
        if (registered && !BotEventGuard.isSynthetic() && event.getSource() == canvas.get()
            && block.getAsBoolean()) event.consume();
        return event;
    }

    @Override public MouseEvent mouseClicked(MouseEvent event) { return filter(event); }
    @Override public MouseEvent mousePressed(MouseEvent event) { return filter(event); }
    @Override public MouseEvent mouseReleased(MouseEvent event) { return filter(event); }
    @Override public MouseEvent mouseEntered(MouseEvent event) { return filter(event); }
    @Override public MouseEvent mouseExited(MouseEvent event) { return filter(event); }
    @Override public MouseEvent mouseDragged(MouseEvent event) { return filter(event); }
    @Override public MouseEvent mouseMoved(MouseEvent event) { return filter(event); }
    @Override public MouseWheelEvent mouseWheelMoved(MouseWheelEvent event) { return filter(event); }
}
