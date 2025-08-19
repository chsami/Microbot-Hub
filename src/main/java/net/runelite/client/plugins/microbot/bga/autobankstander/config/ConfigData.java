package net.runelite.client.plugins.microbot.autobankstander.config;

import com.google.gson.Gson;
import net.runelite.client.plugins.microbot.autobankstander.processors.SkillType;
import net.runelite.client.plugins.microbot.autobankstander.skills.magic.MagicMethod;
import net.runelite.client.plugins.microbot.autobankstander.skills.magic.enchanting.BoltType;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.CleanHerbMode;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.HerblorePotion;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.Mode;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.UnfinishedPotionMode;

public class ConfigData {
    
    // General settings
    private SkillType skill = SkillType.MAGIC;
    
    // Magic settings
    private MagicMethod magicMethod = MagicMethod.ENCHANTING;
    private BoltType boltType = BoltType.SAPPHIRE;
    // TODO: Add other magic method settings (lunar spells, alching, superheating)
    
    // Herblore settings
    private Mode herbloreMode = Mode.CLEAN_HERBS;
    private CleanHerbMode cleanHerbMode = CleanHerbMode.ANY_AND_ALL;
    private UnfinishedPotionMode unfinishedPotionMode = UnfinishedPotionMode.ANY_AND_ALL;
    private HerblorePotion finishedPotion = HerblorePotion.ATTACK;
    private boolean useAmuletOfChemistry = false;
    
    // Default constructor
    public ConfigData() {}
    
    // Copy constructor
    public ConfigData(ConfigData other) {
        this.skill = other.skill;
        this.magicMethod = other.magicMethod;
        this.boltType = other.boltType;
        this.herbloreMode = other.herbloreMode;
        this.cleanHerbMode = other.cleanHerbMode;
        this.unfinishedPotionMode = other.unfinishedPotionMode;
        this.finishedPotion = other.finishedPotion;
        this.useAmuletOfChemistry = other.useAmuletOfChemistry;
    }
    
    // Getters and Setters
    public SkillType getSkill() { return skill; }
    public void setSkill(SkillType skill) { this.skill = skill; }
    
    public MagicMethod getMagicMethod() { return magicMethod; }
    public void setMagicMethod(MagicMethod magicMethod) { this.magicMethod = magicMethod; }
    
    public BoltType getBoltType() { return boltType; }
    public void setBoltType(BoltType boltType) { this.boltType = boltType; }
    
    public Mode getHerbloreMode() { return herbloreMode; }
    public void setHerbloreMode(Mode herbloreMode) { this.herbloreMode = herbloreMode; }
    
    public CleanHerbMode getCleanHerbMode() { return cleanHerbMode; }
    public void setCleanHerbMode(CleanHerbMode cleanHerbMode) { this.cleanHerbMode = cleanHerbMode; }
    
    public UnfinishedPotionMode getUnfinishedPotionMode() { return unfinishedPotionMode; }
    public void setUnfinishedPotionMode(UnfinishedPotionMode unfinishedPotionMode) { this.unfinishedPotionMode = unfinishedPotionMode; }
    
    public HerblorePotion getFinishedPotion() { return finishedPotion; }
    public void setFinishedPotion(HerblorePotion finishedPotion) { this.finishedPotion = finishedPotion; }
    
    public boolean isUseAmuletOfChemistry() { return useAmuletOfChemistry; }
    public void setUseAmuletOfChemistry(boolean useAmuletOfChemistry) { this.useAmuletOfChemistry = useAmuletOfChemistry; }
    
    // JSON serialization
    public String toJson() {
        return new Gson().toJson(this);
    }
    
    public static ConfigData fromJson(String json) {
        try {
            return new Gson().fromJson(json, ConfigData.class);
        } catch (Exception e) {
            return new ConfigData(); // return default config if parsing fails
        }
    }
    
    // Validation
    public boolean isValid() {
        if (skill == null) return false;
        
        switch (skill) {
            case MAGIC:
                return magicMethod != null && validateMagicConfig();
            case HERBLORE:
                return herbloreMode != null && validateHerbloreConfig();
            default:
                return false;
        }
    }
    
    private boolean validateMagicConfig() {
        switch (magicMethod) {
            case ENCHANTING:
                return boltType != null;
            case LUNARS:
            case ALCHING:
            case SUPERHEATING:
                return true; // TODO: Add validation for other magic methods
            default:
                return false;
        }
    }
    
    private boolean validateHerbloreConfig() {
        switch (herbloreMode) {
            case CLEAN_HERBS:
                return cleanHerbMode != null;
            case UNFINISHED_POTIONS:
                return unfinishedPotionMode != null;
            case FINISHED_POTIONS:
                return finishedPotion != null;
            default:
                return false;
        }
    }
    
    @Override
    public String toString() {
        return String.format("ConfigData{skill=%s, method=%s}", 
            skill, skill == SkillType.MAGIC ? magicMethod : herbloreMode);
    }
}