package dev.stormy.client.module.modules.combat;

import dev.stormy.client.module.Module;
import dev.stormy.client.module.setting.impl.DescriptionSetting;
import dev.stormy.client.module.setting.impl.SliderSetting;
import dev.stormy.client.module.setting.impl.TickSetting;
import dev.stormy.client.utils.packet.PacketUtils;
import dev.stormy.client.utils.packet.TimedPacket;
import dev.stormy.client.utils.player.PlayerUtils;
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

@SuppressWarnings("unused")
public class Backtrack extends Module {
    public static SliderSetting delay;
    public static SliderSetting nextBacktrackDelay;
    public static SliderSetting range;
    public static SliderSetting hurtTimeValue;
    public static SliderSetting chance;
    public static SliderSetting trackingBuffer;
    public static SliderSetting lastAttackTime;

    public static TickSetting pauseOnHurtTime;
    public static TickSetting useRange;

    private static final Set<TimedPacket> packetQueue = new LinkedHashSet<>();
    private Optional<EntityPlayer> target = Optional.empty();
    private long lastBacktrackTime = 0;
    private long lastAttackChronoTime = 0;
    private long lastSafetyProcessTime = 0;
    private static final long MAX_PACKET_HOLD_TIME = 5000;
    private long trackingBufferTime = 0;
    private boolean shouldPause = false;
    private final Random random = new Random();

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
        // Shorter default time to reduce chance of disconnects
        this.registerSetting(trackingBuffer = new SliderSetting("Tracking Buffer (ms)", 300.0, 0.0, 2000.0, 10.0));
        this.registerSetting(lastAttackTime = new SliderSetting("Last Attack Time (ms)", 800.0, 0.0, 5000.0, 100.0));
    }

    @SubscribeEvent
    public void setTarget(TickEvent.Pre e) {
        if (PlayerUtils.isPlayerInGame()) {
            if (useRange.isToggled()) {
                target = mc.theWorld != null
                        ? mc.theWorld.playerEntities.stream()
                                .filter(player -> player.getEntityId() != mc.thePlayer.getEntityId() &&
                                        player.getDistanceToEntity(mc.thePlayer) <= range.getInput() &&
                                        isValidTarget(player))
                                .findFirst()
                        : Optional.empty();

                if (target.isPresent()) {
                    trackingBufferTime = System.currentTimeMillis();
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

        if (currentTime - lastSafetyProcessTime >= MAX_PACKET_HOLD_TIME) {
            synchronized (packetQueue) {
                if (!packetQueue.isEmpty() && mc.getNetHandler() != null) {
                    processPackets(true);
                    lastSafetyProcessTime = currentTime;
                }
            }
        }
    }

    @SubscribeEvent
    public void pingSpooferIncoming(PacketEvent.Receive e) {
        if (!PlayerUtils.isPlayerInGame() || !shouldBacktrack())
            return;

        Packet<?> packet = e.getPacket();

        // Add S00PacketServerInfo to the ignored packet types
        if (packet instanceof S02PacketChat ||
                packet instanceof S08PacketPlayerPosLook ||
                packet instanceof S40PacketDisconnect ||
                packet instanceof S00PacketKeepAlive ||
                packet instanceof S03PacketTimeUpdate ||
                packet instanceof S00PacketServerInfo) { // Added this line to fix crash
            return;
        }

        // Clear on teleport
        if (packet instanceof S08PacketPlayerPosLook) {
            clear(true);
            return;
        }

        // Clear on own death
        if (packet instanceof S06PacketUpdateHealth) {
            S06PacketUpdateHealth healthPacket = (S06PacketUpdateHealth) packet;
            if (healthPacket.getHealth() <= 0) {
                clear(true);
                return;
            }
        }

        // Entity position packet checks
        if (target.isPresent() && ((packet instanceof S14PacketEntity
                && ((S14PacketEntity) packet).getEntity(mc.theWorld) == target.get())
                || (packet instanceof S18PacketEntityTeleport
                        && ((S18PacketEntityTeleport) packet).getEntityId() == target.get().getEntityId()))) {
            // If the target's actual position is closer than its tracked position, process
            // all packets
            if (shouldProcessNow()) {
                processPackets(true);
                return;
            }
        }

        // Add safety to prevent packet queue from growing too large
        synchronized (packetQueue) {
            // If we have too many packets, process some to avoid timeout
            if (packetQueue.size() > 500) {
                processPackets(true);
            }

            e.setCancelled(true);
            packetQueue.add(new TimedPacket(packet, System.currentTimeMillis()));
        }
    }

    private boolean shouldProcessNow() {
        // Simplified check - in a real implementation you'd compare tracked position vs
        // actual position
        return random.nextInt(100) < 10; // 10% chance of immediate processing
    }

    private boolean shouldBacktrack() {
        if (!target.isPresent())
            return false;

        EntityPlayer targetPlayer = target.get();

        boolean inRange = targetPlayer.getDistanceToEntity(mc.thePlayer) <= range.getInput();
        boolean withinBuffer = System.currentTimeMillis() - trackingBufferTime <= trackingBuffer.getInput();
        boolean validAttackTime = System.currentTimeMillis() - lastAttackChronoTime <= lastAttackTime.getInput();
        boolean passedChance = random.nextDouble() * 100 < chance.getInput();

        return (inRange || withinBuffer) &&
                isValidTarget(targetPlayer) &&
                mc.thePlayer.ticksExisted > 10 &&
                passedChance &&
                validAttackTime &&
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
                    // Safely handle exceptions without crashing
                    System.err.println("Error processing packet: " + e.getMessage());
                }
            }
        }

        lastBacktrackTime = System.currentTimeMillis();
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
    }

    @Override
    public void onDisable() {
        clear(PlayerUtils.isPlayerInGame() && mc.getNetHandler() != null);
    }

    private double getRandomDelay() {
        double baseDelay = delay.getInput();
        double variation = baseDelay * 0.1;
        return baseDelay + (random.nextDouble() * variation * 2) - variation;
    }
}