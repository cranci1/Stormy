package me.sassan.base.impl.module.combact;

import me.sassan.base.api.module.Module;
import me.sassan.base.api.setting.impl.BooleanSetting;
import me.sassan.base.api.setting.impl.DoubleSliderSetting;
import me.sassan.base.utils.player.PlayerUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.init.Blocks;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.weavemc.loader.api.event.MouseEvent;
import net.weavemc.loader.api.event.SubscribeEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.List;
import java.util.Random;

public class Reach extends Module {
    // Settings
    private final DoubleSliderSetting reachDistance;
    private final BooleanSetting movingOnly;
    private final BooleanSetting sprintOnly;
    private final BooleanSetting hitThroughBlocks;

    private final Random random = new Random();

    public Reach() {
        super("Reach", "Increases your attack reach", Keyboard.KEY_NONE, Category.COMBAT);

        this.reachDistance = new DoubleSliderSetting("Reach", 3.07, 3.10, 3.0, 3.5, 0.01);
        this.movingOnly = new BooleanSetting("Moving only", false);
        this.sprintOnly = new BooleanSetting("Sprint only", false);
        this.hitThroughBlocks = new BooleanSetting("Hit through blocks", false);

        this.addSetting(reachDistance);
        this.addSetting(movingOnly);
        this.addSetting(sprintOnly);
        this.addSetting(hitThroughBlocks);
    }

    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        if (PlayerUtils.isPlayerInGame() && Mouse.isButtonDown(0)) {
            applyReach();
        }
    }

    /**
     * Applies the reach effect by finding entities within the configured reach
     * distance
     * and updating the game's target entity
     */
    private boolean applyReach() {
        if (!isEnabled() || !PlayerUtils.isPlayerInGame()) {
            return false;
        }

        // Check conditions
        if (movingOnly.getValue() && mc.thePlayer.moveForward == 0.0f && mc.thePlayer.moveStrafing == 0.0f) {
            return false;
        }

        if (sprintOnly.getValue() && !mc.thePlayer.isSprinting()) {
            return false;
        }

        // Check if we're trying to hit through blocks
        if (!hitThroughBlocks.getValue() && mc.objectMouseOver != null) {
            BlockPos pos = mc.objectMouseOver.getBlockPos();
            if (pos != null && mc.theWorld.getBlockState(pos).getBlock() != Blocks.air) {
                return false;
            }
        }

        // Calculate reach distance from the slider setting
        double reach = getRandomValueFromRange(reachDistance.getMinValue(), reachDistance.getMaxValue());

        // Find entities within reach
        Object[] result = findEntitiesWithinReach(reach);
        if (result == null) {
            return false;
        }

        // Update the game's target
        Entity entity = (Entity) result[0];
        mc.objectMouseOver = new MovingObjectPosition(entity, (Vec3) result[1]);
        mc.pointedEntity = entity;

        return true;
    }

    /**
     * Finds all entities within the specified reach distance
     */
    private Object[] findEntitiesWithinReach(double reach) {
        // Get the player's view
        Entity renderView = mc.getRenderViewEntity();
        if (renderView == null) {
            return null;
        }

        // Start profiling section
        mc.mcProfiler.startSection("pick");

        // Get eye position and look vector
        Vec3 eyePosition = renderView.getPositionEyes(1.0F);
        Vec3 playerLook = renderView.getLook(1.0F);
        Vec3 reachTarget = eyePosition.addVector(
                playerLook.xCoord * reach,
                playerLook.yCoord * reach,
                playerLook.zCoord * reach);

        // Initialize variables
        Entity target = null;
        Vec3 targetHitVec = null;
        double adjustedReach = reach;

        // Get all entities within the player's reach
        List<Entity> entitiesWithinReach = mc.theWorld.getEntitiesWithinAABBExcludingEntity(
                renderView,
                renderView.getEntityBoundingBox().addCoord(
                        playerLook.xCoord * reach,
                        playerLook.yCoord * reach,
                        playerLook.zCoord * reach).expand(1.0D, 1.0D, 1.0D));

        // Check each entity
        for (Entity entity : entitiesWithinReach) {
            if (entity.canBeCollidedWith()) {
                float borderSize = entity.getCollisionBorderSize();
                AxisAlignedBB entityBoundingBox = entity.getEntityBoundingBox().expand(borderSize, borderSize,
                        borderSize);
                MovingObjectPosition intercept = entityBoundingBox.calculateIntercept(eyePosition, reachTarget);

                // Check if eye position is inside the entity
                if (entityBoundingBox.isVecInside(eyePosition)) {
                    if (0.0D < adjustedReach || adjustedReach == 0.0D) {
                        target = entity;
                        targetHitVec = intercept == null ? eyePosition : intercept.hitVec;
                        adjustedReach = 0.0D;
                    }
                }
                // Check intersection with entity
                else if (intercept != null) {
                    double distanceToVec = eyePosition.distanceTo(intercept.hitVec);
                    if (distanceToVec < adjustedReach || adjustedReach == 0.0D) {
                        // Special handling for riding entities
                        if (entity == renderView.ridingEntity) {
                            if (adjustedReach == 0.0D) {
                                target = entity;
                                targetHitVec = intercept.hitVec;
                            }
                        } else {
                            target = entity;
                            targetHitVec = intercept.hitVec;
                            adjustedReach = distanceToVec;
                        }
                    }
                }
            }
        }

        // Only accept living entities or item frames if they're within the configured
        // reach
        if (adjustedReach < reach && !(target instanceof EntityLivingBase) && !(target instanceof EntityItemFrame)) {
            target = null;
        }

        mc.mcProfiler.endSection();

        // Return the target and hit vector if found
        if (target != null && targetHitVec != null) {
            return new Object[] { target, targetHitVec };
        }

        return null;
    }

    /**
     * Gets a random value between min and max
     */
    private double getRandomValueFromRange(double min, double max) {
        return min == max ? min : min + random.nextDouble() * (max - min);
    }
}
