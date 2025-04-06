package dev.stormy.client.module.modules.combat;

import dev.stormy.client.clickgui.Theme;
import dev.stormy.client.module.Module;
import dev.stormy.client.module.setting.impl.DescriptionSetting;
import dev.stormy.client.module.setting.impl.DoubleSliderSetting;
import dev.stormy.client.module.setting.impl.SliderSetting;
import dev.stormy.client.module.setting.impl.TickSetting;
import dev.stormy.client.utils.Utils;
import dev.stormy.client.utils.math.TimerUtils;
import dev.stormy.client.utils.player.PlayerUtils;
import me.tryfle.stormy.events.PacketEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.weavemc.loader.api.event.RenderWorldEvent;
import net.weavemc.loader.api.event.SubscribeEvent;
import net.weavemc.loader.api.event.TickEvent;
import me.tryfle.stormy.events.EventDirection;

import java.awt.Color;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@SuppressWarnings("unused")
public class Backtrack extends Module {
    // Settings
    public static DoubleSliderSetting targetDistance;
    public static SliderSetting maxDelay, maxHurtTime, cooldown;
    public static TickSetting realPositionIndicator, disableOnHit, weaponOnly;
    public static SliderSetting lineWidth;
    public static TickSetting filled, applyHeadRotation;

    // Maps to track player positions and their delay expiration time
    private final Map<Integer, PlayerData> playerDataMap = new HashMap<>();
    private final Map<Integer, Long> entityDelayExpiration = new HashMap<>();

    // Cooldown timer
    private final TimerUtils cooldownTimer = new TimerUtils();
    private boolean isOnCooldown = false;

    // Flag to track if player was hit
    private boolean wasHit = false;

    public Backtrack() {
        super("Backtrack", ModuleCategory.Combat, 0);
        this.registerSetting(new DescriptionSetting("Lags other players"));
        this.registerSetting(targetDistance = new DoubleSliderSetting("Target Distance", 1.0D, 4.0D, 0.5D, 6.0D, 0.1D));
        this.registerSetting(maxDelay = new SliderSetting("Max Delay (ms)", 200.0D, 0.0D, 1000.0D, 10.0D));
        this.registerSetting(maxHurtTime = new SliderSetting("Max Hurt Time (ms)", 300.0D, 0.0D, 1000.0D, 10.0D));
        this.registerSetting(cooldown = new SliderSetting("Cooldown (s)", 1.0D, 0.0D, 5.0D, 0.1D));
        this.registerSetting(disableOnHit = new TickSetting("Disable on Hit", true));
        this.registerSetting(weaponOnly = new TickSetting("Weapon Only", false));

        // Real position indicator settings
        this.registerSetting(realPositionIndicator = new TickSetting("Real Position Indicator", true));
        this.registerSetting(lineWidth = new SliderSetting("Line Width", 2.0D, 1.0D, 5.0D, 0.1D));
        this.registerSetting(filled = new TickSetting("Filled", true));
        this.registerSetting(applyHeadRotation = new TickSetting("Apply Head Rotation", true));
    }

    @Override
    public void onDisable() {
        // Clear all tracked entities
        playerDataMap.clear();
        entityDelayExpiration.clear();
        wasHit = false;
        isOnCooldown = false;
    }

    @SubscribeEvent
    public void onTick(TickEvent e) {
        if (!PlayerUtils.isPlayerInGame() || mc.currentScreen != null)
            return;

        // Process delayed entity updates
        processDelayedEntities();

        // Handle cooldown
        if (isOnCooldown && cooldownTimer.hasReached((long) (cooldown.getInput() * 1000))) {
            isOnCooldown = false;
        }
    }

    @SubscribeEvent
    public void onPacketReceive(PacketEvent event) {
        if (!PlayerUtils.isPlayerInGame() || mc.currentScreen != null
                || event.getDirection() != EventDirection.INCOMING)
            return;

        Packet<?> packet = event.getPacket();

        // Check if module should be active based on conditions
        if (weaponOnly.isToggled() && !PlayerUtils.isPlayerHoldingWeapon())
            return;

        // If the player was hit and disableOnHit is enabled, release all tracking
        if (wasHit && disableOnHit.isToggled()) {
            clearAllDelays();
            wasHit = false;
            return;
        }

        // Check for cooldown
        if (isOnCooldown)
            return;

        // Check if player was hit (received velocity packet)
        if (packet instanceof S12PacketEntityVelocity) {
            S12PacketEntityVelocity velocityPacket = (S12PacketEntityVelocity) packet;
            if (velocityPacket.getEntityID() == mc.thePlayer.getEntityId()) {
                wasHit = true;
                if (disableOnHit.isToggled()) {
                    clearAllDelays();
                    isOnCooldown = true;
                    cooldownTimer.reset();
                    return;
                }
            }
        }

        // Handle entity movement packets
        if (isMovementPacket(packet)) {
            processMovementPacket(event, packet);
        }
    }

    private boolean isMovementPacket(Packet<?> packet) {
        return packet instanceof S14PacketEntity ||
                packet instanceof S18PacketEntityTeleport;
    }

    private void processMovementPacket(PacketEvent event, Packet<?> packet) {
        int entityId = -1;

        if (packet instanceof S14PacketEntity) {
            entityId = ((S14PacketEntity) packet).getEntity(mc.theWorld).getEntityId();
        } else if (packet instanceof S18PacketEntityTeleport) {
            entityId = ((S18PacketEntityTeleport) packet).getEntityId();
        }

        if (entityId == -1 || entityId == mc.thePlayer.getEntityId())
            return;

        EntityPlayer player = getPlayerById(entityId);
        if (player == null)
            return;

        double distance = mc.thePlayer.getDistanceToEntity(player);

        // Check if player is within target distance range
        if (distance >= targetDistance.getInputMin() && distance <= targetDistance.getInputMax()) {
            // Check hurt time
            if (player.hurtTime <= maxHurtTime.getInput() / 50) {
                // Update player data for rendering
                updatePlayerData(player);

                // Set delay for this entity
                long delayMs = (long) maxDelay.getInput();
                entityDelayExpiration.put(entityId, System.currentTimeMillis() + delayMs);

                // Cancel the packet - we'll apply changes manually when delay expires
                event.setCancelled(true);
            }
        }
    }

    private void processDelayedEntities() {
        long currentTime = System.currentTimeMillis();
        Iterator<Map.Entry<Integer, Long>> iterator = entityDelayExpiration.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<Integer, Long> entry = iterator.next();
            int entityId = entry.getKey();
            long expirationTime = entry.getValue();

            if (currentTime >= expirationTime) {
                // Delay expired, update entity
                updateEntityPosition(entityId);
                iterator.remove();
            }
        }
    }

    private void updateEntityPosition(int entityId) {
        EntityPlayer player = getPlayerById(entityId);
        PlayerData data = playerDataMap.get(entityId);

        if (player != null && data != null) {
            // Update player to stored position
            player.setPosition(data.realX, data.realY, data.realZ);
            player.rotationYawHead = data.yaw;

            // Remove from tracking
            playerDataMap.remove(entityId);
        }
    }

    private void clearAllDelays() {
        // Update all delayed entities to their real positions
        for (Integer entityId : entityDelayExpiration.keySet()) {
            updateEntityPosition(entityId);
        }
        entityDelayExpiration.clear();
        playerDataMap.clear();
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldEvent event) {
        if (!PlayerUtils.isPlayerInGame() || !realPositionIndicator.isToggled() || mc.currentScreen != null)
            return;

        // Render real positions of tracked players
        for (Map.Entry<Integer, PlayerData> entry : playerDataMap.entrySet()) {
            PlayerData data = entry.getValue();
            if (data.lastUpdateTime + 1000 < System.currentTimeMillis()) {
                continue;
            }

            int color = Theme.getMainColor().getRGB();
            float width = (float) lineWidth.getInput();

            // Draw box at the real position using re method
            Utils.HUD.re(new net.minecraft.util.BlockPos(data.realX, data.realY, data.realZ), color,
                    filled.isToggled());

            // Draw rotation indicator if enabled
            if (applyHeadRotation.isToggled()) {
                double rotationOffset = 0.3;
                double rotX = data.realX + Math.sin(Math.toRadians(-data.yaw)) * rotationOffset;
                double rotZ = data.realZ + Math.cos(Math.toRadians(-data.yaw)) * rotationOffset;

                // Use a small box to indicate head rotation instead of a line
                Utils.HUD.re(new net.minecraft.util.BlockPos(rotX, data.realY + data.height - 0.1, rotZ),
                        color, filled.isToggled());
            }
        }
    }

    private EntityPlayer getPlayerById(int entityId) {
        if (mc.theWorld == null)
            return null;

        for (Object entity : mc.theWorld.loadedEntityList) {
            if (entity instanceof EntityPlayer && ((EntityPlayer) entity).getEntityId() == entityId) {
                return (EntityPlayer) entity;
            }
        }
        return null;
    }

    private void updatePlayerData(EntityPlayer player) {
        PlayerData data = playerDataMap.computeIfAbsent(player.getEntityId(), k -> new PlayerData());
        data.realX = player.posX;
        data.realY = player.posY;
        data.realZ = player.posZ;
        data.width = player.width;
        data.height = player.height;
        data.yaw = player.rotationYawHead;
        data.lastUpdateTime = System.currentTimeMillis();
    }

    private static class PlayerData {
        double realX, realY, realZ;
        float width, height, yaw;
        long lastUpdateTime;
    }
}
