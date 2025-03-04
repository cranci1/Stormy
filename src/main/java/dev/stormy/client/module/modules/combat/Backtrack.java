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
import net.minecraft.util.Vec3;
import net.weavemc.loader.api.event.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("unused")
public class Backtrack extends Module {
    public static SliderSetting delay;
    public static SliderSetting nextBacktrackDelay;
    public static SliderSetting minRange;
    public static SliderSetting maxRange;
    public static SliderSetting hurtTimeValue;
    public static SliderSetting chance;
    public static SliderSetting trackingBuffer;
    public static SliderSetting lastAttackTime;
    public static SliderSetting maxSafePackets;
    public static SliderSetting maxPositionHistory;

    public static TickSetting pauseOnHurtTime;
    public static TickSetting useRange;
    public static TickSetting showRealPosition;
    public static ComboSetting<SafetyMode> safeMode;
    public static ComboSetting<EspMode> espMode;
    public static ComboSetting<PositionMode> positionMode;

    // Define enums for settings
    public enum SafetyMode {
        Strict, Balanced, Lenient
    }

    public enum EspMode {
        None, Box, Model, Box3D
    }

    public enum PositionMode {
        ClientPos, ServerPos
    }

    // Use ConcurrentLinkedQueue for thread safety instead of LinkedHashSet
    private final ConcurrentLinkedQueue<TimedPacket> packetQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<PositionHistoryEntry> positionHistory = new ConcurrentLinkedQueue<>();
    private Optional<EntityPlayer> target = Optional.empty();
    private long lastBacktrackTime = 0;
    private long lastAttackChronoTime = 0;
    private long lastSafetyProcessTime = 0;
    private long nextBacktrackAllowedTime = 0;
    private static final long MAX_PACKET_HOLD_TIME = 800; // Reduced to prevent timeouts
    private long trackingBufferTime = 0;
    private final Random random = new Random();
    private boolean shouldRender = true;

    // Track the real positions of entities with timestamps for better history
    private final ConcurrentHashMap<Integer, LinkedList<EntityPosition>> entityPositionHistory = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, EntityPosition> realPositions = new ConcurrentHashMap<>();

    // Maximum number of positions to store per entity
    private static final int DEFAULT_MAX_POSITIONS = 20;

    private static class EntityPosition {
        double x, y, z;
        float yaw, pitch;
        long timestamp;
        boolean interpolated;

        EntityPosition(double x, double y, double z, float yaw, float pitch) {
            this(x, y, z, yaw, pitch, System.currentTimeMillis(), false);
        }

        EntityPosition(double x, double y, double z, float yaw, float pitch, long timestamp, boolean interpolated) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.timestamp = timestamp;
            this.interpolated = interpolated;
        }

        public Vec3 toVec3() {
            return new Vec3(x, y, z);
        }

        @Override
        public String toString() {
            return String.format("Pos[x=%.2f, y=%.2f, z=%.2f, t=%d]", x, y, z, timestamp);
        }
    }

    private static class PositionHistoryEntry {
        Vec3 position;
        long timestamp;

        PositionHistoryEntry(Vec3 position) {
            this.position = position;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public Backtrack() {
        super("Backtrack", ModuleCategory.Combat, 0);
        this.registerSetting(new DescriptionSetting("Delays inbound packets for combat advantage"));
        this.registerSetting(delay = new SliderSetting("Delay (ms)", 100.0, 0.0, 300.0, 5.0));
        this.registerSetting(nextBacktrackDelay = new SliderSetting("Next Backtrack (ms)", 5.0, 0.0, 1000.0, 5.0));
        this.registerSetting(minRange = new SliderSetting("Min Range", 2.0, 0.0, 6.0, 0.1));
        this.registerSetting(maxRange = new SliderSetting("Max Range", 3.0, 0.0, 7.0, 0.1));
        this.registerSetting(chance = new SliderSetting("Chance (%)", 50.0, 0.0, 100.0, 1.0));
        this.registerSetting(useRange = new TickSetting("Target by Range", true));
        this.registerSetting(pauseOnHurtTime = new TickSetting("Pause on HurtTime", true));
        this.registerSetting(hurtTimeValue = new SliderSetting("HurtTime Value", 3.0, 0.0, 10.0, 1.0));
        this.registerSetting(trackingBuffer = new SliderSetting("Tracking Buffer (ms)", 300.0, 0.0, 2000.0, 10.0));
        this.registerSetting(lastAttackTime = new SliderSetting("Last Attack Time (ms)", 800.0, 0.0, 5000.0, 100.0));
        this.registerSetting(maxSafePackets = new SliderSetting("Max Queue Size", 150.0, 50.0, 500.0, 10.0));
        this.registerSetting(maxPositionHistory = new SliderSetting("Position History", 20.0, 5.0, 50.0, 1.0));
        this.registerSetting(showRealPosition = new TickSetting("Show Real Position", true));
        this.registerSetting(safeMode = new ComboSetting<>("Safety Mode", SafetyMode.Balanced));
        this.registerSetting(espMode = new ComboSetting<>("ESP Mode", EspMode.Box));
        this.registerSetting(positionMode = new ComboSetting<>("Position Mode", PositionMode.ServerPos));
    }

    @SubscribeEvent
    public void setTarget(TickEvent.Pre e) {
        if (!PlayerUtils.isPlayerInGame())
            return;

        if (useRange.isToggled() && mc.theWorld != null) {
            target = mc.theWorld.playerEntities.stream()
                    .filter(player -> player.getEntityId() != mc.thePlayer.getEntityId() &&
                            player.getDistanceToEntity(mc.thePlayer) <= maxRange.getInput() &&
                            isValidTarget(player))
                    .min(Comparator.comparingDouble(mc.thePlayer::getDistanceToEntity))
                    .map(Optional::of)
                    .orElse(Optional.empty());

            if (target.isPresent()) {
                trackingBufferTime = System.currentTimeMillis();
            }
        }

        // Update position history for all visible players
        if (mc.theWorld != null) {
            for (EntityPlayer player : mc.theWorld.playerEntities) {
                if (player != mc.thePlayer && player.isEntityAlive()) {
                    updateEntityPositionHistory(player);
                }
            }
        }

        // Clean up old position history entries
        cleanupPositionHistory();
    }

    private void updateEntityPositionHistory(EntityPlayer player) {
        int entityId = player.getEntityId();
        double x = player.posX;
        double y = player.posY;
        double z = player.posZ;
        float yaw = player.rotationYaw;
        float pitch = player.rotationPitch;

        // Get or create position history list for this entity
        LinkedList<EntityPosition> history = entityPositionHistory.computeIfAbsent(
                entityId, k -> new LinkedList<>());

        // Add new position to history
        EntityPosition newPos = new EntityPosition(x, y, z, yaw, pitch);
        history.addLast(newPos);

        // Update current real position
        realPositions.put(entityId, newPos);

        // Limit history size to prevent memory issues
        int maxHistory = (int) maxPositionHistory.getInput();
        while (history.size() > maxHistory) {
            history.removeFirst();
        }
    }

    private void cleanupPositionHistory() {
        long currentTime = System.currentTimeMillis();
        long maxAge = (long) (delay.getInput() * 2);

        // Clean up position history for all entities
        entityPositionHistory.forEach((entityId, history) -> {
            history.removeIf(pos -> currentTime - pos.timestamp > maxAge);
        });

        // Remove entities with empty history
        entityPositionHistory.entrySet().removeIf(entry -> entry.getValue().isEmpty());

        // Clean up position history queue
        positionHistory.removeIf(entry -> currentTime - entry.timestamp > maxAge);
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
    public void onTickProcessing(TickEvent e) {
        if (!PlayerUtils.isPlayerInGame()) {
            synchronizedClear();
            if (this.isEnabled()) {
                this.disable();
            }
            return;
        }

        long currentTime = System.currentTimeMillis();

        // Check if we should process queued packets based on timing
        if (currentTime - lastBacktrackTime >= nextBacktrackDelay.getInput()
                && currentTime >= nextBacktrackAllowedTime) {
            processPackets();
        }

        // More frequent safety checks to prevent kicks
        long safetyInterval = getSafetyInterval();
        if (currentTime - lastSafetyProcessTime >= safetyInterval) {
            if (!packetQueue.isEmpty() && mc.getNetHandler() != null) {
                processPackets(true);
                lastSafetyProcessTime = currentTime;
            }
        }

        // Prevent packet buildup - process old packets
        cleanupOldPackets();
    }

    // Method to cleanup old packets that can cause kicks
    private void cleanupOldPackets() {
        long currentTime = System.currentTimeMillis();
        List<TimedPacket> oldPackets = new ArrayList<>();

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
                        // Ignore errors during cleanup
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

        // Don't delay critical packets to prevent server timeouts
        if (isCriticalPacket(packet)) {
            return;
        }

        // Flush packets on teleport to avoid desyncs
        if (packet instanceof S08PacketPlayerPosLook) {
            clear(true);
            return;
        }

        // Check player health packet
        if (packet instanceof S06PacketUpdateHealth) {
            S06PacketUpdateHealth healthPacket = (S06PacketUpdateHealth) packet;
            if (healthPacket.getHealth() <= 0) {
                clear(true);
                return;
            }
        }

        // Process entity movement packets
        if (packet instanceof S14PacketEntity) {
            handleEntityMovementPacket((S14PacketEntity) packet);
        } else if (packet instanceof S18PacketEntityTeleport) {
            handleEntityTeleportPacket((S18PacketEntityTeleport) packet);
        }

        // Check if we need to process packets for our target
        if (target.isPresent() && isTargetPacket(packet)) {
            if (shouldProcessNow()) {
                processPackets(true);
                return;
            }
        }

        // Queue the packet for later processing
        e.setCancelled(true);
        packetQueue.add(new TimedPacket(packet, System.currentTimeMillis()));

        // Process packets if queue gets too large
        if (packetQueue.size() > maxSafePackets.getInput()) {
            processPackets(true);
        }
    }

    // Check if this is a critical packet that should never be delayed
    private boolean isCriticalPacket(Packet<?> packet) {
        return packet instanceof S02PacketChat ||
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
                packet instanceof S39PacketPlayerAbilities;
    }

    // Check if the packet is related to our target
    private boolean isTargetPacket(Packet<?> packet) {
        if (!target.isPresent())
            return false;
        EntityPlayer targetPlayer = target.get();

        if (packet instanceof S14PacketEntity) {
            S14PacketEntity entityPacket = (S14PacketEntity) packet;
            Entity entity = entityPacket.getEntity(mc.theWorld);
            return entity != null && entity.getEntityId() == targetPlayer.getEntityId();
        } else if (packet instanceof S18PacketEntityTeleport) {
            S18PacketEntityTeleport teleportPacket = (S18PacketEntityTeleport) packet;
            return teleportPacket.getEntityId() == targetPlayer.getEntityId();
        }

        return false;
    }

    private void handleEntityMovementPacket(S14PacketEntity entityPacket) {
        Entity entity = entityPacket.getEntity(mc.theWorld);

        if (entity instanceof EntityPlayer) {
            int entityId = entity.getEntityId();
            EntityPosition pos = realPositions.computeIfAbsent(entityId,
                    id -> new EntityPosition(entity.posX, entity.posY, entity.posZ, entity.rotationYaw,
                            entity.rotationPitch));

            // For relative movement packets
            if (entityPacket instanceof S14PacketEntity.S15PacketEntityRelMove ||
                    entityPacket instanceof S14PacketEntity.S17PacketEntityLookMove) {
                // Access fields through reflection if necessary
                try {
                    java.lang.reflect.Field xField = S14PacketEntity.class.getDeclaredField("field_149069_x");
                    java.lang.reflect.Field yField = S14PacketEntity.class.getDeclaredField("field_149068_y");
                    java.lang.reflect.Field zField = S14PacketEntity.class.getDeclaredField("field_149067_z");

                    xField.setAccessible(true);
                    yField.setAccessible(true);
                    zField.setAccessible(true);

                    byte x = xField.getByte(entityPacket);
                    byte y = yField.getByte(entityPacket);
                    byte z = zField.getByte(entityPacket);

                    double newX = pos.x + x / 32.0D;
                    double newY = pos.y + y / 32.0D;
                    double newZ = pos.z + z / 32.0D;

                    // Create a new position in the history
                    if (positionMode.getMode() == PositionMode.ServerPos) {
                        addEntityPositionToHistory(entityId, newX, newY, newZ, pos.yaw, pos.pitch);
                    }

                    // Update current position
                    pos.x = newX;
                    pos.y = newY;
                    pos.z = newZ;
                } catch (Exception ex) {
                    // Fallback
                    pos.x = entity.posX;
                    pos.y = entity.posY;
                    pos.z = entity.posZ;
                }
            }

            // For rotation packets
            if (entityPacket instanceof S14PacketEntity.S16PacketEntityLook ||
                    entityPacket instanceof S14PacketEntity.S17PacketEntityLookMove) {
                try {
                    java.lang.reflect.Field yawField = S14PacketEntity.class.getDeclaredField("field_149071_v");
                    java.lang.reflect.Field pitchField = S14PacketEntity.class.getDeclaredField("field_149070_w");

                    yawField.setAccessible(true);
                    pitchField.setAccessible(true);

                    byte yaw = yawField.getByte(entityPacket);
                    byte pitch = pitchField.getByte(entityPacket);

                    float newYaw = (float) (yaw * 360) / 256.0F;
                    float newPitch = (float) (pitch * 360) / 256.0F;

                    pos.yaw = newYaw;
                    pos.pitch = newPitch;
                } catch (Exception ex) {
                    pos.yaw = entity.rotationYaw;
                    pos.pitch = entity.rotationPitch;
                }
            }

            pos.timestamp = System.currentTimeMillis();

            // Add to history if client position mode is enabled
            if (positionMode.getMode() == PositionMode.ClientPos) {
                addEntityPositionToHistory(entityId, entity.posX, entity.posY, entity.posZ,
                        entity.rotationYaw, entity.rotationPitch);
            }
        }
    }

    private void handleEntityTeleportPacket(S18PacketEntityTeleport teleportPacket) {
        int entityId = teleportPacket.getEntityId();

        // Store absolute position from teleport
        double x = (double) teleportPacket.getX() / 32.0D;
        double y = (double) teleportPacket.getY() / 32.0D;
        double z = (double) teleportPacket.getZ() / 32.0D;
        float yaw = (float) (teleportPacket.getYaw() * 360) / 256.0F;
        float pitch = (float) (teleportPacket.getPitch() * 360) / 256.0F;

        EntityPosition pos = new EntityPosition(x, y, z, yaw, pitch);
        realPositions.put(entityId, pos);

        // Add to position history
        addEntityPositionToHistory(entityId, x, y, z, yaw, pitch);

        // Save target position specially if this is our target
        if (target.isPresent() && entityId == target.get().getEntityId()) {
            positionHistory.add(new PositionHistoryEntry(new Vec3(x, y, z)));
        }
    }

    private void addEntityPositionToHistory(int entityId, double x, double y, double z, float yaw, float pitch) {
        LinkedList<EntityPosition> history = entityPositionHistory.computeIfAbsent(
                entityId, k -> new LinkedList<>());

        EntityPosition newPos = new EntityPosition(x, y, z, yaw, pitch);
        history.addLast(newPos);

        // Limit history size
        int maxHistory = (int) maxPositionHistory.getInput();
        while (history.size() > maxHistory) {
            history.removeFirst();
        }
    }

    private boolean shouldProcessNow() {
        return random.nextInt(100) < 5; // 5% chance to process immediately for smoother gameplay
    }

    private void processPackets() {
        processPackets(false);
    }

    private void processPackets(boolean clearAll) {
        List<TimedPacket> packetsToProcess = new ArrayList<>();

        for (TimedPacket timedPacket : packetQueue) {
            if (clearAll || System.currentTimeMillis() - timedPacket.time() >= getRandomDelay()) {
                packetsToProcess.add(timedPacket);
            }
        }

        packetQueue.removeAll(packetsToProcess);

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

        // Set a minimum time before next backtrack to prevent spam
        if (!clearAll && nextBacktrackDelay.getInput() < 10) {
            nextBacktrackAllowedTime = System.currentTimeMillis() + 50;
        }
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldEvent event) {
        if (!shouldRender || !target.isPresent() || mc.theWorld == null)
            return;

        EntityPlayer targetPlayer = target.get();

        // Don't render if target is dead or invalid
        if (!targetPlayer.isEntityAlive() || targetPlayer.isDead) {
            shouldRender = false;
            return;
        }

        renderTargetPositions(targetPlayer);
    }

    private void renderTargetPositions(EntityPlayer targetPlayer) {
        if (espMode.getMode() == EspMode.None)
            return;

        // Render delayed position
        renderPlayerPosition(targetPlayer, targetPlayer.hurtTime > 0 ? 0xFFFF0000 : 0xFF242C93,
                targetPlayer.hurtTime > 0);

        // Render real position if enabled
        if (showRealPosition.isToggled() && realPositions.containsKey(targetPlayer.getEntityId())) {
            EntityPosition realPos = realPositions.get(targetPlayer.getEntityId());

            // Show history trail if we have enough history
            LinkedList<EntityPosition> history = entityPositionHistory.get(targetPlayer.getEntityId());
            if (history != null && history.size() > 1) {
                renderPositionHistory(targetPlayer, history);
            }

            // Render the current real position
            renderRealPosition(targetPlayer, realPos);
        }
    }

    private void renderPlayerPosition(EntityPlayer player, int color, boolean highlight) {
        switch (espMode.getMode()) {
            case Box:
                Utils.HUD.drawBoxAroundEntity(player, 1, 0.2, 0.0, color, highlight);
                break;

            case Box3D:
                Utils.HUD.drawBoxAroundEntity(player, 1, 1.0, 0.0, color, highlight);
                break;

            case Model:
                // Implementation would depend on your rendering utilities
                // This is a placeholder for more advanced rendering
                Utils.HUD.drawBoxAroundEntity(player, 1, 0.2, 0.0, color, highlight);
                break;
        }
    }

    private void renderRealPosition(EntityPlayer player, EntityPosition realPos) {
        // Save current position
        double origX = player.posX;
        double origY = player.posY;
        double origZ = player.posZ;
        float origYaw = player.rotationYaw;
        float origPitch = player.rotationPitch;

        // Temporarily modify entity position to render at real position
        player.posX = realPos.x;
        player.posY = realPos.y;
        player.posZ = realPos.z;
        player.rotationYaw = realPos.yaw;
        player.rotationPitch = realPos.pitch;

        // Draw box at real position
        renderPlayerPosition(player, 0xFF00FF00, false); // Green for real position

        // Restore original position
        player.posX = origX;
        player.posY = origY;
        player.posZ = origZ;
        player.rotationYaw = origYaw;
        player.rotationPitch = origPitch;
    }

    private void renderPositionHistory(EntityPlayer player, LinkedList<EntityPosition> history) {
        // Only render if we have enough positions
        if (history.size() < 2)
            return;

        // We'll just render the most recent positions for now
        // You could implement a full trail if desired
        int maxToShow = Math.min(5, history.size() - 1);
        int i = 0;

        for (EntityPosition pos : history) {
            if (i++ < history.size() - maxToShow)
                continue;

            // Calculate alpha based on recency (most recent is most opaque)
            int alpha = 40 + (i * 40 / history.size());
            if (alpha > 200)
                alpha = 200;

            int color = 0x00FFFF | (alpha << 24);

            // Save current position
            double origX = player.posX;
            double origY = player.posY;
            double origZ = player.posZ;

            // Temporarily modify entity position to render at history position
            player.posX = pos.x;
            player.posY = pos.y;
            player.posZ = pos.z;

            // Draw small markers at historical positions
            Utils.HUD.drawBoxAroundEntity(player, 2, 0.1, 0.0, color, false);

            // Restore original position
            player.posX = origX;
            player.posY = origY;
            player.posZ = origZ;
        }
    }

    private void clear(boolean processRemaining) {
        if (processRemaining && PlayerUtils.isPlayerInGame() && mc.getNetHandler() != null) {
            processPackets(true);
        } else {
            synchronizedClear();
        }
        target = Optional.empty();
        shouldRender = false;
    }

    private void synchronizedClear() {
        packetQueue.clear();
    }

    @Override
    public void onEnable() {
        clear(false);
        lastBacktrackTime = 0;
        lastAttackChronoTime = 0;
        trackingBufferTime = 0;
        nextBacktrackAllowedTime = 0;
        shouldRender = true;
        realPositions.clear();
        entityPositionHistory.clear();
        positionHistory.clear();
    }

    @Override
    public void onDisable() {
        clear(PlayerUtils.isPlayerInGame() && mc.getNetHandler() != null);
        realPositions.clear();
        entityPositionHistory.clear();
        positionHistory.clear();
    }

    private boolean shouldBacktrack() {
        if (!target.isPresent())
            return false;
        EntityPlayer targetPlayer = target.get();
        double distance = targetPlayer.getDistanceToEntity(mc.thePlayer);
        boolean effectiveRange = distance >= minRange.getInput() && distance <= maxRange.getInput();
        boolean bufferActive = System.currentTimeMillis() - trackingBufferTime <= trackingBuffer.getInput();
        boolean recentAttack = System.currentTimeMillis() - lastAttackChronoTime <= lastAttackTime.getInput();
        boolean chancePassed = random.nextDouble() * 100 < chance.getInput();
        return (effectiveRange || (bufferActive && recentAttack && chancePassed)) &&
                isValidTarget(targetPlayer) &&
                mc.thePlayer.ticksExisted > 20 &&
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

    private double getRandomDelay() {
        double baseDelay = delay.getInput();
        // Limit maximum delay to prevent kicks
        baseDelay = Math.min(baseDelay, 250.0);
        double variation = baseDelay * 0.1;
        return baseDelay + (random.nextDouble() * variation * 2) - variation;
    }

    private long getSafetyInterval() {
        SafetyMode mode = safeMode.getMode();
        switch (mode) {
            case Strict:
                return 200; // Process packets more frequently
            case Lenient:
                return 800; // Process packets less frequently
            default:
                return 450; // Balanced approach
        }
    }
}