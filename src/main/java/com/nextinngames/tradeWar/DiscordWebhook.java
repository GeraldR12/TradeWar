package com.nextinngames.tradeWar;

import org.bukkit.Bukkit;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class DiscordWebhook {

    public static void sendEmbed(TradeWar plugin, String title, int color, String[][] fields) {
        String webhookUrl = plugin.getWebhookUrl();
        if (webhookUrl == null || webhookUrl.isEmpty() || webhookUrl.equalsIgnoreCase("YOUR_WEBHOOK_URL_HERE")) {
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                URL url = new URL(webhookUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);

                // Build the Embed JSON manually to avoid adding external dependencies
                StringBuilder json = new StringBuilder();
                json.append("{");
                json.append("\"embeds\": [{");
                json.append("\"title\": \"").append(title).append("\",");
                json.append("\"color\": ").append(color).append(",");
                json.append("\"fields\": [");

                for (int i = 0; i < fields.length; i++) {
                    json.append("{");
                    json.append("\"name\": \"").append(fields[i][0]).append("\",");
                    json.append("\"value\": \"").append(fields[i][1]).append("\",");
                    json.append("\"inline\": false");
                    json.append("}");
                    if (i < fields.length - 1) {
                        json.append(",");
                    }
                }

                json.append("]"); // close fields
                json.append("}]"); // close embed
                json.append("}"); // close json

                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = json.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = connection.getResponseCode();
                if (responseCode < 200 || responseCode >= 300) {
                    plugin.getLogger().warning("Discord Webhook responded with error code: " + responseCode);
                }
                connection.disconnect();
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to send Embed to Discord Webhook: " + e.getMessage());
            }
        });
    }
}