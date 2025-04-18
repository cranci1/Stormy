package me.sassan.base.utils.player;

import net.minecraft.client.Minecraft;

public class PlayerUtils {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public static boolean isPlayerInGame() {
        return mc.thePlayer != null && mc.theWorld != null;
    }
}