package net.runelite.client.plugins.microbot.droefficientwalker;

/** One unresolved withdrawal prevents all further withdrawals, including after its timeout. */
final class BankActionAck {
    enum Result { NONE, WAITING, ACKNOWLEDGED, TIMED_OUT }
    private int itemId = -1, beforeCount, expectedCount, dispatchTick;
    private long deadline;

    void begin(int itemId, int beforeCount, int requestedAmount, int dispatchTick, long now, long timeoutMillis) {
        if (pending()) throw new IllegalStateException("A bank withdrawal is still awaiting acknowledgement");
        if (beforeCount < 0 || requestedAmount <= 0 || timeoutMillis <= 0) throw new IllegalArgumentException("Invalid withdrawal acknowledgement");
        this.itemId = itemId; this.beforeCount = beforeCount;
        this.expectedCount = (int) Math.min(Integer.MAX_VALUE, (long) beforeCount + requestedAmount);
        this.dispatchTick = dispatchTick; deadline = now + timeoutMillis;
    }
    Result observe(int currentCount, int tick, long now) {
        if (!pending()) return Result.NONE;
        if (tick > dispatchTick && currentCount >= expectedCount && currentCount > beforeCount) {
            clear(); return Result.ACKNOWLEDGED;
        }
        return now >= deadline ? Result.TIMED_OUT : Result.WAITING;
    }
    boolean pending() { return itemId >= 0; }
    void defer(long pausedMillis) { if (pending()) deadline += Math.max(0, pausedMillis); }
    int itemId() { return itemId; }
    void clear() { itemId = -1; }
}
