package net.runelite.client.plugins.microbot.drocolosseumprayers;

import java.util.IdentityHashMap;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.MicrobotConfig;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;

public final class ArenaInputLease implements AutoCloseable {
    private static final IdentityHashMap<ConfigManager,State> LIVE=new IdentityHashMap<>();
    private static final String GROUP=MicrobotConfig.configGroup,KEY=MicrobotConfig.keyDisableInputYielding;
    public static boolean active(){synchronized(LIVE){return !LIVE.isEmpty();}}
    private final ConfigManager configs;
    private boolean closed;
    private static final class State {String original;boolean disabled;int owners;}
    public ArenaInputLease(ConfigManager configs){
        this.configs=configs;
        synchronized(LIVE){State s=LIVE.get(configs);if(s==null){s=new State();s.original=configs.getConfiguration(GROUP,KEY);s.disabled=InputArbiter.isDisabled();LIVE.put(configs,s);}s.owners++;enforce();}
    }
    public void enforce(){synchronized(LIVE){if(closed)return;if(!"true".equals(configs.getConfiguration(GROUP,KEY)))configs.setConfiguration(GROUP,KEY,true);InputArbiter.setDisabled(true);}}
    @Override public void close(){synchronized(LIVE){if(closed)return;closed=true;State s=LIVE.get(configs);if(s==null||--s.owners>0)return;LIVE.remove(configs);
        if(s.original==null)configs.unsetConfiguration(GROUP,KEY);else configs.setConfiguration(GROUP,KEY,s.original);
        InputArbiter.setDisabled(s.disabled);
    }}
}
