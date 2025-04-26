package dev.stormy.client.module.modules.gamemode;

import dev.stormy.client.module.Module;
import dev.stormy.client.module.setting.impl.DescriptionSetting;
import dev.stormy.client.module.setting.impl.TickSetting;
import dev.stormy.client.utils.player.PlayerUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.init.Items;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockObsidian;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.EnumFacing;
import net.weavemc.loader.api.event.SubscribeEvent;
import net.weavemc.loader.api.event.RenderHandEvent;
import net.weavemc.loader.api.event.ChatReceivedEvent;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Pattern;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

public class BedWars extends Module {
    public static TickSetting diamondArmor, fireball, obsidian, enderPearl, inv, shouldPing;
    public static TickSetting diamondSword, ironSword, stats;
    
    private final int OBSIDIAN_SCAN_RADIUS = 10;
    private final int SCAN_INTERVAL = 20;
    private int tickCounter = 0;
    
    private List<String> armoredPlayers = new ArrayList<>();
    private Map<String, Item> lastHeldItems = new HashMap<>();
    private Map<BlockPos, Long> obsidianPositions = new HashMap<>();
    private Set<String> diamondSwordPlayers = new HashSet<>();
    private Set<String> ironSwordPlayers = new HashSet<>();
    private Set<String> statsCheckedPlayers = new HashSet<>();
    
    private boolean outsideSpawn = true;
    private BlockPos spawnPos = null;
    private boolean checkSpawn = false;
    private boolean bedwarsGameStarted = false;
    
    private static String apiKey = "";
    private static final String CONFIG_DIR = "config/Berry/";
    private static final String API_CONFIG = CONFIG_DIR + "hypixel_api.txt";
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);
    
    // Regex pattern to match UUIDs
    private static final Pattern UUID_PATTERN = Pattern.compile(
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", 
        Pattern.CASE_INSENSITIVE
    );

    public BedWars() {
        super("BedWars", ModuleCategory.GameMode, 0);
        this.registerSetting(new DescriptionSetting("BedWars utilities and alerts"));
        this.registerSetting(diamondArmor = new TickSetting("Diamond Armor", true));
        this.registerSetting(diamondSword = new TickSetting("Diamond Sword", true));
        this.registerSetting(ironSword = new TickSetting("Iron Sword", true));
        this.registerSetting(fireball = new TickSetting("Fireball", true));
        this.registerSetting(obsidian = new TickSetting("Obsidian", true));
        this.registerSetting(enderPearl = new TickSetting("Ender Pearl", true));
        this.registerSetting(inv = new TickSetting("Invisibility", true));
        this.registerSetting(stats = new TickSetting("Player Stats", true));
        this.registerSetting(shouldPing = new TickSetting("Should Ping", true));
        
        // Load API key on initialization
        loadApiKey();
    }

    @Override
    public void onEnable() {
        checkSpawn = false;
        outsideSpawn = true;
        clearTrackers();
    }
    
    @Override
    public void onDisable() {
        outsideSpawn = true;
        clearTrackers();
    }
    
    private void clearTrackers() {
        armoredPlayers.clear();
        lastHeldItems.clear();
        obsidianPositions.clear();
        diamondSwordPlayers.clear();
        ironSwordPlayers.clear();
        statsCheckedPlayers.clear();
        bedwarsGameStarted = false;
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
    public void onRender(RenderHandEvent e) {
        if (!PlayerUtils.isPlayerInGame() || mc.currentScreen != null)
            return;
            
        tickCounter++;
        
        if (tickCounter >= SCAN_INTERVAL) {
            tickCounter = 0;
            
            if (checkSpawn) {
                spawnPos = mc.thePlayer.getPosition();
                checkSpawn = false;
            }
            
            if (spawnPos != null) {
                outsideSpawn = mc.thePlayer.getDistanceSq(spawnPos) > 800;
            } else {
                outsideSpawn = true;
            }
            
            for (EntityPlayer player : mc.theWorld.playerEntities) {
                if (player == null || player == mc.thePlayer || PlayerUtils.isTeamMate(player) || 
                    player.isInvisibleToPlayer(mc.thePlayer) || isSpectator(player))
                    continue;
                    
                String name = player.getName();
                
                // Check for diamond armor
                if (diamondArmor.isToggled()) {
                    ItemStack leggings = player.getCurrentArmor(1);
                    ItemStack boots = player.getCurrentArmor(0);
                    
                    if (!armoredPlayers.contains(name) && 
                        leggings != null && leggings.getItem() == Items.diamond_leggings &&
                        boots != null && boots.getItem() == Items.diamond_boots) {
                        armoredPlayers.add(name);
                        sendAlert(EnumChatFormatting.AQUA + player.getName() + " has diamond armor!");
                        pingPlayer();
                    }
                }
                
                // Check for sword and items
                ItemStack heldItem = player.getHeldItem();
                if (heldItem != null) {
                    Item item = heldItem.getItem();
                    
                    // Diamond sword check
                    if (diamondSword.isToggled() && item instanceof ItemSword) {
                        String materialName = ((ItemSword) item).getToolMaterialName();
                        if (materialName.equals("EMERALD") && !diamondSwordPlayers.contains(name)) {
                            diamondSwordPlayers.add(name);
                            double distance = Math.round(mc.thePlayer.getDistanceToEntity(player));
                            sendAlert(EnumChatFormatting.AQUA + player.getName() + " has a diamond sword! (" + 
                                     EnumChatFormatting.DARK_PURPLE + (int)distance + "m" + EnumChatFormatting.GRAY + ")");
                            pingPlayer();
                        }
                        // Iron sword check
                        else if (ironSword.isToggled() && materialName.equals("IRON") && !ironSwordPlayers.contains(name)) {
                            ironSwordPlayers.add(name);
                            double distance = Math.round(mc.thePlayer.getDistanceToEntity(player));
                            sendAlert(EnumChatFormatting.WHITE + player.getName() + " has an iron sword! (" + 
                                     EnumChatFormatting.DARK_PURPLE + (int)distance + "m" + EnumChatFormatting.GRAY + ")");
                            pingPlayer();
                        }
                    }
                    
                    // Other items check (fireball, ender pearl)
                    if (!lastHeldItems.containsKey(name) || lastHeldItems.get(name) != item) {
                        lastHeldItems.put(name, item);
                        
                        if (fireball.isToggled() && item == Items.fire_charge) {
                            double distance = Math.round(mc.thePlayer.getDistanceToEntity(player));
                            sendAlert(EnumChatFormatting.RED + player.getName() + " has a fireball! (" + 
                                     EnumChatFormatting.DARK_PURPLE + (int)distance + "m" + EnumChatFormatting.GRAY + ")");
                            pingPlayer();
                        }
                        
                        if (enderPearl.isToggled() && item == Items.ender_pearl) {
                            double distance = Math.round(mc.thePlayer.getDistanceToEntity(player));
                            sendAlert(EnumChatFormatting.DARK_GREEN + player.getName() + " has an ender pearl! (" + 
                                     EnumChatFormatting.DARK_PURPLE + (int)distance + "m" + EnumChatFormatting.GRAY + ")");
                            pingPlayer();
                        }
                    }
                }
            }
            
            if (obsidian.isToggled()) {
                scanForObsidian();
                cleanupOldObsidian();
            }
            
            if (inv.isToggled()) {
                checkForInvisPotion();
            }
            
            // Check player stats if game has started and API key is set
            if (stats.isToggled() && bedwarsGameStarted && !apiKey.isEmpty()) {
                checkPlayerStats();
            }
        }
    }
    
    // Player stats checking
    private void checkPlayerStats() {
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player == null || player == mc.thePlayer || 
                PlayerUtils.isTeamMate(player) || isSpectator(player) || 
                statsCheckedPlayers.contains(player.getName()))
                continue;
                
            statsCheckedPlayers.add(player.getName());
            
            // Check if player is nicked (UUID version 1 indicates nickname)
            UUID playerUUID = player.getUniqueID();
            if (playerUUID.version() == 1) {
                String nickedMessage = EnumChatFormatting.RED + "[NICKED] " + 
                                      EnumChatFormatting.GRAY + player.getName();
                sendAlert(nickedMessage);
                continue;
            }
            
            fetchPlayerStats(playerUUID.toString().replace("-", ""), player.getName());
        }
    }
    
    private void fetchPlayerStats(String uuid, String playerName) {
        CompletableFuture.runAsync(() -> {
            try {
                URL url = new URL("https://api.hypixel.net/player?key=" + apiKey + "&uuid=" + uuid);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
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
                sendAlert(EnumChatFormatting.RED + "No stats found for " + playerName);
                return;
            }
            
            JSONObject player = json.getJSONObject("player");
            
            if (!player.has("stats") || !player.getJSONObject("stats").has("Bedwars")) {
                sendAlert(EnumChatFormatting.YELLOW + playerName + " has no BedWars stats");
                return;
            }
            
            JSONObject bedwars = player.getJSONObject("stats").getJSONObject("Bedwars");
            
            int level = player.has("achievements") && player.getJSONObject("achievements").has("bedwars_level") ? 
                        player.getJSONObject("achievements").getInt("bedwars_level") : 0;
            int wins = bedwars.optInt("wins_bedwars", 0);
            int losses = bedwars.optInt("losses_bedwars", 0);
            float wlr = losses > 0 ? (float) wins / losses : wins;
            
            int finalKills = bedwars.optInt("final_kills_bedwars", 0);
            int finalDeaths = bedwars.optInt("final_deaths_bedwars", 0);
            float fkdr = finalDeaths > 0 ? (float) finalKills / finalDeaths : finalKills;
            
            int bedsDestroyed = bedwars.optInt("beds_broken_bedwars", 0);
            int bedsLost = bedwars.optInt("beds_lost_bedwars", 0);
            
            // Format detailed stats message
            String statsMessage = String.format("%s[%d✫] %s %s FKDR: %.2f WLR: %.2f W: %d FK: %d Beds: %d",
                                               getStarColor(level),
                                               level,
                                               playerName,
                                               EnumChatFormatting.GRAY,
                                               fkdr,
                                               wlr,
                                               wins,
                                               finalKills,
                                               bedsDestroyed);
            
            // Send stats to chat on the main thread
            CompletableFuture.runAsync(() -> sendAlert(statsMessage), task -> mc.addScheduledTask(task));
            
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
    
    @SubscribeEvent
    public void onChat(ChatReceivedEvent event) {
        String message = event.getMessage().getUnformattedText();
        
        if (message.contains("Protect your bed and destroy the enemy beds.")) {
            checkSpawn = true;
            clearTrackers();
            bedwarsGameStarted = true;
            statsCheckedPlayers.clear();
            
            // Delay stats checking to ensure all players are loaded
            if (stats.isToggled() && !apiKey.isEmpty()) {
                CompletableFuture.runAsync(() -> {
                    try {
                        Thread.sleep(2000); // 2 second delay
                        checkPlayerStats();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }, EXECUTOR);
            }
        }
    }
    
    private void sendAlert(String message) {
        mc.thePlayer.addChatMessage(new ChatComponentText(EnumChatFormatting.GOLD + "[BedWars] " + EnumChatFormatting.GRAY + message));
    }
    
    private void pingPlayer() {
        if (shouldPing.isToggled()) {
            mc.thePlayer.playSound("note.pling", 1.0F, 1.0F);
        }
    }
    
    // Check if player is in spectator mode
    private boolean isSpectator(EntityPlayer player) {
        // Check if player is a spectator by seeing if they're invisible and not in combat
        return (player.isInvisible() && player.isSpectator()) || 
               (player.isInvisible() && player.getHealth() <= 0) ||
               // Alternative check using GameType when available
               (mc.getNetHandler() != null && 
                mc.getNetHandler().getPlayerInfo(player.getUniqueID()) != null && 
                mc.getNetHandler().getPlayerInfo(player.getUniqueID()).getGameType() == net.minecraft.world.WorldSettings.GameType.SPECTATOR);
    }
    
    // Improved obsidian detection
    private void scanForObsidian() {
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player == null || player == mc.thePlayer || PlayerUtils.isTeamMate(player) || isSpectator(player))
                continue;
                
            BlockPos playerPos = new BlockPos(player.posX, player.posY, player.posZ);
            for (int x = -OBSIDIAN_SCAN_RADIUS; x <= OBSIDIAN_SCAN_RADIUS; x++) {
                for (int y = -3; y <= 3; y++) {
                    for (int z = -OBSIDIAN_SCAN_RADIUS; z <= OBSIDIAN_SCAN_RADIUS; z++) {
                        BlockPos pos = playerPos.add(x, y, z);
                        Block block = mc.theWorld.getBlockState(pos).getBlock();
                        if (block instanceof BlockObsidian && !obsidianPositions.containsKey(pos)) {
                            // Check if obsidian is close to a bed or within player's reach
                            if (isNextToBed(pos) || player.getDistance(pos.getX(), pos.getY(), pos.getZ()) < 5.0) {
                                obsidianPositions.put(pos, System.currentTimeMillis());
                                sendAlert(EnumChatFormatting.DARK_PURPLE + player.getName() + " placed obsidian!");
                                pingPlayer();
                                return;
                            }
                        }
                    }
                }
            }
        }
    }
    
    private boolean isNextToBed(BlockPos blockPos) {
        for (EnumFacing facing : EnumFacing.values()) {
            BlockPos offset = blockPos.offset(facing);
            Block block = mc.theWorld.getBlockState(offset).getBlock();
            if (block instanceof BlockBed) {
                return true;
            }
        }
        
        // Check in a wider radius for beds
        for (int x = -2; x <= 2; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -2; z <= 2; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    BlockPos pos = blockPos.add(x, y, z);
                    Block block = mc.theWorld.getBlockState(pos).getBlock();
                    if (block instanceof BlockBed) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    
    private void cleanupOldObsidian() {
        Iterator<Map.Entry<BlockPos, Long>> iterator = obsidianPositions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, Long> entry = iterator.next();
            BlockPos pos = entry.getKey();
            long timeDetected = entry.getValue();
            
            if (!(mc.theWorld.getBlockState(pos).getBlock() instanceof BlockObsidian) || 
                System.currentTimeMillis() - timeDetected > 30000) {
                iterator.remove();
            }
        }
    }
    
    private void checkForInvisPotion() {
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player == mc.thePlayer || PlayerUtils.isTeamMate(player) || isSpectator(player))
                continue;
                
            if (player.isInvisible() && isPlayerInTabList(player)) {
                double distance = Math.round(mc.thePlayer.getDistanceToEntity(player));
                sendAlert(EnumChatFormatting.GRAY + player.getName() + " is invisible! (" + 
                         EnumChatFormatting.DARK_PURPLE + (int)distance + "m" + EnumChatFormatting.GRAY + ")");
                pingPlayer();
                return;
            }
        }
    }

    private boolean isPlayerInTabList(EntityPlayer player) {
        if (mc.getNetHandler() == null || mc.getNetHandler().getPlayerInfoMap() == null) 
            return false;
            
        return mc.getNetHandler().getPlayerInfoMap().stream()
            .anyMatch(info -> info != null && info.getGameProfile() != null && 
                     info.getGameProfile().getName().equals(player.getName()));
    }
}