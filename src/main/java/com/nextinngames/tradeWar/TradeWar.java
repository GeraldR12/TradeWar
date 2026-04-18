package com.nextinngames.tradeWar;

import com.ghostchu.quickshop.api.QuickShopAPI;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class TradeWar extends JavaPlugin {

    private TradeDataManager dataManager;
    private QuickShopAPI qsApi;

    @Override
    public void onEnable() {
        // 1. Hook into QuickShop-Hikari
        try {
            this.qsApi = QuickShopAPI.getInstance();
            getLogger().info("Successfully hooked into QuickShop-Hikari API!");
        } catch (Exception e) {
            getLogger().severe("QuickShop-Hikari API not found! Disabling TradeWar.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 2. Hook into Towny
        if (Bukkit.getPluginManager().getPlugin("Towny") == null) {
            getLogger().severe("Towny not found! Disabling TradeWar.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 3. Initialize Data and Load from file
        this.dataManager = new TradeDataManager(this);
        this.dataManager.loadData();

        // 4. Register Custom Tax Provider for Tariffs
        try {
            qsApi.getShopManager().taxManager().provider(new TariffTaxProvider(this));
            getLogger().info("TradeWar Tariff TaxProvider registered!");
        } catch (Exception e) {
            getLogger().warning("Failed to register TaxProvider. Tariffs might not work.");
        }

        // 5. Register the /tw command
        if (getCommand("tw") != null) {
            getCommand("tw").setExecutor(new NationTradeCommand(this));
        }

        // 6. Register Blocker Listener (Embargoes/Sanctions)
        getServer().getPluginManager().registerEvents(new TradeListener(this), this);

        getLogger().info("TradeWar for EarthPol is now active!");
    }

    public TradeDataManager getData() {
        return dataManager;
    }

    public QuickShopAPI getQsApi() {
        return qsApi;
    }
}