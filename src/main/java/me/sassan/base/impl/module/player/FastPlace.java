package me.sassan.base.impl.module.player;

import me.sassan.base.api.module.Module;
import me.sassan.base.api.setting.impl.BooleanSetting;
import me.sassan.base.api.setting.impl.SliderSetting;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.weavemc.loader.api.event.SubscribeEvent;
import net.weavemc.loader.api.event.TickEvent;
import org.lwjgl.input.Keyboard;

public class FastPlace extends Module {
    private final SliderSetting delaySlider;
    private final BooleanSetting blocksOnly;
    private final int DEFAULT_DELAY = 4;

    public FastPlace() {
        super("FastPlace", "Place blocks faster", Keyboard.KEY_NONE, Category.PLAYER);
        this.addSetting(delaySlider = new SliderSetting("Delay", 1, 0.0, 3.0, 1.0));
        this.addSetting(blocksOnly = new BooleanSetting("Blocks only", true));
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.Post event) {
        if (!isEnabled() || mc.thePlayer == null || mc.theWorld == null || !mc.inGameHasFocus)
            return;

        if (blocksOnly.getValue()) {
            ItemStack heldItem = mc.thePlayer.getHeldItem();
            if (heldItem == null || !(heldItem.getItem() instanceof ItemBlock)) {
                return;
            }
        }

        double delay = delaySlider.getValue();
        if (delay == DEFAULT_DELAY)
            return;

        if (delay == 0) {
            mc.rightClickDelayTimer = 0;
        } else if (mc.rightClickDelayTimer == DEFAULT_DELAY) {
            mc.rightClickDelayTimer = (int) delay;
        }
    }
}
