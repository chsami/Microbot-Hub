package net.runelite.client.plugins.microbot.mahoganyhomez.v2;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles parsing of contract-related dialogues and chat messages
 */
@Slf4j
@Singleton
public class ContractDialogueHandler {

    private static final Pattern CONTRACT_PATTERN = Pattern.compile(
        "(Please could you g|G)o see (\\w*)[ ,][\\w\\s,-]*[?.] You can get another job once you have furnished \\w* home\\."
    );

    private static final Pattern REMINDER_PATTERN = Pattern.compile(
        "You're currently on an (\\w*) Contract\\. Go see (\\w*)[ ,][\\w\\s,-]*\\. You can get another job once you have furnished \\w* home\\."
    );

    private static final Pattern CONTRACT_FINISHED = Pattern.compile(
        "You have completed [\\d,]* contracts with a total of [\\d,]* points?\\."
    );

    private static final Pattern CONTRACT_ASSIGNED = Pattern.compile(
        "(\\w*) Contract: Go see [\\w\\s,-]*\\."
    );

    private static final Pattern REQUEST_CONTRACT_TIER = Pattern.compile(
        "Could I have an? (\\w*) contract please\\?"
    );

    private final Client client;
    private final ContractStateManager stateManager;

    @Inject
    public ContractDialogueHandler(Client client, ContractStateManager stateManager) {
        this.client = client;
        this.stateManager = stateManager;
    }

    /**
     * Processes NPC dialogue for contract assignments and reminders
     */
    public void processNpcDialogue() {
        Widget dialog = client.getWidget(ComponentID.DIALOG_NPC_TEXT);
        if (dialog == null) {
            return;
        }

        String npcText = Text.sanitizeMultilineText(dialog.getText());
        processContractAssignmentText(npcText);
    }

    /**
     * Processes player dialogue for contract tier requests
     */
    public void processPlayerDialogue() {
        Widget dialog = client.getWidget(ComponentID.DIALOG_PLAYER_TEXT);
        if (dialog == null) {
            return;
        }

        String playerText = Text.sanitizeMultilineText(dialog.getText());
        Matcher matcher = REQUEST_CONTRACT_TIER.matcher(playerText);

        if (matcher.matches()) {
            String tierText = matcher.group(1).toLowerCase();
            Optional<Integer> tier = parseTierFromText(tierText);
            tier.ifPresent(stateManager::setContractTier);
        }
    }

    /**
     * Processes chat messages for contract assignments and completions
     */
    public void processChatMessage(String message) {
        String cleanMessage = Text.removeTags(message);

        // Check for contract assignment
        Matcher assignmentMatcher = CONTRACT_ASSIGNED.matcher(cleanMessage);
        if (assignmentMatcher.matches()) {
            String tierText = assignmentMatcher.group(1).toLowerCase();
            parseTierFromText(tierText).ifPresent(stateManager::setContractTier);
            return;
        }

        // Check for contract completion
        if (CONTRACT_FINISHED.matcher(cleanMessage).matches()) {
            stateManager.incrementSessionStats();
            stateManager.setCurrentHome(null);
        }
    }

    private void processContractAssignmentText(String npcText) {
        ContractAssignment assignment = parseContractAssignment(npcText);
        if (assignment == null) {
            return;
        }

        // Handle contract tier if provided in reminder
        if (assignment.tier.isPresent()) {
            stateManager.setContractTier(assignment.tier.get());
        }

        // Find and assign the home
        Optional<Home> targetHome = findHomeByName(assignment.homeOwner);
        if (targetHome.isPresent()) {
            Home newHome = targetHome.get();

            // Only update if it's a different home or plugin timed out
            if (stateManager.getCurrentHome() != newHome || stateManager.isPluginTimedOut()) {
                stateManager.setCurrentHome(newHome);
            }
        } else {
            log.warn("Could not find home for owner: {}", assignment.homeOwner);
        }
    }

    private ContractAssignment parseContractAssignment(String npcText) {
        // Try contract assignment pattern
        Matcher startMatcher = CONTRACT_PATTERN.matcher(npcText);
        if (startMatcher.matches()) {
            return new ContractAssignment(startMatcher.group(2), Optional.empty());
        }

        // Try reminder pattern (includes tier information)
        Matcher reminderMatcher = REMINDER_PATTERN.matcher(npcText);
        if (reminderMatcher.matches()) {
            String homeOwner = reminderMatcher.group(2);
            String tierText = reminderMatcher.group(1);
            Optional<Integer> tier = parseTierFromText(tierText);
            return new ContractAssignment(homeOwner, tier);
        }

        return null;
    }

    private Optional<Home> findHomeByName(String ownerName) {
        for (Home home : Home.values()) {
            if (home.getName().equalsIgnoreCase(ownerName)) {
                return Optional.of(home);
            }
        }
        return Optional.empty();
    }

    private Optional<Integer> parseTierFromText(String tierText) {
        switch (tierText.toLowerCase()) {
            case "beginner":
                return Optional.of(1);
            case "novice":
                return Optional.of(2);
            case "adept":
                return Optional.of(3);
            case "expert":
                return Optional.of(4);
            default:
                log.warn("Unknown contract tier: {}", tierText);
                return Optional.empty();
        }
    }

    /**
     * Simple data class for contract assignment information
     */
    private static class ContractAssignment {
        final String homeOwner;
        final Optional<Integer> tier;

        ContractAssignment(String homeOwner, Optional<Integer> tier) {
            this.homeOwner = homeOwner;
            this.tier = tier;
        }
    }
}
