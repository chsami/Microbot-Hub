package net.runelite.client.plugins.microbot.autofarmer;
import org.junit.jupiter.api.Test;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InterfaceID;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
class XpLampSkillTest {
 @Test void keepDoesNotTouchClientOrInventory() {
  assertFalse(new FarmingLamp().handle(XpLampSkill.KEEP_LAMP));
  assertFalse(new FarmingLamp().handle(null));
 }
 @Test void everyTrainableSkillHasItsOwnRewardWidget() throws Exception {
  Set<Skill> skills = new HashSet<>(); Set<Integer> widgets = new HashSet<>();
  for (XpLampSkill choice : XpLampSkill.values()) {
   if (!choice.spendsReward()) continue;
   assertTrue(skills.add(choice.skill())); assertTrue(widgets.add(choice.widgetId()));
   assertEquals(InterfaceID.Xpreward.class.getField(choice.skill().name()).getInt(null), choice.widgetId());
  }
  for (Skill skill : Skill.values()) if (skill != Skill.OVERALL) assertTrue(skills.contains(skill), skill.name());
  assertFalse(skills.contains(Skill.OVERALL));
 }
 @Test void defaultKeepsRewardsUntilSkillSelected() {
  assertEquals(XpLampSkill.KEEP_LAMP, new AutoFarmerConfig() {}.xpLampSkill());
  assertFalse(new FarmingLamp().handle(new AutoFarmerConfig() {}.xpLampSkill()));
 }
}

