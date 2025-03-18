package dev.stormy.client.module.modules.combat;

import dev.stormy.client.main.Stormy;
import dev.stormy.client.module.Module;
import dev.stormy.client.module.setting.impl.DescriptionSetting;
import dev.stormy.client.module.setting.impl.SliderSetting;
import dev.stormy.client.module.setting.impl.TickSetting;
import dev.stormy.client.utils.player.PlayerUtils;
import dev.stormy.client.utils.Utils;
import dev.stormy.client.module.modules.client.AntiBot;
import me.tryfle.stormy.events.LivingUpdateEvent;
import net.minecraft.block.BlockLiquid;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.weavemc.loader.api.event.SubscribeEvent;
import org.lwjgl.input.Mouse;

import java.util.Random;

public class AimAssist extends Module {
    public static SliderSetting speed, fov, distance, smoothing, randomization;
    public static TickSetting clickAim, weaponOnly, aimInvis, breakBlocks, ignoreTeammates, humanizeMovement;
    public boolean breakHeld = false;
    private final Random random = new Random();
    private double targetYaw = 0;
    private double lastAimTime = 0;

    public AimAssist() {
        super("AimAssist", ModuleCategory.Combat, 0);
        this.registerSetting(new DescriptionSetting("Aims at enemies."));
        this.registerSetting(speed = new SliderSetting("Speed", 45.0D, 1.0D, 100.0D, 1.0D));
        this.registerSetting(fov = new SliderSetting("FOV", 90.0D, 15.0D, 180.0D, 1.0D));
        this.registerSetting(distance = new SliderSetting("Distance", 3.5D, 1.0D, 10.0D, 0.5D));
        this.registerSetting(smoothing = new SliderSetting("Smoothing", 2.0D, 1.0D, 10.0D, 0.1D));
        this.registerSetting(randomization = new SliderSetting("Randomization", 1.5D, 0.0D, 5.0D, 0.1D));
        this.registerSetting(clickAim = new TickSetting("Clicking only", true));
        this.registerSetting(weaponOnly = new TickSetting("Weapon only", false));
        this.registerSetting(aimInvis = new TickSetting("Aim at invis", false));
        this.registerSetting(breakBlocks = new TickSetting("Break Blocks", true));
        this.registerSetting(ignoreTeammates = new TickSetting("Ignore teammates", true));
        this.registerSetting(humanizeMovement = new TickSetting("Humanize movement", true));
    }

    @SubscribeEvent
    public void onUpdateCenter(LivingUpdateEvent e) {
        if (mc.thePlayer == null
                || mc.currentScreen != null
                || !mc.inGameHasFocus
                || (weaponOnly.isToggled() && !PlayerUtils.isPlayerHoldingWeapon())
                || (breakBlocks.isToggled() && breakBlock()))
            return;

        if (!clickAim.isToggled() ||
                (Stormy.moduleManager.getModuleByClazz(AutoClicker.class).isEnabled() && Mouse.isButtonDown(0)) ||
                Mouse.isButtonDown(0)) {
            Entity en = this.getEnemy();
            if (en != null) {
                double currentTime = System.currentTimeMillis();
                double timeDelta = currentTime - lastAimTime;
                lastAimTime = currentTime;

                // Calculate ideal aim adjustment
                double n = calculateYawDifference(en);

                if (Math.abs(n) > 0.1D) {
                    // Target yaw to reach
                    targetYaw = n;

                    // Apply smoothing with randomization
                    double speedFactor = speed.getInput() / 100.0D;
                    double smoothFactor = 11.0D - smoothing.getInput();

                    // Calculate base adjustment
                    double adjustment = -(n / (smoothFactor / speedFactor));

                    // Add humanized movement
                    if (humanizeMovement.isToggled()) {
                        // Add random micro-movements
                        double randomFactor = randomization.getInput() * 0.01D;
                        double randomAdjustment = (random.nextDouble() - 0.5D) * randomFactor;

                        // Adjust speed based on angle difference (slower when close to target)
                        double proximityFactor = Math.min(1.0D, Math.abs(n) / 45.0D);
                        adjustment *= 0.2D + (0.8D * proximityFactor);

                        // Add small delay variation
                        if (random.nextInt(10) == 0) {
                            adjustment *= 0.8D;
                        }

                        // Add the randomization
                        adjustment += randomAdjustment;
                    }

                    // Apply the rotation
                    mc.thePlayer.rotationYaw += (float) adjustment;

                    // Normalize the rotation
                    mc.thePlayer.rotationYaw = MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw);
                }
            }
        }
    }

    private Entity getEnemy() {
        final int n = (int) fov.getInput();
        EntityPlayer closestEntity = null;
        double closestDistance = Double.MAX_VALUE;

        for (final EntityPlayer entityPlayer : mc.theWorld.playerEntities) {
            if (!isValidTarget(entityPlayer))
                continue;

            double dist = mc.thePlayer.getDistanceToEntity(entityPlayer);
            if (dist < closestDistance) {
                closestDistance = dist;
                closestEntity = entityPlayer;
            }
        }

        return closestEntity;
    }

    private boolean isValidTarget(EntityPlayer entityPlayer) {
        if (entityPlayer == mc.thePlayer || entityPlayer.deathTime != 0)
            return false;
        if (ignoreTeammates.isToggled() && PlayerUtils.isTeamMate(entityPlayer))
            return false;
        if (!aimInvis.isToggled() && entityPlayer.isInvisible())
            return false;
        if (mc.thePlayer.getDistanceToEntity(entityPlayer) > distance.getInput())
            return false;
        if (AntiBot.bot(entityPlayer))
            return false;

        int fovValue = (int) fov.getInput();
        if (fovValue != 360 && !Utils.inFov((float) fovValue, entityPlayer))
            return false;

        return true;
    }

    public static boolean fov(Entity entity, float fov) {
        fov = (float) ((double) fov * 0.5D);
        double v = ((double) (mc.thePlayer.rotationYaw - calculateTargetYaw(entity)) % 360.0D + 540.0D) % 360.0D
                - 180.0D;
        return v > 0.0D && v < (double) fov || (double) (-fov) < v && v < 0.0D;
    }

    public static double calculateYawDifference(Entity en) {
        return ((double) (mc.thePlayer.rotationYaw - calculateTargetYaw(en)) % 360.0D + 540.0D) % 360.0D - 180.0D;
    }

    public static float calculateTargetYaw(Entity ent) {
        double x = ent.posX - mc.thePlayer.posX;
        double z = ent.posZ - mc.thePlayer.posZ;
        double yaw = Math.atan2(x, z) * 57.2957795D;
        return (float) (yaw * -1.0D);
    }

    public boolean breakBlock() {
        if (breakBlocks.isToggled() && mc.objectMouseOver != null) {
            BlockPos p = mc.objectMouseOver.getBlockPos();
            if (p != null && Mouse.isButtonDown(0)) {
                if (mc.theWorld.getBlockState(p).getBlock() != Blocks.air
                        && !(mc.theWorld.getBlockState(p).getBlock() instanceof BlockLiquid)) {
                    if (!breakHeld) {
                        int e = mc.gameSettings.keyBindAttack.getKeyCode();
                        KeyBinding.setKeyBindState(e, true);
                        KeyBinding.onTick(e);
                        breakHeld = true;
                    }
                    return true;
                }
                if (breakHeld) {
                    breakHeld = false;
                }
            }
        }
        return false;
    }
}