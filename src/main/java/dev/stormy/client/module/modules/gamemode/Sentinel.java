package dev.stormy.client.module.modules.gamemode;

import dev.stormy.client.module.Module;
import dev.stormy.client.module.setting.impl.DescriptionSetting;
import dev.stormy.client.module.setting.impl.TickSetting;
import dev.stormy.client.utils.player.PlayerUtils;
import dev.stormy.client.utils.Utils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumChatFormatting;
import net.weavemc.loader.api.event.SubscribeEvent;
import net.weavemc.loader.api.event.RenderGameOverlayEvent;
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
    public static TickSetting editPosition;
    
    // API settings
    private static String apiKey = "";
    private static final String CONFIG_DIR = "config/Berry/";
    private static final String API_CONFIG = CONFIG_DIR + "hypixel_api.txt";
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);
    
    // Player data storage
    private final Map<String, PlayerStats> playerStats = new HashMap<>();
    private final Set<String> nickedPlayers = new HashSet<>();
    private final Set<String> processedPlayers = new HashSet<>();
    private final Map<String, Team> playerTeams = new HashMap<>();
    private long lastTeamUpdate = 0;
    private static final long TEAM_UPDATE_INTERVAL = 10000; // Update team cache every 10 seconds

    // UI positioning
    private int uiX = 5;
    private int uiY = 70;
    private final int ENTRY_HEIGHT = 20;
    private final int COMPACT_ENTRY_HEIGHT = 15;
    
    public Sentinel() {
        super("Sentinel", ModuleCategory.GameMode, Keyboard.KEY_NONE);
        this.registerSetting(new DescriptionSetting("BedWars stats overlay"));
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
        if (!PlayerUtils.isPlayerInGame() || !showUI.isToggled())
            return;
        
        // Check for new players
        checkPlayers();
        
        // Render UI 
        renderStatsUI();
    }
    
    private int calculateUIHeight() {
        int totalEntries = playerStats.size() + (showNicked.isToggled() ? nickedPlayers.size() : 0);
        int entryHeight = compactMode.isToggled() ? COMPACT_ENTRY_HEIGHT : ENTRY_HEIGHT;
        return 20 + (totalEntries * entryHeight); // Header + entries
    }
    
    private void renderStatsUI() {
        try {
            // Calculate UI dimensions
            int uiWidth = 300;
            int uiHeight = calculateUIHeight();
            int entryHeight = compactMode.isToggled() ? COMPACT_ENTRY_HEIGHT : ENTRY_HEIGHT;
            
            // Draw background
            Utils.HUD.drawRoundedRect(uiX, uiY, uiX + uiWidth, uiY + uiHeight, 5, new Color(0, 0, 0, 180));
            
            // Draw header with gradient effect
            Color headerColor = new Color(25, 25, 25, 220);
            Utils.HUD.drawRoundedRect(uiX, uiY, uiX + uiWidth, uiY + 20, 5, headerColor);
            
            // Draw title with custom formatting
            String title = "§b§lSentinel §f- §eBedWars Stats";
            mc.fontRendererObj.drawStringWithShadow(title, uiX + 5, uiY + 6, -1);
            
            // Draw API status with animation
            String apiStatus = apiKey.isEmpty() ? "§c§lNo API Key" : "§a§lAPI Connected";
            mc.fontRendererObj.drawStringWithShadow(apiStatus, 
                    uiX + uiWidth - mc.fontRendererObj.getStringWidth(apiStatus) - 5, uiY + 6, -1);
            
            // Draw player entries
            int yOffset = uiY + 20;
            
            // Sort players by FKDR (highest first)
            List<Map.Entry<String, PlayerStats>> sortedStats = new ArrayList<>(playerStats.entrySet());
            sortedStats.sort((a, b) -> Float.compare(b.getValue().fkdr, a.getValue().fkdr));
            
            // Draw stats for each player
            for (Map.Entry<String, PlayerStats> entry : sortedStats) {
                String playerName = entry.getKey();
                PlayerStats stats = entry.getValue();
                
                if (playerName == null || stats == null) continue; // Skip invalid entries
                
                // Draw entry background with alternating colors
                int entryAlpha = 120;
                int colorShift = sortedStats.indexOf(entry) % 2 == 0 ? 0 : 15;
                Color entryColor = new Color(30 + colorShift, 30 + colorShift, 35 + colorShift, entryAlpha);
                Utils.HUD.drawRoundedRect(uiX, yOffset, uiX + uiWidth, yOffset + entryHeight, 0, entryColor);
                
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
                    // Draw background for nicked players with distinct color
                    int entryAlpha = 120;
                    Color nickedColor = new Color(40, 20, 25, entryAlpha);
                    Utils.HUD.drawRoundedRect(uiX, yOffset, uiX + uiWidth, yOffset + entryHeight, 0, nickedColor);
                    
                    // Display nicked player info
                    mc.fontRendererObj.drawStringWithShadow("§c§lNICKED §f" + nickedPlayer, 
                            uiX + 5, yOffset + (entryHeight / 2) - 4, -1);
                    
                    yOffset += entryHeight;
                }
            }
        } catch (Exception e) {
            // Log error but don't crash
            System.err.println("Error rendering Sentinel UI: " + e.getMessage());
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
        String finalKillsText = "§7KDR: " + getColorForFKDR(stats.kdr) + String.format("%.2f", stats.kdr);
        String bedsText = "§7Beds: " + getColorForBeds(stats.bedsDestroyed) + stats.bedsDestroyed;
        
        int statX = uiX + 5;
        mc.fontRendererObj.drawStringWithShadow(fkdrText, statX, yPos + 12, -1);
        statX += mc.fontRendererObj.getStringWidth(fkdrText) + 10;
        
        mc.fontRendererObj.drawStringWithShadow(wlrText, statX, yPos + 12, -1);
        statX += mc.fontRendererObj.getStringWidth(wlrText) + 10;
        
        mc.fontRendererObj.drawStringWithShadow(finalKillsText, statX, yPos + 12, -1);
        statX += mc.fontRendererObj.getStringWidth(finalKillsText) + 10;
        
        mc.fontRendererObj.drawStringWithShadow(bedsText, statX, yPos + 12, -1);
    }
    
    private void renderCompactEntry(String playerName, PlayerStats stats, int yPos) {
        if (playerName == null || stats == null) return; // Defensive check
        
        String teamColor = getTeamColor(playerName);
        String starDisplay = getStarColor(stats.level) + "[" + stats.level + "✫]";

        String fkdrText = getColorForFKDR(stats.fkdr) + String.format("%.2f", stats.fkdr);
        String wlrText = getColorForWLR(stats.wlr) + String.format("%.2f", stats.wlr);
        String kdrText = getColorForFKDR(stats.kdr) + String.format("%.2f", stats.kdr);
        
        // Draw name and stats
        String displayName = starDisplay + " " + teamColor + playerName;
        mc.fontRendererObj.drawStringWithShadow(displayName, 
                uiX + 5, yPos + (COMPACT_ENTRY_HEIGHT / 2) - 4, -1);
        
        // Draw stats on right side
        String statsText = "§7FK: " + fkdrText + " §7W: " + wlrText + " §7K: " + kdrText;
        mc.fontRendererObj.drawStringWithShadow(statsText,
                uiX + 300 - mc.fontRendererObj.getStringWidth(statsText) - 5,
                yPos + (COMPACT_ENTRY_HEIGHT / 2) - 4, -1);
    }
    
    private void checkPlayers() {
        if (mc == null || mc.theWorld == null || apiKey.isEmpty()) return;
            
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player == null || player == mc.thePlayer || 
                PlayerUtils.isTeamMate(player) || isSpectator(player) ||
                processedPlayers.contains(player.getName()))
                continue;
                
            processedPlayers.add(player.getName());
            
            UUID playerUUID = player.getUniqueID();
            if (playerUUID == null) {
                nickedPlayers.add(player.getName());
                continue;
            }
            if (playerUUID.version() == 1) {
                nickedPlayers.add(player.getName());
                continue;
            }
            
            fetchPlayerStats(playerUUID.toString().replace("-", ""), player.getName());
        }
    }
    
    private boolean isSpectator(EntityPlayer player) {
        return (player.isInvisible() && player.isSpectator()) || 
               (player.isInvisible() && player.getHealth() <= 0) ||
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
            if (!json.has("player") || json.isNull("player")) return;
            
            JSONObject player = json.getJSONObject("player");
            if (!player.has("stats") || !player.getJSONObject("stats").has("Bedwars")) return;
            
            JSONObject bedwars = player.getJSONObject("stats").getJSONObject("Bedwars");
            
            PlayerStats stats = new PlayerStats.Builder()
                .setLevel(player.getJSONObject("achievements").optInt("bedwars_level", 0))
                .calculateWLR(
                    bedwars.optInt("wins_bedwars", 0),
                    bedwars.optInt("losses_bedwars", 0))
                .calculateKDR(
                    bedwars.optInt("kills_bedwars", 0),
                    bedwars.optInt("deaths_bedwars", 0))
                .calculateFKDR(
                    bedwars.optInt("final_kills_bedwars", 0),
                    bedwars.optInt("final_deaths_bedwars", 0))
                .setBedsDestroyed(bedwars.optInt("beds_broken_bedwars", 0))
                .build();
            
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
    
    private String getColorForBeds(int beds) {
        if (beds >= 5000) return "§5"; // Dark Purple
        else if (beds >= 2000) return "§d"; // Light Purple
        else if (beds >= 1000) return "§c"; // Red
        else if (beds >= 500) return "§6"; // Gold
        else return "§7"; // Gray
    }
    
    private String getTeamColor(String playerName) {
        if (playerName == null) return Team.NONE.colorCode;
        
        updateTeams();
        Team team = playerTeams.getOrDefault(playerName, Team.NONE);
        return team.colorCode + "[" + team.shortName + "] ";
    }

    private void updateTeams() {
        if (System.currentTimeMillis() - lastTeamUpdate < TEAM_UPDATE_INTERVAL) return;
        lastTeamUpdate = System.currentTimeMillis();

        if (mc.theWorld == null || mc.thePlayer == null) return;

        playerTeams.clear();
        
        // Get scoreboard
        net.minecraft.scoreboard.Scoreboard scoreboard = mc.theWorld.getScoreboard();
        if (scoreboard == null) return;

        // Process all teams
        for (net.minecraft.scoreboard.ScorePlayerTeam team : scoreboard.getTeams()) {
            String prefix = team.getColorPrefix();
            Team bedwarsTeam = getTeamFromPrefix(prefix);
            
            for (String playerName : team.getMembershipCollection()) {
                playerTeams.put(playerName, bedwarsTeam);
            }
        }
    }

    private Team getTeamFromPrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) return Team.NONE;
        
        // First try exact match
        for (Team team : Team.values()) {
            if (prefix.startsWith(team.colorCode)) {
                return team;
            }
        }

        // Fallback to color-only detection
        return switch (getLastColorCode(prefix)) {
            case "§c" -> Team.RED;
            case "§9" -> Team.BLUE;
            case "§a" -> Team.GREEN;
            case "§e" -> Team.YELLOW;
            case "§f" -> Team.WHITE;
            case "§d" -> Team.PINK;
            case "§7" -> Team.GRAY;
            case "§b" -> Team.AQUA;
            default -> Team.NONE;
        };
    }

    private String getLastColorCode(String text) {
        String lastCode = "§f";
        for (int i = 0; i < text.length() - 1; i++) {
            if (text.charAt(i) == '§') {
                lastCode = "§" + text.charAt(i + 1);
            }
        }
        return lastCode;
    }

    private enum Team {
        RED("§c", "R"),
        BLUE("§9", "B"),
        GREEN("§a", "G"),
        YELLOW("§e", "Y"),
        WHITE("§f", "W"),
        PINK("§d", "P"),
        GRAY("§7", "S"),
        AQUA("§b", "A"),
        NONE("§f", "");

        final String colorCode;
        final String shortName;

        Team(String colorCode, String shortName) {
            this.colorCode = colorCode;
            this.shortName = shortName;
        }
    }

    // Class to store player statistics
    private static class PlayerStats {
        private final int level;
        private final float wlr;
        private final float kdr;
        private final float fkdr;
        private final int bedsDestroyed;

        private PlayerStats(Builder builder) {
            this.level = builder.level;
            this.wlr = builder.wlr;
            this.kdr = builder.kdr;
            this.fkdr = builder.fkdr;
            this.bedsDestroyed = builder.bedsDestroyed;
        }

        static class Builder {
            private int level;
            private float wlr;
            private float kdr;
            private float fkdr;
            private int bedsDestroyed;

            Builder setLevel(int level) {
                this.level = level;
                return this;
            }

            Builder calculateWLR(int wins, int losses) {
                this.wlr = losses > 0 ? (float) wins / losses : wins;
                return this;
            }

            Builder calculateKDR(int kills, int deaths) {
                this.kdr = deaths > 0 ? (float) kills / deaths : kills;
                return this;
            }

            Builder calculateFKDR(int finalKills, int finalDeaths) {
                this.fkdr = finalDeaths > 0 ? (float) finalKills / finalDeaths : finalKills;
                return this;
            }

            Builder setBedsDestroyed(int beds) {
                this.bedsDestroyed = beds;
                return this;
            }

            PlayerStats build() {
                return new PlayerStats(this);
            }
        }
    }
}