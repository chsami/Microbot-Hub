package net.runelite.client.plugins.microbot.drocolosseumprayers;

import net.runelite.client.config.*;

/** Shopping and extra supply checks are one opt-in flow for each arena. */
public interface ArenaPreparationConfig extends Config {
    String BUY_WARNING="Warning: Dart count and rune count are not checked when Automate startup is OFF. Inventory Setup is not checked or loaded.";
    @ConfigItem(keyName="automateStartup",name="Automate startup",description="On: load the selected setup at the Colosseum outside bank, prepare, travel and enter. Off: gear up yourself and start beside the entrance; no bank, Inventory Setup or supply checks.",position=0)
    default boolean automateStartup(){return false;}
    @ConfigItem(keyName="fightInput",name="Fight input",description="Natural mouse uses Dro mouse movement. Direct invokes sends menu actions directly and walks with the Natural mouse. Both use the same fight logic.",position=4)
    default ArenaFightInput fightInput(){return ArenaFightInput.MICROBOT_MOUSE;}
    @ConfigItem(keyName="prayersOnly",name="Prayers only",description="Only switch protection and offensive prayers; you play everything else (movement, attacks, gear, potions, inventory). Prayers are invoked directly without opening the prayer book or moving the mouse.",position=5)
    default boolean prayersOnly(){return false;}
    @ConfigItem(keyName="manualMovement",name="Manual movement",description="The script plays everything except walking and most attacks: prayers, gear, potions, specials and wave starts stay automatic and it attacks only the Fremennik warband (Archer, Seer, Berserker) and casts the last-enemy Blood heal, from where you stand; you move the player and attack everything else yourself (no flare or position steps; the Javelin sidestep stays automatic and clicks its tile without moving the mouse there; direct invokes are used; wave 1 starts once you stand on the start tile). Prayers only takes precedence. Applies on the next run.",position=6)
    default boolean manualMovement(){return false;}
    @Range(min=1,max=99)
    @ConfigItem(keyName="vitalsPercent",name="Eat at HP %",description="Brew when HP drops below this percentage of your current maximum HP (1-99); Frailty's lower maximum counts, so 80% with Frailty I at 99 HP brews below 71. No top-ups between waves except before Sol. Applies on the next run.",position=7)
    default int vitalsPercent(){return 55;}
    /** Shared connection options; unset values are omitted so the server keeps its defaults. */
    static void putSharedOptions(ArenaPreparationConfig config,java.util.Map<String,Object> options){
        int vitals=config.vitalsPercent();
        if(vitals>0)options.put("vitalsPercent",Math.min(99,vitals));
    }
}
