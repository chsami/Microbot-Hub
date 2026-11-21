package net.runelite.client.plugins.microbot.autofarmer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;

final class ProfileFiles {
    private ProfileFiles() {}

    static Path resolve(String profile, String fileName) {
        String directory = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(profile.getBytes(StandardCharsets.UTF_8));
        return Path.of(System.getProperty("user.home"), ".runelite", "auto-farmer",
                "profiles", directory, fileName);
    }
}
