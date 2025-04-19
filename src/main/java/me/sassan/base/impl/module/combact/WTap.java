package me.sassan.base.impl.module.combact;

import me.sassan.base.api.module.Module;
import me.sassan.base.api.setting.impl.BooleanSetting;
import me.sassan.base.api.setting.impl.SliderSetting;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.weavemc.loader.api.event.SubscribeEvent;
import net.weavemc.loader.api.event.TickEvent;
import org.lwjgl.input.Keyboard;

import java.util.HashMap;
import java.util.Map;

public class WTap extends Module {
    private final SliderSetting chance;
    private final BooleanSetting playersOnly;

    private final Map<Integer, Long> targets = new HashMap<>();
    private boolean isWTapping = false;
    private long wtapReleaseTime = 0L;
    private long wtapResetTime = 0L;

    public WTap() {
        super("WTap", "Auto W-tap for increased knockback", Keyboard.KEY_NONE, Category.COMBAT);
        this.chance = new SliderSetting("Chance", 50.0, 0.0, 100.0, 1.0);
        this.playersOnly = new BooleanSetting("Players Only", true);

        this.addSetting(chance);
        this.addSetting(playersOnly);
    }

    @Override
    public void onDisable() {
        // Make sure sprint state is restored when disabling
        if (isWTapping) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
            isWTapping = false;
        }
        targets.clear();
        super.onDisable();
    }

    @SubscribeEvent
    public void onTick(TickEvent event) {
        if (!isEnabled() || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        // Handle W-tap timing
        if (isWTapping) {
            long currentTime = System.currentTimeMillis();

            // Release sprint for a short duration
            if (currentTime < wtapReleaseTime) {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
            }
            // Resume sprint after the release period
            else if (currentTime < wtapResetTime) {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
            }
            // End W-tap sequence
            else {
                isWTapping = false;
            }
        }

        // Check for attacking an entity
        if (mc.gameSettings.keyBindAttack.isKeyDown() && mc.objectMouseOver != null &&
                mc.objectMouseOver.entityHit != null && mc.thePlayer.isSprinting()) {

            if (checkTarget(mc.objectMouseOver.entityHit.getEntityId())) {
                executeWTap();
            }
        }
    }

    private boolean checkTarget(int entityId) {
        if (chance.getValue() == 0) {
            return false;
        }

        // Get the entity from the ID
        if (mc.theWorld.getEntityByID(entityId) == null) {
            return false;
        }

        // Check if the entity is a player (if playersOnly is enabled)
        if (playersOnly.getValue()) {
            if (!(mc.theWorld.getEntityByID(entityId) instanceof EntityPlayer)) {
                return false;
            }
        }
        // Otherwise, at least check if it's a living entity
        else if (!(mc.theWorld.getEntityByID(entityId) instanceof EntityLivingBase)) {
            return false;
        }

        // Check cooldown between hits
        long currentTime = System.currentTimeMillis();
        Long lastHitTime = targets.get(entityId);
        if (lastHitTime != null && (currentTime - lastHitTime) <= 500L) {
            return false;
        }

        // Apply chance
        if (chance.getValue() != 100.0D) {
            double rand = Math.random();
            if (rand >= chance.getValue() / 100.0D) {
                return false;
            }
        }

        // Track this hit
        targets.put(entityId, currentTime);
        return true;
    }

    private void executeWTap() {
        if (isWTapping) {
            return; // Don't start a new W-tap sequence if one is already in progress
        }

        isWTapping = true;
        long currentTime = System.currentTimeMillis();

        // Release sprint for 150-200ms, then reset after another 150ms
        wtapReleaseTime = currentTime + 150 + (int) (Math.random() * 50);
        wtapResetTime = wtapReleaseTime + 150;
    }
}
