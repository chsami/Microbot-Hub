package net.runelite.client.plugins.microbot.droinfernoprayers;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class ArenaInputDiagnostics implements AutoCloseable {
    private static final ThreadLocal<Trace> CURRENT = new ThreadLocal<>();
    private final Trace previous;
    private ArenaInputDiagnostics(Trace trace) {
        previous = CURRENT.get();
        if (trace == null) CURRENT.remove(); else CURRENT.set(trace);
    }
    public static ArenaInputDiagnostics open(Trace trace) { return new ArenaInputDiagnostics(trace); }
    public static Trace current() { return CURRENT.get(); }
    @Override public void close() { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
    public static final class Trace {
        private final long id, received, gesture;
        private final String op;
        private final Consumer<Map<String,Object>> sink;
        private final AtomicLong gestures, presses = new AtomicLong();
        private final java.util.concurrent.atomic.AtomicBoolean completed = new java.util.concurrent.atomic.AtomicBoolean();
        private volatile String noPressReason = "CALLBACK_RETURNED_WITHOUT_PRESS";
        public long gestureCount() { return gestures.get(); }

        public void finishGesture() {
            if (gesture == 0 || !completed.compareAndSet(false, true)) return;
            event("DELIVERY_RECEIPT", presses.get() > 0 ? "PRESS_EMITTED" : noPressReason, 0);
        }
        public Trace(long id, String op, Consumer<Map<String,Object>> sink) {
            this.received=System.nanoTime(); this.id=id; this.op=op; this.sink=sink; this.gesture=0; this.gestures=new AtomicLong();
        }
        private Trace(Trace parent) {
            received=parent.received; id=parent.id; op=parent.op; sink=parent.sink; gestures=parent.gestures;
            gesture=gestures.incrementAndGet();
        }
        public Trace gesture() { return new Trace(this); }
        public void event(String stage, String reason, long waitNanos) {
            event(stage, reason, waitNanos, Map.of());
        }

        public void invoke(boolean returned, net.runelite.api.MenuAction action, int param0, int param1, int identifier, int itemId) {
            event(returned ? "INVOKE_FIRED" : "INVOKE_DISPATCH", action.name(), 0,
                Map.of("inputMode", "INVOKE", "menuAction", action.name(), "opcode", action.getId(),
                    "param0", param0, "param1", param1, "identifier", identifier, "itemId", itemId));
        }
        private void event(String stage, String reason, long waitNanos, Map<String, Object> extra) {
            if (stage.equals("INVOKE_FIRED")) noPressReason = "INVOKE_RETURNED";
            else if (stage.equals("PRAYER_ALREADY_APPLIED")) noPressReason = "ALREADY_SATISFIED";
            else if (!noPressReason.equals("ALREADY_SATISFIED") && (stage.contains("CANCEL") || stage.contains("DROPPED") || stage.contains("ABORT")
                || stage.contains("TIMEOUT") || stage.equals("TAB_NOT_READY") || stage.equals("PRAYER_STATE_UNAVAILABLE")
                || stage.equals("INVALID_COMBAT_POINT") || stage.equals("GESTURE_EXCEPTION"))) noPressReason = reason;
            try {
                var data=new LinkedHashMap<String,Object>();
                data.put("kind","arena_input"); data.put("id",id); data.put("op",op);
                data.put("gesture",gesture); data.put("stage",stage); data.put("reason",reason);
                data.put("elapsedMs",Math.max(0,(System.nanoTime()-received)/1_000_000L));
                data.put("waitNanos",Math.max(0,waitNanos)); data.put("presses",presses.get());
                data.put("thread",Thread.currentThread().getName());
                data.putAll(extra);
                sink.accept(data);
            } catch (RuntimeException ignored) {  }
            if (stage.equals("QUEUE_DROPPED") || stage.equals("GESTURE_DROPPED")) finishGesture();
        }
    }
    public static void event(String stage, String reason) { event(stage,reason,0); }
    public static void event(String stage, String reason, long waitNanos) {
        Trace trace=CURRENT.get(); if(trace!=null)trace.event(stage,reason,waitNanos);
    }
    public static void pressed() {
        pressed("CANVAS_DISPATCH_RETURNED");
    }
    public static void pressed(String reason) {
        Trace trace=CURRENT.get();
        if(trace!=null){trace.presses.incrementAndGet();trace.event("PRESS_EMITTED",reason,0);}
    }

    private static Trace effective(Trace captured) {
        Trace active=CURRENT.get();
        return captured.gesture==0 && active!=null && active.gestures==captured.gestures
            && active.gesture!=0 ? active : captured;
    }
    public static Runnable capture(Runnable task) {
        Trace trace=CURRENT.get(); if(trace==null)return task;
        return ()->{try(var ignored=open(effective(trace))){task.run();}};
    }
    public static BooleanSupplier capture(BooleanSupplier task) {
        Trace trace=CURRENT.get(); if(trace==null)return task;
        return ()->{try(var ignored=open(effective(trace))){return task.getAsBoolean();}};
    }
}
