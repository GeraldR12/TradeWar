package com.nextinngames.tradeWar;

import com.ghostchu.quickshop.api.QuickShopAPI;
import com.nextinngames.tradeWar.bluemap.BlueMapIntegration;
import com.nextinngames.tradeWar.policy.PolicyListener;
import com.nextinngames.tradeWar.policy.StatusScreenListener;
import com.nextinngames.tradeWar.policy.TownyChangeListener;
import com.nextinngames.tradeWar.policy.TownyPolicyCommands;
import com.nextinngames.tradeWar.pl3xmap.Pl3xMapIntegration;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

public final class TradeWar extends JavaPlugin {
    private TradeDataManager dataManager;
    private QuickShopAPI qsApi;
    private BlueMapIntegration blueMapIntegration;
    private Pl3xMapIntegration pl3xMapIntegration;
    private TownyPolicyCommands townyPolicyCommands;

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
            var taxManager = qsApi.getShopManager().taxManager();
            taxManager.provider(new TariffTaxProvider());
            if (taxManager.provider() instanceof TariffTaxProvider) {
                getLogger().warning("QuickShop shop-tax.type is '" + TariffTaxProvider.IDENTIFIER + "', which no longer adds any tax "
                        + "(tariffs are charged by TradeWar directly). Set it to 'basic' if you want QuickShop's own tax back.");
            }
        } catch (Exception e) {
            getLogger().warning("Failed to register the compatibility TaxProvider: " + e.getMessage());
        }

        this.townyPolicyCommands = new TownyPolicyCommands(this);
        this.townyPolicyCommands.register();

        getServer().getPluginManager().registerEvents(new PolicyListener(this), this);
        getServer().getPluginManager().registerEvents(new TownyChangeListener(this), this);
        getServer().getPluginManager().registerEvents(new StatusScreenListener(this), this);

        this.blueMapIntegration = BlueMapIntegration.tryInitialize(this).orElse(null);
        this.pl3xMapIntegration = Pl3xMapIntegration.tryInitialize(this).orElse(null);

        getLogger().info("TradeWar for CartMC is now active!");
    }

    @Override
    public void onDisable() {
        if (townyPolicyCommands != null) {
            townyPolicyCommands.unregister();
        }
        if (blueMapIntegration != null) {
            blueMapIntegration.shutdown();
        }
        if (pl3xMapIntegration != null) {
            pl3xMapIntegration.shutdown();
        }
    }

    /**
     * Re-reads config.yml and data.yml and redraws the map layers. Used by /ta reload tradewar.
     * Map enable/label settings are only applied at startup; the show-* toggles apply right away.
     */
    public void reload() {
        reloadConfig();
        dataManager.reload();
        getBlueMapIntegration().ifPresent(bm -> bm.fullRefresh());
        getPl3xMapIntegration().ifPresent(pm -> pm.fullRefresh());
        getLogger().info("Reloaded config.yml and data.yml.");
    }

    public TradeDataManager getData() { return dataManager; }
    public QuickShopAPI getQsApi() { return qsApi; }
    public String getWebhookUrl() { return getConfig().getString("discord-webhook-url", ""); }
    public Optional<BlueMapIntegration> getBlueMapIntegration() { return Optional.ofNullable(blueMapIntegration); }
    public Optional<Pl3xMapIntegration> getPl3xMapIntegration() { return Optional.ofNullable(pl3xMapIntegration); }
}