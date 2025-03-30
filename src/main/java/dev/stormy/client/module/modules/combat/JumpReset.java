package dev.stormy.client.module.modules.combat;

import dev.stormy.client.module.Module;
import dev.stormy.client.module.setting.impl.DescriptionSetting;
import dev.stormy.client.module.setting.impl.SliderSetting;
import dev.stormy.client.module.setting.impl.TickSetting;
import dev.stormy.client.utils.player.PlayerUtils;
import me.tryfle.stormy.events.PacketEvent;
import me.tryfle.stormy.events.EventDirection;
import me.tryfle.stormy.events.UpdateEvent;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.weavemc.loader.api.event.SubscribeEvent;

@SuppressWarnings("unused")
public class JumpReset extends Module {
    public static SliderSetting jumpDelay;
    public static TickSetting autoWTap;
    public static TickSetting preventNearEdge;

    private final int jumpKey = mc.gameSettings.keyBindJump.getKeyCode();
    private final int wKey = mc.gameSettings.keyBindForward.getKeyCode();
    private boolean shouldJump = false;
    private long lastHitTime = 0;

    public JumpReset() {
        super("JumpReset", ModuleCategory.Combat, 0);
        this.registerSetting(new DescriptionSetting("Jumps when hit to reduce knockback."));
        this.registerSetting(jumpDelay = new SliderSetting("Jump Delay (ms)", 20.0D, 0.0D, 200.0D, 5.0D));
        this.registerSetting(autoWTap = new TickSetting("Auto W-Tap", true));
        this.registerSetting(preventNearEdge = new TickSetting("Prevent Near Edge", true));
    }

    @SubscribeEvent
    public void onPacketEvent(PacketEvent event) {
        // Detect when player is hit
        if (event.getDirection() == EventDirection.INCOMING && event.getPacket() instanceof S19PacketEntityStatus) {
            S19PacketEntityStatus packet = (S19PacketEntityStatus) event.getPacket();
            if (packet.getOpCode() == 2 && packet.getEntity(mc.theWorld).getEntityId() == mc.thePlayer.getEntityId()) {
                lastHitTime = System.currentTimeMillis();
                shouldJump = true;
            }
        }
    }

    @SubscribeEvent
    public void onUpdate(UpdateEvent e) {
        if (!PlayerUtils.isPlayerInGame() || !shouldJump) {
            return;
        }

        // Check if we're near an edge and preventNearEdge is enabled
        if (preventNearEdge.isToggled() && isNearEdge()) {
            shouldJump = false;
            return;
        }

        // Jump at the configured delay time
        if (System.currentTimeMillis() - lastHitTime >= jumpDelay.getInput()) {
            shouldJump = false;

            // Jump to reduce knockback
            KeyBinding.setKeyBindState(jumpKey, true);
            KeyBinding.onTick(jumpKey);

            // Release the jump key in the next tick
            mc.addScheduledTask(() -> {
                KeyBinding.setKeyBindState(jumpKey, false);
                KeyBinding.onTick(jumpKey);

                // W-tap if enabled
                if (autoWTap.isToggled()) {
                    KeyBinding.setKeyBindState(wKey, false);
                    KeyBinding.onTick(wKey);

                    mc.addScheduledTask(() -> {
                        KeyBinding.setKeyBindState(wKey, true);
                        KeyBinding.onTick(wKey);
                    });
                }
            });
        }
    }

    private boolean isNearEdge() {
        // Check if there's no solid block below the player within 2 blocks
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (!mc.theWorld.getBlockState(mc.thePlayer.getPosition().add(x, -1, z)).getBlock().isFullBlock() &&
                        !mc.theWorld.getBlockState(mc.thePlayer.getPosition().add(x, -2, z)).getBlock().isFullBlock()) {
                    return true;
                }
            }
        }
        return false;
    }
}