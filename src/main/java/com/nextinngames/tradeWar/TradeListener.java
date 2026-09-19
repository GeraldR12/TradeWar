package com.nextinngames.tradeWar;

import com.ghostchu.quickshop.api.event.economy.ShopPurchaseEvent;
import com.ghostchu.quickshop.api.event.economy.ShopSuccessPurchaseEvent;
import com.ghostchu.quickshop.api.obj.QUser;
import com.ghostchu.quickshop.api.shop.Shop;
import com.ghostchu.quickshop.api.shop.tax.TaxProvider;
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

        if (shopTown.hasNation() && resident.hasNation()) {
            String hostNation = shopTown.getNationOrNull().getName();
            String buyerNation = resident.getNationOrNull().getName();
            if (plugin.getData().embargoes.getOrDefault(hostNation, new HashSet<>()).contains(buyerNation)) {
                event.setCancelled(true);
                buyer.sendMessage("§c[TradeWar] " + hostNation + " has an active embargo against your nation!");
                return;
            }
        }

        Set<String> townSanctions = plugin.getData().sanctions.getOrDefault(shopTown.getName(), new HashSet<>());
        if (townSanctions.contains(resident.getTownOrNull().getName())) {
            event.setCancelled(true);
            buyer.sendMessage("§c[TradeWar] Your town is sanctioned from trading in " + shopTown.getName() + ".");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onImportAffordabilityCheck(ShopPurchaseEvent event) {
        if (event.getShop().isBuying()) return;

        Player buyer = event.getPurchaser().getBukkitPlayer().orElse(null);
        if (buyer == null) return;

        Town shopTown = TownyAPI.getInstance().getTown(event.getShop().getLocation());
        if (shopTown == null) return;

        Resident resident = TownyAPI.getInstance().getResident(buyer);
        if (resident == null || !resident.hasTown()) return;

        double tariffAmount = resolveTariffAmount(shopTown, resident, event.getShop(), event.getPurchaser(), true, event.getTotal());
        if (tariffAmount <= 0) return;

        double quickShopRate = quickShopInteractorRate(event.getShop(), event.getPurchaser());
        if (!TariffCalculator.canAffordImport(resident.getAccount().getHoldingBalance(), event.getTotal(), quickShopRate, tariffAmount)) {
            event.setCancelled(true);
            if (Double.isFinite(quickShopRate)) {
                double required = event.getTotal() + TariffCalculator.quickShopSurcharge(event.getTotal(), quickShopRate) + tariffAmount;
                buyer.sendMessage("§c[TradeWar] §7You need §e" + String.format("%.2f", required)
                        + "G §7to cover the price plus the §e" + String.format("%.2f", tariffAmount) + "G §7import tariff to §f" + shopTown.getName() + "§7.");
            } else {
                buyer.sendMessage("§c[TradeWar] §7This trade could not be safely validated for tariff purposes. Try again.");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPurchaseSuccess(ShopSuccessPurchaseEvent event) {
        Player buyer = event.getPurchaser().getBukkitPlayer().orElse(null);
        if (buyer == null) return;

        Town shopTown = TownyAPI.getInstance().getTown(event.getShop().getLocation());
        if (shopTown == null) return;

        Resident resident = TownyAPI.getInstance().getResident(buyer);
        if (resident == null || !resident.hasTown()) return;

        boolean isImport = !event.getShop().isBuying();
        double tariffAmount = resolveTariffAmount(shopTown, resident, event.getShop(), event.getPurchaser(), isImport, event.getBalanceWithoutTax());
        if (tariffAmount <= 0) return;

        try {
            String memo = (isImport ? "Import" : "Export") + " tariff on trade with town " + resident.getTownOrNull().getName();
            if (resident.getAccount().payTo(tariffAmount, shopTown.getAccount(), memo)) {
                buyer.sendMessage("§6[TradeWar] §e" + String.format("%.2f", tariffAmount) + "G §7tariff paid to §f" + shopTown.getName());
            } else {
                buyer.sendMessage("§c[TradeWar] §7Tariff of §e" + String.format("%.2f", tariffAmount) + "G §7to §f" + shopTown.getName() + " §7could not be collected (insufficient funds).");
                plugin.getLogger().warning("Failed to collect tariff of " + tariffAmount + " from " + resident.getName() + " for town " + shopTown.getName());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to collect tariff revenue for town " + shopTown.getName() + ": " + e.getMessage());
        }
    }

    private double resolveTariffAmount(Town shopTown, Resident resident, Shop shop, QUser purchaser, boolean isImport, double rawTotal) {
        double effectiveBase = isImport ? rawTotal : TariffCalculator.applyQuickShopSellerDeduction(rawTotal, quickShopInteractorRate(shop, purchaser));
        List<TradeDataManager.TariffRule> rules = plugin.getData().tariffRules.get(shopTown.getName());
        return TariffCalculator.calculateTariff(rules, resident.getTownOrNull().getName(), shop.getItem().getType(), isImport, effectiveBase, System.currentTimeMillis());
    }

    private double quickShopInteractorRate(Shop shop, QUser purchaser) {
        try {
            TaxProvider provider = plugin.getQsApi().getShopManager().taxManager().provider();
            if (provider == null) {
                plugin.getLogger().warning("QuickShop has no active tax provider; treating this trade's tariff as undetermined.");
                return Double.NaN;
            }
            double rate = provider.calculateTax(shop, purchaser).interactorRate();
            if (!Double.isFinite(rate)) {
                plugin.getLogger().warning("QuickShop's active tax provider returned a non-finite rate; treating this trade's tariff as undetermined.");
                return Double.NaN;
            }
            return rate;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to read QuickShop's active tax rate, treating this trade's tariff as undetermined: " + e.getMessage());
            return Double.NaN;
        }
    }
}