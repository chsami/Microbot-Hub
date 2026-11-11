package net.runelite.client.plugins.microbot.droefficientwalker.support;
import java.util.Optional;
import java.util.concurrent.Callable;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class HouseExitControlsTest {
    @Test void observesEachSettingsStepAndNeverClicksAStaleNextControl()throws Exception{
        var thread=mock(ClientThread.class);
        when(thread.runOnClientThreadOptional(any())).thenAnswer(i->Optional.ofNullable(((Callable<?>)i.getArgument(0)).call()));
        try(var micro=mockStatic(Microbot.class);var tabs=mockStatic(Rs2Tab.class);var widgets=mockStatic(Rs2Widget.class)){
            micro.when(Microbot::getClientThread).thenReturn(thread);
            assertEquals("Settings",HouseExitControls.next());
            assertFalse(HouseExitControls.click("Leave"));
            widgets.verify(()->Rs2Widget.clickWidget(InterfaceID.PohOptions.LEAVE_HOUSE),never());
            tabs.when(()->Rs2Tab.isCurrentTab(InterfaceTab.SETTINGS)).thenReturn(true);
            widgets.when(()->Rs2Widget.isWidgetVisible(InterfaceID.SettingsSide.HOUSEOPTIONS)).thenReturn(true);
            assertEquals("House options",HouseExitControls.next());
            HouseExitControls.click("House options");
            widgets.verify(()->Rs2Widget.clickWidget(InterfaceID.SettingsSide.HOUSEOPTIONS));
            widgets.when(()->Rs2Widget.isWidgetVisible(InterfaceID.PohOptions.LEAVE_HOUSE)).thenReturn(true);
            assertEquals("Leave",HouseExitControls.next());HouseExitControls.click("Leave");
            widgets.verify(()->Rs2Widget.clickWidget(InterfaceID.PohOptions.LEAVE_HOUSE));
        }
    }
}
