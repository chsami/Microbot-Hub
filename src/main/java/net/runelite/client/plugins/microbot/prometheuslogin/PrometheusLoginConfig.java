package net.runelite.client.plugins.microbot.prometheuslogin;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigButton;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

/** Accounts themselves are managed in the account window (opened by the button below) and stored in a local file, never in this config. */
@ConfigGroup("prometheuslogin")
public interface PrometheusLoginConfig extends Config
{
    @ConfigItem(keyName = "openManager", name = "Open account manager", description = "Add, remove and log in to saved accounts.", position = 0)
    default ConfigButton openManager() { return new ConfigButton(); }

    @ConfigItem(keyName = "world", name = "World (0 = leave as is)", description = "Log in to this world. 0 keeps the world currently selected on the login screen.", position = 1)
    @Range(min = 0, max = 999)
    default int world() { return 0; }

    @ConfigItem(keyName = "relogin", name = "Log back in after disconnect", description = "After you have clicked an account and it logged in, log that same account back in if the client is disconnected. Off by default: nothing logs in unless you click it.", position = 2)
    default boolean relogin() { return false; }

    @ConfigItem(keyName = "maxAttempts", name = "Max failed attempts", description = "Stop trying after this many attempts in a row that do not reach the game. Protects the account from lockouts caused by a wrong password.", position = 3)
    @Range(min = 1, max = 10)
    default int maxAttempts() { return 3; }
}
