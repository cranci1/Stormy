package dev.stormy.client.utils.player;

import dev.stormy.client.utils.IMethods;
import dev.stormy.client.utils.client.ClientUtils;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.item.Item;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemSword;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MathHelper;

/**
 * @author sassan
 *         23.11.2023, 2023
 */
public class PlayerUtils implements IMethods {
    public static void sendMessageToSelf(String txt) {
        if (isPlayerInGame()) {
            String m = ClientUtils.reformat("&7[&dR&7]&r " + txt);
            mc.thePlayer.addChatMessage(new ChatComponentText(m));
        }
    }

    public static boolean isPlayerInGame() {
        return mc.thePlayer != null && mc.theWorld != null;
    }

    public static boolean isPlayerMoving() {
        return mc.thePlayer.moveForward != 0.0F || mc.thePlayer.moveStrafing != 0.0F;
    }

    public static boolean isBreakingBlock() {
        return mc.playerController != null && mc.playerController.getIsHittingBlock();
    }
    
    /**
     * Returns the yaw difference from the player to the entity.
     */
    public static double fovFromEntity(Entity entity) {
        double dx = entity.posX - mc.thePlayer.posX;
        double dz = entity.posZ - mc.thePlayer.posZ;
        double angleToEntity = Math.toDegrees(Math.atan2(dz, dx));
        double playerYaw = MathHelper.wrapAngleTo180_double(mc.thePlayer.rotationYaw);
        double diff = MathHelper.wrapAngleTo180_double(angleToEntity - playerYaw);
        return diff;
    }

    /**
     * Returns the yaw angle from the player to the entity.
     */
    public static float fovToEntity(Entity entity) {
        double dx = entity.posX - mc.thePlayer.posX;
        double dz = entity.posZ - mc.thePlayer.posZ;
        return (float) Math.toDegrees(Math.atan2(dz, dx));
    }

    /**
     * Checks if the viewer is looking at the target player within a certain
     * distance.
     */
    public static boolean lookingAtPlayer(EntityPlayer viewer, EntityPlayer target, double maxDistance) {
        double dx = target.posX - viewer.posX;
        double dy = (target.posY + target.getEyeHeight()) - (viewer.posY + viewer.getEyeHeight());
        double dz = target.posZ - viewer.posZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance > maxDistance)
            return false;

        double angleToTarget = Math.toDegrees(Math.atan2(dz, dx));
        double viewerYaw = MathHelper.wrapAngleTo180_double(viewer.rotationYaw);
        double yawDiff = MathHelper.wrapAngleTo180_double(angleToTarget - viewerYaw);

        // Consider within 30 degrees as "looking at"
        return Math.abs(yawDiff) < 30.0;
    }

    public static boolean playerOverAir() {
        double x = mc.thePlayer.posX;
        double y = mc.thePlayer.posY - 1.0D;
        double z = mc.thePlayer.posZ;
        BlockPos p = new BlockPos(MathHelper.floor_double(x), MathHelper.floor_double(y), MathHelper.floor_double(z));
        return mc.theWorld.isAirBlock(p);
    }

    public static boolean isPlayerHoldingWeapon() {
        if (mc.thePlayer.getCurrentEquippedItem() == null) {
            return false;
        } else {
            Item item = mc.thePlayer.getCurrentEquippedItem().getItem();
            return item instanceof ItemSword || item instanceof ItemAxe;
        }
    }

    public static boolean isTeamMate(Entity entity) {
        try {
            EntityPlayer teamMate = (EntityPlayer) entity;
            if (mc.thePlayer.isOnSameTeam(teamMate) ||
                    mc.thePlayer.getDisplayName().getUnformattedText()
                            .startsWith(teamMate.getDisplayName().getUnformattedText().substring(0, 2))
                    ||
                    getNetworkDisplayName()
                            .startsWith(teamMate.getDisplayName().getUnformattedText().substring(0, 2))) {
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public static String getNetworkDisplayName() {
        try {
            NetworkPlayerInfo playerInfo = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
            return ScorePlayerTeam.formatPlayerName(playerInfo.getPlayerTeam(), playerInfo.getGameProfile().getName());
        } catch (Exception ignored) {
        }
        return "";
    }
}
