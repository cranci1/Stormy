package dev.stormy.client.module.modules.combat;

import dev.stormy.client.module.Module;
import dev.stormy.client.module.setting.impl.DescriptionSetting;
import dev.stormy.client.module.setting.impl.SliderSetting;
import dev.stormy.client.module.setting.impl.TickSetting;
import dev.stormy.client.utils.player.PlayerUtils;
import dev.stormy.client.utils.asm.HookUtils;
import net.minecraft.block.BlockLiquid;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemSword;
import net.minecraft.util.BlockPos;
import net.weavemc.loader.api.event.SubscribeEvent;
import net.weavemc.loader.api.event.RenderHandEvent;
import org.lwjgl.input.Mouse;

import java.util.Random;

public class AutoClicker extends Module {
    public static SliderSetting minCPS, maxCPS, jitterAmount, blockHitChance;
    public static TickSetting breakBlocks, inventoryFill, weaponOnly, blocksOnly, disableCreative;

    private long nextPressTime = 0L, nextReleaseTime = 0L, nextMultiplierUpdateTime = 0L, nextExtraDelayUpdateTime = 0L,
            blockHitStartTime = 0L;
    private double delayMultiplier = 1.0D;
    private boolean multiplierActive = false, isHoldingBlockBreak = false, isBlockHitActive = false;

    private final Random rand = new Random();
    private final int lmb = mc.gameSettings.keyBindAttack.getKeyCode();
    private final int rmb = mc.gameSettings.keyBindUseItem.getKeyCode();

    public AutoClicker() {
        super("AutoClicker", ModuleCategory.Combat, 0);
        this.registerSetting(new DescriptionSetting("Advanced, human-like auto clicker"));
        this.registerSetting(minCPS = new SliderSetting("Min CPS", 9.0D, 1.0D, 20.0D, 0.5D));
        this.registerSetting(maxCPS = new SliderSetting("Max CPS", 12.0D, 1.0D, 20.0D, 0.5D));
        this.registerSetting(jitterAmount = new SliderSetting("Jitter", 0.0D, 0.0D, 3.0D, 0.1D));
        this.registerSetting(blockHitChance = new SliderSetting("Block Hit %", 0.0D, 0.0D, 100.0D, 1.0D));
        this.registerSetting(breakBlocks = new TickSetting("Break Blocks", false));
        this.registerSetting(inventoryFill = new TickSetting("Inventory Fill", false));
        this.registerSetting(weaponOnly = new TickSetting("Weapon Only", false));
        this.registerSetting(disableCreative = new TickSetting("Disable in Creative", false));
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

    private boolean isHoldingWeapon() {
        return mc.thePlayer != null && mc.thePlayer.getCurrentEquippedItem() != null &&
                mc.thePlayer.getCurrentEquippedItem().getItem() instanceof ItemSword;
    }

    private void applyJitter() {
        if (jitterAmount.getInput() > 0.0D) {
            double jValue = jitterAmount.getInput() * 0.45D;
            mc.thePlayer.rotationYaw += (rand.nextFloat() - 0.5F) * 2 * jValue;
            mc.thePlayer.rotationPitch += (rand.nextFloat() - 0.5F) * 2 * jValue * 0.45D;
        }
    }

    @SubscribeEvent
    public void onRender(RenderHandEvent e) {
        if (!PlayerUtils.isPlayerInGame() || mc.currentScreen != null) {
            resetClickTimers();
            return;
        }

        if (Mouse.isButtonDown(0)) {
            if (weaponOnly.isToggled() && !isHoldingWeapon())
                return;
            if (disableCreative.isToggled() && mc.thePlayer.capabilities.isCreativeMode)
                return;
            if (breakBlocks.isToggled() && tryBreakBlock())
                return;
            performClick(lmb, 0);
        } else {
            resetClickTimers();
        }
    }

    private boolean tryBreakBlock() {
        if (mc.objectMouseOver != null) {
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

    private void performClick(int key, int mouse) {
        applyJitter();

        long currentTime = System.currentTimeMillis();

        if (nextPressTime == 0L || nextReleaseTime == 0L) {
            updateClickDelay();
            return;
        }

        if (currentTime > nextPressTime) {
            KeyBinding.setKeyBindState(key, true);
            KeyBinding.onTick(key);
            HookUtils.setMouseButtonState(mouse, true);

            // Only blockhit if hitting an entity
            if (mouse == 0 && blockHitChance.getInput() > 0.0 && Mouse.isButtonDown(1)
                    && rand.nextDouble() * 100 < blockHitChance.getInput()
                    && isHoldingWeapon()
                    && mc.objectMouseOver != null
                    && mc.objectMouseOver.entityHit != null) {
                KeyBinding.setKeyBindState(rmb, true);
                KeyBinding.onTick(rmb);
                HookUtils.setMouseButtonState(1, true);
                isBlockHitActive = true;
                blockHitStartTime = System.currentTimeMillis();
            }

            updateClickDelay();
        } else if (currentTime > nextReleaseTime) {
            KeyBinding.setKeyBindState(key, false);
            HookUtils.setMouseButtonState(mouse, false);
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