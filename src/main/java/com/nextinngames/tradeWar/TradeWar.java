package com.nextinngames.tradeWar;

import com.ghostchu.quickshop.api.QuickShopAPI;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class TradeWar extends JavaPlugin {
    private TradeDataManager dataManager;
    private QuickShopAPI qsApi;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        try {
            this.qsApi = QuickShopAPI.getInstance();
            getLogger().info("Successfully hooked into QuickShop-Hikari API!");
        } catch (Exception e) {
            getLogger().severe("QuickShop-Hikari API not found! Disabling TradeWar.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (Bukkit.getPluginManager().getPlugin("Towny") == null) {
            getLogger().severe("Towny not found! Disabling TradeWar.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.dataManager = new TradeDataManager(this);
        this.dataManager.loadData();

        try {
            qsApi.getShopManager().taxManager().provider(new TariffTaxProvider(this));
            getLogger().info("TradeWar Tariff TaxProvider registered!");
        } catch (Exception e) {
            getLogger().warning("Failed to register TaxProvider. Tariffs might not work.");
        }

        if (getCommand("tw") != null) {
            getCommand("tw").setExecutor(new NationTradeCommand(this));
            // Register Tab Completer
            getCommand("tw").setTabCompleter(new TradeTabCompleter());
        }

        getServer().getPluginManager().registerEvents(new TradeListener(this), this);
        getLogger().info("TradeWar for CartMC is now active!");
    }

    public TradeDataManager getData() { return dataManager; }
    public QuickShopAPI getQsApi() { return qsApi; }
    public String getWebhookUrl() { return getConfig().getString("discord-webhook-url", ""); }
}