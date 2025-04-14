package dev.stormy.client.module.modules.combat;

import dev.stormy.client.module.Module;
import dev.stormy.client.module.modules.client.ArrayListModule.ColorModes;
import dev.stormy.client.module.setting.impl.*;
import dev.stormy.client.utils.asm.HookUtils;
import dev.stormy.client.utils.math.TimerUtils;
import dev.stormy.client.utils.player.PlayerUtils;
import net.minecraft.block.BlockLiquid;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemSword;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.weavemc.loader.api.event.*;
import org.lwjgl.input.Mouse;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

@SuppressWarnings("unused")
public class AutoClicker extends Module {
    public enum ClickPattern {
        NORMAL, BUTTERFLY, JITTER
    }

    public static ComboSetting<ClickPattern> clickPattern;
    public static SliderSetting minCPS;
    public static SliderSetting maxCPS;
    public static SliderSetting jitterStrength;
    public static SliderSetting butterflyRandom;
    public static SliderSetting blockHitChance;
    public static TickSetting smartBlock;
    public static TickSetting randomizeClicks;
    public static TickSetting breakBlocks;
    public static TickSetting hitSelect;
    public static TickSetting weaponOnly;

    // Timing variables
    private final TimerUtils clickTimer = new TimerUtils();
    private final TimerUtils blockTimer = new TimerUtils();
    private long nextClickDelay;
    private long nextBlockDelay;
    private boolean blocking = false;
    private boolean isBreakingBlock = false;

    // Random generators
    private final Random rand = ThreadLocalRandom.current();
    private double randomizedDelay = 1.0;
    private float currentJitter = 0.0f;

    // Key bindings
    private final int LMB = mc.gameSettings.keyBindAttack.getKeyCode();
    private final int RMB = mc.gameSettings.keyBindUseItem.getKeyCode();

    public AutoClicker() {
        super("AutoClicker", ModuleCategory.Combat, 0);
        this.registerSetting(new DescriptionSetting("Advanced AutoClicker with multiple patterns"));
        this.registerSetting(clickPattern = new ComboSetting<>("Pattern", ClickPattern.NORMAL));
        this.registerSetting(minCPS = new SliderSetting("Min CPS", 9.0, 1.0, 20.0, 0.5));
        this.registerSetting(maxCPS = new SliderSetting("Max CPS", 12.0, 1.0, 20.0, 0.5));
        this.registerSetting(jitterStrength = new SliderSetting("Jitter Strength", 0.0, 0.0, 3.0, 0.1));
        this.registerSetting(butterflyRandom = new SliderSetting("Butterfly Random", 0.85, 0.1, 1.0, 0.05));
        this.registerSetting(blockHitChance = new SliderSetting("Block Chance", 100.0, 0.0, 100.0, 1.0));
        this.registerSetting(smartBlock = new TickSetting("Smart Block", true));
        this.registerSetting(randomizeClicks = new TickSetting("Randomize", true));
        this.registerSetting(breakBlocks = new TickSetting("Break Blocks", true));
        this.registerSetting(hitSelect = new TickSetting("Hit Select", true));
        this.registerSetting(weaponOnly = new TickSetting("Weapon Only", true));
    }

    @SubscribeEvent
    public void onTick(TickEvent e) {
        if (!isEnabled() || !PlayerUtils.isPlayerInGame() || mc.currentScreen != null)
            return;

        if (Mouse.isButtonDown(0)) {
            if (!canClick())
                return;
            if (breakBlocks.isToggled() && handleBlockBreaking())
                return;

            ClickPattern currentPattern = clickPattern.getMode(); // Get current pattern safely
            switch (currentPattern) {
                case NORMAL:
                    normalClick();
                    break;
                case BUTTERFLY:
                    butterflyClick();
                    break;
                case JITTER:
                    jitterClick();
                    break;
            }
        } else {
            resetClicking();
        }

        handleAutoBlock();
    }

    private boolean canClick() {
        if (weaponOnly.isToggled() && !isHoldingWeapon())
            return false;
        if (hitSelect.isToggled() && !isTargetValid())
            return false;
        return true;
    }

    private void normalClick() {
        if (clickTimer.hasReached(nextClickDelay)) {
            simulateClick();
            updateClickDelay();
        }
    }

    private void butterflyClick() {
        if (clickTimer.hasReached(nextClickDelay)) {
            simulateClick();
            if (rand.nextDouble() <= butterflyRandom.getInput()) {
                simulateClick(); // Double click for butterfly effect
            }
            updateClickDelay();
        }
    }

    private void jitterClick() {
        if (clickTimer.hasReached(nextClickDelay)) {
            applyJitter();
            simulateClick();
            updateClickDelay();
        }
    }

    private void simulateClick() {
        KeyBinding.setKeyBindState(LMB, true);
        KeyBinding.onTick(LMB);
        HookUtils.setMouseButtonState(0, true);

        // Release after a small delay
        ThreadLocalRandom.current().nextInt(1, 5);
        KeyBinding.setKeyBindState(LMB, false);
        HookUtils.setMouseButtonState(0, false);
    }

    private void handleAutoBlock() {
        if (!smartBlock.isToggled() || !isHoldingWeapon())
            return;

        MovingObjectPosition mop = mc.objectMouseOver;
        boolean shouldBlock = mop != null &&
                mop.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY &&
                mop.entityHit instanceof EntityPlayer &&
                rand.nextDouble() * 100 < blockHitChance.getInput();

        if (shouldBlock && !blocking && blockTimer.hasReached(nextBlockDelay)) {
            KeyBinding.setKeyBindState(RMB, true);
            KeyBinding.onTick(RMB);
            blocking = true;
            updateBlockDelay();
        } else if (!shouldBlock && blocking) {
            KeyBinding.setKeyBindState(RMB, false);
            blocking = false;
        }
    }

    private void updateClickDelay() {
        double baseDelay = 1000.0 / (minCPS.getInput() +
                (maxCPS.getInput() - minCPS.getInput()) * rand.nextDouble());

        if (randomizeClicks.isToggled()) {
            if (rand.nextInt(100) > 80) {
                randomizedDelay = 1.0 + (rand.nextDouble() * 0.3);
            }
            baseDelay *= randomizedDelay;
        }

        nextClickDelay = (long) baseDelay;
        clickTimer.reset();
    }

    private void updateBlockDelay() {
        nextBlockDelay = 50L + rand.nextInt(100);
        blockTimer.reset();
    }

    private void applyJitter() {
        if (jitterStrength.getInput() <= 0)
            return;

        float strength = (float) jitterStrength.getInput();
        currentJitter = (rand.nextFloat() - 0.5f) * strength;

        mc.thePlayer.rotationYaw += currentJitter;
        mc.thePlayer.rotationPitch += currentJitter * 0.5f;
    }

    private boolean isHoldingWeapon() {
        return mc.thePlayer != null && mc.thePlayer.getCurrentEquippedItem() != null &&
                mc.thePlayer.getCurrentEquippedItem().getItem() instanceof ItemSword;
    }

    private boolean handleBlockBreaking() {
        if (mc.objectMouseOver != null) {
            BlockPos p = mc.objectMouseOver.getBlockPos();

            if (p != null) {
                if (mc.theWorld.getBlockState(p).getBlock() != Blocks.air &&
                        !(mc.theWorld.getBlockState(p).getBlock() instanceof BlockLiquid)) {
                    if (!isBreakingBlock) {
                        KeyBinding.setKeyBindState(LMB, true);
                        KeyBinding.onTick(LMB);
                        isBreakingBlock = true;
                    }
                    return true;
                }
                if (isBreakingBlock) {
                    KeyBinding.setKeyBindState(LMB, false);
                    isBreakingBlock = false;
                }
            }
        }
        return false;
    }

    private boolean isTargetValid() {
        MovingObjectPosition result = mc.objectMouseOver;
        if (result != null && result.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY &&
                result.entityHit instanceof EntityPlayer) {
            EntityPlayer targetPlayer = (EntityPlayer) result.entityHit;
            return PlayerUtils.lookingAtPlayer(mc.thePlayer, targetPlayer, 4);
        }
        return false;
    }

    @Override
    public void onDisable() {
        resetClicking();
        if (blocking) {
            KeyBinding.setKeyBindState(RMB, false);
            blocking = false;
        }
    }

    private void resetClicking() {
        isBreakingBlock = false;
        randomizedDelay = 1.0;
        currentJitter = 0.0f;
        if (mc.thePlayer != null) {
            mc.thePlayer.rotationYaw -= currentJitter;
            mc.thePlayer.rotationPitch -= currentJitter * 0.5f;
        }
    }
}