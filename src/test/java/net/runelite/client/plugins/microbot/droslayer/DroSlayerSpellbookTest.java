package net.runelite.client.plugins.microbot.droslayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spellbook;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class DroSlayerSpellbookTest {
    @Test public void arceuusRequirementNeverResolvesToStandard() throws Exception {
        DroSlayerScript script = new DroSlayerScript();
        setBook(script,3);
        assertEquals(Rs2Spellbook.ARCEUUS,requiredBook(script));
        for (String method : new String[]{"getAltarOptionForSpellbook", "getDialogOptionForSpellbook",
                "getWidgetTextForSpellbook"}) {
            assertEquals("Arceuus",choice(script,method,(Rs2Spellbook) requiredBook(script)));
        }
    }

    @Test public void existingBooksKeepTheirTargetsAndChoices() throws Exception {
        DroSlayerScript script = new DroSlayerScript();
        Rs2Spellbook[] books = {Rs2Spellbook.MODERN,Rs2Spellbook.ANCIENT,Rs2Spellbook.LUNAR};
        String[] altar = {"Standard","Ancient Magicks","Lunar"};
        String[] dialogue = {"Standard","Ancient","Lunar"};
        for (int i=0; i<books.length; i++) {
            setBook(script,i);
            assertEquals(books[i],requiredBook(script));
            assertEquals(altar[i],choice(script,"getAltarOptionForSpellbook",books[i]));
            assertEquals(dialogue[i],choice(script,"getDialogOptionForSpellbook",books[i]));
            assertEquals(altar[i],choice(script,"getWidgetTextForSpellbook",books[i]));
        }
    }

    @Test public void noneMissingAndInvalidRequirementsDoNotSelectStandard() throws Exception {
        DroSlayerScript script = new DroSlayerScript();
        assertNull(requiredBook(script));
        for (int value : new int[]{4,-1,5}) {
            setBook(script,value);
            assertNull(requiredBook(script));
        }
    }

    private static void setBook(DroSlayerScript script,int value) throws Exception {
        InventorySetup setup = mock(InventorySetup.class);
        when(setup.getSpellBook()).thenReturn(value);
        Field field = DroSlayerScript.class.getDeclaredField("currentInventorySetup");
        field.setAccessible(true); field.set(script,setup);
    }

    private static Object requiredBook(DroSlayerScript script) throws Exception {
        Method method = DroSlayerScript.class.getDeclaredMethod("getRequiredSpellbook");
        method.setAccessible(true); return method.invoke(script);
    }

    private static Object choice(DroSlayerScript script,String name,Rs2Spellbook book) throws Exception {
        Method method = DroSlayerScript.class.getDeclaredMethod(name,Rs2Spellbook.class);
        method.setAccessible(true); return method.invoke(script,book);
    }
}
