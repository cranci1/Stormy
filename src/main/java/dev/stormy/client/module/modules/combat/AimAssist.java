package dev.stormy.client.module.modules.combat;

import dev.stormy.client.module.Module;
import dev.stormy.client.module.setting.impl.*;
import dev.stormy.client.utils.player.PlayerUtils;
import me.tryfle.stormy.events.LivingUpdateEvent;
import me.tryfle.stormy.events.RenderEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.weavemc.loader.api.event.SubscribeEvent;
import org.lwjgl.input.Mouse;

@SuppressWarnings("unused")
public class AimAssist extends Module {
    // Settings
    public static TickSetting lowerSens, accelSens, insideNudge, aimbot, flick, onMouse, onlyCombat;
    public static SliderSetting lowerSensAmount, accelAmount, aimbotSpeed, aimbotFOV, flickThreshold;
    // Internal state
    private float prevDist = 0f, currDist = 0f;
    private float[] prevRot = null;
    private boolean wasAccel = false;
    private float sens, gameSens;
    public float yawNudge = 0f, pitchNudge = 0f;
    private float prevSpeed = 0f;

    public AimAssist() {
        super("AimAssist", ModuleCategory.Combat, 0);
        this.registerSetting(lowerSens = new TickSetting("Lower Sensitivity On Target", true));
        this.registerSetting(lowerSensAmount = new SliderSetting("Lowered Sensitivity %", 0.6, 0, 1, 0.05));
        this.registerSetting(accelSens = new TickSetting("Dynamic Acceleration", true));
        this.registerSetting(accelAmount = new SliderSetting("Acceleration Speed", 1.3, 1, 2, 0.05));
        this.registerSetting(insideNudge = new TickSetting("Inside Nudge", true));
        this.registerSetting(aimbot = new TickSetting("AimBot", false));
        this.registerSetting(aimbotSpeed = new SliderSetting("AimBot Speed", 20, 0, 90, 2));
        this.registerSetting(aimbotFOV = new SliderSetting("AimBot FOV", 80, 0, 180, 5));
        this.registerSetting(flick = new TickSetting("Flick", true));
        this.registerSetting(flickThreshold = new SliderSetting("Flick Threshold", 120, 50, 180, 5));
        this.registerSetting(onMouse = new TickSetting("Only Mouse Down", true));
        this.registerSetting(onlyCombat = new TickSetting("Only Combat", true));
    }

    @SubscribeEvent
    public void onRender2D(RenderEvent event) {
        if (event.state != RenderEvent.State.RENDER_2D)
            return;
        if (onMouse.isToggled() && !mc.gameSettings.keyBindAttack.isKeyDown())
            return;
        if (onlyCombat.isToggled() && !isInCombat())
            return;

        yawNudge = 0f;
        pitchNudge = 0f;

        if (mc.objectMouseOver != null && mc.objectMouseOver.entityHit != null) {
            if (insideNudge.isToggled()) {
                float[] nudge = getLimitedRotation(
                        getPlayerRotation(),
                        getTargetRotations(mc.objectMouseOver.entityHit.getEntityBoundingBox(), TargetRotation.OPTIMAL,
                                0.01),
                        (float) (30 * getRotationDifference(getTargetRotations(
                                mc.objectMouseOver.entityHit.getEntityBoundingBox(), TargetRotation.MIDDLE, 0.01)) / 3
                                / getFPS()));
                yawNudge = nudge[0] - getPlayerRotation()[0];
                pitchNudge = nudge[1] - getPlayerRotation()[1];
            }
        }

        if ((mc.objectMouseOver == null || mc.objectMouseOver.entityHit == null) && aimbot.isToggled()) {
            EntityLivingBase target = getTarget(4.6, "FOV");
            if (target != null) {
                if (getRotationDifference(getTargetRotations(target.getEntityBoundingBox(), TargetRotation.MIDDLE,
                        0.01)) < aimbotFOV.getInput()) {
                    prevSpeed = (2 * prevSpeed + (float) (20
                            * randomInRange(aimbotSpeed.getInput() * 0.9f, aimbotSpeed.getInput() * 1.1f) / getFPS()))
                            / 3;
                    float[] nudge = getLimitedRotation(
                            getPlayerRotation(),
                            getTargetRotations(target.getEntityBoundingBox(), TargetRotation.MIDDLE, 0.01),
                            prevSpeed);
                    yawNudge = nudge[0] - getPlayerRotation()[0];
                    pitchNudge = nudge[1] - getPlayerRotation()[1];
                } else {
                    prevSpeed = 0f;
                }
            } else {
                prevSpeed = 0f;
            }
        } else {
            prevSpeed = 0f;
        }

        if (flick.isToggled()) {
            EntityLivingBase target = getTarget(4.6, "FOV");
            if (target != null) {
                if (getRotationDifference(getTargetRotations(target.getEntityBoundingBox(), TargetRotation.CENTER,
                        0.01)) > flickThreshold.getInput()) {
                    float[] nudge = getLimitedRotation(
                            getPlayerRotation(),
                            getTargetRotations(target.getEntityBoundingBox(), TargetRotation.MIDDLE, 0.01),
                            (float) (flickThreshold.getInput() * randomInRange(0.9, 1.1)));
                    yawNudge += nudge[0] - getPlayerRotation()[0];
                    pitchNudge += nudge[1] - getPlayerRotation()[1];
                }
            }
        }

        // Apply nudges to player rotation in render event
        if (yawNudge != 0f || pitchNudge != 0f) {
            mc.thePlayer.rotationYaw += yawNudge;
            mc.thePlayer.rotationPitch += pitchNudge;
            if (mc.thePlayer.rotationPitch > 90)
                mc.thePlayer.rotationPitch = 90;
            if (mc.thePlayer.rotationPitch < -90)
                mc.thePlayer.rotationPitch = -90;
        }
    }

    @SubscribeEvent
    public void onUpdate(LivingUpdateEvent event) {
        gameSens = (float) mc.gameSettings.mouseSensitivity;
        sens = gameSens;

        if (onMouse.isToggled() && !mc.gameSettings.keyBindAttack.isKeyDown())
            return;
        if (onlyCombat.isToggled() && !isInCombat())
            return;

        // Sensitivity logic only
        if (mc.objectMouseOver != null && mc.objectMouseOver.entityHit != null) {
            if (lowerSens.isToggled()) {
                sens = gameSens * (float) lowerSensAmount.getInput();
            }
        }
        if (accelSens.isToggled()) {
            if (mc.objectMouseOver == null || mc.objectMouseOver.entityHit == null) {
                EntityLivingBase target = getTarget(4.6, "FOV");
                if (target != null) {
                    if (wasAccel) {
                        prevDist = currDist;
                        currDist = (float) getRotationDifference(target);
                        if (getRotationDifference(prevRot) * 0.6 < prevDist - currDist && currDist < 120) {
                            sens = gameSens * (float) accelAmount.getInput();
                        } else {
                            sens = gameSens;
                        }
                        prevRot = new float[] { mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch };
                    } else {
                        prevRot = new float[] { mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch };
                        prevDist = (float) getRotationDifference(target);
                        currDist = prevDist;
                        wasAccel = true;
                    }
                } else {
                    sens = gameSens;
                    wasAccel = false;
                }
            }
        }
    }

    // --- Utility methods (adapted from RotationUtil) ---

    public enum TargetRotation {
        EDGE, CENTER, OPTIMAL, MIDDLE, TOPHALF, INNER, DRIFT
    }

    public float getSens() {
        if (isEnabled()) {
            return sens;
        } else {
            return (float) mc.gameSettings.mouseSensitivity;
        }
    }

    private boolean isInCombat() {
        // You may want to hook this to your own combat logic
        // For now, return true if mouse is down or player is swinging
        return Mouse.isButtonDown(0) || mc.thePlayer.isSwingInProgress;
    }

    private int getFPS() {
        // Use Minecraft's debug FPS
        return net.minecraft.client.Minecraft.getDebugFPS();
    }

    private float randomInRange(double min, double max) {
        return (float) (min + Math.random() * (max - min));
    }

    private float[] getPlayerRotation() {
        return new float[] { mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch };
    }

    private float[] getTargetRotations(AxisAlignedBB aabb, TargetRotation mode, double random) {
        // Only implement CENTER, MIDDLE, OPTIMAL for simplicity
        double x = (aabb.minX + aabb.maxX) / 2;
        double y = (aabb.minY + aabb.maxY) / 2;
        double z = (aabb.minZ + aabb.maxZ) / 2;
        if (mode == TargetRotation.CENTER || mode == TargetRotation.OPTIMAL) {
            // Center of bounding box
        } else if (mode == TargetRotation.MIDDLE) {
            x = aabb.minX + (aabb.maxX - aabb.minX) * 0.5;
            y = aabb.minY + (aabb.maxY - aabb.minY) * 0.5;
            z = aabb.minZ + (aabb.maxZ - aabb.minZ) * 0.5;
        }
        // Add randomization
        x += (Math.random() - 0.5) * random;
        y += (Math.random() - 0.5) * random;
        z += (Math.random() - 0.5) * random;
        return getRotations(x, y, z);
    }

    private float[] getRotations(double x, double y, double z) {
        double px = mc.thePlayer.posX;
        double py = mc.thePlayer.posY + mc.thePlayer.getEyeHeight();
        double pz = mc.thePlayer.posZ;
        double dx = x - px;
        double dy = y - py;
        double dz = z - pz;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0F);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, dist));
        return new float[] { yaw, pitch };
    }

    private float[] getLimitedRotation(float[] from, float[] to, float speed) {
        double yawDif = MathHelper.wrapAngleTo180_double(to[0] - from[0]);
        double pitchDif = MathHelper.wrapAngleTo180_double(to[1] - from[1]);
        double rotDif = Math.sqrt(yawDif * yawDif + pitchDif * pitchDif);
        if (rotDif == 0)
            return from;
        double yawLimit = Math.abs(yawDif * speed / rotDif);
        double pitchLimit = Math.abs(pitchDif * speed / rotDif);
        return new float[] {
                updateRots(from[0], to[0], (float) yawLimit),
                updateRots(from[1], to[1], (float) pitchLimit)
        };
    }

    private float updateRots(float from, float to, float speed) {
        float f = MathHelper.wrapAngleTo180_float(to - from);
        if (f > speed)
            f = speed;
        if (f < -speed)
            f = -speed;
        return from + f;
    }

    private double getRotationDifference(float[] rot) {
        float[] playerRot = getPlayerRotation();
        double yawDif = MathHelper.wrapAngleTo180_double(playerRot[0] - rot[0]);
        double pitchDif = MathHelper.wrapAngleTo180_double(playerRot[1] - rot[1]);
        return Math.sqrt(yawDif * yawDif + pitchDif * pitchDif);
    }

    private double getRotationDifference(Entity entity) {
        float[] rot = getRotations(entity.posX, entity.posY + entity.getEyeHeight(), entity.posZ);
        return getRotationDifference(rot);
    }

    private double getRotationDifference(float[] a, float[] b) {
        double yawDif = MathHelper.wrapAngleTo180_double(a[0] - b[0]);
        double pitchDif = MathHelper.wrapAngleTo180_double(a[1] - b[1]);
        return Math.sqrt(yawDif * yawDif + pitchDif * pitchDif);
    }

    private EntityLivingBase getTarget(double range, String sort) {
        EntityLivingBase best = null;
        double bestVal = Double.MAX_VALUE;
        for (Object obj : mc.theWorld.playerEntities) {
            if (!(obj instanceof EntityLivingBase))
                continue;
            EntityLivingBase en = (EntityLivingBase) obj;
            if (en == mc.thePlayer || en.isDead || en.deathTime > 0)
                continue;
            if (mc.thePlayer.getDistanceToEntity(en) > range)
                continue;
            double val = 0;
            if ("fov".equalsIgnoreCase(sort)) {
                val = getRotationDifference(en);
            } else if ("distance".equalsIgnoreCase(sort)) {
                val = mc.thePlayer.getDistanceToEntity(en);
            } else if ("health".equalsIgnoreCase(sort)) {
                val = en.getHealth();
            }
            if (val < bestVal) {
                bestVal = val;
                best = en;
            }
        }
        return best;
    }
}