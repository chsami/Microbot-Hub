package net.runelite.client.plugins.microbot.prometheuslogin;

import com.google.inject.Provides;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;

@PluginDescriptor(
    name = "<html>[<font color=#a18fff>PL</font>] Prometheus Login",
    description = "A simple, fast way to log in to your legacy accounts. Information is stored locally only.",
    tags = {"login", "accounts", "credentials", "prometheus"},
    authors = {"BenMT"},
    version = PrometheusLoginPlugin.version,
    minClientVersion = "2.6.22",
    enabledByDefault = PluginConstants.DEFAULT_ENABLED,
    isExternal = PluginConstants.IS_EXTERNAL
)
public class PrometheusLoginPlugin extends Plugin
{
    static final String version = "1.0.0";
    private static final String GROUP = "prometheuslogin";

    @Inject private PrometheusLoginConfig config;
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
            window = new AccountManagerWindow(store, a -> script.requestLogin(a), () -> script == null ? "" : script.status());
            window.setVisible(true);
        });
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
