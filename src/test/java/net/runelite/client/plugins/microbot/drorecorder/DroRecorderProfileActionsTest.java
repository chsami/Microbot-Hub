package net.runelite.client.plugins.microbot.drorecorder;

import net.runelite.client.events.ConfigChanged;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class DroRecorderProfileActionsTest {
    @Test void restoredChangedAndRemovedButtonValuesNeverInvokeActions() {
        try(var actions=mockStatic(DroRecorderPlugin.class)) {
            var recorder=new DroRecorderPlugin();
            for(String key:new String[]{"openOutputFolder"})
                for(String value:new String[]{"restored-button-uuid",null}) {
                    var event=new ConfigChanged();
                    event.setGroup(DroRecorderConfig.GROUP);event.setKey(key);
                    event.setOldValue("previous-profile-uuid");event.setNewValue(value);
                    recorder.onConfigChanged(event);
                }
            actions.verifyNoInteractions();
        }
    }
}
