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
    public void sendPacket(Packet<?> p_sendPacket_1_, CallbackInfo ci) {
        if (p_sendPacket_1_ == null)
            return;

        PacketEvent e = new PacketEvent(p_sendPacket_1_, EventDirection.OUTGOING);
        EventBus.callEvent(e);

        if (e.isCancelled()) {
            ci.cancel();
            return;
        }

        if (e.getPacket() != p_sendPacket_1_ && e.getPacket() != null) {
            if (e.getPacket().getClass() == p_sendPacket_1_.getClass()) {
                ci.cancel();
                ((NetworkManager) (Object) this).sendPacket(e.getPacket());
            } else {
                System.out.println("[Stormy] Warning: Cannot replace packet of type " + 
                    p_sendPacket_1_.getClass().getName() + " with incompatible type " + 
                    e.getPacket().getClass().getName());
            }
        }
    }

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/Packet;)V", at = @At("HEAD"), cancellable = true)
    public void receivePacket(ChannelHandlerContext p_channelRead0_1_, Packet<?> p_channelRead0_2_, CallbackInfo ci) {
        if (p_channelRead0_2_ == null)
            return;

        PacketEvent e = new PacketEvent(p_channelRead0_2_, EventDirection.INCOMING);
        EventBus.callEvent(e);

        if (e.isCancelled()) {
            ci.cancel();
            return;
        }

        if (e.getPacket() != p_channelRead0_2_ && e.getPacket() != null) {
            if (e.getPacket().getClass() == p_channelRead0_2_.getClass()) {
                ci.cancel();
                try {
                    NetworkManager networkManager = (NetworkManager) (Object) this;
                    networkManager.channelRead0(p_channelRead0_1_, e.getPacket());
                } catch (Exception ex) {
                    System.err.println("[Stormy] Error processing modified packet: " + ex.getMessage());
                    ex.printStackTrace();
                }
            } else {
                System.out.println("[Stormy] Warning: Cannot replace packet of type " + 
                    p_channelRead0_2_.getClass().getName() + " with incompatible type " + 
                    e.getPacket().getClass().getName());
            }
        }
    }
}