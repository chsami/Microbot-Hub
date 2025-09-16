package net.runelite.client.plugins.microbot.Pizza;

import java.util.concurrent.TimeUnit;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

public class PizzaScript extends Script {

    private static final int PIZZA_BASE = 1929;
    private static final int TOMATO = 1933;
    private static final int RAW_PIZZA = 2283;
    private static final int CHEESE = 1982;
    private static final int INCOMPLETE_PIZZA = 2285;
    private static final int DOUGH = 1985;
    private static final int COOKED_PIZZA = 2287;

    private static final int REQUIRED_QUANTITY = 5;

    private static final int WIDGET_GROUP = 270;
    private static final int WIDGET_CHILD = 16;

    public PizzaScript() {}

    @Override
    public boolean run() {
        Microbot.enableAutoRunOn = false;

        this.mainScheduledFuture = this.scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                if (hasEnoughPizza()) {
                    System.out.println("Detectadas " + REQUIRED_QUANTITY + " pizzas cocidas, abriendo banco...");
                    openBankToDepositAndRestart();
                    return;
                }

                if (!isInventoryReady()) {
                    System.out.println("Preparando inventario desde el banco...");
                    manageBankAndWithdrawMaterials();
                }

                if (isInventoryReady()) {
                    createBatchOfPizza();
                }

            } catch (Exception ex) {
                System.out.println("Error: " + ex.getMessage());
            }
        }, 0L, 1000L, TimeUnit.MILLISECONDS);

        return true;
    }

    private boolean hasEnoughPizza() {
        return Rs2Inventory.count(COOKED_PIZZA) >= REQUIRED_QUANTITY;
    }

    private boolean isInventoryReady() {
        return Rs2Inventory.count(PIZZA_BASE) >= REQUIRED_QUANTITY &&
                Rs2Inventory.count(TOMATO) >= REQUIRED_QUANTITY &&
                Rs2Inventory.count(CHEESE) >= REQUIRED_QUANTITY &&
                Rs2Inventory.count(DOUGH) >= REQUIRED_QUANTITY;
    }

    private void manageBankAndWithdrawMaterials() {
        Rs2Bank.openBank();
        Rs2Bank.depositAll();
        this.sleepUntil(Rs2Inventory::isEmpty, 10000);

        Rs2Bank.withdrawX(PIZZA_BASE, REQUIRED_QUANTITY);
        this.sleepUntil(() -> Rs2Inventory.count(PIZZA_BASE) >= REQUIRED_QUANTITY, 5000);

        Rs2Bank.withdrawX(TOMATO, REQUIRED_QUANTITY);
        this.sleepUntil(() -> Rs2Inventory.count(TOMATO) >= REQUIRED_QUANTITY, 5000);

        Rs2Bank.withdrawX(CHEESE, REQUIRED_QUANTITY);
        this.sleepUntil(() -> Rs2Inventory.count(CHEESE) >= REQUIRED_QUANTITY, 5000);

        Rs2Bank.withdrawX(DOUGH, REQUIRED_QUANTITY);
        this.sleepUntil(() -> Rs2Inventory.count(DOUGH) >= REQUIRED_QUANTITY, 5000);

        Rs2Bank.closeBank();
    }

    private void createBatchOfPizza() {
        System.out.println("Creando un lote de " + REQUIRED_QUANTITY + " pizzas...");

        for (int i = 0; i < REQUIRED_QUANTITY; i++) {
            safeCombine(PIZZA_BASE, TOMATO, RAW_PIZZA);
            safeCombine(RAW_PIZZA, CHEESE, INCOMPLETE_PIZZA);
            safeCombine(DOUGH, INCOMPLETE_PIZZA, COOKED_PIZZA);
        }

        openBankToDepositAndRestart();
    }

    private void safeCombine(int item1, int item2, int resultItem) {
        if (!Rs2Inventory.contains(item1) || !Rs2Inventory.contains(item2)) return;

        boolean combined = false;
        int attempts = 0;

        while (!combined && attempts < 3) {
            Rs2Inventory.combine(item1, item2);

            boolean widgetVisible = this.sleepUntil(() -> Rs2Widget.getWidget(WIDGET_GROUP, WIDGET_CHILD) != null, 12000);

            if (widgetVisible) {
                selectPizzaBaseOption();
            } else {
                System.out.println("No se detectó menú, combinando directamente...");
                Rs2Inventory.combine(item1, item2); // fallback
            }

            confirmCombination();

            combined = this.sleepUntil(() -> Rs2Inventory.contains(resultItem), 8000);
            if (!combined) {
                System.out.println("No se detectó el item resultante, reintentando...");
                attempts++;
                this.sleep(500, 1000);
            }
        }
    }

    private void selectPizzaBaseOption() {
        if (Rs2Widget.clickWidget(WIDGET_GROUP, WIDGET_CHILD)) {
            System.out.println("Se seleccionó 'Pizza Base'.");
            this.sleep(1000, 1500);
        } else {
            System.out.println("No se pudo seleccionar 'Pizza Base'.");
        }
    }

    private void confirmCombination() {
        Rs2Keyboard.keyPress(32);
        this.sleep(3000, 3500);
        Rs2Keyboard.keyRelease(32);
        this.sleep(5000, 6000);
    }

    private void openBankToDepositAndRestart() {
        Rs2Bank.openBank();
        Rs2Bank.depositAll(COOKED_PIZZA);
        this.sleepUntil(() -> Rs2Inventory.count(COOKED_PIZZA) == 0, 5000);
        manageBankAndWithdrawMaterials();
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}
