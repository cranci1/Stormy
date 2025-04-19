package me.sassan.base.impl.module.combact;

import me.sassan.base.api.module.Module;
import me.sassan.base.api.setting.impl.BooleanSetting;
import me.sassan.base.api.setting.impl.SliderSetting;
import me.sassan.base.utils.player.PlayerUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.client.Minecraft;
import net.weavemc.loader.api.event.MouseEvent;
import net.weavemc.loader.api.event.RenderGameOverlayEvent;
import net.weavemc.loader.api.event.SubscribeEvent;
import net.weavemc.loader.api.event.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class AimAssist extends Module {
    // Settings
    private final BooleanSetting lowerSensitivity = new BooleanSetting("Lower Sensitivity", true);
    private final SliderSetting sensitivityAmount = new SliderSetting("Sensitivity %", 60.0, 0.0, 100.0, 1.0);

    private final BooleanSetting dynamicAcceleration = new BooleanSetting("Dynamic Acceleration", true);
    private final SliderSetting accelerationSpeed = new SliderSetting("Acceleration Speed", 1.3, 1.0, 2.0, 0.05);

    private final BooleanSetting insideNudge = new BooleanSetting("Inside Nudge", true);

    private final BooleanSetting aimbot = new BooleanSetting("Aimbot", false);
    private final SliderSetting aimbotSpeed = new SliderSetting("Aimbot Speed", 20.0, 0.0, 90.0, 1.0);
    private final SliderSetting aimbotFOV = new SliderSetting("Aimbot FOV", 80.0, 0.0, 180.0, 5.0);

    private final BooleanSetting flick = new BooleanSetting("Flick", true);
    private final SliderSetting flickThreshold = new SliderSetting("Flick Threshold", 120.0, 50.0, 180.0, 5.0);

    private final BooleanSetting onlyMouseDown = new BooleanSetting("Only Mouse Down", true);
    private final BooleanSetting onlyCombat = new BooleanSetting("Only Combat", true);
    private final BooleanSetting playersOnly = new BooleanSetting("Players Only", true);
    private final SliderSetting range = new SliderSetting("Range", 4.6, 1.0, 6.0, 0.1);

    // State variables
    private float prevDist = 0.0f;
    private float currDist = 0.0f;
    private float[] prevRot = new float[2];
    private boolean wasAccel = false;

    private float originalSensitivity;
    private float yawNudge = 0.0f;
    private float pitchNudge = 0.0f;
    private float prevSpeed = 0.0f;

    private long lastAttackTime = 0L;
    private boolean inCombat = false;
    private Random random = new Random();

    public AimAssist() {
        super("AimAssist", "Subtly assists with aiming", Keyboard.KEY_NONE, Category.COMBAT);

        this.addSetting(lowerSensitivity);
        this.addSetting(sensitivityAmount);
        this.addSetting(dynamicAcceleration);
        this.addSetting(accelerationSpeed);
        this.addSetting(insideNudge);
        this.addSetting(aimbot);
        this.addSetting(aimbotSpeed);
        this.addSetting(aimbotFOV);
        this.addSetting(flick);
        this.addSetting(flickThreshold);
        this.addSetting(onlyMouseDown);
        this.addSetting(onlyCombat);
        this.addSetting(playersOnly);
        this.addSetting(range);
    }

    @Override
    public void onEnable() {
        originalSensitivity = mc.gameSettings.mouseSensitivity;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        // Restore original sensitivity
        mc.gameSettings.mouseSensitivity = originalSensitivity;
        super.onDisable();
    }

    @SubscribeEvent
    public void onRender(RenderGameOverlayEvent event) {
        if (!isEnabled() || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        // Check conditions
        if (onlyMouseDown.getValue() && !Mouse.isButtonDown(0)) {
            return;
        }

        if (onlyCombat.getValue() && !isInCombat()) {
            return;
        }

        // Apply inside nudge if mouse over entity
        if (insideNudge.getValue() && mc.objectMouseOver != null && mc.objectMouseOver.entityHit != null) {
            Entity target = mc.objectMouseOver.entityHit;
            if (isValidTarget(target)) {
                float[] playerRot = new float[] { mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch };
                float[] targetRot = getRotationsToEntity(target);
                float rotDiff = getRotationDifference(playerRot, targetRot);

                float[] nudge = limitRotation(
                        playerRot,
                        targetRot,
                        (float) (30.0 * rotDiff / 3.0 / Minecraft.debugFPS));

                yawNudge = nudge[0] - playerRot[0];
                pitchNudge = nudge[1] - playerRot[1];
            }
        }

        // Apply aimbot if enabled and no direct target
        if (aimbot.getValue() && (mc.objectMouseOver == null || mc.objectMouseOver.entityHit == null)) {
            EntityLivingBase target = getClosestTarget();
            if (target != null) {
                float[] playerRot = new float[] { mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch };
                float[] targetRot = getRotationsToEntity(target);
                float rotDiff = getRotationDifference(playerRot, targetRot);

                if (rotDiff < aimbotFOV.getValue()) {
                    // Calculate smooth speed with randomization
                    prevSpeed = (2 * prevSpeed + (float) (20.0 * getRandomInRange(
                            aimbotSpeed.getValue() * 0.9,
                            aimbotSpeed.getValue() * 1.1) / Minecraft.debugFPS)) / 3;

                    float[] nudge = limitRotation(playerRot, targetRot, prevSpeed);

                    yawNudge = nudge[0] - playerRot[0];
                    pitchNudge = nudge[1] - playerRot[1];
                } else {
                    prevSpeed = 0f;
                }
            } else {
                prevSpeed = 0f;
            }
        }

        // Apply flick if enabled
        if (flick.getValue()) {
            EntityLivingBase target = getClosestTarget();
            if (target != null) {
                float[] playerRot = new float[] { mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch };
                float[] targetRot = getRotationsToEntity(target);
                float rotDiff = getRotationDifference(playerRot, targetRot);

                if (rotDiff > flickThreshold.getValue()) {
                    float[] nudge = limitRotation(
                            playerRot,
                            targetRot,
                            (float) (flickThreshold.getValue() * getRandomInRange(0.9, 1.1)));

                    yawNudge += nudge[0] - playerRot[0];
                    pitchNudge += nudge[1] - playerRot[1];
                }
            }
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent event) {
        if (!isEnabled() || mc.thePlayer == null) {
            return;
        }

        // Update combat status
        if (System.currentTimeMillis() - lastAttackTime < 3000) {
            inCombat = true;
        } else {
            inCombat = false;
        }

        // Check conditions
        if (onlyMouseDown.getValue() && !Mouse.isButtonDown(0)) {
            mc.gameSettings.mouseSensitivity = originalSensitivity;
            return;
        }

        if (onlyCombat.getValue() && !isInCombat()) {
            mc.gameSettings.mouseSensitivity = originalSensitivity;
            return;
        }

        // Apply sensitivity modification
        float targetSensitivity = originalSensitivity;

        // Lower sensitivity when targeting
        if (lowerSensitivity.getValue() && mc.objectMouseOver != null && mc.objectMouseOver.entityHit != null) {
            if (isValidTarget(mc.objectMouseOver.entityHit)) {
                targetSensitivity = originalSensitivity * (float) (sensitivityAmount.getValue() / 100.0);
            }
        }

        // Apply dynamic acceleration
        if (dynamicAcceleration.getValue()) {
            if (mc.objectMouseOver == null || mc.objectMouseOver.entityHit == null) {
                EntityLivingBase target = getClosestTarget();
                if (target != null) {
                    if (wasAccel) {
                        prevDist = currDist;
                        currDist = getRotationDifference(getRotationsToEntity(target));
                        float rotDiff = getRotationDifference(prevRot);

                        if (rotDiff * 0.6 < prevDist - currDist && currDist < 120) {
                            targetSensitivity = originalSensitivity * accelerationSpeed.getValue().floatValue();
                        }

                        prevRot = new float[] { mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch };
                    } else {
                        prevRot = new float[] { mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch };
                        prevDist = getRotationDifference(getRotationsToEntity(target));
                        currDist = prevDist;
                        wasAccel = true;
                    }
                } else {
                    targetSensitivity = originalSensitivity;
                    wasAccel = false;
                }
            }
        }

        // Apply the calculated sensitivity
        mc.gameSettings.mouseSensitivity = targetSensitivity;
    }

    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        if (!isEnabled() || mc.thePlayer == null) {
            return;
        }

        // Apply nudges here
        if (yawNudge != 0 || pitchNudge != 0) {
            float yaw = mc.thePlayer.rotationYaw + yawNudge;
            float pitch = mc.thePlayer.rotationPitch + pitchNudge;

            // Set the new rotation values
            mc.thePlayer.rotationYaw = yaw;
            mc.thePlayer.rotationPitch = pitch;

            // Reset nudges after applied
            yawNudge = 0;
            pitchNudge = 0;
        }

        // Track attacks to detect combat state
        if (event.getButton() == 0 && event.getButtonState()) {
            lastAttackTime = System.currentTimeMillis();
        }
    }

    // Helper methods

    private boolean isInCombat() {
        return inCombat;
    }

    private boolean isValidTarget(Entity entity) {
        if (entity == null)
            return false;
        if (!(entity instanceof EntityLivingBase))
            return false;
        if (entity == mc.thePlayer)
            return false;
        if (playersOnly.getValue() && !(entity instanceof EntityPlayer))
            return false;
        if (entity.isDead)
            return false;

        return mc.thePlayer.getDistanceToEntity(entity) <= range.getValue();
    }

    private EntityLivingBase getClosestTarget() {
        List<Entity> entities = mc.theWorld.loadedEntityList;

        return entities.stream()
                .filter(this::isValidTarget)
                .map(entity -> (EntityLivingBase) entity)
                .min(Comparator.comparing(entity -> {
                    float[] rotations = getRotationsToEntity(entity);
                    return (double) getRotationDifference(rotations);
                }))
                .orElse(null);
    }

    private float[] getRotationsToEntity(Entity entity) {
        double x = entity.posX - mc.thePlayer.posX;
        double z = entity.posZ - mc.thePlayer.posZ;

        // Aim at the middle of the entity
        double y = entity.posY + entity.getEyeHeight() - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());

        double dist = Math.sqrt(x * x + z * z);
        float yaw = (float) Math.toDegrees(Math.atan2(z, x)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(y, dist));

        return new float[] { yaw, pitch };
    }

    private float getRotationDifference(float[] rotation) {
        float[] playerRotation = { mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch };
        return getRotationDifference(playerRotation, rotation);
    }

    private float getRotationDifference(float[] from, float[] to) {
        float yawDiff = Math.abs(wrapAngleTo180_float(from[0] - to[0]));
        float pitchDiff = Math.abs(from[1] - to[1]);

        return yawDiff + pitchDiff;
    }

    private float[] limitRotation(float[] from, float[] to, float limit) {
        float yawDiff = wrapAngleTo180_float(to[0] - from[0]);
        float pitchDiff = to[1] - from[1];

        float limitedYaw;
        float limitedPitch;

        if (Math.abs(yawDiff) > limit) {
            limitedYaw = from[0] + (yawDiff > 0 ? limit : -limit);
        } else {
            limitedYaw = to[0];
        }

        if (Math.abs(pitchDiff) > limit) {
            limitedPitch = from[1] + (pitchDiff > 0 ? limit : -limit);
        } else {
            limitedPitch = to[1];
        }

        // Ensure pitch stays within valid range
        limitedPitch = MathHelper.clamp_float(limitedPitch, -90.0F, 90.0F);

        return new float[] { limitedYaw, limitedPitch };
    }

    private float wrapAngleTo180_float(float angle) {
        angle %= 360.0F;
        if (angle >= 180.0F) {
            angle -= 360.0F;
        }
        if (angle < -180.0F) {
            angle += 360.0F;
        }
        return angle;
    }

    private double getRandomInRange(double min, double max) {
        return min + (max - min) * random.nextDouble();
    }
}