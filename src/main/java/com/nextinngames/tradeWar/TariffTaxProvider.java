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
        double buyerTax = 0.0;

        // 1. Get Shop Info
        Town shopTown = TownyAPI.getInstance().getTown(shop.getLocation());
        if (shopTown == null || !shopTown.hasNation()) return new TaxRates(0, 0);

        Nation hostNation = shopTown.getNationOrNull();
        // Corrected: Use getItem().getType() instead of getItemStack()
        Material shopItem = shop.getItem().getType();

        // 2. Get Buyer/Seller Info
        Player interactor = user.getBukkitPlayer().orElse(null);
        if (interactor == null) return new TaxRates(0, 0);

        Resident interactorRes = TownyAPI.getInstance().getResident(interactor);
        boolean isBuyingFromShop = !shop.isBuying(); // Is the player giving money to get items?

        // 3. Check all rules for this nation
        // Corrected: 'plugin' is now recognized as a class field
        List<TradeDataManager.TariffRule> rules = plugin.getData().tariffRules.get(hostNation.getName());
        if (rules != null) {
            for (TradeDataManager.TariffRule rule : rules) {
                // Check Expiry
                if (rule.expiryTime() > 0 && System.currentTimeMillis() > rule.expiryTime()) continue;

                // Check Item
                if (rule.item() != null && rule.item() != shopItem) continue;

                // Check Import/Export
                if (rule.type().equals("import") && !isBuyingFromShop) continue;
                if (rule.type().equals("export") && isBuyingFromShop) continue;

                // Check Target (Town or Nation)
                if (interactorRes == null) continue;
                if (rule.targetType().equals("nation")) {
                    if (!interactorRes.hasNation() || !interactorRes.getNationOrNull().getName().equalsIgnoreCase(rule.targetName())) continue;
                } else if (rule.targetType().equals("town")) {
                    if (!interactorRes.hasTown() || !interactorRes.getTownOrNull().getName().equalsIgnoreCase(rule.targetName())) continue;
                }

                // If all checks pass, calculate the tax
                buyerTax += shop.getPrice() * (rule.percentage() / 100.0);
            }
        }

        // 4. Deposit and Return
        if (buyerTax > 0) {
            try {
                hostNation.getAccount().deposit(buyerTax, "Tariff Revenue");
            } catch (Exception ignored) {
                // Handle economy errors if necessary
            }
        }

        return new TaxRates(buyerTax, 0.0);
    }
}