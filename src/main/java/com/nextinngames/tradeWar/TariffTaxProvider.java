package com.nextinngames.tradeWar;

import com.ghostchu.quickshop.api.obj.QUser;
import com.ghostchu.quickshop.api.shop.Shop;
import com.ghostchu.quickshop.api.shop.tax.TaxProvider;
import com.ghostchu.quickshop.api.shop.tax.TaxRates;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.util.List;

public class TariffTaxProvider implements TaxProvider {

    private final TradeWar plugin;

    public TariffTaxProvider(TradeWar plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String identifier() {
        return "tradewar_tariffs";
    }

    @Override
    public @NotNull TaxRates calculateTax(@NotNull Shop shop, @NotNull QUser user) {
        double buyerTaxTotal = 0.0;

        // 1. Find the location of the shop
        Town shopTown = TownyAPI.getInstance().getTown(shop.getLocation());
        if (shopTown == null || !shopTown.hasNation()) return new TaxRates(0, 0);

        Nation hostNation = shopTown.getNationOrNull();
        Material shopItem = shop.getItem().getType();

        // 2. Identify the player buying/selling
        Player interactor = user.getBukkitPlayer().orElse(null);
        if (interactor == null) return new TaxRates(0, 0);

        Resident interactorRes = TownyAPI.getInstance().getResident(interactor);
        boolean isImporting = !shop.isBuying(); // !shop.isBuying() means shop sells to player

        // 3. Search for matching rules in the new data structure
        List<TradeDataManager.TariffRule> rules = plugin.getData().tariffRules.get(hostNation.getName());
        if (rules != null) {
            for (TradeDataManager.TariffRule rule : rules) {
                // Check Expiry
                if (rule.expiryTime() > 0 && System.currentTimeMillis() > rule.expiryTime()) continue;

                // Check Item Type (null means "all")
                if (rule.item() != null && rule.item() != shopItem) continue;

                // Check Trade Direction
                if (rule.type().equalsIgnoreCase("import") && !isImporting) continue;
                if (rule.type().equalsIgnoreCase("export") && isImporting) continue;

                // Check Target Name (Town or Nation)
                if (interactorRes == null) continue;
                boolean match = false;
                if (rule.targetType().equalsIgnoreCase("nation") && interactorRes.hasNation()) {
                    match = interactorRes.getNationOrNull().getName().equalsIgnoreCase(rule.targetName());
                } else if (rule.targetType().equalsIgnoreCase("town") && interactorRes.hasTown()) {
                    match = interactorRes.getTownOrNull().getName().equalsIgnoreCase(rule.targetName());
                }

                if (match) {
                    buyerTaxTotal += shop.getPrice() * (rule.percentage() / 100.0);
                }
            }
        }

        // 4. Finalize the transaction
        if (buyerTaxTotal > 0) {
            try {
                hostNation.getAccount().deposit(buyerTaxTotal, "Tariff Revenue: " + interactor.getName());
                interactor.sendMessage("§6[TradeWar] §e" + String.format("%.2f", buyerTaxTotal) + "G §7tariff applied by §f" + hostNation.getName());
            } catch (Exception ignored) {}
        }

        return new TaxRates(buyerTaxTotal, 0.0);
    }
}