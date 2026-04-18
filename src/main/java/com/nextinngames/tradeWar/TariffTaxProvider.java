package com.nextinngames.tradeWar;

import com.ghostchu.quickshop.api.obj.QUser;
import com.ghostchu.quickshop.api.shop.Shop;
import com.ghostchu.quickshop.api.shop.tax.TaxProvider;
import com.ghostchu.quickshop.api.shop.tax.TaxRates;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Town;
import org.jetbrains.annotations.NotNull;

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
        double basePrice = shop.getPrice();
        double buyerTax = 0.0;

        Town shopTown = TownyAPI.getInstance().getTown(shop.getLocation());

        if (shopTown != null && shopTown.hasNation()) {
            Nation hostNation = shopTown.getNationOrNull();
            String hostNationName = hostNation.getName();

            if (plugin.getData().tariffs.containsKey(hostNationName)) {
                double rate = plugin.getData().tariffs.get(hostNationName);
                buyerTax = basePrice * (rate / 100.0);

                if (buyerTax > 0) {
                    try {
                        hostNation.getAccount().deposit(buyerTax, "TradeWar Tariff");
                    } catch (Exception ignored) {}
                }
            }
        }
        return new TaxRates(buyerTax, 0.0);
    }
}