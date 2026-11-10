package net.runelite.client.plugins.microbot.droslayer.walker.support;

import java.util.Arrays;
import java.util.Locale;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

/** One observed House Options action per step. Works on every POH floor without stairs. */
public final class HouseExitControls {
    private HouseExitControls() { }
    /** Called on the client thread. */
    public static String next() {
        if(Rs2Widget.isWidgetVisible(InterfaceID.PohOptions.LEAVE_HOUSE))return "Leave";
        if(!Rs2Tab.isCurrentTab(InterfaceTab.SETTINGS))return "Settings";
        if(Rs2Widget.isWidgetVisible(InterfaceID.SettingsSide.HOUSEOPTIONS))return "House options";
        return "Controls";
    }
    public static boolean click(String expected) {
        String current=Microbot.getClientThread().runOnClientThreadOptional(HouseExitControls::next).orElse(null);
        if(expected==null||!expected.equals(current))return false;
        switch(expected) {
            case "Leave":return Rs2Widget.clickWidget(InterfaceID.PohOptions.LEAVE_HOUSE);
            case "Settings":return Rs2Tab.switchTo(InterfaceTab.SETTINGS);
            case "House options":return Rs2Widget.clickWidget(InterfaceID.SettingsSide.HOUSEOPTIONS);
            default:
                Widget controls=Microbot.getClientThread().runOnClientThreadOptional(()->
                    controls(Rs2Widget.getWidget(InterfaceID.SettingsSide.TABS),0)).orElse(null);
                return controls!=null&&Rs2Widget.clickWidget(controls);
        }
    }
    static Widget controls(Widget widget,int depth) {
        if(widget==null||widget.isHidden()||depth>4)return null;
        var bounds=widget.getBounds();var actions=widget.getActions();
        if(bounds!=null&&bounds.width>0&&bounds.height>0&&actions!=null&&Arrays.stream(actions)
            .anyMatch(a->a!=null&&a.replaceAll("<[^>]*>","").toLowerCase(Locale.ROOT).contains("controls")))return widget;
        for(var children:new Widget[][]{widget.getDynamicChildren(),widget.getStaticChildren(),widget.getNestedChildren()})
            if(children!=null)for(var child:children){var found=controls(child,depth+1);if(found!=null)return found;}
        return null;
    }
}
