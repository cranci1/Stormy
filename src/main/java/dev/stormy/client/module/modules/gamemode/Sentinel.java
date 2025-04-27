package dev.stormy.client.module.modules.gamemode;

import dev.stormy.client.module.Module;
import dev.stormy.client.module.setting.impl.DescriptionSetting;
import dev.stormy.client.module.setting.impl.TickSetting;
import dev.stormy.client.utils.player.PlayerUtils;
import dev.stormy.client.utils.Utils;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.weavemc.loader.api.event.SubscribeEvent;
import net.weavemc.loader.api.event.RenderGameOverlayEvent;
import me.tryfle.stormy.events.MouseEvent;
import org.lwjgl.input.Keyboard;

import java.awt.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONObject;
import org.json.JSONException;

public class Sentinel extends Module {
    // UI Settings
    public static TickSetting showUI;
    public static TickSetting compactMode;
    public static TickSetting showNicked;
    
    // API settings
    private static String apiKey = "";
    private static final String CONFIG_DIR = "config/Berry/";
    private static final String API_CONFIG = CONFIG_DIR + "hypixel_api.txt";
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);
    
    // Player data storage
    private final Map<String, PlayerStats> playerStats = new HashMap<>();
    private final Set<String> nickedPlayers = new HashSet<>();
    private final Set<String> processedPlayers = new HashSet<>();
    
    // UI positioning
    private int uiX = 5;
    private int uiY = 70;
    private final int ENTRY_HEIGHT = 20;
    private final int COMPACT_ENTRY_HEIGHT = 15;
    private boolean isDragging = false;
    private int dragOffsetX, dragOffsetY;
    
    public Sentinel() {
        super("Sentinel", ModuleCategory.GameMode, Keyboard.KEY_NONE);
        this.registerSetting(new DescriptionSetting("BedWars stats overlay with UI"));
        this.registerSetting(showUI = new TickSetting("Show UI", true));
        this.registerSetting(compactMode = new TickSetting("Compact Mode", false));
        this.registerSetting(showNicked = new TickSetting("Show Nicked Players", true));
        
        // Load API key on initialization
        loadApiKey();
    }
    
    @Override
    public void onEnable() {
        // Clear previously stored player data
        playerStats.clear();
        nickedPlayers.clear();
        processedPlayers.clear();
    }
    
    @Override
    public void onDisable() {
        // Clear data when disabled
        playerStats.clear();
        nickedPlayers.clear();
        processedPlayers.clear();
    }
    
    // API key management methods
    public static void setApiKey(String key) {
        apiKey = key;
        saveApiKey();
    }
    
    public static String getApiKey() {
        return apiKey;
    }
    
    private static void saveApiKey() {
        try {
            File configDir = new File(CONFIG_DIR);
            if (!configDir.exists()) {
                configDir.mkdirs();
            }
            
            BufferedWriter writer = new BufferedWriter(new FileWriter(API_CONFIG));
            writer.write(apiKey);
            writer.close();
        } catch (IOException e) {
            System.err.println("Error saving Hypixel API key: " + e.getMessage());
        }
    }
    
    private static void loadApiKey() {
        try {
            File apiFile = new File(API_CONFIG);
            if (apiFile.exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(apiFile));
                apiKey = reader.readLine();
                reader.close();
            }
        } catch (IOException e) {
            System.err.println("Error loading Hypixel API key: " + e.getMessage());
        }
    }
    
    @SubscribeEvent
    public void onRender(RenderGameOverlayEvent event) {
        if (!PlayerUtils.isPlayerInGame() || !showUI.isToggled() || mc.currentScreen != null)
            return;
        
        // Check for new players
        checkPlayers();
        
        // Render UI 
        renderStatsUI();
    }
    
    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        if (!PlayerUtils.isPlayerInGame() || !showUI.isToggled())
            return;
            
        ScaledResolution sr = new ScaledResolution(mc);
        int mouseX = event.getX();
        int mouseY = event.getY();
        
        int uiWidth = 300;
        int uiHeight = calculateUIHeight();
        
        // Handle dragging
        if (event.getButton() == 0) {
            if (MouseEvent.isButtonPressed(event)) { // Use the utility method
                if (mouseX >= uiX && mouseX <= uiX + uiWidth && 
                    mouseY >= uiY && mouseY <= uiY + 20) { // Header area
                    isDragging = true;
                    dragOffsetX = mouseX - uiX;
                    dragOffsetY = mouseY - uiY;
                }
            } else if (MouseEvent.isButtonReleased(event)) {
                isDragging = false;
            }
        }
        
        if (isDragging) {
            uiX = mouseX - dragOffsetX;
            uiY = mouseY - dragOffsetY;
        }
    }
    
    private int calculateUIHeight() {
        int totalEntries = playerStats.size() + (showNicked.isToggled() ? nickedPlayers.size() : 0);
        int entryHeight = compactMode.isToggled() ? COMPACT_ENTRY_HEIGHT : ENTRY_HEIGHT;
        return 20 + (totalEntries * entryHeight); // Header + entries
    }
    
    private void renderStatsUI() {
        // Calculate UI dimensions
        int uiWidth = 300;
        int uiHeight = calculateUIHeight();
        int entryHeight = compactMode.isToggled() ? COMPACT_ENTRY_HEIGHT : ENTRY_HEIGHT;
        
        // Draw background
        Utils.HUD.drawRoundedRect(uiX, uiY, uiX + uiWidth, uiY + uiHeight, 5, new Color(0, 0, 0, 180));
        
        // Draw header
        Utils.HUD.drawRoundedRect(uiX, uiY, uiX + uiWidth, uiY + 20, 5, new Color(25, 25, 25, 220));
        mc.fontRendererObj.drawStringWithShadow("§b§lSentinel §f- BedWars Stats", uiX + 5, uiY + 6, -1);
        
        // Draw API status
        String apiStatus = apiKey.isEmpty() ? "§c§lNo API Key" : "§a§lAPI Connected";
        mc.fontRendererObj.drawStringWithShadow(apiStatus, uiX + uiWidth - mc.fontRendererObj.getStringWidth(apiStatus) - 5, uiY + 6, -1);
        
        // Draw player entries
        int yOffset = uiY + 20;
        
        // Sort players by FKDR (highest first)
        List<Map.Entry<String, PlayerStats>> sortedStats = new ArrayList<>(playerStats.entrySet());
        sortedStats.sort((a, b) -> Float.compare(b.getValue().fkdr, a.getValue().fkdr));
        
        // Draw stats for each player
        for (Map.Entry<String, PlayerStats> entry : sortedStats) {
            String playerName = entry.getKey();
            PlayerStats stats = entry.getValue();
            
            // Draw background for entry
            int entryAlpha = 120;
            int colorShift = sortedStats.indexOf(entry) % 2 == 0 ? 0 : 15;
            Utils.HUD.drawRoundedRect(uiX, yOffset, uiX + uiWidth, yOffset + entryHeight, 0, 
                    new Color(30 + colorShift, 30 + colorShift, 35 + colorShift, entryAlpha));
            
            if (compactMode.isToggled()) {
                renderCompactEntry(playerName, stats, yOffset);
            } else {
                renderFullEntry(playerName, stats, yOffset);
            }
            
            yOffset += entryHeight;
        }
        
        // Draw nicked players if enabled
        if (showNicked.isToggled()) {
            for (String nickedPlayer : nickedPlayers) {
                // Draw background for entry
                int entryAlpha = 120;
                int colorShift = (nickedPlayers.size() + playerStats.size()) % 2 == 0 ? 0 : 15;
                Utils.HUD.drawRoundedRect(uiX, yOffset, uiX + uiWidth, yOffset + entryHeight, 0, 
                        new Color(40 + colorShift, 20 + colorShift, 25 + colorShift, entryAlpha));
                
                // Display nicked player info
                mc.fontRendererObj.drawStringWithShadow("§c§lNICKED §f" + nickedPlayer, 
                        uiX + 5, yOffset + (entryHeight / 2) - 4, -1);
                
                yOffset += entryHeight;
            }
        }
    }
    
    private void renderFullEntry(String playerName, PlayerStats stats, int yPos) {
        // Basic player info
        mc.fontRendererObj.drawStringWithShadow(getStarColor(stats.level) + "[" + stats.level + "✫]", 
                uiX + 5, yPos + 3, -1);
        mc.fontRendererObj.drawStringWithShadow("§f" + playerName, 
                uiX + 5 + mc.fontRendererObj.getStringWidth(getStarColor(stats.level) + "[" + stats.level + "✫] "), 
                yPos + 3, -1);
                
        // Main stats
        String fkdrText = "§7FKDR: " + getColorForFKDR(stats.fkdr) + String.format("%.2f", stats.fkdr);
        String wlrText = "§7WLR: " + getColorForWLR(stats.wlr) + String.format("%.2f", stats.wlr);
        String winsText = "§7W: " + getColorForWins(stats.wins) + stats.wins;
        String finalKillsText = "§7FK: " + getColorForFinalKills(stats.finalKills) + stats.finalKills;
        String bedsText = "§7Beds: " + getColorForBeds(stats.bedsDestroyed) + stats.bedsDestroyed;
        
        int statX = uiX + 5;
        mc.fontRendererObj.drawStringWithShadow(fkdrText, statX, yPos + 12, -1);
        statX += mc.fontRendererObj.getStringWidth(fkdrText) + 10;
        
        mc.fontRendererObj.drawStringWithShadow(wlrText, statX, yPos + 12, -1);
        statX += mc.fontRendererObj.getStringWidth(wlrText) + 10;
        
        mc.fontRendererObj.drawStringWithShadow(winsText, statX, yPos + 12, -1);
        statX += mc.fontRendererObj.getStringWidth(winsText) + 10;
        
        mc.fontRendererObj.drawStringWithShadow(finalKillsText, statX, yPos + 12, -1);
        statX += mc.fontRendererObj.getStringWidth(finalKillsText) + 10;
        
        mc.fontRendererObj.drawStringWithShadow(bedsText, statX, yPos + 12, -1);
    }
    
    private void renderCompactEntry(String playerName, PlayerStats stats, int yPos) {
        // More compact layout with less vertical spacing
        String starDisplay = getStarColor(stats.level) + "[" + stats.level + "✫]";
        String fkdrText = getColorForFKDR(stats.fkdr) + String.format("%.2f", stats.fkdr);
        String wlrText = getColorForWLR(stats.wlr) + String.format("%.2f", stats.wlr);
        
        // Draw stats in a single line
        mc.fontRendererObj.drawStringWithShadow(starDisplay + " §f" + playerName, 
                uiX + 5, yPos + (COMPACT_ENTRY_HEIGHT / 2) - 4, -1);
                
        // Draw FKDR/WLR on the right side
        mc.fontRendererObj.drawStringWithShadow("§7FK: " + fkdrText + " §7W: " + wlrText,
                uiX + 300 - mc.fontRendererObj.getStringWidth("§7FK: " + fkdrText + " §7W: " + wlrText) - 5,
                yPos + (COMPACT_ENTRY_HEIGHT / 2) - 4, -1);
    }
    
    private void checkPlayers() {
        if (apiKey.isEmpty())
            return;
            
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player == null || player == mc.thePlayer || 
                PlayerUtils.isTeamMate(player) || isSpectator(player) ||
                processedPlayers.contains(player.getName()))
                continue;
                
            processedPlayers.add(player.getName());
            
            // Check if player is nicked (UUID version 1 indicates nickname)
            UUID playerUUID = player.getUniqueID();
            if (playerUUID.version() == 1) {
                nickedPlayers.add(player.getName());
                continue;
            }
            
            fetchPlayerStats(playerUUID.toString().replace("-", ""), player.getName());
        }
    }
    
    private boolean isSpectator(EntityPlayer player) {
        // Check if player is a spectator by seeing if they're invisible and not in combat
        return (player.isInvisible() && player.isSpectator()) || 
               (player.isInvisible() && player.getHealth() <= 0) ||
               // Alternative check using GameType when available
               (mc.getNetHandler() != null && 
                mc.getNetHandler().getPlayerInfo(player.getUniqueID()) != null && 
                mc.getNetHandler().getPlayerInfo(player.getUniqueID()).getGameType() == net.minecraft.world.WorldSettings.GameType.SPECTATOR);
    }
    
    private void fetchPlayerStats(String uuid, String playerName) {
        CompletableFuture.runAsync(() -> {
            try {
                URI uri = new URI("https://api.hypixel.net/player?key=" + apiKey + "&uuid=" + uuid);
                HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
                connection.setRequestMethod("GET");
                
                if (connection.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    
                    JSONObject json = new JSONObject(response.toString());
                    
                    if (json.getBoolean("success")) {
                        processPlayerStats(json, playerName);
                    } else {
                        System.err.println("API request failed: " + json.optString("cause", "Unknown error"));
                    }
                } else {
                    System.err.println("Error fetching stats: HTTP " + connection.getResponseCode());
                }
                connection.disconnect();
            } catch (Exception e) {
                System.err.println("Error fetching stats for " + playerName + ": " + e.getMessage());
            }
        }, EXECUTOR);
    }
    
    private void processPlayerStats(JSONObject json, String playerName) {
        try {
            if (!json.has("player") || json.isNull("player")) {
                return;
            }
            
            JSONObject player = json.getJSONObject("player");
            
            if (!player.has("stats") || !player.getJSONObject("stats").has("Bedwars")) {
                return;
            }
            
            JSONObject bedwars = player.getJSONObject("stats").getJSONObject("Bedwars");
            
            PlayerStats stats = new PlayerStats();
            stats.level = player.has("achievements") && player.getJSONObject("achievements").has("bedwars_level") ? 
                          player.getJSONObject("achievements").getInt("bedwars_level") : 0;
            stats.wins = bedwars.optInt("wins_bedwars", 0);
            stats.losses = bedwars.optInt("losses_bedwars", 0);
            stats.wlr = stats.losses > 0 ? (float) stats.wins / stats.losses : stats.wins;
            
            stats.finalKills = bedwars.optInt("final_kills_bedwars", 0);
            stats.finalDeaths = bedwars.optInt("final_deaths_bedwars", 0);
            stats.fkdr = stats.finalDeaths > 0 ? (float) stats.finalKills / stats.finalDeaths : stats.finalKills;
            
            stats.bedsDestroyed = bedwars.optInt("beds_broken_bedwars", 0);
            // We'll calculate a bed efficiency ratio (beds broken / beds lost)
            stats.bedsLost = bedwars.optInt("beds_lost_bedwars", 0);
            
            // Add to player stats map on the main thread
            mc.addScheduledTask(() -> playerStats.put(playerName, stats));
            
        } catch (JSONException e) {
            System.err.println("Error processing stats for " + playerName + ": " + e.getMessage());
        }
    }
    
    private String getStarColor(int level) {
        if (level < 100) return EnumChatFormatting.GRAY.toString();
        else if (level < 200) return EnumChatFormatting.WHITE.toString();
        else if (level < 300) return EnumChatFormatting.GOLD.toString();
        else if (level < 400) return EnumChatFormatting.AQUA.toString();
        else if (level < 500) return EnumChatFormatting.DARK_GREEN.toString();
        else if (level < 600) return EnumChatFormatting.DARK_AQUA.toString();
        else if (level < 700) return EnumChatFormatting.DARK_RED.toString();
        else if (level < 800) return EnumChatFormatting.LIGHT_PURPLE.toString();
        else if (level < 900) return EnumChatFormatting.BLUE.toString();
        else return EnumChatFormatting.DARK_PURPLE.toString();
    }
    
    private String getColorForFKDR(float fkdr) {
        if (fkdr >= 10.0f) return "§5"; // Dark Purple for very high
        else if (fkdr >= 5.0f) return "§d"; // Light Purple for high 
        else if (fkdr >= 3.0f) return "§c"; // Red for good
        else if (fkdr >= 1.0f) return "§6"; // Gold for average
        else return "§7"; // Gray for below average
    }
    
    private String getColorForWLR(float wlr) {
        if (wlr >= 8.0f) return "§5"; // Dark Purple for very high
        else if (wlr >= 4.0f) return "§d"; // Light Purple for high
        else if (wlr >= 2.0f) return "§c"; // Red for good
        else if (wlr >= 1.0f) return "§6"; // Gold for average
        else return "§7"; // Gray for below average
    }
    
    private String getColorForWins(int wins) {
        if (wins >= 5000) return "§5"; // Dark Purple
        else if (wins >= 2000) return "§d"; // Light Purple
        else if (wins >= 1000) return "§c"; // Red
        else if (wins >= 500) return "§6"; // Gold
        else return "§7"; // Gray
    }
    
    private String getColorForFinalKills(int finalKills) {
        if (finalKills >= 20000) return "§5"; // Dark Purple
        else if (finalKills >= 10000) return "§d"; // Light Purple
        else if (finalKills >= 5000) return "§c"; // Red
        else if (finalKills >= 1000) return "§6"; // Gold
        else return "§7"; // Gray
    }
    
    private String getColorForBeds(int beds) {
        if (beds >= 5000) return "§5"; // Dark Purple
        else if (beds >= 2000) return "§d"; // Light Purple
        else if (beds >= 1000) return "§c"; // Red
        else if (beds >= 500) return "§6"; // Gold
        else return "§7"; // Gray
    }
    
    // Class to store player statistics
    private static class PlayerStats {
        int level;
        int wins;
        int losses;
        float wlr;
        int finalKills;
        int finalDeaths;
        float fkdr;
        int bedsDestroyed;
        int bedsLost; // We'll use this for bed efficiency calculation
    }
}