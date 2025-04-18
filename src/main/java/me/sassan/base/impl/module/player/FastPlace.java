package me.sassan.base.impl.module.player;

import me.sassan.base.api.module.Module;
import me.sassan.base.api.setting.impl.BooleanSetting;
import me.sassan.base.api.setting.impl.SliderSetting;
import me.sassan.base.utils.player.PlayerUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.weavemc.loader.api.event.SubscribeEvent;
import net.weavemc.loader.api.event.TickEvent;
import org.lwjgl.input.Keyboard;

public class FastPlace extends Module {
    private final SliderSetting delaySlider;
    private final BooleanSetting blocksOnly;

    public FastPlace() {
        super("FastPlace", "Place blocks faster", Keyboard.KEY_NONE, Category.PLAYER);
        this.addSetting(delaySlider = new SliderSetting("Delay", 0.0, 0.0, 3.0, 1.0));
        this.addSetting(blocksOnly = new BooleanSetting("Blocks only", true));
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.Post event) {
        if (!isEnabled())
            return;

        if (mc.thePlayer != null && mc.theWorld != null && mc.inGameHasFocus) {
            if (blocksOnly.getValue()) {
                ItemStack item = mc.thePlayer.getHeldItem();
                if (item == null || !(item.getItem() instanceof ItemBlock)) {
                    return;
                }
            }

            if (delaySlider.getValue() == 0) {
                Minecraft.getMinecraft().rightClickDelayTimer = 0;
            } else {
                if (delaySlider.getValue() == 4) {
                    return;
                }

                if (Minecraft.getMinecraft().rightClickDelayTimer == 4) {
                    Minecraft.getMinecraft().rightClickDelayTimer = delaySlider.getValue().intValue();
                }
            }
        }
    }
}