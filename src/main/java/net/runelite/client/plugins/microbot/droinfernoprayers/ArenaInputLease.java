package net.runelite.client.plugins.microbot.droinfernoprayers;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.MicrobotConfig;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;

/**
 * Keeps input yielding off while the plugin runs. The override is held in memory only: the player's
 * saved "Disable input yielding" setting is read, never written, so a crash or a closed client leaves
 * it exactly as they set it.
 */
public final class ArenaInputLease implements AutoCloseable {
    private static final Object LOCK=new Object();
    private static final String GROUP=MicrobotConfig.configGroup,KEY=MicrobotConfig.keyDisableInputYielding;
    private static int owners;
    private static boolean before;
    public static boolean active(){synchronized(LOCK){return owners>0;}}
    private final ConfigManager configs;
    private boolean closed;
    public ArenaInputLease(ConfigManager configs){
        this.configs=configs;
        synchronized(LOCK){if(owners++==0)before=InputArbiter.isDisabled();enforce();}
    }
    public void enforce(){synchronized(LOCK){if(!closed)InputArbiter.setDisabled(true);}}
    @Override public void close(){synchronized(LOCK){if(closed)return;closed=true;if(--owners>0)return;
        // The player may have changed the setting while the plugin ran; their saved choice wins over the value seen at start.
        String saved=configs==null?null:configs.getConfiguration(GROUP,KEY);
        InputArbiter.setDisabled(saved==null?before:Boolean.parseBoolean(saved));
    }}
}
