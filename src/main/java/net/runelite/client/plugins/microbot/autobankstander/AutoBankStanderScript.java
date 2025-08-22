package net.runelite.client.plugins.microbot.autobankstander;

import java.util.concurrent.TimeUnit;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.autobankstander.processors.BankStandingProcessor;
import net.runelite.client.plugins.microbot.autobankstander.processors.SkillType;
import net.runelite.client.plugins.microbot.autobankstander.skills.magic.MagicMethod;
import net.runelite.client.plugins.microbot.autobankstander.skills.magic.enchanting.EnchantingProcessor;
import net.runelite.client.plugins.microbot.autobankstander.skills.magic.lunars.LunarsProcessor;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.HerbloreProcessor;
import net.runelite.client.plugins.microbot.autobankstander.skills.fletching.FletchingProcessor;
import net.runelite.client.plugins.microbot.autobankstander.skills.fletching.enums.FletchingMode;
import net.runelite.client.plugins.microbot.autobankstander.config.ConfigData;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import lombok.extern.slf4j.Slf4j;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
public class AutoBankStanderScript extends Script {
    private AutoBankStanderState state = AutoBankStanderState.INITIALIZING;
    private ConfigData configData;
    private long stateStartTime = System.currentTimeMillis(); // remember when we started this state for timeout checking
    private BankStandingProcessor processor;
    private AutoBankStanderPlugin plugin;

    public boolean run(ConfigData configData) {
        this.configData = configData; // save the config data so we can use it later
        this.state = AutoBankStanderState.INITIALIZING; // reset state to beginning
        this.stateStartTime = System.currentTimeMillis(); // reset state timer
        this.processor = null; // clear any existing processor
        log.info("Starting Auto Bank Stander script with config: {}", configData);
        
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) {
                    log.info("Super.run() returned false, stopping");
                    return;
                }
                if (!Microbot.isLoggedIn()) {
                    log.info("Not logged in, waiting");
                    return;
                }
                if (Rs2Player.isMoving() || Rs2Player.isAnimating(3000)) {
                    log.info("Player is moving or animating, waiting (extended check for crafting)");
                    return;
                }

                long startTime = System.currentTimeMillis(); // remember when this loop started

                // state timeout protection
                if (System.currentTimeMillis() - stateStartTime > 30000) {
                    log.info("State timeout after 30 seconds, resetting to INITIALIZING");
                    changeState(AutoBankStanderState.INITIALIZING);
                    return;
                }

                switch (state) {
                    case INITIALIZING: handleInitializing(); break; // handle the setup phase
                    case BANKING: handleBanking(); break; // handle banking operations
                    case PROCESSING: handleProcessing(); break; // handle the processing activity
                    case ERROR_RECOVERY: handleErrorRecovery(); break; // handle error situations
                }

                long endTime = System.currentTimeMillis(); // remember when this loop ended
                long totalTime = endTime - startTime; // calculate how long this loop took
                log.info("Total time for loop: {}ms", totalTime);
            } catch (Exception ex) {
                log.error("Error in main script loop: {}", ex.getMessage(), ex);
                changeState(AutoBankStanderState.ERROR_RECOVERY); // switch to error recovery on exception
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private void handleInitializing() {
        log.info("State: INITIALIZING");
        Microbot.status = "Initializing..."; // tell the user we are starting up
        
        // create the appropriate processor based on config data
        processor = createProcessor();
        if (processor == null) {
            log.info("Failed to create processor for skill: {}", configData.getSkill());
            shutdown(); // stop the plugin
            return;
        }
        
        // validate processor configuration
        if (!processor.validate()) {
            log.info("Processor validation failed");
            shutdown(); // stop the plugin
            return;
        }
        
        log.info("Initialization complete - switching to banking");
        changeState(AutoBankStanderState.BANKING); // switch to banking to get our supplies
    }

    private void handleBanking() {
        log.info("State: BANKING");
        Microbot.status = "Banking..."; // tell the user we are handling banking
        
        if (!Rs2Bank.isNearBank(10)) { // if we are too far from any bank
            log.info("Not near bank - walking to bank");
            Rs2Bank.walkToBank(); // walk to the nearest bank
            return;
        }
        
        if (!Rs2Bank.isOpen()) { // if the bank interface isn't open yet
            log.info("Opening bank");
            Rs2Bank.openBank(); // click to open the bank
            boolean opened = sleepUntil(() -> Rs2Bank.isOpen(), 3000); // wait until the bank opens
            if (!opened) {
                log.info("Failed to open bank within timeout");
            }
            return;
        }
        
        // check if we already have all the required items
        if (processor.hasRequiredItems()) {
            log.info("Have all required items - switching to processing");
            Rs2Bank.closeBank(); // close the bank interface
            changeState(AutoBankStanderState.PROCESSING); // switch to processing mode
            return;
        }
        
        // perform banking operations via processor
        boolean bankingSuccess = processor.performBanking();
        if (bankingSuccess) {
            log.info("Banking complete - switching to processing");
            Rs2Bank.closeBank(); // close the bank interface
            changeState(AutoBankStanderState.PROCESSING); // switch to processing mode
        } else {
            log.info("Banking failed - no required items available, shutting down");
            Microbot.status = "No required items available";
            shutdown(); // stop the plugin
        }
    }

    private void handleProcessing() {
        log.info("State: PROCESSING");
        Microbot.status = processor.getStatusMessage(); // tell the user what we are doing
        
        // check if we can continue processing
        if (!processor.canContinueProcessing()) {
            log.info("Cannot continue processing - shutting down");
            shutdown(); // stop the plugin
            return;
        }
        
        // check if processor is actively making items - don't interrupt with banking
        if (processor.isActivelyProcessing()) {
            log.info("Processor is actively making items - waiting");
            return;
        }
        
        // check if we need to go back to banking
        if (!processor.hasRequiredItems()) {
            log.info("Missing required items - going back to banking");
            changeState(AutoBankStanderState.BANKING); // go back to banking to get more items
            return;
        }
        
        // perform the processing activity
        boolean processingSuccess = processor.process();
        if (!processingSuccess) {
            log.info("Processing failed - going to error recovery");
            changeState(AutoBankStanderState.ERROR_RECOVERY); // switch to error recovery
        }
    }

    private void handleErrorRecovery() {
        log.info("State: ERROR_RECOVERY");
        Microbot.status = "Recovering from error..."; // tell the user we are fixing issues
        
        // check for timeout
        if (System.currentTimeMillis() - stateStartTime > 60000) { // if we've been stuck for more than 60 seconds
            log.info("State timeout - resetting to initializing");
            changeState(AutoBankStanderState.INITIALIZING); // go back to the beginning
            return;
        }
        
        // try to recover by going back to banking
        log.info("Attempting recovery - going to banking");
        changeState(AutoBankStanderState.BANKING); // try to recover by going to banking
    }

    private BankStandingProcessor createProcessor() {
        SkillType skill = configData.getSkill();
        log.info("Creating processor for skill: {}", skill);
        switch (skill) {
            case MAGIC:
                return createMagicProcessor();
            case HERBLORE:
                log.info("Creating herblore processor for mode: {}", configData.getHerbloreMode());
                return new HerbloreProcessor(
                    configData.getHerbloreMode(),
                    configData.getCleanHerbMode(),
                    configData.getUnfinishedPotionMode(),
                    configData.getFinishedPotion(),
                    configData.isUseAmuletOfChemistry()
                );
            case FLETCHING:
                log.info("Entering fletching processor creation");
                return createFletchingProcessor();
            default:
                log.info("Skill not yet implemented: {}", skill);
                return null;
        }
    }
    
    private BankStandingProcessor createMagicProcessor() {
        MagicMethod method = configData.getMagicMethod();
        switch (method) {
            case ENCHANTING:
                log.info("Creating enchanting processor for bolt type: {}", configData.getBoltType());
                return new EnchantingProcessor(configData.getBoltType());
            case LUNARS:
                log.info("Creating lunars processor");
                return new LunarsProcessor();
            case ALCHING:
                log.info("Alchemy processor not yet implemented");
                return null;
            case SUPERHEATING:
                log.info("Superheating processor not yet implemented");
                return null;
            default:
                log.info("Unknown magic method: {}", method);
                return null;
        }
    }
    
    private BankStandingProcessor createFletchingProcessor() {
        FletchingMode mode = configData.getFletchingMode();
        log.info("Creating fletching processor for mode: {}", mode);
        log.info("Fletching config details - dart: {}, bolt: {}, arrow: {}, javelin: {}, bow: {}, crossbow: {}, shield: {}", 
            configData.getDartType(), configData.getFletchingBoltType(), configData.getArrowType(), 
            configData.getJavelinType(), configData.getBowType(), configData.getCrossbowType(), configData.getShieldType());
        
        FletchingProcessor processor = new FletchingProcessor(
            mode,
            configData.getDartType(),
            configData.getFletchingBoltType(),
            configData.getArrowType(),
            configData.getJavelinType(),
            configData.getBowType(),
            configData.getCrossbowType(),
            configData.getShieldType()
        );
        
        log.info("Fletching processor created successfully");
        return processor;
    }

    // helper method to change state with timeout reset
    private void changeState(AutoBankStanderState newState) {
        if (newState != state) { // if we are actually changing to a different state
            log.info("State change: {} -> {}", state, newState);
            state = newState; // update our current state
            stateStartTime = System.currentTimeMillis(); // reset our timeout timer for the new state
        }
    }

    public void setPlugin(AutoBankStanderPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void shutdown() {
        if (!isRunning()) {
            log.info("Script already shutdown, ignoring");
            return;
        }
        log.info("Shutting down Auto Bank Stander script");
        super.shutdown(); // clean up the script properly
        
        // notify plugin to update panel state
        if (plugin != null) {
            plugin.updatePanelState();
        }
        
        log.info("Auto Bank Stander script shutdown complete");
    }
}