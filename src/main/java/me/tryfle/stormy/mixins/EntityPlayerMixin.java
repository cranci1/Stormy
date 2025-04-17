package me.tryfle.stormy.mixins;

import net.minecraft.entity.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.StatBase;
import net.weavemc.loader.api.event.EventBus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import me.tryfle.stormy.events.HitSlowDownEvent;

@Mixin(EntityPlayer.class)
public abstract class EntityPlayerMixin {

    @Shadow
    public abstract ItemStack getHeldItem();

    @Shadow
    public abstract void onCriticalHit(Entity p_onCriticalHit_1_);

    @Shadow
    public abstract void onEnchantmentCritical(Entity p_onEnchantmentCritical_1_);

    @Shadow
    public abstract void triggerAchievement(StatBase p_triggerAchievement_1_);

    @Shadow
    public abstract ItemStack getCurrentEquippedItem();

    @Shadow
    public abstract void destroyCurrentEquippedItem();

    @Shadow
    public abstract void addStat(StatBase p_addStat_1_, int p_addStat_2_);

    @Shadow
    public abstract void addExhaustion(float p_addExhaustion_1_);

    /**
     * Replace the hit slowdown behavior with our custom event-based implementation
     */
    @Inject(method = "attackTargetEntityWithCurrentItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;addVelocity(DDD)V", shift = At.Shift.AFTER), locals = LocalCapture.CAPTURE_FAILSOFT)
    private void injectHitSlowDown(Entity targetEntity, CallbackInfo ci,
            float f, int i, float f1, boolean flag,
            boolean flag1, double d0, double d1, double d2, boolean flag2) {
        if (i > 0) {
            // This is where the original code would set sprinting to false
            // We'll replace it with our custom event
            HitSlowDownEvent slowDown = new HitSlowDownEvent(0.6, false);
            EventBus.callEvent(slowDown);

            EntityPlayer player = (EntityPlayer) (Object) this;
            player.motionX *= slowDown.getSlowDown();
            player.motionZ *= slowDown.getSlowDown();
            player.setSprinting(slowDown.isSprinting());
        }
    }
}