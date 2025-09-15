package net.runelite.client.plugins.microbot.LT.SummerPie;

import java.util.concurrent.TimeUnit;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

public class SummerPiesScript extends Script {

    // IDs de items
    private static final int PIE_SHELL = 2315;
    private static final int STRAWBERRY = 5504;
    private static final int WATERMELON = 5982;
    private static final int COOKING_APPLE = 1955;
    private static final int STRAWBERRY_PIE = 7212;
    private static final int WATERMELON_PIE = 7214;
    private static final int SUMMER_PIE = 7218;

    private static final int REQUIRED_QUANTITY = 7;

    public SummerPiesScript() {
    }

    @Override
    public boolean run() {
        Microbot.enableAutoRunOn = false;

        this.mainScheduledFuture = this.scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                if (hasEnoughSummerPies()) {
                    System.out.println("Detectadas " + REQUIRED_QUANTITY + " tartas de verano, abriendo banco...");
                    openBankToDepositAndRestart();
                    return;
                }

                if (!isInventoryReady()) {
                    System.out.println("Preparando inventario desde el banco...");
                    manageBankAndWithdrawMaterials();
                }

                if (isInventoryReady()) {
                    createBatchOfSummerPies();
                }

            } catch (Exception ex) {
                System.out.println("Error: " + ex.getMessage());
            }

        }, 0L, 1000L, TimeUnit.MILLISECONDS);

        return true;
    }

    // Verifica si hay suficientes Summer Pies cocidas
    private boolean hasEnoughSummerPies() {
        return Rs2Inventory.count(SUMMER_PIE) >= REQUIRED_QUANTITY;
    }

    // Verifica si el inventario tiene todos los materiales necesarios
    private boolean isInventoryReady() {
        return Rs2Inventory.count(PIE_SHELL) >= REQUIRED_QUANTITY &&
                Rs2Inventory.count(STRAWBERRY) >= REQUIRED_QUANTITY &&
                Rs2Inventory.count(WATERMELON) >= REQUIRED_QUANTITY &&
                Rs2Inventory.count(COOKING_APPLE) >= REQUIRED_QUANTITY;
    }

    // Maneja el banco y retira materiales
    private void manageBankAndWithdrawMaterials() {
        Rs2Bank.openBank();
        Rs2Bank.depositAll();
        this.sleepUntil(Rs2Inventory::isEmpty, 10000);

        Rs2Bank.withdrawX(PIE_SHELL, REQUIRED_QUANTITY);
        this.sleepUntil(() -> Rs2Inventory.count(PIE_SHELL) >= REQUIRED_QUANTITY, 5000);

        Rs2Bank.withdrawX(STRAWBERRY, REQUIRED_QUANTITY);
        this.sleepUntil(() -> Rs2Inventory.count(STRAWBERRY) >= REQUIRED_QUANTITY, 5000);

        Rs2Bank.withdrawX(WATERMELON, REQUIRED_QUANTITY);
        this.sleepUntil(() -> Rs2Inventory.count(WATERMELON) >= REQUIRED_QUANTITY, 5000);

        Rs2Bank.withdrawX(COOKING_APPLE, REQUIRED_QUANTITY);
        this.sleepUntil(() -> Rs2Inventory.count(COOKING_APPLE) >= REQUIRED_QUANTITY, 5000);

        Rs2Bank.closeBank();
    }

    // Crea un lote de Summer Pies
    private void createBatchOfSummerPies() {
        System.out.println("Creando un lote de " + REQUIRED_QUANTITY + " tartas de verano...");

        for (int i = 0; i < REQUIRED_QUANTITY; i++) {

            if (Rs2Inventory.contains(PIE_SHELL) && Rs2Inventory.contains(STRAWBERRY)) {
                combineTwoItems(PIE_SHELL, STRAWBERRY);
                this.sleepUntil(() -> Rs2Inventory.contains(STRAWBERRY_PIE), 8000);
            }

            if (Rs2Inventory.contains(STRAWBERRY_PIE) && Rs2Inventory.contains(WATERMELON)) {
                combineTwoItems(STRAWBERRY_PIE, WATERMELON);
                this.sleepUntil(() -> Rs2Inventory.contains(WATERMELON_PIE), 8000);
            }

            if (Rs2Inventory.contains(WATERMELON_PIE) && Rs2Inventory.contains(COOKING_APPLE)) {
                combineTwoItems(WATERMELON_PIE, COOKING_APPLE);
                this.sleepUntil(() -> Rs2Inventory.contains(SUMMER_PIE), 8000);
            }
        }

        openBankToDepositAndRestart();
    }

    // Combina dos items usando widgets
    private void combineTwoItems(int item1, int item2) {
        if (!Rs2Inventory.contains(item1) || !Rs2Inventory.contains(item2)) return;

        Rs2Inventory.combine(item1, item2);
        this.sleepUntil(() -> Rs2Widget.isWidgetVisible(17694734), 8000);

        confirmCombination();
    }

    private void confirmCombination() {
        Rs2Keyboard.keyPress(32);
        this.sleep(3000, 3500);
        Rs2Keyboard.keyRelease(32);
        this.sleep(5000, 6000);
    }

    private void openBankToDepositAndRestart() {
        Rs2Bank.openBank();
        Rs2Bank.depositAll(SUMMER_PIE);
        this.sleepUntil(() -> Rs2Inventory.count(SUMMER_PIE) == 0, 5000);
        manageBankAndWithdrawMaterials();
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}