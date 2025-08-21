package net.runelite.client.plugins.microbot.autobankstander.skills.magic.lunars;

import java.util.ArrayList;
import java.util.List;

import net.runelite.client.plugins.microbot.autobankstander.processors.BankStandingProcessor;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LunarsProcessor implements BankStandingProcessor {
    
    @Override
    public boolean validate() {
        log.info("Lunars processor not yet implemented");
        return false; // not implemented yet
    }
    
    @Override
    public List<String> getBankingRequirements() {
        return new ArrayList<>(); // empty requirements for now
    }
    
    @Override
    public boolean hasRequiredItems() {
        return false; // not implemented yet
    }
    
    @Override
    public boolean performBanking() {
        return false; // not implemented yet
    }
    
    @Override
    public boolean process() {
        return false; // not implemented yet
    }
    
    @Override
    public boolean canContinueProcessing() {
        return false; // not implemented yet
    }
    
    @Override
    public String getStatusMessage() {
        return "Lunars processing (not implemented)";
    }
}