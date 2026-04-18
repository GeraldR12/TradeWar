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

        // 1. Get Shop and Town Context
        Town shopTown = TownyAPI.getInstance().getTown(shop.getLocation());
        if (shopTown == null || !shopTown.hasNation()) return new TaxRates(0.0, 0.0);

        Nation hostNation = shopTown.getNationOrNull();
        Material shopItem = shop.getItem().getType();

        // 2. Get Buyer Information
        Player interactor = user.getBukkitPlayer().orElse(null);
        if (interactor == null) return new TaxRates(0.0, 0.0);

        Resident interactorRes = TownyAPI.getInstance().getResident(interactor);

        // QuickShop: !shop.isBuying() means the shop is SELLING to the player (Import)
        boolean isImporting = !shop.isBuying();

        // 3. Process matching Tariff Rules
        List<TradeDataManager.TariffRule> rules = plugin.getData().tariffRules.get(hostNation.getName());
        if (rules != null) {
            for (TradeDataManager.TariffRule rule : rules) {
                // Validate Expiry
                if (rule.expiryTime() > 0 && System.currentTimeMillis() > rule.expiryTime()) continue;

                // Validate Item
                if (rule.item() != null && rule.item() != shopItem) continue;

                // Validate Direction (Import/Export)
                if (rule.type().equalsIgnoreCase("import") && !isImporting) continue;
                if (rule.type().equalsIgnoreCase("export") && isImporting) continue;

                // Validate Target Town/Nation
                if (interactorRes == null) continue;
                if (rule.targetType().equalsIgnoreCase("nation")) {
                    if (!interactorRes.hasNation() || !interactorRes.getNationOrNull().getName().equalsIgnoreCase(rule.targetName())) continue;
                } else if (rule.targetType().equalsIgnoreCase("town")) {
                    if (!interactorRes.hasTown() || !interactorRes.getTownOrNull().getName().equalsIgnoreCase(rule.targetName())) continue;
                }

                // Cumulative tax calculation
                buyerTaxTotal += shop.getPrice() * (rule.percentage() / 100.0);
            }
        }

        // 4. Deposit Revenue and Notify Player
        if (buyerTaxTotal > 0) {
            try {
                hostNation.getAccount().deposit(buyerTaxTotal, "Tariff Revenue from " + interactor.getName());
                // Explicit notification to the buyer explaining the extra cost
                interactor.sendMessage("§6[TradeWar] §7Applied §e" + String.format("%.2f", buyerTaxTotal) + "G §7in tariffs imposed by §f" + hostNation.getName());
            } catch (Exception ignored) {}
        }

        // Return the final tax rates for QuickShop to process the deduction
        return new TaxRates(buyerTaxTotal, 0.0);
    }
}