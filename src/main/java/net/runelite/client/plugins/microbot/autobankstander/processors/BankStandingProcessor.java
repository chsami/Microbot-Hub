package net.runelite.client.plugins.microbot.autobankstander.processors;

import java.util.List;

public interface BankStandingProcessor {
    
    /**
     * validate if the processor can run with current configuration
     * @return true if valid, false otherwise
     */
    boolean validate();
    
    /**
     * get list of required items that need to be in bank or inventory
     * @return list of item names/ids needed
     */
    List<String> getBankingRequirements();
    
    /**
     * check if we have all required items to start processing
     * @return true if ready to process, false if need banking
     */
    boolean hasRequiredItems();
    
    /**
     * perform the banking operations to get required items
     * @return true if banking completed successfully
     */
    boolean performBanking();
    
    /**
     * perform the main processing activity
     * @return true if processing completed successfully
     */
    boolean process();
    
    /**
     * check if there are more items available to process
     * @return true if can continue processing
     */
    boolean canContinueProcessing();
    
    /**
     * get current status message for display
     * @return status string
     */
    String getStatusMessage();
    
    /**
     * check if processor is actively making items (should not interrupt with banking)
     * @return true if currently processing and should wait
     */
    default boolean isActivelyProcessing() {
        return false; // default implementation for processors that don't need this
    }
}