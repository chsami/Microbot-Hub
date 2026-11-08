package net.runelite.client.plugins.microbot.fourtifysbarrows.util.action;

import java.lang.reflect.Proxy;
import net.runelite.api.widgets.Widget;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MicrobotApiTest
{
    @Test
    public void theFirstActionUsesTheDefaultClick()
    {
        assertEquals(0, MicrobotApi.widgetOperation(widget(11, "Close", "Examine"), "Close"));
    }

    @Test
    public void laterActionsSendTheirOneBasedOperation()
    {
        assertEquals(2, MicrobotApi.widgetOperation(widget(11, "Examine", "Close"), "Close"));
        assertEquals(4, MicrobotApi.widgetOperation(widget(11, "A", "B", "C", "Close"), "Close"));
        assertEquals(5, MicrobotApi.widgetOperation(widget(11, "A", "B", "C", "D", "Close"), "Close"));
    }

    @Test
    public void unaddressableOptionsAreRefusedInsteadOfDefaultClicked()
    {
        assertEquals(-1, MicrobotApi.widgetOperation(widget(11, "Examine"), "Close"));
        assertEquals(-1, MicrobotApi.widgetOperation(widget(-1, "Examine", "Close"), "Close"));
        assertEquals(-1, MicrobotApi.widgetOperation(widget(11, "A", "B", "C", "D", "E", "Close"), "Close"));
    }

    private static Widget widget(int index, String... actions)
    {
        return (Widget) Proxy.newProxyInstance(Widget.class.getClassLoader(), new Class<?>[] {Widget.class},
            (proxy, method, arguments) ->
            {
                switch (method.getName())
                {
                    case "getIndex": return index;
                    case "getActions": return actions;
                    default: throw new AssertionError(method.getName());
                }
            });
    }
}
