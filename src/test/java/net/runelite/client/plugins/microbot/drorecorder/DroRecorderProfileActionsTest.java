package net.runelite.client.plugins.microbot.drorecorder;

import net.runelite.client.events.ConfigChanged;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class DroRecorderProfileActionsTest {
    @Test void openOutputFolderInvokesOpenWhenValueChanged() {
        try (var actions = mockStatic(DroRecorderPlugin.class)) {
            var recorder = new DroRecorderPlugin();
            var event = new ConfigChanged();
            event.setGroup(DroRecorderConfig.GROUP);
            event.setKey("openOutputFolder");
            event.setOldValue("previous-profile-uuid");
            event.setNewValue("new-button-click-uuid");
            recorder.onConfigChanged(event);
            actions.verify(DroRecorderPlugin::openOutputFolder);
        }
    }

    @Test void openOutputFolderIgnoredWhenValueNullOrUnchanged() {
        try (var actions = mockStatic(DroRecorderPlugin.class)) {
            var recorder = new DroRecorderPlugin();

            var event1 = new ConfigChanged();
            event1.setGroup(DroRecorderConfig.GROUP);
            event1.setKey("openOutputFolder");
            event1.setOldValue("same-uuid");
            event1.setNewValue("same-uuid");
            recorder.onConfigChanged(event1);

            var event2 = new ConfigChanged();
            event2.setGroup(DroRecorderConfig.GROUP);
            event2.setKey("openOutputFolder");
            event2.setOldValue("previous-uuid");
            event2.setNewValue(null);
            recorder.onConfigChanged(event2);

            actions.verifyNoInteractions();
        }
    }
}
