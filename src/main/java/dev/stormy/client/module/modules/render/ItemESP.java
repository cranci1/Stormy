package dev.stormy.client.module.modules.render;

import dev.stormy.client.module.Module;
import dev.stormy.client.utils.Utils;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.weavemc.loader.api.event.RenderWorldEvent;
import net.weavemc.loader.api.event.SubscribeEvent;

public class ItemESP extends Module {

    public ItemESP() {
        super("ItemESP", ModuleCategory.Render, 0);
    }

    @SubscribeEvent
    public void onRender(RenderWorldEvent e) {
        if (mc.theWorld == null) return;
        
        for (Object obj : mc.theWorld.loadedEntityList) {
            if (!(obj instanceof EntityItem)) continue;
            
            EntityItem itemEntity = (EntityItem) obj;
            ItemStack stack = itemEntity.getEntityItem();
            if (stack == null) continue;
            
            int color;
            if (stack.getItem() == Items.diamond) {
                color = 0xADD8E6;
            } else if (stack.getItem() == Items.iron_ingot) {
                color = 0xD3D3D3;
            } else if (stack.getItem() == Items.gold_ingot) {
                color = 0xFFD700;
            } else if (stack.getItem() == Items.emerald) {
                color = 0x90EE90;
            } else {
                continue;
            }
            Utils.HUD.drawBoxAroundEntity(itemEntity, 2, 0.0D, 0.0D, color, false);
            Utils.HUD.drawTextInsideEntity(itemEntity, String.valueOf(stack.stackSize), color);
        }
    }
}