package net.runelite.client.plugins.microbot.autofarmer;
import java.lang.reflect.*;
import java.util.*;
import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.DefaultMouseMotionNature;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MouseProfileSettingsTest {
 @Test void clientProfileLeavesClientProvidersAndTimingUntouched(){
  assertEquals(MouseMotionMode.CLIENT_PROFILE,new AutoFarmerConfig(){}.mouseMotionMode());
  var n=new DefaultMouseMotionNature();var speed=n.getSpeedManager();var deviation=n.getDeviationProvider();
  int reaction=n.getReactionTimeBaseMs(),variation=n.getReactionTimeVariationMs(),steps=n.getMinSteps();
  new MouseFeel().apply(n,MouseMotionMode.CLIENT_PROFILE);
  assertSame(speed,n.getSpeedManager());assertSame(deviation,n.getDeviationProvider());
  assertEquals(reaction,n.getReactionTimeBaseMs());assertEquals(variation,n.getReactionTimeVariationMs());assertEquals(steps,n.getMinSteps());
 }
 @Test void legacyReleaseKeepsNewerUserTimingAndRestoresOwnedProviders(){
  var n=new DefaultMouseMotionNature();var speed=n.getSpeedManager();var deviation=n.getDeviationProvider();
  var feel=new MouseFeel();feel.apply(n,MouseMotionMode.LEGACY_SMOOTHING);n.setReactionTimeBaseMs(999);feel.apply(n,MouseMotionMode.CLIENT_PROFILE);
  assertEquals(999,n.getReactionTimeBaseMs());assertSame(speed,n.getSpeedManager());assertSame(deviation,n.getDeviationProvider());
  assertDoesNotThrow(()->feel.apply(null,MouseMotionMode.CLIENT_PROFILE));
 }
 @Test void allSettingsHaveValidSectionsAndUniqueRows()throws Exception{
  Set<String> sections=new HashSet<>(),keys=new HashSet<>(),rows=new HashSet<>();
  for(Field f:AutoFarmerConfig.class.getFields())if(f.isAnnotationPresent(ConfigSection.class))assertTrue(sections.add((String)f.get(null)));
  for(Method m:AutoFarmerConfig.class.getMethods())if(m.isAnnotationPresent(ConfigItem.class)){
   ConfigItem c=m.getAnnotation(ConfigItem.class);assertTrue(keys.add(c.keyName()),c.keyName());assertTrue(sections.contains(c.section()),c.keyName());assertTrue(rows.add(c.section()+":"+c.position()),c.keyName());
  }
  assertTrue(AutoFarmerConfig.class.getField("advancedSection").getAnnotation(ConfigSection.class).closedByDefault());
  assertTrue(AutoFarmerConfig.class.getField("inventorySection").getAnnotation(ConfigSection.class).closedByDefault());
 }
}
