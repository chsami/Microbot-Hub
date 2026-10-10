package net.runelite.client.plugins.microbot.drofirecape.optional;

/**
 * Public-client guard lifecycle. The released client uses the existing visible
 * prayer/widget bounds and BaseProfileDro's disabled off-screen parking.
 * This adapter deliberately needs no private client extension.
 */
final class FcCanvasGuard implements AutoCloseable {
    private FcCanvasGuard() {}
    static FcCanvasGuard acquire(){return new FcCanvasGuard();}
    static FcCanvasGuard acquire(ClassLoader loader){return acquire();}
    boolean nativeGuard(){return false;}
    @Override public void close() {}
}
