package net.runelite.client.plugins.microbot.CRONOVISOR;

import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Player;
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
    private final WorldArea GEarea = new WorldArea(3153, 3478, 24, 23, 0);

    public boolean run(CRONOVISORConfig config) {

        Microbot.enableAutoRunOn = false;
        localPlayer = client.getLocalPlayer();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!config.recruit() && !config.sendMessage()) return;
                if (client == null || !Microbot.isLoggedIn()) return;

                if (config.recruit()) {
                    checkAndReturnToArea();

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

    private void sendRandomMessage(CRONOVISORConfig config) {
        List<String> messages = new ArrayList<>();

        // Cargar mensajes según idioma
        if (config.language() == CRONOVISORConfig.LanguageOption.English) {
            if (config.enableMessage1()) messages.add(config.customMessage1_en());
            if (config.enableMessage2()) messages.add(config.customMessage2_en());
            if (config.enableMessage3()) messages.add(config.customMessage3_en());
            if (config.enableMessage4()) messages.add(config.customMessage4_en());
        } else {
            if (config.enableMessage1()) messages.add(config.customMessage1_es());
            if (config.enableMessage2()) messages.add(config.customMessage2_es());
            if (config.enableMessage3()) messages.add(config.customMessage3_es());
            if (config.enableMessage4()) messages.add(config.customMessage4_es());
        }

        if (messages.isEmpty()) return;

        // Elegir mensaje aleatorio
        String message = messages.get(random.nextInt(messages.size()));

        // Variaciones de texto
        message = addTextVariations(message);

        // Emoji aleatorio
        if (random.nextBoolean()) {
            String[] emojis = {"🔥", "⚔️", "💀", "✅", "🚀", "🌍"};
            message += " " + emojis[random.nextInt(emojis.length)];
        }

        // Limitar a 80 caracteres
        if (message.length() > 80) {
            message = message.substring(0, 80);
        }

        // Cambiar canal según configuración
        switch (config.chatType()) {
            case ALL:
                Rs2Keyboard.typeString(""); // abrir chat global
                Rs2Keyboard.enter();
                break;
            case CHANNEL:
                Rs2Keyboard.typeString("/"); // Channel
                break;
            case CLAN:
                Rs2Keyboard.typeString("//"); // friends
                break;
        }

        // Enviar mensaje
        Rs2Keyboard.typeString(message);
        Rs2Keyboard.enter();
        sleep(1000);
    }

    // Variaciones mínimas en letras
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
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private boolean isInArea() {
        return GEarea.contains(localPlayer.getWorldLocation());
    }

    public void checkAndReturnToArea() {
        if (!isInArea()) {
            WorldPoint randomPoint = getRandomPointInArea();
            Rs2Walker.walkTo(randomPoint);
        }
    }

    private WorldPoint getRandomPointInArea() {
        int x = GEarea.getX() + random.nextInt(GEarea.getWidth());
        int y = GEarea.getY() + random.nextInt(GEarea.getHeight());
        return new WorldPoint(x, y, GEarea.getPlane());
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
            if ("Recruit".equals(entry.getOption())) {
                return entry.getType();
            }
        }
        return null;
    }

    @Override
    public void shutdown() {
        if (mainScheduledFuture != null && !mainScheduledFuture.isCancelled()) {
            mainScheduledFuture.cancel(true);
        }
        System.out.println("CRONOVISOR Script shutdown");
    }
}