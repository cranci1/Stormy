package me.tryfle.stormy.mixins;

import io.netty.channel.ChannelHandlerContext;
import me.tryfle.stormy.events.PacketEvent;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.weavemc.loader.api.event.EventBus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import me.tryfle.stormy.events.EventDirection;

@Mixin(priority = 800, value = NetworkManager.class)
public class NetworkManagerMixin {
    @Inject(method = "sendPacket(Lnet/minecraft/network/Packet;)V", at = @At("HEAD"), cancellable = true)
    public void sendPacket(Packet p_sendPacket_1_, CallbackInfo ci) {
        if (p_sendPacket_1_ == null)
            return; // Add null check

        PacketEvent e = new PacketEvent(p_sendPacket_1_, EventDirection.OUTGOING);
        EventBus.callEvent(e);

        if (e.isCancelled()) {
            ci.cancel();
            return;
        }

        // Fix type compatibility check and packet replacement
        if (e.getPacket() != p_sendPacket_1_ && e.getPacket() != null) {
            // Make sure the replacement packet is actually of the correct type
            // Check if the event packet's class is the same as or a subclass of the
            // original packet
            if (e.getPacket().getClass().isAssignableFrom(p_sendPacket_1_.getClass()) ||
                    p_sendPacket_1_.getClass().isAssignableFrom(e.getPacket().getClass())) {
                // This is safer, ensures type compatibility
                ci.cancel();
                // Re-send the packet through the normal channel to maintain type safety
                ((NetworkManager) (Object) this).sendPacket(e.getPacket());
            }
        }
    }

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/Packet;)V", at = @At("HEAD"), cancellable = true)
    public void receivePacket(ChannelHandlerContext p_channelRead0_1_, Packet p_channelRead0_2_, CallbackInfo ci) {
        if (p_channelRead0_2_ == null)
            return;

        PacketEvent e = new PacketEvent(p_channelRead0_2_, EventDirection.INCOMING);
        EventBus.callEvent(e);

        if (e.isCancelled()) {
            ci.cancel();
            return;
        }

        // Fix type compatibility check and packet replacement
        if (e.getPacket() != p_channelRead0_2_ && e.getPacket() != null) {
            // Make sure the replacement packet is actually of the correct type
            // Check if the event packet's class is the same as or a subclass of the
            // original packet
            if (e.getPacket().getClass().isAssignableFrom(p_channelRead0_2_.getClass()) ||
                    p_channelRead0_2_.getClass().isAssignableFrom(e.getPacket().getClass())) {
                // Instead of potentially unsafe assignment, we cancel this packet and handle it
                // properly
                ci.cancel();
                try {
                    // Use reflection to call channelRead0 with the new packet
                    NetworkManager networkManager = (NetworkManager) (Object) this;
                    networkManager.channelRead0(p_channelRead0_1_, e.getPacket());
                } catch (Exception ex) {
                    // Log the error but don't crash
                    System.err.println("Error when trying to process modified packet: " + ex.getMessage());
                }
            }
        }
    }
}