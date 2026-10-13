package com.nextinngames.tradeWar.policy;

import com.ghostchu.quickshop.api.event.economy.ShopPurchaseEvent;
import com.ghostchu.quickshop.api.event.economy.ShopSuccessPurchaseEvent;
import com.ghostchu.quickshop.api.event.management.ShopClickEvent;
import com.ghostchu.quickshop.api.shop.Shop;
import com.nextinngames.tradeWar.TradeWar;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.economy.Account;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Locale;
import java.util.UUID;

/**
 * Enforces embargoes and sanctions, and charges tariffs, on QuickShop trades.
 * <p>
 * The policies that apply come from the shop's location (its town's nation, or the town itself if it has no nation)
 * and from the trader's own town in the same way. Tariffs only apply when a player buys from a selling shop: the buyer
 * pays the tariff on top of the shop price, straight into the bank of the nation or town that set it.
 */
public class PolicyListener implements Listener {
    private final TradeWar plugin;

    public PolicyListener(TradeWar plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPurchaseRestrictions(ShopPurchaseEvent event) {
        Player trader = event.getPurchaser().getBukkitPlayer().orElse(null);
        if (trader == null) return;
        Resident resident = TownyAPI.getInstance().getResident(trader);
        if (resident == null) return;

        Town shopTown = TownyAPI.getInstance().getTown(event.getShop().getLocation());
        Nation shopNation = shopTown == null ? null : shopTown.getNationOrNull();
        PolicyHolder shopHolder = PolicyHolder.governing(shopTown);

        Town traderTown = resident.getTownOrNull();
        Nation traderNation = resident.getNationOrNull();
        PolicyHolder traderHolder = PolicyHolder.governing(traderTown);

        // What the shop's side has imposed on this trader.
        PolicySet shopSide = plugin.getData().get(shopHolder);
        if (shopSide != null) {
            if (PolicyRules.isEmbargoed(shopSide.embargoes, nameOf(traderNation))) {
                block(event, trader, "The " + shopHolder.label() + " has an embargo against your nation.");
                return;
            }
            if (PolicyRules.isSanctioned(shopSide.sanctions, nameOf(traderTown), trader.getUniqueId())) {
                block(event, trader, "The " + shopHolder.label() + " has sanctioned you or your town.");
                return;
            }
        }

        // What the trader's own side has imposed on this shop.
        if (traderHolder == null || traderHolder.equals(shopHolder)) return;
        PolicySet traderSide = plugin.getData().get(traderHolder);
        if (traderSide == null) return;
        if (PolicyRules.isEmbargoed(traderSide.embargoes, nameOf(shopNation))) {
            block(event, trader, "Your " + traderHolder.scope().label().toLowerCase(Locale.ROOT)
                    + " has an embargo against the Nation of " + shopNation.getName() + ".");
            return;
        }
        UUID ownerId = event.getShop().getOwner().getUniqueIdOptional().orElse(null);
        if (PolicyRules.isSanctioned(traderSide.sanctions, nameOf(shopTown), ownerId)) {
            block(event, trader, "Your " + traderHolder.scope().label().toLowerCase(Locale.ROOT)
                    + " has sanctioned this shop's town or owner.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPurchaseTariffCheck(ShopPurchaseEvent event) {
        Player buyer = event.getPurchaser().getBukkitPlayer().orElse(null);
        if (buyer == null) return;
        Quote quote = quote(event.getShop(), buyer, event.getAmount());
        if (quote == null) return;

        if (!quote.resident().getAccount().canPayFromHoldings(event.getTotal() + quote.amount())) {
            block(event, buyer, "You cannot afford this purchase plus the " + formatMoney(quote.amount()) + " tariff ("
                    + PolicyRules.formatPercent(quote.percentage()) + "%) charged by the " + quote.holder().label() + ".");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPurchaseSuccess(ShopSuccessPurchaseEvent event) {
        Player buyer = event.getPurchaser().getBukkitPlayer().orElse(null);
        if (buyer == null) return;
        Quote quote = quote(event.getShop(), buyer, event.getAmount());
        if (quote == null) return;

        String reason = "Tariff from " + buyer.getName();
        if (quote.resident().getAccount().payTo(quote.amount(), quote.bank(), reason)) {
            buyer.sendMessage("§6[TradeWar] §e" + formatMoney(quote.amount()) + " §7tariff ("
                    + PolicyRules.formatPercent(quote.percentage()) + "%) paid to the §f" + quote.holder().label());
        } else {
            plugin.getLogger().warning("Could not collect " + formatMoney(quote.amount()) + " tariff from "
                    + buyer.getName() + " for the " + quote.holder().label() + ".");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShopClick(ShopClickEvent event) {
        Player clicker = event.user().getBukkitPlayer().orElse(null);
        Shop shop = event.shop().orElse(null);
        if (clicker == null || shop == null) return;
        Quote quote = quote(shop, clicker, 1);
        if (quote == null) return;

        clicker.sendMessage("§e[TradeWar] The " + quote.holder().label() + " charges you a §c"
                + PolicyRules.formatPercent(quote.percentage()) + "% §etariff here. Price per item with tariff: §f"
                + formatMoney(shop.getPrice() + quote.amount()));
    }

    /**
     * The tariff this buyer owes for {@code amount} items from {@code shop}, or null if none applies.
     */
    private Quote quote(Shop shop, Player buyer, int amount) {
        if (!shop.isSelling()) return null;
        Town shopTown = TownyAPI.getInstance().getTown(shop.getLocation());
        PolicyHolder shopHolder = PolicyHolder.governing(shopTown);
        PolicySet policies = plugin.getData().get(shopHolder);
        if (policies == null || policies.tariffs.isEmpty()) return null;

        Resident resident = TownyAPI.getInstance().getResident(buyer);
        if (resident == null) return null;
        Town buyerTown = resident.getTownOrNull();
        if (shopHolder.equals(PolicyHolder.governing(buyerTown))) return null;

        double percentage = PolicyRules.applicablePercentage(
                policies.tariffs, nameOf(buyerTown), nameOf(resident.getNationOrNull()), System.currentTimeMillis());
        if (percentage <= 0.0) return null;

        double tariff = shop.getPrice() * amount * percentage / 100.0;
        if (tariff <= 0.0) return null;
        Account bank = shopHolder.scope() == PolicyScope.NATION ? shopTown.getNationOrNull().getAccount() : shopTown.getAccount();
        return new Quote(shopHolder, bank, resident, percentage, tariff);
    }

    private static void block(ShopPurchaseEvent event, Player player, String reason) {
        event.setCancelled(true);
        player.sendMessage("§c[TradeWar] " + reason);
    }

    private static String nameOf(Town town) {
        return town == null ? null : town.getName();
    }

    private static String nameOf(Nation nation) {
        return nation == null ? null : nation.getName();
    }

    private static String formatMoney(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private record Quote(PolicyHolder holder, Account bank, Resident resident, double percentage, double amount) {
    }
}
