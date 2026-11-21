package net.runelite.client.plugins.microbot.droefficientwalker;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import net.runelite.client.plugins.microbot.util.input.AwtEmitter;
import net.runelite.client.plugins.microbot.util.input.PointerState;
import org.mockito.MockedStatic;
import org.mockito.invocation.InvocationOnMock;

import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.mockStatic;

final class AwtEmitterStub implements AutoCloseable {
    static final class Event {
        final String kind;
        final int x;
        final int y;
        Event(String kind,int x,int y) {this.kind=kind;this.x=x;this.y=y;}
        boolean is(String kind,int x,int y) {return this.kind.equals(kind)&&this.x==x&&this.y==y;}
    }

    final List<Event> events=new CopyOnWriteArrayList<>();
    private volatile BiConsumer<Integer,Integer> onMove=(x,y)->{};
    private final MockedStatic<AwtEmitter> mock;

    AwtEmitterStub() {mock=mockStatic(AwtEmitter.class,this::answer);}

    AwtEmitterStub onMove(BiConsumer<Integer,Integer> listener) {onMove=listener;return this;}

    long count(String kind,int x,int y) {return events.stream().filter(e->e.is(kind,x,y)).count();}

    boolean sentAnything() {return !events.isEmpty();}

    private Object answer(InvocationOnMock invocation) throws Throwable {
        String kind=kind(invocation.getMethod().getName());
        if(kind==null) return RETURNS_DEFAULTS.answer(invocation);
        int x=invocation.getArgument(0),y=invocation.getArgument(1);
        events.add(new Event(kind,x,y));
        if(kind.equals("move")) {PointerState.setFromBot(x,y);onMove.accept(x,y);}
        return invocation.getMethod().getReturnType()==boolean.class?Boolean.TRUE:null;
    }

    private static String kind(String method) {
        switch(method) {
            case "moved": case "tryMove": return "move";
            case "pressed": case "tryPress": return "press";
            case "released": case "releaseOwned": return "release";
            case "clicked": case "tryClick": return "click";
            case "wheel": case "tryWheel": return "wheel";
            default: return null;
        }
    }

    @Override public void close() {mock.close();}
}
