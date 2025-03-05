package dev.stormy.client.module.modules.combat;

import dev.stormy.client.module.Module;
import dev.stormy.client.module.setting.impl.DescriptionSetting;
import dev.stormy.client.module.setting.impl.SliderSetting;
import dev.stormy.client.module.setting.impl.TickSetting;
import dev.stormy.client.module.setting.impl.ComboSetting;
import dev.stormy.client.utils.packet.PacketUtils;
import dev.stormy.client.utils.packet.TimedPacket;
import dev.stormy.client.utils.player.PlayerUtils;
import dev.stormy.client.utils.Utils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.server.*;
import net.minecraft.network.status.server.S00PacketServerInfo;
import net.weavemc.loader.api.event.*;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("unused")
public class Backtrack extends Module {
    public static SliderSetting delay;
    public static SliderSetting nextBacktrackDelay;
    public static SliderSetting range;
    public static SliderSetting hurtTimeValue;
    public static SliderSetting chance;
    public static SliderSetting trackingBuffer;
    public static SliderSetting lastAttackTime;
    public static SliderSetting maxSafePackets;

    public static TickSetting pauseOnHurtTime;
    public static TickSetting useRange;
    public static TickSetting showRealPosition;
    public static ComboSetting<SafetyMode> safeMode;

    // Define the SafetyMode enum for use with ComboSetting
    public enum SafetyMode {
        Strict, Balanced, Lenient
    }

    private static final Set<TimedPacket> packetQueue = new LinkedHashSet<>();
    private Optional<EntityPlayer> target = Optional.empty();
    private long lastBacktrackTime = 0;
    private long lastAttackChronoTime = 0;
    private long lastSafetyProcessTime = 0;
    private static final long MAX_PACKET_HOLD_TIME = 1000; // Reduced from 2000ms to prevent kicks
    private long trackingBufferTime = 0;
    private final Random random = new Random();

    // Track the real positions of entities
    private final ConcurrentHashMap<Integer, EntityPosition> realPositions = new ConcurrentHashMap<>();

    private static class EntityPosition {
        double x, y, z;
        float yaw, pitch;
        long timestamp;

        EntityPosition(double x, double y, double z, float yaw, float pitch) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public Backtrack() {
        super("Backtrack", ModuleCategory.Combat, 0);
        this.registerSetting(new DescriptionSetting("Delays inbound packets for combat advantage"));
        this.registerSetting(delay = new SliderSetting("Delay (ms)", 100.0, 0.0, 500.0, 5.0));
        this.registerSetting(nextBacktrackDelay = new SliderSetting("Next Backtrack (ms)", 5.0, 0.0, 1000.0, 5.0));
        this.registerSetting(range = new SliderSetting("Range", 3.0, 0.0, 7.0, 0.1));
        this.registerSetting(chance = new SliderSetting("Chance (%)", 50.0, 0.0, 100.0, 1.0));
        this.registerSetting(useRange = new TickSetting("Target by Range", true));
        this.registerSetting(pauseOnHurtTime = new TickSetting("Pause on HurtTime", true));
        this.registerSetting(hurtTimeValue = new SliderSetting("HurtTime Value", 3.0, 0.0, 10.0, 1.0));
        this.registerSetting(trackingBuffer = new SliderSetting("Tracking Buffer (ms)", 300.0, 0.0, 2000.0, 10.0));
        this.registerSetting(lastAttackTime = new SliderSetting("Last Attack Time (ms)", 800.0, 0.0, 5000.0, 100.0));
        this.registerSetting(maxSafePackets = new SliderSetting("Max Queue Size", 150.0, 50.0, 500.0, 10.0));
        this.registerSetting(showRealPosition = new TickSetting("Show Real Position", true));
        this.registerSetting(safeMode = new ComboSetting<>("Safety Mode", SafetyMode.Balanced));
    }

    @SubscribeEvent
    public void setTarget(TickEvent.Pre e) {
        if (PlayerUtils.isPlayerInGame()) {
            if (useRange.isToggled() && mc.theWorld != null) {
                target = mc.theWorld.playerEntities.stream()
                        .filter(player -> player.getEntityId() != mc.thePlayer.getEntityId() &&
                                player.getDistanceToEntity(mc.thePlayer) <= range.getInput() &&
                                isValidTarget(player))
                        .findFirst();

                if (target.isPresent()) {
                    trackingBufferTime = System.currentTimeMillis();
                }
            }

            // Update real positions for all visible players
            if (mc.theWorld != null) {
                for (EntityPlayer player : mc.theWorld.playerEntities) {
                    if (player != mc.thePlayer && player.isEntityAlive()) {
                        realPositions.put(player.getEntityId(),
                                new EntityPosition(player.posX, player.posY, player.posZ,
                                        player.rotationYaw, player.rotationPitch));
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onAttack(PacketEvent.Send event) {
        if (!PlayerUtils.isPlayerInGame() || !isEnabled())
            return;

        if (event.getPacket() instanceof C02PacketUseEntity) {
            C02PacketUseEntity packet = (C02PacketUseEntity) event.getPacket();
            if (packet.getAction() == C02PacketUseEntity.Action.ATTACK) {
                lastAttackChronoTime = System.currentTimeMillis();
                if (!useRange.isToggled() && packet.getEntityFromWorld(mc.theWorld) instanceof EntityPlayer) {
                    EntityPlayer attacked = (EntityPlayer) packet.getEntityFromWorld(mc.theWorld);
                    if (isValidTarget(attacked)) {
                        target = Optional.of(attacked);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onTickDisabler(TickEvent e) {
        if (!PlayerUtils.isPlayerInGame()) {
            synchronized (packetQueue) {
                packetQueue.clear();
            }
            if (this.isEnabled()) {
                this.disable();
            }
            return;
        }

        long currentTime = System.currentTimeMillis();

        if (currentTime - lastBacktrackTime >= nextBacktrackDelay.getInput()) {
            processPackets();
        }

        // More frequent safety checks to prevent kicks
        long safetyInterval = getSafetyInterval();
        if (currentTime - lastSafetyProcessTime >= safetyInterval) {
            synchronized (packetQueue) {
                if (!packetQueue.isEmpty() && mc.getNetHandler() != null) {
                    processPackets(true);
                    lastSafetyProcessTime = currentTime;
                }
            }
        }

        // Prevent packet buildup - process old packets
        cleanupOldPackets();
    }

    // Method to cleanup old packets that can cause kicks
    private void cleanupOldPackets() {
        synchronized (packetQueue) {
            long currentTime = System.currentTimeMillis();
            Set<TimedPacket> oldPackets = new LinkedHashSet<>();

            for (TimedPacket packet : packetQueue) {
                if (currentTime - packet.time() > MAX_PACKET_HOLD_TIME) {
                    oldPackets.add(packet);
                }
            }

            if (!oldPackets.isEmpty()) {
                packetQueue.removeAll(oldPackets);
                for (TimedPacket packet : oldPackets) {
                    if (mc.getNetHandler() != null) {
                        try {
                            PacketUtils.handle(packet.packet(), false);
                        } catch (Exception e) {
                            // Ignore errors
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void pingSpooferIncoming(PacketEvent.Receive e) {
        if (!PlayerUtils.isPlayerInGame() || !shouldBacktrack())
            return;

        Packet<?> packet = e.getPacket();

        // Don't delay these critical packets - expanded list to prevent kicks
        if (packet instanceof S02PacketChat ||
                packet instanceof S08PacketPlayerPosLook ||
                packet instanceof S40PacketDisconnect ||
                packet instanceof S00PacketKeepAlive ||
                packet instanceof S03PacketTimeUpdate ||
                packet instanceof S00PacketServerInfo ||
                packet instanceof S01PacketJoinGame ||
                packet instanceof S19PacketEntityStatus ||
                packet instanceof S24PacketBlockAction ||
                packet instanceof S32PacketConfirmTransaction ||
                packet instanceof S37PacketStatistics ||
                packet instanceof S38PacketPlayerListItem ||
                packet instanceof S39PacketPlayerAbilities) {
            return;
        }

        if (packet instanceof S08PacketPlayerPosLook) {
            clear(true);
            return;
        }

        if (packet instanceof S06PacketUpdateHealth) {
            S06PacketUpdateHealth healthPacket = (S06PacketUpdateHealth) packet;
            if (healthPacket.getHealth() <= 0) {
                clear(true);
                return;
            }
        }

        if (packet instanceof S14PacketEntity) {
            S14PacketEntity entityPacket = (S14PacketEntity) packet;
            Entity entity = entityPacket.getEntity(mc.theWorld);

            if (entity instanceof EntityPlayer) {
                int entityId = entity.getEntityId();
                EntityPosition pos = realPositions.computeIfAbsent(entityId,
                        id -> new EntityPosition(entity.posX, entity.posY, entity.posZ, entity.rotationYaw,
                                entity.rotationPitch));

                // For S14PacketEntity.S15PacketEntityRelMove and S17PacketEntityLookMove
                // These subclasses contain movement data
                if (entityPacket instanceof S14PacketEntity.S15PacketEntityRelMove ||
                        entityPacket instanceof S14PacketEntity.S17PacketEntityLookMove) {
                    // Access fields through reflection if necessary
                    try {
                        // Get the fields through reflection if direct access isn't available
                        java.lang.reflect.Field xField = S14PacketEntity.class.getDeclaredField("field_149069_x");
                        java.lang.reflect.Field yField = S14PacketEntity.class.getDeclaredField("field_149068_y");
                        java.lang.reflect.Field zField = S14PacketEntity.class.getDeclaredField("field_149067_z");

                        xField.setAccessible(true);
                        yField.setAccessible(true);
                        zField.setAccessible(true);

                        byte x = xField.getByte(entityPacket);
                        byte y = yField.getByte(entityPacket);
                        byte z = zField.getByte(entityPacket);

                        pos.x += x / 32.0D;
                        pos.y += y / 32.0D;
                        pos.z += z / 32.0D;
                    } catch (Exception ex) {
                        // Fallback: Just update with current position
                        pos.x = entity.posX;
                        pos.y = entity.posY;
                        pos.z = entity.posZ;
                    }
                }

                // For S16PacketEntityLook and S17PacketEntityLookMove - these subclasses
                // contain rotation
                if (entityPacket instanceof S14PacketEntity.S16PacketEntityLook ||
                        entityPacket instanceof S14PacketEntity.S17PacketEntityLookMove) {
                    try {
                        java.lang.reflect.Field yawField = S14PacketEntity.class.getDeclaredField("field_149071_v");
                        java.lang.reflect.Field pitchField = S14PacketEntity.class.getDeclaredField("field_149070_w");

                        yawField.setAccessible(true);
                        pitchField.setAccessible(true);

                        byte yaw = yawField.getByte(entityPacket);
                        byte pitch = pitchField.getByte(entityPacket);

                        pos.yaw = (float) (yaw * 360) / 256.0F;
                        pos.pitch = (float) (pitch * 360) / 256.0F;
                    } catch (Exception ex) {
                        // Fallback: Just use current rotation
                        pos.yaw = entity.rotationYaw;
                        pos.pitch = entity.rotationPitch;
                    }
                }

                pos.timestamp = System.currentTimeMillis();
            }
        } else if (packet instanceof S18PacketEntityTeleport) {
            S18PacketEntityTeleport teleportPacket = (S18PacketEntityTeleport) packet;
            int entityId = teleportPacket.getEntityId();

            // Store absolute position from teleport
            double x = (double) teleportPacket.getX() / 32.0D;
            double y = (double) teleportPacket.getY() / 32.0D;
            double z = (double) teleportPacket.getZ() / 32.0D;
            float yaw = (float) (teleportPacket.getYaw() * 360) / 256.0F;
            float pitch = (float) (teleportPacket.getPitch() * 360) / 256.0F;

            EntityPosition pos = new EntityPosition(x, y, z, yaw, pitch);
            realPositions.put(entityId, pos);
        }

        if (target.isPresent() &&
                ((packet instanceof S14PacketEntity &&
                        ((S14PacketEntity) packet).getEntity(mc.theWorld) == target.get()) ||
                        (packet instanceof S18PacketEntityTeleport &&
                                ((S18PacketEntityTeleport) packet).getEntityId() == target.get().getEntityId()))) {
            if (shouldProcessNow()) {
                processPackets(true);
                return;
            }
        }

        synchronized (packetQueue) {
            // Process packets if queue gets too large
            if (packetQueue.size() > maxSafePackets.getInput()) {
                processPackets(true);
            }
            e.setCancelled(true);
            packetQueue.add(new TimedPacket(packet, System.currentTimeMillis()));
        }
    }

    private boolean shouldBacktrack() {
        if (!target.isPresent())
            return false;
        EntityPlayer targetPlayer = target.get();
        double distance = targetPlayer.getDistanceToEntity(mc.thePlayer);
        boolean effectiveRange = distance <= range.getInput();
        boolean bufferActive = System.currentTimeMillis() - trackingBufferTime <= trackingBuffer.getInput();
        boolean recentAttack = System.currentTimeMillis() - lastAttackChronoTime <= lastAttackTime.getInput();
        boolean chancePassed = random.nextDouble() * 100 < chance.getInput();
        return (effectiveRange || (bufferActive && recentAttack && chancePassed)) &&
                isValidTarget(targetPlayer) &&
                mc.thePlayer.ticksExisted > 10 &&
                !shouldPause();
    }

    private boolean shouldPause() {
        if (!pauseOnHurtTime.isToggled() || !target.isPresent())
            return false;
        EntityPlayer targetPlayer = target.get();
        return targetPlayer.hurtTime >= hurtTimeValue.getInput();
    }

    private boolean isValidTarget(EntityPlayer player) {
        return player != null &&
                player.isEntityAlive() &&
                !player.isInvisible() &&
                player != mc.thePlayer;
    }

    private boolean shouldProcessNow() {
        return random.nextInt(100) < 10;
    }

    private void processPackets() {
        processPackets(false);
    }

    private void processPackets(boolean clearAll) {
        Set<TimedPacket> packetsToProcess = new LinkedHashSet<>();
        synchronized (packetQueue) {
            for (TimedPacket timedPacket : packetQueue) {
                if (clearAll || System.currentTimeMillis() - timedPacket.time() >= getRandomDelay()) {
                    packetsToProcess.add(timedPacket);
                }
            }
            packetQueue.removeAll(packetsToProcess);
        }
        if (mc.getNetHandler() != null) {
            for (TimedPacket timedPacket : packetsToProcess) {
                try {
                    PacketUtils.handle(timedPacket.packet(), false);
                } catch (Exception e) {
                    System.err.println("Error processing packet: " + e.getMessage());
                }
            }
        }
        lastBacktrackTime = System.currentTimeMillis();
    }

    private void renderTargetMarker(EntityPlayer targetPlayer) {
        int delayedPositionColor = targetPlayer.hurtTime > 0 ? 0xFFFF0000 : 0xFF242C93;
        // Draw box around the current (delayed) position
        Utils.HUD.drawBoxAroundEntity(targetPlayer, 1, 0.2, 0.0, delayedPositionColor, targetPlayer.hurtTime > 0);

        // Draw box around the real position if enabled and available
        if (showRealPosition.isToggled() && realPositions.containsKey(targetPlayer.getEntityId())) {
            EntityPosition realPos = realPositions.get(targetPlayer.getEntityId());
            int realPosColor = 0xFF00FF00; // Green for real position

            // Save current position
            double origX = targetPlayer.posX;
            double origY = targetPlayer.posY;
            double origZ = targetPlayer.posZ;
            float origYaw = targetPlayer.rotationYaw;
            float origPitch = targetPlayer.rotationPitch;

            // Temporarily modify entity position to render at real position
            targetPlayer.posX = realPos.x;
            targetPlayer.posY = realPos.y;
            targetPlayer.posZ = realPos.z;
            targetPlayer.rotationYaw = realPos.yaw;
            targetPlayer.rotationPitch = realPos.pitch;

            // Draw box at real position
            Utils.HUD.drawBoxAroundEntity(targetPlayer, 1, 0.2, 0.0, realPosColor, false);

            // Restore original position
            targetPlayer.posX = origX;
            targetPlayer.posY = origY;
            targetPlayer.posZ = origZ;
            targetPlayer.rotationYaw = origYaw;
            targetPlayer.rotationPitch = origPitch;
        }
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldEvent event) {
        if (target.isPresent() && mc.theWorld != null) {
            renderTargetMarker(target.get());
        }
    }

    private void clear(boolean processRemaining) {
        if (processRemaining && PlayerUtils.isPlayerInGame() && mc.getNetHandler() != null) {
            processPackets(true);
        } else {
            synchronized (packetQueue) {
                packetQueue.clear();
            }
        }
        target = Optional.empty();
    }

    @Override
    public void onEnable() {
        clear(false);
        lastBacktrackTime = 0;
        lastAttackChronoTime = 0;
        trackingBufferTime = 0;
        realPositions.clear();
    }

    @Override
    public void onDisable() {
        clear(PlayerUtils.isPlayerInGame() && mc.getNetHandler() != null);
        realPositions.clear();
    }

    private double getRandomDelay() {
        double baseDelay = delay.getInput();
        // Limit maximum delay to prevent kicks
        baseDelay = Math.min(baseDelay, 300.0);
        double variation = baseDelay * 0.1;
        return baseDelay + (random.nextDouble() * variation * 2) - variation;
    }

    private long getSafetyInterval() {
        SafetyMode mode = safeMode.getMode();
        switch (mode) {
            case Strict:
                return 250; // Process packets more frequently
            case Lenient:
                return 1000; // Process packets less frequently
            default:
                return 500; // Balanced approach
        }
    }
}