package dev.stormy.client.module.modules.combat;

import dev.stormy.client.clickgui.Theme;
import dev.stormy.client.module.Module;
import dev.stormy.client.module.setting.impl.DescriptionSetting;
import dev.stormy.client.module.setting.impl.SliderSetting;
import dev.stormy.client.module.setting.impl.TickSetting;
import dev.stormy.client.module.setting.impl.ComboSetting;
import dev.stormy.client.utils.math.TimerUtils;
import dev.stormy.client.utils.Utils;
import dev.stormy.client.utils.player.PlayerUtils;
import dev.stormy.client.module.modules.client.AntiBot;
import me.tryfle.stormy.events.UpdateEvent;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.weavemc.loader.api.event.*;
import org.lwjgl.input.Mouse;

import java.util.Comparator;
import java.util.Optional;

@SuppressWarnings("unused")
public class Killaura extends Module {
    static Optional<EntityPlayer> target = Optional.empty();
    // Settings
    public static SliderSetting range, cps, hurtTimeAmt, rotRand, rotationRange, blockingRange;
    public static TickSetting shouldBlock, targetESP, alwaysAB, rots, whenLooking, keepSprint, noSprint,
            onlyWhileHoldingSword, onlyWhileNotMoving, onlyWhileInvOpen, onlyWhileTimer, showAttackConditions;
    public static ComboSetting<AutoBlockMode> autoblockMode;
    public static ComboSetting<RotationsMode> rotationsMode;
    public TimerUtils timer = new TimerUtils();
    public boolean delaying, isAttacking = false;
    long lastClickTime = 0;
    int rmb = mc.gameSettings.keyBindUseItem.getKeyCode();

    public enum AutoBlockMode {
        None, Fake
    }

    public enum RotationsMode {
        Normal, Random, Legit
    }

    public Killaura() {
        super("Killaura", ModuleCategory.Combat, 0);
        this.registerSetting(new DescriptionSetting("Advanced Killaura with many options."));
        this.registerSetting(range = new SliderSetting("Attack Range", 3.2, 3, 6, 0.05));
        this.registerSetting(rotationRange = new SliderSetting("Rotation Range", 4, 3, 6, 0.05));
        this.registerSetting(blockingRange = new SliderSetting("Blocking Range", 4, 3, 6, 0.05));
        this.registerSetting(cps = new SliderSetting("CPS", 10, 1, 20, 0.5));
        this.registerSetting(hurtTimeAmt = new SliderSetting("Ignore before hurt time", 1, 1, 20, 1));
        this.registerSetting(rotRand = new SliderSetting("Rotation Randomization", 2, 0, 3, .01));
        this.registerSetting(rotationsMode = new ComboSetting<>("Rotations Mode", RotationsMode.Normal));
        this.registerSetting(autoblockMode = new ComboSetting<>("AutoBlock Mode", AutoBlockMode.None));
        this.registerSetting(rots = new TickSetting("Rotations (for bypassing)", false));
        this.registerSetting(whenLooking = new TickSetting("Only when looking at player", false));
        this.registerSetting(shouldBlock = new TickSetting("Autoblock (Hold RMB)", false));
        this.registerSetting(alwaysAB = new TickSetting("Autoblock Always", false));
        this.registerSetting(targetESP = new TickSetting("ESP", false));
        this.registerSetting(keepSprint = new TickSetting("KeepSprint", false));
        this.registerSetting(noSprint = new TickSetting("NoSprint", false));
        this.registerSetting(onlyWhileHoldingSword = new TickSetting("Only While Holding Sword", false));
        this.registerSetting(onlyWhileNotMoving = new TickSetting("Only While Not Moving", false));
        this.registerSetting(onlyWhileInvOpen = new TickSetting("Attack While Inventory Open", false));
        this.registerSetting(onlyWhileTimer = new TickSetting("Attack While Using Timer", false));
        this.registerSetting(showAttackConditions = new TickSetting("Show Attack Conditions", false));
    }

    @Override
    public void onDisable() {
        target = Optional.empty();
    }

    @SubscribeEvent
    public void setTarget(TickEvent.Pre e) {
        if (PlayerUtils.isPlayerInGame()) {
            target = mc.theWorld != null
                    ? mc.theWorld.playerEntities.stream()
                            .filter(player -> player.getEntityId() != mc.thePlayer.getEntityId())
                            .filter(player -> player.getDistanceToEntity(mc.thePlayer) <= range.getInput())
                            .filter(player -> player.hurtTime < hurtTimeAmt.getInput() && player.deathTime == 0)
                            .filter(player -> !AntiBot.bot(player))
                            .min(Comparator.comparingDouble(player -> player.getDistanceToEntity(mc.thePlayer)))
                    : Optional.empty();
        }
    }

    public boolean aBooleanCheck() {
        if (!whenLooking.isToggled())
            return false;
        MovingObjectPosition result = mc.objectMouseOver;
        if (result != null && result.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY
                && result.entityHit instanceof EntityPlayer targetPlayer) {
            return whenLooking.isToggled()
                    && PlayerUtils.lookingAtPlayer(mc.thePlayer, targetPlayer, range.getInput() + 1);
        } else
            return false;
    }

    @SubscribeEvent
    public void onUpdate(UpdateEvent.Pre e) {
        if (target.isEmpty() || !PlayerUtils.isPlayerInGame()) {
            isAttacking = false;
            return;
        }
        if (onlyWhileHoldingSword.isToggled() && !PlayerUtils.isPlayerHoldingWeapon())
            return;
        if (onlyWhileNotMoving.isToggled() && (mc.thePlayer.moveForward != 0 || mc.thePlayer.moveStrafing != 0))
            return;
        if (onlyWhileInvOpen.isToggled() && mc.currentScreen == null)
            return;
        if (timer.hasReached(1000 / cps.getInput() + Utils.Java.randomInt(-3, 3))
                && mc.thePlayer.hurtTime < hurtTimeAmt.getInput() && mc.currentScreen == null) {
            if (target.isPresent()) {
                if (mc.thePlayer.isBlocking() || mc.thePlayer.isEating())
                    return;
                if (whenLooking.isToggled() && !aBooleanCheck())
                    return;
                if (target.get().deathTime > 0)
                    return;
                mc.thePlayer.swingItem();
                mc.playerController.attackEntity(mc.thePlayer, target.get());
                timer.reset();
                isAttacking = true;
            }
        }
    }

    public void finishDelay() {
        long currentTime = System.currentTimeMillis();
        int newdelay = Utils.Java.randomInt(20, 70);
        if (currentTime - lastClickTime >= newdelay) {
            lastClickTime = currentTime;
            KeyBinding.setKeyBindState(rmb, false);
            KeyBinding.onTick(rmb);
            delaying = false;
        }
    }

    @SubscribeEvent
    public void onRender(RenderHandEvent e) {
        boolean fakeAB = autoblockMode.getMode() == AutoBlockMode.Fake;
        if (((Mouse.isButtonDown(1) && shouldBlock.isToggled()) || alwaysAB.isToggled())
                && PlayerUtils.isPlayerHoldingWeapon() && isAttacking && mc.currentScreen == null) {
            long currentTime = System.currentTimeMillis();
            int delay = 1000 / (int) cps.getInput() + Utils.Java.randomInt(-3, 3) - 4;
            if (autoblockMode.getMode() != AutoBlockMode.Fake) {
                if (currentTime - lastClickTime >= delay && !delaying) {
                    lastClickTime = currentTime;
                    KeyBinding.setKeyBindState(rmb, true);
                    KeyBinding.onTick(rmb);
                    delaying = true;
                }
                if (delaying) {
                    finishDelay();
                }
            }
        }
    }

    @SubscribeEvent
    public void ESP(RenderWorldEvent e) {
        if (targetESP.isToggled() && target.isPresent()) {
            Utils.HUD.drawBoxAroundEntity(target.get(), 1, 0.0D, 0.0D, Theme.getMainColor().getRGB(), true);
            Utils.HUD.drawBoxAroundEntity(target.get(), 2, 0.0D, 0.0D, Theme.getMainColor().getRGB(), true);
        }
    }

    @SubscribeEvent
    public void unblockthings(TickEvent e) {
        if (!PlayerUtils.isPlayerInGame())
            return;
        if (mc.thePlayer.isBlocking() && PlayerUtils.isPlayerHoldingWeapon() && !Mouse.isButtonDown(1)
                && mc.currentScreen == null && !isAttacking) {
            long neow = System.currentTimeMillis();
            int ubdelay = Utils.Java.randomInt(850, 1050);
            if (neow >= ubdelay) {
                KeyBinding.setKeyBindState(rmb, false);
                KeyBinding.onTick(rmb);
            }
        }
    }

    @SubscribeEvent
    public void onClientTick(RenderWorldEvent e) {
        if (PlayerUtils.isPlayerInGame() && target.isPresent() && mc.currentScreen == null && rots.isToggled()
                && !mc.thePlayer.isEating()) {
            double deltaX = target.get().posX - mc.thePlayer.posX;
            double deltaY = target.get().posY + target.get().getEyeHeight() - mc.thePlayer.posY
                    - mc.thePlayer.getEyeHeight();
            double deltaZ = target.get().posZ - mc.thePlayer.posZ;
            double distance = MathHelper.sqrt_double(deltaX * deltaX + deltaZ * deltaZ);

            float yaw = (float) (Math.atan2(deltaZ, deltaX) * (180 / Math.PI)) - 90.0F;
            float pitch = (float) (-(Math.atan2(deltaY, distance) * (180 / Math.PI)));
            RotationsMode rotMode = rotationsMode.getMode();
            if (rotMode == RotationsMode.Random) {
                yaw += (float) Utils.Java.randomInt((int) -rotRand.getInput(), (int) rotRand.getInput());
                pitch += (float) Utils.Java.randomInt((int) -rotRand.getInput(), (int) rotRand.getInput());
            } else if (rotMode == RotationsMode.Legit) {
                yaw += (float) (Math.random() * rotRand.getInput() - rotRand.getInput() / 2);
                pitch += (float) (Math.random() * rotRand.getInput() - rotRand.getInput() / 2);
            }
            // Movement fix: adjust moveForward/moveStrafing to match new yaw
            float yawDiff = MathHelper.wrapAngleTo180_float(yaw - mc.thePlayer.rotationYaw);
            if (Math.abs(yawDiff) > 1.0f) {
                float forward = mc.thePlayer.moveForward;
                float strafe = mc.thePlayer.moveStrafing;
                float angle = (float) Math.toRadians(yawDiff);
                float newForward = forward * MathHelper.cos(angle) - strafe * MathHelper.sin(angle);
                float newStrafe = forward * MathHelper.sin(angle) + strafe * MathHelper.cos(angle);
                mc.thePlayer.moveForward = newForward;
                mc.thePlayer.moveStrafing = newStrafe;
            }
            mc.thePlayer.rotationYaw = yaw;
            mc.thePlayer.rotationPitch = pitch;
        }
    }
}