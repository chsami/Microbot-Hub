package net.runelite.client.plugins.microbot.mahoganyhomez.v2;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.Optional;

/**
 * Manages the current contract state and persistence
 */
@Slf4j
@Singleton
public class ContractStateManager {

    private final Client client;
    private final ConfigManager configManager;

    @Getter
    @Setter
    @Nullable
    private Home currentHome;

    @Getter
    @Setter
    private int contractTier = 0;

    @Getter
    @Setter
    private Instant lastChanged;

    @Getter
    @Setter
    private int sessionContracts = 0;

    @Getter
    @Setter
    private int sessionPoints = 0;

    @Inject
    public ContractStateManager(Client client, ConfigManager configManager) {
        this.client = client;
        this.configManager = configManager;
    }

    public void setCurrentHome(@Nullable Home home) {
        this.currentHome = home;
        this.lastChanged = Instant.now();

        if (currentHome == null) {
            contractTier = 0;
        }

        updateConfig();
    }

    public void setContractTier(int tier) {
        this.contractTier = tier;
        updateConfig();
    }

    public void incrementSessionStats() {
        sessionContracts++;
        sessionPoints += getPointsForCompletingTask();
    }

    public void resetSessionStats() {
        sessionContracts = 0;
        sessionPoints = 0;
    }

    public boolean hasActiveContract() {
        return currentHome != null;
    }

    public Optional<Home> getCurrentContract() {
        return Optional.ofNullable(currentHome);
    }

    public void loadFromConfig() {
        final String group = getConfigGroupName();
        final String homeName = configManager.getConfiguration(group, MahoganyHomesConfig.HOME_KEY);

        if (homeName == null) {
            return;
        }

        try {
            final Home home = Home.valueOf(homeName.trim().toUpperCase());
            setCurrentHome(home);
        } catch (IllegalArgumentException e) {
            log.warn("Stored unrecognized home: {}", homeName);
            currentHome = null;
            configManager.setConfiguration(group, MahoganyHomesConfig.HOME_KEY, null);
        }

        // Load contract tier if home was loaded successfully
        if (currentHome != null) {
            loadContractTierFromConfig();
        }
    }

    private void loadContractTierFromConfig() {
        final String group = getConfigGroupName();
        final String tierString = configManager.getConfiguration(group, MahoganyHomesConfig.TIER_KEY);

        if (tierString == null) {
            return;
        }

        try {
            contractTier = Integer.parseInt(tierString);
        } catch (IllegalArgumentException e) {
            log.warn("Stored unrecognized contract tier: {}", tierString);
            contractTier = 0;
            configManager.unsetConfiguration(group, MahoganyHomesConfig.TIER_KEY);
        }
    }

    private void updateConfig() {
        final String group = getConfigGroupName();

        if (currentHome == null) {
            configManager.unsetConfiguration(group, MahoganyHomesConfig.HOME_KEY);
            configManager.unsetConfiguration(group, MahoganyHomesConfig.TIER_KEY);
        } else {
            configManager.setConfiguration(group, MahoganyHomesConfig.HOME_KEY, currentHome.getName());
            configManager.setConfiguration(group, MahoganyHomesConfig.TIER_KEY, String.valueOf(contractTier));
        }
    }

    private String getConfigGroupName() {
        return MahoganyHomesConfig.GROUP_NAME + "." + client.getAccountHash();
    }

    private int getPointsForCompletingTask() {
        // Contracts reward 2-5 points depending on tier
        return contractTier + 1;
    }

    public boolean isPluginTimedOut() {
        // This was always returning false in the original implementation
        // Could be implemented to check if no progress has been made for a certain time
        return false;
    }
}
