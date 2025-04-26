package dev.stormy.client.commands;

import dev.stormy.client.module.modules.gamemode.BedWars;
import net.weavemc.loader.api.command.Command;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.Arrays;
import java.util.List;
import java.util.Collections;

public class HypixelApiCommand extends Command {
    
    public HypixelApiCommand() {
        super("hypixelapi", "hapi");
    }
    
    @Override
    public void handle(String[] args) {
        if (args.length == 0) {
            sendMessage(EnumChatFormatting.RED + "Usage: /hypixelapi <set|clear|status> [key]");
            sendMessage(EnumChatFormatting.GRAY + "Example: /hypixelapi set YOUR_API_KEY_HERE");
            return;
        }
        
        String action = args[0].toLowerCase();
        
        switch (action) {
            case "set":
                if (args.length < 2) {
                    sendMessage(EnumChatFormatting.RED + "Please provide your API key!");
                    return;
                }
                
                String apiKey = args[1];
                // Validate key format (simple check)
                if (apiKey.length() < 20) {
                    sendMessage(EnumChatFormatting.RED + "The provided key seems too short to be valid.");
                    return;
                }
                
                // Verify the key with the Hypixel API
                sendMessage(EnumChatFormatting.YELLOW + "Validating API key...");
                validateApiKey(apiKey);
                break;
                
            case "clear":
                BedWars.setApiKey("");
                sendMessage(EnumChatFormatting.GREEN + "API key cleared successfully.");
                break;
                
            case "status":
                String currentKey = BedWars.getApiKey();
                if (currentKey.isEmpty()) {
                    sendMessage(EnumChatFormatting.RED + "No API key is currently set.");
                    sendMessage(EnumChatFormatting.GRAY + "Set one with /hypixelapi set YOUR_API_KEY");
                } else {
                    String maskedKey = maskApiKey(currentKey);
                    sendMessage(EnumChatFormatting.GREEN + "API key is currently set: " + 
                               EnumChatFormatting.GRAY + maskedKey);
                }
                break;
                
            default:
                sendMessage(EnumChatFormatting.RED + "Unknown action: " + action);
                sendMessage(EnumChatFormatting.GRAY + "Valid actions: set, clear, status");
        }
    }
    
    private void validateApiKey(String apiKey) {
        CompletableFuture.runAsync(() -> {
            try {
                // Use a different endpoint since the key endpoint is deprecated
                // Using the player endpoint with a limit of 1 to minimize data transfer
                URL url = new URL("https://api.hypixel.net/player?uuid=00000000-0000-0000-0000-000000000000&key=" + apiKey);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                
                int responseCode = connection.getResponseCode();
                if (responseCode == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    
                    if (response.toString().contains("\"success\":true")) {
                        BedWars.setApiKey(apiKey);
                        sendMessageLater(EnumChatFormatting.GREEN + "API key validated and saved successfully!");
                    } else {
                        sendMessageLater(EnumChatFormatting.RED + "Invalid API key. Please check and try again.");
                    }
                } else {
                    sendMessageLater(EnumChatFormatting.RED + "Failed to validate API key. HTTP code: " + responseCode);
                }
                
                connection.disconnect();
            } catch (Exception e) {
                sendMessageLater(EnumChatFormatting.RED + "Error validating API key: " + e.getMessage());
            }
        });
    }
    
    private String maskApiKey(String apiKey) {
        if (apiKey.length() <= 8) return apiKey;
        return apiKey.substring(0, 4) + "..." + apiKey.substring(apiKey.length() - 4);
    }
    
    private void sendMessage(String message) {
        Minecraft.getMinecraft().thePlayer.addChatMessage(
            new ChatComponentText(EnumChatFormatting.GOLD + "[HypixelAPI] " + EnumChatFormatting.RESET + message)
        );
    }
    
    private void sendMessageLater(String message) {
        Minecraft.getMinecraft().addScheduledTask(() -> sendMessage(message));
    }

    public List<String> getCompletions(String[] args) {
        if (args.length == 1) {
            return Arrays.asList("set", "clear", "status");
        }
        return Collections.emptyList(); // Return empty list instead of calling super
    }
}
