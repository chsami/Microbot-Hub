package net.runelite.client.plugins.microbot.accountbuilder.profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Loads and saves {@link AccountProfile} JSON files from
 * {@code ~/.runelite/microbot/accountbuilder/<accountName>.json}.
 *
 * On first access a randomized profile is generated and persisted so the
 * same account always uses the same behavioral parameters.
 */
@Slf4j
public class ProfileManager {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static File profileDir() {
        File dir = new File(
                System.getProperty("user.home"),
                ".runelite/microbot/accountbuilder"
        );
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private static File profileFile(String accountName) {
        return new File(profileDir(), accountName + ".json");
    }

    /**
     * Loads the profile for {@code accountName} from disk.
     * If no file exists, creates a randomized profile, saves it, and returns it.
     */
    public static AccountProfile loadOrCreate(String accountName) {
        if (accountName == null || accountName.isEmpty()) {
            accountName = "default";
        }

        File file = profileFile(accountName);
        if (file.exists()) {
            try {
                AccountProfile profile = MAPPER.readValue(file, AccountProfile.class);
                log.info("Loaded account profile for '{}'", accountName);
                return profile;
            } catch (IOException e) {
                log.warn("Failed to load profile for '{}', creating new one: {}", accountName, e.getMessage());
            }
        }

        AccountProfile profile = createRandomProfile(accountName);
        save(profile);
        log.info("Created new account profile for '{}'", accountName);
        return profile;
    }

    /** Saves the profile to disk. */
    public static void save(AccountProfile profile) {
        File file = profileFile(profile.getAccountName());
        try {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(file, profile);
        } catch (IOException e) {
            log.error("Failed to save profile for '{}': {}", profile.getAccountName(), e.getMessage());
        }
    }

    private static AccountProfile createRandomProfile(String accountName) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        AccountProfile p = new AccountProfile();
        p.setAccountName(accountName);
        // 600–1100 ms tick delay
        p.setTickDelayMs(rng.nextLong(600, 1101));
        // 0.85–1.20 reaction variance
        p.setReactionVariance(0.85 + rng.nextDouble() * 0.35);
        // 0.01–0.04 idle chance per tick
        p.setIdleChance(0.01 + rng.nextDouble() * 0.03);
        // 1500–4000 ms max idle duration
        p.setIdleMaxMs(rng.nextLong(1500, 4001));
        // 15–40 minutes break interval
        p.setBreakIntervalMs(rng.nextLong(15 * 60_000L, 40 * 60_000L + 1));
        // 30–120 seconds break duration
        p.setBreakDurationMs(rng.nextLong(30_000L, 120_001L));
        return p;
    }
}
