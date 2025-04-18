package me.sassan.base.impl.module.player;

import me.sassan.base.api.module.Module;
import me.sassan.base.api.setting.impl.BooleanSetting;
import org.lwjgl.input.Keyboard;

/**
 * @author sassan
 *         18.11.2023, 2023
 */
public class Jump extends Module {
    private final BooleanSetting autoJump = new BooleanSetting("Bypass", true);

    public Jump() {
        super("Jump", "Jump", Keyboard.KEY_F, Category.PLAYER);
        this.addSetting(autoJump);
    }

    @Override
    public void onEnable() {
        mc.thePlayer.jump();

        mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText(String.valueOf(autoJump.getValue())));

        super.onEnable();
    }
}
