package net.runelite.client.plugins.microbot.banksorter;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.ClientTick;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.awt.event.MouseEvent;

@PluginDescriptor(
        name = PluginDescriptor.Default + "Bank Tab Sorter",
        description = "Sorts the current bank tab  using an advanced heuristic algorithm",
        tags = {"bank", "sort", "microbot", "tab"},
        authors = {"unknown"},
        version = BankTabSorterPlugin.version,
        minClientVersion = "2.0.7",
        iconUrl = "https://chsami.github.io/Microbot-Hub/BanksShopperPlugin/assets/icon.png",
        cardUrl = "https://chsami.github.io/Microbot-Hub/BanksShopperPlugin/assets/card.png",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class BankTabSorterPlugin extends Plugin {
    final static String version = "1.0.1";

    @Inject
    private Client client;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private BankTabSorterOverlay sortButtonOverlay;

    @Inject
    private MouseManager mouseManager;

    @Inject
    private BankTabSorterScript bankTabSorterScript;
    private Rectangle sortButtonBounds = new Rectangle();
    @Getter
    private boolean isHovering = false;
    private final MouseAdapter mouseAdapter = new MouseAdapter() {
        @Override
        public MouseEvent mousePressed(MouseEvent e) {
            if (sortButtonBounds.contains(e.getPoint())) {
                startSorting();
                e.consume();
            }
            return e;
        }

        @Override
        public MouseEvent mouseMoved(MouseEvent e) {
            isHovering = sortButtonBounds.contains(e.getPoint());
            return e;
        }
    };

    @Override
    protected void startUp() {
        overlayManager.add(sortButtonOverlay);
        mouseManager.registerMouseListener(mouseAdapter);
    }

    @Override
    protected void shutDown() {
        bankTabSorterScript.shutdown();
        overlayManager.remove(sortButtonOverlay);
        mouseManager.unregisterMouseListener(mouseAdapter);
    }

    public void setSortButtonBounds(Rectangle bounds) {
        this.sortButtonBounds = bounds;
    }

    public void startSorting() {
        Microbot.getClientThread().runOnSeperateThread(() -> {
            if (Rs2Bank.isOpen()) {
                bankTabSorterScript.run();
            } else {
                Microbot.log("Bank is not open!");
            }
            return false;
        });
    }

    @Subscribe
    public void onClientTick(ClientTick event) {
        // Ensure the overlay is only shown when bank is open
        if (Rs2Bank.isOpen()) {
            if (!overlayManager.anyMatch(o -> o == sortButtonOverlay)) {
                overlayManager.add(sortButtonOverlay);
            }
        } else {
            if (overlayManager.anyMatch(o -> o == sortButtonOverlay)) {
                overlayManager.remove(sortButtonOverlay);
            }
        }
    }
}
