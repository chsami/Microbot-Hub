package net.runelite.client.plugins.microbot.droefficientwalker;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class PreparedWalkBankPolicyTest {
    @Test public void preparedJourneyDisablesBankSuppliesAndNormalPolicyCanBeRestored() throws Exception {
        try (HousePortalRecoveryTest.Fixture fixture = new HousePortalRecoveryTest.Fixture()) {
            EfficientWalker walker = fixture.walker;
            Field field = WalkerTestReflection.declaredField(EfficientWalker.class, "config");
            field.setAccessible(true);
            EfficientWalkerConfig config = (EfficientWalkerConfig) field.get(walker);
            when(config.useItemsFromBank()).thenReturn(true);
            Method policy = WalkerTestReflection.declaredMethod(EfficientWalker.class, "setBankSuppliesAllowed",boolean.class);
            policy.setAccessible(true);
            Method effective = WalkerTestReflection.declaredMethod(EfficientWalker.class, "bankSuppliesAllowed");
            effective.setAccessible(true);
            Method bridge = WalkerTestReflection.declaredMethod(EfficientWalker.class, "invokeConfig",Object.class,Method.class,Object[].class);
            bridge.setAccessible(true);
            Method bankSetting = EfficientWalkerConfig.class.getMethod("useItemsFromBank");
            try (var bank = mockStatic(Rs2Bank.class)) {
                bank.when(Rs2Bank::isOpen).thenReturn(true);
                policy.invoke(walker,true);
                assertEquals(true,effective.invoke(walker));
                policy.invoke(walker,false);
                assertEquals(false,effective.invoke(walker));
                assertEquals(false,bridge.invoke(walker,new Object(),bankSetting,null));
                policy.invoke(walker,true);
                assertEquals(true,effective.invoke(walker));
                assertEquals(true,bridge.invoke(walker,new Object(),bankSetting,null));
                assertTrue(config.useItemsFromBank());
            }
        }
    }
}
