package dev.stormy.client.module.modules.gamemode;

import dev.stormy.client.module.Module;
import dev.stormy.client.module.setting.impl.ComboSetting;
import dev.stormy.client.module.setting.impl.DescriptionSetting;
import dev.stormy.client.module.setting.impl.SliderSetting;
import dev.stormy.client.utils.player.PlayerUtils;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.weavemc.loader.api.event.ChatReceivedEvent;
import net.weavemc.loader.api.event.SubscribeEvent;

import java.util.Timer;
import java.util.TimerTask;

public class AutoPlay extends Module {
    private ComboSetting<modes> mode;
    private SliderSetting delay;
    private Timer queueTimer;

    public AutoPlay() {
        super("AutoPlay", ModuleCategory.GameMode, 0);
        this.registerSetting(new DescriptionSetting("Automatically queues into games"));
        this.registerSetting(mode = new ComboSetting<>("Mode", modes.HypixelSolo));
        this.registerSetting(delay = new SliderSetting("Delay (s)", 2.0D, 0.5D, 10.0D, 0.5D));
    }

    @Override
    public void onDisable() {
        if (queueTimer != null) {
            queueTimer.cancel();
            queueTimer = null;
        }
    }

    @SubscribeEvent
    public void onChatReceived(ChatReceivedEvent event) {
        if (!PlayerUtils.isPlayerInGame() || mc.thePlayer == null)
            return;

        String message = event.getMessage().getUnformattedText();
        modes currentMode = mode.getMode();

        // Check for Hypixel elimination message
        if (currentMode.name().startsWith("Hypixel") && message.contains("You have been eliminated!")) {
            scheduleHypixelQueue();
        }
        // Check for BlocksMc game end
        else if (currentMode == modes.BlocksMcSW && message.contains("SkyWars Solo ▏ Match Recap")) {
            scheduleBlocksMcQueue();
        }
    }

    private void scheduleHypixelQueue() {
        if (queueTimer != null) {
            queueTimer.cancel();
        }

        queueTimer = new Timer();
        queueTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (mc.thePlayer != null) {
                    String command = getHypixelCommand();
                    mc.thePlayer.sendChatMessage(command);
                    notifyQueued(command);
                }
            }
        }, (long) (delay.getInput() * 1000));
    }

    private void scheduleBlocksMcQueue() {
        if (queueTimer != null) {
            queueTimer.cancel();
        }

        queueTimer = new Timer();
        queueTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (mc.thePlayer != null) {
                    // Switch to slot 8 (index 7)
                    mc.thePlayer.inventory.currentItem = 7;

                    // Perform right click
                    KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
                    KeyBinding.onTick(mc.gameSettings.keyBindUseItem.getKeyCode());

                    // Reset the key state after a short delay
                    new Timer().schedule(new TimerTask() {
                        @Override
                        public void run() {
                            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
                        }
                    }, 50);

                    notifyQueued("BlocksMc SkyWars");
                }
            }
        }, (long) (delay.getInput() * 1000));
    }

    private String getHypixelCommand() {
        modes currentMode = mode.getMode();

        switch (currentMode) {
            case HypixelSolo:
                return "/play bedwars_eight_one";
            case HypixelDouble:
                return "/play bedwars_eight_two";
            case HypixelTrio:
                return "/play bedwars_four_three";
            case HypixelSquad:
                return "/play bedwars_four_four";
            default:
                return "/play bedwars_eight_one";
        }
    }

    private void notifyQueued(String gameMode) {
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new ChatComponentText(
                    EnumChatFormatting.DARK_PURPLE + "Stormy: " + EnumChatFormatting.GRAY + "Queuing "
                            + EnumChatFormatting.GREEN + gameMode));
        }
    }

    public enum modes {
        HypixelSolo, HypixelDouble, HypixelTrio, HypixelSquad, BlocksMcSW
    }
}