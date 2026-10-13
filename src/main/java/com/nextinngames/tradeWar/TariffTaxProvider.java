package com.nextinngames.tradeWar;

import com.ghostchu.quickshop.api.obj.QUser;
import com.ghostchu.quickshop.api.shop.Shop;
import com.ghostchu.quickshop.api.shop.tax.TaxProvider;
import com.ghostchu.quickshop.api.shop.tax.TaxRates;
import org.jetbrains.annotations.NotNull;

/**
 * Tariffs are collected by {@link com.nextinngames.tradeWar.policy.PolicyListener}, not through QuickShop's tax.
 * This zero-rate provider only stays registered so a QuickShop config with {@code shop-tax.type: tradewar_tariffs}
 * keeps working (QuickShop fails every purchase if its configured provider is missing).
 */
public class TariffTaxProvider implements TaxProvider {
    public static final String IDENTIFIER = "tradewar_tariffs";

    @Override
    public @NotNull String identifier() { return IDENTIFIER; }

    @Override
    public @NotNull TaxRates calculateTax(@NotNull Shop shop, @NotNull QUser user) {
        return new TaxRates(0.0, 0.0);
    }
}
