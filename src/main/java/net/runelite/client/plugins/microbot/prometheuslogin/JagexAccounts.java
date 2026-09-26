package net.runelite.client.plugins.microbot.prometheuslogin;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the display names of Jagex accounts the Microbot Launcher has already signed in, from the launcher's own
 * {@code ~/.microbot/accounts.json}. This class never reads the session id, account id or user hash from that file
 * into a field, only the display name - nothing here can be used to authenticate as the account, and nothing here
 * is written anywhere. This plugin cannot sign a new Jagex account in itself; see {@link LauncherBridge}.
 */
final class JagexAccounts
{
    private JagexAccounts() { }

    /** Deliberately holds only displayName: Gson silently drops the sessionId/accountId/userHash fields present in
     *  the launcher's file, so they never exist as Java values here. */
    private static final class Entry
    {
        @SerializedName("displayName") String displayName;
    }

    static List<String> displayNames()
    {
        List<String> names = new ArrayList<>();
        try
        {
            Path file = Path.of(System.getProperty("user.home", ""), ".microbot", "accounts.json");
            if (!Files.isRegularFile(file)) return names;
            String json = Files.readString(file, StandardCharsets.UTF_8);
            Entry[] entries = new Gson().fromJson(json, Entry[].class);
            if (entries != null)
            {
                for (Entry e : entries)
                {
                    if (e.displayName != null && !e.displayName.isBlank()) names.add(e.displayName);
                }
            }
        }
        catch (Exception ignored) { /* launcher file missing, unreadable, or its format changed: show nothing rather than guess */ }
        return names;
    }
}
