package net.runelite.client.plugins.microbot.drozulrah;

import com.google.inject.Provides;
import net.runelite.api.Actor;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.events.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.drozulrah.helper.ZulrahRotationHelper;
import net.runelite.client.plugins.microbot.drozulrah.helper.overlays.*;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@PluginDescriptor(
        name=PluginConstants.DRO + "Zulrah",
        version="1.10.17",
        minClientVersion = "2.6.26",
        description="Inventory-setup driven Zulrah trips, rotations, prayer, switches, thralls and regear",
        tags={"microbot","zulrah","dro"},
        authors={"droplugins"},
        iconUrl="https://chsami.github.io/Microbot-Hub/DroZulrahPlugin/assets/icon.png",
        cardUrl="https://chsami.github.io/Microbot-Hub/DroZulrahPlugin/assets/card.png",
        enabledByDefault=PluginConstants.DEFAULT_ENABLED,
        isExternal=PluginConstants.IS_EXTERNAL
)
public class DroZulrahPlugin extends Plugin
{
    @Inject private DroZulrahScript script;
    @Inject private DroZulrahConfig config;
    @Inject private DroZulrahOverlay overlay;
    @Inject private OverlayManager overlayManager;
    @Inject private EventBus eventBus;
    @Inject private ZulrahRotationHelper rotationHelper;
    @Inject private SceneOverlay sceneOverlay;
    @Inject private PhaseOverlay phaseOverlay;
    @Inject private PrayerHelperOverlay prayerOverlay;
    @Inject private PrayerMarkerOverlay prayerMarkerOverlay;
    @Inject private InstanceTimerOverlay instanceTimerOverlay;

    @Provides
    DroZulrahConfig provideConfig(ConfigManager cm)
    {
        return cm.getConfig(DroZulrahConfig.class);
    }

    @Override
    protected void startUp()
    {
        overlayManager.add(overlay);
        // Own the overlays here to avoid a dependency cycle with their phase-data helper.
        overlayManager.add(sceneOverlay);
        overlayManager.add(phaseOverlay);
        overlayManager.add(prayerOverlay);
        overlayManager.add(prayerMarkerOverlay);
        overlayManager.add(instanceTimerOverlay);
        rotationHelper.start(eventBus, instanceTimerOverlay);
        script.run(config);
    }

    @Override
    protected void shutDown()
    {
        script.shutdown();
        overlayManager.remove(overlay);
        rotationHelper.stop(eventBus);
        overlayManager.remove(sceneOverlay);
        overlayManager.remove(phaseOverlay);
        overlayManager.remove(prayerOverlay);
        overlayManager.remove(prayerMarkerOverlay);
        overlayManager.remove(instanceTimerOverlay);
    }

    @Subscribe
    public void onProjectileMoved(ProjectileMoved e)
    {
        script.onProjectileMoved(e);
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged e)
    {
        Actor actor = e.getActor();
        if (actor instanceof NPC && "Zulrah".equalsIgnoreCase(actor.getName()))
        {
            script.onZulrahAnimation(actor.getAnimation());
        }
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned e)
    {
        script.onZulrahDespawned(e.getNpc());
    }

    @Subscribe
    public void onActorDeath(ActorDeath e)
    {
        script.onActorDeath(e.getActor());
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned e)
    {
        GameObject object = e.getGameObject();
        if (object != null && object.getId() == DroZulrahScript.VENOM_CLOUD_OBJECT)
        {
            script.onCloudSpawn(object.getLocalLocation());
        }
    }

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned e)
    {
        GameObject object = e.getGameObject();
        if (object != null && object.getId() == DroZulrahScript.VENOM_CLOUD_OBJECT)
        {
            script.onCloudDespawn(object.getLocalLocation());
        }
    }
}
