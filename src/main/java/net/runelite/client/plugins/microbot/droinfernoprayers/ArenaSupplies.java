package net.runelite.client.plugins.microbot.droinfernoprayers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsItem;

/** Quantities only. This stage never asks for prices or values already-owned gear. */
final class ArenaSupplies {
    static final int POUCH = 12791, NOTE = 24587;
    static final int[][] POTIONS = {{6685,6687,6689,6691},{3024,3026,3028,3030},{2444,169,171,173},{12625,12627,12629,12631}};
    static final class Supply {
        final int id, target;
        final String name;
        final List<Match> matches;
        Supply(int id, String name, int target) {
            this(id,name,target,List.of(new Match(id,name,target,false,false)));
        }
        Supply(int id,String name,int target,List<Match> matches){
            if (id <= 0 || target <= 0) throw new IllegalArgumentException("Invalid supply target");
            this.id = id; this.name = name == null ? "Item " + id : name; this.target = target;
            this.matches=List.copyOf(matches);
        }
    }
    /** Only native loader-accepted alternatives; charged/degraded equipment stays free when owned. */
    static final class Match {
        final int id,quantity;final String name;final boolean fuzzy,equipment;
        Match(int id,String name,int quantity,boolean fuzzy,boolean equipment){this.id=id;this.name=name;this.quantity=quantity;this.fuzzy=fuzzy;this.equipment=equipment;}
        boolean accepts(int liveId,String liveName){
            if(equipment&&liveId==id)return true;
            String desired=name==null?"":name.toLowerCase(java.util.Locale.ROOT);
            String live=liveName==null?"":liveName.toLowerCase(java.util.Locale.ROOT);
            if(!equipment)return fuzzy?!desired.isBlank()&&live.contains(desired):liveId==id;
            if(desired.isBlank()||liveName==null)return liveId==id||fuzzy
                &&net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsVariationMapping.map(liveId)
                    ==net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsVariationMapping.map(id);
            boolean byName=fuzzy||InventorySetupsItem.isBarrowsItem(desired);
            return byName?live.contains(desired):liveId==id||live.equals(desired);
        }
    }
    private ArenaSupplies() { }

    static List<InventorySetupsItem> rows(InventorySetup setup) {
        List<InventorySetupsItem> rows = new ArrayList<>();
        if (setup.getInventory() != null) rows.addAll(setup.getInventory());
        if (setup.getEquipment() != null) rows.addAll(setup.getEquipment());
        rows.removeIf(row -> row == null || row.getId() <= 0 || row.getQuantity() <= 0);
        return rows;
    }
    static List<Supply> basket(InventorySetup setup, ArenaSupplyReceipt receipt, ArenaBlowpipe pipe, boolean buyNote) {
        return basket(setup,receipt,pipe,buyNote,java.util.function.IntUnaryOperator.identity());
    }
    static List<Supply> basket(InventorySetup setup, ArenaSupplyReceipt receipt, ArenaBlowpipe pipe, boolean buyNote,java.util.function.IntUnaryOperator canonical) {
        String invalid=ArenaContainerPreparation.validationError(setup);
        if(invalid!=null)throw new IllegalArgumentException(invalid);
        Map<Integer, Supply> result = new LinkedHashMap<>();
        addRows(result,setup.getInventory(),false,canonical);
        addRows(result,setup.getEquipment(),true,canonical);
        if (pipe != null) add(result, pipe.actualId(), "Selected blowpipe", 1);
        addContainer(result, setup.getRune_pouch(), receipt);
        List<InventorySetupsItem> ammo = new ArrayList<>();
        if (setup.getBoltPouch() != null) ammo.addAll(setup.getBoltPouch());
        if (setup.getQuiver() != null) ammo.addAll(setup.getQuiver());
        addContainer(result, ammo, receipt);
        for (int[] doses : POTIONS) {
            int chosen = doses[0];
            for (int i = doses.length - 1; i >= 0; i--) {
                Supply finalRows = result.get(doses[i]);
                if (receipt.owned(doses[i]) > (finalRows == null ? 0 : finalRows.target)) { chosen = doses[i]; break; }
            }
            add(result, chosen, "Pre-sip potion " + chosen, 1);
        }
        if (pipe != null) {
            add(result, ArenaBlowpipe.DRAGON_DART, "Dragon dart", pipe.dartsNeeded());
            add(result, ArenaBlowpipe.SCALES, "Zulrah's scales", pipe.scalesNeeded());
        }
        Supply pouch = result.get(POUCH);
        int missingPouch=pouch==null?0:receipt.deficits(List.of(pouch)).getOrDefault(pouch,0);
        if (pouch != null && missingPouch>0 && buyNote) {
            result.remove(POUCH);
            add(result, NOTE, "Rune pouch note", missingPouch);
        }
        return List.copyOf(result.values());
    }
    private static void addContainer(Map<Integer, Supply> result, List<InventorySetupsItem> rows, ArenaSupplyReceipt receipt) {
        if (rows == null) return;
        Map<Integer, Supply> total = new LinkedHashMap<>();
        for (InventorySetupsItem row : rows)
            if (row != null && row.getId() > 0 && row.getQuantity() > 0) add(total, row.getId(), row.getName(), row.getQuantity());
        for (Supply row : total.values())
            add(result, row.id, row.name, Math.toIntExact(Math.max(0, row.target - receipt.stored.getOrDefault(row.id, 0L))));
    }
    static void add(Map<Integer, Supply> rows, int id, String name, int amount) {
        if (amount <= 0) return;
        Supply prior = rows.get(id);
        List<Match> matches=new ArrayList<>();if(prior!=null)matches.addAll(prior.matches);matches.add(new Match(id,name,amount,false,false));
        rows.put(id, new Supply(id, name, Math.addExact(amount, prior == null ? 0 : prior.target),matches));
    }
    private static void addRows(Map<Integer,Supply> result,List<InventorySetupsItem> rows,boolean equipment,java.util.function.IntUnaryOperator canonical){
        if(rows==null)return;
        for(InventorySetupsItem row:rows)if(row!=null&&row.getId()>0&&row.getQuantity()>0&&!ArenaBlowpipe.isPipe(row.getId())){
            int id=canonical.applyAsInt(row.getId());Supply prior=result.get(id);
            List<Match> matches=new ArrayList<>();if(prior!=null)matches.addAll(prior.matches);
            matches.add(new Match(id,row.getName(),row.getQuantity(),row.isFuzzy(),equipment));
            result.put(id,new Supply(id,row.getName(),Math.addExact(row.getQuantity(),prior==null?0:prior.target),matches));
        }
    }
}
