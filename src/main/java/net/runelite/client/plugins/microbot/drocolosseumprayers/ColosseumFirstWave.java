package net.runelite.client.plugins.microbot.drocolosseumprayers;

/**
 * Inferno's Jump-in starts wave 1; Colosseum arrival waits for Minimus "Start-wave".
 * Issues that first start after observed local opening readiness; later waves belong to the local engine.
 */
final class ColosseumFirstWave {
    static final int MINIMUS=12808;
    static final long SETTLE_MS=1_800L,RETRY_MS=4_200L;
    static final int ATTEMPTS=3;
    private long nextAt=-1;
    private int attempts;
    private boolean done,exhausted;
    void reset(){nextAt=-1;attempts=0;done=false;exhausted=false;}
    boolean done(){return done;}
    boolean exhausted(){return exhausted;}
    /**
     * @param ready local opening receipts complete and arena input allowed
     * @param startWaveIndex Minimus index while it offers Start-wave, otherwise -1
     * @param waveActive hostile NPCs present
     * @return the Minimus index to click now, or -1
     */
    int poll(long now,boolean ready,int startWaveIndex,boolean waveActive){
        if(done)return -1;
        // A visible wave, or Start-wave gone after our click, is the start receipt.
        if(waveActive||attempts>0&&startWaveIndex<0){done=true;return -1;}
        if(!ready)return -1;
        if(nextAt<0)nextAt=now+SETTLE_MS;
        if(startWaveIndex<0||now<nextAt)return -1;
        if(attempts>=ATTEMPTS){done=true;exhausted=true;return -1;}
        attempts++;nextAt=now+RETRY_MS;
        return startWaveIndex;
    }
}
