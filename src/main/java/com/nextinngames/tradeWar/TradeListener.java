package com.nextinngames.tradeWar;

import com.ghostchu.quickshop.api.event.economy.ShopPurchaseEvent;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TradeListener implements Listener {
    private final TradeWar plugin;
    public TradeListener(TradeWar plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPurchaseBlocker(ShopPurchaseEvent event) {
        Player buyer = event.getPurchaser().getBukkitPlayer().orElse(null);
        if (buyer == null) return;

        Town shopTown = TownyAPI.getInstance().getTown(event.getShop().getLocation());
        if (shopTown == null) return;

        Resident resident = TownyAPI.getInstance().getResident(buyer);
        if (resident == null || !resident.hasTown()) return;

        // 1. Check Nation Embargoes (if nations exist)
        if (shopTown.hasNation() && resident.hasNation()) {
            String hostNation = shopTown.getNationOrNull().getName();
            String buyerNation = resident.getNationOrNull().getName();
            if (plugin.getData().embargoes.getOrDefault(hostNation, new HashSet<>()).contains(buyerNation)) {
                event.setCancelled(true);
                buyer.sendMessage("§c[TradeWar] " + hostNation + " has an active embargo against your nation!");
                return;
            }
        }

        // 2. Check Town Sanctions
        Set<String> townSanctions = plugin.getData().sanctions.getOrDefault(shopTown.getName(), new HashSet<>());
        if (townSanctions.contains(resident.getTownOrNull().getName())) {
            event.setCancelled(true);
            buyer.sendMessage("§c[TradeWar] Your town is sanctioned from trading in " + shopTown.getName() + ".");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPurchaseSuccess(ShopPurchaseEvent event) {
        Player buyer = event.getPurchaser().getBukkitPlayer().orElse(null);
        if (buyer == null) return;

        Town shopTown = TownyAPI.getInstance().getTown(event.getShop().getLocation());
        if (shopTown == null) return;

        Resident resident = TownyAPI.getInstance().getResident(buyer);
        if (resident == null || !resident.hasTown()) return;

        double basePriceTotal = event.getTotal();
        double tariffAmount = 0.0;

        List<TradeDataManager.TariffRule> rules = plugin.getData().tariffRules.get(shopTown.getName());
        if (rules != null) {
            for (TradeDataManager.TariffRule rule : rules) {
                if (rule.expiryTime() > 0 && System.currentTimeMillis() > rule.expiryTime()) continue;
                if (rule.item() != null && rule.item() != event.getShop().getItem().getType()) continue;

                boolean isImport = !event.getShop().isBuying();
                if (rule.type().equalsIgnoreCase("import") && !isImport) continue;
                if (rule.type().equalsIgnoreCase("export") && isImport) continue;

                if (resident.getTownOrNull().getName().equalsIgnoreCase(rule.targetName())) {
                    tariffAmount += basePriceTotal * (rule.percentage() / 100.0);
                }
            }
        }

        // Deposit directly into the Town Bank Account
        if (tariffAmount > 0) {
            try {
                shopTown.getAccount().deposit(tariffAmount, "Tariff Revenue from town " + resident.getTownOrNull().getName());
                buyer.sendMessage("§6[TradeWar] §e" + String.format("%.2f", tariffAmount) + "G §7tariff deposited to §f" + shopTown.getName());
            } catch (Exception ignored) {}
        }
    }
}