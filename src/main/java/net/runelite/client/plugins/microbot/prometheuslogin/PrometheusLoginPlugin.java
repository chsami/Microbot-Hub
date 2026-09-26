package net.runelite.client.plugins.microbot.prometheuslogin;

import com.google.inject.Provides;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
    name = "<html>[<font color=#a18fff>PL</font>] Prometheus Login",
    description = "A simple, fast way to log in to your legacy accounts. Information is stored locally only.",
    tags = {"login", "accounts", "credentials", "prometheus"},
    authors = {"BenMT"},
    version = PrometheusLoginPlugin.version,
    minClientVersion = "2.6.24",
    enabledByDefault = PluginConstants.DEFAULT_ENABLED,
    isExternal = PluginConstants.IS_EXTERNAL
)
public class PrometheusLoginPlugin extends Plugin
{
    static final String version = "1.1.0";
    private static final String GROUP = "prometheuslogin";
    private static final Logger log = LoggerFactory.getLogger(PrometheusLoginPlugin.class);

    @Inject private PrometheusLoginConfig config;
    @Inject private EventBus eventBus;
    private final LoginStore store = new LoginStore();
    private PrometheusLoginScript script;
    private AccountManagerWindow window;

    @Provides PrometheusLoginConfig provideConfig(ConfigManager manager) { return manager.getConfig(PrometheusLoginConfig.class); }

    @Override protected void startUp()
    {
        store.load(); // saved accounts are read from disk on every start; no account is logged in automatically
        script = new PrometheusLoginScript();
        script.run(config);
        SwingUtilities.invokeLater(() -> {
            if (script == null) return;
            window = new AccountManagerWindow(store, a -> script.requestLogin(a), () -> script == null ? "" : script.status(),
                JagexAccounts::displayNames, LauncherBridge::start, this::switchToJagexAccount);
            window.setVisible(true);
        });
    }

    /** Option B: just open the launcher (nothing here closes). Returns false if it could not be found/started. */
    boolean openLauncher()
    {
        return LauncherBridge.start();
    }

    /** Option C: open the launcher, then close this client so the user lands on its account picker. Only exits if the
     *  launcher actually started - a missing launcher must never leave the user stranded without a client. The
     *  caller (the window) is responsible for confirming this with the user first. */
    boolean switchToJagexAccount()
    {
        if (!LauncherBridge.start()) return false;
        log.info("Closing the client to switch Jagex accounts via the Microbot Launcher.");
        // Give other plugins/core a chance to react and save state, the same way the client's own close button does,
        // then exit shortly after regardless (mirrors BreakHandlerScript's own shutdown pattern).
        eventBus.post(new ClientShutdown());
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "prometheuslogin-shutdown");
            t.setDaemon(true);
            return t;
        }).schedule(() -> System.exit(0), 700, TimeUnit.MILLISECONDS);
        return true;
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (GROUP.equals(event.getGroup()) && "openManager".equals(event.getKey()))
            SwingUtilities.invokeLater(() -> {
                if (window == null) return;
                window.setVisible(true);
                window.setState(java.awt.Frame.NORMAL);
                window.toFront();
            });
    }

    @Override protected void shutDown()
    {
        if (script != null) { script.shutdown(); script = null; }
        SwingUtilities.invokeLater(() -> {
            if (window != null) { window.dispose(); window = null; }
        });
    }
}
