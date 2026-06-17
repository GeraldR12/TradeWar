package com.nextinngames.tradeWar;

import com.ghostchu.quickshop.api.obj.QUser;
import com.ghostchu.quickshop.api.shop.Shop;
import com.ghostchu.quickshop.api.shop.tax.TaxProvider;
import com.ghostchu.quickshop.api.shop.tax.TaxRates;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.util.List;

public class TariffTaxProvider implements TaxProvider {
    private final TradeWar plugin;
    public TariffTaxProvider(TradeWar plugin) { this.plugin = plugin; }

    @Override
    public @NotNull String identifier() { return "tradewar_tariffs"; }

    @Override
    public @NotNull TaxRates calculateTax(@NotNull Shop shop, @NotNull QUser user) {
        double buyerTaxTotal = 0.0;
        Town shopTown = TownyAPI.getInstance().getTown(shop.getLocation());
        if (shopTown == null) return new TaxRates(0, 0);

        Material shopItem = shop.getItem().getType();
        Player buyer = user.getBukkitPlayer().orElse(null);
        if (buyer == null) return new TaxRates(0, 0);

        Resident res = TownyAPI.getInstance().getResident(buyer);
        if (res == null || !res.hasTown()) return new TaxRates(0, 0);

        boolean isImport = !shop.isBuying();

        // Load rules indexed by the shop's town name
        List<TradeDataManager.TariffRule> rules = plugin.getData().tariffRules.get(shopTown.getName());
        if (rules != null) {
            for (TradeDataManager.TariffRule rule : rules) {
                if (rule.expiryTime() > 0 && System.currentTimeMillis() > rule.expiryTime()) continue;
                if (rule.item() != null && rule.item() != shopItem) continue;
                if (rule.type().equalsIgnoreCase("import") && !isImport) continue;
                if (rule.type().equalsIgnoreCase("export") && isImport) continue;

                // Check town-to-town mapping
                if (res.getTownOrNull().getName().equalsIgnoreCase(rule.targetName())) {
                    buyerTaxTotal += shop.getPrice() * (rule.percentage() / 100.0);
                }
            }
        }
        return new TaxRates(buyerTaxTotal, 0.0);
    }
}