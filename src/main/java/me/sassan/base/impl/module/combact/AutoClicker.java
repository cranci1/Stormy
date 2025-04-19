package me.sassan.base.impl.module.combact;

import me.sassan.base.api.module.Module;
import me.sassan.base.api.setting.impl.*;
import net.weavemc.loader.api.event.SubscribeEvent;
import net.weavemc.loader.api.event.TickEvent;
import org.lwjgl.input.Mouse;
import org.lwjgl.input.Keyboard;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.item.ItemBlock;

import java.lang.reflect.Method;
import java.util.Random;

public class AutoClicker extends Module {
    private final DoubleSliderSetting cpsRange = new DoubleSliderSetting("CPS Range", 10.0, 12.0, 1.0, 20.0, 0.5);
    private final SliderSetting jitterAmount = new SliderSetting("Jitter", 0.0, 0.0, 3.0, 0.1);
    private final SliderSetting blockHitChance = new SliderSetting("Block Hit %", 10, 0, 100, 1);
    private final BooleanSetting breakBlocks = new BooleanSetting("Break Blocks", true);
    private final BooleanSetting hitSelect = new BooleanSetting("Hit Select", false);
    private final BooleanSetting weaponOnly = new BooleanSetting("Weapon Only", false);
    private final BooleanSetting leftClick = new BooleanSetting("Left Click", true);
    private final BooleanSetting rightClick = new BooleanSetting("Right Click", false);
    private final BooleanSetting blocksOnly = new BooleanSetting("Blocks Only", false);
    private final BooleanSetting inventoryFill = new BooleanSetting("Inventory Fill", false);

    private long nextPressTime = 0L;
    private long nextReleaseTime = 0L;
    private long nextMultiplierUpdateTime = 0L;
    private long nextExtraDelayUpdateTime = 0L;
    private long blockHitStartTime = 0L;
    private double delayMultiplier = 1.0D;
    private boolean multiplierActive = false;
    private boolean isBlockHitActive = false;
    private boolean isHoldingBlock = false;

    private long useItemReleaseTime = 0L;
    private boolean useItemKeyHeld = false;

    private final Random rand = new Random();
    private Method guiScreenMouseClick;

    public AutoClicker() {
        super("AutoClicker", "Automatically clicks mouse", Keyboard.KEY_NONE, Category.COMBAT);
        this.addSetting(cpsRange);
        this.addSetting(jitterAmount);
        this.addSetting(leftClick);
        this.addSetting(rightClick);
        this.addSetting(inventoryFill);
        this.addSetting(weaponOnly);
        this.addSetting(blocksOnly);
        this.addSetting(breakBlocks);
        this.addSetting(blockHitChance);
        this.addSetting(hitSelect);

        try {
            this.guiScreenMouseClick = net.minecraft.client.gui.GuiScreen.class.getDeclaredMethod(
                    "mouseClicked", Integer.TYPE, Integer.TYPE, Integer.TYPE);
            this.guiScreenMouseClick.setAccessible(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onEnable() {
        super.onEnable();
        isBlockHitActive = Mouse.isButtonDown(1);
        isHoldingBlock = false;
        resetClickTimers();
    }

    @SubscribeEvent
    public void onTick(TickEvent event) {
        if (isEnabled()) {
            onTickInternal();
        }
    }

    private void onTickInternal() {
        if (!isEnabled() || mc.thePlayer == null) {
            return;
        }

        if (useItemKeyHeld && System.currentTimeMillis() >= useItemReleaseTime) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
            useItemKeyHeld = false;
        }

        if (mc.thePlayer.isUsingItem()) {
            resetClickTimers();
            return;
        }

        if (inventoryFill.getValue() && mc.currentScreen instanceof GuiInventory) {
            if (Mouse.isButtonDown(0) && (Keyboard.isKeyDown(54) || Keyboard.isKeyDown(42))) { // Shift key pressed
                if (nextPressTime != 0L && nextReleaseTime != 0L) {
                    if (System.currentTimeMillis() > nextReleaseTime) {
                        simulateInventoryClick();
                        updateClickDelay();
                    }
                } else {
                    updateClickDelay();
                }
            } else {
                resetClickTimers();
            }
            return;
        }

        if (mc.currentScreen != null) {
            resetClickTimers();
            return;
        }

        if (leftClick.getValue() && Mouse.isButtonDown(0)) {
            if (!shouldAttack())
                return;

            if (breakBlocks.getValue() && mc.objectMouseOver != null) {
                BlockPos pos = mc.objectMouseOver.getBlockPos();
                if (pos != null) {
                    Block block = mc.theWorld.getBlockState(pos).getBlock();
                    if (block != Blocks.air && !(block instanceof BlockLiquid)) {
                        if (!isHoldingBlock) {
                            KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), true);
                            KeyBinding.onTick(mc.gameSettings.keyBindAttack.getKeyCode());
                            isHoldingBlock = true;
                        }
                        return;
                    }

                    if (isHoldingBlock) {
                        KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
                        isHoldingBlock = false;
                    }
                }
            }

            performClick(mc.gameSettings.keyBindAttack.getKeyCode(), 0);
        } else if (rightClick.getValue() && Mouse.isButtonDown(1)) {
            if (!shouldRightClick())
                return;
            performClick(mc.gameSettings.keyBindUseItem.getKeyCode(), 1);
        } else {
            resetClickTimers();
            if (isHoldingBlock) {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
                isHoldingBlock = false;
            }
        }
    }

    @Override
    public void onDisable() {
        resetClickTimers();
        isBlockHitActive = false;
        isHoldingBlock = false;
        super.onDisable();
    }

    private void resetClickTimers() {
        nextPressTime = 0L;
        nextReleaseTime = 0L;
    }

    private boolean isHoldingWeapon() {
        return mc.thePlayer != null && mc.thePlayer.getCurrentEquippedItem() != null &&
                mc.thePlayer.getCurrentEquippedItem().getItem().getClass().getSimpleName().toLowerCase()
                        .contains("sword");
    }

    private boolean isHoldingBlock() {
        return mc.thePlayer != null && mc.thePlayer.getCurrentEquippedItem() != null &&
                mc.thePlayer.getCurrentEquippedItem().getItem() instanceof ItemBlock;
    }

    private boolean shouldAttack() {
        if (weaponOnly.getValue() && !isHoldingWeapon()) {
            return false;
        }
        if (hitSelect.getValue()) {
            return true;
        }
        return true;
    }

    private boolean shouldRightClick() {
        if (blocksOnly.getValue() && !isHoldingBlock()) {
            return false;
        }
        return true;
    }

    private void applyJitter() {
        if (jitterAmount.getValue() > 0.0D) {
            double jValue = jitterAmount.getValue() * 0.45D;
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

    private void performClick(int key, int mouseButton) {
        applyJitter();

        long currentTime = System.currentTimeMillis();

        if (nextPressTime == 0L || nextReleaseTime == 0L) {
            updateClickDelay();
            return;
        }

        if (currentTime > nextPressTime) {
            KeyBinding.setKeyBindState(key, true);
            KeyBinding.onTick(key);

            if (mouseButton == 0 && blockHitChance.getValue() > 0.0
                    && rand.nextDouble() * 100 < blockHitChance.getValue()
                    && mc.objectMouseOver != null && mc.objectMouseOver.entityHit != null) {
                if (isHoldingWeapon() && !useItemKeyHeld) {
                    KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
                    KeyBinding.onTick(mc.gameSettings.keyBindUseItem.getKeyCode());
                    useItemKeyHeld = true;
                    useItemReleaseTime = System.currentTimeMillis() + 15 + rand.nextInt(15);
                }
            }

            updateClickDelay();
        } else if (currentTime > nextReleaseTime) {
            KeyBinding.setKeyBindState(key, false);
        }
    }

    private void simulateInventoryClick() {
        if (guiScreenMouseClick != null && mc.currentScreen != null) {
            try {
                int x = Mouse.getX() * mc.currentScreen.width / mc.displayWidth;
                int y = mc.currentScreen.height - Mouse.getY() * mc.currentScreen.height / mc.displayHeight - 1;
                guiScreenMouseClick.invoke(mc.currentScreen, x, y, 0);
            } catch (Exception ignored) {
            }
        }
    }

    private void updateClickDelay() {
        double minCps = cpsRange.getMinValue();
        double maxCps = cpsRange.getMaxValue();
        double cps = minCps + (maxCps - minCps) * rand.nextDouble() + (0.4D * rand.nextDouble());
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