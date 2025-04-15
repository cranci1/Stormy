package dev.stormy.client.module.modules.combat;

import dev.stormy.client.module.Module;
import dev.stormy.client.module.setting.impl.DescriptionSetting;
import dev.stormy.client.module.setting.impl.SliderSetting;
import dev.stormy.client.module.setting.impl.TickSetting;
import dev.stormy.client.module.modules.client.AntiBot;
import me.tryfle.stormy.events.LivingUpdateEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.weavemc.loader.api.event.SubscribeEvent;
import org.lwjgl.input.Mouse;

public class AimAssist extends Module {
    private SliderSetting speed, fov, distance;
    private TickSetting clickAim, weaponOnly, aimInvis, blatantMode, ignoreTeammates;

    public AimAssist() {
        super("AimAssist", ModuleCategory.Combat, 0);
        this.registerSetting(new DescriptionSetting("Aims at enemies."));
        this.registerSetting(speed = new SliderSetting("Speed", 45.0D, 1.0D, 100.0D, 1.0D));
        this.registerSetting(fov = new SliderSetting("FOV", 90.0D, 15.0D, 180.0D, 1.0D));
        this.registerSetting(distance = new SliderSetting("Distance", 4.5D, 1.0D, 10.0D, 0.5D));
        this.registerSetting(clickAim = new TickSetting("Click aim", true));
        this.registerSetting(weaponOnly = new TickSetting("Weapon only", false));
        this.registerSetting(aimInvis = new TickSetting("Aim at invis", false));
        this.registerSetting(blatantMode = new TickSetting("Blatant mode", false));
        this.registerSetting(ignoreTeammates = new TickSetting("Ignore teammates", false));
    }

    @SubscribeEvent
    public void onUpdate(LivingUpdateEvent e) {
        if (mc.thePlayer == null || mc.currentScreen != null || !mc.inGameHasFocus)
            return;
        if (mc.objectMouseOver != null
                && mc.objectMouseOver.typeOfHit == net.minecraft.util.MovingObjectPosition.MovingObjectType.BLOCK
                && mc.gameSettings.keyBindAttack.isKeyDown())
            return;
        if (weaponOnly.isToggled() && !isPlayerHoldingWeapon())
            return;
        if (clickAim.isToggled() && !Mouse.isButtonDown(0))
            return;

        Entity en = getEnemy();
        if (en != null) {
            if (blatantMode.isToggled()) {
                aimDirect(en);
            } else {
                double yawDiff = getYawDifference(en);
                if (Math.abs(yawDiff) > 1.0D) {
                    float val = (float) (-(yawDiff / (101.0D - speed.getInput())));
                    mc.thePlayer.rotationYaw += val;
                    mc.thePlayer.rotationYaw = MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw);
                }
            }
        }
    }

    private Entity getEnemy() {
        final int fovValue = (int) fov.getInput();
        EntityPlayer closest = null;
        double closestDist = Double.MAX_VALUE;

        for (final EntityPlayer entityPlayer : mc.theWorld.playerEntities) {
            if (!isValidTarget(entityPlayer, fovValue))
                continue;
            double dist = mc.thePlayer.getDistanceToEntity(entityPlayer);
            if (dist < closestDist) {
                closestDist = dist;
                closest = entityPlayer;
            }
        }
        return closest;
    }

    private boolean isValidTarget(EntityPlayer entityPlayer, int fovValue) {
        if (entityPlayer == mc.thePlayer || entityPlayer.deathTime != 0)
            return false;
        if (ignoreTeammates.isToggled() && isTeamMate(entityPlayer))
            return false;
        if (!aimInvis.isToggled() && entityPlayer.isInvisible())
            return false;
        if (mc.thePlayer.getDistanceToEntity(entityPlayer) > distance.getInput())
            return false;
        if (AntiBot.bot(entityPlayer))
            return false;
        if (!blatantMode.isToggled() && fovValue != 360 && !inFov(fovValue, entityPlayer))
            return false;
        return true;
    }

    private boolean isPlayerHoldingWeapon() {
        if (mc.thePlayer == null || mc.thePlayer.getHeldItem() == null)
            return false;
        String itemName = mc.thePlayer.getHeldItem().getDisplayName().toLowerCase();
        return itemName.contains("sword") || itemName.contains("axe") || itemName.contains("bow");
    }

    private boolean isTeamMate(EntityPlayer other) {
        if (mc.thePlayer.getDisplayName() == null || other.getDisplayName() == null)
            return false;
        String myName = mc.thePlayer.getDisplayName().getFormattedText();
        String otherName = other.getDisplayName().getFormattedText();
        // Compare color code prefix
        return myName.length() > 2 && otherName.length() > 2
                && myName.substring(0, 2).equals(otherName.substring(0, 2));
    }

    private boolean inFov(int fov, Entity target) {
        float halfFov = fov / 2.0F;
        double yawToTarget = getYawToEntity(target);
        double diff = MathHelper.wrapAngleTo180_double(yawToTarget - mc.thePlayer.rotationYaw);
        return diff >= -halfFov && diff <= halfFov;
    }

    private double getYawToEntity(Entity entity) {
        double dx = entity.posX - mc.thePlayer.posX;
        double dz = entity.posZ - mc.thePlayer.posZ;
        double yaw = Math.atan2(dx, dz) * (180.0D / Math.PI);
        return -yaw;
    }

    private double getYawDifference(Entity entity) {
        double yawToTarget = getYawToEntity(entity);
        double diff = MathHelper.wrapAngleTo180_double(mc.thePlayer.rotationYaw - yawToTarget);
        return diff;
    }

    private void aimDirect(Entity entity) {
        double yawToTarget = getYawToEntity(entity);
        mc.thePlayer.rotationYaw = (float) yawToTarget;
    }
}