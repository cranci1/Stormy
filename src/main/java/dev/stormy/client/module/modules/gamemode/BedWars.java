package dev.stormy.client.module.modules.gamemode;

import dev.stormy.client.module.Module;
import dev.stormy.client.module.setting.impl.DescriptionSetting;
import dev.stormy.client.module.setting.impl.TickSetting;
import dev.stormy.client.utils.player.PlayerUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
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

public class BedWars extends Module {
    public static TickSetting diamondArmor, fireball, obsidian, enderPearl, inv, shouldPing;
    
    private final int OBSIDIAN_SCAN_RADIUS = 10;
    private final int SCAN_INTERVAL = 20;
    private int tickCounter = 0;
    
    private List<String> armoredPlayers = new ArrayList<>();
    private Map<String, Item> lastHeldItems = new HashMap<>();
    private Map<BlockPos, Long> obsidianPositions = new HashMap<>();
    
    private boolean outsideSpawn = true;
    private BlockPos spawnPos = null;
    private boolean checkSpawn = false;

    public BedWars() {
        super("BedWars", ModuleCategory.GameMode, 0);
        this.registerSetting(new DescriptionSetting("BedWars utilities and alerts"));
        this.registerSetting(diamondArmor = new TickSetting("Diamond Armor", true));
        this.registerSetting(fireball = new TickSetting("Fireball", true));
        this.registerSetting(obsidian = new TickSetting("Obsidian", true));
        this.registerSetting(enderPearl = new TickSetting("Ender Pearl", true));
        this.registerSetting(inv = new TickSetting("Invisibility", true));
        this.registerSetting(shouldPing = new TickSetting("Should Ping", true));
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
                if (player == null || player == mc.thePlayer || PlayerUtils.isTeamMate(player))
                    continue;
                    
                String name = player.getName();
                
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
                
                ItemStack heldItem = player.getHeldItem();
                if (heldItem != null) {
                    Item item = heldItem.getItem();
                    
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
        }
    }
    
    private void scanForObsidian() {
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player == mc.thePlayer || PlayerUtils.isTeamMate(player))
                continue;
                
            BlockPos playerPos = new BlockPos(player.posX, player.posY, player.posZ);
            for (int x = -OBSIDIAN_SCAN_RADIUS; x <= OBSIDIAN_SCAN_RADIUS; x++) {
                for (int y = -3; y <= 3; y++) {
                    for (int z = -OBSIDIAN_SCAN_RADIUS; z <= OBSIDIAN_SCAN_RADIUS; z++) {
                        BlockPos pos = playerPos.add(x, y, z);
                        Block block = mc.theWorld.getBlockState(pos).getBlock();
                        if (block instanceof BlockObsidian && !obsidianPositions.containsKey(pos) && isNextToBed(pos)) {
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
    
    private boolean isNextToBed(BlockPos blockPos) {
        for (EnumFacing facing : EnumFacing.values()) {
            BlockPos offset = blockPos.offset(facing);
            if (mc.theWorld.getBlockState(offset).getBlock() instanceof BlockBed) {
                return true;
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
            if (player == mc.thePlayer || PlayerUtils.isTeamMate(player))
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
    
    @SubscribeEvent
    public void onChat(ChatReceivedEvent event) {
        String message = event.getMessage().getUnformattedText();
        
        if (message.contains("Protect your bed and destroy the enemy beds.")) {
            checkSpawn = true;
            clearTrackers();
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
}