package dev.stormy.client.module.modules.combat;

import dev.stormy.client.module.Module;
import dev.stormy.client.module.setting.impl.DescriptionSetting;
import dev.stormy.client.module.setting.impl.DoubleSliderSetting;
import dev.stormy.client.module.setting.impl.SliderSetting;
import dev.stormy.client.module.setting.impl.TickSetting;
import dev.stormy.client.utils.math.MathUtils;
import dev.stormy.client.utils.math.TimerUtils;
import dev.stormy.client.utils.player.PlayerUtils;
import me.tryfle.stormy.events.UpdateEvent;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;
import net.weavemc.loader.api.event.SubscribeEvent;
import org.lwjgl.input.Mouse;

@SuppressWarnings("unused")
public class WTap extends Module {
    public static SliderSetting range, chance;
    public static DoubleSliderSetting delay;
    public static TickSetting playersOnly, sTapEnabled;

    TimerUtils timer = new TimerUtils();

    public WTap() {
        super("WTap", Module.ModuleCategory.Combat, 0);
        this.registerSetting(new DescriptionSetting("Sprint resets automatically."));
        this.registerSetting(range = new SliderSetting("Range", 4.0D, 0.0D, 6.0D, 0.1D));
        this.registerSetting(chance = new SliderSetting("Chance", 50.0D, 0.0D, 100.0D, 1.0D));
        this.registerSetting(delay = new DoubleSliderSetting("Delay", 50.0D, 100.0D, 0.0D, 300.0D, 5.0D));
        this.registerSetting(playersOnly = new TickSetting("Players Only", true));
        this.registerSetting(sTapEnabled = new TickSetting("STap", false));
    }

    private final int wkey = mc.gameSettings.keyBindForward.getKeyCode();

    public boolean isLookingAtPlayer() {
        MovingObjectPosition result = mc.objectMouseOver;
        if (result != null && result.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY
                && result.entityHit instanceof EntityPlayer targetPlayer) {
            return PlayerUtils.lookingAtPlayer(mc.thePlayer, targetPlayer, range.getInput());
        }
        return false;
    }

    @SubscribeEvent
    public void onUpdate(UpdateEvent e) {
        if (!PlayerUtils.isPlayerInGame()) {
            return;
        }
        if (playersOnly.isToggled() && !isLookingAtPlayer()) {
            return;
        }
        if (Mouse.isButtonDown(0) && mc.thePlayer.moveForward > 0 && mc.currentScreen == null) {
            if (chance.getInput() != 100.0D) {
                double ch = Math.random() * 100;
                if (ch >= chance.getInput()) {
                    return;
                }
            }
            int d = MathUtils.randomInt(delay.getInputMin(), delay.getInputMax());
            if (timer.hasReached(d)) {
                KeyBinding.setKeyBindState(wkey, false);
                KeyBinding.onTick(wkey);
                if (sTapEnabled.isToggled()) {
                    int sprintKey = mc.gameSettings.keyBindSprint.getKeyCode();
                    KeyBinding.setKeyBindState(sprintKey, false);
                    KeyBinding.onTick(sprintKey);
                }
                timer.reset();
                rePress();
            }
        }
    }

    public void rePress() {
        if (mc.thePlayer.moveForward > 0) {
            KeyBinding.setKeyBindState(wkey, true);
            KeyBinding.onTick(wkey);
            if (sTapEnabled.isToggled()) {
                int sprintKey = mc.gameSettings.keyBindSprint.getKeyCode();
                KeyBinding.setKeyBindState(sprintKey, true);
                KeyBinding.onTick(sprintKey);
            }
        }
    }
}