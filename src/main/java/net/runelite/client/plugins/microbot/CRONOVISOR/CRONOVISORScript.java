package net.runelite.client.plugins.microbot.CRONOVISOR;

import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Player;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.Rs2PlayerModel;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import org.apache.commons.lang3.RandomUtils;

import javax.inject.Inject;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class CRONOVISORScript extends Script {

    @Inject
    private Client client;
    private ScheduledFuture<?> mainScheduledFuture;
    private Set<String> recruitedPlayers = new HashSet<>();
    private final Random random = new Random();

    @Inject
    public CRONOVISORScript(Client client) {
        this.client = client;
        this.recruitedPlayers = new HashSet<>();
    }

    private Player localPlayer;

    // Áreas de trabajo
    private final WorldArea GEarea = new WorldArea(3153, 3478, 24, 23, 0);
    private final WorldArea FEROXarea = new WorldArea(3125, 3618, 30, 34, 0);
    private final WorldArea LUMBYarea = new WorldArea(3200, 3200, 30, 30, 0);
    private final WorldArea WILDYarea = new WorldArea(3284, 3847, 10, 10, 0);
    private final WorldArea Artioarea = new WorldArea(3219, 3785, 10, 10, 0);
    private final WorldArea Line30Wilderarea = new WorldArea(3283, 3760, 10, 10, 0);
    private final WorldArea Faladorarea = new WorldArea(3045, 3377, 10, 10, 0);
    private final WorldArea Rimmingtonarea = new WorldArea(2957, 3224, 10, 10, 0);

    // IDs de items
    private static final int CLAN_CLOAK = 25712;
    private static final int CLAN_VEXILLUM = 25721;

    public boolean run(CRONOVISORConfig config) {

        Microbot.enableAutoRunOn = false;
        localPlayer = client.getLocalPlayer();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!config.recruit() && !config.sendMessage()) return;
                if (client == null || !Microbot.isLoggedIn()) return;

                if (config.recruit()) {
                    handleBankItems(); // Manejo de banco y equipamiento
                    checkAndReturnToArea(config);

                    List<Rs2PlayerModel> localPlayers = Rs2Player.getPlayers(p -> true)
                            .collect(Collectors.toList());

                    for (Rs2PlayerModel player : localPlayers) {
                        if (player == null || player.getName() == null || recruitedPlayers.contains(player.getName()))
                            continue;

                        Rs2Camera.turnTo(player);
                        sleep(1000);
                        moveMouseToPlayer(player);

                        MenuAction menuAction = findRecruitAction();
                        if (menuAction != null) {
                            NewMenuEntry recruitMenuEntry = new NewMenuEntry(
                                    "Recruit",
                                    player.getName(),
                                    player.getId(),
                                    MenuAction.of(menuAction.getId()),
                                    0,
                                    0,
                                    false
                            );

                            Microbot.targetMenu = recruitMenuEntry;
                            Microbot.doInvoke(recruitMenuEntry, player.getCanvasTilePoly().getBounds());

                            inviteToClanWithRetry(player.getName());
                            recruitedPlayers.add(player.getName());
                        }

                        if (config.sendMessage() && random.nextInt(5) == 0) {
                            sleep(5000);
                            sendRandomMessage(config);
                        }

                        break; // solo un jugador por ciclo
                    }
                }

                if (config.sendMessage() && !config.recruit() && random.nextInt(5) == 0) {
                    sleep(5000);
                    sendRandomMessage(config);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);

        return true;
    }

    private void handleBankItems() {
        // Verificar si los items están equipados
        boolean cloakEquipped = Rs2Equipment.isWearing(CLAN_CLOAK)
                && Rs2Equipment.isWearing(EquipmentInventorySlot.CAPE);

        boolean vexillumEquipped = Rs2Equipment.isWearing(CLAN_VEXILLUM)
                && Rs2Equipment.isWearing(EquipmentInventorySlot.SHIELD);

        //boolean cloakEquipped = Rs2Equipment.isEquipped(CLAN_CLOAK, EquipmentInventorySlot.CAPE);
        //boolean vexillumEquipped = Rs2Equipment.isEquipped(CLAN_VEXILLUM, EquipmentInventorySlot.SHIELD);

        // Verificar si los items están en el inventario
        boolean cloakInInventory = Rs2Inventory.contains(CLAN_CLOAK);
        boolean vexillumInInventory = Rs2Inventory.contains(CLAN_VEXILLUM);

        System.out.println("DEBUG: cloakEquipped=" + cloakEquipped + ", cloakInInventory=" + cloakInInventory
                + ", vexillumEquipped=" + vexillumEquipped + ", vexillumInInventory=" + vexillumInInventory);

        // Si ambos items están equipados o en inventario, no abrir el banco
        if ((cloakEquipped || cloakInInventory) && (vexillumEquipped || vexillumInInventory)) {
            // Equipar items si están en inventario
            if (!cloakEquipped && cloakInInventory) {
                Rs2Inventory.interact(CLAN_CLOAK, "Wear");
                sleep(500);
            }
            if (!vexillumEquipped && vexillumInInventory) {
                Rs2Inventory.interact(CLAN_VEXILLUM, "Wear");
                sleep(500);
            }
            return; // nada más que hacer
        }

        // Abrir banco solo si falta algún item **y está disponible en el banco**
        boolean needBank = false;

        if ((!cloakEquipped && !cloakInInventory) && Rs2Bank.hasItem(CLAN_CLOAK)) needBank = true;
        if ((!vexillumEquipped && !vexillumInInventory) && Rs2Bank.hasItem(CLAN_VEXILLUM)) needBank = true;

        if (!needBank) {
            System.out.println("DEBUG: No items missing or not in bank. Skipping bank.");
            return;
        }

        // Abrir banco
        if (!Rs2Bank.isOpen()) {
            Rs2Bank.openBank();
            sleepUntil(Rs2Bank::isOpen, 3000);
        }

        // Retirar y equipar items que falten
        if (!cloakEquipped && Rs2Bank.hasItem(CLAN_CLOAK)) {
            Rs2Bank.withdrawAndEquip(CLAN_CLOAK);
            sleepUntil(() ->
                            Rs2Equipment.isWearing(CLAN_CLOAK)
                                    && Rs2Equipment.isWearing(EquipmentInventorySlot.CAPE),
                    3000);
        }

        if (!vexillumEquipped && Rs2Bank.hasItem(CLAN_VEXILLUM)) {
            Rs2Bank.withdrawAndEquip(CLAN_VEXILLUM);
            sleepUntil(() ->
                            Rs2Equipment.isWearing(CLAN_VEXILLUM)
                                    && Rs2Equipment.isWearing(EquipmentInventorySlot.SHIELD),
                    3000);
        }


        // Cerrar banco
        if (Rs2Bank.isOpen()) {
            Rs2Bank.closeBank();
            sleep(500);
        }
    }


    // Resto del código permanece igual
    private void sendRandomMessage(CRONOVISORConfig config) {
        List<String> messages = new ArrayList<>();

        if (config.language() == CRONOVISORConfig.LanguageOption.English
                || config.language() == CRONOVISORConfig.LanguageOption.Both) {
            if (config.enableMessage1()) messages.add(config.customMessage1_en());
            if (config.enableMessage2()) messages.add(config.customMessage2_en());
            if (config.enableMessage3()) messages.add(config.customMessage3_en());
            if (config.enableMessage4()) messages.add(config.customMessage4_en());
            if (config.enableMessage5() && !config.customMessage5_en().isEmpty()) messages.add(config.customMessage5_en());
        }

        if (config.language() == CRONOVISORConfig.LanguageOption.Spanish
                || config.language() == CRONOVISORConfig.LanguageOption.Both) {
            if (config.enableMessage1()) messages.add(config.customMessage1_es());
            if (config.enableMessage2()) messages.add(config.customMessage2_es());
            if (config.enableMessage3()) messages.add(config.customMessage3_es());
            if (config.enableMessage4()) messages.add(config.customMessage4_es());
            if (config.enableMessage5() && !config.customMessage5_es().isEmpty()) messages.add(config.customMessage5_es());
        }

        if (messages.isEmpty()) return;

        String message = messages.get(random.nextInt(messages.size()));
        message = addTextVariations(message);

        if (random.nextBoolean()) {
            String[] emojis = {"🔥", "⚔️", "💀", "✅", "🚀", "🌍"};
            message += " " + emojis[random.nextInt(emojis.length)];
        }

        if (message.length() > 80) message = message.substring(0, 80);

        // Cambiar canal según configuración
        switch (config.chatType()) {
            case ALL:
                Rs2Keyboard.typeString("");
                Rs2Keyboard.enter();
                break;
            case CHANNEL:
                Rs2Keyboard.typeString("/");
                break;
            case CLAN:
                Rs2Keyboard.typeString("//");
                break;
        }

        Rs2Keyboard.typeString(message);
        Rs2Keyboard.enter();
        sleep(1000);
    }

    private String addTextVariations(String input) {
        Map<Character, String[]> variations = new HashMap<>();
        variations.put('a', new String[]{"a", "â", "à", "á", "ä"});
        variations.put('e', new String[]{"e", "ê", "è", "é", "ë"});
        variations.put('i', new String[]{"i", "î", "ì", "í", "ï"});
        variations.put('o', new String[]{"o", "ô", "ò", "ó", "ö"});
        variations.put('u', new String[]{"u", "û", "ù", "ú", "ü"});
        variations.put('c', new String[]{"c", "ç"});
        variations.put('n', new String[]{"n", "ñ"});

        StringBuilder sb = new StringBuilder();
        for (char ch : input.toCharArray()) {
            if (variations.containsKey(Character.toLowerCase(ch)) && random.nextInt(100) < 15) {
                String[] opts = variations.get(Character.toLowerCase(ch));
                sb.append(opts[random.nextInt(opts.length)]);
            } else sb.append(ch);
        }
        return sb.toString();
    }

    public void checkAndReturnToArea(CRONOVISORConfig config) {
        switch (config.location()) {
            case Ferox:
                if (!FEROXarea.contains(localPlayer.getWorldLocation())) Rs2Walker.walkTo(getRandomPoint(FEROXarea));
                break;
            case Lumbridge:
                if (!LUMBYarea.contains(localPlayer.getWorldLocation())) Rs2Walker.walkTo(getRandomPoint(LUMBYarea));
                break;
            case Callisto:
                if (!WILDYarea.contains(localPlayer.getWorldLocation())) Rs2Walker.walkTo(getRandomPoint(WILDYarea));
                break;
            case Artio:
                if (!Artioarea.contains(localPlayer.getWorldLocation())) Rs2Walker.walkTo(getRandomPoint(Artioarea));
                break;
            case Line30Wilder:
                if (!Line30Wilderarea.contains(localPlayer.getWorldLocation())) Rs2Walker.walkTo(getRandomPoint(Line30Wilderarea));
                break;
            case Falador:
                if (!Faladorarea.contains(localPlayer.getWorldLocation())) Rs2Walker.walkTo(getRandomPoint(Faladorarea));
                break;
            case Rimmington:
                if (!Rimmingtonarea.contains(localPlayer.getWorldLocation())) Rs2Walker.walkTo(getRandomPoint(Rimmingtonarea));
                break;
            case GrandExchange:
            default:
                if (!GEarea.contains(localPlayer.getWorldLocation())) Rs2Walker.walkTo(getRandomPoint(GEarea));
                break;
        }
    }

    private WorldPoint getRandomPoint(WorldArea area) {
        int x = area.getX() + random.nextInt(area.getWidth());
        int y = area.getY() + random.nextInt(area.getHeight());
        return new WorldPoint(x, y, area.getPlane());
    }

    public void inviteToClanWithRetry(String playerName) {
        for (int attempt = 0; attempt < 3; attempt++) {
            sleep(600);
            if (inviteToClan(playerName)) break;
            sleep(1000, 1500);
        }
    }

    public boolean inviteToClan(String playerName) {
        CompletableFuture<Boolean> resultFuture = new CompletableFuture<>();
        Microbot.getClientThread().invoke(() -> {
            String inviteText = "Invite " + playerName + " to join the clan.";
            boolean clicked = Rs2Widget.clickWidget(inviteText);
            resultFuture.complete(clicked);
        });

        try {
            return resultFuture.get();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private void moveMouseToPlayer(Player player) {
        if (player == null) return;

        Shape bounds = player.getCanvasTilePoly();
        if (bounds != null) {
            Rectangle rect = bounds.getBounds();
            net.runelite.api.Point p = new net.runelite.api.Point(
                    rect.x + RandomUtils.nextInt(0, rect.width),
                    rect.y + RandomUtils.nextInt(0, rect.height)
            );
            Microbot.getMouse().move(p.getX(), p.getY());
            sleep(500);
        }
    }

    private MenuAction findRecruitAction() {
        MenuEntry[] entries = Microbot.getClient().getMenuEntries();
        for (MenuEntry entry : entries) {
            if ("Recruit".equals(entry.getOption())) return entry.getType();
        }
        return null;
    }

    @Override
    public void shutdown() {
        if (mainScheduledFuture != null && !mainScheduledFuture.isCancelled()) mainScheduledFuture.cancel(true);
        System.out.println("CRONOVISOR Script shutdown");
    }
}