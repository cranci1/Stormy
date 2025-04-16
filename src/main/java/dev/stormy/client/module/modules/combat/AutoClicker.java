package dev.stormy.client.module.modules.combat;

import dev.stormy.client.module.Module;
import dev.stormy.client.module.setting.impl.DescriptionSetting;
import dev.stormy.client.module.setting.impl.SliderSetting;
import dev.stormy.client.module.setting.impl.TickSetting;
import dev.stormy.client.utils.Utils;
import dev.stormy.client.utils.asm.HookUtils;
import dev.stormy.client.utils.math.TimerUtils;
import dev.stormy.client.utils.player.PlayerUtils;
import net.minecraft.block.BlockLiquid;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemSword;
import net.minecraft.util.BlockPos;
import net.weavemc.loader.api.event.*;
import org.lwjgl.input.Mouse;
import net.minecraft.util.MovingObjectPosition;

import java.util.Random;

@SuppressWarnings("unused")
public class AutoClicker extends Module {
    // Settings
    public static SliderSetting minCPS;
    public static SliderSetting maxCPS;
    public static SliderSetting jitterAmount;
    public static SliderSetting blockHitChance;
    public static TickSetting breakBlocks;
    public static TickSetting hitSelect;
    public static TickSetting weaponOnly;

    // Click timing variables
    private long nextPressTime = 0L;
    private long nextReleaseTime = 0L;
    private long nextMultiplierUpdateTime = 0L;
    private long nextExtraDelayUpdateTime = 0L;
    private long blockHitStartTime = 0L;
    private double delayMultiplier = 1.0D;
    private boolean multiplierActive = false;
    private boolean isHoldingBlockBreak = false;
    private boolean isBlockHitActive = false;

    // Random and utility
    private final Random rand = new Random();
    private final TimerUtils timer = new TimerUtils();
    private final int lmb = mc.gameSettings.keyBindAttack.getKeyCode();
    private final int rmb = mc.gameSettings.keyBindUseItem.getKeyCode();

    public AutoClicker() {
        super("AutoClicker", ModuleCategory.Combat, 0);
        this.registerSetting(new DescriptionSetting("Click automatically with advanced options"));
        this.registerSetting(minCPS = new SliderSetting("Min CPS", 9.0D, 1.0D, 20.0D, 0.5D));
        this.registerSetting(maxCPS = new SliderSetting("Max CPS", 12.0D, 1.0D, 20.0D, 0.5D));
        this.registerSetting(jitterAmount = new SliderSetting("Jitter", 0.0D, 0.0D, 3.0D, 0.1D));
        this.registerSetting(blockHitChance = new SliderSetting("Block Hit %", 0.0D, 0.0D, 100.0D, 1.0D));
        this.registerSetting(breakBlocks = new TickSetting("Break blocks", false));
        this.registerSetting(hitSelect = new TickSetting("Hit Select", false));
        this.registerSetting(weaponOnly = new TickSetting("Weapon Only", false));
    }

    @Override
    public void onEnable() {
        this.isBlockHitActive = Mouse.isButtonDown(1);
        resetClickTimers();
    }

    @Override
    public void onDisable() {
        resetClickTimers();
        this.isHoldingBlockBreak = false;
        this.isBlockHitActive = false;
    }

    private void resetClickTimers() {
        this.nextPressTime = 0L;
        this.nextReleaseTime = 0L;
    }

    public boolean shouldAttack() {
        if (weaponOnly.isToggled() && !isHoldingWeapon()) {
            return false;
        }

        if (hitSelect.isToggled()) {
            return hitSelectLogic();
        }

        return true;
    }

    private boolean isHoldingWeapon() {
        return mc.thePlayer != null && mc.thePlayer.getCurrentEquippedItem() != null &&
                mc.thePlayer.getCurrentEquippedItem().getItem() instanceof ItemSword;
    }

    public boolean breakBlock() {
        if (breakBlocks.isToggled() && mc.objectMouseOver != null) {
            BlockPos p = mc.objectMouseOver.getBlockPos();

            if (p != null) {
                if (mc.theWorld.getBlockState(p).getBlock() != Blocks.air &&
                        !(mc.theWorld.getBlockState(p).getBlock() instanceof BlockLiquid)) {
                    if (!isHoldingBlockBreak) {
                        KeyBinding.setKeyBindState(lmb, true);
                        KeyBinding.onTick(lmb);
                        isHoldingBlockBreak = true;
                    }
                    return true;
                }
                if (isHoldingBlockBreak) {
                    KeyBinding.setKeyBindState(lmb, false);
                    isHoldingBlockBreak = false;
                }
            }
        }
        return false;
    }

    public boolean hitSelectLogic() {
        if (!hitSelect.isToggled())
            return false;
        MovingObjectPosition result = mc.objectMouseOver;
        if (result != null && result.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY &&
                result.entityHit instanceof EntityPlayer) {
            EntityPlayer targetPlayer = (EntityPlayer) result.entityHit;
            return PlayerUtils.lookingAtPlayer(mc.thePlayer, targetPlayer, 4);
        }
        return false;
    }

    private void applyJitter() {
        if (jitterAmount.getInput() > 0.0D) {
            double jValue = jitterAmount.getInput() * 0.45D;
            if (rand.nextBoolean()) {
                mc.thePlayer.rotationYaw += rand.nextFloat() * jValue;
            } else {
                mc.thePlayer.rotationYaw -= rand.nextFloat() * jValue;
            }

            if (rand.nextBoolean()) {
                mc.thePlayer.rotationPitch += rand.nextFloat() * jValue * 0.45D;
            } else {
                mc.thePlayer.rotationPitch -= rand.nextFloat() * jValue * 0.45D;
            }
        }
    }

    @SubscribeEvent
    public void onRender(RenderHandEvent e) {
        if (!PlayerUtils.isPlayerInGame() || mc.currentScreen != null) {
            resetClickTimers();
            return;
        }

        if (Mouse.isButtonDown(0)) {
            if (!shouldAttack())
                return;
            if (breakBlock())
                return;

            performClick();
        } else {
            resetClickTimers();
        }
    }

    private void performClick() {
        applyJitter();

        long currentTime = System.currentTimeMillis();

        if (nextPressTime == 0L || nextReleaseTime == 0L) {
            updateClickDelay();
            return;
        }

        if (currentTime > nextPressTime) {
            KeyBinding.setKeyBindState(lmb, true);
            KeyBinding.onTick(lmb);
            HookUtils.setMouseButtonState(0, true);

            if (blockHitChance.getInput() > 0.0 && rand.nextDouble() * 100 < blockHitChance.getInput()) {
                if (mc.objectMouseOver != null &&
                        mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY) {
                    if (mc.thePlayer != null && mc.thePlayer.getCurrentEquippedItem() != null &&
                            mc.thePlayer.getCurrentEquippedItem().getItem() instanceof ItemSword) {

                        isBlockHitActive = true;

                        KeyBinding.setKeyBindState(rmb, true);
                        KeyBinding.onTick(rmb);
                        HookUtils.setMouseButtonState(1, true);

                        blockHitStartTime = System.currentTimeMillis();
                    }
                }
            }

            updateClickDelay();
        } else if (currentTime > nextReleaseTime) {
            KeyBinding.setKeyBindState(lmb, false);
            HookUtils.setMouseButtonState(0, false);
        }

        if (isBlockHitActive) {
            long blockHitDuration = System.currentTimeMillis() - blockHitStartTime;
            if (blockHitDuration >= 20 + rand.nextInt(20)) {
                KeyBinding.setKeyBindState(rmb, false);
                HookUtils.setMouseButtonState(1, false);
                isBlockHitActive = false;
            }
        }
    }

    private void updateClickDelay() {
        double cps = minCPS.getInput() + (maxCPS.getInput() - minCPS.getInput()) * rand.nextDouble() +
                (0.4D * rand.nextDouble());

        long delay = Math.round(1000.0D / cps);
        long currentTime = System.currentTimeMillis();

        if (currentTime > nextMultiplierUpdateTime) {
            if (!multiplierActive && rand.nextInt(100) >= 85) {
                multiplierActive = true;
                delayMultiplier = 1.1D + rand.nextDouble() * 0.15D;
            } else {
                multiplierActive = false;
                delayMultiplier = 1.0D;
            }
            nextMultiplierUpdateTime = currentTime + 500L + rand.nextInt(1500);
        }

        if (currentTime > nextExtraDelayUpdateTime) {
            if (rand.nextInt(100) >= 80) {
                delay += 50L + rand.nextInt(100);
            }
            nextExtraDelayUpdateTime = currentTime + 500L + rand.nextInt(1500);
        }

        if (multiplierActive) {
            delay = (long) (delay * delayMultiplier);
        }

        nextPressTime = currentTime + delay;
        nextReleaseTime = currentTime + (delay / 2L) - rand.nextInt(10);
    }
}