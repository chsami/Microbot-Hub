package net.runelite.client.plugins.microbot.prometheuslogin;

import java.io.File;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finds and starts the real, official Microbot Launcher so the user can sign a Jagex account in (or switch to one)
 * themselves, in the launcher's own window. This class never touches a Jagex credential, token or session: it only
 * looks for the launcher's installed .exe and asks Windows to start it, exactly as double-clicking its shortcut would.
 */
final class LauncherBridge
{
    private LauncherBridge() { }

    private static final Logger log = LoggerFactory.getLogger(LauncherBridge.class);

    private static File find()
    {
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null)
        {
            File exe = Path.of(localAppData, "Programs", "Microbot Launcher", "Microbot Launcher.exe").toFile();
            if (exe.isFile()) return exe;
        }
        String userHome = System.getProperty("user.home", "");
        File shortcut = Path.of(userHome, "Desktop", "Microbot Launcher.lnk").toFile();
        if (shortcut.isFile()) return shortcut; // "cmd /c start" below can open a .lnk directly via the shell
        return null;
    }

    static boolean available()
    {
        return find() != null;
    }

    /** Starts the launcher as its own independent process (it keeps running after this client exits). Returns false
     *  if it could not be found or started, in which case nothing was launched. */
    static boolean start()
    {
        File target = find();
        if (target == null) return false;
        try
        {
            new ProcessBuilder("cmd", "/c", "start", "\"\"", target.getAbsolutePath())
                .directory(target.getParentFile())
                .start();
            return true;
        }
        catch (Exception e)
        {
            log.warn("Could not start the Microbot Launcher", e);
            return false;
        }
    }
}
