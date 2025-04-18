package me.sassan.base.impl.module.player;

import me.sassan.base.api.module.Module;
import me.sassan.base.api.setting.impl.BooleanSetting;
import me.sassan.base.api.setting.impl.DoubleSliderSetting;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.weavemc.loader.api.event.SubscribeEvent;
import net.weavemc.loader.api.event.TickEvent;
import org.lwjgl.input.Keyboard;

public class SafeWalk extends Module {
    private final BooleanSetting blocksOnly = new BooleanSetting("Blocks Only", true);
    private final BooleanSetting shiftOnJump = new BooleanSetting("Shift on Jump", false);
    private final BooleanSetting onHold = new BooleanSetting("On Shift Hold", false);
    private final BooleanSetting lookDown = new BooleanSetting("Only When Looking Down", true);
    private final DoubleSliderSetting pitchRange = new DoubleSliderSetting("Pitch Range", 70D, 85D, 0D, 90D, 1D);
    private final DoubleSliderSetting shiftTime = new DoubleSliderSetting("Shift Time (ms)", 140D, 200D, 0D, 280D, 5D);

    private boolean shouldBridge = false;
    private boolean isShifting = false;
    private long shiftTimerEnd = 0;

    public SafeWalk() {
        super("SafeWalk", "Bridges for you", Keyboard.KEY_NONE, Category.PLAYER);
        this.addSetting(shiftOnJump);
        this.addSetting(shiftTime);
        this.addSetting(onHold);
        this.addSetting(blocksOnly);
        this.addSetting(lookDown);
        this.addSetting(pitchRange);
    }

    @Override
    public void onDisable() {
        setShift(false);
        shouldBridge = false;
        isShifting = false;
        super.onDisable();
    }

    @SubscribeEvent
    public void onTick(TickEvent e) {
        if (mc.currentScreen != null || mc.thePlayer == null)
            return;

        final boolean shiftTimeActive = shiftTime.getMaxValue() > 0;
        final boolean holdKey = Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode());
        final boolean onHoldVal = onHold.getValue();
        final boolean lookDownVal = lookDown.getValue();
        final boolean blocksOnlyVal = blocksOnly.getValue();
        final boolean shiftOnJumpVal = shiftOnJump.getValue();

        if (lookDownVal) {
            float pitch = mc.thePlayer.rotationPitch;
            double minPitch = pitchRange.getMinValue();
            double maxPitch = pitchRange.getMaxValue();
            if (pitch < minPitch || pitch > maxPitch) {
                shouldBridge = false;
                if (holdKey)
                    setShift(true);
                return;
            }
        }

        if (onHoldVal && !holdKey) {
            shouldBridge = false;
            return;
        }

        if (blocksOnlyVal) {
            ItemStack held = mc.thePlayer.getHeldItem();
            if (held == null || !(held.getItem() instanceof ItemBlock)) {
                if (isShifting) {
                    isShifting = false;
                    setShift(false);
                }
                return;
            }
        }

        if (mc.thePlayer.onGround) {
            if (playerOverAir()) {
                if (shiftTimeActive) {
                    double min = shiftTime.getMinValue();
                    double max = shiftTime.getMaxValue();
                    shiftTimerEnd = System.currentTimeMillis() + (long) (min + Math.random() * (max - min));
                }
                isShifting = true;
                setShift(true);
                shouldBridge = true;
            } else if (mc.thePlayer.isSneaking() && !holdKey && onHoldVal) {
                isShifting = false;
                shouldBridge = false;
                setShift(false);
            } else if (onHoldVal && !holdKey) {
                isShifting = false;
                shouldBridge = false;
                setShift(false);
            } else if (mc.thePlayer.isSneaking() && holdKey && onHoldVal
                    && (!shiftTimeActive || shiftTimerFinished())) {
                isShifting = false;
                setShift(false);
                shouldBridge = true;
            } else if (mc.thePlayer.isSneaking() && !onHoldVal && (!shiftTimeActive || shiftTimerFinished())) {
                isShifting = false;
                setShift(false);
                shouldBridge = true;
            }
        } else if (shouldBridge && mc.thePlayer.capabilities.isFlying) {
            setShift(false);
            shouldBridge = false;
        } else if (shouldBridge && blockRelativeToPlayer(0, -1, 0) instanceof BlockAir && shiftOnJumpVal) {
            isShifting = true;
            setShift(true);
        } else {
            isShifting = false;
            setShift(false);
        }
    }

    private boolean shiftTimerFinished() {
        return System.currentTimeMillis() > shiftTimerEnd;
    }

    private boolean playerOverAir() {
        Block below = blockRelativeToPlayer(0, -1, 0);
        return below == null || below instanceof BlockAir;
    }

    private Block blockRelativeToPlayer(double offsetX, double offsetY, double offsetZ) {
        return mc.theWorld.getBlockState(new BlockPos(mc.thePlayer).add(offsetX, offsetY, offsetZ)).getBlock();
    }

    private void setShift(boolean sh) {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), sh);
    }
}
